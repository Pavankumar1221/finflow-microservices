# FinFlow — Loan Management Microservices
### Complete Setup Guide for Eclipse / Spring Tool Suite (STS)

---

## 📋 Table of Contents
1. [Project Overview](#project-overview)
2. [Architecture Overview](#architecture-overview)
3. [Prerequisites — Install Everything First](#prerequisites)
4. [Step 1 — MySQL Setup](#step-1--mysql-setup)
5. [Step 2 — RabbitMQ Setup](#step-2--rabbitmq-setup)
6. [Step 3 — Import Project into Eclipse / STS](#step-3--import-project-into-eclipse--sts)
7. [Step 4 — Fill in Your Personal Details](#step-4--fill-in-your-personal-details)
8. [Step 5 — Start Services in Order](#step-5--start-services-in-order)
9. [Step 6 — Verify Everything is Running](#step-6--verify-everything-is-running)
10. [Step 7 — Test APIs](#step-7--test-apis)
11. [Quick Reference — Ports & URLs](#quick-reference--ports--urls)
12. [RabbitMQ Event Flow](#rabbitmq-event-flow)
13. [Tech Stack Summary](#tech-stack-summary)
14. [🐳 Docker Containerization](#-docker-containerization)
15. [📊 Centralized Logging — Loki + Grafana](#-centralized-logging--loki--grafana)
16. [🔐 Security Architecture](#-security-architecture)
17. [📁 Project Structure](#-project-structure)
18. [Common Errors & Fixes](#common-errors--fixes)

---

## Prerequisites

Install all of these before starting:

| Tool | Version | Download |
|------|---------|----------|
| Java JDK | **17** | https://adoptium.net |
| Apache Maven | **3.8+** | https://maven.apache.org |
| MySQL | **8.0+** | https://dev.mysql.com/downloads |
| RabbitMQ | **3.12+** | https://rabbitmq.com/download.html |
| Eclipse / STS | Latest | https://spring.io/tools |

> **Check your Java version:** Open a terminal → type `java -version` → must show `17`
> **Check Maven:** type `mvn -version` → must show `3.8+`

---

## Step 1 — MySQL Setup

Open **MySQL Workbench** or any MySQL client and run this SQL:

```sql
-- Create all 4 databases
CREATE DATABASE IF NOT EXISTS finflow_auth;
CREATE DATABASE IF NOT EXISTS finflow_application;
CREATE DATABASE IF NOT EXISTS finflow_document;
CREATE DATABASE IF NOT EXISTS finflow_admin;

-- NOTE: User 'pavan' already exists on your MySQL.
-- Just grant permissions to all 4 databases:
GRANT ALL PRIVILEGES ON finflow_auth.*        TO 'finflow'@'localhost';
GRANT ALL PRIVILEGES ON finflow_application.* TO 'finflow'@'localhost';
GRANT ALL PRIVILEGES ON finflow_document.*    TO 'finflow'@'localhost';
GRANT ALL PRIVILEGES ON finflow_admin.*       TO 'finflow'@'localhost';

FLUSH PRIVILEGES;
```

> If you ever get **"Access denied"**, run this to reset your password:
> ```sql
> ALTER USER 'pavan'@'localhost' IDENTIFIED BY 'finflow';
> FLUSH PRIVILEGES;
> ```

---

## Step 2 — RabbitMQ Setup

**Option A — Using Docker (easiest):**
```bash
docker run -d --hostname rabbitmq --name rabbitmq \
  -p 5672:5672 -p 15672:15672 \
  rabbitmq:3-management
```
RabbitMQ Management UI → http://localhost:15672  
Login: `guest` / `guest`

**Option B — Install directly:**
- Download Erlang first: https://erlang.org/download
- Download RabbitMQ: https://rabbitmq.com/download.html
- Enable management plugin: `rabbitmq-plugins enable rabbitmq_management`
- Start the service

---

## Step 3 — Import Project into Eclipse / STS

### 3.1 Open Eclipse / STS

### 3.2 Import the Maven project
1. Go to **File → Import**
2. Select **Maven → Existing Maven Projects** → click **Next**
3. Click **Browse** → navigate to `d:\FinFlow` → click **OK**
4. You'll see a list of all 7 `pom.xml` files — check **all of them**
5. Click **Finish**

Eclipse will now download all dependencies (takes 2–5 minutes first time).

### 3.3 If you see dependency errors
- Right-click on the **parent project** (`FinFlow`) → **Maven → Update Project**
- Check **Force Update of Snapshots/Releases** → click **OK**

---

## Step 4 — Fill in Your Personal Details

> These are the **only places you need to change** if your setup differs.

### 4.1 MySQL Password

Your MySQL password is currently set to `root@1234` in every service config.  
**If your MySQL password is different**, update it in these files:

| File | Line to change |
|------|---------------|
| `auth-service/src/main/resources/application.yml` | `password: root@1234` |
| `application-service/src/main/resources/application.yml` | `password: root@1234` |
| `document-service/src/main/resources/application.yml` | `password: root@1234` |
| `admin-service/src/main/resources/application.yml` | `password: root@1234` |

Also update the **same 4 files** in your GitHub config repo  
(`d:\FinFlow\config-repo\auth-service.yml`, etc.)

### 4.2 GitHub Config Repo URL

In `config-server/src/main/resources/application.yml`:
```yaml
spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/Pavankumar1221/finflow-config-repo.git  ✅ Already set
          default-label: main   # Change to 'master' if your repo default branch is master
```

### 4.3 JWT Secret Key

The JWT secret is in `api-gateway/src/main/resources/application.yml` and `auth-service/src/main/resources/application.yml`:
```yaml
jwt:
  secret: *************
```
> This **must be identical** in both files. You can change it but keep both in sync.

### 4.4 Document Upload Path

In `document-service/src/main/resources/application.yml`:
```yaml
document:
  upload:
    path: C:/finflow/documents/    # Change this to any folder on your machine
```
**Create this folder** manually before starting the Document Service:
```
mkdir C:\finflow\documents
```

### 4.5 RabbitMQ credentials (if you changed them)

In each service's `application.yml` (and in `d:\FinFlow\config-repo\application.yml` on GitHub):
```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest      # Change if your RabbitMQ has a different user
    password: guest      # Change if your RabbitMQ has a different password
```

---

## Step 5 — Start Services in Order

> **Always start them in this exact order — each one depends on the previous.**

### In Eclipse / STS:
For each service, right-click the **main class** → **Run As → Spring Boot App**

| Order | Service | Main Class to Run | Port |
|-------|---------|-------------------|------|
| 1st | **Config Server** | `ConfigServerApplication.java` | 7001 |
| 2nd | **Eureka Server** | `EurekaServerApplication.java` | 7002 |
| 3rd | **API Gateway** | `ApiGatewayApplication.java` | 7003 |
| 4th | **Auth Service** | `AuthServiceApplication.java` | 7004 |
| 5th | **Application Service** | `ApplicationServiceApplication.java` | 7005 |
| 6th | **Document Service** | `DocumentServiceApplication.java` | 7006 |
| 7th | **Admin Service** | `AdminServiceApplication.java` | 7007 |

> ⏳ Wait for each service to print `Started ... in X seconds` in the console before starting the next one.

> 💡 **Tip for STS:** Use the **Boot Dashboard** (Window → Show View → Other → Spring → Spring Boot Dashboard) to see all services and start/stop them easily.

---

## Step 6 — Verify Everything is Running

Open your browser or Postman and check each URL:

### 6.1 Health checks
| Service | URL | Expected Response |
|---------|-----|-------------------|
| Config Server | http://localhost:7001/actuator/health | `{"status":"UP"}` |
| Eureka | http://localhost:7002 | Eureka dashboard (HTML page) |
| API Gateway | http://localhost:7003/actuator/health | `{"status":"UP"}` |
| Auth Service | http://localhost:7004/actuator/health | `{"status":"UP"}` |
| Application Svc | http://localhost:7005/actuator/health | `{"status":"UP"}` |
| Document Svc | http://localhost:7006/actuator/health | `{"status":"UP"}` |
| Admin Service | http://localhost:7007/actuator/health | `{"status":"UP"}` |

### 6.2 Verify Config Server is reading from GitHub
Open: http://localhost:7001/auth-service/default  
You should see the content of your `auth-service.yml` from GitHub.

### 6.3 Verify all services appear in Eureka
Open: http://localhost:7002  
You should see **6 services** listed: `API-GATEWAY`, `AUTH-SERVICE`, `APPLICATION-SERVICE`, `DOCUMENT-SERVICE`, `ADMIN-SERVICE`, `CONFIG-SERVER`

### 6.4 Swagger UI for each service
| Service | Swagger URL |
|---------|------------|
| Auth Service | http://localhost:7004/swagger-ui.html |
| Application Service | http://localhost:7005/swagger-ui.html |
| Document Service | http://localhost:7006/swagger-ui.html |
| Admin Service | http://localhost:7007/swagger-ui.html |

---

## Step 7 — Test APIs

Use **Postman** or **Swagger UI** to test. All requests go through the **API Gateway on port 7003**.

### 7.1 Register a User
```
POST http://localhost:7003/auth/register
Content-Type: application/json

{
  "fullName": "Pavan Kumar",
  "email": "pavan@example.com",
  "mobileNumber": "9876543210",
  "password": "SecurePass@123",
  "role": "ROLE_APPLICANT"
}
```

### 7.2 Login and get JWT Token
```
POST http://localhost:7003/auth/login
Content-Type: application/json

{
  "email": "pavan@example.com",
  "password": "SecurePass@123"
}
```
**Copy the `token` value** from the response — you need it for all other requests.

### 7.3 Create a Loan Application Draft
```
POST http://localhost:7003/applications/draft
Authorization: Bearer <paste-your-token-here>
```

### 7.4 Submit the Application
```
POST http://localhost:7003/applications/1/submit
Authorization: Bearer <paste-your-token-here>
```
This triggers a **RabbitMQ event** → Document Service listens and initializes the document checklist.

### 7.5 Upload a Document
```
POST http://localhost:7003/documents/upload
Authorization: Bearer <paste-your-token-here>
Content-Type: multipart/form-data

applicationId: 1
documentType: AADHAAR
file: (select any PDF or image file)
```

### 7.6 Register an Admin & Approve Application
```
# First register an admin user
POST http://localhost:7003/auth/register
{
  "fullName": "Admin User",
  "email": "admin@finflow.com",
  "mobileNumber": "9999999999",
  "password": "Admin@123",
  "role": "ROLE_ADMIN"
}

# Login as admin to get admin token
POST http://localhost:7003/auth/login
{ "email": "admin@finflow.com", "password": "Admin@123" }

# Approve the application (use admin token)
POST http://localhost:7003/admin/applications/1/approve
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "approvedAmount": 480000,
  "approvedTenureMonths": 36,
  "interestRate": 10.5,
  "decisionReason": "All documents verified, applicant is creditworthy"
}
```

---

## Common Errors & Fixes

| Error | Fix |
|-------|-----|
| `Connection refused` on port 7001 | Start Config Server first |
| `No instances available` in Feign | Eureka not running or service not registered yet — wait 30 seconds |
| `Unable to connect to RabbitMQ` | RabbitMQ service is not running — start it |
| `Access denied for user 'pavan'` | MySQL user not created — run the SQL in Step 1 |
| `Could not clone repository` in Config Server | Check your GitHub URL in `config-server/application.yml` |
| `401 Unauthorized` on API calls | Missing or expired JWT token — login again |
| `File not found` for document upload | Create the upload folder: `mkdir C:\finflow\documents` |

---

## Quick Reference — Ports & URLs

| Service | Port | Swagger | Health |
|---------|------|---------|--------|
| Config Server | 7001 | — | http://localhost:7001/actuator/health |
| Eureka Server | 7002 | — | http://localhost:7002 |
| API Gateway | 7003 | — | http://localhost:7003/actuator/health |
| Auth Service | 7004 | http://localhost:7004/swagger-ui.html | http://localhost:7004/actuator/health |
| Application Svc | 7005 | http://localhost:7005/swagger-ui.html | http://localhost:7005/actuator/health |
| Document Svc | 7006 | http://localhost:7006/swagger-ui.html | http://localhost:7006/actuator/health |
| Admin Service | 7007 | http://localhost:7007/swagger-ui.html | http://localhost:7007/actuator/health |

---

## RabbitMQ Event Flow

```
Applicant submits loan        →  application.submitted  →  Document Service notified
All documents verified        →  documents.verified     →  Admin Service notified
Admin approves/rejects        →  decision.made          →  (Notification / downstream)
```

---

## Tech Stack Summary

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.2, Spring Cloud 2023.0.1 |
| Service Discovery | Netflix Eureka |
| Config Management | Spring Cloud Config Server + GitHub |
| API Gateway | Spring Cloud Gateway + JWT |
| Inter-service calls | OpenFeign + Resilience4j |
| Messaging | RabbitMQ (AMQP) |
| Database | MySQL 8 + Spring Data JPA |
| Security | JWT (jjwt 0.11.5) |
| API Docs | SpringDoc OpenAPI 3 (Swagger UI) |
| Monitoring | Spring Boot Actuator + Micrometer |
| Build | Maven |
| Containerization | Docker + Docker Compose |
| Log Aggregation | Loki + Grafana (loki4j logback appender) |
| Code Quality | SonarQube + JaCoCo |
| Testing | JUnit 5 + Mockito |

---

## Project Overview

FinFlow is a **production-grade Loan Management System** built using a microservices architecture on Spring Boot 3.x and Spring Cloud. It models the full lifecycle of a loan application — from applicant registration, loan application drafting and submission, document upload and verification, to admin review and final approval or rejection.

### Key Capabilities

- **JWT-based authentication** with role separation (`ROLE_APPLICANT`, `ROLE_ADMIN`)
- **Event-driven workflow** via RabbitMQ — actions in one service automatically trigger reactions in others without tight coupling
- **Centralized configuration** pulled from a public GitHub repository at runtime via Spring Cloud Config Server
- **Service discovery** via Netflix Eureka — no hardcoded service URLs between microservices
- **Resilient inter-service communication** via OpenFeign + Resilience4j circuit breakers and retry policies
- **Centralized structured logging** pushed to Loki and visualized in Grafana dashboards
- **Full Docker containerization** — the entire 11-container stack launches with one command
- **Aggregated Swagger UI** served at the API Gateway giving a single-pane view of all service APIs

---

## Architecture Overview

### High-Level Request Flow

```
  Browser / Postman
        │
        ▼
  ┌─────────────────────────────────────────────────────────────────┐
  │                    API Gateway  :7003                           │
  │  JWT Validation → Route → Load Balance (Eureka lb://)          │
  └──────┬──────────────┬────────────────┬──────────────┬──────────┘
         │              │                │              │
         ▼              ▼                ▼              ▼
  ┌────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
  │auth-service│ │application-  │ │document-     │ │admin-service │
  │   :7004    │ │service :7005 │ │service :7006 │ │   :7007      │
  └────────────┘ └──────┬───────┘ └──────┬───────┘ └──────┬───────┘
        │               │                │                 │
        └───────────────┴────────────────┴─────────────────┘
                                │
               ┌────────────────┼─────────────────┐
               ▼                ▼                 ▼
         ┌──────────┐    ┌──────────┐      ┌──────────────┐
         │  MySQL   │    │ RabbitMQ │      │    Eureka    │
         │  :3306   │    │  :5672   │      │    :7002     │
         └──────────┘    └──────────┘      └──────────────┘
                                │
               ┌────────────────┴────────────────┐
               ▼                                 ▼
         ┌──────────┐                     ┌──────────┐
         │   Loki   │                     │ Grafana  │
         │  :3100   │─────────────────────│  :3000   │
         └──────────┘                     └──────────┘
```

### Service Responsibilities

| Service | Port | Responsibility |
|---|---|---|
| **config-server** | 7001 | Pulls YAML configs from GitHub and serves them to all other services at startup |
| **eureka-server** | 7002 | Service registry — all services register here; Feign clients resolve URLs through it |
| **api-gateway** | 7003 | Single entry point — validates JWT, routes requests, serves aggregated Swagger UI |
| **auth-service** | 7004 | User registration, login, JWT generation using jjwt. Owns `finflow_auth` DB |
| **application-service** | 7005 | Loan application CRUD, status lifecycle (DRAFT → SUBMITTED → VERIFIED → APPROVED/REJECTED). Owns `finflow_application` DB |
| **document-service** | 7006 | File upload, document metadata, document verification status. Owns `finflow_document` DB |
| **admin-service** | 7007 | Admin dashboard, loan decision (approve/reject), audit trail. Calls other services via Feign. Owns `finflow_admin` DB |

### Config Server — Git-backed Configuration

All service-specific configuration (datasource URLs, RabbitMQ, JWT secret, Feign timeouts) lives in a **public GitHub repository**: `Pavankumar1221/finflow-config-repo`. The Config Server clones this repo at startup and serves the relevant YAML file to each service on demand.

```
  config-repo (GitHub)
  ├── application.yml          ← shared config for ALL services
  ├── auth-service.yml         ← auth-service specific
  ├── application-service.yml
  ├── document-service.yml
  ├── admin-service.yml
  ├── api-gateway.yml
  └── eureka-server.yml
```

### RabbitMQ Event Flow (Detailed)

```
  1. Applicant submits loan
     application-service  ──► [application.exchange]  ──► documents.init.queue
                                                           └─► document-service creates document checklist

  2. All documents verified
     document-service     ──► [documents.exchange]    ──► documents.verified.queue
                                                           └─► application-service sets status = VERIFIED
                                                           └─► admin-service notified for review

  3. Admin makes decision
     admin-service        ──► [decision.exchange]     ──► decision.made.queue
                                                           └─► application-service sets APPROVED / REJECTED
```

### Database-per-Service Pattern

Each microservice owns exactly one MySQL database. No service directly queries another service's database — all cross-service data access is done via REST (Feign clients) or RabbitMQ events.

| Service | Database |
|---|---|
| auth-service | `finflow_auth` |
| application-service | `finflow_application` |
| document-service | `finflow_document` |
| admin-service | `finflow_admin` |

### Resilience Pattern

All Feign client calls between services are protected by **Resilience4j**:
- **Circuit Breaker** — opens after 50% failure rate, waits 10 s before retry
- **Retry** — 3 attempts with 2 s wait between each attempt
- Named instances: `applicationServiceCB`, `documentServiceCB`, `authServiceCB`

---

## 🐳 Docker Containerization

The entire FinFlow stack is fully containerized. All 11 containers start with a single command.

### Files Added

```
FinFlow/
├── config-server/Dockerfile
├── eureka-server/Dockerfile
├── api-gateway/Dockerfile
├── auth-service/Dockerfile
├── application-service/Dockerfile
├── document-service/Dockerfile
├── admin-service/Dockerfile
└── docker-compose/
    ├── docker-compose.yml       ← Full 11-container stack
    ├── .env                     ← Centralised environment variables
    ├── mysql-init/
    │   └── init.sql             ← Creates all 4 databases + user on first start
    ├── loki-config.yml
    ├── grafana-datasources.yml
    ├── grafana-dashboards.yml
    └── finflow-logs-dashboard.json
```

### Dockerfile Design (Multi-Stage Build)

Every service Dockerfile uses a **two-stage build** so no pre-built JARs are needed:

```dockerfile
# Stage 1 — Build (Maven + JDK)
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2 — Runtime (JRE only, ~200 MB smaller)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S finflow && adduser -S finflow -G finflow
COPY --from=builder /app/target/*.jar app.jar
RUN chown finflow:finflow app.jar
USER finflow
EXPOSE <port>
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+UseContainerSupport", "-jar", "app.jar"]
```

**Key design decisions:**
- Non-root user (`finflow`) for security
- `UseContainerSupport` and `MaxRAMPercentage=75.0` for proper JVM memory limits inside Docker
- `-Djava.security.egd=file:/dev/./urandom` for faster startup

### Container Stack

| Container | Image | Port(s) | Depends On |
|---|---|---|---|
| `finflow-mysql` | `mysql:8.0` | 3307→3306 | — |
| `finflow-rabbitmq` | `rabbitmq:3.13-management-alpine` | 5672, 15672 | — |
| `finflow-loki` | `grafana/loki:2.9.0` | 3100 | — |
| `finflow-grafana` | `grafana/grafana:11.0.0` | 3000 | loki |
| `finflow-config-server` | build | 7001 | — |
| `finflow-eureka-server` | build | 7002 | config-server |
| `finflow-api-gateway` | build | 7003 | eureka-server |
| `finflow-auth-service` | build | 7004 | mysql, rabbitmq, eureka-server |
| `finflow-application-service` | build | 7005 | mysql, rabbitmq, eureka-server |
| `finflow-document-service` | build | 7006 | mysql, rabbitmq, eureka-server |
| `finflow-admin-service` | build | 7007 | mysql, rabbitmq, eureka-server |

### How to Run with Docker

> **Prerequisites:** Docker Desktop installed and running.

```powershell
# Navigate to the docker-compose directory
cd d:\FinFlow\docker-compose

# Build all images and start the stack (first run ~10 min for Maven downloads)
docker compose up --build

# Run in detached mode (background)
docker compose up --build -d

# View logs
docker compose logs -f

# View logs for a specific service
docker compose logs -f auth-service

# Stop everything
docker compose down

# Stop and delete all data volumes (full reset)
docker compose down -v
```

### Environment Variable Override Strategy

The GitHub config repo contains `localhost` URLs (for local development). In Docker, `localhost` inside a container refers to the container itself — not MySQL or RabbitMQ. FinFlow solves this **without modifying any source code** by injecting environment variables in `docker-compose.yml` that Spring Boot picks up at the highest priority:

| Variable | Docker Value | Overrides Config Repo |
|---|---|---|
| `SPRING_CONFIG_IMPORT` | `configserver:http://config-server:7001` | `localhost:7001` |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | `http://eureka-server:7002/eureka/` | `localhost:7002` |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://mysql:3306/...` | `localhost:3306` |
| `SPRING_RABBITMQ_HOST` | `rabbitmq` | `localhost` |
| `LOGGING_LOKI_URL` | `http://loki:3100/loki/api/v1/push` | `localhost:3100` |
| `DOCUMENT_UPLOAD_PATH` | `/app/uploads/` | `C:/finflow/documents/` |
| `SPRING_CLOUD_CONFIG_OVERRIDE_NONE` | `true` | Forces env-vars to win |

### Docker Volumes

| Volume | Purpose |
|---|---|
| `mysql-data` | MySQL database files persist across restarts |
| `rabbitmq-data` | RabbitMQ queues and messages |
| `loki-data` | Loki log storage |
| `grafana-data` | Grafana dashboards and settings |
| `document-uploads` | Uploaded files from document-service |

### Startup Order & Health Checks

All Spring Boot services expose `/actuator/health`. Docker Compose `depends_on: condition: service_healthy` ensures:

```
mysql (healthy)
rabbitmq (healthy)
loki (healthy)
    ↓
grafana
config-server (healthy)
    ↓
eureka-server (healthy)
    ↓
api-gateway
auth-service        ┐
application-service ├─ all wait for: mysql + rabbitmq + eureka-server healthy
document-service    │
admin-service       ┘
```

### Validation After Docker Start

| Check | URL |
|---|---|
| Eureka — all 5 services registered | http://localhost:7002 |
| Aggregated Swagger UI (all APIs) | http://localhost:7003/swagger-ui.html |
| RabbitMQ Management | http://localhost:15672 (guest/guest) |
| Grafana Dashboards | http://localhost:3000 (admin/admin) |
| Config Server health | http://localhost:7001/actuator/health |

---

## 📊 Centralized Logging — Loki + Grafana

All 7 microservices ship structured JSON logs directly to **Loki** over HTTP using the `loki4j` logback appender. Grafana connects to Loki as a data source and provides real-time log exploration and pre-built dashboards.

### How It Works

```
  Spring Boot Service
       │
       ├── Console Appender  ──►  terminal / IDE output
       │
       └── Loki Async Appender
              │  (HTTP push, batched every 1 s)
              ▼
          Loki :3100
              │
              ▼
          Grafana :3000
          (LogQL queries, dashboards)
```

### Log Labels

Each log line is tagged with:
```
service_name=<spring.application.name>
log_level=INFO|DEBUG|WARN|ERROR
host=<container hostname>
```

### Log Format (JSON structured)

```json
{
  "timestamp": "2026-03-26T20:10:08.000+0530",
  "level": "INFO",
  "service": "auth-service",
  "traceId": "abc123",
  "spanId": "def456",
  "thread": "http-nio-7004-exec-1",
  "logger": "com.finflow.auth.service.AuthService",
  "message": "User registered: pavan@example.com",
  "exception": ""
}
```

### Grafana Access

1. Open http://localhost:3000
2. Login: `admin` / `admin`
3. Go to **Dashboards → FinFlow Logs Dashboard** (auto-provisioned)
4. Filter by service, log level, or search for specific log messages

### Useful LogQL Queries

```logql
# All logs from auth-service
{service_name="auth-service"}

# All ERROR logs across all services
{service_name=~".+"} | json | level="ERROR"

# Logs containing a specific message
{service_name="application-service"} |= "submitted"

# Logs for a specific trace ID
{service_name=~".+"} | json | traceId="abc123"
```

### Configuration

Loki URL is configured in each service via the `logging.loki.url` property (overridden to `http://loki:3100/loki/api/v1/push` in Docker). The `logback-spring.xml` in each service reads this property:

```xml
<springProperty name="LOKI_URL" source="logging.loki.url"
  defaultValue="http://localhost:3100/loki/api/v1/push"/>
```

This means the same logback config works both **locally** (pushes to `localhost:3100`) and **inside Docker** (pushes to `loki:3100`), with no config file changes.

---

## 🔐 Security Architecture

### JWT Authentication Flow

```
  1. Client  ──POST /auth/login──►  auth-service
                                        │
                                        ▼
                              Validates credentials
                              Generates JWT (HS256)
                              Returns: { "token": "eyJ..." }

  2. Client  ──GET /applications──►  api-gateway
             Authorization: Bearer eyJ...
                                        │
                                        ▼
                              AuthenticationFilter validates JWT
                              Extracts userId + roles
                              Forwards request to application-service
                              (with X-User-Id / X-User-Roles headers)

  3. application-service receives request
     Reads X-User-Id from header (no DB lookup needed)
     Returns response
```

### Public vs Secured Routes

| Route Pattern | Auth Required | Notes |
|---|---|---|
| `POST /auth/register` | ❌ Public | User self-registration |
| `POST /auth/login` | ❌ Public | JWT token issuance |
| `GET /products`, `GET /products/**` | ❌ Public | Loan product catalog |
| `GET/POST /applications/**` | ✅ JWT | Applicant routes |
| `GET/POST /documents/**` | ✅ JWT | Document upload |
| `GET/POST /admin/**` | ✅ JWT + ROLE_ADMIN | Admin operations |
| `/**/swagger-ui/**`, `/**/v3/api-docs/**` | ❌ Public | API documentation |

### JWT Secret Management

The JWT secret key `finflow_super_secret_key_256_bits_long_for_HS256_2026` is shared between:
- **auth-service** — signs the token
- **api-gateway** — verifies the token

In Docker, both get the secret from the `JWT_SECRET` environment variable defined in `.env`.

---

## 📁 Project Structure

```
FinFlow/
├── config-server/               # Spring Cloud Config Server
│   ├── src/main/resources/
│   │   ├── application.yml      # Points to GitHub config repo
│   │   └── logback-spring.xml
│   └── Dockerfile
│
├── eureka-server/               # Netflix Eureka Service Registry
│   ├── src/main/resources/
│   │   └── application.yml
│   └── Dockerfile
│
├── api-gateway/                 # Spring Cloud Gateway
│   ├── src/main/java/com/finflow/gateway/
│   │   └── filter/
│   │       └── AuthenticationFilter.java   # JWT validation
│   ├── src/main/resources/
│   │   └── application.yml      # Routes + JWT config
│   └── Dockerfile
│
├── auth-service/                # User auth + JWT issuance
│   ├── src/main/java/com/finflow/auth/
│   │   ├── controller/          # /auth/register, /auth/login
│   │   ├── service/             # AuthService, JwtService
│   │   ├── entity/              # User, Role
│   │   └── repository/
│   └── Dockerfile
│
├── application-service/         # Loan application lifecycle
│   ├── src/main/java/com/finflow/application/
│   │   ├── controller/          # /applications/**
│   │   ├── service/
│   │   ├── entity/              # Application, ApplicationStatusHistory
│   │   ├── listener/            # RabbitMQ consumers
│   │   └── messaging/           # Publishers
│   └── Dockerfile
│
├── document-service/            # Document upload + verification
│   ├── src/main/java/com/finflow/document/
│   │   ├── controller/          # /documents/**
│   │   ├── service/
│   │   ├── entity/              # Document
│   │   └── client/              # Feign client → application-service
│   └── Dockerfile
│
├── admin-service/               # Admin review + decisions
│   ├── src/main/java/com/finflow/admin/
│   │   ├── controller/          # /admin/**
│   │   ├── service/             # AdminService, ReportService
│   │   ├── entity/              # AuditLog, LoanDecision
│   │   └── client/              # Feign → auth, application, document services
│   └── Dockerfile
│
├── config-repo/                 # Local copy of GitHub config repo
│   ├── application.yml          # Shared: RabbitMQ, Eureka, Actuator
│   ├── auth-service.yml
│   ├── application-service.yml
│   ├── document-service.yml
│   ├── admin-service.yml
│   ├── api-gateway.yml
│   └── eureka-server.yml
│
└── docker-compose/              # Docker stack
    ├── docker-compose.yml
    ├── .env
    ├── mysql-init/
    │   └── init.sql
    ├── loki-config.yml
    ├── grafana-datasources.yml
    ├── grafana-dashboards.yml
    └── finflow-logs-dashboard.json
```

---

## Common Errors & Fixes

| Error | Context | Fix |
|---|---|---|
| `Connection refused` on port 7001 | Local dev | Start Config Server first |
| `No instances available` in Feign | Local dev | Eureka not running — wait 30 s |
| `Unable to connect to RabbitMQ` | Local dev | Start RabbitMQ service |
| `Access denied for user 'pavan'` | Local dev | Run SQL in Step 1 |
| `Could not clone repository` | Config Server | Check GitHub URL in `application.yml` |
| `401 Unauthorized` on API calls | Any | Expired JWT — login again |
| `File not found` on document upload | Local dev | `mkdir C:\finflow\documents` |
| `Public Key Retrieval is not allowed` | Docker | Add `allowPublicKeyRetrieval=true` to datasource URL |
| Service uses `localhost` despite env var | Docker | Add `SPRING_CLOUD_CONFIG_OVERRIDE_NONE: "true"` to service |
| Services not in Eureka after Docker start | Docker | Wait 2–3 min for full startup cascade; check `docker compose logs -f` |
| `OOMKilled` in container | Docker | Increase Docker Desktop memory limit (recommend 8 GB+) |

