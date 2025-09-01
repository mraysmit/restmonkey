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
 * Users API tests using Fluent Builder API only (no YAML configuration)
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-31
 * @version 1.0
 */
class FluentUsersApiTest {

    private static final Logger logger = LoggerFactory.getLogger(FluentUsersApiTest.class);
    private final HttpClient http = HttpClient.newHttpClient();
    private RestMonkey server;
    private String baseUrl;

    @BeforeEach
    void setUp(TestInfo testInfo) throws Exception {
        logger.info("=== Starting test: {} ===", testInfo.getDisplayName());
        
        server = RestMonkey.builder()
            .port(0) // Auto-bind to free port
            .authToken("test-token")
            .enableTemplating()
            .enableHotReload()
            .resource("users")
                .idField("id")
                .enableCrud()
                .seed("id", "u1", "name", "Ada Lovelace", "email", "ada@example.com", "age", 36)
                .seed("id", "u2", "name", "Grace Hopper", "email", "grace@navy.mil", "age", 85)
                .seed("id", "u3", "name", "Katherine Johnson", "email", "katherine@nasa.gov", "age", 101)
                .done()
            .staticEndpoint()
                .get("/health")
                .status(200)
                .response("status", "healthy", "timestamp", "{{now}}")
                .done()
            .start();
        
        baseUrl = server.getBaseUrl();
    }

    @AfterEach
    void tearDown(TestInfo testInfo) throws Exception {
        logger.info("=== Completed test: {} ===", testInfo.getDisplayName());
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void listUsers() throws Exception {
        logger.info("Starting listUsers test with baseUrl: {}", baseUrl);

        var req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/users")).GET().build();
        logger.debug("Sending GET request to: {}", req.uri());

        var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        logger.info("Received response: status={}, body length={}", resp.statusCode(), resp.body().length());
        logger.debug("Response body: {}", resp.body());

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("Ada"));
        assertTrue(resp.body().contains("Grace"));
        assertTrue(resp.body().contains("Katherine"));
        logger.info("listUsers test completed successfully");
    }

    @Test
    void createUserRequiresAuth() throws Exception {
        logger.info("Starting createUserRequiresAuth test with baseUrl: {}", baseUrl);

        var requestBody = "{\"name\":\"Alan Turing\",\"email\":\"alan@bletchley.gov.uk\"}";
        var req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/users"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        logger.debug("Sending POST request without auth to: {}", req.uri());
        var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        logger.info("Received response: status={}, body={}", resp.statusCode(), resp.body());

        assertEquals(401, resp.statusCode());
        assertTrue(resp.body().contains("Missing/invalid bearer token"));
        logger.info("createUserRequiresAuth test completed successfully");
    }

    @Test
    void createUserWithValidAuth() throws Exception {
        logger.info("Starting createUserWithValidAuth test with baseUrl: {}", baseUrl);

        var requestBody = "{\"name\":\"Alan Turing\",\"email\":\"alan@bletchley.gov.uk\"}";
        var req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/users"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer test-token")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        logger.debug("Sending POST request with auth to: {}", req.uri());
        var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        logger.info("Received response: status={}, body={}", resp.statusCode(), resp.body());

        assertEquals(201, resp.statusCode());
        assertTrue(resp.body().contains("Alan Turing"));
        assertTrue(resp.body().contains("alan@bletchley.gov.uk"));
        logger.info("createUserWithValidAuth test completed successfully");
    }

    @Test
    void getUserById() throws Exception {
        logger.info("Starting getUserById test with baseUrl: {}", baseUrl);

        var req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/users/u1")).GET().build();
        logger.debug("Sending GET request to: {}", req.uri());

        var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        logger.info("Received response: status={}, body={}", resp.statusCode(), resp.body());

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("Ada Lovelace"));
        assertTrue(resp.body().contains("ada@example.com"));
        logger.info("getUserById test completed successfully");
    }

    @Test
    void updateUserRequiresAuth() throws Exception {
        logger.info("Starting updateUserRequiresAuth test with baseUrl: {}", baseUrl);

        var requestBody = "{\"name\":\"Ada Lovelace Updated\",\"email\":\"ada.updated@example.com\"}";
        var req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/users/u1"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        logger.debug("Sending PUT request without auth to: {}", req.uri());
        var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        logger.info("Received response: status={}, body={}", resp.statusCode(), resp.body());

        assertEquals(401, resp.statusCode());
        logger.info("updateUserRequiresAuth test completed successfully");
    }

    @Test
    void deleteUserRequiresAuth() throws Exception {
        logger.info("Starting deleteUserRequiresAuth test with baseUrl: {}", baseUrl);

        var req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/users/u1"))
                .DELETE()
                .build();

        logger.debug("Sending DELETE request without auth to: {}", req.uri());
        var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        logger.info("Received response: status={}, body={}", resp.statusCode(), resp.body());

        assertEquals(401, resp.statusCode());
        logger.info("deleteUserRequiresAuth test completed successfully");
    }
}
