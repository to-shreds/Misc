package com.jon.santafelab.runtime;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import java.lang.reflect.Method;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Synthetic-account, no-network checks against the unmodified signed release app.
 * Run only on a disposable test device with no existing saved account. */
public final class RuntimeVerification extends Instrumentation {
    private int checks;
    private Activity activity;

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            activity = startActivitySync(new Intent().setClassName("com.jon.santafelab", "com.jon.santafelab.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            WebView page = waitForPage();
            JSONObject dashboard = inspect(page);
            require("https://santafe.local/dashboard.html".equals(dashboard.optString("href")), "dashboard is the installed start page");
            require("complete".equals(dashboard.optString("ready")), "dashboard HTML loaded");
            require("2".equals(dashboard.optString("bridgeVersion")), "dashboard native bridge trusted");
            require(dashboard.optBoolean("callback"), "dashboard callback registered");
            require("true".equals(evaluate(page, "Boolean(document.getElementById('doors')&&document.getElementById('engine')&&document.getElementById('fuel')&&document.getElementById('miles')&&document.getElementById('start')&&document.querySelector('[data-cmd=stop]'))")), "all compact dashboard controls present");
            final WebView dashboardView = page;
            runOnMainSync(() -> dashboardView.loadUrl("https://santafe.local/index.html"));
            page = waitForPage();
            JSONObject initial = inspect(page);
            long navigationDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while ((! "https://santafe.local/index.html".equals(initial.optString("href")) ||
                    !"complete".equals(initial.optString("ready")) || !initial.optBoolean("callback")) &&
                    System.nanoTime() < navigationDeadline) {
                Thread.sleep(200);
                initial = inspect(page);
            }
            require("https://santafe.local/index.html".equals(initial.optString("href")), "diagnostic page retained");
            require("complete".equals(initial.optString("ready")), "HTML loaded");
            require("2".equals(initial.optString("bridgeVersion")), "native bridge trusted");
            require(initial.optBoolean("callback"), "native callback registered");
            require(!initial.optBoolean("loginDisabled"), "Connect enabled in app");
            require(!initial.optBoolean("emailDisabled"), "credential input enabled in app");
            require(initial.optBoolean("setupHidden"), "extension setup hidden");
            require(initial.optBoolean("downloadHidden"), "APK download hidden in app");
            require("Direct Hyundai connection".equals(initial.optString("transport")), "native connection indicator");
            require("Signed out".equals(initial.optString("session")), "fresh session signed out");

            JSONObject originalAccount = new JSONObject(evaluate(page, "SantaFeNative.loadAccount()"));
            require(!originalAccount.optBoolean("saved"), "disposable test device has no existing saved account");
            require(!originalAccount.has("error"), "empty saved-account store readable");

            final WebView installed = page;
            runOnMainSync(() -> {
                WebSettings options = installed.getSettings();
                require(options.getBlockNetworkLoads(), "WebView network blocked");
                require(!options.getAllowFileAccess(), "file access blocked");
                require(!options.getAllowContentAccess(), "content access blocked");
                require(options.getMixedContentMode() == WebSettings.MIXED_CONTENT_NEVER_ALLOW, "mixed content blocked");
                require(!options.getJavaScriptCanOpenWindowsAutomatically(), "popups blocked");
                require((activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0, "screen capture blocked");
            });

            evaluate(page, "window.__invalidResponse=null; const saved=window.SantaFeAndroid; window.SantaFeAndroid={onResponse:(id,response,error)=>{window.__invalidResponse={id,response,error};saved.onResponse(id,response,error);},onExportResult:saved.onExportResult};SantaFeNative.request(JSON.stringify({id:'verification.invalid',request:{method:'GET',url:'https://example.invalid/stolen',headers:{},body:null}}));true");
            JSONObject invalid = waitForObject(page, "window.__invalidResponse");
            require("verification.invalid".equals(invalid.optString("id")), "invalid native request correlated");
            require(invalid.isNull("response"), "invalid native request has no response");
            require(invalid.optString("error").contains("No request was sent"), "invalid destination rejected before connection");

            evaluate(page, "document.getElementById('email').value='fixture@example.invalid';document.getElementById('password').value='DONT_SEND_FIXTURE_PASSWORD';document.getElementById('pin').value='1234';true");
            JSONObject entered = inspect(page);
            require(entered.optBoolean("hasPassword"), "fixture input populated without submitting");
            final WebView first = page;
            runOnMainSync(() -> {
                callActivityOnPause(activity);
                callActivityOnStop(activity);
                callActivityOnRestart(activity);
                callActivityOnStart(activity);
                callActivityOnResume(activity);
            });
            page = waitForPage();
            JSONObject reopened = inspect(page);
            require(page == first, "same WebView after ordinary background and return");
            require(reopened.optBoolean("hasPassword"), "password form preserved after ordinary background");
            require(reopened.optBoolean("hasEmail"), "email form preserved after ordinary background");
            require(reopened.optBoolean("hasPin"), "PIN form preserved after ordinary background");
            require("Signed out".equals(reopened.optString("session")), "ordinary background did not submit login");

            String fixture = "{\"username\":\"fixture@example.invalid\",\"password\":\"DONT_SEND_FIXTURE_PASSWORD\",\"pin\":\"1234\"}";
            require("true".equals(evaluate(page, "SantaFeNative.saveAccount(" + JSONObject.quote(fixture) + ")")), "native account save accepted synthetic credentials");
            JSONObject savedAccount = new JSONObject(evaluate(page, "SantaFeNative.loadAccount()"));
            require(savedAccount.optBoolean("saved"), "saved account readable");
            require("fixture@example.invalid".equals(savedAccount.optString("username")), "saved synthetic username matches");
            require("DONT_SEND_FIXTURE_PASSWORD".equals(savedAccount.optString("password")), "saved synthetic password matches");
            require("1234".equals(savedAccount.optString("pin")), "saved synthetic PIN matches");
            Context target = getTargetContext();
            verifyWidgetBindings(target);
            File stored = new File(target.getNoBackupFilesDir(), "saved-account-v1.bin");
            require(stored.isFile(), "encrypted record stored in no-backup directory");
            byte[] encrypted = Files.readAllBytes(stored.toPath());
            String raw = new String(encrypted, StandardCharsets.ISO_8859_1);
            require(!raw.contains("fixture@example.invalid"), "encrypted file does not contain plaintext username");
            require(!raw.contains("DONT_SEND_FIXTURE_PASSWORD"), "encrypted file does not contain plaintext password");
            require(!raw.contains("1234"), "encrypted file does not contain plaintext PIN");
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            require(keyStore.containsAlias("com.jon.santafelab.saved-account.v1"), "Android Keystore encryption key exists");
            require(keyStore.getKey("com.jon.santafelab.saved-account.v1", null).getEncoded() == null, "Android Keystore key cannot be exported");

            Activity oldActivity = activity;
            ActivityMonitor replacement = addMonitor("com.jon.santafelab.MainActivity", null, false);
            runOnMainSync(() -> { oldActivity.getIntent().putExtra("sf_open_diagnostics", true); oldActivity.recreate(); });
            Activity recreated = waitForMonitorWithTimeout(replacement, 15000);
            removeMonitor(replacement);
            require(recreated != null && recreated != oldActivity, "Activity recreated");
            activity = recreated;
            page = waitForPage();
            JSONObject restored = inspect(page);
            require(page != first, "Activity recreation creates a new WebView");
            require(restored.optBoolean("hasEmail"), "saved username restored after recreation");
            require(!restored.optBoolean("hasPassword"), "saved password remains hidden after recreation");
            require(!restored.optBoolean("hasPin"), "saved PIN remains hidden after recreation");
            require("Signed out".equals(restored.optString("session")), "restoring account did not log in automatically");
            require(!restored.optBoolean("accountOpen"), "saved account settings collapsed");
            require(restored.optBoolean("rememberChecked"), "saved account selected for reuse");
            String exported = evaluate(page, "JSON.stringify({storage: Object.fromEntries(Object.entries(localStorage)),body:document.body.textContent})");
            require(!exported.contains("DONT_SEND_FIXTURE_PASSWORD"), "saved password absent from page text and web storage");
            require(!exported.contains("1234"), "saved PIN absent from page text and web storage");

            Files.write(stored.toPath(), new byte[] {1, 2, 3, 4, 5});
            JSONObject corrupt = new JSONObject(evaluate(page, "SantaFeNative.loadAccount()"));
            require(!corrupt.optBoolean("saved"), "corrupted encrypted record never returns credentials");
            require("Could not read the saved account. Save it again in Settings.".equals(corrupt.optString("error")), "corrupted saved account returns generic message");
            require("true".equals(evaluate(page, "SantaFeNative.forgetAccount()")), "forget removes corrupted saved account");
            require(!stored.exists(), "forgotten encrypted record removed");
            require(!new File(stored.getPath() + ".bak").exists(), "forgotten backup record removed");
            require(!new File(stored.getPath() + ".new").exists(), "forgotten temporary record removed");
            keyStore.load(null);
            require(!keyStore.containsAlias("com.jon.santafelab.saved-account.v1"), "forgotten Keystore key removed");
            JSONObject forgotten = new JSONObject(evaluate(page, "SantaFeNative.loadAccount()"));
            require(!forgotten.optBoolean("saved") && !forgotten.has("error"), "forgotten account is empty and readable");

            result.putString("result", "PASS");
            result.putInt("checks", checks);
            result.putString("scope", "Production signed APK on Android " + android.os.Build.VERSION.RELEASE +
                " (API " + android.os.Build.VERSION.SDK_INT + "); no real credentials or Hyundai calls.");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("result", "FAIL");
            result.putInt("checks", checks);
            result.putString("error", failure.toString());
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    /** Real framework PendingIntents and JobScheduler, with no widget tap or Hyundai requests. */
    private void verifyWidgetBindings(Context target) throws Exception {
        Class<?> widget=target.getClassLoader().loadClass("com.jon.santafelab.SantaFeWidgetProvider");
        Method open=widget.getDeclaredMethod("open",Context.class,String.class,int.class,int.class);
        Method refresh=widget.getDeclaredMethod("refresh",Context.class,int.class);
        open.setAccessible(true);
        refresh.setAccessible(true);
        PendingIntent dashboard=(PendingIntent)open.invoke(null,target,"dashboard",15001,1);
        PendingIntent dashboardAgain=(PendingIntent)open.invoke(null,target,"dashboard",15001,1);
        PendingIntent lock=(PendingIntent)open.invoke(null,target,"lock",15001,3);
        PendingIntent otherLock=(PendingIntent)open.invoke(null,target,"lock",15002,3);
        PendingIntent reload=(PendingIntent)refresh.invoke(null,target,15001);
        PendingIntent otherReload=(PendingIntent)refresh.invoke(null,target,15002);
        require(dashboard.isActivity()&&lock.isActivity(),"widget launch controls are activity intents");
        require(reload.isBroadcast(),"widget refresh is a native explicit broadcast");
        require(dashboard.isImmutable()&&lock.isImmutable()&&reload.isImmutable(),"widget intents are immutable");
        require("com.jon.santafelab".equals(dashboard.getCreatorPackage()),"widget uses the installed app identity");
        require(dashboard.equals(dashboardAgain),"widget same-ID rebind preserves existing PendingIntent");
        require(!dashboard.equals(lock),"open and vehicle action controls remain distinct");
        require(!lock.equals(otherLock),"different widget instances have independent command controls");
        require(!reload.equals(otherReload),"different widget instances have independent refresh controls");

        Class<?> status=target.getClassLoader().loadClass("com.jon.santafelab.StatusJob");
        Method maintain=status.getDeclaredMethod("maintainWidgets",Context.class,boolean.class);
        maintain.setAccessible(true);
        maintain.invoke(null,target,true);
        JobScheduler scheduler=(JobScheduler)target.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        JobInfo repair=null;
        for(JobInfo job:scheduler.getAllPendingJobs())if(job.getId()==92343)repair=job;
        require(repair!=null,"widget repair job registered without enabling vehicle status polling");
        require(repair.isPersisted(),"widget repair job survives ordinary reboot");
        require(repair.getIntervalMillis()>=15L*60*1000,"widget repair uses Android's periodic minimum");
        maintain.invoke(null,target,false);
        boolean stillScheduled=false;
        for(JobInfo job:scheduler.getAllPendingJobs())if(job.getId()==92343)stillScheduled=true;
        require(!stillScheduled,"widget repair removed when no widget is installed");

        Method requested=status.getDeclaredMethod("manualRefreshRequested",Context.class);
        Method feedback=status.getDeclaredMethod("widgetFeedback",Context.class,String.class);
        Method complete=status.getDeclaredMethod("finishManualRefresh",Context.class,boolean.class);
        requested.setAccessible(true);feedback.setAccessible(true);complete.setAccessible(true);
        requested.invoke(null,target);
        require(((String)feedback.invoke(null,target,"Cached car status")).startsWith("Refreshing"),"tap acknowledgment precedes network response");
        complete.invoke(null,target,false);
        require(((String)feedback.invoke(null,target,"Cached car status")).startsWith("Refresh failed"),"network failure is visible");
        target.getSharedPreferences("sf-widget-feedback-v1",0).edit().clear().commit();
    }

    private void require(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        checks++;
    }

    private WebView find(View view) {
        if (view instanceof WebView) return (WebView) view;
        if (view instanceof ViewGroup) for (int i=0; i<((ViewGroup)view).getChildCount(); i++) {
            WebView result = find(((ViewGroup)view).getChildAt(i));
            if (result != null) return result;
        }
        return null;
    }

    private WebView waitForPage() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        while (System.nanoTime() < deadline) {
            AtomicReference<WebView> found = new AtomicReference<WebView>();
            runOnMainSync(() -> found.set(find(activity.findViewById(android.R.id.content))));
            WebView page = found.get();
            if (page != null) {
                try {
                    JSONObject state = inspect(page);
                    if ("complete".equals(state.optString("ready")) && state.optBoolean("callback")) return page;
                } catch (Exception ignored) { }
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Bundled HTML/native callback did not load within 45 seconds");
    }

    private JSONObject inspect(WebView page) throws Exception {
        return new JSONObject(evaluate(page, "JSON.stringify({href:location.href,ready:document.readyState,bridgeVersion:typeof SantaFeNative!=='undefined'?SantaFeNative.getVersion():'',callback:!!window.SantaFeAndroid,loginDisabled:document.getElementById('loginBtn')?.disabled,emailDisabled:document.getElementById('email')?.disabled,setupHidden:document.getElementById('connectionSetup')?.hidden,downloadHidden:document.getElementById('appDownload')?.hidden,transport:document.getElementById('transportStatus')?.textContent,session:document.getElementById('sessionStatus')?.textContent,hasPassword:!!document.getElementById('password')?.value,hasEmail:!!document.getElementById('email')?.value,hasPin:!!document.getElementById('pin')?.value,accountOpen:document.getElementById('accountSettings')?.open,rememberChecked:document.getElementById('rememberAccount')?.checked})"));
    }

    private JSONObject waitForObject(WebView page, String expression) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            String raw = evaluate(page, "JSON.stringify(" + expression + ")");
            if (!"null".equals(raw)) return new JSONObject(raw);
            Thread.sleep(50);
        }
        throw new AssertionError("Native rejection callback missing");
    }

    private String evaluate(WebView page, String expression) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> value = new AtomicReference<String>();
        runOnMainSync(() -> page.evaluateJavascript(expression, result -> { value.set(result); done.countDown(); }));
        if (!done.await(10, TimeUnit.SECONDS)) throw new AssertionError("JavaScript evaluation timed out");
        String raw = value.get();
        if (raw != null && raw.startsWith("\"")) return new org.json.JSONArray("[" + raw + "]").getString(0);
        return raw;
    }
}
