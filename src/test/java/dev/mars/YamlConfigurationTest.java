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
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for YAML configuration options
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-30
 * @version 1.0
 */
class YamlConfigurationTest {

    private static final Logger logger = LoggerFactory.getLogger(YamlConfigurationTest.class);
    HttpClient client = HttpClient.newHttpClient();

    @Nested
    @ExtendWith(TinyRest.JUnitTinyRestExtension.class)
    @TinyRest.UseTinyRest(configPath = "src/test/resources/config-minimal.yaml")
    class MinimalConfigurationTest {
        
        @Test
        void minimalConfigShouldWork(@TinyRest.TinyRestBaseUrl String baseUrl) throws Exception {
            // Test that minimal config creates basic CRUD endpoints
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/items"))
                    .GET()
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals("[]", response.body()); // Empty array since no seed data
        }
        
        @Test
        void minimalConfigShouldAllowMutationsWithoutAuth(@TinyRest.TinyRestBaseUrl String baseUrl) throws Exception {
            // Since no authToken is specified, mutations should work without auth
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/items"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"test item\"}"))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(201, response.statusCode());
        }
    }

    @Nested
    @ExtendWith(TinyRest.JUnitTinyRestExtension.class)
    @TinyRest.UseTinyRest(configPath = "src/test/resources/config-no-auth.yaml")
    class NoAuthConfigurationTest {
        
        @Test
        void shouldUseCustomIdField(@TinyRest.TinyRestBaseUrl String baseUrl) throws Exception {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/products"))
                    .GET()
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("productId"));
            assertTrue(response.body().contains("Widget"));
        }
        
        @Test
        void shouldAllowMutationsWithoutAuth(@TinyRest.TinyRestBaseUrl String baseUrl) throws Exception {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/products"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"New Product\",\"price\":15.99}"))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(201, response.statusCode());
        }
        
        @Test
        void shouldHaveTemplatingDisabled(@TinyRest.TinyRestBaseUrl String baseUrl) throws Exception {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/status"))
                    .GET()
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            // Should not contain templated values since templating is disabled
            assertFalse(response.body().contains("{{"));
        }
    }

    @Nested
    @ExtendWith(TinyRest.JUnitTinyRestExtension.class)
    @TinyRest.UseTinyRest(configPath = "src/test/resources/config-multiple-resources.yaml")
    class MultipleResourcesTest {
        
        @Test
        void shouldCreateCrudForEnabledResources(@TinyRest.TinyRestBaseUrl String baseUrl) throws Exception {
            // Test users resource (CRUD enabled)
            var usersRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/users"))
                    .GET()
                    .build();
            var usersResponse = client.send(usersRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, usersResponse.statusCode());
            assertTrue(usersResponse.body().contains("John Doe"));
            
            // Test posts resource (CRUD enabled)
            var postsRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/posts"))
                    .GET()
                    .build();
            var postsResponse = client.send(postsRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, postsResponse.statusCode());
            assertTrue(postsResponse.body().contains("First Post"));
        }
        
        @Test
        void shouldNotCreateCrudForDisabledResources(@TinyRest.TinyRestBaseUrl String baseUrl) throws Exception {
            // Categories has enableCrud: false, so should return 404
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/categories"))
                    .GET()
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(404, response.statusCode());
        }
        
        @Test
        void shouldHandleCustomStatusCodes(@TinyRest.TinyRestBaseUrl String baseUrl) throws Exception {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/custom-status"))
                    .GET()
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(418, response.statusCode()); // I'm a teapot
            assertTrue(response.body().contains("teapot"));
        }
        
        @Test
        void shouldHandleEchoEndpoint(@TinyRest.TinyRestBaseUrl String baseUrl) throws Exception {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/echo-post"))
                    .header("Authorization", "Bearer multi-token")
                    .header("Content-Type", "application/json")
                    .header("X-Test-Header", "test-value")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"test\":\"data\"}"))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            
            // Echo should include request details
            String body = response.body();
            assertTrue(body.contains("POST"));
            assertTrue(body.contains("/echo-post"));
            assertTrue(body.contains("test-value"));
            assertTrue(body.contains("test"));
        }
    }
}
