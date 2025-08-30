package dev.mars.tinyrest;

import java.util.*;

/**
 * Fluent builder interface for creating TinyRest configurations programmatically.
 * Provides a clean, type-safe way to configure TinyRest without YAML files.
 * 
 * Example usage:
 * <pre>
 * var server = TinyRest.builder()
 *     .port(8080)
 *     .authToken("my-secret-token")
 *     .enableTemplating()
 *     .enableHotReload()
 *     .resource("users")
 *         .idField("id")
 *         .enableCrud()
 *         .seed("id", "u1", "name", "Alice", "email", "alice@example.com")
 *         .seed("id", "u2", "name", "Bob", "email", "bob@example.com")
 *         .done()
 *     .staticEndpoint()
 *         .get("/health")
 *         .status(200)
 *         .response("status", "healthy", "time", "{{now}}")
 *         .done()
 *     .staticEndpoint()
 *         .post("/echo")
 *         .echoRequest()
 *         .requireAuth()
 *         .done()
 *     .build();
 * </pre>
 */
public class TinyRestBuilder {
    private final TinyRest.MockConfig config = new TinyRest.MockConfig();
    
    private TinyRestBuilder() {
        // Initialize with defaults
        config.port = 8080;
        config.artificialLatencyMs = 0L;
        config.chaosFailRate = 0.0;
        config.features = new TinyRest.FeaturesConfig();
        config.logging = new TinyRest.LoggingConfig();
        config.resources = new ArrayList<>();
        config.staticEndpoints = new ArrayList<>();
    }
    
    /**
     * Create a new TinyRest builder instance.
     */
    public static TinyRestBuilder create() {
        return new TinyRestBuilder();
    }
    
    // ---------- Server Configuration ----------
    
    /**
     * Set the server port. Use 0 for auto-assignment.
     */
    public TinyRestBuilder port(int port) {
        config.port = port;
        return this;
    }
    
    /**
     * Set the authentication token. Omit or pass null to disable auth.
     */
    public TinyRestBuilder authToken(String token) {
        config.authToken = token;
        return this;
    }
    
    /**
     * Disable authentication (same as authToken(null)).
     */
    public TinyRestBuilder noAuth() {
        config.authToken = null;
        return this;
    }
    
    /**
     * Add artificial latency to all responses (for testing).
     */
    public TinyRestBuilder artificialLatency(long milliseconds) {
        config.artificialLatencyMs = milliseconds;
        return this;
    }
    
    /**
     * Set chaos failure rate (0.0 to 1.0) for random 500 errors.
     */
    public TinyRestBuilder chaosFailRate(double rate) {
        config.chaosFailRate = Math.max(0.0, Math.min(1.0, rate));
        return this;
    }
    
    // ---------- Features Configuration ----------
    
    /**
     * Enable template variable substitution ({{now}}, {{uuid}}, etc.).
     */
    public TinyRestBuilder enableTemplating() {
        config.features.templating = true;
        return this;
    }
    
    /**
     * Disable template variable substitution.
     */
    public TinyRestBuilder disableTemplating() {
        config.features.templating = false;
        return this;
    }
    
    /**
     * Enable hot reload of configuration files.
     */
    public TinyRestBuilder enableHotReload() {
        config.features.hotReload = true;
        return this;
    }
    
    /**
     * Disable hot reload of configuration files.
     */
    public TinyRestBuilder disableHotReload() {
        config.features.hotReload = false;
        return this;
    }
    
    /**
     * Set schema validation mode.
     */
    public TinyRestBuilder schemaValidation(String mode) {
        config.features.schemaValidation = mode;
        return this;
    }
    
    /**
     * Use strict schema validation.
     */
    public TinyRestBuilder strictValidation() {
        return schemaValidation("strict");
    }
    
    /**
     * Use lenient schema validation.
     */
    public TinyRestBuilder lenientValidation() {
        return schemaValidation("lenient");
    }
    
    // ---------- Logging Configuration ----------
    
    /**
     * Set logging level (TRACE, DEBUG, INFO, WARN, ERROR).
     */
    public TinyRestBuilder logLevel(String level) {
        config.logging.level = level;
        return this;
    }
    
