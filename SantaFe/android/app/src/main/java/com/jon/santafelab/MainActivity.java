package com.jon.santafelab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.ServiceWorkerController;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Only the three installed HTML assets can access the native bridge. No remote page is loaded. */
public final class MainActivity extends Activity {
    static final String PAGE = "https://santafe.local/index.html";
    private static final int EXPORT = 301;
    private static final int MAX_EXPORT_BYTES = 8 * 1024 * 1024;
    private FrameLayout root;
    private WebView page;
    private NativeTransport transport;
    private AccountStore accountStore;
    private volatile boolean trustedPage;
    private boolean loaded;
    private byte[] pendingExport;
    private String exportMessage;
    private boolean exportSuccess;
    private boolean exportBusy;
    private final ExecutorService fileWriter = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(null); // Never restore form fields, WebView state, or authentication.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(Color.rgb(16, 25, 28));
        getWindow().setNavigationBarColor(Color.rgb(16, 25, 28));
        CookieHandler.setDefault(null);
        WebView.setWebContentsDebuggingEnabled(false);
        CookieManager.getInstance().setAcceptCookie(false);
        ServiceWorkerController.getInstance().getServiceWorkerWebSettings().setBlockNetworkLoads(true);
        ServiceWorkerController.getInstance().getServiceWorkerWebSettings().setAllowContentAccess(false);
        ServiceWorkerController.getInstance().getServiceWorkerWebSettings().setAllowFileAccess(false);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(16, 25, 28));
        root.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        setContentView(root);
        accountStore = new AccountStore(getApplicationContext());
    }

    @Override public void onStart() {
        super.onStart();
        if (page == null) createPage();
        page.resumeTimers();
        page.onResume();
    }

    @Override public void onStop() {
        // Preserve the live account/session when switching apps or saving a test log.
        // Tokens remain memory-only and disappear when this Activity is destroyed.
        if (page != null) { page.onPause(); page.pauseTimers(); }
        super.onStop();
    }

    @Override public void onDestroy() {
        destroyPage();
        pendingExport = null;
        fileWriter.shutdown();
        super.onDestroy();
    }

    @SuppressWarnings("deprecation") private void createPage() {
        final WebView view = new WebView(this);
        page = view;
        loaded = false;
        view.setBackgroundColor(Color.rgb(16, 25, 28));
        view.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true); // Sanitized logs only, never credentials.
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setBlockNetworkLoads(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSaveFormData(false);
        settings.setSavePassword(false);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, false);

        final NativeTransport nativeTransport = new NativeTransport((id, response, error) ->
            runOnUiThread(() -> {
                if (page != view || !trustedPage || !loaded) return;
                String code = "window.SantaFeAndroid&&window.SantaFeAndroid.onResponse(" +
                    JSONObject.quote(id) + "," + (response == null ? "null" : response.toString()) +
                    "," + (error == null ? "null" : JSONObject.quote(error)) + ")";
                view.evaluateJavascript(code, null);
            }));
        transport = nativeTransport;
        view.addJavascriptInterface(new Bridge(view, nativeTransport), "SantaFeNative");
        view.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView webView, String url, android.graphics.Bitmap icon) {
                trustedPage = page == view && PAGE.equals(url);
                loaded = false;
            }
            @Override public void onPageFinished(WebView webView, String url) {
                loaded = page == view && trustedPage && PAGE.equals(url);
                if (loaded) notifyExport();
            }
            @Override public boolean shouldOverrideUrlLoading(WebView webView, WebResourceRequest request) {
                return true; // Links, redirects, external apps and other pages are never opened here.
            }
            @Override public boolean shouldOverrideUrlLoading(WebView webView, String url) { return true; }
            @Override public WebResourceResponse shouldInterceptRequest(WebView webView, WebResourceRequest request) {
                return bundledAsset(request);
            }
            @Override public void onReceivedSslError(WebView webView, SslErrorHandler handler, SslError error) {
                handler.cancel();
            }
            @Override public boolean onRenderProcessGone(WebView webView, android.webkit.RenderProcessGoneDetail detail) {
                if (page == view) {
                    destroyPage();
                    Toast.makeText(MainActivity.this, "The tester closed. Reopen the app to start a new session.", Toast.LENGTH_LONG).show();
                    finish();
                }
                return true;
            }
        });
        view.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest request) { request.deny(); }
            @Override public boolean onJsConfirm(WebView webView, String url, String message, JsResult result) {
                if (page != view || !trustedPage) { result.cancel(); return true; }
                new AlertDialog.Builder(MainActivity.this).setMessage(message)
                    .setPositiveButton("Confirm", (dialog, which) -> result.confirm())
                    .setNegativeButton("Cancel", (dialog, which) -> result.cancel())
                    .setOnCancelListener(dialog -> result.cancel()).show();
                return true;
            }
            @Override public boolean onJsAlert(WebView webView, String url, String message, JsResult result) {
                if (page != view || !trustedPage) { result.cancel(); return true; }
                new AlertDialog.Builder(MainActivity.this).setMessage(message)
                    .setPositiveButton("OK", (dialog, which) -> result.confirm())
                    .setOnCancelListener(dialog -> result.cancel()).show();
                return true;
            }
        });
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));
        view.loadUrl(PAGE);
    }

    private void destroyPage() {
        trustedPage = false;
        loaded = false;
        if (transport != null) { transport.close(); transport = null; }
        if (page != null) {
            WebView old = page;
            page = null;
            old.removeJavascriptInterface("SantaFeNative");
            old.stopLoading();
            root.removeView(old);
            old.clearHistory();
            old.destroy();
        }
    }

    static WebResourceResponse blocked() {
        return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked",
            Collections.singletonMap("Cache-Control", "no-store"), new ByteArrayInputStream(new byte[0]));
    }

    private WebResourceResponse bundledAsset(WebResourceRequest request) {
        Uri uri = request.getUrl();
        if (!"GET".equals(request.getMethod()) || !"https".equals(uri.getScheme()) ||
            !"santafe.local".equals(uri.getEncodedAuthority()) || uri.getEncodedQuery() != null ||
            uri.getEncodedFragment() != null) return blocked();
        final String path = uri.getEncodedPath();
        String mime;
        if ("/index.html".equals(path)) mime = "text/html";
        else if ("/diagnostic.js".equals(path)) mime = "application/javascript";
        else if ("/diagnostic.css".equals(path)) mime = "text/css";
        else return blocked();
        try {
            InputStream stream = getAssets().open(path.substring(1));
            Map<String, String> headers = new HashMap<>();
            headers.put("Cache-Control", "no-store");
            headers.put("X-Content-Type-Options", "nosniff");
            headers.put("Referrer-Policy", "no-referrer");
            headers.put("Content-Security-Policy", "default-src 'none'; script-src 'self'; style-src 'self'; " +
                "img-src 'self' data:; connect-src 'none'; frame-src 'none'; worker-src 'none'; object-src 'none'; " +
                "base-uri 'none'; form-action 'none'; frame-ancestors 'none'");
            return new WebResourceResponse(mime, "UTF-8", 200, "OK", headers, stream);
        } catch (Exception ignored) { return blocked(); }
    }

    public final class Bridge {
        private final WebView view;
        private final NativeTransport connection;
        Bridge(WebView view, NativeTransport connection) { this.view = view; this.connection = connection; }
        private boolean trusted() { return trustedPage && page == view; }
        @JavascriptInterface public String getVersion() { return trusted() ? "1" : ""; }
        @JavascriptInterface public String loadAccount() {
            return trusted() ? accountStore.loadAccount() : "{\"saved\":false}";
        }
        @JavascriptInterface public boolean saveAccount(String json) {
            return trusted() && accountStore.saveAccount(json);
        }
        @JavascriptInterface public boolean forgetAccount() {
            return trusted() && accountStore.forgetAccount();
        }
        @JavascriptInterface public void request(String envelope) { if (trusted()) connection.request(envelope); }
        @JavascriptInterface public void cancel(String id) { if (trusted()) connection.cancel(id); }
        @JavascriptInterface public boolean exportLog(String json) {
            if (!trusted() || json == null || json.length() > MAX_EXPORT_BYTES) return false;
            final byte[] data;
            try {
                JSONObject report = new JSONObject(json);
                if (!"Santa Fe API Lab".equals(report.optString("app")) ||
                    !(report.opt("requests") instanceof JSONArray) || !(report.opt("evidence") instanceof JSONArray)) return false;
                data = json.getBytes(StandardCharsets.UTF_8);
                if (data.length > MAX_EXPORT_BYTES) return false;
            } catch (Exception ignored) { return false; }
            synchronized (MainActivity.this) {
                if (exportBusy) return false;
                exportBusy = true;
            }
            runOnUiThread(() -> {
                if (!trusted()) { synchronized (MainActivity.this) { exportBusy = false; } return; }
                pendingExport = data; // Only the already sanitized report, never a raw request or response.
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
                intent.putExtra(Intent.EXTRA_TITLE, "SantaFe-test-log-" + stamp + ".json");
                try { startActivityForResult(intent, EXPORT); }
                catch (Exception ignored) { pendingExport = null; finishExport(false, "No file picker is available. The log remains in the app."); }
            });
            return true;
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode != EXPORT) return;
        final byte[] data = pendingExport;
        pendingExport = null;
        if (resultCode != RESULT_OK || intent == null || intent.getData() == null || data == null) {
            finishExport(false, "Export cancelled. The log remains in the app.");
            return;
        }
        final Uri destination = intent.getData();
        fileWriter.execute(() -> {
            boolean success = false;
            try (OutputStream output = getContentResolver().openOutputStream(destination, "wt")) {
                if (output == null) throw new java.io.IOException();
                output.write(data);
                output.flush();
                success = true;
            } catch (Exception ignored) { /* Do not print provider paths or report contents. */ }
            final boolean saved = success;
            runOnUiThread(() -> finishExport(saved, saved ? "Sanitized log saved." : "Could not save the log. It remains in the app; try another location."));
        });
    }

    private void finishExport(boolean success, String message) {
        synchronized (this) { exportBusy = false; }
        exportSuccess = success;
        exportMessage = message;
        notifyExport();
    }

    private void notifyExport() {
        if (page == null || !trustedPage || !loaded || exportMessage == null) return;
        page.evaluateJavascript("window.SantaFeAndroid&&window.SantaFeAndroid.onExportResult(" +
            exportSuccess + "," + JSONObject.quote(exportMessage) + ")", null);
        exportMessage = null;
    }

    @Override public void onBackPressed() { finish(); }
}
