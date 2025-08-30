package dev.mars;

import dev.mars.tinyrest.TinyRest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test the fluent builder interface for TinyRest.
 * Demonstrates programmatic configuration without YAML files.
 */
public class TinyRestBuilderTest {
    
    private TinyRest server;
    private final HttpClient client = HttpClient.newBuilder()
            .build();
    
    @AfterEach
    void cleanup() {
        if (server != null) {
            server.stop();
        }
    }
    
    @Test
    void testBasicBuilderUsage() throws Exception {
        // Create server using fluent builder
        server = TinyRest.builder()
                .port(0) // Auto-assign port
                .authToken("test-token")
                .enableTemplating()
                .strictValidation()
                .resource("users")
                    .idField("id")
                    .enableCrud()
                    .seed("id", "u1", "name", "Alice", "email", "alice@example.com")
                    .seed("id", "u2", "name", "Bob", "email", "bob@example.com")
                    .done()
                .staticEndpoint()
                    .get("/health")
                    .status(200)
                    .response("status", "healthy", "time", "{{now}}")
                    .done()
                .staticEndpoint()
                    .post("/echo")
                    .echoRequest()
                    .done()
                .start();
        
        String baseUrl = server.getBaseUrl();
        assertNotNull(baseUrl);
        assertTrue(server.getPort() > 0);
        
        // Test health endpoint
        var healthRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/health"))
                .GET()
                .build();
        
        var healthResponse = client.send(healthRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, healthResponse.statusCode());
        String healthBody = healthResponse.body();
        System.out.println("Health response: " + healthBody); // Debug output
        assertTrue(healthBody.contains("\"status\":\"healthy\""));
        assertTrue(healthBody.contains("\"time\":"));
        
        // Test users resource
        var usersRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/users"))
                .GET()
                .build();
        
        var usersResponse = client.send(usersRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, usersResponse.statusCode());
        assertTrue(usersResponse.body().contains("Alice"));
        assertTrue(usersResponse.body().contains("Bob"));
        
        // Test echo endpoint with auth
        var echoRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/echo"))
                .header("Authorization", "Bearer test-token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"test\":\"data\"}"))
                .build();
        
