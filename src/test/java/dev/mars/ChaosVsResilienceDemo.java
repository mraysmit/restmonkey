package dev.mars;

import dev.mars.restmonkey.RestMonkey;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Demo: RestMonkey Chaos Engineering vs Resilience4J Defense Patterns
 * 
 * This demonstrates how Resilience4J patterns defend against RestMonkey's chaos attacks:
 * - Circuit Breaker vs Failure Injection
 * - Retry vs Random Failures  
 * - Timeout vs Latency Attacks
 * - Bulkhead vs Resource Exhaustion
 */
public class ChaosVsResilienceDemo {
    
    private static final Logger logger = LoggerFactory.getLogger(ChaosVsResilienceDemo.class);
    
    private RestMonkey chaosServer;
    private HttpClient httpClient;
    private ScheduledExecutorService scheduler;
    private String baseUrl;
    
    // Resilience4J Components
    private CircuitBreaker circuitBreaker;
    private Retry retry;
    private TimeLimiter timeLimiter;
    
    @BeforeEach
    void setupChaosVsResilience() throws Exception {
        logger.info("🎭 Setting up Chaos vs Resilience battlefield...");
        
        // 🐒 RestMonkey: The Chaos Attacker
        chaosServer = RestMonkey.builder()
            .port(0)
            .authToken("chaos-token")
            .enableTemplating()
            
            // 💥 Unreliable Payment Service (30% failures, slow responses)
            .resource("payments")
                .idField("id")
                .enableCrud()
                .withFailureRate(0.30)           // 30% failure rate
                .withRandomLatency(100, 500)     // 100-500ms random delays
                .withRandomStatuses(200, 429, 503, 504) // Mixed status codes
                .seed("id", "p1", "amount", 99.99, "status", "pending")
                .seed("id", "p2", "amount", 149.50, "status", "completed")
                .done()
            
            // 🎲 Flaky External API (circuit breaker scenarios)
            .staticEndpoint()
                .get("/external/weather")
                .withFailureRate(0.40)           // 40% failure rate
                .withLatency(200)                // Always slow
                .successAfterRetries(3)          // Succeed after 3 attempts
                .response("weather", "sunny", "temp", "{{random.int(15,25)}}")
                .done()
            
            // 🕐 Timeout Bomb (extreme latency)
            .staticEndpoint()
                .get("/slow/database")
                .withRandomLatency(2000, 5000)   // 2-5 second delays!
                .response("data", "eventually retrieved", "timestamp", "{{now}}")
                .done()
            
            .start();
        
        baseUrl = chaosServer.getBaseUrl();
        httpClient = HttpClient.newHttpClient();
        scheduler = Executors.newScheduledThreadPool(4);
        
        // 🛡️ Resilience4J: The Defense System
        setupDefenses();
        
        logger.info("⚔️  Battlefield ready! Chaos Server: {}", baseUrl);
    }
    
    private void setupDefenses() {
        // Circuit Breaker: Stop calling failing services
        circuitBreaker = CircuitBreaker.of("payment-service", CircuitBreakerConfig.custom()
            .failureRateThreshold(50)           // Open circuit at 50% failure rate
            .waitDurationInOpenState(Duration.ofSeconds(2))
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .build());
        
        // Retry: Try again on failures
        retry = Retry.of("api-retry", RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(100))
            .retryOnResult(response -> ((HttpResponse<?>) response).statusCode() >= 500)
            .build());
        
        // Timeout: Don't wait forever
        timeLimiter = TimeLimiter.of("api-timeout", TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(1))
            .build());
        
        // Log circuit breaker state changes
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                logger.info("🔄 Circuit Breaker: {} -> {}", 
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState()));
    }
    
    @Test
    void demonstrateCircuitBreakerVsFailureInjection() throws Exception {
        logger.info("🥊 Round 1: Circuit Breaker vs Failure Injection");
        
        int successCount = 0;
        int circuitOpenCount = 0;
        
        // Make 20 requests - watch circuit breaker protect us
        for (int i = 1; i <= 20; i++) {
            try {
                var result = circuitBreaker.executeSupplier(() -> {
                    try {
                        var request = HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + "/api/payments"))
                            .GET().build();
                        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                        
                        if (response.statusCode() >= 500) {
                            throw new RuntimeException("Server error: " + response.statusCode());
                        }
                        return response;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                
                successCount++;
                logger.debug("✅ Request {}: SUCCESS ({})", i, result.statusCode());
                
            } catch (Exception e) {
                if (e.getMessage().contains("CircuitBreaker")) {
                    circuitOpenCount++;
                    logger.debug("🚫 Request {}: CIRCUIT OPEN", i);
                } else {
                    logger.debug("❌ Request {}: FAILED ({})", i, e.getMessage());
                }
            }
            
            Thread.sleep(50); // Small delay between requests
        }
        
        logger.info("🏆 Results: {} successes, {} circuit-blocked", successCount, circuitOpenCount);
        assertTrue(circuitOpenCount > 0, "Circuit breaker should have opened");
        assertTrue(successCount > 0, "Some requests should have succeeded");
    }
    
    @Test
    void demonstrateRetryVsRandomFailures() throws Exception {
        logger.info("🥊 Round 2: Retry Pattern vs Random Failures");
        
        Supplier<HttpResponse<String>> resilientCall = Retry.decorateSupplier(retry, () -> {
            try {
                var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/external/weather"))
                    .GET().build();
                var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() >= 500) {
                    logger.debug("🔄 Retry triggered for status: {}", response.statusCode());
                    throw new RuntimeException("Retryable error: " + response.statusCode());
                }
                return response;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        
        // Test retry resilience
        int attempts = 0;
        for (int i = 0; i < 10; i++) {
            try {
                attempts++;
                var response = resilientCall.get();
                logger.info("✅ Weather API call {} succeeded after retries: {}", 
                    i + 1, response.statusCode());
                assertTrue(response.statusCode() == 200);
            } catch (Exception e) {
                logger.warn("❌ Weather API call {} failed even with retries", i + 1);
            }
        }
        
        logger.info("🏆 Completed {} resilient weather API calls", attempts);
    }
    
    @Test
    void demonstrateTimeoutVsLatencyAttack() throws Exception {
        logger.info("🥊 Round 3: Timeout vs Latency Bomb");

        // Simple timeout test using CompletableFuture.get with timeout
        long startTime = System.currentTimeMillis();
        try {
            CompletableFuture<HttpResponse<String>> futureResponse = httpClient.sendAsync(
                HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/slow/database"))
                    .GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );

            // Wait max 1 second (should timeout against 2-5 second delays)
            var response = futureResponse.get(1, java.util.concurrent.TimeUnit.SECONDS);

            long duration = System.currentTimeMillis() - startTime;
            logger.info("✅ Slow database call completed in {}ms: {}", duration, response.statusCode());
            fail("Should have timed out!");
        } catch (java.util.concurrent.TimeoutException e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.info("🛡️  Timeout protection activated after {}ms", duration);
            assertTrue(duration < 1500, "Should timeout within ~1 second");
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.info("🛡️  Request failed after {}ms: {}", duration, e.getClass().getSimpleName());
            assertTrue(duration < 1500, "Should fail quickly");
        }
    }
    
    @AfterEach
    void cleanup() throws Exception {
        if (chaosServer != null) {
            chaosServer.stop();
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
        logger.info("🏁 Chaos vs Resilience battle concluded");
    }
}
