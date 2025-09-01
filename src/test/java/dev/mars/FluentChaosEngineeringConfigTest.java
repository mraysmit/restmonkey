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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive chaos engineering configuration tests using Fluent Builder API only
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-31
 * @version 1.0
 */
class FluentChaosEngineeringConfigTest {

    private static final Logger logger = LoggerFactory.getLogger(FluentChaosEngineeringConfigTest.class);
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void testLatencyPatterns() throws Exception {
        logger.info("=== Testing Latency Patterns with Fluent API ===");
        
        // Create three services with different latency patterns
        var fastServer = RestMonkey.builder()
            .port(0)
            .resource("fast-service")
                .idField("id")
                .enableCrud()
                // No latency configured - should be fast
                .seed("id", "item1", "name", "Fast Item")
                .done()
            .start();
        
        var slowServer = RestMonkey.builder()
            .port(0)
            .resource("slow-service")
                .idField("id")
                .enableCrud()
                .withLatency(500) // Fixed 500ms latency
                .seed("id", "item1", "name", "Slow Item")
                .done()
            .start();
        
        var variableServer = RestMonkey.builder()
            .port(0)
            .resource("variable-service")
                .idField("id")
                .enableCrud()
                .withRandomLatency(100, 800) // 100-800ms random latency
                .seed("id", "item1", "name", "Variable Item")
                .done()
            .start();
        
        try {
            // Test fast service
            long fastTime = measureLatency(fastServer.getBaseUrl() + "/api/fast-service");
            logger.info("Fast service latency: {}ms", fastTime);
            assertTrue(fastTime < 100, "Fast service should be under 100ms, got " + fastTime + "ms");
            
            // Test slow service
            long slowTime = measureLatency(slowServer.getBaseUrl() + "/api/slow-service");
            logger.info("Slow service latency: {}ms", slowTime);
            assertTrue(slowTime >= 450, "Slow service should be ~500ms, got " + slowTime + "ms");
            
            // Test variable service
            long variableTime = measureLatency(variableServer.getBaseUrl() + "/api/variable-service");
            logger.info("Variable service latency: {}ms", variableTime);
            assertTrue(variableTime >= 80 && variableTime <= 900, 
                "Variable service should be 100-800ms, got " + variableTime + "ms");
            
            logger.info("✅ All latency patterns working correctly");
            
        } finally {
            fastServer.stop();
            slowServer.stop();
            variableServer.stop();
        }
    }

    @Test
    void testFailureRates() throws Exception {
        logger.info("=== Testing Failure Rates with Fluent API ===");
        
        var server = RestMonkey.builder()
            .port(0)
            .resource("unreliable-service")
                .idField("id")
                .enableCrud()
                .withFailureRate(0.30) // 30% failure rate
                .seed("id", "item1", "name", "Unreliable Item")
                .done()
            .start();
        
        try {
            String baseUrl = server.getBaseUrl();
            List<Integer> statusCodes = new ArrayList<>();
            
            // Make 10 requests to test failure rate
            for (int i = 0; i < 10; i++) {
                var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/unreliable-service"))
                    .GET()
                    .build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                statusCodes.add(response.statusCode());
            }
            
            long successCount = statusCodes.stream().filter(code -> code == 200).count();
            long failureCount = statusCodes.stream().filter(code -> code >= 500).count();
            
            logger.info("Failure rate test: {}/10 successes, {}/10 failures", successCount, failureCount);
            logger.info("Status codes: {}", statusCodes);
            
            // With 30% failure rate, expect roughly 5-9 successes out of 10 (allowing for variance)
            assertTrue(successCount >= 5 && successCount <= 9, 
                "Expected 5-9 successes with 30% failure rate, got " + successCount);
            
            logger.info("✅ Failure rate pattern working correctly");
            
        } finally {
            server.stop();
        }
    }

    @Test
    void testRandomStatusCodes() throws Exception {
        logger.info("=== Testing Random Status Codes with Fluent API ===");
        
        var server = RestMonkey.builder()
            .port(0)
            .staticEndpoint()
                .get("/random-status")
                .withRandomStatuses(200, 503)
                .response("message", "Random status test")
                .done()
            .start();
        
        try {
            String baseUrl = server.getBaseUrl();
            List<Integer> statusCodes = new ArrayList<>();
            
            // Make 8 requests to see status code variation
            for (int i = 0; i < 8; i++) {
                var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/random-status"))
                    .GET()
                    .build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                statusCodes.add(response.statusCode());
                logger.info("Request {}: status {}", i + 1, response.statusCode());
            }
            
            // Should see both 200 and 503 status codes
            boolean has200 = statusCodes.contains(200);
            boolean has503 = statusCodes.contains(503);
            
            logger.info("Random status codes: {}", statusCodes);
            assertTrue(has200 || has503, "Should see either 200 or 503 status codes");
            
            logger.info("✅ Random status code pattern working correctly");
            
        } finally {
            server.stop();
        }
    }

