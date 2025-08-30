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

import static org.junit.jupiter.api.Assertions.*;

/**
 * TinyRest UsersApiTest implementation.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-30
 * @version 1.0
 */
@ExtendWith(TinyRest.JUnitTinyRestExtension.class)
@TinyRest.UseTinyRest(
        configPath = "src/test/resources/tinyrest.yaml",
        port = 0 // auto-bind to a free port
)
class UsersApiTest {

    private static final Logger logger = LoggerFactory.getLogger(UsersApiTest.class);
    HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void setUp(TestInfo testInfo) {
        logger.info("=== Starting test: {} ===", testInfo.getDisplayName());
    }

    @AfterEach
    void tearDown(TestInfo testInfo) {
        logger.info("=== Completed test: {} ===", testInfo.getDisplayName());
    }

    @Test
    void listUsers(@TinyRest.TinyRestBaseUrl URI baseUrl) throws Exception {
        logger.info("Starting listUsers test with baseUrl: {}", baseUrl);

        var req = HttpRequest.newBuilder(baseUrl.resolve("/api/users")).GET().build();
        logger.debug("Sending GET request to: {}", req.uri());

        var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        logger.info("Received response: status={}, body length={}", resp.statusCode(), resp.body().length());
        logger.debug("Response body: {}", resp.body());

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("Ada"));
        logger.info("listUsers test completed successfully");
    }

    @Test
    void createUserRequiresAuth(@TinyRest.TinyRestBaseUrl URI baseUrl) throws Exception {
        logger.info("Starting createUserRequiresAuth test with baseUrl: {}", baseUrl);

        var requestBody = "{\"name\":\"Grace Hopper\",\"email\":\"g@navy\"}";
        var req = HttpRequest.newBuilder(baseUrl.resolve("/api/users"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        logger.debug("Sending POST request to: {} with body: {}", req.uri(), requestBody);
        logger.debug("Request headers: {}", req.headers().map());

        var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        logger.info("Received response: status={}, body: {}", resp.statusCode(), resp.body());

        assertEquals(401, resp.statusCode()); // auth token required by config
        logger.info("createUserRequiresAuth test completed successfully - got expected 401");
    }

    @Test
    void createUserWithAuth(@TinyRest.TinyRestBaseUrl URI baseUrl) throws Exception {
        logger.info("Starting createUserWithAuth test with baseUrl: {}", baseUrl);

        var requestBody = "{\"name\":\"Grace Hopper\",\"email\":\"g@navy\"}";
        var req = HttpRequest.newBuilder(baseUrl.resolve("/api/users"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer test-token")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        logger.debug("Sending authenticated POST request to: {} with body: {}", req.uri(), requestBody);
        logger.debug("Request headers: {}", req.headers().map());

        var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        logger.info("Received response: status={}, body: {}", resp.statusCode(), resp.body());
        logger.debug("Response headers: {}", resp.headers().map());

        assertEquals(201, resp.statusCode());
        assertTrue(resp.headers().firstValue("Location").isPresent());
        logger.info("createUserWithAuth test completed successfully - got expected 201 with Location header");
    }

    @Test
    void healthCheck(@TinyRest.TinyRestBaseUrl String baseUrl) throws Exception {
        logger.info("Starting healthCheck test with baseUrl: {}", baseUrl);

        var healthUrl = baseUrl + "/health";
        var req = HttpRequest.newBuilder(URI.create(healthUrl)).GET().build();
        logger.debug("Sending GET request to health endpoint: {}", healthUrl);

        var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        logger.info("Health check response: status={}, body: {}", resp.statusCode(), resp.body());

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("ok"));
        logger.info("healthCheck test completed successfully - health endpoint is working");
    }
}
