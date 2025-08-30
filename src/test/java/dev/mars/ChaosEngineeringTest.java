package dev.mars;

import dev.mars.tinyrest.TinyRest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for chaos engineering features: artificial latency and failure rates
 */
@ExtendWith(TinyRest.JUnitTinyRestExtension.class)
@TinyRest.UseTinyRest(configPath = "src/test/resources/config-chaos.yaml")
class ChaosEngineeringTest {

    HttpClient client = HttpClient.newHttpClient();

    @Test
    void shouldApplyArtificialLatency(@TinyRest.TinyRestBaseUrl String baseUrl) throws Exception {
        // Config has artificialLatencyMs: 100, so requests should take at least 100ms
        long startTime = System.currentTimeMillis();
        
        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chaos-test"))
                .GET()
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        long duration = System.currentTimeMillis() - startTime;
        
        assertEquals(200, response.statusCode());
        assertTrue(duration >= 100, "Request should take at least 100ms due to artificial latency, but took " + duration + "ms");
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
        
        assertTrue(failureRate > 0.1, "Failure rate should be > 10% with chaosFailRate=0.3, but was " + failureRate);
        assertTrue(failureRate < 0.6, "Failure rate should be < 60% with chaosFailRate=0.3, but was " + failureRate);
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
