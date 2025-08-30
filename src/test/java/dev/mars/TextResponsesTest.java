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

import dev.mars.tinyrest.TinyRest;
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
 * Tests for simple text responses from static endpoints.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-30
 * @version 1.0
 */
@ExtendWith(TinyRest.JUnitTinyRestExtension.class)
@TinyRest.UseTinyRest(configPath = "src/test/resources/config-text-responses.yaml")
class TextResponsesTest {

    private static final Logger logger = LoggerFactory.getLogger(TextResponsesTest.class);
    HttpClient client = HttpClient.newHttpClient();

    @Test
    void shouldReturnSimpleTextResponses(@TinyRest.TinyRestBaseUrl String baseUrl) throws Exception {
        logger.info("Testing simple text responses");

        // Test various simple text endpoints
        assertTextResponse(baseUrl, "/health", 200, "healthy");
        assertTextResponse(baseUrl, "/status", 200, "OK");
        assertTextResponse(baseUrl, "/ping", 200, "pong");
        assertTextResponse(baseUrl, "/ready", 200, "READY");
    }

    @Test
    void shouldReturnCustomStatusCodes(@TinyRest.TinyRestBaseUrl String baseUrl) throws Exception {
        logger.info("Testing custom status codes with text responses");

        assertTextResponse(baseUrl, "/created", 201, "Resource created");
        assertTextResponse(baseUrl, "/accepted", 202, "Request accepted");
        assertTextResponse(baseUrl, "/teapot", 418, "I'm a teapot");
        assertTextResponse(baseUrl, "/error", 500, "Internal server error");
        assertTextResponse(baseUrl, "/forbidden", 403, "Access denied");
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
