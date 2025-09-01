# RestMonkey

<div align="center">
  <img src="docs/restmonkey-cigar.png" alt="RestMonkey" width="200">
</div>

[![Java](https://img.shields.io/badge/Java-23-orange.svg)](https://openjdk.java.net/projects/jdk/23/)
[![Maven](https://img.shields.io/badge/Maven-3.8+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**Version:** 1.0
**Date:** 2025-08-31
**Author:** Mark Andrew Ray-Smith Cityline Ltd

**REST API server with chaos engineering for testing and prototyping** — a lightweight Java server that creates realistic HTTP endpoints with configurable failure patterns. Define resources, endpoints, and chaos behaviours in YAML or using a fluent builder API.

No servlet container. No frameworks. Starts fast. Built for resilience testing. **Comprehensive chaos monkey engineering** with latency simulation, failure injection, and retry patterns.

---

## Why RestMonkey?

You need **real HTTP** behavior with **chaos engineering** for testing resilient applications. RestMonkey provides:

- **Self-contained**: Single Java file (`RestMonkey.java`) + Jackson - no external dependencies
- **Chaos Engineering**: Advanced failure simulation with configurable patterns
  - **Latency injection**: Fixed delays (`latencyMs: 500`) or random ranges (`randomLatencyMinMs/MaxMs`)
  - **Failure rates**: Configurable error percentages (`failureRate: 0.30` = 30% failures)
  - **Random status codes**: Weighted distributions (`randomStatuses: [200, 503, 504]`)
  - **Retry patterns**: Test circuit breakers (`successAfterRetries`, `successAfterSeconds`)
- **Fluent Builder API**: Type-safe programmatic configuration alternative to YAML
- **CRUD Resources**: Auto-generated REST endpoints with seed data and authentication
- **JUnit 5 Integration**: `@UseRestMonkey` annotation with dependency injection
- **Production-Ready Logging**: Structured SLF4J logging with performance metrics

If you want simple mocking, use WireMock. If you want **chaos engineering** and **resilience testing**, RestMonkey is perfect.

## 🚀 Latest Features (v1.0)

- **🐒 Advanced Chaos Engineering**: 5 focused YAML configurations demonstrating different failure patterns
- **⏱️ Latency Simulation**: Fixed delays, random ranges, and variable response times
- **💥 Failure Injection**: Configurable error rates with realistic failure distributions
- **🔄 Retry Pattern Testing**: Circuit breaker simulation with attempt-based and time-based recovery
- **🎯 Production Scenarios**: Realistic combinations for testing microservice resilience
- **📊 Enhanced Logging**: Chaos events logged with detailed timing and failure reasons

---

## Requirements

- **Java 17+** (recommend 21 LTS).
- Maven (if you use the provided `pom.xml`).

---

## Quick Start

```bash
# 1) Put RestMonkey.java in src/main/java
# 2) Add restmonkey.yml to src/test/resources (see example below)
# 3) Use the POM provided (Shade plugin builds a runnable JAR)

mvn -q -DskipTests package
java -jar target/restmonkey-1.0.0-SNAPSHOT.jar src/test/resources/restmonkey.yml
```

Test it:

```bash
# Replace <PORT> with the printed port (or set port: 8080 in YAML)
curl -s http://localhost:<PORT>/api/users | jq
curl -s http://localhost:<PORT>/health | jq
```

You'll see detailed logs with chaos engineering in action:
```
20:33:33.157 [main] INFO  RestMonkey - RestMonkey server started successfully on port 8080
20:33:33.379 [pool-2-thread-1] INFO  http - -> GET /health
20:33:33.443 [pool-2-thread-1] INFO  http - <- 200 GET /health (65ms)
```

---


## Fluent Builder API

RestMonkey provides a type-safe fluent builder API as an alternative to YAML configuration:

```java
// Simple server with chaos engineering
var server = RestMonkey.builder()
    .port(8080)
    .authToken("my-secret-token")
    .enableTemplating()

    // Add a resource with chaos patterns
    .resource("payments")
        .idField("id")
        .enableCrud()
        .latency(500)                    // 500ms delay
        .failureRate(0.20)              // 20% failure rate
        .seed("id", "p1", "amount", 99.99, "status", "pending")
        .done()

    // Add static endpoint with retry pattern
    .staticEndpoint()
        .get("/external-api")
        .response("status", "available")
        .successAfterRetries(2)         // Fail twice, then succeed
        .done()

    .start(); // Returns running RestMonkey instance

// Use in tests
String baseUrl = server.getBaseUrl();
// Make HTTP calls to test resilience...
```

**Key Builder Features:**
- **Type-safe configuration**: Compile-time validation of settings
- **Chaos engineering methods**: `.latency()`, `.failureRate()`, `.successAfterRetries()`
- **Fluent resource building**: Chain resource configuration with `.resource().done()`
- **Immediate startup**: `.start()` returns running server instance
- **Perfect for tests**: No external YAML files needed

---

## RESTMonkey.yml (starter)

`src/test/resources/RESTMonkey.yml`

```yaml
port: 0                   # 0 = auto-assign a free port; use 8080 for manual runs
authToken: test-token     # omit or "" to disable auth
artificialLatencyMs: 0
chaosFailRate: 0.0

logging:
  level: INFO             # TRACE, DEBUG, INFO, WARN, ERROR
  httpRequests: true      # log HTTP requests/responses
  enableFileLogging: true # write logs to files
  logDirectory: logs      # directory for log files

features:
  templating: true
  hotReload: false
  schemaValidation: strict
  recordReplay:
    mode: off             # off | record | replay
    file: target/RESTMonkey.recordings.jsonl
    replayOnMiss: fallback

resources:
  - name: users
    idField: id
    enableCrud: true
    seed:
      - id: u1
        name: Ada Lovelace
        email: ada@math.example
      - id: u2
        name: Alan Turing
        email: alan@logic.example

staticEndpoints:
  - method: GET
    path: /health
    status: 200
    response:
      status: ok
      time: "{{now}}"
```

### What this gives you

- CRUD at `/api/users`:
  - `GET /api/users?limit=&offset=`
  - `POST /api/users` (requires `Authorization: Bearer test-token`)
  - `GET /api/users/{id}`, `PUT /api/users/{id}`, `DELETE /api/users/{id}` (PUT/DELETE require auth)
- `GET /health` static endpoint with templating.

---

## JUnit 5 Integration

RESTMonkey ships with an embedded JUnit 5 extension to boot and tear down the server per test class and inject the base URL.

### Example test

`src/test/java/example/UsersApiTest.java`

```java
package example;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.net.URI;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(RESTMonkey.JUnitRESTMonkeyExtension.class)
@RESTMonkey.UseRESTMonkey(
  configPath = "src/test/resources/RESTMonkey.yml",
  port = 0 // auto-bind for parallel test safety
)
class UsersApiTest {

  HttpClient http = HttpClient.newHttpClient();

  @Test
  void listUsers(@RESTMonkey.RESTMonkeyBaseUrl URI baseUrl) throws Exception {
    var req = HttpRequest.newBuilder(baseUrl.resolve("/api/users")).GET().build();
    var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, resp.statusCode());
    assertTrue(resp.body().contains("Ada"));
  }

  @Test
  void createUserRequiresAuth(@RESTMonkey.RESTMonkeyBaseUrl URI baseUrl) throws Exception {
    var req = HttpRequest.newBuilder(baseUrl.resolve("/api/users"))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString("{"name":"Grace","email":"g@navy"}"))
      .build();
    var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
    assertEquals(401, resp.statusCode()); // auth enforced by YAML
  }

  @Test
  void createUserWithAuth(@RESTMonkey.RESTMonkeyBaseUrl URI baseUrl) throws Exception {
    var req = HttpRequest.newBuilder(baseUrl.resolve("/api/users"))
      .header("Content-Type", "application/json")
      .header("Authorization", "Bearer test-token")
      .POST(HttpRequest.BodyPublishers.ofString("{"name":"Grace Hopper","email":"g@navy"}"))
      .build();
    var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
    assertEquals(201, resp.statusCode());
    assertTrue(resp.headers().firstValue("Location").isPresent());
  }
}
```

**What the extension does**

- Starts RESTMonkey before all tests in the class.
- Binds to a random free port by default (`port=0`).
- Injects base URL into `@RESTMonkey.RESTMonkeyBaseUrl` params (`String` or `URI`).
- Exposes system props:
  - `RESTMonkey.baseUrl` (e.g., `http://localhost:12345`)
  - `RESTMonkey.port` (e.g., `12345`)
- Stops the server after all tests.

**Flip record/replay per suite (optional)**

```java
@RESTMonkey.UseRESTMonkey(
  configPath = "src/test/resources/RESTMonkey.yml",
  recordReplayMode = "record",                      // or "replay"
  recordReplayFile = "target/RESTMonkey.record.jsonl" // overrides YAML
)
class MySuite { ... }
```

---

## Features (toggle via YAML)

| Feature | YAML toggle / setting | Notes |
|---|---|---|
| CRUD per resource | `resources[].enableCrud: true` | Routes: `GET/POST /api/{name}`, `GET/PUT/DELETE /api/{name}/{id}` |
| Seed data | `resources[].seed` | Objects stored in memory; IDs auto-generated if missing |
| Auth on mutating ops | `authToken: <token>` | Requires `Authorization: Bearer <token>` on `POST/PUT/DELETE` |
| CORS | Always on | `Access-Control-Allow-Origin: *` |
| Latency injection | `artificialLatencyMs` | Integer ms; 0 disables |
| Chaos testing | `chaosFailRate` | `0.0..1.0`; randomly throw 500s |
| Static endpoints | `staticEndpoints[]` | Fixed responses or `echoRequest: true` |
| Templating | `features.templating: true` | Expand strings with `{{…}}` (see below) |
| Hot reload | `features.hotReload: true` | Watches the YAML file, reapplies config on change |
| Validation | `features.schemaValidation: strict|lenient` | Strict fails fast at startup |
| Record/Replay | `features.recordReplay.*` | JSONL file with captured responses; replay later |
| **Structured Logging** | `logging.*` | **SLF4J/Logback with TRACE/DEBUG/INFO/WARN/ERROR levels, performance timing, specialized loggers (HTTP/hotreload/recorder), colored console output, file rotation** |

## Comprehensive Logging

RESTMonkey includes enterprise-grade logging with SLF4J/Logback providing detailed observability:

### Log Levels & Features
- **TRACE**: Every internal operation (route matching, templating, data operations)
- **DEBUG**: Development insights (configuration parsing, route creation, auth checks)
- **INFO**: Production monitoring (server lifecycle, resource summaries, HTTP requests)
- **WARN**: Security issues (auth failures, missing routes, config problems)
- **ERROR**: Critical problems (server errors, validation failures, stack traces)

### Visual Logging
- **Color-coded console** output (INFO=blue, WARN=yellow, ERROR=red)
- **Performance timing** for all HTTP requests: `<- 200 GET /health (65ms)`

### Specialized Loggers
- **`dev.mars.RESTMonkey.http`** - Clean HTTP request/response logs
- **`dev.mars.RESTMonkey.hotreload`** - Configuration change monitoring
- **`dev.mars.RESTMonkey.recorder`** - Record/replay functionality
- **`dev.mars.RESTMonkey.RESTMonkey`** - Main application events

### Log Files
- **`logs/RESTMonkey.log`** - Complete application logs with automatic rotation
- **`logs/RESTMonkey-http.log`** - Dedicated HTTP traffic logs
- **Daily rotation** with size limits and configurable retention

### Example Output
```
20:33:33.129 [main] INFO  RESTMonkey$Engine - Engine configuration: templating=true, hotReload=true
20:33:33.144 [main] INFO  RESTMonkey$Engine - Initialized resource 'users' with 2 seed records
20:33:33.379 [pool-2-thread-1] INFO  http - -> GET /health
20:33:33.443 [pool-2-thread-1] INFO  http - <- 200 GET /health (65ms)
20:33:33.668 [pool-2-thread-4] WARN  http - <- 401 POST /api/users (54ms) - Missing/invalid bearer token
```

See [LOGGING.md](LOGGING.md) and [LOGGING_EXAMPLES.md](LOGGING_EXAMPLES.md) for complete documentation.

### Templating expressions (when enabled)

In any **string** value of a static response you can use:

- `{{now}}` — ISO-instant timestamp
- `{{uuid}}` — random UUID
- `{{path.<name>}}` — path param (from `/api/things/{id}`)
- `{{query.<name>}}` — query param (from `?foo=bar`)
- `{{body.<dot.path>}}` — extract from JSON request body
- `{{header.<Name>}}` — request header
- `{{random.int(a,b)}}` — random int in `[a,b]`

Example:

```yaml
staticEndpoints:
  - method: GET
    path: /api/users/{id}/profile
    status: 200
    response:
      id: "{{path.id}}"
      corrId: "{{uuid}}"
      echo: "hi {{query.name}}, body says: {{body.note}}"
```

### Record / Replay

```yaml
features:
  recordReplay:
    mode: record|replay|off
    file: target/RESTMonkey.recordings.jsonl
    replayOnMiss: fallback|error
```

- **record**: After a route produces a response, RESTMonkey appends a JSON object to the file.
- **replay**: On each request, RESTMonkey tries to match a recorded entry (method, path, query, opt headers/body). If `replayOnMiss: fallback`, it routes normally; if `error`, it returns **501** so you notice gaps.

> Matching knobs (`features.recordReplay.match`) are implemented in the `RESTMonkey.java` provided earlier. If you need body/header matching, add those keys in YAML accordingly.

---

## CI Tips

- Always bind to a **random port** in CI (`port: 0`) and let the **JUnit extension** inject the base URL.
- Keep `RESTMonkey.yml` minimal and deterministic. If you use templating randomness, constrain it (e.g., `random.int(1,3)`).
- Persist `target/RESTMonkey.recordings.jsonl` as an artifact if you rely on replay.

---

## Troubleshooting

- **401 on POST/PUT/DELETE** → you set `authToken`. Add:
  ```
  Authorization: Bearer <token>
  ```
- **“Port already in use”** → set `port: 0` and consume the injected base URL in tests; for manual runs, pick a fixed port.
- **YAML edits not applied** → set `features.hotReload: true` or restart RESTMonkey.
- **Replay misses** → set `replayOnMiss: fallback` while iterating; switch to `error` to lock it down.

---

## Design choices (and tradeoffs)

- Uses the JDK’s `com.sun.net.httpserver.HttpServer`. Not a servlet container — by design. Less magic, faster startup.
- Routing is simple regex over path segments. No annotations, no reflection.
- In-memory store is a `ConcurrentHashMap`. If you want persistence or relations, that’s a different product.
- Templating is intentionally minimal — no loops/ifs. It’s a test helper, not a view engine.

---

## FAQ

**Q: Can I host multiple independent resources?**  
A: Yes. Add more entries under `resources:`. Each gets CRUD under `/api/{name}`.

**Q: Can I add custom logic per route?**  
A: Yes. You own `RESTMonkey.java`. Add a route, call into your code, return a `Response`.

**Q: Does it support HTTPS?**  
A: Not out of the box. For tests, plain HTTP is enough. If you need TLS, wrap behind a test reverse proxy or extend the server.

**Q: Does it work with virtual threads?**  
A: The JDK server uses a thread-per-request model. For test loads, that’s fine. If you want virtual threads, swap the executor or move to a server that supports it — but you probably don’t need it for this use case.

---

## Project Layout (suggested)

```
your-project/
├─ pom.xml
├─ src/
│  ├─ main/java/dev/mars/restmonkey/RestMonkey.java
│  └─ test/
│     ├─ java/example/UsersApiTest.java
│     └─ resources/restmonkey.yml
```

---

## Installation (Maven)

Use this **copy-paste POM**. It pulls Jackson (JSON+YAML), SLF4J/Logback (logging), JUnit, and builds a **fat JAR** with Shade.

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>com.example</groupId>
  <artifactId>restmonkey</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <packaging>jar</packaging>
  <name>RestMonkey</name>
  <description>A lightweight HTTP server for mocking REST APIs with chaos engineering capabilities</description>

  <properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <junit.version>5.10.2</junit.version>
    <jackson.version>2.17.2</jackson.version>
  </properties>

  <dependencies>
    <!-- Runtime -->
    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
      <version>${jackson.version}</version>
    </dependency>
    <dependency>
      <groupId>com.fasterxml.jackson.dataformat</groupId>
      <artifactId>jackson-dataformat-yaml</artifactId>
      <version>${jackson.version}</version>
    </dependency>

    <!-- Logging -->
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-api</artifactId>
      <version>2.0.9</version>
    </dependency>
    <dependency>
      <groupId>ch.qos.logback</groupId>
      <artifactId>logback-classic</artifactId>
      <version>1.4.11</version>
    </dependency>

    <!-- Tests -->
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter-api</artifactId>
      <version>${junit.version}</version>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter-engine</artifactId>
      <version>${junit.version}</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <!-- JUnit 5 -->
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>3.2.5</version>
        <configuration>
          <useModulePath>false</useModulePath>
        </configuration>
      </plugin>

      <!-- Fat JAR with RestMonkey as Main-Class -->
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <version>3.5.3</version>
        <executions>
          <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
              <createDependencyReducedPom>true</createDependencyReducedPom>
              <filters>
                <filter>
                  <artifact>*:*</artifact>
                  <excludes>
                    <exclude>META-INF/*.SF</exclude>
                    <exclude>META-INF/*.DSA</exclude>
                    <exclude>META-INF/*.RSA</exclude>
                  </excludes>
                </filter>
              </filters>
              <transformers>
                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                  <mainClass>dev.mars.restmonkey.RestMonkey</mainClass>
                </transformer>
              </transformers>
              <shadedArtifactAttached>false</shadedArtifactAttached>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

**Build and Run:**

```bash
# Build the JAR
mvn -q -DskipTests package

# Run with configuration
java -jar target/restmonkey-1.0.0-SNAPSHOT.jar src/test/resources/restmonkey.yml
```

---

## License

Pick whatever fits your org. MIT is typical for utility code like this.

---

## Final word

RESTMonkey exists to **unblock testing**. It’s not a framework. If you’re fighting it, you’re solving the wrong problem — reach for a real service or WireMock. Otherwise, enjoy the speed and simplicity.

