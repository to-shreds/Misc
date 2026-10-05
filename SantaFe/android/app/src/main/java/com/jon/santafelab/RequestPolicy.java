package com.jon.santafelab;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The only outbound destinations the bundled API Lab may request. No Android dependency. */
public final class RequestPolicy {
    public static final String ORIGIN = "https://api.telematics.hyundaiusa.com";
    private static final String AUTHORITY = "api.telematics.hyundaiusa.com";
    private static final String ENROLLMENT = "/ac/v2/enrollment/details/";
    private static final int MAX_URL_CHARS = 4096;
    private static final int MAX_BODY_BYTES = 8192;
    private static final int MAX_HEADER_VALUE_CHARS = 4096;
    private static final int MAX_TOTAL_HEADER_CHARS = 16384;
    private static final Map<String, Set<String>> ENDPOINTS;
    private static final Set<String> HEADER_NAMES;

    static {
        Map<String, Set<String>> paths = new HashMap<String, Set<String>>();
        paths.put("/v2/ac/oauth/token", methods("GET", "POST"));
        paths.put("/ac/v2/rcs/rvs/vehicleStatus", methods("GET"));
        paths.put("/ac/v2/rmt/getRunningStatus", methods("GET"));
        String[] commands = {"rcs/rdo/off", "rcs/rdo/on", "rcs/rhl/light", "rcs/rhl/hnl",
                "rcs/rsc/start", "rcs/rsc/stop", "evc/fatc/start", "evc/fatc/stop"};
        for (String command : commands) paths.put("/ac/v2/" + command, methods("POST"));
        ENDPOINTS = Collections.unmodifiableMap(paths);
        HEADER_NAMES = methods("content-type", "accept", "from", "to", "language", "offset",
                "refresh", "encryptflag", "brandindicator", "client_id", "clientsecret",
                "origin", "referer", "username", "accesstoken", "bluelinkservicepin",
                "registrationid", "gen", "vin", "appcloud-vin", "tid", "login_id", "service_type");
    }

    private RequestPolicy() { }

    public static final class Validated {
        public final URL url;
        public final String method;
        public final Map<String, String> headers;
        public final String body;

        private Validated(URL url, String method, Map<String, String> headers, String body) {
            this.url = url;
            this.method = method;
            this.headers = Collections.unmodifiableMap(headers);
            this.body = body;
        }
    }

    /** Reject before opening a connection. Error messages deliberately exclude input values. */
    public static Validated validate(String method, String url, Map<String, String> headers,
                                     String body) throws IllegalArgumentException {
        if (!"GET".equals(method) && !"POST".equals(method)) reject("Unsupported request method.");
        if (url == null || url.length() > MAX_URL_CHARS) reject("Invalid request URL.");
        final URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException failure) {
            throw new IllegalArgumentException("Invalid request URL.");
        }
        if (!"https".equals(uri.getScheme()) || !AUTHORITY.equals(uri.getRawAuthority())
                || uri.getUserInfo() != null || uri.getPort() != -1
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !url.equals(uri.toASCIIString())) {
            reject("Request destination is outside the Hyundai allowlist.");
        }
        String path = uri.getRawPath();
        Set<String> permitted = ENDPOINTS.get(path);
        boolean enrollment = path != null && path.startsWith(ENROLLMENT);
        if (permitted != null) {
            if (!permitted.contains(method)) reject("Method is not allowed for this endpoint.");
        } else if (enrollment && "GET".equals(method)) {
            validateEnrollment(path.substring(ENROLLMENT.length()));
        } else {
            reject("Request endpoint is outside the Hyundai allowlist.");
        }
        if (body != null) {
            if ("GET".equals(method)) reject("GET request bodies are not allowed.");
            if (body.length() > MAX_BODY_BYTES
                    || body.getBytes(StandardCharsets.UTF_8).length > MAX_BODY_BYTES) {
                reject("Request body is too large.");
            }
        }
        if (headers == null || headers.size() > HEADER_NAMES.size()) reject("Invalid request headers.");
        Map<String, String> copied = new LinkedHashMap<String, String>();
        Set<String> seen = new HashSet<String>();
        int total = 0;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            if (name == null || value == null) reject("Invalid request header.");
            String lower = name.toLowerCase(Locale.ROOT);
            if (!HEADER_NAMES.contains(lower) || !seen.add(lower)
                    || value.length() > MAX_HEADER_VALUE_CHARS) reject("Invalid request header.");
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c < 0x20 || c == 0x7f || (c >= 0x80 && c <= 0x9f)) {
                    reject("Invalid request header.");
                }
            }
            if ("origin".equals(lower) && !ORIGIN.equals(value)) reject("Invalid Origin header.");
            if ("referer".equals(lower) && !(ORIGIN + "/login").equals(value)) {
                reject("Invalid Referer header.");
            }
            total += name.length() + value.length();
            if (total > MAX_TOTAL_HEADER_CHARS) reject("Request headers are too large.");
            copied.put(name, value);
        }
        try {
            return new Validated(uri.toURL(), method, copied, body);
        } catch (java.net.MalformedURLException failure) {
            throw new IllegalArgumentException("Invalid request URL.");
        }
    }

    private static void validateEnrollment(String encoded) {
        if (encoded.length() == 0) reject("Invalid enrollment identifier.");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(encoded.length());
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            if (c == '%') {
                if (i + 2 >= encoded.length()) reject("Invalid enrollment identifier.");
                int high = Character.digit(encoded.charAt(++i), 16);
                int low = Character.digit(encoded.charAt(++i), 16);
                if (high < 0 || low < 0) reject("Invalid enrollment identifier.");
                bytes.write((high << 4) | low);
            } else {
                if (!unescapedComponent(c)) reject("Invalid enrollment identifier.");
                bytes.write(c);
            }
        }
        final String email;
        try {
            email = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        } catch (CharacterCodingException failure) {
            throw new IllegalArgumentException("Invalid enrollment identifier.");
        }
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1 || at != email.lastIndexOf('@')
                || ".".equals(email) || "..".equals(email)) reject("Invalid enrollment identifier.");
        for (int i = 0; i < email.length(); i++) {
            char c = email.charAt(i);
            // Reject separators, nested escapes, controls and whitespace before native URL handling.
            if (c == '/' || c == '\\' || c == '%' || Character.isISOControl(c)
                    || Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                reject("Invalid enrollment identifier.");
            }
        }
        if (!encoded.equals(encodeComponent(email))) reject("Noncanonical enrollment identifier.");
    }

    private static String encodeComponent(String input) {
        StringBuilder encoded = new StringBuilder();
        final char[] hex = "0123456789ABCDEF".toCharArray();
        for (byte item : input.getBytes(StandardCharsets.UTF_8)) {
            int value = item & 0xff;
            if (unescapedComponent((char) value)) encoded.append((char) value);
            else encoded.append('%').append(hex[value >>> 4]).append(hex[value & 0xf]);
        }
        return encoded.toString();
    }

    private static boolean unescapedComponent(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9') || "-_.!~*'()".indexOf(c) >= 0;
    }

    private static Set<String> methods(String... items) {
        Set<String> values = new HashSet<String>();
        Collections.addAll(values, items);
        return Collections.unmodifiableSet(values);
    }

    private static void reject(String message) {
        throw new IllegalArgumentException(message);
    }
}
