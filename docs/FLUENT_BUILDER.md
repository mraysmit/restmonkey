# RESTMonkey Fluent Builder Interface

The RESTMonkey fluent builder interface allows you to create and configure REST APIs programmatically without YAML files. This provides a clean, type-safe way to set up mock servers in your Java code.

## Quick Start

```java
import dev.mars.RESTMonkey.RESTMonkey;

// Create a simple server
var server = RESTMonkey.builder()
    .port(8080)
    .authToken("my-secret-token")
    .enableTemplating()
    .resource("users")
        .idField("id")
        .enableCrud()
        .seed("id", "u1", "name", "Alice", "email", "alice@example.com")
        .seed("id", "u2", "name", "Bob", "email", "bob@example.com")
        .done()
    .staticEndpoint()
        .get("/health")
        .status(200)
        .response("status", "healthy", "time", "{{now}}")
        .done()
    .start();

System.out.println("Server running at: " + server.getBaseUrl());
```

## Server Configuration

### Basic Settings
```java
RESTMonkey.builder()
    .port(8080)                    // Set port (0 for auto-assign)
    .authToken("secret-token")     // Enable authentication
    .noAuth()                      // Disable authentication
    .artificialLatency(100)        // Add latency in milliseconds
    .chaosFailRate(0.1)           // 10% random failures
```

### Features
```java
RESTMonkey.builder()
    .enableTemplating()           // Enable {{now}}, {{uuid}} templates
    .disableTemplating()          // Disable templating
    .enableHotReload()            // Watch config files for changes
    .disableHotReload()           // Disable hot reload
    .strictValidation()           // Strict schema validation
    .lenientValidation()          // Lenient schema validation
```

### Logging
```java
RESTMonkey.builder()
    .logLevel("DEBUG")            // Set log level
    .enableFileLogging()          // Enable file logging
    .enableHttpLogging()          // Log HTTP requests
    .disableHttpLogging()         // Disable HTTP logging
    .logDirectory("custom/logs")  // Custom log directory
```

## Resources (CRUD APIs)

Create REST resources with full CRUD operations:

```java
RESTMonkey.builder()
    .resource("users")
        .idField("userId")                    // Custom ID field name
        .enableCrud()                         // Enable CRUD endpoints
        .seed("userId", "u1", "name", "Alice", "email", "alice@example.com")
        .seed("userId", "u2", "name", "Bob", "email", "bob@example.com")
        .done()
    .resource("categories")
        .idField("categoryId")
        .disableCrud()                        // Data-only, no endpoints
        .seed("categoryId", "c1", "name", "Electronics")
        .done()
```

This creates endpoints:
- `GET /api/users` - List all users
- `POST /api/users` - Create user (requires auth)
- `GET /api/users/{id}` - Get user by ID
- `PUT /api/users/{id}` - Update user (requires auth)
- `DELETE /api/users/{id}` - Delete user (requires auth)

## Static Endpoints

Create custom endpoints with fixed responses:

```java
RESTMonkey.builder()
    // Simple text response
    .staticEndpoint()
        .get("/health")
        .response("healthy")
        .done()
    
    // JSON response with templates
    .staticEndpoint()
        .get("/info")
        .status(200)
        .response("service", "my-api", "time", "{{now}}", "id", "{{uuid}}")
        .done()
    
    // Echo request details
    .staticEndpoint()
        .post("/echo")
        .echoRequest()
        .requireAuth()
        .done()
    
    // Custom status codes
    .staticEndpoint()
        .get("/teapot")
        .status(418)
        .response("message", "I'm a teapot")
        .done()
```

## HTTP Methods

```java
.staticEndpoint()
    .get("/path")          // GET endpoint
    .post("/path")         // POST endpoint  
    .put("/path")          // PUT endpoint
    .delete("/path")       // DELETE endpoint
    .method("PATCH")       // Custom method
    .path("/custom")       // Set path separately
```

## Response Types

### Plain Text
```java
.response("Hello World")
```

### JSON Object (key-value pairs)
```java
.response("status", "ok", "count", 42, "active", true)
```

### JSON Object (from Map)
```java
Map<String, Object> data = Map.of("key", "value");
.response(data)
```

### JSON Array
```java
List<String> items = List.of("item1", "item2", "item3");
.response(items)
```

## Template Variables

When templating is enabled, use these variables in responses:

- `{{now}}` - Current ISO timestamp
- `{{uuid}}` - Random UUID
- `{{server.port}}` - Server port
- `{{server.routes.list}}` - Route summary

```java
.enableTemplating()
.staticEndpoint()
    .get("/status")
    .response("timestamp", "{{now}}", "server_id", "{{uuid}}")
    .done()
```

## Record & Replay

```java
RESTMonkey.builder()
    .recordRequests("requests.json")     // Record all requests
    .replayRequests("requests.json")     // Replay from file
    .replayOnMiss("passthrough")         // Behavior when no match
```

## Complete Example

```java
public class MyApiServer {
    public static void main(String[] args) throws Exception {
        var server = RESTMonkey.builder()
            .port(8080)
            .authToken("demo-token")
            .enableTemplating()
            .enableFileLogging()
            .logLevel("INFO")
            
            // Users API
            .resource("users")
                .idField("userId")
                .enableCrud()
                .seed("userId", "u1", "name", "Alice", "role", "admin")
                .seed("userId", "u2", "name", "Bob", "role", "user")
                .done()
            
            // Health endpoints
            .staticEndpoint()
                .get("/health")
                .response("status", "healthy", "time", "{{now}}")
                .done()
            
            .staticEndpoint()
                .get("/info")
                .response("service", "My API", "version", "1.0", "port", "{{server.port}}")
                .done()
            
            // Echo for testing
            .staticEndpoint()
                .post("/echo")
                .echoRequest()
                .requireAuth()
                .done()
            
            .start();
        
        System.out.println("API Server running at: " + server.getBaseUrl());
        System.out.println("Use: curl -H 'Authorization: Bearer demo-token' " + server.getBaseUrl() + "/api/users");
        
        // Keep running
        Thread.currentThread().join();
    }
}
```

## Benefits

- **No YAML files needed** - Pure Java configuration
- **Type safety** - Compile-time validation
- **IDE support** - Full autocomplete and refactoring
- **Fluent API** - Readable, chainable method calls
- **All features supported** - Everything from YAML configs available
- **Easy testing** - Perfect for unit/integration tests
