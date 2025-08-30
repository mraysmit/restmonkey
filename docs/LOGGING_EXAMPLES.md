# TinyRest Detailed Logging Examples

This document shows examples of the comprehensive logging system implemented in TinyRest, demonstrating all log levels with real output from actual test runs.

## Visual Logging Features

- **Color-Coded Output**: INFO=blue, WARN=yellow, ERROR=red, DEBUG=cyan
- **Performance Timing**: Every HTTP request shows duration
- **Structured Format**: Timestamp, thread, level, logger, message

## Log Level Examples

### TRACE Level (Most Detailed)
Shows every internal operation, perfect for deep debugging:

```
20:41:30.463 [pool-2-thread-1] TRACE TinyRest$Engine - Incoming request: GET /health from /127.0.0.1:62610
20:41:30.527 [pool-2-thread-1] TRACE TinyRest$Engine - Testing route pattern ^/\Qapi\E/\Qusers\E$ against path /health: method match=true, pattern match=false
20:41:30.528 [pool-2-thread-1] TRACE TinyRest$Engine - Testing route pattern ^/\Qhealth\E$ against path /health: method match=true, pattern match=true
20:41:30.531 [pool-2-thread-1] TRACE TinyRest$Engine - Applying templating to static response for GET /health
20:41:30.532 [pool-2-thread-1] TRACE TinyRest$Engine - Returning static response for GET /health with status 200
20:41:30.638 [pool-2-thread-2] TRACE TinyRest$Engine - Listing resources from 'users': limit=10, offset=0
20:41:30.639 [pool-2-thread-2] TRACE TinyRest$Engine - Retrieved 2 items from resource 'users'
20:41:30.704 [pool-2-thread-3] TRACE TinyRest$Engine - Generated new ID 'a34bbfd2-a368-4b0a-8bdf-f261648a4b42' for resource 'users'
20:41:30.705 [pool-2-thread-3] TRACE TinyRest$Engine - Created new resource 'users' with ID 'a34bbfd2-a368-4b0a-8bdf-f261648a4b42'
20:41:30.216 [main] TRACE TinyRest$Engine - Seeded record with ID '1' in resource 'users'
20:41:30.217 [main] TRACE TinyRest$Engine - Seeded record with ID '2' in resource 'users'
```

### DEBUG Level (Development)
Shows detailed flow and decision points:

```
20:41:30.201 [main] DEBUG TinyRest$Engine - Engine initialization starting...
20:41:30.203 [main] DEBUG TinyRest$Engine - Configuration validation mode: strict
20:41:30.203 [main] DEBUG TinyRest$Engine - Running strict configuration validation...
20:41:30.203 [main] DEBUG TinyRest$Engine - Strict validation passed
20:41:30.219 [main] DEBUG TinyRest$Engine -  Adding route: GET /api/users (list resources)
20:41:30.222 [main] DEBUG TinyRest$Engine -  Adding route: POST /api/users (create resource, auth required)
20:41:30.465 [pool-2-thread-1] DEBUG TinyRest$Engine - Request headers: {Connection=Upgrade, Host=localhost:62609, User-agent=Java-http-client/23}
20:41:30.465 [pool-2-thread-1] DEBUG TinyRest$Engine - Applying artificial latency: 50ms
20:41:30.527 [pool-2-thread-1] DEBUG TinyRest$Engine - Searching for matching route among 8 routes for GET /health
20:41:30.529 [pool-2-thread-1] DEBUG TinyRest$Engine - Route matched: GET /health -> handler (mutates=false)
20:41:30.530 [pool-2-thread-1] DEBUG TinyRest$Engine - Executing handler for GET /health
20:41:30.534 [pool-2-thread-1] DEBUG TinyRest$Engine - Handler completed for GET /health -> 200 (71ms)
20:41:30.700 [pool-2-thread-3] DEBUG TinyRest$Engine - Checking authorization for mutating operation POST /api/users
20:41:30.700 [pool-2-thread-3] DEBUG TinyRest$Engine - Authorization successful for POST /api/users
```

### INFO Level (Production)
Shows key events and successful operations:

```
20:33:33.129 [main] INFO  dev.mars.tinyrest.TinyRest$Engine - Engine configuration: templating=true, hotReload=true, schemaValidation=strict, recordReplay=off
20:33:33.144 [main] INFO  dev.mars.tinyrest.TinyRest$Engine - Initialized resource 'users' with 2 seed records (idField='id')
20:33:33.144 [main] INFO  dev.mars.tinyrest.TinyRest$Engine - Creating CRUD routes for resource 'users' at base path '/api/users'
20:33:33.152 [main] INFO  dev.mars.tinyrest.TinyRest$Engine - Route initialization complete: 8 total routes (5 CRUD + 3 static)
20:33:33.157 [main] INFO  dev.mars.tinyrest.TinyRest - HTTP server started successfully on port 62238
20:33:33.157 [main] INFO  dev.mars.tinyrest.hotreload - Starting file watcher for configuration hot reload
20:33:33.161 [tinyrest-hot-reload] INFO  dev.mars.tinyrest.hotreload - File watcher active - monitoring directory: src\test\resources
20:33:33.379 [pool-2-thread-1] INFO  dev.mars.tinyrest.http - → GET /health 
20:33:33.443 [pool-2-thread-1] INFO  dev.mars.tinyrest.http - ← 200 GET /health (65ms)
```

### WARN Level (Issues & Security)
Shows authentication failures and configuration issues:

