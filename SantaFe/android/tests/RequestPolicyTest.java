package com.jon.santafelab;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Standalone adversarial tests. Synthetic inputs only; no network or account is used. */
public final class RequestPolicyTest {
    private static final String BASE = RequestPolicy.ORIGIN;
    private static int checks;

    public static void main(String[] args) {
        acceptedRecipes();
        rejectedDestinations();
        rejectedPathsAndMethods();
        rejectedEnrollmentIdentifiers();
        checkedHeadersAndBody();
        immutableValidatedRequest();
        System.out.println("RequestPolicy: " + checks + " checks passed; no network requests made.");
    }

    private static void acceptedRecipes() {
        accepted("POST", "/v2/ac/oauth/token", "{\"username\":\"test@example.invalid\",\"password\":\"synthetic\"}");
        accepted("GET", "/v2/ac/oauth/token", null);
        accepted("GET", "/ac/v2/enrollment/details/test.user%2Btag%40example.invalid", null);
        accepted("GET", "/ac/v2/enrollment/details/test%40example.invalid", null);
        accepted("GET", "/ac/v2/enrollment/details/m%C3%BCller%40example.invalid", null);
        accepted("GET", "/ac/v2/enrollment/details/test.user%2Btag@example.invalid", null);
        accepted("GET", "/ac/v2/enrollment/details/test@example.invalid", null);
        accepted("GET", "/ac/v2/enrollment/details/m%C3%BCller@example.invalid", null);
        accepted("GET", "/ac/v2/rcs/rvs/vehicleStatus", null);
        accepted("GET", "/ac/v2/rmt/getRunningStatus", null);
        for (String endpoint : new String[]{"rcs/rdo/off", "rcs/rdo/on", "rcs/rhl/light", "rcs/rhl/hnl",
                "rcs/rsc/start", "rcs/rsc/stop", "evc/fatc/start", "evc/fatc/stop"}) {
            accepted("POST", "/ac/v2/" + endpoint, endpoint.endsWith("stop") ? null : "{}");
        }
        Map<String, String> recipe = new LinkedHashMap<String, String>();
        recipe.put("Content-Type", "application/json;charset=UTF-8");
        recipe.put("Accept", "application/json, text/plain, */*");
        recipe.put("from", "SPA"); recipe.put("to", "ISS"); recipe.put("language", "0");
        recipe.put("offset", "-4"); recipe.put("refresh", "false"); recipe.put("encryptFlag", "false");
        recipe.put("brandIndicator", "H"); recipe.put("client_id", "synthetic");
        recipe.put("clientSecret", "synthetic"); recipe.put("Origin", BASE); recipe.put("Referer", BASE + "/login");
        recipe.put("username", "test@example.invalid"); recipe.put("accessToken", "synthetic-token");
        recipe.put("blueLinkServicePin", "0000"); recipe.put("registrationId", "synthetic-registration");
        recipe.put("gen", "2"); recipe.put("vin", "SYNTHETIC"); recipe.put("APPCLOUD-VIN", "SYNTHETIC");
        recipe.put("tid", "synthetic-transaction"); recipe.put("login_id", "test@example.invalid");
        recipe.put("service_type", "synthetic-service");
        RequestPolicy.Validated spec = RequestPolicy.validate("POST", BASE + "/ac/v2/rcs/rdo/off", recipe, "{}");
        check(spec.headers.size() == recipe.size(), "All supported recipe headers retained");
    }

    private static void rejectedDestinations() {
        String path = "/v2/ac/oauth/token";
        String[] urls = {"http://api.telematics.hyundaiusa.com" + path,
                "HTTPS://api.telematics.hyundaiusa.com" + path,
                "https://API.TELEMATICS.HYUNDAIUSA.COM" + path,
                "https://api.telematics.hyundaiusa.com:443" + path,
                "https://api.telematics.hyundaiusa.com:8443" + path,
                "https://api.telematics.hyundaiusa.com." + path,
                "https://api.telematics.hyundaiusa.com.evil.invalid" + path,
                "https://evil.invalid@api.telematics.hyundaiusa.com" + path,
                "https://api.telematics.hyundaiusa.com@evil.invalid" + path,
                "https://api%2Etelematics.hyundaiusa.com" + path,
                "https://127.0.0.1" + path, "file:///v2/ac/oauth/token", "//api.telematics.hyundaiusa.com" + path,
                BASE + path + "?next=https://evil.invalid", BASE + path + "?", BASE + path + "#", BASE + path + "#secret",
                " " + BASE + path, BASE + path + "\n", BASE + path + "\u0000"};
        for (String url : urls) rejected("POST", url, empty(), "{}");
        rejected("POST", null, empty(), "{}");
        rejected("POST", BASE + repeat("a", 4096), empty(), "{}");
    }

    private static void rejectedPathsAndMethods() {
        for (String path : new String[]{"/", "/v2/ac/oauth/token/", "/v2/ac/oauth/../oauth/token",
                "/v2/ac/oauth/%74oken", "/v2/ac/oauth//token", "/ac/v2/rmt/unknown", "/ac/v2/rcs/rdo/off/extra"}) {
            rejected("POST", BASE + path, empty(), "{}");
        }
        for (String method : new String[]{null, "get", "post", " POST", "POST ", "DELETE", "CONNECT", "HEAD", "POST\r\n"}) {
            rejected(method, BASE + "/v2/ac/oauth/token", empty(), "{}");
        }
        rejected("POST", BASE + "/ac/v2/rcs/rvs/vehicleStatus", empty(), "{}");
        rejected("GET", BASE + "/ac/v2/rcs/rdo/on", empty(), null);
        rejected("POST", BASE + "/ac/v2/enrollment/details/test%40example.invalid", empty(), "{}");
        rejected("POST", BASE + "/ac/v2/enrollment/details/test@example.invalid", empty(), "{}");
    }

