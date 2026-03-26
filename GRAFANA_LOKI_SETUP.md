# FinFlow — Grafana + Loki Centralized Logging Setup

## Architecture Overview

```
[All Microservices]
  ↓  (HTTP push via loki4j appender)
[Loki :3100]  ←→  [Grafana :3000]
```

Each service ships structured JSON logs **directly to Loki** over HTTP using the `loki4j-logback-appender`. No agent, no file tailing, no sidecar.

---

## Quick Start

### Step 1 — Start Loki & Grafana

```powershell
cd d:\FinFlow\docker-compose
docker compose up -d
```

Wait about 30 seconds for both containers to become healthy.

**Verify:**
```powershell
# Loki ready
curl http://localhost:3100/ready
# → ready

# Grafana health
curl http://localhost:3000/api/health
```

### Step 2 — Reload Maven in Your IDE

After pulling the new `loki-logback-appender` dependency, reload Maven in each service:
- **IntelliJ IDEA**: Right-click `pom.xml` → Maven → Reload Project
- **Eclipse/STS**: Right-click project → Maven → Update Project
- **Command line**: `mvn dependency:resolve` inside each service folder

### Step 3 — Start Your Microservices

Start services in the normal order (Config Server → Eureka → then the rest). Logs will automatically start flowing to Loki.

### Step 4 — Open Grafana

Navigate to: **http://localhost:3000**

| Credential | Value |
|---|---|
| Username | `admin` |
| Password | `admin` |

The **"FinFlow Microservices Logs"** dashboard is pre-loaded — find it under **Dashboards → Browse**.

---

## Dashboard Panels

| Panel | Description |
|---|---|
| **ERROR Logs (1h)** | Count of error-level logs in the last hour |
| **WARN Logs (1h)** | Count of warning-level logs in the last hour |
| **Total Logs (1h)** | Total log volume across all services |
| **Log Volume by Service** | Time-series graph of log rate per service |
| **All Service Logs** | Filterable log viewer (by service + log level) |
| **ERROR & WARN Logs** | Pre-filtered view of problems only |

### Dashboard Dropdown Filters
- **Service**: Select one or multiple services (or All)
- **Log Level**: Filter by ERROR, WARN, INFO, DEBUG (or All)

---

## Sample LogQL Queries (Grafana Explore)

> Go to **Explore → Select "Loki" datasource** to run these.

### All logs from a specific service
```logql
{service_name="auth-service"}
```

### All ERROR logs across all services
```logql
{log_level="ERROR"}
```

### Errors in the last 15 minutes
```logql
{log_level="ERROR"} | json | __error__=""
```

### Search for a specific message pattern
```logql
{service_name=~".+"} |= "NullPointerException"
```

### Trace a request across services by traceId
```logql
{service_name=~".+"} | json | traceId="<paste-your-traceId-here>"
```

### Log rate per service (for graphing)
```logql
sum by (service_name) (rate({service_name=~".+"}[5m]))
```

### Count errors per service in the last 1 hour
```logql
sum by (service_name) (count_over_time({log_level="ERROR"}[1h]))
```

---

## Log Structure

Each log entry sent to Loki is structured JSON:

```json
{
  "timestamp": "2026-03-25T21:30:00.000+0530",
  "level": "INFO",
  "service": "auth-service",
  "traceId": "a1b2c3d4e5f60000",
  "spanId": "f60000a1b2c3d4e5",
  "thread": "http-nio-8081-exec-1",
  "logger": "com.finflow.auth.service.AuthService",
  "message": "User authenticated successfully",
  "exception": ""
}
```

### Loki Labels (stream selectors)

| Label | Values | Purpose |
|---|---|---|
| `service_name` | e.g. `auth-service`, `api-gateway` | Filter by service |
| `log_level` | `INFO`, `WARN`, `ERROR`, `DEBUG` | Filter by severity |
| `host` | hostname of the machine | Multi-instance support |

> **Keep label cardinality low.** Never use request IDs or user IDs as labels — put them in the log message body instead.

---

## Customizing Loki URL (Optional)

By default every service pushes to `http://localhost:3100`. To override (e.g. different host), add this to the service's `application.yml` or `application-<profile>.yml`:

```yaml
logging:
  loki:
    url: http://your-loki-host:3100/loki/api/v1/push
```

The `logback-spring.xml` reads this via `${LOKI_URL}` — no code change required.

---

## Safety Notes

| Concern | Resolution |
|---|---|
| **Loki is down** | The async appender drops logs silently — console output is unaffected, zero app impact |
| **Loki is slow** | `neverBlock=true` ensures request threads are never blocked by logging |
| **Existing tests** | No changes to any Java class — all existing unit tests pass unchanged |
| **Business logic** | Zero changes to controllers, services, entities, or APIs |

---

## Log Retention

Loki is configured with **7-day retention**. To change it:

```yaml
# docker-compose/loki-config.yml
limits_config:
  retention_period: 336h  # 14 days
```

Then restart Loki: `docker compose restart loki`

---

## Stopping the Stack

```powershell
cd d:\FinFlow\docker-compose
docker compose down         # stop but keep volumes (logs preserved)
docker compose down -v      # stop AND delete volumes (logs wiped)
```

---

## Best Practices for Log Structuring

1. **Use SLF4J placeholders** — `log.info("User {} logged in", userId)` not string concatenation
2. **Log at INFO for business events** — authentication, state transitions, RabbitMQ message receipt
3. **Log at WARN for recoverable issues** — circuit breaker open, retry triggered
4. **Log at ERROR with full exception** — use `log.error("message", exception)` not `log.error(exception.getMessage())`
5. **Include context** — always include entity IDs (application ID, user ID) in log messages
6. **Never log sensitive data** — never log passwords, tokens, or PII

### Example: Good Logging Pattern
```java
@Slf4j
public class ApplicationService {

    public LoanApplication createApplication(LoanApplicationRequest request) {
        log.info("Creating loan application for userId={}, amount={}", 
                 request.getUserId(), request.getAmount());
        // ...
        log.info("Loan application created: applicationId={}, status={}",
                 application.getId(), application.getStatus());
        return application;
    }

    public void processApplication(Long id) {
        try {
            // ...
        } catch (ApplicationNotFoundException ex) {
            log.error("Application not found: applicationId={}", id, ex);
            throw ex;
        }
    }
}
```
