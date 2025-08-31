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

import java.net.URI;
import java.net.http.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RestMonkey LoggingDemoTest implementation.
 *
 * @author Mark Andrew Ray-Smith Cityline Ltd
 * @since 2025-08-30
 * @version 1.0
 */
@ExtendWith(RestMonkey.JUnitRestMonkeyExtension.class)
@RestMonkey.UseRestMonkey(
        configPath = "src/test/resources/RestMonkey-trace.yaml",
        recordReplayFile = "target/demo-recordings.jsonl"
)
class LoggingDemoTest {

    @Test
    void demonstrateDetailedLogging(@RestMonkey.RestMonkeyBaseUrl String baseUrl) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        
        // Test 1: Health check (static endpoint with templating)
        var healthRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/health"))
                .GET()
                .build();
        var healthResponse = client.send(healthRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, healthResponse.statusCode());
        
        // Test 2: List users (CRUD endpoint)
        var listRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/users?limit=10&offset=0"))
                .GET()
                .build();
        var listResponse = client.send(listRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, listResponse.statusCode());
        
        // Test 3: Create user with auth (successful)
        var createRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/users"))
                .header("Authorization", "Bearer test-token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Charlie\",\"email\":\"charlie@example.com\"}"))
                .build();
        var createResponse = client.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createResponse.statusCode());
        
        // Test 4: Create user without auth (should fail)
        var unauthorizedRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/users"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Dave\",\"email\":\"dave@example.com\"}"))
                .build();
        var unauthorizedResponse = client.send(unauthorizedRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, unauthorizedResponse.statusCode());
        
        // Test 5: Echo endpoint (requires auth since it's POST)
        var echoRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/echo?test=value"))
                .header("Authorization", "Bearer test-token")
                .header("X-Custom-Header", "test-value")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"test\":\"data\"}"))
                .build();
        var echoResponse = client.send(echoRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, echoResponse.statusCode());
        
        // Test 6: Non-existent endpoint (should 404)
        var notFoundRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/nonexistent"))
                .GET()
                .build();
        var notFoundResponse = client.send(notFoundRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, notFoundResponse.statusCode());
    }
}
