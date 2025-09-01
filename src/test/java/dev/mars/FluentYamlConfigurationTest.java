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
 * Configuration validation tests using Fluent Builder API only (no YAML configuration)
 * This demonstrates that the fluent API can replicate all YAML functionality
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-31
 * @version 1.0
 */
class FluentYamlConfigurationTest {

    private static final Logger logger = LoggerFactory.getLogger(FluentYamlConfigurationTest.class);
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void shouldReplicateBasicYamlConfiguration() throws Exception {
        logger.info("Testing that fluent API can replicate basic YAML configuration");
        
        // This replicates a typical YAML config using fluent API
        var server = RestMonkey.builder()
            .port(0)
            .authToken("yaml-equivalent-token")
            .enableTemplating()
            // Hot reload disabled for fluent API (no config file)
            
            // Replicate resources section
            .resource("users")
                .idField("id")
                .enableCrud()
                .seed("id", "u1", "name", "Alice", "email", "alice@example.com")
                .seed("id", "u2", "name", "Bob", "email", "bob@example.com")
                .done()
            
            // Replicate staticEndpoints section
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
            
            // Test resource endpoints
            var usersRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/users"))
                .GET()
                .build();
            var usersResponse = client.send(usersRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, usersResponse.statusCode());
            assertTrue(usersResponse.body().contains("Alice"));
            assertTrue(usersResponse.body().contains("Bob"));
            
            // Test static endpoints
            var healthRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/health"))
                .GET()
                .build();
            var healthResponse = client.send(healthRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, healthResponse.statusCode());
            assertTrue(healthResponse.body().contains("healthy"));
            assertFalse(healthResponse.body().contains("{{now}}")); // Template should be rendered
            
            // Test auth requirement
            var webhookRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/webhook"))
                .POST(HttpRequest.BodyPublishers.ofString("{\"test\":\"data\"}"))
                .build();
            var webhookResponse = client.send(webhookRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(401, webhookResponse.statusCode()); // Should require auth
            
            logger.info("Fluent API successfully replicated YAML configuration functionality");
            
        } finally {
            server.stop();
        }
    }

    @Test
    void shouldSupportComplexNestedConfiguration() throws Exception {
        logger.info("Testing complex nested configuration using fluent API");
        
        var server = RestMonkey.builder()
            .port(0)
            .authToken("complex-token")
            .enableTemplating()
            
            // Complex resource with multiple fields
            .resource("products")
                .idField("productId")
                .enableCrud()
                .seed("productId", "p1", 
                      "name", "Laptop", 
                      "price", 999.99, 
                      "category", "electronics",
                      "inStock", true,
                      "tags", new String[]{"computer", "portable"},
                      "specs", java.util.Map.of(
                          "cpu", "Intel i7",
                          "ram", "16GB",
                          "storage", "512GB SSD"
                      ))
                .done()
            
            // Multiple static endpoints with different methods
            .staticEndpoint()
                .get("/api/status")
                .status(200)
                .response(
                    "service", "RestMonkey",
                    "version", "1.0.0",
                    "uptime", "{{now}}"
                )
                .done()
            
            .staticEndpoint()
                .post("/api/events")
                .status(201)
                .response(
                    "eventId", "{{uuid}}",
                    "timestamp", "{{now}}",
                    "status", "created"
                )
                .requireAuth()
                .done()
            
            .staticEndpoint()
                .put("/api/config")
                .status(200)
                .echoRequest()
                .requireAuth()
                .done()
            
            .start();
        
        try {
            String baseUrl = server.getBaseUrl();
            
            // Test complex resource
            var productRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/products/p1"))
                .GET()
                .build();
            var productResponse = client.send(productRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, productResponse.statusCode());
            
            String productBody = productResponse.body();
            assertTrue(productBody.contains("\"name\":\"Laptop\""));
            assertTrue(productBody.contains("\"price\":999.99"));
            assertTrue(productBody.contains("\"inStock\":true"));
            assertTrue(productBody.contains("\"tags\":[\"computer\",\"portable\"]"));
            assertTrue(productBody.contains("\"cpu\":\"Intel i7\""));
            
            // Test status endpoint with templating
            var statusRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/status"))
                .GET()
                .build();
            var statusResponse = client.send(statusRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, statusResponse.statusCode());
            assertTrue(statusResponse.body().contains("\"service\":\"RestMonkey\""));
            assertFalse(statusResponse.body().contains("{{now}}")); // Should be rendered
            
            // Test authenticated endpoint
            var eventRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/events"))
                .header("Authorization", "Bearer complex-token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"type\":\"test\"}"))
                .build();
            var eventResponse = client.send(eventRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(201, eventResponse.statusCode());
            assertTrue(eventResponse.body().contains("\"status\":\"created\""));
            assertFalse(eventResponse.body().contains("{{uuid}}")); // Should be rendered
            
            logger.info("Complex nested configuration test passed");
            
        } finally {
            server.stop();
        }
    }

    @Test
    void shouldValidateFluentApiEquivalence() throws Exception {
        logger.info("Validating that fluent API provides equivalent functionality to YAML");
        
        var server = RestMonkey.builder()
            .port(0)
            .authToken("validation-token")
            .enableTemplating()
            // Hot reload disabled for fluent API (no config file)
            
            // All major features in one configuration
            .resource("items")
                .idField("itemId")
                .enableCrud()
                .withLatency(5) // Small latency for testing
                // No failure rate - this test validates basic functionality
                .seed("itemId", "i1", "name", "Item 1", "active", true)
                .done()
            
            .staticEndpoint()
                .get("/metrics")
                .status(200)
                .response("requests", 0)
                .response("uptime", "{{uptime}}")
                .response("memory", Runtime.getRuntime().totalMemory())
                .done()
            
            .staticEndpoint()
                .post("/echo")
                .status(200)
                .echoRequest()
                .withLatency(5)
                .done()
            
            .start();
        
        try {
            String baseUrl = server.getBaseUrl();
            
            // Validate all features work
            logger.info("✓ Server started successfully");
            
            // Test CRUD resource
            var itemsResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/items")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            assertEquals(200, itemsResponse.statusCode());
            logger.info("✓ CRUD resources working");
            
            // Test templating
            var metricsResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/metrics")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            assertEquals(200, metricsResponse.statusCode());
            assertFalse(metricsResponse.body().contains("{{uptime}}"));
            logger.info("✓ Templating working");
            
            // Test echo (requires auth)
            var echoResponse = client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/echo"))
                    .header("Authorization", "Bearer validation-token")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"test\":\"data\"}"))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
            assertEquals(200, echoResponse.statusCode());
            assertTrue(echoResponse.body().contains("\"method\":\"POST\""));
            logger.info("✓ Echo functionality working");
            
            logger.info("✓ All YAML equivalent features validated in fluent API");
            
        } finally {
            server.stop();
        }
    }
}
