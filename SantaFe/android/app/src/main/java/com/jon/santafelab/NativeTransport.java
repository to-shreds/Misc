package com.jon.santafelab;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;

/** Direct, bounded HTTPS transport for the bundled page. It never persists request data. */
public final class NativeTransport implements AutoCloseable {
    public interface Callback {
        void reply(String id, JSONObject response, String error);
    }

    // Injection is package-private and is used by credential-free JVM fixture tests only.
    interface ConnectionFactory {
        HttpsURLConnection open(URL url) throws IOException;
    }

    static final int MAX_ENVELOPE_BYTES = 65536;
    static final int MAX_RESPONSE_BYTES = 1048576;
    static final int CONNECT_TIMEOUT_MS = 15000;
    static final int READ_TIMEOUT_MS = 30000;
    static final long DEADLINE_MS = 45000;
    private static final int MAX_HEADER_COUNT = 64;
    private static final int MAX_HEADER_VALUE_CHARS = 4096;
    private static final int MAX_RESPONSE_HEADER_CHARS = 32768;

    private static final String INVALID = "The app rejected an invalid request. No request was sent.";
    private static final String BUSY = "Two requests are already running. No additional request was sent.";
    private static final String TIMEOUT = "Request timed out. Outcome unknown; no automatic retry.";
    private static final String CANCELLED = "Stopped locally. A submitted vehicle command may still run; it was not retried.";
    private static final String NETWORK = "The HTTPS request failed. Outcome unknown; no automatic retry.";
    private static final String TLS = "The HTTPS security check failed. Outcome unknown; no automatic retry.";
    private static final String TOO_LARGE = "The Hyundai response exceeded the app limit. Outcome unknown; no automatic retry.";
    private static final String INVALID_RESPONSE = "The app could not read the Hyundai response. Outcome unknown; no automatic retry.";

    private final Object lock = new Object();
    private final Callback callback;
    private final ConnectionFactory connectionFactory;
    private final long deadlineMillis;
    private final Map<String, Job> active = new HashMap<String, Job>();
    private final ThreadPoolExecutor workers;
    private final ScheduledThreadPoolExecutor deadlines;
    private boolean closed;

    public NativeTransport(Callback callback) {
        this(callback, new ConnectionFactory() {
            @Override public HttpsURLConnection open(URL url) throws IOException {
                // Ordinary Android TLS and hostname verification remain in force.
                return (HttpsURLConnection) url.openConnection(Proxy.NO_PROXY);
            }
        }, DEADLINE_MS);
    }

