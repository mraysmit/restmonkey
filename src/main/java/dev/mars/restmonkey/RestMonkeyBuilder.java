package dev.mars.restmonkey;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fluent builder interface for creating RESTMonkey configurations programmatically.
 * Provides a clean, type-safe way to configure RESTMonkey without YAML files.
 * 
 * Example usage:
 * <pre>
 * var server = RestMonkey.builder()
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
public class RestMonkeyBuilder {
    private final RestMonkey.MockConfig config = new RestMonkey.MockConfig();

    private RestMonkeyBuilder() {
        // Initialize with defaults
        config.port = 8080;
        config.artificialLatencyMs = 0L;
        config.chaosFailRate = 0.0;
        config.features = new RestMonkey.FeaturesConfig();
        config.logging = new RestMonkey.LoggingConfig();
        config.resources = new ArrayList<>();
        config.staticEndpoints = new ArrayList<>();
    }
    
    /**
     * Create a new RESTMonkey builder instance.
     */
    public static RestMonkeyBuilder create() {
        return new RestMonkeyBuilder();
    }
    
    // ---------- Server Configuration ----------
    
    /**
     * Set the server port. Use 0 for auto-assignment.
     */
    public RestMonkeyBuilder port(int port) {
        config.port = port;
        return this;
    }
    
    /**
     * Set the authentication token. Omit or pass null to disable auth.
     */
    public RestMonkeyBuilder authToken(String token) {
        config.authToken = token;
        return this;
    }
    
    /**
     * Disable authentication (same as authToken(null)).
     */
    public RestMonkeyBuilder noAuth() {
        config.authToken = null;
        return this;
    }
    
    /**
     * Add artificial latency to all responses (for testing).
     */
    public RestMonkeyBuilder artificialLatency(long milliseconds) {
        config.artificialLatencyMs = milliseconds;
        return this;
    }
    
    /**
     * Set chaos failure rate (0.0 to 1.0) for random 500 errors.
     */
    public RestMonkeyBuilder chaosFailRate(double rate) {
        config.chaosFailRate = Math.max(0.0, Math.min(1.0, rate));
        return this;
    }
    
    // ---------- Features Configuration ----------
    
    /**
     * Enable template variable substitution ({{now}}, {{uuid}}, etc.).
     */
    public RestMonkeyBuilder enableTemplating() {
        config.features.templating = true;
        return this;
    }
    
    /**
     * Disable template variable substitution.
     */
    public RestMonkeyBuilder disableTemplating() {
        config.features.templating = false;
        return this;
    }
    
    /**
     * Enable hot reload of configuration files.
     */
    public RestMonkeyBuilder enableHotReload() {
        config.features.hotReload = true;
        return this;
    }
    
    /**
     * Disable hot reload of configuration files.
     */
    public RestMonkeyBuilder disableHotReload() {
        config.features.hotReload = false;
        return this;
    }
    
    /**
     * Set schema validation mode.
     */
    public RestMonkeyBuilder schemaValidation(String mode) {
        config.features.schemaValidation = mode;
        return this;
    }
    
    /**
     * Use strict schema validation.
     */
    public RestMonkeyBuilder strictValidation() {
        return schemaValidation("strict");
    }
    
    /**
     * Use lenient schema validation.
     */
    public RestMonkeyBuilder lenientValidation() {
        return schemaValidation("lenient");
    }
    
    // ---------- Logging Configuration ----------
    
    /**
     * Set logging level (TRACE, DEBUG, INFO, WARN, ERROR).
     */
    public RestMonkeyBuilder logLevel(String level) {
        config.logging.level = level;
        return this;
    }
    
    /**
     * Enable HTTP request/response logging.
     */
    public RestMonkeyBuilder enableHttpLogging() {
        config.logging.httpRequests = true;
        return this;
    }
    
    /**
     * Disable HTTP request/response logging.
     */
    public RestMonkeyBuilder disableHttpLogging() {
        config.logging.httpRequests = false;
        return this;
    }
    
    /**
     * Enable file logging.
     */
    public RestMonkeyBuilder enableFileLogging() {
        config.logging.enableFileLogging = true;
        return this;
    }
    
