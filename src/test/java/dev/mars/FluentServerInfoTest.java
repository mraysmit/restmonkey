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
 * Server info tests using Fluent Builder API only (no YAML configuration)
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-31
 * @version 1.0
 */
class FluentServerInfoTest {

    private static final Logger logger = LoggerFactory.getLogger(FluentServerInfoTest.class);
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void shouldReturnSimpleTextResponse() throws Exception {
        logger.info("Testing simple text health response using fluent API");
        
        var server = RestMonkey.builder()
            .port(0)
            .staticEndpoint()
                .get("/health")
                .status(200)
                .response("healthy")
                .done()
            .start();
        
        try {
            String baseUrl = server.getBaseUrl();
            
            // Test simple "healthy" response
            var healthRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/health"))
                .GET()
                .build();
            var healthResponse = client.send(healthRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, healthResponse.statusCode());
            assertEquals("\"healthy\"", healthResponse.body()); // JSON string
            logger.info("Health response: {}", healthResponse.body());
            
        } finally {
            server.stop();
        }
    }

    @Test
    void shouldReturnServerInfo() throws Exception {
        logger.info("Testing server info endpoint using fluent API");
        
        var server = RestMonkey.builder()
            .port(0)
            .enableTemplating()
            .staticEndpoint()
                .get("/server/info")
                .status(200)
                .response(
                    "server", "RestMonkey",
                    "version", "1.0.0-SNAPSHOT",
                    "java_version", "{{java.version}}",
                    "port", "{{server.port}}",
                    "started_at", "{{server.started}}",
                    "timestamp", "{{now}}"
                )
                .done()
            .start();
        
        try {
            String baseUrl = server.getBaseUrl();
            
            var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/server/info"))
                .GET()
                .build();
            
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            
            String body = response.body();
            logger.info("Server info response: {}", body);
            
            // Verify server information is present
            assertTrue(body.contains("\"server\":\"RestMonkey\""));
            assertTrue(body.contains("\"version\":\"1.0.0-SNAPSHOT\""));
            assertTrue(body.contains("\"java_version\""));
            assertTrue(body.contains("\"port\""));
            assertTrue(body.contains("\"started_at\""));
            assertTrue(body.contains("\"timestamp\""));
            assertTrue(body.contains("\"timestamp\""));
            
            // Verify templates were rendered
            assertFalse(body.contains("{{port}}"));
            assertFalse(body.contains("{{uptime}}"));
            assertFalse(body.contains("{{now}}"));
            
        } finally {
            server.stop();
        }
    }

    @Test
    void shouldReturnEnvironmentInfo() throws Exception {
        logger.info("Testing environment info endpoint using fluent API");
        
        var server = RestMonkey.builder()
            .port(0)
            .enableTemplating()
            .staticEndpoint()
                .get("/env")
                .status(200)
                .response(
                    "environment", "test",
                    "hostname", System.getProperty("user.name"),
                    "os", System.getProperty("os.name"),
                    "arch", System.getProperty("os.arch"),
                    "java_home", System.getProperty("java.home"),
                    "working_dir", System.getProperty("user.dir")
                )
                .done()
            .start();
        
        try {
            String baseUrl = server.getBaseUrl();
            
            var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/env"))
                .GET()
                .build();
            
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            
            String body = response.body();
            logger.info("Environment info response: {}", body);
            
            // Verify environment information is present
            assertTrue(body.contains("\"environment\":\"test\""));
            assertTrue(body.contains("\"hostname\""));
            assertTrue(body.contains("\"os\""));
            assertTrue(body.contains("\"arch\""));
            assertTrue(body.contains("\"java_home\""));
            assertTrue(body.contains("\"working_dir\""));
            
        } finally {
            server.stop();
        }
    }

    @Test
    void shouldReturnMetricsInfo() throws Exception {
        logger.info("Testing metrics endpoint using fluent API");
        
        var server = RestMonkey.builder()
            .port(0)
            .enableTemplating()
            .staticEndpoint()
                .get("/metrics")
                .status(200)
                .response(
                    "requests_total", 0,
                    "uptime_seconds", "{{now}}",
                    "memory_used_mb", Runtime.getRuntime().totalMemory() / 1024 / 1024,
                    "memory_free_mb", Runtime.getRuntime().freeMemory() / 1024 / 1024,
                    "cpu_cores", Runtime.getRuntime().availableProcessors()
                )
                .done()
            .start();
        
        try {
            String baseUrl = server.getBaseUrl();
            
            var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/metrics"))
                .GET()
                .build();
            
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            
            String body = response.body();
            logger.info("Metrics response: {}", body);
            
            // Verify metrics information is present
            assertTrue(body.contains("\"requests_total\":0"));
            assertTrue(body.contains("\"uptime_seconds\""));
            assertTrue(body.contains("\"memory_used_mb\""));
            assertTrue(body.contains("\"memory_free_mb\""));
            assertTrue(body.contains("\"cpu_cores\""));
            
            // Verify timestamp template was rendered
            assertFalse(body.contains("{{now}}"));
            assertTrue(body.contains("2025-")); // Should contain current year
            
        } finally {
            server.stop();
        }
    }

    private void assertTextResponse(String baseUrl, String path, int expectedStatus, String expectedText) throws Exception {
        var request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .GET()
            .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(expectedStatus, response.statusCode());
        assertEquals("\"" + expectedText + "\"", response.body()); // JSON string format
        logger.info("{} -> {} {}", path, expectedStatus, response.body());
    }
}
