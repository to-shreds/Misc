package com.jon.santafelab;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;

/** Runs without a network or credentials. The injected fixtures are not live Hyundai verification. */
public final class NativeTransportTest {
    private static final String URL_ROOT = "https://api.telematics.hyundaiusa.com";
    private static final String STATUS = URL_ROOT + "/ac/v2/rcs/rvs/vehicleStatus";
    private static final String TOKEN = URL_ROOT + "/v2/ac/oauth/token";
    private static int tests;

    public static void main(String[] args) throws Exception {
        successAndConnectionSettings();
        enrollmentComparisonPreservesOnlyUrlDifference();
        postStreamingAndUtf8Body();
        errorAndRedirectResponses();
        rejectInvalidRequestsBeforeOpen();
        duplicateAndConcurrencyCap();
        cancelClearsJobAndSuppressesLateReply();
        deadlineDisconnectsAndDoesNotRetry();
        closePreventsStaleCallbacks();
        responseBounds();
        responseHeaderBounds();
        exceptionsNeverReflectSecrets();
        callbackReentryCanClose();
        System.out.println("NativeTransport: " + tests + " credential-free fixture checks passed.");
    }

    private static void successAndConnectionSettings() throws Exception {
        Fixture connection = new Fixture(200, "{\"ok\":true}");
        connection.headers.put("Content-Type", Collections.singletonList("application/json"));
        Factory factory = new Factory(connection);
        Replies replies = new Replies();
        try (NativeTransport transport = new NativeTransport(replies, factory, 2000)) {
            transport.request(envelope("get", "GET", STATUS, new JSONObject(), null));
            Reply result = replies.take();
            check(result.error == null && result.response.getInt("status") == 200, "GET status");
            check("{\"ok\":true}".equals(result.response.getString("text")), "GET body");
            check("application/json".equals(result.response.getJSONObject("headers").getString("Content-Type")), "GET headers");
            check(!connection.getInstanceFollowRedirects() && !connection.getUseCaches()
                    && !connection.getDefaultUseCaches(), "redirects and cache disabled");
            check(connection.getConnectTimeout() == 15000 && connection.getReadTimeout() == 30000, "socket timeouts");
            check("close".equals(connection.getRequestProperty("Connection")), "no keepalive header");
            check(!connection.getDoOutput() && connection.outputCalls == 0, "GET has no output body");
            check(factory.opens.get() == 1 && STATUS.equals(factory.lastUrl), "single validated destination open");
        }
    }

    private static void enrollmentComparisonPreservesOnlyUrlDifference() throws Exception {
        String prefix = URL_ROOT + "/ac/v2/enrollment/details/";
        String encodedUrl = prefix + "fixture%2Btag%40example.invalid";
        String literalUrl = prefix + "fixture%2Btag@example.invalid";
        String body = "{\"errorSubCode\":\"C500\",\"functionName\":\"getEnrollmentDetailsByUser\"}";
        Fixture encoded = new Fixture(502, body);
        Fixture literal = new Fixture(502, body);
        Factory factory = new Factory(encoded, literal);
        Replies replies = new Replies();
        JSONObject headers = new JSONObject().put("username", "fixture+tag@example.invalid")
                .put("accessToken", "synthetic-token").put("blueLinkServicePin", "0000")
                .put("Content-Type", "application/json;charset=UTF-8").put("Accept", "application/json")
                .put("Origin", URL_ROOT).put("Referer", URL_ROOT + "/login");
        try (NativeTransport transport = new NativeTransport(replies, factory, 2000)) {
            transport.request(envelope("encoded", "GET", encodedUrl, headers, null));
            Reply first = replies.take();
            check(first.error == null && first.response.getInt("status") == 502
                    && body.equals(first.response.getString("text")), "encoded enrollment response retained");
            check(encodedUrl.equals(factory.lastUrl) && factory.opens.get() == 1, "encoded enrollment exact URL");
            check(replies.queue.poll(100, TimeUnit.MILLISECONDS) == null && factory.opens.get() == 1,
                    "failed enrollment does not open comparison or retry automatically");

            transport.request(envelope("literal", "GET", literalUrl, headers, null));
            Reply second = replies.take();
            check(second.error == null && second.response.getInt("status") == 502
                    && body.equals(second.response.getString("text")), "literal-at enrollment response retained");
            check(literalUrl.equals(factory.lastUrl) && factory.opens.get() == 2, "literal-at enrollment exact URL");
            check("fixture+tag@example.invalid".equals(encoded.getRequestProperty("username"))
                    && "synthetic-token".equals(encoded.getRequestProperty("accessToken"))
                    && "0000".equals(encoded.getRequestProperty("blueLinkServicePin")),
                    "enrollment authenticated headers forwarded unchanged");
            check(encoded.getRequestProperties().equals(literal.getRequestProperties()),
                    "comparison headers identical across email URL forms");
            check(!encoded.getDoOutput() && !literal.getDoOutput()
                    && encoded.outputCalls == 0 && literal.outputCalls == 0,
                    "both enrollment comparisons remain body-free GET requests");
            check(replies.queue.poll(100, TimeUnit.MILLISECONDS) == null && factory.opens.get() == 2,
                    "only the two manually submitted enrollment requests were opened");
        }
    }

