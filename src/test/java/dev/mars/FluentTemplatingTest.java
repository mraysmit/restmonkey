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
 * Template rendering tests using Fluent Builder API only (no YAML configuration)
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-31
 * @version 1.0
 */
class FluentTemplatingTest {

    private static final Logger logger = LoggerFactory.getLogger(FluentTemplatingTest.class);
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void shouldRenderNowTemplate() throws Exception {
        logger.info("Testing {{now}} template rendering using fluent API");
        
        var server = RestMonkey.builder()
            .port(0)
            .enableTemplating()
            .staticEndpoint()
                .get("/time")
                .status(200)
                .response(
                    "current_time", "{{now}}",
                    "formatted_time", "Current time: {{now}}",
                    "message", "Time endpoint"
                )
                .done()
            .start();
        
        try {
            String baseUrl = server.getBaseUrl();
            
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
            
        } finally {
            server.stop();
        }
    }

    @Test
    void shouldRenderRandomTemplates() throws Exception {
        logger.info("Testing random templates using fluent API");
        
        var server = RestMonkey.builder()
            .port(0)
            .enableTemplating()
            .staticEndpoint()
                .get("/random")
                .status(200)
                .response(
                    "uuid", "{{uuid}}",
                    "random_number", "{{random.int(1,100)}}"
                )
                .done()
            .start();
        
        try {
            String baseUrl = server.getBaseUrl();
            
            var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/random"))
                .GET()
                .build();
            
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            
            String body = response.body();
            logger.info("Random template response: {}", body);
            
            // Should not contain template placeholders
            assertFalse(body.contains("{{uuid}}"));
            assertFalse(body.contains("{{random.int"));

            // Should contain valid UUID format
            assertTrue(body.matches(".*\"uuid\"\\s*:\\s*\"[0-9a-f-]{36}\".*"));

            // Should contain random number
            assertTrue(body.matches(".*\"random_number\"\\s*:\\s*\"\\d+\".*"));
            
        } finally {
            server.stop();
        }
    }

    @Test
    void shouldRenderTemplatesInResourceData() throws Exception {
        logger.info("Testing template rendering in resource seed data using fluent API");
        
        var server = RestMonkey.builder()
            .port(0)
            .enableTemplating()
            .resource("events")
                .idField("eventId")
                .enableCrud()
                .seed("eventId", "e1", "name", "Meeting", "timestamp", "{{now}}", "id", "{{uuid}}")
                .seed("eventId", "e2", "name", "Conference", "timestamp", "{{now}}", "attendees", "{{random}}")
                .done()
            .start();
        
        try {
            String baseUrl = server.getBaseUrl();
            
            var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/events"))
                .GET()
                .build();
            
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            
            String body = response.body();
            logger.info("Resource template response: {}", body);
            
            // Templates should NOT be rendered in resource seed data - they remain as literal strings
            assertTrue(body.contains("{{now}}"));
            assertTrue(body.contains("{{uuid}}"));
            assertTrue(body.contains("{{random}}"));

            // Should contain the literal template strings, not rendered values
            assertTrue(body.contains("\"timestamp\":\"{{now}}\""));
            assertTrue(body.contains("\"id\":\"{{uuid}}\""));
            
        } finally {
            server.stop();
        }
    }

    @Test
    void shouldDisableTemplatingWhenNotEnabled() throws Exception {
        logger.info("Testing that templates are NOT rendered when templating is disabled");
        
        var server = RestMonkey.builder()
            .port(0)
            // Note: NOT calling .enableTemplating()
            .staticEndpoint()
                .get("/no-templates")
                .status(200)
                .response(
                    "timestamp", "{{now}}",
                    "id", "{{uuid}}"
                )
                .done()
            .start();
        
        try {
            String baseUrl = server.getBaseUrl();
            
            var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/no-templates"))
                .GET()
                .build();
            
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            
            String body = response.body();
            logger.info("No templating response: {}", body);
            
            // Templates should NOT be rendered - should remain as literal text
            assertTrue(body.contains("{{now}}"));
            assertTrue(body.contains("{{uuid}}"));
            
        } finally {
            server.stop();
        }
    }
}
