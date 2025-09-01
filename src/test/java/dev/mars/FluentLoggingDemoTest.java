package dev.mars;

/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import dev.mars.restmonkey.RestMonkey;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Logging demonstration tests using Fluent Builder API only (no YAML configuration)
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-31
 * @version 1.0
 */
class FluentLoggingDemoTest {

    private static final Logger logger = LoggerFactory.getLogger(FluentLoggingDemoTest.class);
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void shouldDemonstrateComprehensiveLogging() throws Exception {
        logger.info("=== Demonstrating RestMonkey's comprehensive logging using fluent API ===");
        
        var server = RestMonkey.builder()
            .port(0)
            .authToken("demo-token")
            .enableTemplating()
            .enableHotReload()
            .resource("products")
                .idField("productId")
                .enableCrud()
                .withLatency(50) // Add some latency for timing logs
                .seed("productId", "p1", "name", "Widget", "price", 19.99, "category", "gadgets")
                .seed("productId", "p2", "name", "Gadget", "price", 29.99, "category", "tools")
                .done()
            .staticEndpoint()
                .get("/health")
                .status(200)
                .response("status", "healthy", "timestamp", "{{now}}")
                .done()
            .staticEndpoint()
                .post("/webhook")
                .status(200)
                .echoRequest()
                .requireAuth()
                .done()
            .start();
        
        try {
            String baseUrl = server.getBaseUrl();
            logger.info("RestMonkey server started at: {}", baseUrl);
            
            // 1. Test GET request (no auth required)
            logger.info("--- Testing GET request (should show HTTP request/response logs) ---");
            var getRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/products"))
                .header("User-Agent", "FluentLoggingDemo/1.0")
                .GET()
                .build();
            var getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, getResponse.statusCode());
            
            // 2. Test POST without auth (should show auth failure logs)
            logger.info("--- Testing POST without auth (should show authentication failure logs) ---");
            var postRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/products"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"New Product\",\"price\":39.99}"))
                .build();
            var postResponse = client.send(postRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(401, postResponse.statusCode());
            
            // 3. Test POST with auth (should show successful creation logs)
            logger.info("--- Testing POST with auth (should show successful creation logs) ---");
            var authPostRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/products"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer demo-token")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Authenticated Product\",\"price\":49.99}"))
                .build();
            var authPostResponse = client.send(authPostRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(201, authPostResponse.statusCode());
            
            // 4. Test templating endpoint (should show template rendering logs)
            logger.info("--- Testing templating endpoint (should show template rendering logs) ---");
            var templateRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/health"))
                .GET()
                .build();
            var templateResponse = client.send(templateRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, templateResponse.statusCode());
            assertFalse(templateResponse.body().contains("{{now}}"));
            
            // 5. Test echo endpoint (should show request echo logs)
            logger.info("--- Testing echo endpoint (should show request echo logs) ---");
            var echoRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/webhook?test=param"))
                .header("Authorization", "Bearer demo-token")
                .header("X-Custom-Header", "demo-value")
                .POST(HttpRequest.BodyPublishers.ofString("{\"webhook\":\"data\"}"))
                .build();
            var echoResponse = client.send(echoRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, echoResponse.statusCode());
            assertTrue(echoResponse.body().contains("\"method\":\"POST\""));
            
            // 6. Test 404 (should show not found logs)
            logger.info("--- Testing 404 endpoint (should show not found logs) ---");
            var notFoundRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/nonexistent"))
                .GET()
                .build();
            var notFoundResponse = client.send(notFoundRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(404, notFoundResponse.statusCode());
            
            logger.info("=== Logging demonstration completed ===");
            logger.info("Check the console output above to see RestMonkey's comprehensive logging:");
            logger.info("- Server startup and configuration logs");
            logger.info("- HTTP request/response logs with timing");
            logger.info("- Authentication success/failure logs");
            logger.info("- Template rendering logs");
            logger.info("- Request echo logs");
            logger.info("- Error handling logs");
            
        } finally {
            server.stop();
        }
    }

    @Test
    void shouldLogPerformanceMetrics() throws Exception {
        logger.info("Testing performance logging using fluent API");
        
        var server = RestMonkey.builder()
            .port(0)
            .resource("performance-test")
                .idField("id")
                .enableCrud()
                .withLatency(100) // Add latency to see timing logs
                .seed("id", "item1", "name", "Test Item")
                .done()
            .start();
        
        try {
            String baseUrl = server.getBaseUrl();
            
            // Make multiple requests to generate performance logs
            for (int i = 0; i < 3; i++) {
                logger.info("--- Performance test request {} ---", i + 1);
                
                long start = System.currentTimeMillis();
                var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/performance-test"))
                    .GET()
                    .build();
                
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                long elapsed = System.currentTimeMillis() - start;
                
                assertEquals(200, response.statusCode());
                logger.info("Request {} completed in {}ms", i + 1, elapsed);
                
                // Should see latency in action
                assertTrue(elapsed >= 90, "Expected latency of ~100ms, got " + elapsed + "ms");
            }
            
        } finally {
            server.stop();
        }
    }

    @Test
    void shouldLogChaosEngineeringEvents() throws Exception {
        logger.info("Testing chaos engineering event logging using fluent API");
        
        var server = RestMonkey.builder()
            .port(0)
            .resource("chaos-test")
                .idField("id")
                .enableCrud()
                .withFailureRate(0.5) // 50% failure rate
                .withLatency(25) // Small latency
                .seed("id", "item1", "name", "Chaos Item")
                .done()
            .start();
        
        try {
            String baseUrl = server.getBaseUrl();
            
            // Make several requests to trigger chaos events
            for (int i = 0; i < 5; i++) {
                logger.info("--- Chaos test request {} ---", i + 1);
                
                var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/chaos-test"))
                    .GET()
                    .build();
                
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                logger.info("Request {} result: status={}", i + 1, response.statusCode());
                
                // Should get mix of 200 and 5xx responses
                assertTrue(response.statusCode() == 200 || response.statusCode() >= 500);
            }
            
            logger.info("=== Chaos engineering logging demonstration completed ===");
            logger.info("Check the logs above for chaos engineering events:");
            logger.info("- Latency injection logs");
            logger.info("- Failure simulation logs");
            logger.info("- Performance timing with chaos effects");
            
        } finally {
            server.stop();
        }
    }
}
