package dev.mars;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.net.URI;
import java.net.http.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(TinyRest.JUnitTinyRestExtension.class)
@TinyRest.UseTinyRest(
        configPath = "src/test/resources/tinyrest.yml",
        port = 0 // auto-bind to a free port
)
class UsersApiTest {

    HttpClient http = HttpClient.newHttpClient();

    @Test
    void listUsers(@TinyRest.TinyRestBaseUrl URI baseUrl) throws Exception {
        var req = HttpRequest.newBuilder(baseUrl.resolve("/api/users")).GET().build();
        var resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("Ada"));
    }

    @Test
    void createUserRequiresAuth(@TinyRest.TinyRestBaseUrl URI baseUrl) throws Exception {
        var req = HttpRequest.newBuilder(baseUrl.resolve("/api/users"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Grace Hopper\",\"email\":\"g@navy\"}"))
                .build();

        var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, resp.statusCode()); // auth token required by config
    }

    @Test
    void createUserWithAuth(@TinyRest.TinyRestBaseUrl URI baseUrl) throws Exception {
        var req = HttpRequest.newBuilder(baseUrl.resolve("/api/users"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer test-token")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Grace Hopper\",\"email\":\"g@navy\"}"))
                .build();

        var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, resp.statusCode());
        assertTrue(resp.headers().firstValue("Location").isPresent());
    }

    @Test
    void healthCheck(@TinyRest.TinyRestBaseUrl String baseUrl) throws Exception {
        var req = HttpRequest.newBuilder(URI.create(baseUrl + "/health")).GET().build();
        var resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("ok"));
    }
}
