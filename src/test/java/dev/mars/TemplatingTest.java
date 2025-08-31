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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for templating functionality in static endpoints
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-30
 * @version 1.0
 */
@ExtendWith(RestMonkey.JUnitRestMonkeyExtension.class)
@RestMonkey.UseRestMonkey(configPath = "src/test/resources/config-templating.yaml")
class TemplatingTest {

    private static final Logger logger = LoggerFactory.getLogger(TemplatingTest.class);
    HttpClient client = HttpClient.newHttpClient();

    @Test
    void shouldRenderNowTemplate(@RestMonkey.RestMonkeyBaseUrl String baseUrl) throws Exception {
        logger.info("Testing {{now}} template rendering");

        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/time"))
                .GET()
                .build();

        logger.debug("Sending GET request to templating endpoint: {}", request.uri());
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        String body = response.body();
        logger.info("Template response: status={}, body: {}", response.statusCode(), body);

        // Should contain actual timestamp, not template
        assertFalse(body.contains("{{now}}"));
        assertTrue(body.contains("current_time"));
        assertTrue(body.contains("formatted_time"));

        // Should contain valid ISO timestamp format
        assertTrue(body.matches(".*\"current_time\"\\s*:\\s*\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*"));
        logger.info("Template rendering test passed - {{now}} was properly replaced with timestamp");
    }

    @Test
    void shouldRenderRandomTemplates(@RestMonkey.RestMonkeyBaseUrl String baseUrl) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/random"))
                .GET()
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        assertEquals(200, response.statusCode());
        String body = response.body();
        
        // Should not contain template expressions
        assertFalse(body.contains("{{random"));
        assertFalse(body.contains("{{uuid}}"));
        
        // Should contain actual values
        assertTrue(body.contains("random_number"));
        assertTrue(body.contains("random_uuid"));
        
        // Random number should be between 1-100
        assertTrue(body.matches(".*\"random_number\"\\s*:\\s*\"?\\d{1,3}\"?.*"));
        
        // UUID should be valid format
        assertTrue(body.matches(".*\"random_uuid\"\\s*:\\s*\"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\".*"));
    }

    @Test
    void shouldRenderComplexNestedTemplates(@RestMonkey.RestMonkeyBaseUrl String baseUrl) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/complex-template"))
                .GET()
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        assertEquals(200, response.statusCode());
        String body = response.body();
        
        // Should not contain any template expressions
        assertFalse(body.contains("{{"));
        assertFalse(body.contains("}}"));
        
        // Should have nested structure with templated values
        assertTrue(body.contains("server_info"));
        assertTrue(body.contains("timestamp"));
        assertTrue(body.contains("random_id"));
        assertTrue(body.contains("data"));
        
        // Should contain valid values
        assertTrue(body.matches(".*\"timestamp\"\\s*:\\s*\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*"));
        assertTrue(body.matches(".*\"random_id\"\\s*:\\s*\"[0-9a-f-]{36}\".*"));
    }

    @Test
    void shouldHandleTemplatingInEchoEndpoint(@RestMonkey.RestMonkeyBaseUrl String baseUrl) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/template-echo"))
                .header("Authorization", "Bearer template-token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"hello\"}"))
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        assertEquals(200, response.statusCode());
        String body = response.body();
        
        // Should contain templated values
        assertTrue(body.contains("received_at"));
        assertTrue(body.contains("You sent a POST request to /template-echo"));

        // Should not contain template expressions
        assertFalse(body.contains("{{now}}"));

        // Should have actual timestamp
        assertTrue(body.matches(".*\"received_at\"\\s*:\\s*\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*"));
    }

    @Test
    void multipleRequestsShouldHaveDifferentTemplatedValues(@RestMonkey.RestMonkeyBaseUrl String baseUrl) throws Exception {
        // Make two requests and verify they have different random/time values
        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/random"))
                .GET()
                .build();
        
        var response1 = client.send(request, HttpResponse.BodyHandlers.ofString());
        Thread.sleep(10); // Small delay to ensure different timestamps
        var response2 = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        assertEquals(200, response1.statusCode());
        assertEquals(200, response2.statusCode());
        
        // Responses should be different due to random values and timestamps
        assertNotEquals(response1.body(), response2.body());
    }

    @Test
    void shouldWorkWithCrudEndpoints(@RestMonkey.RestMonkeyBaseUrl String baseUrl) throws Exception {
        // Verify that templating doesn't interfere with CRUD operations
        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/events"))
                .GET()
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Conference"));
        assertTrue(response.body().contains("San Francisco"));
    }
}