    private static void rejectedEnrollmentIdentifiers() {
        String prefix = BASE + "/ac/v2/enrollment/details/";
        for (String identifier : new String[]{"", ".", "..", "%74est%40example.invalid",
                "test%40example.invalid/extra", "test%40example.invalid%2Fextra", "test%40example.invalid%2fextra",
                "test%40example.invalid%5Cextra", "test%40example.invalid%252Fextra",
                "%2e%2e%2ftest%40example.invalid", "test%40example.invalid%00", "test%40example.invalid%0A",
                "test%20user%40example.invalid", "test%C2%A0user%40example.invalid", "test%40", "%40example.invalid",
                "test%40example%40invalid", "test%40example.invalid%", "test%40example.invalid%Q0",
                "test%40example.invalid%C0%AF", "test%40example.invalid%ED%A0%80", "test%40example.invalid%C3"}) {
            rejected("GET", prefix + identifier, empty(), null);
        }
        for (String identifier : new String[]{"test+tag@example.invalid", "test+tag%40example.invalid",
                "test%2btag@example.invalid", "%74est@example.invalid", "test%2Euser@example.invalid",
                "test@@example.invalid", "test%40@example.invalid", "test@%40example.invalid",
                "test%2540example.invalid", "test@example.invalid/extra", "test@example.invalid%2Fextra",
                "test@example.invalid%5Cextra", "test@example.invalid%252Fextra", "test@example.invalid%00",
                "test@example.invalid%0A", "test%20user@example.invalid", "test%C2%A0user@example.invalid",
                "test@example.invalid?extra", "test@example.invalid#extra", "test@example.invalid%Q0"}) {
            rejected("GET", prefix + identifier, empty(), null);
        }
    }

    private static void checkedHeadersAndBody() {
        String url = BASE + "/v2/ac/oauth/token";
        for (String name : new String[]{"Host", "Cookie", "Authorization", "Proxy-Authorization", "Connection",
                "Content-Length", "Transfer-Encoding", "X-Forwarded-Host", "accessToken\r\n", " accessToken"}) {
            rejected("POST", url, header(name, "synthetic"), "{}");
        }
        for (String value : new String[]{"abc\r\nInjected: yes", "abc\n", "abc\r", "abc\u0000", "abc\t",
                "abc\u001f", "abc\u007f", "abc\u0085", repeat("a", 4097)}) {
            rejected("POST", url, header("accessToken", value), "{}");
        }
        rejected("POST", url, header(null, "synthetic"), "{}");
        rejected("POST", url, header("accessToken", null), "{}");
        rejected("POST", url, null, "{}");
        rejected("POST", url, header("Origin", "https://evil.invalid"), "{}");
        rejected("POST", url, header("Referer", BASE + "/other"), "{}");
        Map<String, String> duplicate = header("accessToken", "first");
        duplicate.put("ACCESSTOKEN", "second"); rejected("POST", url, duplicate, "{}");
        Map<String, String> large = empty();
        for (String name : new String[]{"accessToken", "clientSecret", "username", "registrationId"}) {
            large.put(name, repeat("a", 4096));
        }
        rejected("POST", url, large, "{}");
        rejected("GET", url, empty(), "");
        rejected("GET", url, empty(), "{}");
        rejected("POST", url, empty(), repeat("a", 8193));
        rejected("POST", url, empty(), repeat("\u20ac", 2731));
        RequestPolicy.validate("POST", url, header("accessToken", repeat("a", 4096)), repeat("a", 8192));
        checks++;
    }

    private static void immutableValidatedRequest() {
        Map<String, String> source = header("accessToken", "original");
        RequestPolicy.Validated spec = RequestPolicy.validate("POST", BASE + "/v2/ac/oauth/token", source, "{}");
        source.put("accessToken", "changed"); source.put("Host", "evil.invalid");
        check("original".equals(spec.headers.get("accessToken")) && !spec.headers.containsKey("Host"), "Defensive header copy");
        try { spec.headers.put("Host", "evil.invalid"); throw new AssertionError("Mutable validated headers"); }
        catch (UnsupportedOperationException expected) { checks++; }
        check("POST".equals(spec.method) && BASE.concat("/v2/ac/oauth/token").equals(spec.url.toExternalForm())
                && "{}".equals(spec.body), "Validated fields preserve approved recipe");
    }

    private static void accepted(String method, String path, String body) {
        RequestPolicy.validate(method, BASE + path, empty(), body); checks++;
    }
    private static void rejected(String method, String url, Map<String, String> headers, String body) {
        try { RequestPolicy.validate(method, url, headers, body); throw new AssertionError("Rejected input was accepted"); }
        catch (IllegalArgumentException expected) {
            // Native errors must not echo secrets from rejected URLs, bodies, or headers.
            check(!expected.getMessage().contains("synthetic") && !expected.getMessage().contains("example.invalid"), "Safe validation error");
        }
    }
    private static Map<String, String> empty() { return new LinkedHashMap<String, String>(); }
    private static Map<String, String> header(String key, String value) {
        Map<String, String> result = empty(); result.put(key, value); return result;
    }
    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(); for (int i = 0; i < count; i++) result.append(value); return result.toString();
    }
    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message); checks++;
    }
}