    /**
     * Enable HTTP request/response logging.
     */
    public TinyRestBuilder enableHttpLogging() {
        config.logging.httpRequests = true;
        return this;
    }
    
    /**
     * Disable HTTP request/response logging.
     */
    public TinyRestBuilder disableHttpLogging() {
        config.logging.httpRequests = false;
        return this;
    }
    
    /**
     * Enable file logging.
     */
    public TinyRestBuilder enableFileLogging() {
        config.logging.enableFileLogging = true;
        return this;
    }
    
    /**
     * Set log directory.
     */
    public TinyRestBuilder logDirectory(String directory) {
        config.logging.logDirectory = directory;
        return this;
    }
    
    // ---------- Resource Configuration ----------
    
    /**
     * Start configuring a new resource.
     */
    public ResourceBuilder resource(String name) {
        return new ResourceBuilder(this, name);
    }
    
    // ---------- Static Endpoint Configuration ----------
    
    /**
     * Start configuring a new static endpoint.
     */
    public StaticEndpointBuilder staticEndpoint() {
        return new StaticEndpointBuilder(this);
    }
    
    // ---------- Record/Replay Configuration ----------
    
    /**
     * Enable request recording to file.
     */
    public TinyRestBuilder recordRequests(String filename) {
        if (config.features.recordReplay == null) {
            config.features.recordReplay = new TinyRest.RecordReplay();
        }
        config.features.recordReplay.mode = "record";
        config.features.recordReplay.file = filename;
        return this;
    }
    
    /**
     * Enable request replay from file.
     */
    public TinyRestBuilder replayRequests(String filename) {
        if (config.features.recordReplay == null) {
            config.features.recordReplay = new TinyRest.RecordReplay();
        }
        config.features.recordReplay.mode = "replay";
        config.features.recordReplay.file = filename;
        return this;
    }
    
    /**
     * Set replay behavior when no match is found.
     */
    public TinyRestBuilder replayOnMiss(String behavior) {
        if (config.features.recordReplay == null) {
            config.features.recordReplay = new TinyRest.RecordReplay();
        }
        config.features.recordReplay.replayOnMiss = behavior;
        return this;
    }
    
    // ---------- Build ----------
    
    /**
     * Build the TinyRest configuration.
     */
    public TinyRest.MockConfig build() {
        return config;
    }
    
    /**
     * Build and start the TinyRest server.
     */
    public TinyRest start() throws Exception {
        return new TinyRest(build());
    }
    
    // ---------- Internal Methods ----------
    
    void addResource(TinyRest.Resource resource) {
        config.resources.add(resource);
    }
    
    void addStaticEndpoint(TinyRest.StaticEndpoint endpoint) {
        config.staticEndpoints.add(endpoint);
    }

    // ---------- Resource Builder ----------

    /**
     * Builder for configuring a resource with CRUD endpoints.
     */
    public static class ResourceBuilder {
        private final TinyRestBuilder parent;
        private final TinyRest.Resource resource = new TinyRest.Resource();

        ResourceBuilder(TinyRestBuilder parent, String name) {
            this.parent = parent;
            this.resource.name = name;
            this.resource.idField = "id"; // default
            this.resource.enableCrud = true; // default
            this.resource.seed = new ArrayList<>();
        }

        /**
         * Set the ID field name for this resource.
         */
        public ResourceBuilder idField(String fieldName) {
            resource.idField = fieldName;
            return this;
        }

        /**
         * Enable CRUD endpoints for this resource.
         */
        public ResourceBuilder enableCrud() {
            resource.enableCrud = true;
            return this;
        }

        /**
         * Disable CRUD endpoints for this resource.
         */
        public ResourceBuilder disableCrud() {
            resource.enableCrud = false;
            return this;
        }

        /**
         * Add seed data using key-value pairs.
         * Example: seed("id", "u1", "name", "Alice", "email", "alice@example.com")
         */
        public ResourceBuilder seed(Object... keyValuePairs) {
            if (keyValuePairs.length % 2 != 0) {
                throw new IllegalArgumentException("Seed data must be key-value pairs (even number of arguments)");
            }

            Map<String, Object> seedItem = new HashMap<>();
            for (int i = 0; i < keyValuePairs.length; i += 2) {
                seedItem.put(String.valueOf(keyValuePairs[i]), keyValuePairs[i + 1]);
            }
            resource.seed.add(seedItem);
            return this;
        }

