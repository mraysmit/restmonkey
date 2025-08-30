
package dev.mars.tinyrest;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger log = LoggerFactory.getLogger(TinyRest.class);

    public static void main(String[] args) throws Exception {
        log.debug("TinyRest starting with {} command line arguments", args.length);
        if (args.length == 0) {
            log.error("No configuration file specified. Usage: java TinyRest <tinyrest.yml|json>");
            System.exit(1);
        }

        var path = Paths.get(args[0]).toAbsolutePath();
        log.info("TinyRest initializing with configuration file: {}", path);
        log.debug("Configuration file exists: {}, readable: {}", Files.exists(path), Files.isReadable(path));

        try {
            var cfg = loadConfig(path.toString());
            log.debug("Configuration loaded successfully. Port: {}, Auth: {}, Features: templating={}, hotReload={}",
                    cfg.port, cfg.authToken != null ? "[CONFIGURED]" : "[NONE]",
                    cfg.features != null && Boolean.TRUE.equals(cfg.features.templating),
                    cfg.features != null && Boolean.TRUE.equals(cfg.features.hotReload));

            log.info("Starting TinyRest server with {} resources, {} static endpoints, and {} features enabled",
                    cfg.resources != null ? cfg.resources.size() : 0,
                    cfg.staticEndpoints != null ? cfg.staticEndpoints.size() : 0,
                    countEnabledFeatures(cfg));

            var handle = start(cfg, path);
            log.info("TinyRest server successfully started on http://localhost:{}/", handle.boundAddress().getPort());
            log.info("Configuration file being watched: {}", path);
            log.debug("Server bound to address: {}, executor pool size: {}",
                    handle.boundAddress(), Runtime.getRuntime().availableProcessors());
        } catch (Exception e) {
            log.error("Failed to start TinyRest server: {}", e.getMessage(), e);
            throw e;
        }
    }

    // ---------- Public API ----------
    public static MockConfig loadConfig(String path) throws IOException {
        log.debug("Loading configuration from file: {}", path);
        try (var in = new FileInputStream(path)) {
            boolean yaml = path.endsWith(".yml") || path.endsWith(".yaml");
            log.debug("Configuration file format detected: {}", yaml ? "YAML" : "JSON");
            ObjectMapper mapper = yaml ? yamlMapper() : jsonMapper();

            var config = mapper.readValue(in, MockConfig.class);
            log.debug("Configuration parsed successfully. Raw config: port={}, resources={}, staticEndpoints={}",
                    config.port,
                    config.resources != null ? config.resources.size() : 0,
                    config.staticEndpoints != null ? config.staticEndpoints.size() : 0);

            return config;
        } catch (FileNotFoundException e) {
            log.error("Configuration file not found: {}", path);
            throw new IOException("Configuration file not found: " + path, e);
        } catch (IOException e) {
            log.error("Failed to read configuration file {}: {}", path, e.getMessage());
            throw new IOException("Failed to parse configuration file: " + e.getMessage(), e);
        }
    }

    private static int countEnabledFeatures(MockConfig cfg) {
        int count = 0;
        if (cfg.features != null) {
            if (Boolean.TRUE.equals(cfg.features.templating)) count++;
            if (Boolean.TRUE.equals(cfg.features.hotReload)) count++;
            if (cfg.features.recordReplay != null && !"off".equalsIgnoreCase(cfg.features.recordReplay.mode)) count++;
        }
        return count;
    }

    public static ServerHandle start(MockConfig cfg, Path configPath) throws IOException {
        int port = cfg.port != null ? cfg.port : 8080;
        log.debug("Creating HTTP server on port {} (0 = auto-assign)", port);

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            int threadPoolSize = Math.max(4, Runtime.getRuntime().availableProcessors());
            log.debug("Setting up thread pool executor with {} threads", threadPoolSize);
            server.setExecutor(Executors.newFixedThreadPool(threadPoolSize));

            log.debug("Initializing request engine with configuration defaults");
            var engine = new Engine(defaults(cfg), configPath);

            log.debug("Creating root context handler for all requests");
            server.createContext("/", ex -> {
                try {
                    engine.handle(ex);
                } catch (Exception e) {
                    log.error("Unhandled exception in request handler: {}", e.getMessage(), e);
                } finally {
                    ex.close();
                }
            });

            log.debug("Starting HTTP server...");
            server.start();
            int actualPort = server.getAddress().getPort();
            log.info("HTTP server started successfully on port {}", actualPort);

            if (engine.features.hotReload) {
                log.debug("Hot reload is enabled, starting file watcher");
                engine.startFileWatcher();
            } else {
                log.debug("Hot reload is disabled");
            }

            return new ServerHandle(server, engine);
        } catch (IOException e) {
            log.error("Failed to start HTTP server on port {}: {}", port, e.getMessage(), e);
            throw e;
        }
    }

    // ---------- Engine ----------
    static class Engine {
        private static final Logger log = LoggerFactory.getLogger(Engine.class);
        private static final Logger httpLog = LoggerFactory.getLogger("dev.mars.tinyrest.http");
        private static final Logger hotReloadLog = LoggerFactory.getLogger("dev.mars.tinyrest.hotreload");

        volatile MockConfig cfg;
        final ObjectMapper om = jsonMapper();
        final Map<String, ResourceStore> stores = new ConcurrentHashMap<>();
        volatile List<Route> routes = new CopyOnWriteArrayList<>();
        final Path configPath;
        final Features features;
        final Recorder recorder;

        Engine(MockConfig cfg, Path configPath) {
            log.debug("Engine initialization starting...");
            this.cfg = cfg;
            this.configPath = configPath;
            this.features = Features.from(cfg.features);

            log.info("Engine configuration: templating={}, hotReload={}, schemaValidation={}, recordReplay={}",
                    features.templating, features.hotReload, features.schemaValidation,
                    features.recordReplay != null ? features.recordReplay.mode : "off");

            log.debug("Configuration validation mode: {}", features.schemaValidation);
            if ("strict".equalsIgnoreCase(features.schemaValidation)) {
                log.debug("Running strict configuration validation...");
                try {
                    validateOrDie(cfg);
                    log.debug("Strict validation passed");
                } catch (Exception e) {
                    log.error("Strict validation failed: {}", e.getMessage());
                    throw e;
                }
            } else {
                log.debug("Running lenient configuration validation...");
                validateLenient(cfg);
            }

            log.debug("Initializing data stores...");
            initStores();

            log.debug("Initializing request routes...");
            initRoutes();

            log.debug("Initializing recorder with mode: {}",
                    features.recordReplay != null ? features.recordReplay.mode : "off");
            this.recorder = new Recorder(features.recordReplay, om);
            if (recorder.isReplay()) {
                log.debug("Loading replay data from file...");
                recorder.loadFromFile();
            }

            log.info("Engine initialized successfully: {} routes, {} stores, {} features active",
                    routes.size(), stores.size(), countActiveFeatures());
        }

        private int countActiveFeatures() {
            int count = 0;
            if (features.templating) count++;
            if (features.hotReload) count++;
            if (recorder.isRecording() || recorder.isReplay()) count++;
            return count;
        }

        void handle(HttpExchange ex) throws IOException {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            String query = ex.getRequestURI().getQuery();
            String remoteAddr = ex.getRemoteAddress().toString();
            long startTime = System.currentTimeMillis();

            log.trace("Incoming request: {} {} from {}", method, path, remoteAddr);
            httpLog.info("-> {} {} {}", method, path, query != null ? "?" + query : "");

            // Log request headers in debug mode
            if (log.isDebugEnabled()) {
                var headers = ex.getRequestHeaders();
                log.debug("Request headers: {}", headers.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size() > 1 ? e.getValue() : e.getValue().get(0))));
            }

            addCORS(ex);
            if (handleCorsPreflight(ex)) {
                log.debug("CORS preflight handled for {} {}", method, path);
                return;
            }

            // Apply artificial latency if configured
            if (cfg.artificialLatencyMs != null && cfg.artificialLatencyMs > 0) {
                log.debug("Applying artificial latency: {}ms", cfg.artificialLatencyMs);
                withLatency(cfg.artificialLatencyMs);
            }

            // Apply chaos engineering if configured
            if (cfg.chaosFailRate != null && cfg.chaosFailRate > 0) {
                log.debug("Chaos rate configured: {}", cfg.chaosFailRate);
                try {
                    maybeChaos(cfg.chaosFailRate);
                } catch (RuntimeException e) {
                    long duration = System.currentTimeMillis() - startTime;
                    log.warn("Chaos engineering triggered failure for {} {} ({}ms)", method, path, duration);
                    httpLog.warn("<- 500 {} {} ({}ms) - Chaos failure", method, path, duration);
                    var resp = Response.json(500, Map.of("error", "chaos", "message", "Simulated failure"));
                    write(ex, resp);
                    return;
                }
            }

            try {
                // record/replay — replay happens BEFORE any routing
                if (recorder.isReplay()) {
                    log.debug("Attempting replay for {} {}", method, path);
                    var hit = recorder.tryReplay(ex);
                    if (hit != null) {
                        long duration = System.currentTimeMillis() - startTime;
                        log.debug("Replay hit found for {} {}", method, path);
                        httpLog.info("<- {} {} {} ({}ms) [REPLAY]", hit.status, method, path, duration);
                        write(ex, hit);
                        return;
                    }

                    log.debug("No replay match found for {} {}", method, path);
                    if ("error".equalsIgnoreCase(recorder.replayOnMiss())) {
                        long duration = System.currentTimeMillis() - startTime;
                        log.warn("Replay miss configured as error for {} {}", method, path);
                        httpLog.warn("<- 501 {} {} ({}ms) [REPLAY MISS]", method, path, duration);
                        write(ex, Response.json(501, Map.of("error", "replay_miss", "path", path, "method", method)));
                        return;
                    }
                    log.debug("Replay miss, falling back to normal routing for {} {}", method, path);
                }

                log.debug("Searching for matching route among {} routes for {} {}", routes.size(), method, path);
                for (Route r : routes) {
                    Matcher m = r.pattern.matcher(path);
                    log.trace("Testing route pattern {} against path {}: method match={}, pattern match={}",
                            r.pattern.pattern(), path, r.method.equals(method), m.matches());

                    if (r.method.equals(method) && m.matches()) {
                        log.debug("Route matched: {} {} -> handler (mutates={})", method, path, r.mutates);

                        var ctx = new Ctx(ex, om, cfg, m, r, features);

                        // Check authorization for mutating operations
                        if (r.mutates) {
                            log.debug("Checking authorization for mutating operation {} {}", method, path);
                            if (!isAuthorized(ctx)) {
                                log.warn("Authorization failed for {} {} - missing or invalid bearer token", method, path);
                                throw new Unauthorized("Missing/invalid bearer token");
                            }
                            log.debug("Authorization successful for {} {}", method, path);
                        }

                        log.debug("Executing handler for {} {}", method, path);
                        Response resp = r.handler.handle(ctx);
                        long duration = System.currentTimeMillis() - startTime;

                        log.debug("Handler completed for {} {} -> {} ({}ms)", method, path, resp.status, duration);
                        httpLog.info("<- {} {} {} ({}ms)", resp.status, method, path, duration);

                        write(ex, resp);

                        // record AFTER successful handling
                        if (recorder.isRecording()) {
                            log.debug("Recording request/response for {} {}", method, path);
                            recorder.record(ex, resp);
                        }
                        return;
                    }
                }
                // No route found
                long duration = System.currentTimeMillis() - startTime;
                log.warn("No matching route found for {} {} after checking {} routes", method, path, routes.size());
                httpLog.warn("<- 404 {} {} ({}ms) - No matching route", method, path, duration);

                var notFound = Response.json(404, Map.of(
                        "error","not_found",
                        "message","No route "+method+" "+path,
                        "availableRoutes", routes.stream().map(r -> r.method + " " + r.pattern.pattern()).collect(Collectors.toList())));
                write(ex, notFound);
                if (recorder.isRecording()) {
                    log.debug("Recording 404 response for {} {}", method, path);
                    recorder.record(ex, notFound);
                }
            } catch (BadRequest e) {
                long duration = System.currentTimeMillis() - startTime;
                log.warn("Bad request for {} {}: {}", method, path, e.getMessage());
                httpLog.warn("<- 400 {} {} ({}ms) - {}", method, path, duration, e.getMessage());

                var resp = Response.json(400, Map.of(
                        "error","bad_request",
                        "message", e.getMessage(),
                        "path", path,
                        "method", method));
                write(ex, resp);
                if (recorder.isRecording()) {
                    log.debug("Recording 400 response for {} {}", method, path);
                    recorder.record(ex, resp);
                }
            } catch (Unauthorized e) {
                long duration = System.currentTimeMillis() - startTime;
                log.warn("Unauthorized access attempt for {} {}: {}", method, path, e.getMessage());
                httpLog.warn("<- 401 {} {} ({}ms) - {}", method, path, duration, e.getMessage());

                var resp = Response.json(401, Map.of(
                        "error","unauthorized",
                        "message", e.getMessage(),
                        "path", path,
                        "method", method,
                        "hint", "Include 'Authorization: Bearer <token>' header"));
                write(ex, resp);
                if (recorder.isRecording()) {
                    log.debug("Recording 401 response for {} {}", method, path);
                    recorder.record(ex, resp);
                }
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                log.error("Internal server error for {} {} ({}ms): {}", method, path, duration, e.getMessage(), e);
                httpLog.error("<- 500 {} {} ({}ms) - Internal error: {}", method, path, duration, e.getMessage());

                var resp = Response.json(500, Map.of(
                        "error","internal",
                        "message","Internal server error",
                        "path", path,
                        "method", method,
                        "timestamp", Instant.now().toString()));
                write(ex, resp);
                if (recorder.isRecording()) {
                    log.debug("Recording 500 response for {} {}", method, path);
                    recorder.record(ex, resp);
                }
            }
        }

        private void initStores() {
            log.debug("Initializing data stores...");
            stores.clear();

            if (cfg.resources == null || cfg.resources.isEmpty()) {
                log.info("No resources configured - no data stores created");
                return;
            }

            log.debug("Creating {} resource stores", cfg.resources.size());
            for (var r : cfg.resources) {
                log.debug("Processing resource configuration: name='{}', idField='{}', enableCrud={}, seedCount={}",
                        r.name, r.idField, r.enableCrud, r.seed != null ? r.seed.size() : 0);

                if (blank(r.name)) {
                    log.warn("Skipping resource with blank name");
                    continue;
                }

                String idField = blank(r.idField) ? "id" : r.idField;
                if (!r.idField.equals(idField)) {
                    log.debug("Using default ID field '{}' for resource '{}'", idField, r.name);
                }

                var store = new ResourceStore(r.name, idField);

                if (r.seed != null && !r.seed.isEmpty()) {
                    log.debug("Seeding resource '{}' with {} records", r.name, r.seed.size());
                    int seededCount = 0;
                    for (Map<String,Object> row : r.seed) {
                        Object id = row.get(idField);
                        if (id == null) {
                            id = UUID.randomUUID().toString();
                            row.put(idField, id);
                            log.trace("Generated ID '{}' for seed record in resource '{}'", id, r.name);
                        }
                        store.put(id.toString(), deepCopy(row));
                        seededCount++;
                        log.trace("Seeded record with ID '{}' in resource '{}'", id, r.name);
                    }
                    log.info("Initialized resource '{}' with {} seed records (idField='{}')",
                            r.name, seededCount, idField);
                } else {
                    log.info("Initialized empty resource '{}' (idField='{}')", r.name, idField);
                }

                stores.put(r.name, store);
            }

            log.debug("Store initialization complete: {} stores created", stores.size());
        }

        private void initRoutes() {
            log.debug("Initializing request routes...");
            var newRoutes = new ArrayList<Route>();
            int crudRoutes = 0;
            int staticRoutes = 0;

            // Generate CRUD routes for resources
            if (cfg.resources != null && !cfg.resources.isEmpty()) {
                log.debug("Processing {} resources for CRUD route generation", cfg.resources.size());

                for (var r : cfg.resources) {
                    boolean enable = r.enableCrud == null || r.enableCrud;
                    log.debug("Resource '{}': enableCrud={}", r.name, enable);

                    if (!enable) {
                        log.info("CRUD disabled for resource '{}' - skipping route generation", r.name);
                        continue;
                    }

                    if (blank(r.name)) {
                        log.warn("Skipping resource with blank name for route generation");
                        continue;
                    }

                    var base = "/api/" + r.name;
                    log.info("Creating CRUD routes for resource '{}' at base path '{}'", r.name, base);

                    // GET /api/{resource} - List resources
                    log.debug("  Adding route: GET {} (list resources)", base);
                    newRoutes.add(new Route("GET", base, false, (ctx) -> {
                        var store = stores.get(r.name);
                        var qp = ctx.query();
                        int limit = qp.getInt("limit", 50);
                        int offset = qp.getInt("offset", 0);
                        log.trace("Listing resources from '{}': limit={}, offset={}", r.name, limit, offset);
                        var result = store.list(limit, offset);
                        log.trace("Retrieved {} items from resource '{}'", result.size(), r.name);
                        return Response.json(200, result);
                    }));

                    // POST /api/{resource} - Create resource
                    log.debug("  Adding route: POST {} (create resource, auth required)", base);
                    newRoutes.add(new Route("POST", base, true, (ctx) -> {
                        var store = stores.get(r.name);
                        Map<String,Object> in = ctx.readJsonMap();
                        Object id = in.get(store.idField);
                        if (id == null) {
                            id = UUID.randomUUID().toString();
                            in.put(store.idField, id);
                            log.trace("Generated new ID '{}' for resource '{}'", id, r.name);
                        }
                        store.put(id.toString(), in);
                        log.trace("Created new resource '{}' with ID '{}'", r.name, id);
                        return Response.json(201, in).withHeader("Location", base + "/" + id);
                    }));

                    // GET /api/{resource}/{id} - Get specific resource
                    log.debug("  Adding route: GET {}/:id (get resource by ID)", base);
                    newRoutes.add(new Route("GET", base + "/{id}", false, (ctx) -> {
                        var store = stores.get(r.name);
                        String id = ctx.path("id");
                        log.trace("Looking up resource '{}' with ID '{}'", r.name, id);
                        var row = store.get(id);
                        if (row == null) {
                            log.trace("Resource '{}' with ID '{}' not found", r.name, id);
                            return Response.json(404, Map.of("error","not_found","message","No "+r.name+" with ID "+id));
                        }
                        log.trace("Found resource '{}' with ID '{}'", r.name, id);
                        return Response.json(200, row);
                    }));

                    // PUT /api/{resource}/{id} - Update resource
                    log.debug("  Adding route: PUT {}/:id (update resource, auth required)", base);
                    newRoutes.add(new Route("PUT", base + "/{id}", true, (ctx) -> {
                        var store = stores.get(r.name);
                        String id = ctx.path("id");
                        log.trace("Updating resource '{}' with ID '{}'", r.name, id);
                        var existing = store.get(id);
                        if (existing == null) {
                            log.trace("Cannot update - resource '{}' with ID '{}' not found", r.name, id);
                            return Response.json(404, Map.of("error","not_found","message","No "+r.name+" with ID "+id));
                        }
                        var patch = ctx.readJsonMap();
                        log.trace("Applying {} field updates to resource '{}' ID '{}'", patch.size(), r.name, id);
                        existing.putAll(patch);
                        existing.put(store.idField, id); // Ensure ID is preserved
                        store.put(id, existing);
                        log.trace("Successfully updated resource '{}' with ID '{}'", r.name, id);
                        return Response.json(200, existing);
                    }));

                    // DELETE /api/{resource}/{id} - Delete resource
                    log.debug("  Adding route: DELETE {}/:id (delete resource, auth required)", base);
                    newRoutes.add(new Route("DELETE", base + "/{id}", true, (ctx) -> {
                        var store = stores.get(r.name);
                        String id = ctx.path("id");
                        log.trace("Deleting resource '{}' with ID '{}'", r.name, id);
                        boolean removed = store.remove(id);
                        if (removed) {
                            log.trace("Successfully deleted resource '{}' with ID '{}'", r.name, id);
                            return new Response(204, new LinkedHashMap<>(), null);
                        } else {
                            log.trace("Cannot delete - resource '{}' with ID '{}' not found", r.name, id);
                            return Response.json(404, Map.of("error","not_found","message","No "+r.name+" with ID "+id));
                        }
                    }));

                    crudRoutes += 5; // 5 routes per resource (list, create, get, update, delete)
                }
            }

            // Generate static endpoints
            if (cfg.staticEndpoints != null && !cfg.staticEndpoints.isEmpty()) {
                log.debug("Processing {} static endpoints", cfg.staticEndpoints.size());

                for (var se : cfg.staticEndpoints) {
                    if (blank(se.path)) {
                        log.warn("Skipping static endpoint with blank path");
                        continue;
                    }

                    String method = blank(se.method) ? "GET" : se.method.toUpperCase();
                    int status = se.status == null ? 200 : se.status;
                    boolean mutates = switch (method) {
                        case "POST","PUT","DELETE","PATCH" -> true; default -> false;
                    };
                    boolean isEcho = Boolean.TRUE.equals(se.echoRequest);
                    boolean hasTemplating = features.templating && se.response != null;

                    log.info("Creating static endpoint: {} {} -> {} (echo={}, templating={}, auth={})",
                            method, se.path, status, isEcho, hasTemplating, mutates);

                    newRoutes.add(new Route(method, se.path, mutates, (ctx) -> {
                        if (Boolean.TRUE.equals(se.echoRequest)) {
                            log.trace("Echoing request details for {} {}", method, se.path);
                            return Response.json(status, Map.of(
                                    "method", ctx.exchange.getRequestMethod(),
                                    "path", ctx.exchange.getRequestURI().getPath(),
                                    "query", ctx.query().raw,
                                    "headers", ctx.headersMap(),
                                    "body", ctx.readBodyAsString(),
                                    "time", Instant.now().toString(),
                                    "endpoint", "static-echo"
                            ));
                        } else {
                            Object body = se.response == null ? Map.of() : deepCopy(se.response);
                            if (features.templating && body != null) {
                                log.trace("Applying templating to static response for {} {}", method, se.path);
                                body = Template.apply(body, ctx);
                            }
                            log.trace("Returning static response for {} {} with status {}", method, se.path, status);
                            return Response.json(status, body);
                        }
                    }));
                    staticRoutes++;
                }
                log.debug("Static endpoint processing complete: {} endpoints created", staticRoutes);
            } else {
                log.debug("No static endpoints configured");
            }

            this.routes = newRoutes;
            log.info("Route initialization complete: {} total routes ({} CRUD + {} static)",
                    newRoutes.size(), crudRoutes, staticRoutes);

            if (log.isDebugEnabled()) {
                log.debug("Route summary:");
                for (Route route : newRoutes) {
                    log.debug("  {} {} (mutates={})", route.method, route.pattern.pattern(), route.mutates);
                }
            }
        }

        // ---- Hot reload ----
        void startFileWatcher() {
            hotReloadLog.info("Starting file watcher for configuration hot reload: {}", configPath);
            Thread t = new Thread(() -> {
                try (WatchService ws = FileSystems.getDefault().newWatchService()) {
                    var dir = configPath.getParent();
                    if (dir == null) {
                        hotReloadLog.error("Cannot watch config file - no parent directory for: {}", configPath);
                        return;
                    }

                    dir.register(ws, StandardWatchEventKinds.ENTRY_MODIFY);
                    hotReloadLog.info("File watcher active - monitoring directory: {}", dir);
                    hotReloadLog.debug("Watching for changes to file: {}", configPath.getFileName());

                    while (true) {
                        var key = ws.take();
                        hotReloadLog.trace("File system event detected, processing {} events", key.pollEvents().size());

                        for (WatchEvent<?> ev : key.pollEvents()) {
                            var changed = (Path) ev.context();
                            hotReloadLog.trace("File changed: {} (looking for: {})", changed, configPath.getFileName());

                            if (changed != null && configPath.getFileName().equals(changed)) {
                                hotReloadLog.info("Configuration file changed, initiating hot reload...");
                                long reloadStart = System.currentTimeMillis();

                                try {
                                    // Load new configuration
                                    hotReloadLog.debug("Loading new configuration from: {}", configPath);
                                    var newCfg = loadConfig(configPath.toString());

                                    // Validate configuration
                                    hotReloadLog.debug("Validating new configuration (mode: {})", features.schemaValidation);
                                    if ("strict".equalsIgnoreCase(features.schemaValidation)) {
                                        validateOrDie(newCfg);
                                        hotReloadLog.debug("Strict validation passed");
                                    } else {
                                        validateLenient(newCfg);
                                    }

                                    // Apply new configuration
                                    var oldRouteCount = routes.size();
                                    var oldStoreCount = stores.size();

                                    this.cfg = defaults(newCfg);
                                    hotReloadLog.debug("Reinitializing data stores...");
                                    initStores();
                                    hotReloadLog.debug("Reinitializing routes...");
                                    initRoutes();

                                    long reloadDuration = System.currentTimeMillis() - reloadStart;
                                    hotReloadLog.info("Hot reload completed successfully in {}ms at {}",
                                            reloadDuration, Instant.now());
                                    hotReloadLog.info("Configuration changes: routes {} -> {}, stores {} -> {}",
                                            oldRouteCount, routes.size(), oldStoreCount, stores.size());

                                } catch (Exception e) {
                                    long reloadDuration = System.currentTimeMillis() - reloadStart;
                                    hotReloadLog.error("Hot reload failed after {}ms: {}", reloadDuration, e.getMessage(), e);
                                    hotReloadLog.warn("Server continues with previous configuration");
                                }
                            }
                        }
                        key.reset();
                    }
                } catch (InterruptedException e) {
                    hotReloadLog.info("File watcher interrupted - hot reload disabled");
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    hotReloadLog.error("File watcher stopped unexpectedly: {}", e.getMessage(), e);
                }
            }, "tinyrest-hot-reload");
            t.setDaemon(true);
            t.start();
            hotReloadLog.debug("Hot reload thread started successfully");
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
        private static final Logger recorderLog = LoggerFactory.getLogger("dev.mars.tinyrest.recorder");

        final RecordReplay rr;
        final ObjectMapper om;
        Recorder(RecordReplay rr, ObjectMapper om) {
            this.rr = rr == null ? new RecordReplay() : rr;
            this.om = om;

            String mode = this.rr.mode != null ? this.rr.mode.toLowerCase() : "off";
            recorderLog.debug("Initializing recorder with mode: {}", mode);

            if (isRecording()) {
                recorderLog.info("Recording mode enabled - capturing requests to: {}",
                        blank(this.rr.file) ? "[NO FILE SPECIFIED]" : this.rr.file);
                if (blank(this.rr.file)) {
                    recorderLog.warn("Recording enabled but no file specified - requests will not be saved");
                }
            } else if (isReplay()) {
                recorderLog.info("Replay mode enabled - replaying from: {}",
                        blank(this.rr.file) ? "[NO FILE SPECIFIED]" : this.rr.file);
                recorderLog.debug("Replay on miss strategy: {}", replayOnMiss());
            } else {
                recorderLog.debug("Record/replay disabled");
            }
        }
        boolean isRecording() { return rr != null && "record".equalsIgnoreCase(rr.mode); }
        boolean isReplay()    { return rr != null && "replay".equalsIgnoreCase(rr.mode); }
        String replayOnMiss() { return rr == null ? "fallback" : (blank(rr.replayOnMiss) ? "fallback" : rr.replayOnMiss); }

        final List<ReplayItem> items = new CopyOnWriteArrayList<>();

        void loadFromFile() {
            if (blank(rr.file)) {
                recorderLog.debug("No replay file specified - replay will be empty");
                return;
            }

            var f = Paths.get(rr.file);
            recorderLog.debug("Loading replay data from: {}", f.toAbsolutePath());

            if (!Files.exists(f)) {
                recorderLog.warn("Replay file does not exist: {} - replay will be empty", rr.file);
                return;
            }

            if (!Files.isReadable(f)) {
                recorderLog.error("Replay file is not readable: {}", rr.file);
                return;
            }

            long fileSize = 0;
            try {
                fileSize = Files.size(f);
                recorderLog.debug("Replay file size: {} bytes", fileSize);
            } catch (IOException e) {
                recorderLog.warn("Cannot determine replay file size: {}", e.getMessage());
            }

            try (var br = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                String line;
                int lineNumber = 0;
                int loadedCount = 0;
                int errorCount = 0;

                while ((line = br.readLine()) != null) {
                    lineNumber++;
                    if (line.trim().isEmpty()) {
                        recorderLog.trace("Skipping empty line {} in replay file", lineNumber);
                        continue;
                    }

                    try {
                        var item = om.readValue(line, ReplayItem.class);
                        items.add(item);
                        loadedCount++;
                        recorderLog.trace("Loaded replay item {} from line {}", loadedCount, lineNumber);
                    } catch (Exception e) {
                        errorCount++;
                        recorderLog.warn("Failed to parse replay item at line {}: {}", lineNumber, e.getMessage());
                        if (errorCount > 10) {
                            recorderLog.error("Too many parse errors ({}), stopping replay file loading", errorCount);
                            break;
                        }
                    }
                }

                recorderLog.info("Loaded {} replay entries from {} ({} lines processed, {} errors)",
                        loadedCount, rr.file, lineNumber, errorCount);

                if (loadedCount > 0) {
                    recorderLog.debug("Replay entries summary: {} unique request patterns loaded",
                            items.stream().map(item -> item.request.get("method") + " " + item.request.get("path")).distinct().count());
                }

            } catch (Exception e) {
                recorderLog.error("Failed loading replay file {}: {}", rr.file, e.getMessage(), e);
            }
        }

        void record(HttpExchange ex, Response resp) {
            if (!isRecording()) {
                recorderLog.trace("Recording skipped - not in recording mode");
                return;
            }

            if (blank(rr.file)) {
                recorderLog.warn("Recording skipped - no file specified");
                return;
            }

            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();

            recorderLog.trace("Recording request: {} {} -> {}", method, path, resp.status);

            try {
                var item = ReplayItem.from(ex, resp, rr);

                // Ensure directory exists
                var file = Paths.get(rr.file);
                var dir = file.getParent();
                if (dir != null && !Files.exists(dir)) {
                    Files.createDirectories(dir);
                    recorderLog.debug("Created recording directory: {}", dir);
                }

                try (var fw = new FileWriter(rr.file, true);
                     var bw = new BufferedWriter(fw)) {
                    om.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
                    String json = om.writeValueAsString(item);
                    bw.write(json);
                    bw.write("\n");
                    bw.flush();
                }

                recorderLog.debug("Recorded: {} {} -> {} (file: {})", method, path, resp.status, rr.file);

                // Log file size periodically
                try {
                    long fileSize = Files.size(file);
                    if (fileSize > 0 && fileSize % (1024 * 1024) == 0) { // Every MB
                        recorderLog.info("Recording file size: {} MB", fileSize / (1024 * 1024));
                    }
                } catch (IOException ignored) {
                    // File size check is not critical
                }

            } catch (Exception e) {
                recorderLog.error("Recording failed for {} {}: {}", method, path, e.getMessage(), e);
            }
        }

        Response tryReplay(HttpExchange ex) {
            if (!isReplay()) {
                recorderLog.trace("Replay skipped - not in replay mode");
                return null;
            }

            if (items.isEmpty()) {
                recorderLog.trace("Replay skipped - no replay items loaded");
                return null;
            }

            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();

            recorderLog.trace("Searching for replay match: {} {} among {} items", method, path, items.size());

            var req = ReplayItem.captureRequest(ex);
            int checkedCount = 0;

            for (var it : items) {
                checkedCount++;
                recorderLog.trace("Checking replay item {}/{}: {} {}", checkedCount, items.size(),
                        it.request.get("method"), it.request.get("path"));

                if (it.matches(req, rr)) {
                    int status = (int) it.response.get("status");
                    recorderLog.debug("Replay match found for {} {} -> {} (item {}/{})",
                            method, path, status, checkedCount, items.size());
                    return it.toResponse();
                }
            }

            recorderLog.debug("No replay match found for {} {} after checking {} items",
                    method, path, checkedCount);
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
        final String name; 
        final String idField; 
        final ConcurrentMap<String, Map<String,Object>> data = new ConcurrentHashMap<>();
        
        ResourceStore(String name, String idField) { 
            this.name = name; this.idField = idField; 
        }
        
        void put(String id, Map<String,Object> row){
            data.put(id, deepCopy(row)); 
        }
        
        Map<String,Object> get(String id){ 
            return data.get(id); 
        }
        
        boolean remove(String id){ 
            return data.remove(id) != null; 
        }
        
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
        public LoggingConfig logging;
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
    public static class LoggingConfig {
        public String level; // TRACE, DEBUG, INFO, WARN, ERROR
        public Boolean httpRequests;
        public Boolean enableFileLogging;
        public String logDirectory;
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
                var map = new LinkedHashMap<String,Object>();
                map.put("method", method);
                map.put("path", path);
                map.put("query", query != null ? query : "");
                map.put("headers", headers != null ? headers : Map.of());
                map.put("body", body != null ? body : "");
                return map;
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
        try {
            validateOrDie(c);
        } catch (Exception e) {
            LoggerFactory.getLogger(TinyRest.class).warn("Configuration validation (lenient mode): {}", e.getMessage());
        }
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
        String recordReplayFile() default "";
    }

    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(java.lang.annotation.ElementType.PARAMETER)
    public @interface TinyRestBaseUrl {}
}


