# TinyRest Logging Guide

TinyRest now includes comprehensive logging using SLF4J with Logback, providing structured, configurable logging for better observability and debugging.

## Features

### 🎨 **Colored Console Output**
- Color-coded log levels (INFO=blue, WARN=yellow, ERROR=red)
- Structured format with timestamps, thread names, and logger names
- Easy-to-read HTTP request/response logs

### 📁 **File Logging**
- Separate log files for different concerns
- Automatic log rotation by date and size
- Configurable retention policies

### 🔍 **HTTP Request Logging**
- Dedicated HTTP traffic log (`tinyrest-http.log`)
- Request/response timing information
- Authentication failures and errors
- Clean format: `→ GET /api/users` and `← 200 GET /api/users (5ms)`

### ⚙️ **Configurable Logging**
- YAML-based logging configuration
- Multiple log levels (TRACE, DEBUG, INFO, WARN, ERROR)
- Enable/disable specific logging features

## Configuration

Add logging configuration to your `tinyrest.yaml`:

```yaml
logging:
  level: INFO             # TRACE, DEBUG, INFO, WARN, ERROR
  httpRequests: true      # log HTTP requests/responses
  enableFileLogging: true # write logs to files
  logDirectory: logs      # directory for log files
```

## Log Files

TinyRest creates several log files in the `logs/` directory:

- **`tinyrest.log`** - Main application logs (startup, configuration, errors)
- **`tinyrest-http.log`** - HTTP request/response logs with timing
- **`tinyrest-YYYY-MM-DD.log`** - Daily rotated archives

## Log Levels

### INFO (Default)
- Server startup/shutdown
- Resource and route initialization
- HTTP requests with response codes and timing
- Hot reload events

### DEBUG
- Detailed configuration parsing
- Route matching details
- Template rendering steps
- Internal state changes

### WARN
- Authentication failures
- Configuration validation issues (lenient mode)
- Missing files or resources

### ERROR
- Server startup failures
- Internal server errors (500s)
- Configuration validation failures (strict mode)
- File I/O errors

## Example Output

### Console Output
```
20:21:46.442 [main] INFO  dev.mars.tinyrest.TinyRest$Engine - Initialized resource 'users' with 2 seed records
20:21:46.447 [main] INFO  dev.mars.tinyrest.TinyRest$Engine - Initialized 6 routes
20:21:46.660 [pool-2-thread-1] INFO  dev.mars.tinyrest.http - → GET /health 
20:21:46.665 [pool-2-thread-1] INFO  dev.mars.tinyrest.http - ← 200 GET /health (5ms)
20:21:46.757 [pool-2-thread-4] WARN  dev.mars.tinyrest.http - ← 401 POST /api/users (1ms) - Missing/invalid bearer token
```

### HTTP Log File
```
20:21:46.660 → GET /health 
20:21:46.665 ← 200 GET /health (5ms)
20:21:46.718 → GET /api/users 
20:21:46.724 ← 200 GET /api/users (6ms)
20:21:46.757 → POST /api/users 
20:21:46.757 ← 401 POST /api/users (1ms) - Missing/invalid bearer token
```

## Specialized Loggers

TinyRest uses specialized loggers for different components:

- **`dev.mars.tinyrest.http`** - HTTP request/response logging
- **`dev.mars.tinyrest.hotreload`** - Configuration hot reload events
- **`dev.mars.tinyrest.recorder`** - Record/replay functionality
- **`dev.mars.tinyrest.TinyRest`** - Main application events

## Customizing Logging

You can customize logging by modifying `src/main/resources/logback.xml`:

### Change Log Format
```xml
<pattern>%d{HH:mm:ss} %-5level %logger{20} - %msg%n</pattern>
```

### Add Custom Appender
```xml
<appender name="CUSTOM" class="ch.qos.logback.core.FileAppender">
    <file>custom.log</file>
    <encoder>
        <pattern>%msg%n</pattern>
    </encoder>
</appender>
```

### Filter by Logger
```xml
<logger name="dev.mars.tinyrest.http" level="DEBUG" additivity="false">
    <appender-ref ref="HTTP_LOG"/>
</logger>
```

## Benefits

1. **Debugging** - Detailed logs help identify issues quickly
2. **Monitoring** - Track request patterns and performance
3. **Auditing** - HTTP logs provide audit trail
4. **Development** - See exactly what TinyRest is doing
5. **Production** - Structured logs for log aggregation systems

## Performance

- Asynchronous logging for minimal performance impact
- Configurable log levels to reduce verbosity in production
- Automatic log rotation prevents disk space issues
- Separate HTTP logs don't clutter main application logs