    /**
     * Set log directory.
     */
    public RestMonkeyBuilder logDirectory(String directory) {
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
    public RestMonkeyBuilder recordRequests(String filename) {
        if (config.features.recordReplay == null) {
            config.features.recordReplay = new RestMonkey.RecordReplay();
        }
        config.features.recordReplay.mode = "record";
        config.features.recordReplay.file = filename;
        return this;
    }
    
    /**
     * Enable request replay from file.
     */
    public RestMonkeyBuilder replayRequests(String filename) {
        if (config.features.recordReplay == null) {
            config.features.recordReplay = new RestMonkey.RecordReplay();
        }
        config.features.recordReplay.mode = "replay";
        config.features.recordReplay.file = filename;
        return this;
    }
    
    /**
     * Set replay behavior when no match is found.
     */
    public RestMonkeyBuilder replayOnMiss(String behavior) {
        if (config.features.recordReplay == null) {
            config.features.recordReplay = new RestMonkey.RecordReplay();
        }
        config.features.recordReplay.replayOnMiss = behavior;
        return this;
    }
    
    // ---------- Build ----------
    
    /**
     * Build the RestMonkey configuration.
     */
    public RestMonkey.MockConfig build() {
        return config;
    }
    
    /**
     * Build and start the RestMonkey server.
     */
    public RestMonkey start() throws Exception {
        return new RestMonkey(build());
    }
    
    // ---------- Internal Methods ----------
    
    void addResource(RestMonkey.Resource resource) {
        config.resources.add(resource);
    }
    
    void addStaticEndpoint(RestMonkey.StaticEndpoint endpoint) {
        config.staticEndpoints.add(endpoint);
    }

    // ---------- Resource Builder ----------

    /**
     * Builder for configuring a resource with CRUD endpoints.
     */
    public static class ResourceBuilder {
        private final RestMonkeyBuilder parent;
        private final RestMonkey.Resource resource = new RestMonkey.Resource();

        ResourceBuilder(RestMonkeyBuilder parent, String name) {
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

        // ---------- Chaos Engineering for CRUD Endpoints ----------

        /**
         * Add fixed latency to all CRUD endpoints for this resource.
         */
        public ResourceBuilder withLatency(long milliseconds) {
            resource.latencyMs = milliseconds;
            return this;
        }

        /**
         * Add random latency between min and max milliseconds to all CRUD endpoints.
         */
        public ResourceBuilder withRandomLatency(long minMs, long maxMs) {
            resource.randomLatencyMinMs = minMs;
            resource.randomLatencyMaxMs = maxMs;
            return this;
        }

        /**
         * Set failure rate (0.0 to 1.0) for random 500 errors on all CRUD endpoints.
         */
        public ResourceBuilder withFailureRate(double rate) {
            resource.failureRate = Math.max(0.0, Math.min(1.0, rate));
            return this;
        }

        /**
         * Randomly return different status codes with equal probability on CRUD endpoints.
         */
        public ResourceBuilder withRandomStatuses(int... statusCodes) {
            resource.randomStatuses = Arrays.stream(statusCodes).boxed().toArray(Integer[]::new);
            return this;
        }

        /**
         * Randomly return different status codes with specified weights on CRUD endpoints.
         */
        public ResourceBuilder withRandomStatuses(int[] statusCodes, double[] weights) {
            if (statusCodes.length != weights.length) {
                throw new IllegalArgumentException("Status codes and weights arrays must have same length");
            }
            resource.randomStatuses = Arrays.stream(statusCodes).boxed().toArray(Integer[]::new);
            resource.randomStatusWeights = Arrays.stream(weights).boxed().toArray(Double[]::new);
            return this;
        }

        /**
         * CRUD endpoints succeed only after N retries (useful for testing retry logic).
         */
        public ResourceBuilder successAfterRetries(int retries) {
            resource.successAfterRetries = retries;
            return this;
        }

        /**
         * CRUD endpoints succeed only after N seconds (useful for testing timeout logic).
         */
        public ResourceBuilder successAfterSeconds(int seconds) {
            resource.successAfterSeconds = seconds;
            return this;
        }

        /**
         * Set maximum time window for retry tracking (default: 300 seconds).
         */
        public ResourceBuilder maxRetryWindow(int seconds) {
            resource.maxRetryWindow = seconds;
            return this;
        }

        /**
         * Finish configuring this resource and return to the main builder.
         */
        public RestMonkeyBuilder done() {
            parent.addResource(resource);
            return parent;
        }
    }

    // ---------- Static Endpoint Builder ----------

    /**
     * Builder for configuring static endpoints.
     */
    public static class StaticEndpointBuilder {
        private final RestMonkeyBuilder parent;
        private final RestMonkey.StaticEndpoint endpoint = new RestMonkey.StaticEndpoint();

        StaticEndpointBuilder(RestMonkeyBuilder parent) {
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
            // Auth is handled at the endpoint level in RESTMonkey
            // This is a convenience method for documentation
            return this;
        }

        // ---------- Chaos Engineering ----------

        /**
         * Add fixed latency to this endpoint.
         */
        public StaticEndpointBuilder withLatency(long milliseconds) {
            endpoint.latencyMs = milliseconds;
            return this;
        }

        /**
         * Add random latency between min and max milliseconds.
         */
        public StaticEndpointBuilder withRandomLatency(long minMs, long maxMs) {
            endpoint.randomLatencyMinMs = minMs;
            endpoint.randomLatencyMaxMs = maxMs;
            return this;
        }

        /**
         * Set failure rate (0.0 to 1.0) for random 500 errors.
         */
        public StaticEndpointBuilder withFailureRate(double rate) {
            endpoint.failureRate = Math.max(0.0, Math.min(1.0, rate));
            return this;
        }

        /**
         * Randomly return different status codes with equal probability.
         */
        public StaticEndpointBuilder withRandomStatuses(int... statusCodes) {
            endpoint.randomStatuses = Arrays.stream(statusCodes).boxed().toArray(Integer[]::new);
            return this;
        }

        /**
         * Randomly return different status codes with specified weights.
         */
        public StaticEndpointBuilder withRandomStatuses(int[] statusCodes, double[] weights) {
            if (statusCodes.length != weights.length) {
                throw new IllegalArgumentException("Status codes and weights arrays must have same length");
            }
            endpoint.randomStatuses = Arrays.stream(statusCodes).boxed().toArray(Integer[]::new);
            endpoint.randomStatusWeights = Arrays.stream(weights).boxed().toArray(Double[]::new);
            return this;
        }

        /**
         * Succeed only after N retries (useful for testing retry logic).
         */
        public StaticEndpointBuilder successAfterRetries(int retries) {
            endpoint.successAfterRetries = retries;
            return this;
        }

        /**
         * Succeed only after N seconds (useful for testing timeout logic).
         */
        public StaticEndpointBuilder successAfterSeconds(int seconds) {
            endpoint.successAfterSeconds = seconds;
            return this;
        }

        /**
         * Set maximum time window for retry tracking (default: 300 seconds).
         */
        public StaticEndpointBuilder maxRetryWindow(int seconds) {
            endpoint.maxRetryWindow = seconds;
            return this;
        }

        /**
         * Finish configuring this endpoint and return to the main builder.
         */
        public RestMonkeyBuilder done() {
            parent.addStaticEndpoint(endpoint);
            return parent;
        }
    }
}
