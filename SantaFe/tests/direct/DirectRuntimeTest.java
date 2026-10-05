import bsh.Interpreter;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import okhttp3.*;
import org.json.*;

/** Executes the delivered BeanShell, with real OkHttp request construction and intercepted replies. */
public class DirectRuntimeTest {
    static int checks, scenarios;
    static Path sources;
    static final String VIN = "KM8S5DA11TU000001";
    static final String VIN2 = "KM8S5DA11TU000002";
    static final String EMAIL = "fixture+car@example.invalid";
    static final String PASSWORD = "fixture-quote\"-slash\\-newline\n";
    static final String PIN = "0123";

    public static class Context {
        final File root;
        Context(File root) { this.root = root; }
        public File getNoBackupFilesDir() { return root; }
    }
    public static class TaskInfo { public int getActionCount() { return 1; } }
    public static class Tasker {
        public final Map<String, String> variables = new HashMap<>();
        public final Map<String, Object> objects = new HashMap<>();
        public final List<String> toasts = new ArrayList<>();
        public String getVariable(String key) { return variables.get(key); }
        public void setVariable(String key, Object value) { variables.put(key, String.valueOf(value)); }
        public Map<String, Object> getGlobalJavaVariables() { return objects; }
        public void setJavaVariable(String key, Object value) { objects.put(key, value); }
        public void showToast(String value) { toasts.add(value); }
        public TaskInfo getTask() { return new TaskInfo(); }
    }
    static class Expected {
        final String method, path, reply;
        final int code;
        String tid;
        java.util.function.Consumer<Request> inspect = request -> {};
        Expected(String method, String path, int code, String reply) {
            this.method=method; this.path=path; this.code=code; this.reply=reply;
        }
        Expected check(java.util.function.Consumer<Request> check) { inspect=check; return this; }
        Expected tid(String value) { tid=value; return this; }
    }
    public static class Fixture {
        public long now=1791172800000L;
        final Tasker tasker=new Tasker();
        final Context context;
        final Interpreter interpreter=new Interpreter();
        final Queue<Expected> expected=new ArrayDeque<>();
        final List<Request> seen=new ArrayList<>();
        final List<Long> delays=new ArrayList<>();
        final String entry;
        public String confirmation="yes";
        public int confirmationRequests;
        public String chosen="cancel";
        public JSONObject account=new JSONObject().put("cancel", true);
        public JSONObject climate=new JSONObject().put("cancel", true);
        public JSONObject watchSettings=new JSONObject().put("cancel", true);
        public JSONObject joinSettings=new JSONObject().put("cancel", true);
        public final List<String> notifications=new ArrayList<>();
        public JSONObject locationSettings=new JSONObject().put("cancel", true);
        public JSONObject phone=new JSONObject();
        Fixture() throws Exception {
            context=new Context(Files.createTempDirectory("sfd-runtime-").toFile());
            interpreter.set("tasker", tasker); interpreter.set("context", context); interpreter.set("fixture", this);
            interpreter.eval(Files.readString(sources.resolve("api.java")));
            interpreter.eval(Files.readString(sources.resolve("ui.java")));
            interpreter.eval(Files.readString(sources.resolve("watch.java")));
            interpreter.eval(Files.readString(sources.resolve("location.java")));
            interpreter.eval(Files.readString(sources.resolve("remote.java")));
            String core=Files.readString(sources.resolve("core.java"));
            int cut=core.indexOf("// ENTRY:");
            interpreter.eval(core.substring(0,cut));
            entry=core.substring(cut);
            OkHttpClient real=(OkHttpClient)interpreter.eval("sfMakeClient();");
            check(!real.retryOnConnectionFailure() && !real.followRedirects() && !real.followSslRedirects(), "production retry and redirects disabled");
            check(real.cache()==null && real.cookieJar()==CookieJar.NO_COOKIES && real.callTimeoutMillis()==45000, "bounded uncached transport");
            OkHttpClient client=real.newBuilder().addInterceptor(chain -> {
                Request request=chain.request(); seen.add(request);
                check(request.url().host().equals("api.telematics.hyundaiusa.com") && request.url().isHttps(), "fixed Hyundai HTTPS origin");
                Expected item=expected.poll();
                if(item==null) throw new AssertionError("Unexpected request: "+request.method()+" "+request.url().encodedPath());
                check(request.method().equals(item.method) && request.url().encodedPath().equals(item.path), "request method/path");
                item.inspect.accept(request);
                Response.Builder result=new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(item.code).message("Fixture")
                    .body(ResponseBody.create(MediaType.parse("application/json"),item.reply));
                if(item.tid!=null) result.header("tmstid",item.tid);
                return result.build();
            }).build();
            interpreter.set("sfFixtureClient",client);
            interpreter.eval("sfMakeClient() { return sfFixtureClient; }\nlong sfNow() { return fixture.now; }\nvoid sfDelay(long value) { fixture.delay(value); }\n"
                +"String sfConfirm(String a, String b, String c) { fixture.confirmationRequests++; return fixture.confirmation; }\n"
                +"void sfMessage(String a, String b) {}\nString sfChoose(String a, String[] b, String[] c) { return fixture.chosen; }\n"
                +"JSONObject sfAccountForm() { return fixture.account; }\nJSONObject sfClimateForm(String a, String b, int c, boolean d) { return fixture.climate; }\nJSONObject sfWatchForm() { return fixture.watchSettings; }\nJSONObject sfJoinForm() { return fixture.joinSettings; }\nJSONObject sfLocationForm() { return fixture.locationSettings; }\nJSONObject sfPhoneFix() { return fixture.phone; }\nvoid sfRemoteNotification(String message) { fixture.notifications.add(message); }");
        }
        public void delay(long value) { delays.add(value); now+=value; }
        void credentials() { tasker.variables.put("SFDEmail",EMAIL); tasker.variables.put("SFDPassword",PASSWORD); tasker.variables.put("SFDPin",PIN); }
        Map session() { return (Map)tasker.objects.get("sfDirectSession"); }
        File marker() { return new File(context.root,"santa-fe-direct-pending.json"); }
        String run(String operation) throws Exception {
            tasker.variables.put("par1",operation);
            Object result=interpreter.eval(entry);
            check(expected.isEmpty(),"all planned requests consumed for "+operation);
            for(String value : tasker.toasts) {
                check(!value.contains(PASSWORD) && !value.contains(PIN) && !value.contains(EMAIL) && !value.contains("fixture-token") && !value.contains("fixture-tid"),"messages contain no credentials/token/transaction");
            }
            return String.valueOf(result);
        }
        String state() { return tasker.variables.get("SFDState"); }
        void pairWatch() { tasker.setVariable("SFDWatchKey", "00".repeat(32)); tasker.setVariable("SFDWatchEnabled", "1"); }
        String watchMessage(String operation, boolean safe, String id, long issued) throws Exception {
            String content="sfd1|"+id+"|"+issued+"|"+operation+"|"+(safe?"1":"0");
            javax.crypto.Mac mac=javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(new byte[32],"HmacSHA256"));
            return content+"|"+java.util.HexFormat.of().formatHex(mac.doFinal(content.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        }
        String runWatch(String message) throws Exception { tasker.setVariable("par2",message); return run("watch"); }
        void login() { expected.add(new Expected("POST","/v2/ac/oauth/token",200,"{\"access_token\":\"fixture-token\",\"expires_in\":1800}").check(r -> {
            JSONObject body=body(r); check(body.keySet().equals(Set.of("username","password")) && body.getString("username").equals(EMAIL) && body.getString("password").equals(PASSWORD),"login JSON escaping and exact fields");
            check(r.header("accessToken")==null && r.header("username")==null && r.header("blueLinkServicePin")==null,"login contains no session headers");
            common(r);
        })); }
        void enroll(JSONArray records) { expected.add(new Expected("GET","/ac/v2/enrollment/details/fixture%2Bcar@example.invalid",200,new JSONObject().put("enrolledVehicleDetails",records).toString()).check(r -> {
            common(r); check(r.header("accessToken").equals("fixture-token") && r.header("blueLinkServicePin").equals(PIN),"authenticated enrollment headers");
            check(r.header("vin")==null && r.body()==null,"enrollment has no vehicle/body");
        })); }
        void enroll() { enroll(new JSONArray().put(new JSONObject().put("vehicleDetails",vehicle(VIN,"reg-fixture")))); }
        void status(boolean fresh) { expected.add(new Expected("GET","/ac/v2/rcs/rvs/vehicleStatus",200,"{\"vehicleStatus\":{\"dateTime\":\"20261005061000\",\"doorLock\":true,\"engine\":false,\"airCtrlOn\":false}}").check(r -> {
            common(r); vehicleHeaders(r,VIN,"reg-fixture"); check(r.body()==null && r.header("refresh").equals(fresh?"true":"false"),"status refresh/header/body");
        })); }
        void connect() throws Exception { credentials(); login(); enroll(); status(false); run("connect"); check(state().equals("READ"),"connect succeeds"); }
        void location(JSONObject location) {
            expected.add(new Expected("GET", "/ac/v2/rcs/rfc/findMyCar", 200, location.toString()).check(r -> { common(r); vehicleHeaders(r,VIN,"reg-fixture"); check(r.body()==null,"location request is a bodyless GET"); }));
        }
        void locationReady() throws Exception {
            connect(); phone=new JSONObject().put("lat",0).put("lon",0).put("time",now).put("accuracy",15);
            location(new JSONObject().put("coord",new JSONObject().put("lat",0).put("lon",0)).put("time",java.time.Instant.ofEpochMilli(now).toString()));
            run("location");
        }
        Expected command(String operation, int http, String content, String tid) {
            String endpoint=Map.of("lock","/ac/v2/rcs/rdo/off","unlock","/ac/v2/rcs/rdo/on","start","/ac/v2/rcs/rsc/start","stop","/ac/v2/rcs/rsc/stop").get(operation);
            Expected item=new Expected("POST",endpoint,http,content).tid(tid).check(r -> {
                check(marker().isFile() && marker().length()>0,"guard exists before POST");
                common(r); vehicleHeaders(r,VIN,"reg-fixture");
                if(operation.equals("stop")) check(rawBody(r).isEmpty(),"remote stop sends empty body");
                if(operation.equals("lock") || operation.equals("unlock")) {
                    check(r.header("APPCLOUD-VIN").equals(VIN),"lock/unlock APPCLOUD-VIN");
                    JSONObject body=body(r); check(body.keySet().equals(Set.of("userName","vin")) && body.getString("userName").equals(EMAIL) && body.getString("vin").equals(VIN),"lock/unlock body parity");
                }
                if(operation.equals("start")) {
                    JSONObject body=body(r);
                    check(body.keySet().equals(Set.of("Ims","airCtrl","airTemp","defrost","heating1","igniOnDuration","seatHeaterVentInfo","username","vin")),"start body field parity");
                    check(body.getString("vin").equals("reg-fixture") && body.getString("username").equals(EMAIL),"non-EV start uses registration ID in body");
                    check(body.getJSONObject("airTemp").getInt("unit")==1 && body.getInt("airCtrl")==1 && body.getInt("Ims")==0 && body.getInt("heating1")==0,"climate fixed fields");
                    check(body.getJSONObject("seatHeaterVentInfo").keySet().equals(Set.of("drvSeatHeatState","astSeatHeatState","rlSeatHeatState","rrSeatHeatState")),"seat fields exact");
                    for(String key:body.getJSONObject("seatHeaterVentInfo").keySet()) check(body.getJSONObject("seatHeaterVentInfo").getInt(key)==0,"seats off");
                }
            }); expected.add(item); return item;
        }
        void poll(String result) { expected.add(new Expected("GET","/ac/v2/rmt/getRunningStatus",200,"{\"status\":\""+result+"\",\"tid\":\"fixture-tid\",\"nextPollingInterval\":7}").check(r -> {
            common(r); vehicleHeaders(r,VIN,"reg-fixture"); check(r.header("tid").equals("fixture-tid") && r.header("login_id").equals(EMAIL) && r.header("service_type").equals("REMOTE_POLL"),"poll transaction headers");
            check(r.body()==null,"poll has no body");
        })); }
    }
    static JSONObject vehicle(String vin,String regid) { return new JSONObject().put("vin",vin).put("regid",regid).put("vehicleGeneration",3).put("evStatus","N").put("enrollmentStatus","ACTIVE"); }
    static String rawBody(Request request) { try { okio.Buffer buffer=new okio.Buffer(); request.body().writeTo(buffer); return buffer.readUtf8(); } catch(IOException e){throw new RuntimeException(e);} }
    static JSONObject body(Request request) { return new JSONObject(rawBody(request)); }
    static void common(Request r) {
        Map<String,String> expected=Map.of("Content-Type","application/json;charset=UTF-8","Accept","application/json, text/plain, */*","from","SPA","to","ISS","language","0","encryptFlag","false","brandIndicator","H","Origin","https://api.telematics.hyundaiusa.com","Referer","https://api.telematics.hyundaiusa.com/login");
        for(Map.Entry<String,String> entry:expected.entrySet()) check(entry.getValue().equals(r.header(entry.getKey())),"common header "+entry.getKey());
        check(r.header("client_id").equals("m66129Bb-em93-SPAHYN-bZ91-am4540zp19920") && r.header("clientSecret").equals("v558o935-6nne-423i-baa8"),"existing public client identifiers");
        check(r.header("Cookie")==null && r.header("Authorization")==null,"no cookie or substituted authorization scheme");
        check(!r.header("offset").endsWith(".0"),"whole-hour offset matches the working JavaScript representation");
    }
    static void vehicleHeaders(Request r,String vin,String regid) {
        check(r.header("registrationId").equals(regid) && r.header("vin").equals(vin) && r.header("gen").equals("3"),"vehicle header parity");
    }
    static void check(boolean value,String label) { checks++; if(!value) throw new AssertionError(label); }
    static Fixture f() throws Exception { return new Fixture(); }
    static void test(String name, RunnableWithException test) throws Exception { test.run(); scenarios++; System.out.println("PASS "+name); }
    interface RunnableWithException { void run() throws Exception; }
    public static void main(String[] args) throws Exception {
        sources=Path.of(args[0]);
        test("offline imported action self-check",()->{Fixture f=f();check(f.run("verify").startsWith("Santa Fe Direct 1.2.0 loaded."),"BeanShell returns the reported result");check(f.state().equals("READY") && f.seen.isEmpty(),"verify is offline");});
        test("setup remembers account without network",()->{Fixture f=f();f.account=new JSONObject().put("email",EMAIL).put("password",PASSWORD).put("pin",PIN).put("vin","");f.run("setup");check(f.state().equals("SAVED") && f.tasker.getVariable("SFDPassword").equals(PASSWORD),"setup saved");});
        test("initial login and cached status",()->{Fixture f=f();f.connect();check(f.session().get("token").equals("fixture-token") && !f.tasker.variables.containsValue("fixture-token"),"token only Java object");check(f.tasker.getVariable("SFDDoorLock").equals("Locked") && f.tasker.getVariable("SFDEngine").equals("Off") && f.tasker.getVariable("SFDClimate").equals("Off"),"boolean status values render correctly: " + f.tasker.getVariable("SFDStatus"));});
        test("status labels cover both boolean states",()->{Fixture f=f();f.connect();f.expected.add(new Expected("GET","/ac/v2/rcs/rvs/vehicleStatus",200,"{\"vehicleStatus\":{\"dateTime\":\"2026-10-05T18:00:43Z\",\"doorLock\":false,\"engine\":true,\"airCtrlOn\":true}}"));f.run("status");check(f.tasker.getVariable("SFDDoorLock").equals("Unlocked") && f.tasker.getVariable("SFDEngine").equals("Running") && f.tasker.getVariable("SFDClimate").equals("On"),"false and true map to the displayed labels");check(f.tasker.getVariable("SFDVehicleTime").equals("2026-10-05T18:00:43Z"),"vehicle timestamp retained");});
        test("string booleans render without a boxed-type gate",()->{Fixture f=f();f.connect();f.expected.add(new Expected("GET","/ac/v2/rcs/rvs/vehicleStatus",200,"{\"vehicleStatus\":{\"doorLock\":\"true\",\"engine\":\"false\",\"airCtrlOn\":\"false\"}}"));f.run("status");check(f.tasker.getVariable("SFDDoorLock").equals("Locked") && f.tasker.getVariable("SFDEngine").equals("Off") && f.tasker.getVariable("SFDClimate").equals("Off"),"JSON boolean strings use explicit boolean reading");});
        test("missing and malformed status flags stay unknown",()->{Fixture f=f();f.connect();for(String fields:List.of("", "\"doorLock\":null,\"engine\":null,\"airCtrlOn\":null", "\"doorLock\":2,\"engine\":\"unexpected\",\"airCtrlOn\":{}")){f.expected.add(new Expected("GET","/ac/v2/rcs/rvs/vehicleStatus",200,"{\"vehicleStatus\":{"+fields+"}}"));f.run("status");check(f.tasker.getVariable("SFDDoorLock").equals("Unknown") && f.tasker.getVariable("SFDEngine").equals("Unknown") && f.tasker.getVariable("SFDClimate").equals("Unknown"),"unknown values never become a false/off default");}});
        test("scene preparation is offline before setup",()->{Fixture f=f();f.interpreter.eval(Files.readString(sources.resolve("gui.java")));check(f.seen.isEmpty() && f.tasker.getVariable("SFDGuiStatus").contains("Doors: Unknown") && f.tasker.getVariable("SFDGuiAccount").startsWith("Account not saved"),"GUI opens without credentials or network");});
        test("scene preparation shows saved settings without secrets",()->{Fixture f=f();f.connect();f.tasker.setVariable("SFDTemperature","68");int count=f.seen.size();f.interpreter.eval(Files.readString(sources.resolve("gui.java")));check(f.seen.size()==count && f.tasker.getVariable("SFDGuiStatus").contains("Doors: Locked") && f.tasker.getVariable("SFDGuiClimate").contains("68 F"),"GUI reflects read status and saved climate settings");for(Map.Entry<String,String> value:f.tasker.variables.entrySet()){if(value.getKey().startsWith("SFDGui"))check(!value.getValue().contains(EMAIL) && !value.getValue().contains(PASSWORD) && !value.getValue().contains(PIN) && !value.getValue().contains("fixture-token"),"GUI summaries exclude credential and session secrets");}});
        test("scene preparation retains unresolved outcome",()->{Fixture f=f();Files.writeString(f.marker().toPath(),"{\"action\":\"lock\"}");f.interpreter.eval(Files.readString(sources.resolve("gui.java")));check(f.tasker.getVariable("SFDGuiPending").startsWith("Outcome unresolved") && f.marker().exists() && f.seen.isEmpty(),"opening GUI does not clear a guard or resend anything");});
        test("live session reused and explicit refresh",()->{Fixture f=f();f.connect();int n=f.seen.size();f.status(true);f.run("refresh");check(f.seen.size()==n+1,"no extra credential request");});
        test("expired session authenticates before read",()->{Fixture f=f();f.connect();f.now+=2000000;f.login();f.enroll();f.status(false);f.run("status");check(f.state().equals("READ"),"expiry repaired");});
        test("lock completes after bounded pending poll",()->{Fixture f=f();f.connect();f.status(false);f.command("lock",200,"","fixture-tid");f.poll("PENDING");f.poll("SUCCESS");f.run("lock");check(f.state().equals("SUCCESS")&&!f.marker().exists(),"lock completion clears guard");check(f.delays.equals(List.of(10000L,10000L)),"bounded fixed polling interval without guessing API units");});
        test("unlock completion and no replay",()->{Fixture f=f();f.connect();f.status(false);f.command("unlock",200,"","fixture-tid");f.poll("SUCCESS");f.run("unlock");check(f.state().equals("SUCCESS"),"unlock completes");});
        test("remote start body and saved settings",()->{Fixture f=f();f.connect();f.tasker.setVariable("SFDTemperature","68");f.tasker.setVariable("SFDDuration","7");f.tasker.setVariable("SFDDefrost","1");f.status(false);Expected c=f.command("start",200,"","fixture-tid");java.util.function.Consumer<Request> original=c.inspect;c.inspect=r->{original.accept(r);JSONObject b=body(r);check(b.getJSONObject("airTemp").getInt("value")==68&&b.getInt("igniOnDuration")==7&&b.getBoolean("defrost"),"saved climate settings");};f.poll("SUCCESS");f.run("start");check(f.state().equals("SUCCESS"),"start completes");});
        test("remote stop has empty request body",()->{Fixture f=f();f.connect();f.status(false);f.command("stop",200,"","fixture-tid");f.poll("SUCCESS");f.run("stop");check(f.state().equals("SUCCESS"),"stop completes");});
        test("cancelled remote start never submitted",()->{Fixture f=f();f.connect();int n=f.seen.size();f.confirmation="no";f.run("start");check(f.seen.size()==n&&!f.marker().exists()&&f.state().equals("CANCELLED"),"cancel honored");});
        test("missing transaction prevents repeat and survives lost Java session",()->{Fixture f=f();f.connect();f.status(false);f.command("lock",200,"",null);f.run("lock");check(f.state().equals("UNKNOWN")&&f.marker().exists(),"unknown guarded");int n=f.seen.size();f.run("unlock");check(f.seen.size()==n,"repeat blocked before network");f.tasker.objects.clear();f.run("stop");check(f.seen.size()==n&&f.marker().exists(),"guard survives memory loss");});
        test("command 401 never replayed",()->{Fixture f=f();f.connect();f.status(false);f.command("unlock",401,"{\"secret\":\"fixture-token\"}",null);f.run("unlock");check(f.state().equals("UNKNOWN")&&!f.session().containsKey("token"),"auth fail is unknown");int n=f.seen.size();f.run("unlock");check(f.seen.size()==n,"401 command not replayed");});
        test("login rejection blocks automatic repeated password attempts",()->{Fixture f=f();f.credentials();f.expected.add(new Expected("POST","/v2/ac/oauth/token",401,"{}"));f.run("connect");check(f.state().equals("FAILED"),"bad login");int n=f.seen.size();f.run("lock");check(f.seen.size()==n&&!f.marker().exists(),"no repeated login or command");});
        test("enrollment failure blocks automatic retry but explicit Connect recovers",()->{Fixture f=f();f.credentials();f.login();f.expected.add(new Expected("GET","/ac/v2/enrollment/details/fixture%2Bcar@example.invalid",502,"{}"));f.run("connect");int n=f.seen.size();f.run("start");check(f.seen.size()==n&&!f.marker().exists(),"no command after partial login");f.login();f.enroll();f.status(false);f.run("connect");check(f.state().equals("READ")&&Boolean.FALSE.equals(f.session().get("authBlocked")),"explicit Connect repairs failed enrollment even with an unexpired token");});
        test("status failure prevents control submission",()->{Fixture f=f();f.connect();f.expected.add(new Expected("GET","/ac/v2/rcs/rvs/vehicleStatus",502,"{}"));f.run("lock");check(!f.marker().exists()&&f.state().equals("FAILED"),"failed pre-command read");});
        test("terminal ERROR is a failed command without retry",()->{Fixture f=f();f.connect();f.status(false);f.command("stop",200,"","fixture-tid");f.poll("ERROR");f.run("stop");check(f.state().equals("FAILED")&&!f.marker().exists(),"terminal error clears pending");});
        test("unrecognized poll result stays unknown",()->{Fixture f=f();f.connect();f.status(false);f.command("lock",200,"","fixture-tid");f.poll("fixture-token");f.run("lock");check(f.state().equals("UNKNOWN")&&f.marker().exists(),"unknown state guarded and suppressed");});
        test("bounded pending polls stop without command resend",()->{Fixture f=f();f.connect();f.status(false);f.command("lock",200,"","fixture-tid");for(int i=0;i<10;i++)f.poll("PENDING");f.run("lock");check(f.state().equals("PENDING")&&f.marker().exists()&&f.delays.size()==10,"bounded poll count");});
        test("manual later poll completes pending transaction",()->{Fixture f=f();f.connect();f.status(false);f.command("lock",200,"","fixture-tid");f.poll("UNKNOWN");f.run("lock");f.poll("SUCCESS");f.run("poll");check(f.state().equals("SUCCESS")&&!f.marker().exists(),"same transaction completed");});
        test("explicit physical acknowledgement sends no API request",()->{Fixture f=f();Files.writeString(f.marker().toPath(),"corrupt fixture");f.run("resolve");check(f.state().equals("ACKNOWLEDGED")&&!f.marker().exists()&&f.seen.isEmpty(),"ack only clears guard");});
        test("cancelled acknowledgement preserves guard",()->{Fixture f=f();Files.writeString(f.marker().toPath(),"{}");f.confirmation="no";f.run("resolve");check(f.marker().exists()&&f.state().equals("CANCELLED"),"guard preserved");});
        test("setup blocked while outcome unresolved",()->{Fixture f=f();Files.writeString(f.marker().toPath(),"{}");f.run("setup");check(f.state().equals("UNKNOWN")&&f.seen.isEmpty(),"settings cannot override pending");});
        test("forget account retains unresolved outcome",()->{Fixture f=f();f.credentials();Files.writeString(f.marker().toPath(),"{}");f.run("forget");check(f.tasker.getVariable("SFDPassword").isEmpty()&&f.marker().exists(),"forget does not discard pending");});
        test("clearing login retains saved account and pending guard",()->{Fixture f=f();f.connect();Files.writeString(f.marker().toPath(),"{}");f.run("clear");check(f.session().isEmpty()&&f.tasker.getVariable("SFDPassword").equals(PASSWORD)&&f.marker().exists(),"clear scoped correctly");});
        test("invalid climate settings withheld before control request",()->{Fixture f=f();f.connect();for(String value:List.of("61","82","bad","70.5")){f.tasker.setVariable("SFDTemperature",value);f.run("start");check(!f.marker().exists()&&f.state().equals("FAILED"),"temperature rejected");}});
        test("unconfirmed vehicle type withheld",()->{Fixture f=f();f.connect();((JSONObject)f.session().get("vehicle")).put("evStatus","E");f.run("start");check(f.state().equals("FAILED")&&!f.marker().exists(),"EV fallback absent");});
        test("multiple active vehicles require explicit selection",()->{Fixture f=f();f.credentials();f.login();f.enroll(new JSONArray().put(new JSONObject().put("vehicleDetails",vehicle(VIN,"reg-fixture"))).put(new JSONObject().put("vehicleDetails",vehicle(VIN2,"reg-two"))));f.run("connect");check(f.state().equals("FAILED")&&!f.marker().exists(),"no arbitrary selection");f.chosen=VIN2;f.run("choose");check(f.tasker.getVariable("SFDVin").equals(VIN2)&&f.state().equals("SELECTED"),"selected VIN saved");});
        test("cancelled enrollment never selected",()->{Fixture f=f();f.credentials();f.login();f.enroll(new JSONArray().put(new JSONObject().put("vehicleDetails",vehicle(VIN,"reg-fixture").put("enrollmentStatus","CANCELLED"))));f.run("connect");check(f.state().equals("FAILED")&&((JSONArray)f.session().get("vehicles")).isEmpty(),"cancelled enrollment filtered");});
        test("rate limit prevents further network until cooldown",()->{Fixture f=f();f.credentials();f.expected.add(new Expected("POST","/v2/ac/oauth/token",429,"{}"));f.run("connect");int n=f.seen.size();f.run("connect");check(f.seen.size()==n,"cooldown honored");});
        test("credential identity change invalidates stale token",()->{Fixture f=f();f.connect();f.tasker.setVariable("SFDPassword","changed-fixture");f.expected.add(new Expected("POST","/v2/ac/oauth/token",401,"{}"));f.run("connect");check(!f.session().containsKey("token"),"old session invalidated");});
        test("mismatched transaction remains guarded",()->{Fixture f=f();f.connect();f.status(false);f.command("lock",200,"","fixture-tid");f.expected.add(new Expected("GET","/ac/v2/rmt/getRunningStatus",200,"{\"status\":\"SUCCESS\",\"tid\":\"different-fixture\"}"));f.run("lock");check(f.state().equals("UNKNOWN")&&f.marker().exists(),"other transaction cannot confirm command");});
        test("guard write failure prevents POST",()->{Fixture f=f();f.connect();f.status(false);f.interpreter.eval("void sfSaveMarker(String op) { sfFail(\"Could not save the pending-command guard. No command was sent.\"); }");f.run("lock");check(f.state().equals("FAILED")&&!f.marker().exists(),"fail closed before POST");});
        test("unknown operations and horn/lights rejected offline",()->{Fixture f=f();for(String op:List.of("lights","horn","horn_lights","horn_lights;lock")){f.run(op);check(f.seen.isEmpty()&&f.state().equals("FAILED"),"unconfirmed operation rejected");}});
        test("oversized and malformed replies fail without leak or retry",()->{for(String payload:List.of("not-json fixture-token", "x".repeat(1048577))){Fixture f=f();f.credentials();f.expected.add(new Expected("POST","/v2/ac/oauth/token",200,payload));f.run("connect");check(f.state().equals("FAILED")&&f.seen.size()==1,"bad response stops");}});
        test("header injection prevented before network",()->{Fixture f=f();f.credentials();f.tasker.setVariable("SFDEmail","fixture@example.invalid\r\nEvil: 1");f.run("connect");check(f.state().equals("FAILED")&&f.seen.isEmpty(),"header injection blocked");});
        test("enrollment encoding matches confirmed literal-at recipe",()->{Fixture f=f();Object path=f.interpreter.eval("sfEnrollmentPath(\"a+b/c ?#@example.invalid\");");check(path.equals("/ac/v2/enrollment/details/a%2Bb%2Fc%20%3F%23@example.invalid"),"other delimiters remain encoded");});
        test("overlapping tasks blocked before opening guard channel",()->{
            Fixture f=f();java.util.concurrent.locks.ReentrantLock lock=new java.util.concurrent.locks.ReentrantLock();
            f.tasker.objects.put("sfDirectLock",lock);
            java.util.concurrent.CountDownLatch held=new java.util.concurrent.CountDownLatch(1), release=new java.util.concurrent.CountDownLatch(1);
            Thread other=new Thread(()->{lock.lock();held.countDown();try{release.await();}catch(InterruptedException ignored){}finally{lock.unlock();}});
            other.start();held.await();
            try {f.run("verify");check(f.seen.isEmpty() && !new File(f.context.root,"santa-fe-direct.lock").exists(),"overlap has no network/file-channel side effects");}
            finally {release.countDown();other.join();}
            f.run("verify");check(f.state().equals("READY"),"lock remains reusable");
        });
        test("cold and hot defaults use unchanged start endpoint and preserve regular preset",()->{
            for(String op:List.of("start_cold","start_hot")) {
                Fixture f=f();f.connect();f.tasker.setVariable("SFDTemperature","70");f.tasker.setVariable("SFDDuration","5");f.status(false);
                Expected command=f.command("start",200,"","fixture-tid");var original=command.inspect;
                command.inspect=r->{original.accept(r);JSONObject b=body(r);check(b.getJSONObject("airTemp").getInt("value")== (op.equals("start_cold")?62:81) && b.getBoolean("defrost")==op.equals("start_hot") && b.getInt("igniOnDuration")==10,"preset values only");};
                f.poll("SUCCESS");f.run(op);check(f.state().equals("SUCCESS") && f.tasker.getVariable("SFDTemperature").equals("70") && f.tasker.getVariable("SFDDuration").equals("5"),"regular preset unchanged");
            }
        });
        test("editable preset saves without sending commands",()->{Fixture f=f();f.tasker.setVariable("SFDTemperature","70");f.climate=new JSONObject().put("temperature",66).put("duration",6).put("defrost",false);f.run("climate_cold");check(f.seen.isEmpty() && f.tasker.getVariable("SFDColdTemperature").equals("66") && f.tasker.getVariable("SFDTemperature").equals("70"),"independent cold settings");});
        test("custom cold preset affects only its submitted request",()->{Fixture f=f();f.connect();f.tasker.setVariable("SFDColdTemperature","65");f.tasker.setVariable("SFDColdDuration","6");f.tasker.setVariable("SFDColdDefrost","1");f.status(false);Expected c=f.command("start",200,"","fixture-tid");var original=c.inspect;c.inspect=r->{original.accept(r);JSONObject b=body(r);check(b.getJSONObject("airTemp").getInt("value")==65 && b.getInt("igniOnDuration")==6 && b.getBoolean("defrost"),"editable preset body");};f.poll("SUCCESS");f.run("start_cold");});
        test("cancelled cold and hot starts never submitted",()->{for(String op:List.of("start_cold","start_hot")){Fixture f=f();f.connect();int count=f.seen.size();f.confirmation="no";f.run(op);check(f.seen.size()==count && f.state().equals("CANCELLED") && f.confirmationRequests==1,"preset confirmation retained");}});
        test("invalid preset values fail before any status or control request",()->{Fixture f=f();f.connect();int count=f.seen.size();f.tasker.setVariable("SFDHotTemperature","82");f.run("start_hot");check(f.state().equals("FAILED") && f.seen.size()==count && !f.marker().exists(),"out-of-range hot withheld");});
        test("watch setup is offline and disabled until enabled",()->{Fixture f=f();f.watchSettings=new JSONObject().put("enabled",false).put("key","00".repeat(32));f.run("watch_settings");check(f.seen.isEmpty() && f.tasker.getVariable("SFDWatchEnabled").equals("0"),"offline disabled setup");f.runWatch(f.watchMessage("lock",false,"fixture_request_01",f.now));check(f.seen.isEmpty() && f.state().equals("FAILED"),"disabled receiver ignores signed message");});
        test("signed watch test authenticates without car credentials or network",()->{Fixture f=f();f.pairWatch();f.runWatch(f.watchMessage("ping",false,"fixture_request_01",f.now));check(f.state().equals("WATCH PAIRED") && f.seen.isEmpty() && f.confirmationRequests==0,"pairing test is offline");});
        test("tampered and malformed watch pushes cannot authenticate",()->{for(String message:List.of("sfd1|bad", "sfd1|fixture_request_01|1791172800000|lock|0|"+"00".repeat(32), "sfd1|fixture_request_01|1791172800000|unlock;lock|0|"+"00".repeat(32))){Fixture f=f();f.pairWatch();f.runWatch(message);check(f.seen.isEmpty() && f.state().equals("FAILED") && !new File(f.context.root,"santa-fe-direct-watch-seen.json").exists(),"invalid push has no network or receipt");}});
        test("expired future and unconfirmed watch starts are withheld",()->{Fixture f=f();f.pairWatch();for(long issued:List.of(f.now-90001,f.now+10001)){f.runWatch(f.watchMessage("lock",false,"fixture_request_01",issued));check(f.seen.isEmpty()&&f.state().equals("FAILED"),"age limits");}f.runWatch(f.watchMessage("start",false,"fixture_request_01",f.now));check(f.seen.isEmpty()&&f.state().equals("FAILED"),"signed start without confirmation blocked");});
        test("valid signed watch cold start uses watch confirmation and normal guarded core",()->{Fixture f=f();f.connect();f.pairWatch();f.confirmation="no";f.status(false);f.command("start",200,"","fixture-tid");f.poll("SUCCESS");String message=f.watchMessage("start_cold",true,"fixture_request_01",f.now);f.runWatch(message);check(f.state().equals("SUCCESS")&&f.confirmationRequests==0,"watch confirmation avoids phone dialog only after authentication");int count=f.seen.size();f.runWatch(message);check(f.seen.size()==count&&f.state().equals("FAILED"),"successful command never replayed");f.confirmation="no";f.run("start");check(f.state().equals("CANCELLED")&&f.confirmationRequests==1,"manual task still confirms after watch call");});
        test("watch replay receipt survives process loss",()->{Fixture f=f();f.pairWatch();String message=f.watchMessage("ping",false,"fixture_request_01",f.now);f.runWatch(message);f.tasker.objects.clear();f.runWatch(message);check(f.state().equals("FAILED")&&f.seen.isEmpty(),"durable deduplication");});
        test("watch controls cannot bypass unresolved outcome guard",()->{Fixture f=f();f.pairWatch();Files.writeString(f.marker().toPath(),"{}");f.runWatch(f.watchMessage("unlock",false,"fixture_request_01",f.now));check(f.state().equals("UNKNOWN")&&f.marker().exists()&&f.seen.isEmpty(),"pending guard blocks signed command before login");});
        test("invalid replay storage fails closed before commands",()->{Fixture f=f();f.pairWatch();Files.writeString(new File(f.context.root,"santa-fe-direct-watch-seen.json").toPath(),"bad JSON");f.runWatch(f.watchMessage("unlock",false,"fixture_request_01",f.now));check(f.seen.isEmpty()&&f.state().equals("FAILED"),"invalid durable receipts stop request");});
        test("manual GPS read returns valid zero coordinates and approximate near state",()->{Fixture f=f();f.locationReady();check(f.tasker.getVariable("SFDCarLat").equals("0.0")&&f.tasker.getVariable("SFDProximity").equals("Near")&&f.tasker.getVariable("SFDDistanceMeters").equals("0"),"valid equator/prime meridian not treated as missing");check(f.tasker.getVariable("SFDLocationVerified").equals(VIN)&&!f.marker().exists(),"successful read verified without command guard");});
        test("GPS time parsing rejects malformed dates and supports known UTC formats",()->{Fixture f=f();for(String date:List.of("20261005180043","2026-10-05T18:00:43Z","2026-10-05T14:00:43-04:00")){f.interpreter.set("date",date);check(((Number)f.interpreter.eval("sfLocationMillis(date);")).longValue()==java.time.Instant.parse("2026-10-05T18:00:43Z").toEpochMilli(),"UTC formats agree");}for(String date:List.of("20260230000000","2026-10-05T18:00:43","untrusted")){f.interpreter.set("date",date);check(((Number)f.interpreter.eval("sfLocationMillis(date);")).longValue()==0,"ambiguous or invalid time withheld");}});
        test("location distance handles dateline and antipodes",()->{Fixture f=f();double oneDegree=((Number)f.interpreter.eval("sfDistanceMeters(0,0,0,1);")).doubleValue();check(Math.abs(oneDegree-111195)<2,"known equatorial distance");double dateline=((Number)f.interpreter.eval("sfDistanceMeters(0,179.9,0,-179.9);")).doubleValue();check(Math.abs(dateline-22239)<3,"dateline uses short route");double opposite=((Number)f.interpreter.eval("sfDistanceMeters(90,0,-90,0);")).doubleValue();check(Double.isFinite(opposite)&&Math.abs(opposite-20015114)<3,"antipodal finite");});
        test("stale car position never produces current near or apart verdict",()->{Fixture f=f();f.locationReady();f.now+=900001;f.phone.put("time",f.now);int seen=f.seen.size();f.run("compare_location");check(f.tasker.getVariable("SFDProximity").equals("Unknown")&&f.tasker.getVariable("SFDLocationResult").contains("Stale")&&f.seen.size()==seen,"stale comparison is offline and labelled");});
        test("missing phone GPS retains timestamp but clears old distance verdict",()->{Fixture f=f();f.locationReady();f.phone=new JSONObject();int seen=f.seen.size();f.run("compare_location");check(f.tasker.getVariable("SFDDistanceMeters").isEmpty()&&f.tasker.getVariable("SFDProximity").equals("Unknown")&&f.tasker.getVariable("SFDLocationResult").contains("unavailable")&&f.seen.size()==seen,"permission or missing fix is unknown without Hyundai request");});
        test("inaccurate phone fix cannot produce near or apart verdict",()->{Fixture f=f();f.locationReady();f.phone.put("accuracy",200);f.run("compare_location");check(f.tasker.getVariable("SFDProximity").equals("Unknown"),"poor accuracy withheld");f.phone.put("accuracy",15).put("lon",0.0013);f.run("compare_location");check(f.tasker.getVariable("SFDProximity").equals("Uncertain"),"threshold crossing accuracy is uncertain");f.phone.put("lon",1);f.run("compare_location");check(f.tasker.getVariable("SFDProximity").equals("Apart"),"separated fresh positions");});
        test("invalid car GPS response retains the last sample without stale verdict",()->{Fixture f=f();f.locationReady();String original=f.tasker.getVariable("SFDCarTime");f.location(new JSONObject().put("coord",new JSONObject().put("lat",91).put("lon",0)));f.run("location");check(f.tasker.getVariable("SFDCarTime").equals(original)&&f.tasker.getVariable("SFDProximity").equals("Unknown")&&f.tasker.getVariable("SFDDistanceMeters").isEmpty(),"invalid range fails without corrupting cache or current verdict");});
        test("periodic GPS disabled and cannot enable without a successful lookup",()->{Fixture f=f();f.run("periodic_location");check(f.seen.isEmpty(),"disabled task is offline");f.locationSettings=new JSONObject().put("enabled",true).put("hours",1).put("meters",150);f.run("location_settings");check(!"1".equals(f.tasker.getVariable("SFDAutoLocation"))&&f.seen.isEmpty(),"unverified location cannot start schedule");});
        test("periodic GPS interval survives live-session loss and never submits controls",()->{Fixture f=f();f.locationReady();f.tasker.setVariable("SFDAutoLocation","1");f.tasker.setVariable("SFDLocationHours","2");f.location(new JSONObject().put("coord",new JSONObject().put("lat",0).put("lon",0)).put("time",java.time.Instant.ofEpochMilli(f.now).toString()));f.run("periodic_location");int seen=f.seen.size();f.tasker.objects.clear();f.now+=3600000;f.run("periodic_location");check(f.seen.size()==seen&&!f.marker().exists(),"not due after one hour despite lost session");check(f.seen.stream().filter(r->r.method().equals("POST")).allMatch(r->r.url().encodedPath().equals("/v2/ac/oauth/token")),"only authentication POST, never control");});
        test("locations cannot be compared across vehicles or credential identities",()->{Fixture f=f();f.locationReady();int seen=f.seen.size();f.tasker.setVariable("SFDVin",VIN2);f.run("compare_location");check(f.tasker.getVariable("SFDProximity").equals("Unknown")&&f.seen.size()==seen,"old VIN invalidated offline");f.tasker.setVariable("SFDVin","");f.tasker.setVariable("SFDPassword","different");f.run("compare_location");check(f.tasker.getVariable("SFDLocationResult").contains("selected account/car"),"different account cannot reuse coordinates");});
        test("failed periodic lookup pauses schedule even after process loss",()->{Fixture f=f();f.locationReady();f.tasker.setVariable("SFDAutoLocation","1");f.expected.add(new Expected("GET","/ac/v2/rcs/rfc/findMyCar",502,"{}"));f.run("periodic_location");check(f.tasker.getVariable("SFDAutoLocation").equals("0")&&f.tasker.getVariable("SFDLocationResult").contains("paused"),"failure disables background lookup");int seen=f.seen.size();f.tasker.objects.clear();f.now+=3600000;f.run("periodic_location");check(f.seen.size()==seen,"process loss cannot trigger another credential attempt");});
        test("forget account also removes GPS cache and periodic consent",()->{Fixture f=f();f.locationReady();f.tasker.setVariable("SFDAutoLocation","1");int seen=f.seen.size();f.run("forget");check(f.tasker.getVariable("SFDCarLat").isEmpty()&&f.tasker.getVariable("SFDCarIdentity").isEmpty()&&f.tasker.getVariable("SFDLocationVerified").isEmpty()&&f.tasker.getVariable("SFDAutoLocation").equals("0")&&f.seen.size()==seen,"forget clears location offline");});
        test("Join opt-in and connection test are offline", () -> {
            Fixture f=f(); f.tasker.setVariable("par2", "ping"); f.run("join");
            check(f.state().equals("FAILED") && f.seen.isEmpty(), "receiver disabled by default");
            f.joinSettings=new JSONObject().put("enabled", true); f.run("join_settings");
            f.tasker.setVariable("par2", "ping|fixture_join_001|"+f.now+"|0"); f.run("join");
            check(f.state().equals("PHONE REACHED") && f.seen.isEmpty() && f.notifications.size()==2, "ping never logs in or controls vehicle");
            JSONObject state=new JSONObject(f.tasker.getVariable("SFDWebState"));
            check(state.getJSONObject("result").getString("id").equals("fixture_join_001") && !state.getBoolean("busy"), "final reply correlated to request");
        });
        test("Join lock unlock and stop use existing exact API recipes once", () -> {
            for (String command:List.of("lock", "unlock", "ignition_off")) {
                Fixture f=f(); f.connect(); f.tasker.setVariable("SFDJoinEnabled", "1");
                f.tasker.setVariable("par2", command+"|fixture_join_001|"+f.now+"|0");
                f.status(false); f.command(command.equals("ignition_off")?"stop":command,200,"","fixture-tid"); f.poll("SUCCESS"); f.run("join");
                int seen=f.seen.size(); f.run("join");
                check(f.seen.size()==seen && f.state().equals("FAILED"), "duplicate never repeats command");
            }
        });
        test("Join three start modes require frontend confirmation and use distinct presets", () -> {
            for (String command:List.of("ignition_on", "ignition_on_cold", "ignition_on_hot")) {
                Fixture f=f(); f.connect(); f.tasker.setVariable("SFDJoinEnabled", "1"); f.confirmation="no";
                f.tasker.setVariable("par2", command+"|fixture_join_001|"+f.now+"|1");
                int temp=command.endsWith("cold")?62:command.endsWith("hot")?81:72;
                f.status(false); Expected request=f.command("start",200,"","fixture-tid"); var original=request.inspect;
                request.inspect=r -> { original.accept(r); check(body(r).getJSONObject("airTemp").getInt("value")==temp, "correct saved preset temperature"); };
                f.poll("SUCCESS"); f.run("join");
                check(f.state().equals("SUCCESS") && f.confirmationRequests==0, "confirmed remote start runs without second phone prompt");
            }
        });
        test("Legacy sample routes exact command and keeps phone start confirmation", () -> {
            Fixture f=f(); f.connect(); f.tasker.setVariable("SFDJoinEnabled", "1");
            f.tasker.setVariable("par2", "hyundai=:=lock"); f.status(false); f.command("lock",200,"","fixture-tid"); f.poll("SUCCESS"); f.run("join");
            f.confirmation="no"; f.tasker.setVariable("par2", "ignition_on_hot"); f.run("join");
            check(f.state().equals("CANCELLED") && f.confirmationRequests==1, "bare start cannot bypass confirmation");
        });
        test("Malformed expired future and unconfirmed Join commands are offline", () -> {
            Fixture f=f(); f.tasker.setVariable("SFDJoinEnabled", "1");
            for (String payload:List.of("lock;unlock", "horn", "lights", "forget", "lock|bad|"+f.now+"|0", "lock|fixture_join_001|"+(f.now-90001)+"|0", "lock|fixture_join_001|"+(f.now+10001)+"|0", "ignition_on|fixture_join_001|"+f.now+"|0", "lock|fixture_join_001|"+f.now+"|1")) {
                f.tasker.setVariable("par2", payload); f.run("join");
                check(f.state().equals("FAILED") && f.seen.isEmpty(), "invalid commands cannot contact Hyundai");
            }
        });
        test("Join durable replay guard and unresolved control guard survive session loss", () -> {
            Fixture f=f(); f.tasker.setVariable("SFDJoinEnabled", "1");
            f.tasker.setVariable("par2", "ping|fixture_join_001|"+f.now+"|0"); f.run("join"); f.tasker.objects.clear(); f.run("join");
            check(f.state().equals("FAILED") && f.seen.isEmpty(), "replay blocked after process loss");
            Files.writeString(f.marker().toPath(), "{}"); f.tasker.setVariable("par2", "unlock|fixture_join_002|"+f.now+"|0"); f.run("join");
            check(f.state().equals("UNKNOWN") && f.marker().exists() && f.seen.isEmpty(), "pending guard precedes authentication");
        });
        test("Corrupt Join receipts fail closed before authentication", () -> {
            Fixture f=f(); f.tasker.setVariable("SFDJoinEnabled", "1");
            Files.writeString(new File(f.context.root,"santa-fe-direct-join-seen.json").toPath(), "bad JSON");
            f.tasker.setVariable("par2", "lock|fixture_join_001|"+f.now+"|0"); f.run("join");
            check(f.state().equals("FAILED") && f.seen.isEmpty(), "corruption cannot cause repeat controls");
        });
        test("Bundled WebView uses local receiver with correlation without Join opt-in", () -> {
            Fixture f=f(); f.run("web_prepare");
            check(f.seen.isEmpty() && new JSONObject(f.tasker.getVariable("SFDWebState")).getInt("version")==1, "opening phone interface is offline");
            f.tasker.setVariable("par2", "ping|fixture_web_0001|"+f.now+"|0"); f.run("web");
            check(f.state().equals("PHONE REACHED") && f.seen.isEmpty(), "local test works without Join key");
            f.tasker.setVariable("par2", "lock"); f.run("web");
            check(f.state().equals("FAILED") && f.seen.isEmpty(), "local UI requires replay metadata");
        });
        test("Phone logs and reply snapshots exclude credentials and GPS in activity", () -> {
            Fixture f=f(); f.locationReady(); f.run("web_prepare");
            String state=f.tasker.getVariable("SFDWebState"), logs=f.tasker.getVariable("SFDWebLog");
            for (String secret:List.of(EMAIL,PASSWORD,PIN,VIN,"fixture-token","fixture-tid")) check(!state.contains(secret) && !logs.contains(secret), "no account/token/full VIN in UI export");
            JSONArray list=new JSONArray(logs); JSONObject last=list.getJSONObject(list.length()-1);
            check(!last.has("lat") && !last.has("lon") && !last.getString("message").contains("Car GPS:"), "GPS coordinates only in private live display");
            check(new JSONObject(state).getString("carLat").equals("0.0"), "zero latitude remains a usable location");
        });
        test("Private activity history is bounded and survives session loss", () -> {
            Fixture f=f(); for (int i=0;i<70;i++) f.run("verify");
            check(new JSONArray(f.tasker.getVariable("SFDWebLog")).length()==60, "at most 60 phone results");
            f.tasker.objects.clear(); f.run("web_prepare");
            check(new JSONArray(f.tasker.getVariable("SFDWebLog")).length()==60, "history retained without adding a UI-open event");
        });
        System.out.println("PASS "+checks+" runtime assertions; "+scenarios+" scenarios; zero real network requests or vehicle operations.");
    }
}