    private static void postStreamingAndUtf8Body() throws Exception {
        Fixture connection = new Fixture(200, "{}");
        Replies replies = new Replies();
        Factory factory = new Factory(connection);
        JSONObject headers = new JSONObject().put("content-type", "application/json");
        String body = "{\"username\":\"fixture@example.invalid\",\"password\":\"synthetic-π\"}";
        try (NativeTransport transport = new NativeTransport(replies, factory, 2000)) {
            transport.request(envelope("post", "POST", TOKEN, headers, body));
            check(replies.take().error == null, "POST success");
            check(connection.getDoOutput() && connection.fixedLength == body.getBytes(StandardCharsets.UTF_8).length, "UTF8 fixed-length POST");
            check(body.equals(new String(connection.output.toByteArray(), StandardCharsets.UTF_8)), "POST exact fixture body");
            check(connection.outputCalls == 1 && factory.opens.get() == 1, "POST no application replay");
            check("application/json".equals(connection.getRequestProperty("content-type")), "allowlisted headers forwarded");
        }
    }

    private static void errorAndRedirectResponses() throws Exception {
        for (int status : new int[]{302, 401, 429, 500}) {
            Fixture connection = new Fixture(status, "fixture response");
            connection.headers.put("Location", Collections.singletonList("https://not-hyundai.invalid/"));
            Factory factory = new Factory(connection);
            Replies replies = new Replies();
            try (NativeTransport transport = new NativeTransport(replies, factory, 2000)) {
                transport.request(envelope("status" + status, "GET", STATUS, new JSONObject(), null));
                Reply result = replies.take();
                check(result.error == null && result.response.getInt("status") == status
                        && "fixture response".equals(result.response.getString("text"))
                        && factory.opens.get() == 1 && !connection.getInstanceFollowRedirects(), "HTTP " + status + " returned without follow/retry");
            }
        }
    }