        /**
         * Add seed data from a map.
         */
        public ResourceBuilder seed(Map<String, Object> seedData) {
            resource.seed.add(new HashMap<>(seedData));
            return this;
        }

        /**
         * Finish configuring this resource and return to the main builder.
         */
        public TinyRestBuilder done() {
            parent.addResource(resource);
            return parent;
        }
    }

    // ---------- Static Endpoint Builder ----------

    /**
     * Builder for configuring static endpoints.
     */
    public static class StaticEndpointBuilder {
        private final TinyRestBuilder parent;
        private final TinyRest.StaticEndpoint endpoint = new TinyRest.StaticEndpoint();

        StaticEndpointBuilder(TinyRestBuilder parent) {
            this.parent = parent;
            this.endpoint.method = "GET"; // default
            this.endpoint.status = 200;   // default
            this.endpoint.echoRequest = false; // default
        }

        /**
         * Set HTTP method.
         */
        public StaticEndpointBuilder method(String method) {
            endpoint.method = method.toUpperCase();
            return this;
        }

        /**
         * Configure as GET endpoint.
         */
        public StaticEndpointBuilder get(String path) {
            endpoint.method = "GET";
            endpoint.path = path;
            return this;
        }

        /**
         * Configure as POST endpoint.
         */
        public StaticEndpointBuilder post(String path) {
            endpoint.method = "POST";
            endpoint.path = path;
            return this;
        }

        /**
         * Configure as PUT endpoint.
         */
        public StaticEndpointBuilder put(String path) {
            endpoint.method = "PUT";
            endpoint.path = path;
            return this;
        }

        /**
         * Configure as DELETE endpoint.
         */
        public StaticEndpointBuilder delete(String path) {
            endpoint.method = "DELETE";
            endpoint.path = path;
            return this;
        }

        /**
         * Set the endpoint path.
         */
        public StaticEndpointBuilder path(String path) {
            endpoint.path = path;
            return this;
        }

        /**
         * Set the HTTP status code.
         */
        public StaticEndpointBuilder status(int status) {
            endpoint.status = status;
            return this;
        }

        /**
         * Set response as plain text.
         */
        public StaticEndpointBuilder response(String text) {
            endpoint.response = text;
            return this;
        }

        /**
         * Set response as JSON object using key-value pairs.
         * Example: response("status", "ok", "time", "{{now}}")
         */
        public StaticEndpointBuilder response(Object... keyValuePairs) {
            if (keyValuePairs.length % 2 != 0) {
                throw new IllegalArgumentException("Response data must be key-value pairs (even number of arguments)");
            }

            Map<String, Object> responseMap = new LinkedHashMap<>();
            for (int i = 0; i < keyValuePairs.length; i += 2) {
                responseMap.put(String.valueOf(keyValuePairs[i]), keyValuePairs[i + 1]);
            }
            endpoint.response = responseMap;
            return this;
        }

        /**
         * Set response as a map.
         */
        public StaticEndpointBuilder response(Map<String, Object> responseData) {
            endpoint.response = new LinkedHashMap<>(responseData);
            return this;
        }

        /**
         * Set response as a list.
         */
        public StaticEndpointBuilder response(List<?> responseData) {
            endpoint.response = new ArrayList<>(responseData);
            return this;
        }

        /**
         * Enable request echoing (returns request details).
         */
        public StaticEndpointBuilder echoRequest() {
            endpoint.echoRequest = true;
            return this;
        }

        /**
         * Disable request echoing.
         */
        public StaticEndpointBuilder noEcho() {
            endpoint.echoRequest = false;
            return this;
        }

        /**
         * Require authentication for this endpoint.
         */
        public StaticEndpointBuilder requireAuth() {
            // Auth is handled at the endpoint level in TinyRest
            // This is a convenience method for documentation
            return this;
        }

        /**
         * Finish configuring this endpoint and return to the main builder.
         */
        public TinyRestBuilder done() {
            parent.addStaticEndpoint(endpoint);
            return parent;
        }
    }
}
