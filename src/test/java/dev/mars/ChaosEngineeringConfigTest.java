package dev.mars;

import dev.mars.restmonkey.RestMonkey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite demonstrating focused chaos engineering patterns using YAML configuration.
 * Each test demonstrates one specific chaos engineering concept clearly.
 */
public class ChaosEngineeringConfigTest {
    
    private RestMonkey server;
    private HttpClient client;
    
    @BeforeEach
    void setUp() {
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.stop();
        }
    }
    
    @Test
    void testLatencyPatterns() throws Exception {
        // Demo 1: Different latency patterns
        var config = RestMonkey.loadConfig("src/test/resources/chaos-1-latency.yaml");
        server = new RestMonkey(config);
        String baseUrl = server.getBaseUrl();

        System.out.println("⏱️ Demo 1: Latency Patterns");

        // Fast service (no latency)
        long start = System.currentTimeMillis();
        var fastResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/users")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        long fastTime = System.currentTimeMillis() - start;

        // Slow service (500ms fixed)
        start = System.currentTimeMillis();
        var slowResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/reports")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        long slowTime = System.currentTimeMillis() - start;

        // Variable service (100-800ms random)
        start = System.currentTimeMillis();
        var variableResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/analytics")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        long variableTime = System.currentTimeMillis() - start;

        System.out.println("  Fast service:     " + fastTime + "ms (expected: <50ms)");
        System.out.println("  Slow service:     " + slowTime + "ms (expected: ~500ms)");
        System.out.println("  Variable service: " + variableTime + "ms (expected: 100-800ms)");

        assertEquals(200, fastResponse.statusCode());
        assertEquals(200, slowResponse.statusCode());
        assertEquals(200, variableResponse.statusCode());
        assertTrue(fastTime < 200); // Allow for test overhead
        assertTrue(slowTime >= 450); // Should have ~500ms latency
        assertTrue(variableTime >= 90 && variableTime <= 850); // Should be 100-800ms
    }

    @Test
    void testFailurePatterns() throws Exception {
        // Demo 2: Failure simulation
        var config = RestMonkey.loadConfig("src/test/resources/chaos-2-failures.yaml");
        server = new RestMonkey(config);
        String baseUrl = server.getBaseUrl();

        System.out.println("💥 Demo 2: Failure Patterns");

        // Test reliable service (should always work)
        var reliableResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/users")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, reliableResponse.statusCode());
        System.out.println("  Reliable service: " + reliableResponse.statusCode() + " (always works)");

        // Test unreliable service (30% failure rate)
        int successes = 0;
        for (int i = 0; i < 10; i++) {
            try {
                var response = client.send(
                        HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/payments")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) successes++;
            } catch (Exception e) {
                // Expected failures
            }
        }

        System.out.println("  Unreliable service: " + successes + "/10 successes (expected: ~7/10)");
        assertTrue(successes >= 3 && successes <= 9); // Should be around 70% success (with variance)
    }

    @Test
    void testStatusCodePatterns() throws Exception {
        // Demo 3: Random status codes
        var config = RestMonkey.loadConfig("src/test/resources/chaos-3-status-codes.yaml");
        server = new RestMonkey(config);
        String baseUrl = server.getBaseUrl();

        System.out.println("🎲 Demo 3: Random Status Codes");

        // Test load balancer endpoint (75% healthy, 25% down)
        int healthyCount = 0;
        for (int i = 0; i < 8; i++) {
            var response = client.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/load-balancer")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) healthyCount++;
            System.out.println("  Load balancer attempt " + (i+1) + ": " + response.statusCode());
        }

        System.out.println("  Healthy responses: " + healthyCount + "/8 (expected: ~6/8)");
        assertTrue(healthyCount >= 4); // Should be mostly healthy
    }

    @Test
    void testRetryPatterns() throws Exception {
        // Demo 4: Retry patterns
        var config = RestMonkey.loadConfig("src/test/resources/chaos-4-retry-patterns.yaml");
        server = new RestMonkey(config);
        String baseUrl = server.getBaseUrl();

        System.out.println("🔄 Demo 4: Retry Patterns");

        // Test retry-after-attempts endpoint
        var firstAttempt = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/retry-after-attempts")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(503, firstAttempt.statusCode());
        System.out.println("  First attempt: " + firstAttempt.statusCode() + " (expected failure)");

        var secondAttempt = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/retry-after-attempts")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, secondAttempt.statusCode());
        System.out.println("  Second attempt: " + secondAttempt.statusCode() + " (success after retry)");

        // Test time-based retry
        var timeFirst = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/retry-after-time")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(503, timeFirst.statusCode());
        System.out.println("  Time-based first: " + timeFirst.statusCode() + " (expected failure)");

        Thread.sleep(2500); // Wait 2.5 seconds

        var timeSecond = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/retry-after-time")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, timeSecond.statusCode());
        System.out.println("  Time-based second: " + timeSecond.statusCode() + " (success after wait)");
    }

    @Test
    void testRealisticScenarios() throws Exception {
        // Demo 5: Realistic combined scenarios
        var config = RestMonkey.loadConfig("src/test/resources/chaos-5-combined-realistic.yaml");
        server = new RestMonkey(config);
        String baseUrl = server.getBaseUrl();

        System.out.println("🌍 Demo 5: Realistic Production Scenarios");

        // Fast user service
        long start = System.currentTimeMillis();
        var userResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/users")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        long userTime = System.currentTimeMillis() - start;

        // Slow database
        start = System.currentTimeMillis();
        var reportResponse = client.send(
                HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/large-reports")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        long reportTime = System.currentTimeMillis() - start;

        System.out.println("  User service:   " + userTime + "ms (fast)");
        System.out.println("  Report service: " + reportTime + "ms (slow database)");
        System.out.println("  Load balancer:  Testing...");

        // Test load balancer health
        int healthyLB = 0;
        for (int i = 0; i < 5; i++) {
            var lbResponse = client.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/health/lb")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (lbResponse.statusCode() == 200) healthyLB++;
        }

        System.out.println("  Load balancer:  " + healthyLB + "/5 healthy (expected: ~4/5)");

        assertEquals(200, userResponse.statusCode());
        assertTrue(userTime < 100);
        assertTrue(reportTime >= 1150); // Should have ~1200ms latency
        assertTrue(healthyLB >= 3); // Should be mostly healthy
    }
}