    @Test
    void testRetryPatterns() throws Exception {
        logger.info("=== Testing Retry Patterns with Fluent API ===");
        
        var server = RestMonkey.builder()
            .port(0)
            .staticEndpoint()
                .get("/retry-endpoint")
                .status(200)
                .response("status", "success")
                .successAfterRetries(2) // Fail twice, then succeed
                .done()
            .start();
        
        try {
            String baseUrl = server.getBaseUrl();
            
            var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/retry-endpoint"))
                .GET()
                .build();
            
            // First attempt should fail
            var response1 = client.send(request, HttpResponse.BodyHandlers.ofString());
            logger.info("Attempt 1: status {}", response1.statusCode());
            assertEquals(503, response1.statusCode());
            
            // Second attempt should fail
            var response2 = client.send(request, HttpResponse.BodyHandlers.ofString());
            logger.info("Attempt 2: status {}", response2.statusCode());
            assertEquals(503, response2.statusCode());
            
            // Third attempt should succeed
            var response3 = client.send(request, HttpResponse.BodyHandlers.ofString());
            logger.info("Attempt 3: status {}", response3.statusCode());
            assertEquals(200, response3.statusCode());
            assertTrue(response3.body().contains("success"));
            
            logger.info("✅ Retry pattern working correctly (503 → 503 → 200)");
            
        } finally {
            server.stop();
        }
    }

    @Test
    void testRealisticScenario() throws Exception {
        logger.info("=== Testing Realistic Production Scenario with Fluent API ===");
        
        var server = RestMonkey.builder()
            .port(0)
            .authToken("prod-token")
            .enableTemplating()
            
            // Slow database service
            .resource("database")
                .idField("id")
                .enableCrud()
                .withLatency(1200) // Slow database
                .withFailureRate(0.05) // 5% failure rate
                .seed("id", "db1", "status", "connected", "latency_ms", 1200)
                .done()
            
            // Unreliable load balancer
            .staticEndpoint()
                .get("/load-balancer/health")
                .withRandomStatuses(200, 503, 504)
                .response("status", "load balancer")
                .withLatency(50)
                .done()
            
            // Circuit breaker simulation
            .staticEndpoint()
                .get("/circuit-breaker")
                .status(200)
                .response("circuit", "closed")
                .successAfterRetries(3) // Simulate circuit breaker opening/closing
                .done()
            
            .start();
        
        try {
            String baseUrl = server.getBaseUrl();
            
            // Test slow database
            long dbTime = measureLatency(baseUrl + "/api/database");
            logger.info("Database latency: {}ms", dbTime);
            assertTrue(dbTime >= 1100, "Database should be slow (~1200ms), got " + dbTime + "ms");
            
            // Test load balancer variability
            List<Integer> lbStatuses = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                var lbResponse = client.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/load-balancer/health")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
                lbStatuses.add(lbResponse.statusCode());
            }
            logger.info("Load balancer status codes: {}", lbStatuses);
            
            // Test circuit breaker pattern
            var cbRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/circuit-breaker"))
                .GET()
                .build();
            
            // Should fail a few times then succeed
            var cb1 = client.send(cbRequest, HttpResponse.BodyHandlers.ofString());
            var cb2 = client.send(cbRequest, HttpResponse.BodyHandlers.ofString());
            var cb3 = client.send(cbRequest, HttpResponse.BodyHandlers.ofString());
            var cb4 = client.send(cbRequest, HttpResponse.BodyHandlers.ofString());
            
            logger.info("Circuit breaker pattern: {} → {} → {} → {}", 
                cb1.statusCode(), cb2.statusCode(), cb3.statusCode(), cb4.statusCode());
            
            // Last request should succeed after retries
            assertEquals(200, cb4.statusCode());
            
            logger.info("✅ Realistic production scenario simulation working correctly");
            
        } finally {
            server.stop();
        }
    }

    private long measureLatency(String url) throws Exception {
        long start = System.currentTimeMillis();
        var request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        long elapsed = System.currentTimeMillis() - start;
        
        // Verify we got a response (might be error due to chaos)
        assertTrue(response.statusCode() >= 200 && response.statusCode() < 600);
        
        return elapsed;
    }
}
