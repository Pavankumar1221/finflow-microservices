# FinFlow — Loan Management Microservices
### Complete Setup Guide for Eclipse / Spring Tool Suite (STS)

---

## 📋 Table of Contents
1. [Prerequisites — Install Everything First](#prerequisites)
2. [Step 1 — MySQL Setup](#step-1--mysql-setup)
3. [Step 2 — RabbitMQ Setup](#step-2--rabbitmq-setup)
4. [Step 3 — Import Project into Eclipse / STS](#step-3--import-project-into-eclipse--sts)
5. [Step 4 — Fill in Your Personal Details](#step-4--fill-in-your-personal-details)
6. [Step 5 — Start Services in Order](#step-5--start-services-in-order)
7. [Step 6 — Verify Everything is Running](#step-6--verify-everything-is-running)
8. [Step 7 — Test APIs](#step-7--test-apis)
9. [Quick Reference — Ports & URLs](#quick-reference--ports--urls)

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
GRANT ALL PRIVILEGES ON finflow_auth.*        TO 'pavan'@'localhost';
GRANT ALL PRIVILEGES ON finflow_application.* TO 'pavan'@'localhost';
GRANT ALL PRIVILEGES ON finflow_document.*    TO 'pavan'@'localhost';
GRANT ALL PRIVILEGES ON finflow_admin.*       TO 'pavan'@'localhost';

FLUSH PRIVILEGES;
```

> If you ever get **"Access denied"**, run this to reset your password:
> ```sql
> ALTER USER 'pavan'@'localhost' IDENTIFIED BY 'Pavan@2004';
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
  secret: finflow_super_secret_key_256_bits_long_for_HS256_2026
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
