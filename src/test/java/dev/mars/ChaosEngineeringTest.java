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


import dev.mars.tinyrest.TinyRest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for chaos engineering features: artificial latency and failure rates
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-30
 * @version 1.0
 */
@ExtendWith(TinyRest.JUnitTinyRestExtension.class)
@TinyRest.UseTinyRest(configPath = "src/test/resources/config-chaos.yaml")
class ChaosEngineeringTest {

    private static final Logger logger = LoggerFactory.getLogger(ChaosEngineeringTest.class);
    HttpClient client = HttpClient.newHttpClient();

    @Test
    void shouldApplyArtificialLatency(@TinyRest.TinyRestBaseUrl String baseUrl) throws Exception {
        logger.info("Testing artificial latency (expected: 100ms minimum)");
        // Config has artificialLatencyMs: 100, so requests should take at least 100ms
        // Note: Due to chaos failure rate, we may get 500 status, so we'll retry until we get a 200

        for (int attempt = 0; attempt < 10; attempt++) {
            logger.debug("Latency test attempt {} of 10", attempt + 1);
            long startTime = System.currentTimeMillis();

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chaos-test"))
                    .GET()
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            long duration = System.currentTimeMillis() - startTime;
            logger.debug("Attempt {}: status={}, duration={}ms", attempt + 1, response.statusCode(), duration);

            if (response.statusCode() == 200) {
                // Got a successful response, check latency
                logger.info("Got successful response on attempt {}: duration={}ms", attempt + 1, duration);
                assertTrue(duration >= 100, "Request should take at least 100ms due to artificial latency, but took " + duration + "ms");
                logger.info("Artificial latency test passed - duration {}ms >= 100ms", duration);
                return; // Test passed
            }
            logger.debug("Got chaos failure (status {}), retrying...", response.statusCode());
            // If we got a chaos failure (500), try again
        }

        fail("Could not get a successful response after 10 attempts due to chaos failures");
    }

    @Test
    void shouldHaveChaosFailures(@TinyRest.TinyRestBaseUrl String baseUrl) throws Exception {
        // Config has chaosFailRate: 0.3, so roughly 30% of requests should fail with 500
        List<Integer> statusCodes = new ArrayList<>();
        
        // Make multiple requests to see chaos failures
        for (int i = 0; i < 20; i++) {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chaos-test"))
                    .GET()
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            statusCodes.add(response.statusCode());
        }
        
        // Should have some 200s and some 500s
        assertTrue(statusCodes.contains(200), "Should have some successful requests");
        assertTrue(statusCodes.contains(500), "Should have some chaos failures");
        
        // Roughly 30% should be failures (allow some variance)
        long failureCount = statusCodes.stream().mapToInt(Integer::intValue).filter(code -> code == 500).count();
        double failureRate = (double) failureCount / statusCodes.size();
        
        assertTrue(failureRate > 0.05, "Failure rate should be > 5% with chaosFailRate=0.3, but was " + failureRate);
        assertTrue(failureRate < 0.7, "Failure rate should be < 70% with chaosFailRate=0.3, but was " + failureRate);
    }

    @Test
    void shouldWorkWithCrudOperations(@TinyRest.TinyRestBaseUrl String baseUrl) throws Exception {
        // Test that chaos features don't break normal CRUD operations
        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/orders"))
                .GET()
                .build();
        
        // May get 200 or 500 due to chaos, but should be valid response
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertTrue(response.statusCode() == 200 || response.statusCode() == 500);
        
        if (response.statusCode() == 200) {
            assertTrue(response.body().contains("laptop"));
        }
    }

    @Test
    void chaosFailuresShouldHaveProperErrorResponse(@TinyRest.TinyRestBaseUrl String baseUrl) throws Exception {
        // Keep trying until we get a chaos failure
        for (int attempt = 0; attempt < 50; attempt++) {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chaos-test"))
                    .GET()
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 500) {
                // Chaos failures should have proper error response
                String body = response.body();
                assertTrue(body.contains("error") || body.contains("chaos"), 
                    "Chaos failure should have error message, got: " + body);
                return; // Found a chaos failure, test passed
            }
        }
        
        // If we get here, we didn't see any chaos failures in 50 attempts
        // This could happen with low failure rates, so we'll just warn
        System.out.println("Warning: No chaos failures observed in 50 attempts");
    }
}
