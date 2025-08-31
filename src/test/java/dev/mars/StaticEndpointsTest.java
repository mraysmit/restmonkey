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

package dev.mars;

import dev.mars.restmonkey.RestMonkey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for static endpoints with various response types and status codes.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-30
 * @version 1.0
 */
@ExtendWith(RestMonkey.JUnitRestMonkeyExtension.class)
@RestMonkey.UseRestMonkey(configPath = "src/test/resources/config-static-endpoints.yaml")
class StaticEndpointsTest {

    private static final Logger logger = LoggerFactory.getLogger(StaticEndpointsTest.class);
    HttpClient client = HttpClient.newHttpClient();

    @Test
    void shouldReturnTestDataWithVariousTypes(@RestMonkey.RestMonkeyBaseUrl String baseUrl) throws Exception {
        logger.info("Testing /test-data endpoint with various data types");

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
    }

    @Test
    void shouldReturnCustomStatusCodes(@RestMonkey.RestMonkeyBaseUrl String baseUrl) throws Exception {
        logger.info("Testing custom status codes");

        // Test 201 Created
        var createdRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/test-created"))
                .GET()
                .build();
        var createdResponse = client.send(createdRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createdResponse.statusCode());
        assertTrue(createdResponse.body().contains("Resource created successfully"));

        // Test 202 Accepted
        var acceptedRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/test-accepted"))
                .GET()
                .build();
        var acceptedResponse = client.send(acceptedRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(202, acceptedResponse.statusCode());
        assertTrue(acceptedResponse.body().contains("Request accepted for processing"));

        // Test 418 I'm a teapot
        var teapotRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/test-teapot"))
                .GET()
                .build();
        var teapotResponse = client.send(teapotRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(418, teapotResponse.statusCode());
        assertTrue(teapotResponse.body().contains("I'm a teapot"));
    }

    @Test
    void shouldEchoRequestDetails(@RestMonkey.RestMonkeyBaseUrl String baseUrl) throws Exception {
        logger.info("Testing echo request functionality");

        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/test-echo?param1=value1&param2=value2"))
                .header("Authorization", "Bearer static-token")
                .header("X-Test-Header", "test-value")
                .POST(HttpRequest.BodyPublishers.ofString("{\"test\":\"data\"}"))
                .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        
        String body = response.body();
        logger.info("Echo response: {}", body);

        // Verify request details are echoed
        assertTrue(body.contains("\"method\":\"POST\""));
        assertTrue(body.contains("\"path\":\"/test-echo\""));
        assertTrue(body.contains("\"param1\":[\"value1\"]"));
        assertTrue(body.contains("\"param2\":[\"value2\"]"));
        assertTrue(body.contains("\"X-test-header\":[\"test-value\"]")); // Headers are lowercase
        assertTrue(body.contains("\"body\":\"{\\\"test\\\":\\\"data\\\"}\""));
    }

    @Test
    void shouldReturnComplexNestedStructure(@RestMonkey.RestMonkeyBaseUrl String baseUrl) throws Exception {
        logger.info("Testing complex nested response structure");

        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/test-complex"))
                .GET()
                .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        
        String body = response.body();
        logger.info("Complex response structure received");

        // Verify nested structure
        assertTrue(body.contains("\"metadata\""));
        assertTrue(body.contains("\"version\":\"1.0.0\""));
        assertTrue(body.contains("\"data\""));
        assertTrue(body.contains("\"users\""));
        assertTrue(body.contains("\"statistics\""));
        assertTrue(body.contains("\"links\""));
        
        // Verify templating in nested structure
        assertFalse(body.contains("{{"));
        assertTrue(body.matches(".*\"generated_at\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*"));
        assertTrue(body.matches(".*\"server_id\":\"[0-9a-f-]{36}\".*"));
    }

    @Test
    void shouldRequireAuthForSubmitEndpoint(@RestMonkey.RestMonkeyBaseUrl String baseUrl) throws Exception {
        logger.info("Testing authenticated POST endpoint");

        // Test without auth - should fail
        var unauthRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/test-submit"))
                .POST(HttpRequest.BodyPublishers.ofString("{\"data\":\"test\"}"))
                .build();
        var unauthResponse = client.send(unauthRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, unauthResponse.statusCode());

        // Test with auth - should succeed
        var authRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/test-submit"))
                .header("Authorization", "Bearer static-token")
                .POST(HttpRequest.BodyPublishers.ofString("{\"data\":\"test\"}"))
                .build();
        var authResponse = client.send(authRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, authResponse.statusCode());
        assertTrue(authResponse.body().contains("Data submitted successfully"));
    }
}
