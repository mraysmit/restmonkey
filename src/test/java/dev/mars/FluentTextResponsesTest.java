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
 * Text response tests using Fluent Builder API only (no YAML configuration)
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-31
 * @version 1.0
 */
class FluentTextResponsesTest {

    private static final Logger logger = LoggerFactory.getLogger(FluentTextResponsesTest.class);
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void shouldReturnSimpleTextResponses() throws Exception {
        logger.info("Testing simple text responses using fluent API");
        
        var server = RestMonkey.builder()
            .port(0)
            .staticEndpoint()
                .get("/health")
                .status(200)
                .response("healthy")
                .done()
            .staticEndpoint()
                .get("/status")
                .status(200)
                .response("OK")
                .done()
            .staticEndpoint()
                .get("/ping")
                .status(200)
                .response("pong")
                .done()
            .staticEndpoint()
                .get("/ready")
                .status(200)
                .response("READY")
                .done()
            .start();
        
        try {
            String baseUrl = server.getBaseUrl();
            
            // Test various simple text endpoints
            assertTextResponse(baseUrl, "/health", 200, "healthy");
            assertTextResponse(baseUrl, "/status", 200, "OK");
            assertTextResponse(baseUrl, "/ping", 200, "pong");
            assertTextResponse(baseUrl, "/ready", 200, "READY");
            
        } finally {
            server.stop();
        }
    }

    @Test
    void shouldReturnCustomStatusCodes() throws Exception {
        logger.info("Testing custom status codes with text responses using fluent API");
        
        var server = RestMonkey.builder()
            .port(0)
            .staticEndpoint()
                .get("/created")
                .status(201)
                .response("Resource created")
                .done()
            .staticEndpoint()
                .get("/accepted")
                .status(202)
                .response("Request accepted")
                .done()
            .staticEndpoint()
                .get("/teapot")
                .status(418)
                .response("I'm a teapot")
                .done()
            .staticEndpoint()
                .get("/error")
                .status(500)
                .response("Internal server error")
                .done()
            .staticEndpoint()
                .get("/forbidden")
                .status(403)
                .response("Access denied")
                .done()
            .start();
        
        try {
            String baseUrl = server.getBaseUrl();
            
            assertTextResponse(baseUrl, "/created", 201, "Resource created");
            assertTextResponse(baseUrl, "/accepted", 202, "Request accepted");
            assertTextResponse(baseUrl, "/teapot", 418, "I'm a teapot");
            assertTextResponse(baseUrl, "/error", 500, "Internal server error");
            assertTextResponse(baseUrl, "/forbidden", 403, "Access denied");
            
        } finally {
            server.stop();
        }
    }

    @Test
    void shouldHandleMultipleHttpMethods() throws Exception {
        logger.info("Testing multiple HTTP methods with text responses using fluent API");
        
        var server = RestMonkey.builder()
            .port(0)
            .staticEndpoint()
                .get("/api/status")
                .status(200)
                .response("GET OK")
                .done()
            .staticEndpoint()
                .post("/api/status")
                .status(201)
                .response("POST Created")
                .done()
            .staticEndpoint()
                .put("/api/status")
                .status(200)
                .response("PUT Updated")
                .done()
            .staticEndpoint()
                .delete("/api/status")
                .status(200)
                .response("DELETE No Content")
                .done()
            .start();
        
        try {
            String baseUrl = server.getBaseUrl();
            
            // Test GET
            var getRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/status"))
                .GET()
                .build();
            var getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, getResponse.statusCode());
            assertEquals("\"GET OK\"", getResponse.body());
            
            // Test POST
            var postRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/status"))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
            var postResponse = client.send(postRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(201, postResponse.statusCode());
            assertEquals("\"POST Created\"", postResponse.body());
            
            // Test PUT
            var putRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/status"))
                .PUT(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
            var putResponse = client.send(putRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, putResponse.statusCode());
            assertEquals("\"PUT Updated\"", putResponse.body());
            
            // Test DELETE
            var deleteRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/status"))
                .DELETE()
                .build();
            var deleteResponse = client.send(deleteRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, deleteResponse.statusCode());
            assertEquals("\"DELETE No Content\"", deleteResponse.body());
            
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
