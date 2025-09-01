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
 * Authentication tests using Fluent Builder API only (no YAML configuration)
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-31
 * @version 1.0
 */
class FluentAuthenticationTest {

    private static final Logger logger = LoggerFactory.getLogger(FluentAuthenticationTest.class);
    private final HttpClient client = HttpClient.newHttpClient();

    @Nested
    class WithAuthConfiguredTest {
        
        private RestMonkey server;
        private String baseUrl;
        
        @BeforeEach
        void setUp() throws Exception {
            server = RestMonkey.builder()
                .port(0) // Random port
                .authToken("test-token")
                .enableTemplating()
                .resource("users")
                    .idField("id")
                    .enableCrud()
                    .seed("id", "u1", "name", "Alice", "email", "alice@example.com")
                    .seed("id", "u2", "name", "Bob", "email", "bob@example.com")
                    .done()
                .staticEndpoint()
                    .get("/health")
                    .status(200)
                    .response("status", "ok", "time", "{{now}}")
                    .done()
                .start();
            
            baseUrl = server.getBaseUrl();
        }
        
        @AfterEach
        void tearDown() throws Exception {
            if (server != null) {
                server.stop();
            }
        }
        
        @Test
        void getMutationsShouldNotRequireAuth() throws Exception {
            // GET operations should work without auth
            var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/users"))
                .GET()
                .build();
            
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("Alice"));
        }
        
        @Test
        void postMutationsShouldRequireAuth() throws Exception {
            // POST without auth should fail
            var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/users"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Charlie\",\"email\":\"charlie@example.com\"}"))
                .build();
            
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(401, response.statusCode());
            assertTrue(response.body().contains("Missing/invalid bearer token"));
        }
        
        @Test
        void postWithValidAuthShouldSucceed() throws Exception {
            // POST with valid auth should work
            var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/users"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer test-token")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Charlie\",\"email\":\"charlie@example.com\"}"))
                .build();
            
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(201, response.statusCode());
            assertTrue(response.body().contains("Charlie"));
        }
        
        @Test
        void putMutationsShouldRequireAuth() throws Exception {
            // PUT without auth should fail
            var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/users/u1"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"name\":\"Updated Alice\",\"email\":\"alice.updated@example.com\"}"))
                .build();
            
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(401, response.statusCode());
        }
        
        @Test
        void deleteMutationsShouldRequireAuth() throws Exception {
            // DELETE without auth should fail
            var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/users/u1"))
                .DELETE()
                .build();
            
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(401, response.statusCode());
        }
    }

    @Nested
    class WithoutAuthConfiguredTest {
        
        private RestMonkey server;
        private String baseUrl;
        
        @BeforeEach
        void setUp() throws Exception {
            server = RestMonkey.builder()
                .port(0) // Random port
                // No auth token configured
                .resource("products")
                    .idField("productId")
                    .enableCrud()
                    .seed("productId", "p1", "name", "Widget", "price", 19.99)
                    .done()
                .start();
            
            baseUrl = server.getBaseUrl();
        }
        
        @AfterEach
        void tearDown() throws Exception {
            if (server != null) {
                server.stop();
            }
        }
        
        @Test
        void allOperationsShouldWorkWithoutAuth() throws Exception {
            // When no auth is configured, all operations should work
            
            // GET should work
            var getRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/products"))
                .GET()
                .build();
            var getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, getResponse.statusCode());
            
            // POST should work
            var postRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/products"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Gadget\",\"price\":29.99}"))
                .build();
            var postResponse = client.send(postRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(201, postResponse.statusCode());
        }
    }
}
