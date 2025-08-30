package dev.mars;

import dev.mars.tinyrest.TinyRest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.net.URI;
import java.net.http.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for authentication and authorization
 */
class AuthenticationTest {

    HttpClient client = HttpClient.newHttpClient();

    @Nested
    @ExtendWith(TinyRest.JUnitTinyRestExtension.class)
    @TinyRest.UseTinyRest(configPath = "src/test/resources/tinyrest.yaml") // Has authToken: test-token
    class WithAuthConfiguredTest {
        
        @Test
        void getMutationsShouldNotRequireAuth(@TinyRest.TinyRestBaseUrl String baseUrl) throws Exception {
            // GET operations should work without auth
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/users"))
                    .GET()
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
        }
        
        @Test
        void postWithoutAuthShouldFail(@TinyRest.TinyRestBaseUrl String baseUrl) throws Exception {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/users"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Test User\"}"))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(401, response.statusCode());
            assertTrue(response.body().contains("bearer token") || response.body().contains("unauthorized"));
        }
        
        @Test
        void postWithValidAuthShouldSucceed(@TinyRest.TinyRestBaseUrl String baseUrl) throws Exception {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/users"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer test-token")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Test User\"}"))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(201, response.statusCode());
        }
        
        @Test
        void postWithInvalidAuthShouldFail(@TinyRest.TinyRestBaseUrl String baseUrl) throws Exception {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/users"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer wrong-token")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Test User\"}"))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(401, response.statusCode());
        }
        
        @Test
        void putWithoutAuthShouldFail(@TinyRest.TinyRestBaseUrl String baseUrl) throws Exception {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/users/u1"))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString("{\"name\":\"Updated User\"}"))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(401, response.statusCode());
        }
        
        @Test
        void deleteWithoutAuthShouldFail(@TinyRest.TinyRestBaseUrl String baseUrl) throws Exception {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/users/u1"))
                    .DELETE()
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(401, response.statusCode());
        }
        
        @Test
        void authHeaderVariationsShouldWork(@TinyRest.TinyRestBaseUrl String baseUrl) throws Exception {
            // Test different Authorization header formats
            // Note: Currently only "Bearer" (capital B) is supported
            String[] validHeaders = {
                "Bearer test-token"
            };
            
            for (String authHeader : validHeaders) {
                var request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/users"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", authHeader)
                        .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Test User\"}"))
                        .build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertEquals(201, response.statusCode(), "Auth header '" + authHeader + "' should work");
            }
        }
        
        @Test
        void invalidAuthFormatsShouldFail(@TinyRest.TinyRestBaseUrl String baseUrl) throws Exception {
            String[] invalidHeaders = {
                "test-token",           // Missing Bearer
                "bearer test-token",    // Wrong case (lowercase)
                "BEARER test-token",    // Wrong case (uppercase)
                "Basic test-token",     // Wrong auth type
                "Bearer",               // Missing token
                "Bearer ",              // Empty token
                "Bearer  test-token"    // Extra spaces
            };
            
            for (String authHeader : invalidHeaders) {
                var request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/users"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", authHeader)
                        .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Test User\"}"))
                        .build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertEquals(401, response.statusCode(), "Invalid auth header '" + authHeader + "' should fail");
            }
        }
    }

    @Nested
    @ExtendWith(TinyRest.JUnitTinyRestExtension.class)
    @TinyRest.UseTinyRest(configPath = "src/test/resources/config-no-auth.yaml") // No authToken configured
    class NoAuthConfiguredTest {
        
        @Test
        void mutationsShouldWorkWithoutAuth(@TinyRest.TinyRestBaseUrl String baseUrl) throws Exception {
            // When no authToken is configured, mutations should work without auth
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/products"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Test Product\",\"price\":9.99}"))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(201, response.statusCode());
        }
        
        @Test
        void authHeaderShouldBeIgnored(@TinyRest.TinyRestBaseUrl String baseUrl) throws Exception {
            // Even with auth header, should work since no auth is configured
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/products"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer any-token")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Test Product\",\"price\":9.99}"))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(201, response.statusCode());
        }
    }
}
