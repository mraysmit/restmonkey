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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chaos engineering tests using Fluent Builder API only (no YAML configuration)
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-31
 * @version 1.0
 */
class FluentChaosEngineeringTest {

    private static final Logger logger = LoggerFactory.getLogger(FluentChaosEngineeringTest.class);
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void shouldApplyArtificialLatency() throws Exception {
        logger.info("Testing artificial latency using fluent API (expected: 100ms minimum)");
        
        var server = RestMonkey.builder()
            .port(0)
            .resource("slow-items")
                .idField("id")
                .enableCrud()
                .withLatency(100) // 100ms artificial latency
                .seed("id", "item1", "name", "Slow Widget")
                .done()
            .start();
        
        try {
            String baseUrl = server.getBaseUrl();
            
            long start = System.currentTimeMillis();
            var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/slow-items"))
                .GET()
                .build();
            
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            long elapsed = System.currentTimeMillis() - start;
            
            assertEquals(200, response.statusCode());
            assertTrue(elapsed >= 90, "Expected at least 90ms latency, got " + elapsed + "ms");
            logger.info("Latency test passed: {}ms (expected: >=100ms)", elapsed);
            
        } finally {
            server.stop();
        }
    }

    @Test
    void shouldSimulateFailureRates() throws Exception {
        logger.info("Testing failure rate simulation using fluent API");
        
        var server = RestMonkey.builder()
            .port(0)
            .resource("unreliable-service")
                .idField("id")
                .enableCrud()
                .withFailureRate(0.5) // 50% failure rate
                .seed("id", "item1", "name", "Flaky Widget")
                .done()
            .start();
        
        try {
            String baseUrl = server.getBaseUrl();
            
            List<Integer> statusCodes = new ArrayList<>();
            
            // Make 20 requests to get a good sample
            for (int i = 0; i < 20; i++) {
                var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/unreliable-service"))
                    .GET()
                    .build();
                
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                statusCodes.add(response.statusCode());
            }
            
            long successCount = statusCodes.stream().mapToInt(Integer::intValue).filter(code -> code == 200).count();
            long failureCount = statusCodes.stream().mapToInt(Integer::intValue).filter(code -> code >= 500).count();
            
            logger.info("Failure rate test: {}/20 successes, {}/20 failures", successCount, failureCount);
            
            // With 50% failure rate, expect roughly 6-14 successes out of 20 (allowing for variance)
            assertTrue(successCount >= 6 && successCount <= 14, 
                "Expected 6-14 successes with 50% failure rate, got " + successCount);
            
        } finally {
            server.stop();
        }
    }

    @Test
    void shouldTestRetryPatterns() throws Exception {
        logger.info("Testing retry patterns using fluent API");
        
        var server = RestMonkey.builder()
            .port(0)
            .staticEndpoint()
                .get("/retry-test")
                .status(200)
                .response("status", "success")
                .successAfterRetries(2) // Fail twice, then succeed
                .done()
            .start();
        
        try {
            String baseUrl = server.getBaseUrl();
            
            // First attempt should fail
            var request1 = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/retry-test"))
                .GET()
                .build();
            var response1 = client.send(request1, HttpResponse.BodyHandlers.ofString());
            assertEquals(503, response1.statusCode());
            logger.info("First attempt: {} (expected failure)", response1.statusCode());
            
            // Second attempt should fail
            var response2 = client.send(request1, HttpResponse.BodyHandlers.ofString());
            assertEquals(503, response2.statusCode());
            logger.info("Second attempt: {} (expected failure)", response2.statusCode());
            
            // Third attempt should succeed
            var response3 = client.send(request1, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response3.statusCode());
            logger.info("Third attempt: {} (expected success)", response3.statusCode());
            
        } finally {
            server.stop();
        }
    }

    @Test
    void shouldTestRandomLatencyRanges() throws Exception {
        logger.info("Testing random latency ranges using fluent API");
        
        var server = RestMonkey.builder()
            .port(0)
            .resource("variable-latency")
                .idField("id")
                .enableCrud()
                .withRandomLatency(50, 200) // 50-200ms random latency
                .seed("id", "item1", "name", "Variable Widget")
                .done()
            .start();
        
        try {
            String baseUrl = server.getBaseUrl();
            
            List<Long> latencies = new ArrayList<>();
            
            // Make 5 requests to test latency variance
            for (int i = 0; i < 5; i++) {
                long start = System.currentTimeMillis();
                var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/variable-latency"))
                    .GET()
                    .build();
                
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                long elapsed = System.currentTimeMillis() - start;
                latencies.add(elapsed);
                
                assertEquals(200, response.statusCode());
                assertTrue(elapsed >= 40, "Expected at least 40ms latency, got " + elapsed + "ms");
                assertTrue(elapsed <= 250, "Expected at most 250ms latency, got " + elapsed + "ms");
            }
            
            logger.info("Random latency test passed: {} ms (range: 50-200ms)", latencies);
            
        } finally {
            server.stop();
        }
    }
}
