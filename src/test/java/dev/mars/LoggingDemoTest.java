package dev.mars;

import dev.mars.tinyrest.TinyRest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.net.URI;
import java.net.http.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(TinyRest.JUnitTinyRestExtension.class)
@TinyRest.UseTinyRest(
        configPath = "src/test/resources/tinyrest-trace.yaml",
        recordReplayFile = "target/demo-recordings.jsonl"
)
class LoggingDemoTest {

    @Test
    void demonstrateDetailedLogging(@TinyRest.TinyRestBaseUrl String baseUrl) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        
        // Test 1: Health check (static endpoint with templating)
        var healthRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/health"))
                .GET()
                .build();
        var healthResponse = client.send(healthRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, healthResponse.statusCode());
        
        // Test 2: List users (CRUD endpoint)
        var listRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/users?limit=10&offset=0"))
                .GET()
                .build();
        var listResponse = client.send(listRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, listResponse.statusCode());
        
        // Test 3: Create user with auth (successful)
        var createRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/users"))
                .header("Authorization", "Bearer test-token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Charlie\",\"email\":\"charlie@example.com\"}"))
                .build();
        var createResponse = client.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createResponse.statusCode());
        
        // Test 4: Create user without auth (should fail)
        var unauthorizedRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/users"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Dave\",\"email\":\"dave@example.com\"}"))
                .build();
        var unauthorizedResponse = client.send(unauthorizedRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, unauthorizedResponse.statusCode());
        
        // Test 5: Echo endpoint (requires auth since it's POST)
        var echoRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/echo?test=value"))
                .header("Authorization", "Bearer test-token")
                .header("X-Custom-Header", "test-value")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"test\":\"data\"}"))
                .build();
        var echoResponse = client.send(echoRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, echoResponse.statusCode());
        
        // Test 6: Non-existent endpoint (should 404)
        var notFoundRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/nonexistent"))
                .GET()
                .build();
        var notFoundResponse = client.send(notFoundRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, notFoundResponse.statusCode());
    }
}