    private static void rejectInvalidRequestsBeforeOpen() throws Exception {
        Factory factory = new Factory(new Fixture(200, "{}"));
        Replies replies = new Replies();
        try (NativeTransport transport = new NativeTransport(replies, factory, 2000)) {
            String[] invalid = {
                envelope("outside", "GET", "https://not-hyundai.invalid/ac/v2/rcs/rvs/vehicleStatus", new JSONObject(), null),
                envelope("http", "GET", STATUS.replace("https:", "http:"), new JSONObject(), null),
                envelope("path", "GET", URL_ROOT + "/not-allowed", new JSONObject(), null),
                envelope("method", "DELETE", STATUS, new JSONObject(), null),
                envelope("getbody", "GET", STATUS, new JSONObject(), "{}"),
                envelope("headers", "GET", STATUS, new JSONObject().put("Cookie", "fixture"), null),
                envelope("headertype", "GET", STATUS, new JSONObject().put("accept", 5), null),
                envelope("bodytype", "POST", TOKEN, new JSONObject(), new JSONObject()),
                envelope("arraybody", "POST", TOKEN, new JSONObject(), "[]"),
                envelope("junkbody", "POST", TOKEN, new JSONObject(), "{} extra"),
                envelope("caseheaders", "GET", STATUS, new JSONObject().put("Accept", "a").put("accept", "b"), null),
                envelope("injection", "GET", STATUS, new JSONObject().put("accept", "a\r\nCookie: fixture"), null),
                "{\"id\":\"badrequest\",\"request\":true}",
                "{\"id\":\"badmethodtype\",\"request\":{\"method\":3,\"url\":\"" + STATUS + "\",\"headers\":{}}}"
            };
            for (String value : invalid) {
                transport.request(value);
                Reply result = replies.take();
                check(result.response == null && result.error.contains("invalid request"), "invalid request rejected");
            }
            check(factory.opens.get() == 0, "all invalid inputs rejected before connection factory");
            transport.request("malformed");
            transport.request(envelope("illegal\nidentifier", "GET", STATUS, new JSONObject(), null));
            transport.request(envelope("oversized", "GET", STATUS, new JSONObject().put("accept", repeat('π', 40000)), null));
            transport.request(envelope("trailing", "GET", STATUS, new JSONObject(), null) + "extra");
            check(replies.queue.poll(150, TimeUnit.MILLISECONDS) == null && factory.opens.get() == 0, "uncorrelatable/malformed envelopes discarded without open");
        }
    }

    private static void duplicateAndConcurrencyCap() throws Exception {
        Fixture first = new Fixture(200, "{}"); first.block = true;
        Fixture second = new Fixture(200, "{}"); second.block = true;
        Factory factory = new Factory(first, second);
        Replies replies = new Replies();
        try (NativeTransport transport = new NativeTransport(replies, factory, 2000)) {
            String request = envelope("first", "GET", STATUS, new JSONObject(), null);
            transport.request(request); first.awaitEntered();
            transport.request(request);
            transport.request("{\"id\":\"first\",\"request\":true}");
            check(replies.queue.poll(100, TimeUnit.MILLISECONDS) == null && factory.opens.get() == 1, "duplicates cannot open or settle an active id");
            transport.request(envelope("second", "GET", STATUS, new JSONObject(), null)); second.awaitEntered();
            transport.request(envelope("third", "GET", STATUS, new JSONObject(), null));
            Reply capped = replies.take();
            check("third".equals(capped.id) && capped.error.contains("Two requests") && factory.opens.get() == 2, "concurrency cap two");
            first.release.countDown(); second.release.countDown();
            check(replies.take().error == null && replies.take().error == null, "active requests survive duplicate and cap rejection");
        }
    }

    private static void cancelClearsJobAndSuppressesLateReply() throws Exception {
        Fixture fixture = new Fixture(200, "{}"); fixture.block = true;
        Replies replies = new Replies();
        Factory factory = new Factory(fixture);
        try (NativeTransport transport = new NativeTransport(replies, factory, 2000)) {
            transport.request(envelope("cancel", "GET", STATUS, new JSONObject(), null)); fixture.awaitEntered();
            transport.cancel("cancel");
            Reply cancelled = replies.take();
            check(cancelled.error.contains("Stopped locally"), "cancel fixed outcome message");
            awaitDisconnect(fixture);
            fixture.release.countDown();
            check(replies.queue.poll(150, TimeUnit.MILLISECONDS) == null && factory.opens.get() == 1, "cancel disconnects without stale callback or retry");
        }
    }

    private static void deadlineDisconnectsAndDoesNotRetry() throws Exception {
        Fixture fixture = new Fixture(200, "{}"); fixture.block = true;
        Replies replies = new Replies();
        Factory factory = new Factory(fixture);
        try (NativeTransport transport = new NativeTransport(replies, factory, 80)) {
            transport.request(envelope("timeout", "GET", STATUS, new JSONObject(), null)); fixture.awaitEntered();
            check(replies.take().error.contains("timed out"), "absolute deadline");
            awaitDisconnect(fixture);
            check(replies.queue.poll(150, TimeUnit.MILLISECONDS) == null && factory.opens.get() == 1, "deadline disconnects without retry or stale reply");
        }
    }

