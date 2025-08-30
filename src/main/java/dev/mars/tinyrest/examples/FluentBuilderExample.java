package dev.mars.tinyrest.examples;

import dev.mars.tinyrest.TinyRest;

/**
 * Example demonstrating the fluent builder interface for TinyRest.
 * Shows how to create a complete REST API without any YAML configuration files.
 */
public class FluentBuilderExample {
    
    public static void main(String[] args) throws Exception {
        System.out.println("🚀 Starting TinyRest with Fluent Builder Interface");
        
        // Create a complete REST API using the fluent builder
        var server = TinyRest.builder()
                // Server configuration
                .port(8080)
                .authToken("demo-secret-token")
                .enableTemplating()
                .enableFileLogging()
                .logLevel("INFO")
                
                // Users resource with CRUD operations
                .resource("users")
                    .idField("userId")
                    .enableCrud()
                    .seed("userId", "u1", "name", "Alice Johnson", "email", "alice@example.com", "role", "admin")
                    .seed("userId", "u2", "name", "Bob Smith", "email", "bob@example.com", "role", "user")
                    .seed("userId", "u3", "name", "Carol Davis", "email", "carol@example.com", "role", "user")
                    .done()
                
                // Products resource with CRUD operations
                .resource("products")
                    .idField("productId")
                    .enableCrud()
                    .seed("productId", "p1", "name", "Laptop", "price", 999.99, "category", "electronics")
                    .seed("productId", "p2", "name", "Coffee Mug", "price", 12.50, "category", "kitchen")
                    .seed("productId", "p3", "name", "Notebook", "price", 5.99, "category", "office")
                    .done()
                
                // Categories resource (data-only, no CRUD endpoints)
                .resource("categories")
                    .idField("categoryId")
                    .disableCrud() // Only for data storage, no REST endpoints
                    .seed("categoryId", "electronics", "name", "Electronics", "description", "Electronic devices")
                    .seed("categoryId", "kitchen", "name", "Kitchen", "description", "Kitchen supplies")
                    .seed("categoryId", "office", "name", "Office", "description", "Office supplies")
                    .done()
                
                // Health check endpoint
                .staticEndpoint()
                    .get("/health")
                    .status(200)
                    .response("status", "healthy", "service", "tinyrest-demo", "timestamp", "{{now}}", "uptime", "{{now}}")
                    .done()
                
                // Server info endpoint
                .staticEndpoint()
                    .get("/info")
                    .status(200)
                    .response(
                        "service", "TinyRest Demo API",
                        "version", "1.0.0",
                        "description", "Demonstration of fluent builder interface",
                        "endpoints", "See /api/users, /api/products, /health, /info, /echo",
                        "timestamp", "{{now}}"
                    )
                    .done()
                
                // Echo endpoint for testing
                .staticEndpoint()
                    .post("/echo")
                    .echoRequest()
                    .requireAuth()
                    .done()
                
                // Custom status codes
                .staticEndpoint()
                    .get("/teapot")
                    .status(418)
                    .response("message", "I'm a teapot", "rfc", "RFC 2324")
                    .done()
                
                // Created response
                .staticEndpoint()
                    .post("/webhook")
                    .status(201)
                    .response("message", "Webhook received", "id", "{{uuid}}", "timestamp", "{{now}}")
                    .requireAuth()
                    .done()
                
                // Start the server
                .start();
        
        String baseUrl = server.getBaseUrl();
        int port = server.getPort();
        
        System.out.println("✅ TinyRest server started successfully!");
        System.out.println("🌐 Base URL: " + baseUrl);
        System.out.println("🔌 Port: " + port);
        System.out.println();
        System.out.println("📋 Available Endpoints:");
        System.out.println("  GET  " + baseUrl + "/health           - Health check");
        System.out.println("  GET  " + baseUrl + "/info             - Server information");
        System.out.println("  GET  " + baseUrl + "/teapot           - I'm a teapot (418)");
        System.out.println();
        System.out.println("👥 Users CRUD API:");
        System.out.println("  GET    " + baseUrl + "/api/users       - List all users");
        System.out.println("  POST   " + baseUrl + "/api/users       - Create user (auth required)");
        System.out.println("  GET    " + baseUrl + "/api/users/{id}  - Get user by ID");
        System.out.println("  PUT    " + baseUrl + "/api/users/{id}  - Update user (auth required)");
        System.out.println("  DELETE " + baseUrl + "/api/users/{id}  - Delete user (auth required)");
        System.out.println();
        System.out.println("🛍️  Products CRUD API:");
        System.out.println("  GET    " + baseUrl + "/api/products       - List all products");
        System.out.println("  POST   " + baseUrl + "/api/products       - Create product (auth required)");
        System.out.println("  GET    " + baseUrl + "/api/products/{id}  - Get product by ID");
        System.out.println("  PUT    " + baseUrl + "/api/products/{id}  - Update product (auth required)");
        System.out.println("  DELETE " + baseUrl + "/api/products/{id}  - Delete product (auth required)");
        System.out.println();
        System.out.println("🔐 Authentication:");
        System.out.println("  Use header: Authorization: Bearer demo-secret-token");
        System.out.println();
        System.out.println("🧪 Test Commands:");
        System.out.println("  curl " + baseUrl + "/health");
        System.out.println("  curl " + baseUrl + "/api/users");
        System.out.println("  curl " + baseUrl + "/api/products");
        System.out.println("  curl -H 'Authorization: Bearer demo-secret-token' \\");
        System.out.println("       -H 'Content-Type: application/json' \\");
        System.out.println("       -d '{\"test\":\"data\"}' \\");
        System.out.println("       " + baseUrl + "/echo");
        System.out.println();
        System.out.println("Press Ctrl+C to stop the server");
        
        // Keep the server running
        Thread.currentThread().join();
    }
}