    NativeTransport(Callback callback, ConnectionFactory connectionFactory, long deadlineMillis) {
        if (callback == null || connectionFactory == null || deadlineMillis < 1) {
            throw new IllegalArgumentException("Invalid transport configuration.");
        }
        this.callback = callback;
        this.connectionFactory = connectionFactory;
        this.deadlineMillis = deadlineMillis;
        // A zero-length queue prevents cancelled, blocked sockets from creating a backlog.
        workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
                new SynchronousQueue<Runnable>(), threads("sf-https"),
                new ThreadPoolExecutor.AbortPolicy());
        deadlines = new ScheduledThreadPoolExecutor(1, threads("sf-deadline"));
        deadlines.setRemoveOnCancelPolicy(true);
    }

    private static ThreadFactory threads(final String prefix) {
        return new ThreadFactory() {
            private final AtomicInteger sequence = new AtomicInteger();
            @Override public Thread newThread(Runnable task) {
                Thread thread = new Thread(task, prefix + "-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };
    }

    /** Parses and validates every field before any connection can be opened. */
    public void request(String envelope) {
        String id = null;
        RequestPolicy.Validated validated;
        try {
            if (envelope == null || envelope.length() > MAX_ENVELOPE_BYTES
                    || envelope.getBytes(StandardCharsets.UTF_8).length > MAX_ENVELOPE_BYTES) {
                return; // An unparsed envelope has no trusted correlation id.
            }
            JSONObject root = object(envelope);
            Object rawId = root.opt("id");
            if (!(rawId instanceof String) || !validId((String) rawId)) return;
            id = (String) rawId;
            Object rawRequest = root.opt("request");
            if (!(rawRequest instanceof JSONObject)) throw new IllegalArgumentException();
            JSONObject request = (JSONObject) rawRequest;
            Object rawMethod = request.opt("method");
            Object rawUrl = request.opt("url");
            Object rawHeaders = request.opt("headers");
            Object rawBody = request.opt("body");
            if (!(rawMethod instanceof String) || !(rawUrl instanceof String)
                    || !(rawHeaders instanceof JSONObject)
                    || (rawBody != null && rawBody != JSONObject.NULL && !(rawBody instanceof String))) {
                throw new IllegalArgumentException();
            }
            JSONObject headers = (JSONObject) rawHeaders;
            if (headers.length() > MAX_HEADER_COUNT) throw new IllegalArgumentException();
            Map<String, String> values = new LinkedHashMap<String, String>();
            Iterator<String> names = headers.keys();
            while (names.hasNext()) {
                String name = names.next();
                Object value = headers.opt(name);
                if (!(value instanceof String)) throw new IllegalArgumentException();
                values.put(name, (String) value);
            }
            String body = rawBody instanceof String ? (String) rawBody : null;
            validated = RequestPolicy.validate((String) rawMethod, (String) rawUrl, values, body);
            if ("POST".equals(validated.method) && body != null) {
                // All current Hyundai recipes send a JSON object, never an arbitrary payload.
                object(body);
            }
        } catch (Exception rejected) {
            reject(id, INVALID);
            return;
        }

        final Job job = new Job(id, validated);
        synchronized (lock) {
            if (closed || active.containsKey(id)) return;
            if (active.size() >= 2) {
                callback.reply(id, null, BUSY);
                return;
            }
            active.put(id, job);
            try {
                job.deadline = deadlines.schedule(new Runnable() {
                    @Override public void run() { stop(job, TIMEOUT); }
                }, deadlineMillis, TimeUnit.MILLISECONDS);
                job.future = workers.submit(new Runnable() {
                    @Override public void run() { perform(job); }
                });
            } catch (RejectedExecutionException unavailable) {
                finish(job, null, BUSY);
            }
        }
    }

    private static boolean validId(String id) {
        return id.length() >= 1 && id.length() <= 128 && id.matches("[A-Za-z0-9_.:-]+");
    }

    private static JSONObject object(String input) throws Exception {
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < input.length(); i++) {
            char value = input.charAt(i);
            if (quote != 0) {
                if (escaped) escaped = false;
                else if (value == '\\') escaped = true;
                else if (value == quote) quote = 0;
            } else if (value == '"' || value == '\'') quote = value;
            else if (value == '{' || value == '[') {
                if (++depth > 64) throw new IllegalArgumentException();
            } else if (value == '}' || value == ']') --depth;
        }
        JSONTokener parser = new JSONTokener(input);
        Object parsed = parser.nextValue();
        if (!(parsed instanceof JSONObject) || parser.nextClean() != 0) throw new IllegalArgumentException();
        return (JSONObject) parsed;
    }

    private void reject(String id, String error) {
        if (id == null) return;
        synchronized (lock) {
            // A duplicate cannot replace or settle the request that already owns its id.
            if (!closed && !active.containsKey(id)) callback.reply(id, null, error);
        }
    }

    private boolean running(Job job) {
        synchronized (lock) { return !closed && active.get(job.id) == job; }
    }

    private void perform(Job job) {
        HttpsURLConnection connection = null;
        try {
            if (!running(job)) return;
            RequestPolicy.Validated request = job.request;
            if (request == null) return;
            // request.url was created by RequestPolicy after the exact-host/path checks.
            connection = connectionFactory.open(request.url);
            synchronized (lock) {
                if (closed || active.get(job.id) != job) return;
                job.connection = connection;
            }
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setDefaultUseCaches(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod(request.method);
            connection.setRequestProperty("Connection", "close");
            for (Map.Entry<String, String> header : request.headers.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            if ("POST".equals(request.method)) {
                byte[] body = (request.body == null ? "" : request.body).getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                // Streaming mode prevents automatic buffering and replay for auth/redirects.
                connection.setFixedLengthStreamingMode(body.length);
                if (!running(job)) return;
                try (OutputStream output = connection.getOutputStream()) {
                    if (!running(job)) return;
                    output.write(body);
                } finally {
                    java.util.Arrays.fill(body, (byte) 0);
                }
            }
            if (!running(job)) return;
            int status = connection.getResponseCode();
            if (status < 100 || status > 599) throw new InvalidResponseException();
            if (connection.getContentLength() > MAX_RESPONSE_BYTES) throw new ResponseLimitException();
            JSONObject headers = responseHeaders(connection.getHeaderFields());
            InputStream source = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String responseText = "";
            if (source != null) {
                try (InputStream input = source) { responseText = readResponse(job, input); }
            }
            if (!running(job)) return;
            JSONObject response = new JSONObject();
            response.put("status", status);
            response.put("text", responseText);
            response.put("headers", headers);
            finish(job, response, null);
        } catch (ResponseLimitException exceeded) {
            finish(job, null, TOO_LARGE);
        } catch (InvalidResponseException invalid) {
            finish(job, null, INVALID_RESPONSE);
        } catch (SocketTimeoutException timeout) {
            finish(job, null, TIMEOUT);
        } catch (SSLException tls) {
            finish(job, null, TLS);
        } catch (Exception failure) {
            finish(job, null, NETWORK);
        } finally {
            if (connection != null) connection.disconnect();
            synchronized (lock) { job.connection = null; job.request = null; }
        }
    }

    private static JSONObject responseHeaders(Map<String, List<String>> raw) throws Exception {
        JSONObject headers = new JSONObject();
        if (raw == null) return headers;
        int count = 0;
        int characters = 0;
        for (Map.Entry<String, List<String>> entry : raw.entrySet()) {
            String name = entry.getKey();
            if (name == null) continue; // HTTP status line.
            if (++count > MAX_HEADER_COUNT || name.length() > 256
                    || !name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) {
                throw new InvalidResponseException();
            }
            StringBuilder combined = new StringBuilder();
            if (entry.getValue() != null) {
                for (String value : entry.getValue()) {
                    if (value == null || value.length() > MAX_HEADER_VALUE_CHARS
                            || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
                        throw new InvalidResponseException();
                    }
                    if (combined.length() > 0) combined.append(", ");
                    combined.append(value);
                    if (combined.length() > MAX_HEADER_VALUE_CHARS) throw new InvalidResponseException();
                }
            }
            characters += name.length() + combined.length();
            if (characters > MAX_RESPONSE_HEADER_CHARS) throw new InvalidResponseException();
            headers.put(name, combined.toString());
        }
        return headers;
    }

    private String readResponse(Job job, InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(8192);
        byte[] chunk = new byte[8192];
        int size;
        while ((size = input.read(chunk)) != -1) {
            if (!running(job)) throw new IOException();
            if (bytes.size() > MAX_RESPONSE_BYTES - size) throw new ResponseLimitException();
            bytes.write(chunk, 0, size);
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    public void cancel(String id) {
        if (id == null) return;
        Job job;
        synchronized (lock) { job = active.get(id); }
        if (job != null) stop(job, CANCELLED);
    }

    private void stop(Job job, String error) {
        finish(job, null, error);
        HttpsURLConnection connection;
        Future<?> future;
        synchronized (lock) { connection = job.connection; future = job.future; }
        if (future != null) future.cancel(true);
        if (connection != null) connection.disconnect();
    }

    private void finish(Job job, JSONObject response, String error) {
        synchronized (lock) {
            if (active.get(job.id) != job) return;
            active.remove(job.id);
            job.request = null;
            if (job.deadline != null) job.deadline.cancel(false);
            if (!closed) callback.reply(job.id, response, error);
        }
    }

    @Override public void close() {
        List<Job> jobs;
        synchronized (lock) {
            if (closed) return;
            closed = true;
            jobs = new ArrayList<Job>(active.values());
            active.clear();
            for (Job job : jobs) {
                job.request = null;
                if (job.deadline != null) job.deadline.cancel(false);
                if (job.future != null) job.future.cancel(true);
            }
        }
        for (Job job : jobs) {
            HttpsURLConnection connection = job.connection;
            if (connection != null) connection.disconnect();
        }
        deadlines.shutdownNow();
        workers.shutdownNow();
    }

    private static final class Job {
        final String id;
        volatile RequestPolicy.Validated request;
        volatile HttpsURLConnection connection;
        Future<?> future;
        ScheduledFuture<?> deadline;
        Job(String id, RequestPolicy.Validated request) { this.id = id; this.request = request; }
    }

    private static final class ResponseLimitException extends IOException {
        private static final long serialVersionUID = 1L;
    }
    private static final class InvalidResponseException extends IOException {
        private static final long serialVersionUID = 1L;
    }
}
