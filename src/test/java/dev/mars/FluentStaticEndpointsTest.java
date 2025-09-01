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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Static endpoints tests using Fluent Builder API only (no YAML configuration)
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-31
 * @version 1.0
 */
class FluentStaticEndpointsTest {

    private static final Logger logger = LoggerFactory.getLogger(FluentStaticEndpointsTest.class);
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void shouldReturnTestDataWithVariousTypes() throws Exception {
        logger.info("Testing static endpoint with various data types using fluent API");
        
        var server = RestMonkey.builder()
            .port(0)
            .enableTemplating()
            .resource("items")
                .idField("id")
                .enableCrud()
                .done()
            .staticEndpoint()
                .get("/test-data")
                .status(200)
                .response(
                    "string_field", "Hello World",
                    "number_field", 42,
                    "boolean_field", true,
                    "null_field", null,
                    "array_field", new String[]{"item1", "item2", "item3"},
                    "nested_object", Map.of(
                        "inner_string", "nested value",
                        "inner_number", 123,
                        "inner_array", new int[]{1, 2, 3}
                    ),
                    "timestamp", "{{now}}",
                    "random_id", "{{uuid}}"
                )
                .done()
            .start();
        
        try {
            String baseUrl = server.getBaseUrl();
            
            var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/test-data"))
                .GET()
                .build();
            
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            
            String body = response.body();
            logger.info("Test data response: {}", body);
            
            // Verify various data types are present
            assertTrue(body.contains("\"string_field\":\"Hello World\""));
            assertTrue(body.contains("\"number_field\":42"));
            assertTrue(body.contains("\"boolean_field\":true"));
            assertTrue(body.contains("\"null_field\":null"));
            assertTrue(body.contains("\"array_field\":[\"item1\",\"item2\",\"item3\"]"));
            assertTrue(body.contains("\"nested_object\""));
            assertTrue(body.contains("\"inner_string\":\"nested value\""));
            
            // Verify templating worked
            assertFalse(body.contains("{{now}}"));
            assertFalse(body.contains("{{uuid}}"));
            assertTrue(body.matches(".*\"timestamp\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*"));
            assertTrue(body.matches(".*\"random_id\":\"[0-9a-f-]{36}\".*"));
            
        } finally {
            server.stop();
        }
    }

    @Test
    void shouldReturnCustomStatusCodes() throws Exception {
        logger.info("Testing custom status codes using fluent API");
        
        var server = RestMonkey.builder()
            .port(0)
            .staticEndpoint()
                .get("/created")
                .status(201)
                .response("message", "Resource created")
                .done()
            .staticEndpoint()
                .get("/accepted")
                .status(202)
                .response("message", "Request accepted")
                .done()
            .staticEndpoint()
                .get("/teapot")
                .status(418)
                .response("message", "I'm a teapot")
                .done()
            .start();
        
        try {
            String baseUrl = server.getBaseUrl();
            
            // Test 201 Created
            var createdRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/created"))
                .GET()
                .build();
            var createdResponse = client.send(createdRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(201, createdResponse.statusCode());
            assertTrue(createdResponse.body().contains("Resource created"));
            
            // Test 202 Accepted
            var acceptedRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/accepted"))
                .GET()
                .build();
            var acceptedResponse = client.send(acceptedRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(202, acceptedResponse.statusCode());
            assertTrue(acceptedResponse.body().contains("Request accepted"));
            
            // Test 418 I'm a teapot
            var teapotRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/teapot"))
                .GET()
                .build();
            var teapotResponse = client.send(teapotRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(418, teapotResponse.statusCode());
            assertTrue(teapotResponse.body().contains("I'm a teapot"));
            
        } finally {
            server.stop();
        }
    }

    @Test
    void shouldHandleEchoRequests() throws Exception {
        logger.info("Testing echo request functionality using fluent API");
        
        var server = RestMonkey.builder()
            .port(0)
            .authToken("static-token")
            .staticEndpoint()
                .post("/test-echo")
                .status(200)
                .echoRequest()
                .requireAuth()
                .done()
            .start();
        
        try {
            String baseUrl = server.getBaseUrl();
            
            var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/test-echo?param1=value1&param2=value2"))
                .header("Authorization", "Bearer static-token")
                .header("X-test-header", "test-value")
                .POST(HttpRequest.BodyPublishers.ofString("{\"test\":\"data\"}"))
                .build();
            
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            
            String body = response.body();
            logger.info("Echo response: {}", body);
            
            // Verify echo functionality
            assertTrue(body.contains("\"method\":\"POST\""));
            assertTrue(body.contains("\"path\":\"/test-echo\""));
            assertTrue(body.contains("\"body\":\"{\\\"test\\\":\\\"data\\\"}\""));
            assertTrue(body.contains("\"Authorization\":[\"Bearer static-token\"]"));
            assertTrue(body.contains("\"X-test-header\":[\"test-value\"]"));
            assertTrue(body.contains("\"param1\":[\"value1\"]"));
            assertTrue(body.contains("\"param2\":[\"value2\"]"));
            
        } finally {
            server.stop();
        }
    }
}
