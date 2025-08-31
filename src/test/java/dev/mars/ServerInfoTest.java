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
 * Tests for server information endpoints and simple text responses.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-30
 * @version 1.0
 */
@ExtendWith(RestMonkey.JUnitRestMonkeyExtension.class)
@RestMonkey.UseRestMonkey(configPath = "src/test/resources/config-server-info.yaml")
class ServerInfoTest {

    private static final Logger logger = LoggerFactory.getLogger(ServerInfoTest.class);
    HttpClient client = HttpClient.newHttpClient();

    @Test
    void shouldReturnSimpleTextResponse(@RestMonkey.RestMonkeyBaseUrl String baseUrl) throws Exception {
        logger.info("Testing simple text health response");

        // Test simple "healthy" response
        var healthRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/health"))
                .GET()
                .build();
        var healthResponse = client.send(healthRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, healthResponse.statusCode());
        assertEquals("\"healthy\"", healthResponse.body()); // JSON string
        logger.info("Health response: {}", healthResponse.body());
    }

    @Test
    void shouldReturnServerInfo(@RestMonkey.RestMonkeyBaseUrl String baseUrl) throws Exception {
        logger.info("Testing server info endpoint");

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
        assertTrue(body.contains("\"features\""));
        assertTrue(body.contains("\"templating\""));
        assertTrue(body.contains("\"hot_reload\""));
        assertTrue(body.contains("\"schema_validation\""));
        
        // Verify no template expressions remain (they should be replaced)
        assertFalse(body.contains("{{server."));
        assertFalse(body.contains("{{java."));
    }

    @Test
    void shouldReturnRouteInfo(@RestMonkey.RestMonkeyBaseUrl String baseUrl) throws Exception {
        logger.info("Testing route info endpoint");

        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/server/routes"))
                .GET()
                .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        
        String body = response.body();
        logger.info("Route info response: {}", body);

        // Verify route information is present
        assertTrue(body.contains("\"total_routes\""));
        assertTrue(body.contains("\"crud_routes\""));
        assertTrue(body.contains("\"static_routes\""));
        assertTrue(body.contains("\"routes\""));
        
        // Should contain route summary as comma-separated list
        assertTrue(body.contains("\"routes\":\"GET api/users"));
        assertTrue(body.contains("POST api/users"));
        assertTrue(body.contains("GET health"));
        assertTrue(body.contains("GET server/info"));

        // Verify no template expressions remain
        assertFalse(body.contains("{{server."));
    }

    @Test
    void shouldReturnDetailedRoutesList(@RestMonkey.RestMonkeyBaseUrl String baseUrl) throws Exception {
        logger.info("Testing detailed routes list endpoint");

        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/server/routes/list"))
                .GET()
                .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());

        String body = response.body();
        logger.info("Detailed routes list response: {}", body);

        // Should be a JSON string containing a JSON array
        assertTrue(body.startsWith("\"[{"));
        assertTrue(body.endsWith("}]\""));
        assertTrue(body.contains("\\\"method\\\":\\\"GET\\\""));
        assertTrue(body.contains("\\\"path\\\":\\\"api/users\\\""));
        assertTrue(body.contains("\\\"type\\\":\\\"crud\\\""));
        assertTrue(body.contains("\\\"type\\\":\\\"static\\\""));
        assertTrue(body.contains("\\\"mutates\\\":true"));
        assertTrue(body.contains("\\\"mutates\\\":false"));

        // Should contain clean paths (note: double slash is current behavior)
        assertTrue(body.contains("\\\"path\\\":\\\"api/users//:id\\\""));
        assertTrue(body.contains("\\\"path\\\":\\\"health\\\""));
        assertTrue(body.contains("\\\"path\\\":\\\"server/info\\\""));
    }

    @Test
    void shouldReturnResourceInfo(@RestMonkey.RestMonkeyBaseUrl String baseUrl) throws Exception {
        logger.info("Testing resource info endpoint");

        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/server/resources"))
                .GET()
                .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        
        String body = response.body();
        logger.info("Resource info response: {}", body);

        // Verify resource information is present
        assertTrue(body.contains("\"total_resources\""));
        assertTrue(body.contains("\"resources\""));
        
        // Should contain resource details as JSON string (embedded JSON)
        assertTrue(body.contains("\"resources\":\"[{"));
        assertTrue(body.contains("\\\"name\\\""));
        assertTrue(body.contains("\\\"idField\\\""));
        assertTrue(body.contains("\\\"count\\\""));

        // Should show the users resource
        assertTrue(body.contains("\\\"users\\\""));

        // Verify no template expressions remain
        assertFalse(body.contains("{{server."));
    }
}
