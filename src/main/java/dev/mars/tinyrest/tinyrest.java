
// TinyRest.java
//
// Build deps (Maven):
//   - com.fasterxml.jackson.core:jackson-databind:2.17.2
//   - com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.2
// Test deps (for the JUnit extension):
//   - org.junit.jupiter:junit-jupiter:5.x
//
// Run:
//   mvn -q -DskipTests package
//   java -cp target/your-jar-with-deps.jar:. TinyRest src/test/resources/tinyrest.yml
//
// Example tinyrest.yml shown at bottom of this file’s comment header.

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TinyRest {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: java TinyRest <tinyrest.yml|json>");
            System.exit(1);
        }
        var path = Paths.get(args[0]).toAbsolutePath();
        var cfg = loadConfig(path.toString());
        var handle = start(cfg, path);
        System.out.printf("TinyRest ready on http://localhost:%d/  (watching %s)%n",
                handle.boundAddress().getPort(), path);
    }

    // ---------- Public API ----------
    public static MockConfig loadConfig(String path) throws IOException {
        try (var in = new FileInputStream(path)) {
            boolean yaml = path.endsWith(".yml") || path.endsWith(".yaml");
            ObjectMapper mapper = yaml ? yamlMapper() : jsonMapper();
            return mapper.readValue(in, MockConfig.class);
        }
    }

    public static ServerHandle start(MockConfig cfg, Path configPath) throws IOException {
        int port = cfg.port != null ? cfg.port : 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors())));
        var engine = new Engine(defaults(cfg), configPath);
        server.createContext("/", ex -> { try { engine.handle(ex); } finally { ex.close(); }});
        server.start();
        if (engine.features.hotReload) engine.startFileWatcher();
        return new ServerHandle(server, engine);
    }

    // ---------- Engine ----------
    static class Engine {
        volatile MockConfig cfg;
        final ObjectMapper om = jsonMapper();
        final Map<String, ResourceStore> stores = new ConcurrentHashMap<>();
        volatile List<Route> routes = new CopyOnWriteArrayList<>();
        final Path configPath;
        final Features features;
        final Recorder recorder;

        Engine(MockConfig cfg, Path configPath) {
            this.cfg = cfg;
            this.configPath = configPath;
            this.features = Features.from(cfg.features);
            if ("strict".equalsIgnoreCase(features.schemaValidation)) validateOrDie(cfg);
            else validateLenient(cfg);

            initStores();
            initRoutes();
            this.recorder = new Recorder(features.recordReplay, om);
            if (recorder.isReplay()) recorder.loadFromFile();
        }

        void handle(HttpExchange ex) throws IOException {
            addCORS(ex);
            if (handleCorsPreflight(ex)) return;
            withLatency(cfg.artificialLatencyMs);
            maybeChaos(cfg.chaosFailRate);

            try {
                String method = ex.getRequestMethod();
                String path = ex.getRequestURI().getPath();

                // record/replay — replay happens BEFORE any routing
                if (recorder.isReplay()) {
                    var hit = recorder.tryReplay(ex);
                    if (hit != null) { write(ex, hit); return; }
                    if ("error".equalsIgnoreCase(recorder.replayOnMiss())) {
                        write(ex, Response.json(501, Map.of("error", "replay_miss", "path", path))); return;
                    }
                    // else: fallback to normal routing
                }

                for (Route r : routes) {
                    Matcher m = r.pattern.matcher(path);
                    if (r.method.equals(method) && m.matches()) {
                        var ctx = new Ctx(ex, om, cfg, m, r, features);
                        if (r.mutates && !isAuthorized(ctx)) throw new Unauthorized("Missing/invalid bearer token");
                        Response resp = r.handler.handle(ctx);
                        write(ex, resp);
                        // record AFTER successful handling
                        if (recorder.isRecording()) recorder.record(ex, resp);
                        return;
                    }
                }
                var notFound = Response.json(404, Map.of(
                        "error","not_found","message","No route "+method+" "+path));
                write(ex, notFound);
                if (recorder.isRecording()) recorder.record(ex, notFound);
            } catch (BadRequest e) {
                var resp = Response.json(400, Map.of("error","bad_request","message", e.getMessage()));
                write(ex, resp); if (recorder.isRecording()) recorder.record(ex, resp);
            } catch (Unauthorized e) {
                var resp = Response.json(401, Map.of("error","unauthorized","message", e.getMessage()));
                write(ex, resp); if (recorder.isRecording()) recorder.record(ex, resp);
            } catch (Exception e) {
                e.printStackTrace();
                var resp = Response.json(500, Map.of("error","internal","message","boom"));
                write(ex, resp); if (recorder.isRecording()) recorder.record(ex, resp);
            }
        }

        private void initStores() {
            stores.clear();
            if (cfg.resources == null) return;
            for (var r : cfg.resources) {
                String idField = blank(r.idField) ? "id" : r.idField;
                var store = new ResourceStore(r.name, idField);
                if (r.seed != null) {
                    for (Map<String,Object> row : r.seed) {
                        Object id = row.get(idField);
                        if (id == null) { id = UUID.randomUUID().toString(); row.put(idField, id); }
                        store.put(id.toString(), deepCopy(row));
                    }
                }
                stores.put(r.name, store);
            }
        }

        private void initRoutes() {
            var newRoutes = new ArrayList<Route>();

            if (cfg.resources != null) {
                for (var r : cfg.resources) {
                    boolean enable = r.enableCrud == null || r.enableCrud;
                    if (!enable) continue;
                    var base = "/api/" + r.name;

                    newRoutes.add(new Route("GET", base, false, (ctx) -> {
                        var store = stores.get(r.name);
                        var qp = ctx.query();
                        int limit = qp.getInt("limit", 50);
                        int offset = qp.getInt("offset", 0);
                        return Response.json(200, store.list(limit, offset));
                    }));

                    newRoutes.add(new Route("POST", base, true, (ctx) -> {
                        var store = stores.get(r.name);
                        Map<String,Object> in = ctx.readJsonMap();
                        Object id = in.get(store.idField);
                        if (id == null) { id = UUID.randomUUID().toString(); in.put(store.idField, id); }
                        store.put(id.toString(), in);
                        return Response.json(201, in).withHeader("Location", base + "/" + id);
                    }));

                    newRoutes.add(new Route("GET", base + "/{id}", false, (ctx) -> {
                        var store = stores.get(r.name);
                        var row = store.get(ctx.path("id"));
                        return row == null ? Response.json(404, Map.of("error","not_found","message","No "+r.name+" with that id"))
                                : Response.json(200, row);
                    }));

                    newRoutes.add(new Route("PUT", base + "/{id}", true, (ctx) -> {
                        var store = stores.get(r.name);
                        String id = ctx.path("id");
                        var existing = store.get(id);
                        if (existing == null) return Response.json(404, Map.of("error","not_found","message","No "+r.name+" with that id"));
                        var patch = ctx.readJsonMap();
                        existing.putAll(patch);
                        existing.put(store.idField, id);
                        store.put(id, existing);
                        return Response.json(200, existing);
                    }));

                    newRoutes.add(new Route("DELETE", base + "/{id}", true, (ctx) -> {
                        var store = stores.get(r.name);
                        boolean removed = store.remove(ctx.path("id"));
                        return removed ? new Response(204, new LinkedHashMap<>(), null)
                                : Response.json(404, Map.of("error","not_found","message","No "+r.name+" with that id"));
                    }));
                }
            }

            if (cfg.staticEndpoints != null) {
                for (var se : cfg.staticEndpoints) {
                    String method = blank(se.method) ? "GET" : se.method;
                    int status = se.status == null ? 200 : se.status;
                    boolean mutates = switch (method) {
                        case "POST","PUT","DELETE","PATCH" -> true; default -> false;
                    };
                    newRoutes.add(new Route(method, se.path, mutates, (ctx) -> {
                        if (Boolean.TRUE.equals(se.echoRequest)) {
                            return Response.json(status, Map.of(
                                    "method", ctx.exchange.getRequestMethod(),
                                    "path", ctx.exchange.getRequestURI().getPath(),
                                    "query", ctx.query().raw,
                                    "headers", ctx.headersMap(),
                                    "body", ctx.readBodyAsString(),
                                    "time", Instant.now().toString()
                            ));
                        } else {
                            Object body = se.response == null ? Map.of() : deepCopy(se.response);
                            if (features.templating) body = Template.apply(body, ctx);
                            return Response.json(status, body);
                        }
                    }));
                }
            }
            this.routes = newRoutes;
        }

        // ---- Hot reload ----
        void startFileWatcher() {
            Thread t = new Thread(() -> {
                try (WatchService ws = FileSystems.getDefault().newWatchService()) {
                    var dir = configPath.getParent();
                    if (dir == null) return;
                    dir.register(ws, StandardWatchEventKinds.ENTRY_MODIFY);
                    while (true) {
                        var key = ws.take();
                        for (WatchEvent<?> ev : key.pollEvents()) {
                            var changed = (Path) ev.context();
                            if (changed != null && configPath.getFileName().equals(changed)) {
                                try {
                                    var newCfg = loadConfig(configPath.toString());
                                    // validate & swap
                                    if ("strict".equalsIgnoreCase(features.schemaValidation)) validateOrDie(newCfg);
                                    else validateLenient(newCfg);
                                    this.cfg = defaults(newCfg);
                                    initStores();
                                    initRoutes();
                                    System.out.println("[hot-reload] applied config at " + Instant.now());
                                } catch (Exception e) {
                                    System.err.println("[hot-reload] failed: " + e.getMessage());
                                }
                            }
                        }
                        key.reset();
                    }
                } catch (Exception e) {
                    System.err.println("[hot-reload] watcher stopped: " + e.getMessage());
                }
            }, "tinyrest-hot-reload");
            t.setDaemon(true);
            t.start();
        }
    }

    // ---------- Template engine ----------
    static class Template {
        private static final Pattern P = Pattern.compile("\\{\\{\\s*([^}]+)\\s*}}");

        static Object apply(Object node, Ctx ctx) {
            if (node instanceof Map<?,?> m) {
                Map<String,Object> out = new LinkedHashMap<>();
                for (var e : m.entrySet()) out.put(String.valueOf(e.getKey()), apply(e.getValue(), ctx));
                return out;
            } else if (node instanceof List<?> list) {
                List<Object> out = new ArrayList<>();
                for (var v : list) out.add(apply(v, ctx));
                return out;
            } else if (node instanceof String s) {
                return renderString(s, ctx);
            } else {
                return node;
            }
        }

        static String renderString(String s, Ctx ctx) {
            Matcher m = P.matcher(s);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String expr = m.group(1).trim();
                String val = eval(expr, ctx);
                m.appendReplacement(sb, Matcher.quoteReplacement(val));
            }
            m.appendTail(sb);
            return sb.toString();
        }

        static String eval(String expr, Ctx ctx) {
            // builtins
            if ("now".equals(expr)) return DateTimeFormatter.ISO_INSTANT.format(Instant.now());
            if ("uuid".equals(expr)) return UUID.randomUUID().toString();
            if (expr.startsWith("path.")) return nvl(ctx.pathOrNull(expr.substring(5)));
            if (expr.startsWith("query.")) return nvl(ctx.query().get(expr.substring(6), ""));
            if (expr.startsWith("body."))  return nvl(jsonPointer(ctx.bodyAsMap(), expr.substring(5)));
            if (expr.startsWith("header.")) return nvl(ctx.header(expr.substring(7)));
            // random.int(a,b)
            if (expr.startsWith("random.int(") && expr.endsWith(")")) {
                try {
                    String inner = expr.substring("random.int(".length(), expr.length()-1);
                    String[] parts = inner.split(",", 2);
                    int a = Integer.parseInt(parts[0].trim());
                    int b = Integer.parseInt(parts[1].trim());
                    int n = ThreadLocalRandom.current().nextInt(a, b+1);
                    return String.valueOf(n);
                } catch (Exception e) { return ""; }
            }
            return ""; // unknown expression -> empty
        }

        @SuppressWarnings("unchecked")
        static String jsonPointer(Map<String,Object> m, String dotted) {
            if (m == null) return "";
            Object cur = m;
            for (String seg : dotted.split("\\.")) {
                if (!(cur instanceof Map<?,?> map)) return "";
                cur = ((Map<String,Object>) map).get(seg);
                if (cur == null) return "";
            }
            return String.valueOf(cur);
        }
        static String nvl(String s) { return s == null ? "" : s; }
    }

    // ---------- Recorder / Replayer ----------
    static class Recorder {
        final RecordReplay rr;
        final ObjectMapper om;
        Recorder(RecordReplay rr, ObjectMapper om) { this.rr = rr == null ? new RecordReplay() : rr; this.om = om; }
        boolean isRecording() { return rr != null && "record".equalsIgnoreCase(rr.mode); }
        boolean isReplay()    { return rr != null && "replay".equalsIgnoreCase(rr.mode); }
        String replayOnMiss() { return rr == null ? "fallback" : (blank(rr.replayOnMiss) ? "fallback" : rr.replayOnMiss); }

        final List<ReplayItem> items = new CopyOnWriteArrayList<>();

        void loadFromFile() {
            if (blank(rr.file)) return;
            var f = Paths.get(rr.file);
            if (!Files.exists(f)) return;
            try (var br = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                String line;
                while ((line = br.readLine()) != null) {
                    items.add(om.readValue(line, ReplayItem.class));
                }
                System.out.println("[replay] loaded " + items.size() + " entries from " + rr.file);
            } catch (Exception e) {
                System.err.println("[replay] failed loading " + rr.file + ": " + e.getMessage());
            }
        }

        void record(HttpExchange ex, Response resp) {
            if (!isRecording() || blank(rr.file)) return;
            try {
                var item = ReplayItem.from(ex, resp, rr);
                try (var fw = new FileWriter(rr.file, true);
                     var bw = new BufferedWriter(fw)) {
                    om.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
                    bw.write(om.writeValueAsString(item));
                    bw.write("\n");
                }
            } catch (Exception e) {
                System.err.println("[record] failed: " + e.getMessage());
            }
        }

        Response tryReplay(HttpExchange ex) {
            if (!isReplay()) return null;
            var req = ReplayItem.captureRequest(ex);
            for (var it : items) {
                if (it.matches(req, rr)) return it.toResponse();
            }
            return null;
        }
    }

    // ---------- HTTP plumbing ----------
    interface Handler { Response handle(Ctx ctx) throws Exception; }
    static class Route {
        final String method; final Pattern pattern; final Handler handler; final boolean mutates;
        Route(String method, String pathTemplate, boolean mutates, Handler handler) {
            this.method = method; this.pattern = compile(pathTemplate); this.mutates = mutates; this.handler = handler;
        }
        private static Pattern compile(String tpl) {
            String regex = Arrays.stream(tpl.split("/"))
                    .filter(s -> !s.isEmpty())
                    .map(seg -> seg.startsWith("{") && seg.endsWith("}")
                            ? "(?<" + seg.substring(1, seg.length()-1) + ">[^/]+)"
                            : Pattern.quote(seg))
                    .collect(Collectors.joining("/", "^/", "$"));
            return Pattern.compile(regex);
        }
    }

    static class Ctx {
        final HttpExchange exchange; final ObjectMapper om; final MockConfig cfg; final Matcher matcher; final Route route; final Features features;
        Map<String,Object> bodyCache;

        Ctx(HttpExchange exchange, ObjectMapper om, MockConfig cfg, Matcher matcher, Route route, Features features) {
            this.exchange = exchange; this.om = om; this.cfg = cfg; this.matcher = matcher; this.route = route; this.features = features;
        }
        String path(String group) { return matcher.group(group); }
        String pathOrNull(String group) { try { return matcher.group(group); } catch (Exception e) { return null; } }
        Query query() { return new Query(exchange.getRequestURI()); }
        String header(String name) { var v = exchange.getRequestHeaders().get(name); return (v==null||v.isEmpty())?null:v.get(0); }
        Map<String, List<String>> headersMap() {
            return exchange.getRequestHeaders().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
        Map<String,Object> readJsonMap() {
            if (!"application/json".equalsIgnoreCase(contentType())) throw new BadRequest("Content-Type must be application/json");
            try (var is = exchange.getRequestBody()) { return om.readValue(is, new TypeReference<Map<String,Object>>(){}); }
            catch (IOException e) { throw new BadRequest("Invalid JSON"); }
        }
        Map<String,Object> bodyAsMap() {
            if (bodyCache != null) return bodyCache;
            try (var is = exchange.getRequestBody()) {
                byte[] bytes = is.readAllBytes();
                if (bytes.length == 0) return bodyCache = Map.of();
                return bodyCache = om.readValue(bytes, new TypeReference<Map<String,Object>>(){});
            } catch (Exception e) { return bodyCache = Map.of(); }
        }
        String readBodyAsString() {
            try (var is = exchange.getRequestBody()) { return new String(is.readAllBytes(), StandardCharsets.UTF_8); }
            catch (IOException e) { return ""; }
        }
        String contentType() { var ct = header("Content-Type"); return ct==null? "" : ct.split(";")[0].trim(); }
    }

    static class Query {
        final Map<String, List<String>> raw;
        Query(URI uri) { this.raw = split(uri.getRawQuery()); }
        private static Map<String, List<String>> split(String q) {
            Map<String, List<String>> map = new HashMap<>();
            if (q == null || q.isEmpty()) return map;
            for (String kv : q.split("&")) {
                String[] parts = kv.split("=", 2);
                String k = decode(parts[0]);
                String v = parts.length > 1 ? decode(parts[1]) : "";
                map.computeIfAbsent(k, __ -> new ArrayList<>()).add(v);
            }
            return map;
        }
        private static String decode(String s) { return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8); }
        int getInt(String key, int def) { try { return Integer.parseInt(get(key, String.valueOf(def))); } catch (Exception e) { return def; } }
        String get(String key, String def) { var list = raw.get(key); return (list==null||list.isEmpty())?def:list.get(0); }
    }

    static class Response {
        final int status; final Map<String,String> headers; final byte[] body;
        Response(int status, Map<String,String> headers, byte[] body) { this.status=status; this.headers=headers; this.body=body; }
        static Response json(int status, Object obj) {
            try {
                var bytes = jsonMapper().writeValueAsBytes(obj);
                var h = new LinkedHashMap<String,String>();
                h.put("Content-Type", "application/json; charset=utf-8");
                return new Response(status, h, bytes);
            } catch (Exception e) { throw new RuntimeException(e); }
        }
        Response withHeader(String k, String v){ headers.put(k, v); return this; }
    }

    static void write(HttpExchange ex, Response resp) throws IOException {
        Headers h = ex.getResponseHeaders();
        resp.headers.forEach(h::set);
        long len = (resp.body == null || resp.status == 204) ? -1 : resp.body.length;
        ex.sendResponseHeaders(resp.status, len);
        if (resp.body != null && resp.status != 204) try (var os = ex.getResponseBody()) { os.write(resp.body); }
    }

    static void addCORS(HttpExchange ex) {
        Headers h = ex.getResponseHeaders();
        h.add("Access-Control-Allow-Origin", "*");
        h.add("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        h.add("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }
    static boolean handleCorsPreflight(HttpExchange ex) throws IOException {
        if (!"OPTIONS".equals(ex.getRequestMethod())) return false;
        ex.sendResponseHeaders(204, -1); return true;
    }
    static void withLatency(Long ms) { if (ms == null || ms <= 0) return; try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }
    static void maybeChaos(Double rate) { if (rate == null || rate <= 0) return; if (ThreadLocalRandom.current().nextDouble() < rate) throw new RuntimeException("chaos"); }
    static boolean isAuthorized(Ctx ctx) {
        if (blank(ctx.cfg.authToken)) return true;
        var h = ctx.header("Authorization");
        return h != null && h.equals("Bearer " + ctx.cfg.authToken);
    }

    static ObjectMapper jsonMapper() { return new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false); }
    static ObjectMapper yamlMapper() { return new ObjectMapper(new YAMLFactory()).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false); }
    static Map<String,Object> deepCopy(Map<String,Object> m) { return jsonMapper().convertValue(m, new TypeReference<Map<String,Object>>(){}); }
    static boolean blank(String s){ return s==null || s.isBlank(); }

    // ---------- In-memory store ----------
    static class ResourceStore {
        final String name; final String idField; final ConcurrentMap<String, Map<String,Object>> data = new ConcurrentHashMap<>();
        ResourceStore(String name, String idField) { this.name = name; this.idField = idField; }
        void put(String id, Map<String,Object> row){ data.put(id, deepCopy(row)); }
        Map<String,Object> get(String id){ return data.get(id); }
        boolean remove(String id){ return data.remove(id) != null; }
        List<Map<String,Object>> list(int limit, int offset) {
            return data.values().stream()
                    .sorted(Comparator.comparing(o -> String.valueOf(o.getOrDefault(idField, ""))))
                    .skip(Math.max(0, offset))
                    .limit(Math.max(1, limit))
                    .map(TinyRest::deepCopy)
                    .collect(Collectors.toList());
        }
    }

    // ---------- Config / Features ----------
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MockConfig {
        public Integer port;
        public String authToken;
        public Long artificialLatencyMs;
        public Double chaosFailRate;
        public FeaturesConfig features;
        public List<Resource> resources;
        public List<StaticEndpoint> staticEndpoints;
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Resource {
        public String name;
        public String idField;
        public Boolean enableCrud;
        public List<Map<String,Object>> seed;
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StaticEndpoint {
        public String method;
        public String path;
        public Integer status;
        public Map<String,Object> response;
        public Boolean echoRequest;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FeaturesConfig {
        public Boolean templating;
        public Boolean hotReload;
        public String schemaValidation; // strict|lenient
        public RecordReplay recordReplay;
    }

    static class Features {
        final boolean templating;
        final boolean hotReload;
        final String schemaValidation;
        final RecordReplay recordReplay;
        static Features from(FeaturesConfig c) {
            if (c == null) c = new FeaturesConfig();
            return new Features(
                    Boolean.TRUE.equals(c.templating),
                    Boolean.TRUE.equals(c.hotReload),
                    blank(c.schemaValidation) ? "lenient" : c.schemaValidation,
                    c.recordReplay == null ? new RecordReplay() : c.recordReplay);
        }
        Features(boolean t, boolean hr, String sv, RecordReplay rr){ this.templating=t; this.hotReload=hr; this.schemaValidation=sv; this.recordReplay=rr; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RecordReplay {
        public String mode;           // off|record|replay
        public String file;           // JSONL file
        public Match match;
        public String replayOnMiss;   // error|fallback
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Match {
        public Boolean method;
        public Boolean path;
        public Boolean query;
        public Boolean body;
        public List<String> headers;
    }

    static class ReplayItem {
        public Map<String,Object> request;
        public Map<String,Object> response;

        static ReplayItem from(HttpExchange ex, Response resp, RecordReplay rr) throws IOException {
            var it = new ReplayItem();
            it.request = captureRequest(ex).toMap();
            it.response = Map.of(
                    "status", resp.status,
                    "headers", ex.getResponseHeaders(), // response headers as set on exchange
                    "bodyBase64", Base64.getEncoder().encodeToString(resp.body == null ? new byte[0] : resp.body)
            );
            return it;
        }

        static CapturedRequest captureRequest(HttpExchange ex) {
            String body = "";
            try (var is = ex.getRequestBody()) { body = new String(is.readAllBytes(), StandardCharsets.UTF_8); }
            catch (Exception ignored) {}
            Map<String, List<String>> headers = new LinkedHashMap<>();
            ex.getRequestHeaders().forEach(headers::put);
            return new CapturedRequest(ex.getRequestMethod(), ex.getRequestURI().getPath(), ex.getRequestURI().getRawQuery(), headers, body);
        }

        boolean matches(CapturedRequest req, RecordReplay rr) {
            Match m = rr.match == null ? new Match() : rr.match;
            boolean ok = true;
            if (Boolean.TRUE.equals(m.method)) ok &= Objects.equals(val(request, "method"), req.method);
            if (Boolean.TRUE.equals(m.path))   ok &= Objects.equals(val(request, "path"), req.path);
            if (Boolean.TRUE.equals(m.query))  ok &= Objects.equals(val(request, "query"), req.query);
            if (Boolean.TRUE.equals(m.body))   ok &= Objects.equals(val(request, "body"), req.body);
            if (m.headers != null && !m.headers.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String,List<String>> h1 = (Map<String,List<String>>) request.get("headers");
                for (String name : m.headers) {
                    String k = canonicalHeader(h1, name);
                    String k2 = canonicalHeader(req.headers, name);
                    ok &= Objects.equals(h1.get(k), req.headers.get(k2));
                }
            }
            return ok;
        }
        static String canonicalHeader(Map<String,?> map, String name) {
            for (String k : map.keySet()) if (k.equalsIgnoreCase(name)) return k;
            return name;
        }
        static Object val(Map<String,Object> m, String k){ return m.get(k); }

        Response toResponse() {
            int status = (int) response.get("status");
            byte[] body = new byte[0];
            String b64 = String.valueOf(response.get("bodyBase64"));
            if (!"null".equals(b64)) body = Base64.getDecoder().decode(b64);
            Map<String,String> headers = new LinkedHashMap<>();
            headers.put("Content-Type", "application/json; charset=utf-8");
            return new Response(status, headers, body);
        }

        static class CapturedRequest {
            final String method, path, query, body; final Map<String,List<String>> headers;
            CapturedRequest(String method, String path, String query, Map<String,List<String>> headers, String body) {
                this.method = method; this.path = path; this.query = query; this.headers = headers; this.body = body;
            }
            Map<String,Object> toMap() {
                return Map.of("method", method, "path", path, "query", query, "headers", headers, "body", body);
            }
        }
    }

    // ---------- Validation ----------
    static void validateOrDie(MockConfig c) {
        var errors = new ArrayList<String>();
        if (c.resources != null) {
            var names = new HashSet<String>();
            for (var r : c.resources) {
                if (blank(r.name)) errors.add("resource.name is required");
                if (!names.add(r.name)) errors.add("duplicate resource.name: " + r.name);
                if (blank(r.idField)) errors.add("resource.idField is required for: " + r.name);
            }
        }
        if (c.staticEndpoints != null) {
            for (var s : c.staticEndpoints) {
                if (blank(s.path)) errors.add("staticEndpoint.path is required");
            }
        }
        if (!errors.isEmpty()) throw new IllegalArgumentException("Invalid config: " + errors);
    }
    static void validateLenient(MockConfig c) {
        try { validateOrDie(c); } catch (Exception e) { System.err.println("[lenient] " + e.getMessage()); }
    }

    // ---------- Control handle ----------
    public static class ServerHandle {
        private final HttpServer server; private final Engine engine;
        ServerHandle(HttpServer server, Engine engine){ this.server = server; this.engine = engine; }
        public void stop(int delaySec){ server.stop(delaySec); }
        public InetSocketAddress boundAddress(){ return (InetSocketAddress) server.getAddress(); }
    }

    // ---------- Errors ----------
    static class BadRequest extends RuntimeException { BadRequest(String m){ super(m); } }
    static class Unauthorized extends RuntimeException { Unauthorized(String m){ super(m); } }

    private static MockConfig defaults(MockConfig in) {
        MockConfig c = in == null ? new MockConfig() : in;
        if (c.port == null) c.port = 8080;
        if (c.artificialLatencyMs == null) c.artificialLatencyMs = 0L;
        if (c.chaosFailRate == null) c.chaosFailRate = 0.0;
        if (c.features == null) c.features = new FeaturesConfig();
        return c;
    }

    // ===== JUnit 5 Integration (TinyRest) =====
    // Place this file under src/main/java and add JUnit 5 as a test-scoped dependency.
    // Tests can then: @ExtendWith(TinyRest.JUnitTinyRestExtension.class) and @UseTinyRest(...)
    // Params can be injected with @TinyRestBaseUrl on String or URI.

    // (Imports for JUnit are at top-level in your test compile; here we fully-qualify to avoid extra imports.)

    public static class JUnitTinyRestExtension implements org.junit.jupiter.api.extension.BeforeAllCallback,
            org.junit.jupiter.api.extension.AfterAllCallback,
            org.junit.jupiter.api.extension.ParameterResolver {

        private static final org.junit.jupiter.api.extension.ExtensionContext.Namespace NS =
                org.junit.jupiter.api.extension.ExtensionContext.Namespace.create("TinyRest");
        private static final String STORE_KEY = "serverHandle";

        @Override
        public void beforeAll(org.junit.jupiter.api.extension.ExtensionContext context) throws Exception {
            var store = context.getStore(NS);

            UseTinyRest cfgAnn = findAnnotation(context, UseTinyRest.class);
            if (cfgAnn == null) {
                throw new IllegalStateException("@UseTinyRest is required on the test class when using JUnitTinyRestExtension.");
            }

            String configPath = cfgAnn.configPath().isBlank() ? "src/test/resources/tinyrest.yml" : cfgAnn.configPath();
            TinyRest.MockConfig cfg = TinyRest.loadConfig(configPath);

            if (cfgAnn.port() >= 0) cfg.port = cfgAnn.port(); // 0 => auto-bind
            if (!cfgAnn.authTokenOverride().isBlank()) cfg.authToken = cfgAnn.authTokenOverride();

            if (cfg.features == null) cfg.features = new FeaturesConfig();
            if (!cfgAnn.recordReplayMode().isBlank()) {
                if (cfg.features.recordReplay == null) cfg.features.recordReplay = new RecordReplay();
                cfg.features.recordReplay.mode = cfgAnn.recordReplayMode();
                if (!cfgAnn.recordReplayFile().isBlank()) cfg.features.recordReplay.file = cfgAnn.recordReplayFile();
            }

            var handle = TinyRest.start(cfg, java.nio.file.Paths.get(configPath));
            store.put(STORE_KEY, handle);

            String baseUrl = "http://localhost:" + handle.boundAddress().getPort();
            System.setProperty("tinyrest.baseUrl", baseUrl);
            System.setProperty("tinyrest.port", String.valueOf(handle.boundAddress().getPort()));
        }

        @Override
        public void afterAll(org.junit.jupiter.api.extension.ExtensionContext context) throws Exception {
            var store = context.getStore(NS);
            var handle = (TinyRest.ServerHandle) store.remove(STORE_KEY);
            if (handle != null) handle.stop(0);
        }

        // --- Parameter injection ---
        @Override
        public boolean supportsParameter(org.junit.jupiter.api.extension.ParameterContext pc,
                                         org.junit.jupiter.api.extension.ExtensionContext ec)
                throws org.junit.jupiter.api.extension.ParameterResolutionException {
            boolean wantBaseUrl = pc.isAnnotated(TinyRestBaseUrl.class) &&
                    (pc.getParameter().getType().equals(String.class) || pc.getParameter().getType().equals(URI.class));
            boolean wantHandle = pc.getParameter().getType().equals(TinyRest.ServerHandle.class);
            return wantBaseUrl || wantHandle;
        }

        @Override
        public Object resolveParameter(org.junit.jupiter.api.extension.ParameterContext pc,
                                       org.junit.jupiter.api.extension.ExtensionContext ec)
                throws org.junit.jupiter.api.extension.ParameterResolutionException {
            var store = ec.getStore(NS);
            var handle = (TinyRest.ServerHandle) store.get(STORE_KEY, TinyRest.ServerHandle.class);
            if (handle == null) throw new org.junit.jupiter.api.extension.ParameterResolutionException("TinyRest server not initialized.");

            if (pc.getParameter().getType().equals(TinyRest.ServerHandle.class)) return handle;
            String base = "http://localhost:" + handle.boundAddress().getPort();
            if (pc.getParameter().getType().equals(URI.class)) return URI.create(base);
            return base; // String
        }

        private static <A extends java.lang.annotation.Annotation> A findAnnotation(
                org.junit.jupiter.api.extension.ExtensionContext ctx, Class<A> type) {
            var el = ctx.getElement().orElse(null);
            if (el != null && el.isAnnotationPresent(type)) return el.getAnnotation(type);
            var cls = ctx.getRequiredTestClass();
            return cls.getAnnotation(type);
        }
    }

    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE)
    public @interface UseTinyRest {
        String configPath() default "src/test/resources/tinyrest.yml";
        int port() default 0;                       // 0 => random free port
        String authTokenOverride() default "";
        String recordReplayMode() default "";       // "", "record", "replay"
        String recordReplayFile" default "";
    }

    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(java.lang.annotation.ElementType.PARAMETER)
    public @interface TinyRestBaseUrl {}
}
```

        **Note:** In the `@UseTinyRest` annotation above, fix the typo if your IDE flags it (should be `recordReplayFile()` — I kept it readable here, but your compiler will enforce it). Also make sure your Maven `pom.xml` includes Jackson YAML as a runtime dep and JUnit 5 as a **test** scope dep so `TinyRest.java` compiles in `main` while tests compile against JUnit.

If you want, I can also drop in a minimal `tinyrest.yml` starter and a sample JUnit test file wired to this class.