    private static void closePreventsStaleCallbacks() throws Exception {
        Fixture fixture = new Fixture(200, "{}"); fixture.block = true;
        Replies replies = new Replies();
        Factory factory = new Factory(fixture);
        NativeTransport transport = new NativeTransport(replies, factory, 100);
        transport.request(envelope("close", "GET", STATUS, new JSONObject(), null)); fixture.awaitEntered();
        transport.close(); transport.close();
        transport.request(envelope("afterclose", "GET", STATUS, new JSONObject(), null));
        awaitDisconnect(fixture);
        check(replies.queue.poll(200, TimeUnit.MILLISECONDS) == null && factory.opens.get() == 1, "closed transport suppresses callbacks and new requests");
    }

    private static void responseBounds() throws Exception {
        for (int bytes : new int[]{1048576, 1048577}) {
            Fixture fixture = new Fixture(200, repeat('x', bytes));
            fixture.contentLength = -1;
            Replies replies = new Replies();
            try (NativeTransport transport = new NativeTransport(replies, new Factory(fixture), 2000)) {
                transport.request(envelope("size" + bytes, "GET", STATUS, new JSONObject(), null));
                Reply result = replies.take();
                check(bytes == 1048576 ? result.error == null && result.response.getString("text").length() == bytes
                        : result.response == null && result.error.contains("exceeded"), "stream byte limit " + bytes);
            }
        }
        Fixture fixture = new Fixture(200, "small"); fixture.contentLength = 1048577;
        Replies replies = new Replies();
        try (NativeTransport transport = new NativeTransport(replies, new Factory(fixture), 2000)) {
            transport.request(envelope("contentlength", "GET", STATUS, new JSONObject(), null));
            check(replies.take().error.contains("exceeded") && fixture.inputCalls == 0, "declared oversize response rejected before read");
        }
    }

    private static void responseHeaderBounds() throws Exception {
        for (int variant = 0; variant < 4; variant++) {
            Fixture fixture = new Fixture(200, "{}");
            if (variant == 0) fixture.headers.put("injected\r\nheader", Collections.singletonList("fixture"));
            if (variant == 1) fixture.headers.put("safe", Collections.singletonList("value\nfixture"));
            if (variant == 2) fixture.headers.put("safe", Collections.singletonList(repeat('x', 4097)));
            if (variant == 3) for (int i = 0; i < 65; i++) fixture.headers.put("header" + i, Collections.singletonList("fixture"));
            Replies replies = new Replies();
            try (NativeTransport transport = new NativeTransport(replies, new Factory(fixture), 2000)) {
                transport.request(envelope("header" + variant, "GET", STATUS, new JSONObject(), null));
                check(replies.take().error.contains("could not read"), "response header bound " + variant);
            }
        }
    }

    private static void exceptionsNeverReflectSecrets() throws Exception {
        IOException[] exceptions = {
            new IOException("synthetic-password-in-exception"),
            new SSLHandshakeException("synthetic-password-in-exception"),
            new SocketTimeoutException("synthetic-password-in-exception")
        };
        for (IOException error : exceptions) {
            Fixture fixture = new Fixture(200, "{}"); fixture.failure = error;
            Replies replies = new Replies();
            Factory factory = new Factory(fixture);
            try (NativeTransport transport = new NativeTransport(replies, factory, 2000)) {
                transport.request(envelope("failure", "GET", STATUS, new JSONObject(), null));
                Reply result = replies.take();
                check(result.response == null && result.error != null && !result.error.contains("synthetic-password")
                        && factory.opens.get() == 1, "exception message never disclosed or retried");
            }
        }
    }

