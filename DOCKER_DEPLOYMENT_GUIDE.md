# FinFlow Docker Deployment Guide

This guide is for deploying the current working FinFlow backend from the local repository with Docker Compose.

## What Was Updated

The Docker setup was adjusted to match the current codebase:

- `config-server` now reads from the local [`config-repo`](D:/FinFlow/config-repo) mounted into the container
- `admin-service` and `document-service` now receive the mail environment variables needed for email notifications
- RabbitMQ sample credentials were changed from `guest/guest` to a real container-safe user
- the current stack remains compatible with:
  - RabbitMQ messaging
  - OpenFeign service-to-service calls via Eureka
  - email notifications
  - logout blacklist flow
  - document uploads via Docker volume
  - Loki + Grafana + Zipkin observability

## Files Involved

- Compose file: [docker-compose.yml](D:/FinFlow/docker-compose/docker-compose.yml)
- Compose env file: [.env](D:/FinFlow/docker-compose/.env)
- Local config repo:
  - [application.yml](D:/FinFlow/config-repo/application.yml)
  - [api-gateway.yml](D:/FinFlow/config-repo/api-gateway.yml)
  - [auth-service.yml](D:/FinFlow/config-repo/auth-service.yml)
  - [application-service.yml](D:/FinFlow/config-repo/application-service.yml)
  - [document-service.yml](D:/FinFlow/config-repo/document-service.yml)
  - [admin-service.yml](D:/FinFlow/config-repo/admin-service.yml)

## Important Deployment Behavior

### 1. Config Server

The stack is now set to use the local `config-repo` from your machine instead of pulling config from GitHub.

This is important because your latest working config changes are local and deployment should reflect the current codebase exactly.

### 2. RabbitMQ

Do not use `guest/guest` for inter-container access.

The compose env now uses:

- `RABBITMQ_USER=finflow`
- `RABBITMQ_PASSWORD=FinFlowRabbit@2026`

All services already inherit those values through container environment variables.

### 3. Email Notifications

Email notifications will work in Docker only if these are filled in inside [`docker-compose/.env`](D:/FinFlow/docker-compose/.env):

- `FINFLOW_MAIL_ENABLED=true`
- `MAIL_HOST=smtp.gmail.com`
- `MAIL_PORT=587`
- `MAIL_USERNAME=your-email@gmail.com`
- `MAIL_PASSWORD=your-app-password`
- `FINFLOW_MAIL_FROM=your-email@gmail.com`

These are now passed into:

- `admin-service`
- `document-service`

### 4. Document Uploads

Uploaded documents are stored in the Docker volume mounted to:

- `/app/uploads`

This is already wired in `document-service`.

## Before You Start

Update [`docker-compose/.env`](D:/FinFlow/docker-compose/.env) with your real values.

At minimum review:

- `MYSQL_ROOT_PASSWORD`
- `MYSQL_APP_USER`
- `MYSQL_APP_PASSWORD`
- `RABBITMQ_USER`
- `RABBITMQ_PASSWORD`
- `JWT_SECRET`
- `JWT_EXPIRATION`
- `FINFLOW_MAIL_ENABLED`
- `MAIL_USERNAME`
- `MAIL_PASSWORD`
- `FINFLOW_MAIL_FROM`

## Start The Stack

Run from:

```powershell
D:\FinFlow\docker-compose
```

Use:

```powershell
docker-compose up --build
```

To run in detached mode:

```powershell
docker-compose up --build -d
```

## Expected Startup Order

1. `mysql`, `rabbitmq`, `zipkin`, `loki`
2. `grafana`
3. `config-server`
4. `eureka-server`
5. `api-gateway`
6. `auth-service`
7. `application-service`
8. `document-service`
9. `admin-service`

## URLs After Deployment

- Gateway Swagger: [http://localhost:7003/swagger-ui.html](http://localhost:7003/swagger-ui.html)
- Eureka: [http://localhost:7002](http://localhost:7002)
- RabbitMQ UI: [http://localhost:15672](http://localhost:15672)
- Zipkin: [http://localhost:9411](http://localhost:9411)
- Grafana: [http://localhost:3000](http://localhost:3000)

## Post-Deployment Verification Checklist

### Auth and Gateway

- login from Swagger
- paste JWT once in Swagger `Authorize`
- call secured endpoint successfully
- call `POST /auth/logout`
- retry the same secured endpoint with the same token
- confirm it returns `401`

### RabbitMQ Messaging

- create application draft
- save sections
- submit application
- upload all 3 documents
- verify all 3 documents
- approve or reject the application

Expected:

- application status should move through async event flow
- RabbitMQ UI should show exchanges and queue activity

### OpenFeign

Expected internal Feign flows:

- `document-service -> application-service` ownership validation
- `document-service -> auth-service` email recipient lookup
- `admin-service -> application-service`
- `admin-service -> document-service`
- `admin-service -> auth-service`
- `api-gateway -> auth-service` logout blacklist check

If these are working, service-to-service calls are healthy.

### Email Notifications

Check both:

- document verification or rejection email
- loan approval or rejection email

If mail does not send, first verify:

- `FINFLOW_MAIL_ENABLED=true`
- `MAIL_USERNAME` is the email address
- `MAIL_PASSWORD` is the app password
- `FINFLOW_MAIL_FROM` is set

### Caching

Check repeated `GET` requests:

- first call should hit DB
- second identical call should be quieter
- after update event or write operation, cache should be evicted and fresh data should return

### Document Storage

- upload a document
- view/download it
- restart the stack without deleting volumes
- verify uploaded files still exist

## Helpful Commands

Show running containers:

```powershell
docker-compose ps
```

View logs:

```powershell
docker-compose logs -f
```

View logs for one service:

```powershell
docker-compose logs -f admin-service
```

Stop containers:

```powershell
docker-compose down
```

Stop containers and remove volumes:

```powershell
docker-compose down -v
```

## Notes

- `docker compose` on your machine currently appears unavailable, but `docker-compose` works
- `docker-compose config` resolved successfully after the updates, so the compose YAML is syntactically valid
- there is a local Docker warning about access to `C:\Users\PAVAN\.docker\config.json`, but the compose config still resolved

## Recommended First Deployment Mode

For your current project, the best first deployment is:

- local machine
- local Docker Compose
- local config repo mounted into config-server
- real RabbitMQ
- real MySQL
- real Gmail SMTP app password

That gives you the closest result to your tested app without adding cloud complexity yet.
