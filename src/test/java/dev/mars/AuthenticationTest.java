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
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for authentication and authorization
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-30
 * @version 1.0
 */
class AuthenticationTest {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationTest.class);
    HttpClient client = HttpClient.newHttpClient();

    @Nested
    @ExtendWith(RestMonkey.JUnitRestMonkeyExtension.class)
    @RestMonkey.UseRestMonkey(configPath = "src/test/resources/RestMonkey.yaml") // Has authToken: test-token
    class WithAuthConfiguredTest {
        
        @Test
        void getMutationsShouldNotRequireAuth(@RestMonkey.RestMonkeyBaseUrl String baseUrl) throws Exception {
            // GET operations should work without auth
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/users"))
                    .GET()
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
        }
        
        @Test
        void postWithoutAuthShouldFail(@RestMonkey.RestMonkeyBaseUrl String baseUrl) throws Exception {
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
        void postWithValidAuthShouldSucceed(@RestMonkey.RestMonkeyBaseUrl String baseUrl) throws Exception {
            logger.info("Testing POST with valid Bearer token authentication");

            var requestBody = "{\"name\":\"Test User\"}";
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/users"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer test-token")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            logger.debug("Sending authenticated POST: {} with body: {}", request.uri(), requestBody);
            logger.debug("Auth header: {}", request.headers().firstValue("Authorization").orElse("NONE"));

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            logger.info("Auth test response: status={}, body: {}", response.statusCode(), response.body());

            assertEquals(201, response.statusCode());
            logger.info("Valid auth test passed - got expected 201");
        }
        
        @Test
        void postWithInvalidAuthShouldFail(@RestMonkey.RestMonkeyBaseUrl String baseUrl) throws Exception {
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
        void putWithoutAuthShouldFail(@RestMonkey.RestMonkeyBaseUrl String baseUrl) throws Exception {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/users/u1"))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString("{\"name\":\"Updated User\"}"))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(401, response.statusCode());
        }
        
        @Test
        void deleteWithoutAuthShouldFail(@RestMonkey.RestMonkeyBaseUrl String baseUrl) throws Exception {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/users/u1"))
                    .DELETE()
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(401, response.statusCode());
        }
        
        @Test
        void authHeaderVariationsShouldWork(@RestMonkey.RestMonkeyBaseUrl String baseUrl) throws Exception {
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
        void invalidAuthFormatsShouldFail(@RestMonkey.RestMonkeyBaseUrl String baseUrl) throws Exception {
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
    @ExtendWith(RestMonkey.JUnitRestMonkeyExtension.class)
    @RestMonkey.UseRestMonkey(configPath = "src/test/resources/config-no-auth.yaml") // No authToken configured
    class NoAuthConfiguredTest {
        
        @Test
        void mutationsShouldWorkWithoutAuth(@RestMonkey.RestMonkeyBaseUrl String baseUrl) throws Exception {
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
        void authHeaderShouldBeIgnored(@RestMonkey.RestMonkeyBaseUrl String baseUrl) throws Exception {
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