    private static void callbackReentryCanClose() throws Exception {
        final NativeTransport[] holder = new NativeTransport[1];
        final CountDownLatch closed = new CountDownLatch(1);
        holder[0] = new NativeTransport(new NativeTransport.Callback() {
            @Override public void reply(String id, JSONObject response, String error) {
                holder[0].close(); closed.countDown();
            }
        }, new Factory(new Fixture(200, "{}")), 2000);
        holder[0].request(envelope("reentry", "GET", STATUS, new JSONObject(), null));
        check(closed.await(2, TimeUnit.SECONDS), "callback close reentry does not deadlock");
    }

    private static String envelope(String id, String method, String url, JSONObject headers, Object body) throws Exception {
        JSONObject request = new JSONObject().put("method", method).put("url", url).put("headers", headers)
                .put("body", body == null ? JSONObject.NULL : body);
        return new JSONObject().put("id", id).put("request", request).toString();
    }

    private static String repeat(char value, int count) {
        char[] values = new char[count]; java.util.Arrays.fill(values, value); return new String(values);
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        tests++;
    }

    private static void awaitDisconnect(Fixture fixture) throws Exception {
        if (!fixture.disconnected.await(2, TimeUnit.SECONDS)) throw new AssertionError("connection was not disconnected");
    }

    private static final class Reply {
        final String id; final JSONObject response; final String error;
        Reply(String id, JSONObject response, String error) { this.id = id; this.response = response; this.error = error; }
    }

    private static final class Replies implements NativeTransport.Callback {
        final BlockingQueue<Reply> queue = new LinkedBlockingQueue<Reply>();
        @Override public void reply(String id, JSONObject response, String error) { queue.add(new Reply(id, response, error)); }
        Reply take() throws Exception {
            Reply result = queue.poll(3, TimeUnit.SECONDS);
            if (result == null) throw new AssertionError("missing transport reply");
            return result;
        }
    }

    private static final class Factory implements NativeTransport.ConnectionFactory {
        final AtomicInteger opens = new AtomicInteger();
        final Fixture[] fixtures;
        volatile String lastUrl;
        Factory(Fixture... fixtures) { this.fixtures = fixtures; }
        @Override public HttpsURLConnection open(URL url) throws IOException {
            lastUrl = url.toExternalForm();
            int index = opens.getAndIncrement();
            if (index >= fixtures.length) throw new IOException("Unexpected fixture open");
            return fixtures[index];
        }
    }

    private static final class Fixture extends HttpsURLConnection {
        final int status;
        final byte[] body;
        final Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch disconnected = new CountDownLatch(1);
        volatile boolean block;
        volatile IOException failure;
        volatile int contentLength;
        volatile int outputCalls;
        volatile int inputCalls;
        volatile int fixedLength = -1;
        Fixture(int status, String body) throws Exception {
            super(new URL(STATUS)); this.status = status;
            this.body = body.getBytes(StandardCharsets.UTF_8); this.contentLength = this.body.length;
        }
        @Override public String getCipherSuite() { return "fixture"; }
        @Override public Certificate[] getLocalCertificates() { return null; }
        @Override public Certificate[] getServerCertificates() { return new Certificate[0]; }
        @Override public void connect() { connected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void disconnect() { disconnected.countDown(); release.countDown(); }
        @Override public void setFixedLengthStreamingMode(int length) { super.setFixedLengthStreamingMode(length); fixedLength = length; }
        @Override public OutputStream getOutputStream() { outputCalls++; return output; }
        @Override public int getResponseCode() throws IOException {
            entered.countDown();
            if (block) {
                try { release.await(3, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException("fixture interrupted"); }
            }
            if (failure != null) throw failure;
            return status;
        }
        @Override public Map<String, List<String>> getHeaderFields() { return headers; }
        @Override public int getContentLength() { return contentLength; }
        @Override public InputStream getInputStream() { inputCalls++; return new ByteArrayInputStream(body); }
        @Override public InputStream getErrorStream() { inputCalls++; return new ByteArrayInputStream(body); }
        void awaitEntered() throws Exception {
            if (!entered.await(2, TimeUnit.SECONDS)) throw new AssertionError("fixture request did not start");
        }
    }
}