```
20:33:33.668 [pool-2-thread-4] WARN  dev.mars.tinyrest.TinyRest$Engine - Authorization failed for POST /api/users - missing or invalid bearer token
20:33:33.668 [pool-2-thread-4] WARN  dev.mars.tinyrest.TinyRest$Engine - Unauthorized access attempt for POST /api/users: Missing/invalid bearer token
20:33:33.668 [pool-2-thread-4] WARN  dev.mars.tinyrest.http - ← 401 POST /api/users (54ms) - Missing/invalid bearer token
20:33:33.791 [pool-2-thread-6] WARN  dev.mars.tinyrest.TinyRest$Engine - No matching route found for GET /nonexistent after checking 8 routes
20:33:33.791 [pool-2-thread-6] WARN  dev.mars.tinyrest.http - ← 404 GET /nonexistent (59ms) - No matching route
```

### ERROR Level (Critical Issues)
Shows serious problems and stack traces:

```
20:32:29.969 [pool-2-thread-1] ERROR dev.mars.tinyrest.recorder - Recording failed for GET /health: null
java.lang.NullPointerException: null
    at java.base/java.util.Objects.requireNonNull(Objects.java:220)
    at java.base/java.util.ImmutableCollections$MapN.<init>(ImmutableCollections.java:1195)
    at dev.mars.tinyrest.TinyRest$ReplayItem$CapturedRequest.toMap(TinyRest.java:1230)
    ...
```

## Specialized Loggers

### HTTP Request Logger (`dev.mars.tinyrest.http`)
Clean, structured HTTP traffic logs:

```
20:33:33.379 [pool-2-thread-1] INFO  dev.mars.tinyrest.http - → GET /health 
20:33:33.443 [pool-2-thread-1] INFO  dev.mars.tinyrest.http - ← 200 GET /health (65ms)
20:33:33.482 [pool-2-thread-2] INFO  dev.mars.tinyrest.http - → GET /api/users ?limit=10&offset=0
20:33:33.546 [pool-2-thread-2] INFO  dev.mars.tinyrest.http - ← 200 GET /api/users (65ms)
20:33:33.550 [pool-2-thread-3] INFO  dev.mars.tinyrest.http - → POST /api/users 
20:33:33.611 [pool-2-thread-3] INFO  dev.mars.tinyrest.http - ← 201 POST /api/users (62ms)
20:33:33.614 [pool-2-thread-4] INFO  dev.mars.tinyrest.http - → POST /api/users 
20:33:33.668 [pool-2-thread-4] WARN  dev.mars.tinyrest.http - ← 401 POST /api/users (54ms) - Missing/invalid bearer token
```

### Hot Reload Logger (`dev.mars.tinyrest.hotreload`)
Configuration change monitoring:

```
20:33:33.157 [main] INFO  dev.mars.tinyrest.hotreload - Starting file watcher for configuration hot reload: src\test\resources\tinyrest-trace.yaml
20:33:33.161 [tinyrest-hot-reload] INFO  dev.mars.tinyrest.hotreload -  File watcher active - monitoring directory: src\test\resources
```

### Recorder Logger (`dev.mars.tinyrest.recorder`)
Record/replay functionality:

```
20:32:29.653 [main] INFO  dev.mars.tinyrest.recorder - Recording mode enabled - capturing requests to: target/test-recordings.jsonl
20:32:29.969 [pool-2-thread-1] ERROR dev.mars.tinyrest.recorder - Recording failed for GET /health: null
```

## Key Logging Features Demonstrated

### **Visual Indicators**
- Color-coded log levels (INFO=blue, WARN=yellow, ERROR=red)
- Consistent formatting with timestamps and thread names

### **Performance Monitoring**
- Request duration tracking: `← 200 GET /health (65ms)`
- Artificial latency logging: `Applying artificial latency: 50ms`
- Route matching performance insights

### **Security Logging**
- Authentication attempts: `Checking authorization for mutating operation`
- Auth failures: `Authorization failed - missing or invalid bearer token`
- Unauthorized access attempts with full context

### **Route Management**
- Route creation: `Adding route: GET /api/users (list resources)`
- Route matching: `Testing route pattern ^/\Qapi\E/\Qusers\E$ against path /health`
- Route summaries with regex patterns and mutation flags

### **Data Management**
- Store initialization: ` Initializing data stores...`
- Seed data loading: `Seeded record with ID '1' in resource 'users'`
- CRUD operations: `Generated new ID`, `Created new resource`

### **File Operations**
- Configuration loading: `Loading configuration from file: src/test/resources/tinyrest-trace.yaml`
- File format detection: `Configuration file format detected: YAML`
- Hot reload monitoring: `File watcher active - monitoring directory`

## Log File Organization

### Main Application Log (`logs/tinyrest.log`)
- Server lifecycle events
- Configuration parsing and validation
- Route and store initialization
- Error details with stack traces

### HTTP Traffic Log (`logs/tinyrest-http.log`)
- Clean request/response format
- Performance timing
- Status codes and error messages
- Separate from application logs for easy analysis

## Benefits of This Logging System

1. **Debugging**: TRACE level shows every internal decision
2. **Monitoring**: Performance metrics and request patterns
3. **Security**: Detailed auth failure logging
4. **Operations**: Clear startup/shutdown and error information
5. **Analytics**: Structured logs perfect for log aggregation systems
6. **Readability**: Emojis and colors make logs easy to scan
7. **Development**: Different log levels for different environments

The logging system provides complete observability into TinyRest's operation while maintaining excellent performance and readability!