        var echoResponse = client.send(echoRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, echoResponse.statusCode());
        String echoBody = echoResponse.body();
        assertTrue(echoBody.contains("\"method\":\"POST\""));
        assertTrue(echoBody.contains("{\\\"test\\\":\\\"data\\\"}"));
    }
    
    @Test
    void testMinimalConfiguration() throws Exception {
        // Minimal configuration - just a health endpoint
        server = TinyRest.builder()
                .port(0)
                .noAuth()
                .staticEndpoint()
                    .get("/ping")
                    .response("pong")
                    .done()
                .start();
        
        String baseUrl = server.getBaseUrl();
        assertNotNull(baseUrl);
        
        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/ping"))
                .GET()
                .build();
        
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("\"pong\"", response.body());
    }
    
    @Test
    void testComplexConfiguration() throws Exception {
        // Complex configuration with multiple resources and endpoints
        server = TinyRest.builder()
                .port(0)
                .authToken("complex-token")
                .enableTemplating()
                .enableFileLogging()
                .logLevel("DEBUG")
                .artificialLatency(10)
                .chaosFailRate(0.0) // Disable chaos for predictable tests
                .resource("products")
                    .idField("productId")
                    .enableCrud()
                    .seed("productId", "p1", "name", "Widget", "price", 10.99)
                    .seed("productId", "p2", "name", "Gadget", "price", 25.50)
                    .done()
                .resource("categories")
                    .idField("categoryId")
                    .disableCrud() // Data-only resource
                    .seed("categoryId", "c1", "name", "Electronics")
                    .seed("categoryId", "c2", "name", "Books")
                    .done()
                .staticEndpoint()
                    .get("/status")
                    .status(200)
                    .response("service", "tinyrest", "version", "test", "uptime", "{{now}}")
                    .done()
                .staticEndpoint()
                    .post("/submit")
                    .status(201)
                    .response("message", "Created", "id", "{{uuid}}")
                    .done()
                .staticEndpoint()
                    .get("/teapot")
                    .status(418)
                    .response("I'm a teapot")
                    .done()
                .start();
        
        String baseUrl = server.getBaseUrl();
        assertNotNull(baseUrl);
        
        // Test products resource
        var productsRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/products"))
                .GET()
                .build();
        
        var productsResponse = client.send(productsRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, productsResponse.statusCode());
        assertTrue(productsResponse.body().contains("Widget"));
        assertTrue(productsResponse.body().contains("Gadget"));
        
        // Test categories should not have CRUD endpoints (disabled)
        var categoriesRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/categories"))
                .GET()
                .build();
        
        var categoriesResponse = client.send(categoriesRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, categoriesResponse.statusCode()); // No CRUD endpoints
        
        // Test custom status codes
        var teapotRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/teapot"))
                .GET()
                .build();
        
        var teapotResponse = client.send(teapotRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(418, teapotResponse.statusCode());
        assertEquals("\"I'm a teapot\"", teapotResponse.body());
        
        // Test templating
        var statusRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/status"))
                .GET()
                .build();
        
        var statusResponse = client.send(statusRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, statusResponse.statusCode());
        assertTrue(statusResponse.body().contains("\"service\":\"tinyrest\""));
        assertTrue(statusResponse.body().contains("\"uptime\":")); // Should have timestamp
    }
    
    @Test
    void testBuilderChaining() {
        // Test that builder methods can be chained fluently
        var config = TinyRest.builder()
                .port(9999)
                .authToken("chain-test")
                .enableTemplating()
                .enableFileLogging()
                .logLevel("INFO")
                .resource("items")
                    .idField("itemId")
                    .enableCrud()
                    .seed("itemId", "i1", "name", "Test Item")
                    .done()
                .staticEndpoint()
                    .get("/test")
                    .status(200)
                    .response("message", "test successful")
                    .done()
                .build();

        // Verify configuration was built correctly
        assertEquals(9999, config.port.intValue());
        assertEquals("chain-test", config.authToken);
        assertTrue(config.features.templating);
        assertEquals("INFO", config.logging.level);
        assertEquals(1, config.resources.size());
        assertEquals("items", config.resources.get(0).name);
        assertEquals(1, config.staticEndpoints.size());
        assertEquals("/test", config.staticEndpoints.get(0).path);
    }

    @Test
    void testMinimalConfigurationLikeYaml() throws Exception {
        // Equivalent to config-minimal.yaml
        server = TinyRest.builder()
                .port(0)
                .noAuth()
                .resource("items")
                    .idField("id")
                    .enableCrud()
                    .done()
                .start();

        String baseUrl = server.getBaseUrl();

        // Test empty resource returns empty array
        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/items"))
                .GET()
                .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("[]", response.body());
    }

    @Test
    void testTextResponsesLikeYaml() throws Exception {
        // Equivalent to config-text-responses.yaml
        server = TinyRest.builder()
                .port(0)
                .noAuth()
                .disableTemplating()
                .strictValidation()
                .resource("items")
                    .idField("id")
                    .enableCrud()
                    .done()
                .staticEndpoint()
                    .get("/health")
                    .status(200)
                    .response("healthy")
                    .done()
                .staticEndpoint()
                    .get("/status")
                    .status(200)
                    .response("OK")
                    .done()
                .staticEndpoint()
                    .get("/ping")
                    .status(200)
                    .response("pong")
                    .done()
                .staticEndpoint()
                    .get("/ready")
                    .status(200)
                    .response("READY")
                    .done()
                .staticEndpoint()
                    .get("/created")
                    .status(201)
                    .response("Resource created")
                    .done()
                .staticEndpoint()
                    .get("/accepted")
                    .status(202)
                    .response("Request accepted")
                    .done()
                .staticEndpoint()
                    .get("/teapot")
                    .status(418)
                    .response("I'm a teapot")
                    .done()
                .staticEndpoint()
                    .get("/error")
                    .status(500)
                    .response("Internal server error")
                    .done()
                .staticEndpoint()
                    .get("/forbidden")
                    .status(403)
                    .response("Access denied")
                    .done()
                .start();

        String baseUrl = server.getBaseUrl();

        // Test various status codes and text responses
        var tests = new Object[][]{
            {"/health", 200, "\"healthy\""},
            {"/status", 200, "\"OK\""},
            {"/ping", 200, "\"pong\""},
            {"/ready", 200, "\"READY\""},
            {"/created", 201, "\"Resource created\""},
            {"/accepted", 202, "\"Request accepted\""},
            {"/teapot", 418, "\"I'm a teapot\""},
            {"/error", 500, "\"Internal server error\""},
            {"/forbidden", 403, "\"Access denied\""}
        };

        for (var test : tests) {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + test[0]))
                    .GET()
                    .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals((Integer) test[1], response.statusCode());
            assertEquals(test[2], response.body());
        }
    }

    @Test
    void testServerInfoLikeYaml() throws Exception {
        // Equivalent to config-server-info.yaml
        server = TinyRest.builder()
                .port(0)
                .authToken("info-token")
                .enableTemplating()
                .strictValidation()
                .resource("users")
                    .idField("id")
                    .enableCrud()
                    .seed("id", "u1", "name", "John Doe", "email", "john@example.com")
                    .seed("id", "u2", "name", "Jane Smith", "email", "jane@example.com")
                    .done()
                .staticEndpoint()
                    .get("/health")
                    .status(200)
                    .response("healthy")
                    .done()
                .staticEndpoint()
                    .get("/server/info")
                    .status(200)
                    .response(
                        "server", "TinyRest",
                        "version", "1.0.0-SNAPSHOT",
                        "java_version", "{{java.version}}",
                        "port", "{{server.port}}",
                        "started_at", "{{server.started}}"
                    )
                    .done()
                .staticEndpoint()
                    .get("/server/routes")
                    .status(200)
                    .response(
                        "total_routes", "{{server.routes.count}}",
                        "crud_routes", "{{server.routes.crud}}",
                        "static_routes", "{{server.routes.static}}",
                        "routes", "{{server.routes.list}}"
                    )
                    .done()
                .start();

        String baseUrl = server.getBaseUrl();

        // Test server info endpoint
        var infoRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/server/info"))
                .GET()
                .build();

        var infoResponse = client.send(infoRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, infoResponse.statusCode());
        assertTrue(infoResponse.body().contains("\"server\":\"TinyRest\""));
        assertTrue(infoResponse.body().contains("\"port\":"));
        assertTrue(infoResponse.body().contains("\"started_at\":"));

        // Test routes info endpoint
        var routesRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/server/routes"))
                .GET()
                .build();

        var routesResponse = client.send(routesRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, routesResponse.statusCode());
        assertTrue(routesResponse.body().contains("\"total_routes\":"));
        assertTrue(routesResponse.body().contains("\"crud_routes\":"));
        assertTrue(routesResponse.body().contains("\"routes\":"));
    }

    @Test
    void testMultipleResourcesLikeYaml() throws Exception {
        // Equivalent to config-multiple-resources.yaml
        server = TinyRest.builder()
                .port(0)
                .authToken("multi-token")
                .enableTemplating()
                .strictValidation()
                .resource("users")
                    .idField("userId")
                    .enableCrud()
                    .seed("userId", "u1", "name", "Alice", "role", "admin")
                    .seed("userId", "u2", "name", "Bob", "role", "user")
                    .done()
                .resource("posts")
                    .idField("postId")
                    .enableCrud()
                    .seed("postId", "p1", "title", "First Post", "author", "Alice")
                    .seed("postId", "p2", "title", "Second Post", "author", "Bob")
                    .done()
                .resource("categories")
                    .idField("id")
                    .disableCrud() // Data-only resource
                    .seed("id", "c1", "name", "Tech")
                    .seed("id", "c2", "name", "News")
                    .done()
                .staticEndpoint()
                    .get("/info")
                    .status(200)
                    .response("service", "tinyrest", "timestamp", "{{now}}")
                    .done()
                .staticEndpoint()
                    .post("/echo-post")
                    .echoRequest()
                    .requireAuth()
                    .done()
                .staticEndpoint()
                    .get("/custom-status")
                    .status(418)
                    .response("message", "I'm a teapot")
                    .done()
                .start();

        String baseUrl = server.getBaseUrl();

        // Test multiple resources
        var usersResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/users")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, usersResponse.statusCode());
        assertTrue(usersResponse.body().contains("Alice"));

        var postsResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/posts")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, postsResponse.statusCode());
        assertTrue(postsResponse.body().contains("First Post"));

        // Categories should not have CRUD endpoints (disabled)
        var categoriesResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/categories")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(404, categoriesResponse.statusCode());

        // Test custom status
        var statusResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/custom-status")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(418, statusResponse.statusCode());
    }

    @Test
    void testTemplatingLikeYaml() throws Exception {
        // Equivalent to config-templating.yaml
        server = TinyRest.builder()
                .port(0)
                .authToken("template-token")
                .enableTemplating()
                .strictValidation()
                .resource("events")
                    .idField("eventId")
                    .enableCrud()
                    .seed("eventId", "e1", "name", "Conference", "date", "2024-01-15", "location", "San Francisco")
                    .done()
                .staticEndpoint()
                    .get("/time")
                    .status(200)
                    .response("current_time", "{{now}}")
                    .done()
                .staticEndpoint()
                    .get("/random")
                    .status(200)
                    .response("random_uuid", "{{uuid}}")
                    .done()
                .start();

        String baseUrl = server.getBaseUrl();

        // Test templating
        var timeResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/time")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, timeResponse.statusCode());
        assertTrue(timeResponse.body().contains("\"current_time\":\"2025-"));

        var randomResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/random")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, randomResponse.statusCode());
        assertTrue(randomResponse.body().contains("\"random_uuid\":"));
    }

    @Test
    void testRecordReplayConfiguration() {
        // Test record/replay builder methods (config-only test)
        var recordConfig = TinyRest.builder()
                .port(0)
                .noAuth()
                .recordRequests("test-recording.json")
                .replayOnMiss("passthrough")
                .resource("items")
                    .idField("id")
                    .enableCrud()
                    .done()
                .build();

        // Verify record/replay configuration
        assertNotNull(recordConfig.features.recordReplay);
        assertEquals("record", recordConfig.features.recordReplay.mode);
        assertEquals("test-recording.json", recordConfig.features.recordReplay.file);
        assertEquals("passthrough", recordConfig.features.recordReplay.replayOnMiss);

        var replayConfig = TinyRest.builder()
                .port(0)
                .replayRequests("test-recording.json")
                .build();

        assertEquals("replay", replayConfig.features.recordReplay.mode);
        assertEquals("test-recording.json", replayConfig.features.recordReplay.file);
    }

    @Test
    void testHttpMethodsAndResponseTypes() throws Exception {
        // Test all HTTP methods and response types
        server = TinyRest.builder()
                .port(0)
                .authToken("methods-token")
                .enableTemplating()
                .staticEndpoint()
                    .get("/get-endpoint")
                    .response("method", "GET")
                    .done()
                .staticEndpoint()
                    .post("/post-endpoint")
                    .response("method", "POST")
                    .done()
                .staticEndpoint()
                    .put("/put-endpoint")
                    .response("method", "PUT")
                    .done()
                .staticEndpoint()
                    .delete("/delete-endpoint")
                    .response("method", "DELETE")
                    .done()
                .staticEndpoint()
                    .method("PATCH")
                    .path("/patch-endpoint")
                    .response("method", "PATCH")
                    .done()
                .start();

        String baseUrl = server.getBaseUrl();

        // Test all HTTP methods
        var methods = new String[]{"GET", "POST", "PUT", "DELETE", "PATCH"};
        for (String method : methods) {
            var requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/" + method.toLowerCase() + "-endpoint"));

            // Add auth header for methods that require it
            if (!method.equals("GET")) {
                requestBuilder.header("Authorization", "Bearer methods-token");
            }

            var request = requestBuilder
                    .method(method, method.equals("GET") || method.equals("DELETE") ?
                            HttpRequest.BodyPublishers.noBody() :
                            HttpRequest.BodyPublishers.ofString("{}"))
                    .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"method\":\"" + method + "\""));
        }
    }
}
