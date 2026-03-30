# FinFlow API Refactor README

Last updated: 2026-03-29

This document captures the API and service-layer refactor work completed across FinFlow so far. It is intended to be the single source of truth for:

- what was changed
- what was removed
- what was added
- which endpoints are currently active
- how authentication and authorization now work
- the latest fixes applied after the main refactor

## 1. Core Security Model

FinFlow now follows a direct-to-gateway zero-trust model.

- External clients call the API Gateway only.
- The API Gateway validates the JWT.
- The API Gateway strips untrusted client identity headers.
- The API Gateway injects trusted headers for downstream services:
  - `X-User-Id`
  - `X-User-Roles`
  - `X-User-Email`
- Internal microservices must not validate JWTs themselves for normal business endpoints.
- Authorization inside downstream services is based on trusted identity propagated by the gateway.

### Ownership Rules

- `ROLE_APPLICANT`
  - Can only access or modify their own resources.
  - Ownership is enforced by matching `X-User-Id` with the resource owner.
- `ROLE_ADMIN`
  - Has global read/write access where business rules permit.

## 2. High-Level Refactor Summary

### Auth Service

- Removed insecure public user-by-id lookup.
- Added safe current-user profile retrieval.
- `/auth/my` now resolves the current user from the authenticated security context instead of exposing `X-User-Id` in Swagger UI.

### Application Service

- Removed public synchronous status update endpoint.
- Added paginated listing endpoints.
- Restored section `PUT` endpoints for full save/replace flows.
- Added section `PATCH` endpoints for partial updates.
- Added soft delete support using `is_deleted` and `CANCELLED`.
- Added cache-aware async status handling so DB updates and API status responses stay in sync.

### Product Endpoints

- Confirmed product listing is served by `application-service`.
- Fixed gateway routing so `/products` and `/products/**` work through port `7003`, not only directly on `7005`.

### Document Service

- Added ownership verification through synchronous Feign calls to `application-service`.
- Added file download endpoint.
- Added secure delete endpoint for applicants.
- Kept inline browser viewing.
- Kept upload, but implemented upsert behavior by `applicationId + documentType`.

### Admin Service

- Consolidated separate approve/reject endpoints into one unified decision endpoint.
- Reintroduced approval-specific fields in the unified request payload.
- Added pagination to admin listings and audit log.
- Kept document verification/rejection through admin-controlled internal flows.

## 3. Removed Endpoints

These endpoints were intentionally removed from the public API.

### Auth Service

- Removed: `GET /auth/users/{id}`
  - Reason: prevents direct object reference access to arbitrary user records.

### Application Service

- Removed: `PUT /applications/{id}/status`
  - Reason: application status transitions must happen asynchronously through RabbitMQ events, not by direct public mutation.

### Admin Service

- Removed: `POST /admin/applications/{id}/approve`
- Removed: `POST /admin/applications/{id}/reject`
  - Reason: replaced with one unified decision endpoint.

## 4. Current Auth Service API

Base path: `/auth`

### Active Endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/auth/register` | Register a new user |
| POST | `/auth/login` | Login and receive JWT |
| GET | `/auth/validate` | Token validation endpoint used by gateway flow |
| GET | `/auth/my` | Get current authenticated user's profile |
| GET | `/auth/internal/users` | Internal admin-only user listing |
| PUT | `/auth/internal/users/{id}` | Internal admin-only user update |
| GET | `/auth/health` | Health check |

### Important Notes

- `/auth/my` no longer asks Swagger users to manually enter `X-User-Id`.
- Current user resolution now comes from `Authentication` populated by the trusted header authentication filter.

## 5. Current Application Service API

Base path: `/applications`

### Active Endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/applications/draft` | Create a draft application |
| PUT | `/applications/{id}/personal` | Save or replace personal details |
| PATCH | `/applications/{id}/personal` | Partially update personal details |
| PUT | `/applications/{id}/employment` | Save or replace employment details |
| PATCH | `/applications/{id}/employment` | Partially update employment details |
| PUT | `/applications/{id}/loan-details` | Save or replace loan details |
| PATCH | `/applications/{id}/loan-details` | Partially update loan details |
| POST | `/applications/{id}/submit` | Submit draft application and publish event |
| GET | `/applications/{id}` | Get full application details |
| GET | `/applications/{id}/status` | Get current status and status history |
| GET | `/applications/my` | Get logged-in applicant's applications with pagination |
| GET | `/applications` | Admin-only paginated application listing |
| DELETE | `/applications/{id}` | Soft delete application |
| GET | `/applications/internal/reports` | Internal admin reports endpoint |

### PUT vs PATCH Rules

- `PUT /applications/{id}/personal`
  - Use when saving the full personal section.
- `PATCH /applications/{id}/personal`
  - Use when updating only selected personal fields.
- The same rule applies for:
  - `/employment`
  - `/loan-details`

### Supported Partial Update Fields

#### Personal

- `firstName`
- `lastName`
- `dob`
- `gender`
- `maritalStatus`
- `addressLine1`
- `addressLine2`
- `city`
- `state`
- `pincode`
- `nationality`

#### Employment

- `employmentType`
- `companyName`
- `designation`
- `monthlyIncome`
- `totalWorkExperience`
- `officeAddress`
- `employmentStatus`

#### Loan Details

- `loanType`
- `loanAmountRequested`
- `tenureMonths`
- `purpose`
- `repaymentType`

### Pagination Changes

Pagination was added to:

- `GET /applications`
- `GET /applications/my`

These now accept standard Spring `Pageable` parameters such as:

- `page`
- `size`
- `sort`

### Soft Delete Changes

Applications are no longer hard-deleted.

- Added `is_deleted` on `loan_applications`
- Deleted applications are marked:
  - `deleted = true`
  - `status = CANCELLED`
  - `currentStage = Cancelled`

### Async Status Update Changes

Status changes now happen via event listeners:

- document verification event -> application moves to `DOCS_VERIFIED`
- admin decision event -> application moves to `APPROVED` or `REJECTED`

Recent fix:

- async listeners now clear `applications-cache`
- async listeners now also update `currentStage`

This was required because the database status was changing correctly, but `/applications/{id}/status` could still return stale cached data.

## 6. Current Product API

Base path: `/products`

### Active Endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/products` | Get complete list of loan products |

### Current Static Products

- Home Loan
- Personal Loan
- Car Loan

### Important Fix

The product endpoint worked directly on application-service at `http://localhost:7005/products`, but returned `404` through gateway at `http://localhost:7003/products`.

This was fixed by adding `/products` routes to the active Config Server gateway config.

## 7. Current Document Service API

Base path: `/documents`

### Active Endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/documents/upload` | Upload or replace a document |
| GET | `/documents/application/{applicationId}` | List documents for an application |
| GET | `/documents/required/{applicationId}` | Get required/uploaded/pending document types |
| GET | `/documents/{id}` | Get document metadata |
| GET | `/documents/{id}/view` | View file inline in browser |
| GET | `/documents/{id}/download` | Download file as attachment |
| GET | `/documents/{id}/history` | Get document verification history |
| DELETE | `/documents/{id}` | Delete a document owned by applicant |
| PUT | `/documents/internal/{id}/status` | Internal admin-only document status update |

### Ownership Enforcement

For all sensitive read/delete/file endpoints, document-service validates access by:

1. loading the document
2. extracting its `applicationId`
3. calling `application-service` via Feign
4. blocking access if the ownership check fails

This applies to:

- metadata access
- view
- download
- delete

### Upload Behavior

Upload now uses upsert logic:

- if a document with the same `documentType` already exists for the `applicationId`
  - overwrite metadata and file
- otherwise
  - insert a new record

### Download Behavior

- `GET /documents/{id}/view`
  - returns `Content-Disposition: inline`
- `GET /documents/{id}/download`
  - returns `Content-Disposition: attachment`

## 8. Current Admin Service API

Base path: `/admin`

### Active Endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/admin/applications` | Get paginated list of applications |
| GET | `/admin/applications/{id}/review` | Get application + documents for review |
| GET | `/admin/applications/{id}/decision` | Get saved decision for an application |
| POST | `/admin/applications/{id}/decision` | Create unified approval/rejection decision |
| GET | `/admin/users` | Get all users |
| PUT | `/admin/users/{id}` | Update user role or status |
| PUT | `/admin/documents/{documentId}/verify` | Verify document |
| PUT | `/admin/documents/{documentId}/reject` | Reject document |
| GET | `/admin/audit-log` | Get paginated audit log |
| GET | `/admin/health` | Health check |

### Unified Decision Endpoint

`POST /admin/applications/{id}/decision` is now the only public decision endpoint.

### Request Payload for Approval

```json
{
  "decision": "APPROVED",
  "remarks": "All docs verified and approved",
  "approvedAmount": 200000.00,
  "approvedTenureMonths": 60,
  "interestRate": 8.50,
  "terms": "EMI"
}
```

### Request Payload for Rejection

```json
{
  "decision": "REJECTED",
  "remarks": "Income criteria not met"
}
```

### Approval Rules

If `decision = APPROVED`, the following fields are expected and persisted:

- `approvedAmount`
- `approvedTenureMonths`
- `interestRate`
- `terms`

Recent fix:

- these fields were added back into the unified request DTO
- admin-service now validates and stores them again for approved loans

### Pagination Changes

Pagination was added to:

- `GET /admin/applications`
- `GET /admin/audit-log`

## 9. Internal and Infrastructure Changes

### Gateway

Updated routing and security behavior:

- gateway injects trusted user headers
- gateway strips spoofed `X-User-*` headers from incoming requests
- product routes added to Config Server gateway config

### Config Server

The effective gateway route fix was applied in:

- `config-repo/api-gateway.yml`

This is important because gateway runtime configuration is loaded from Config Server, so local gateway YAML alone may not be enough.

### Caching

Application cache behavior was refined:

- mutation endpoints evict `applications-cache`
- async RabbitMQ listeners now also evict `applications-cache`

This prevents stale status reads after approval or document verification.

## 10. Entity and Status Model Updates

### LoanApplication

Added:

- `deleted` mapped to `is_deleted`
- new status: `CANCELLED`

### Decision

Unified decision flow now supports:

- `decisionStatus`
- `decisionReason`
- `approvedAmount`
- `approvedTenureMonths`
- `interestRate`
- `terms`

## 11. RabbitMQ Flow After Refactor

### Application Submission

1. Applicant creates draft
2. Applicant fills sections
3. Applicant submits application
4. application-service publishes submission event

### Document Verification

1. Applicant uploads documents
2. Admin verifies documents
3. document-service updates document verification status
4. when all required docs are verified, document-service publishes `DOCUMENTS_VERIFIED`
5. application-service consumes event and updates application to `DOCS_VERIFIED`

### Final Loan Decision

1. Admin calls `POST /admin/applications/{id}/decision`
2. admin-service stores decision record
3. admin-service publishes decision event
4. application-service consumes event
5. application-service updates status to `APPROVED` or `REJECTED`
6. status history is recorded
7. application cache is cleared

## 12. Final Current-State Checklist

### Confirmed Current Behavior

- `/auth/my` uses current authenticated user context
- `/applications/{id}/personal`, `/employment`, `/loan-details` support both `PUT` and `PATCH`
- `/applications/{id}/status` is read-only
- application status transitions are async
- `/products` is public and available through gateway
- documents support metadata, inline view, download, and delete with ownership validation
- admin decisions are unified under one endpoint
- approval fields are stored for approved loans
- paginated admin and application listings are enabled
- soft delete is enabled for applications

## 13. Files Most Directly Affected

Primary implementation areas updated during this refactor include:

- `auth-service/src/main/java/.../AuthController.java`
- `auth-service/src/main/java/.../AuthService.java`
- `application-service/src/main/java/.../ApplicationController.java`
- `application-service/src/main/java/.../ApplicationService.java`
- `application-service/src/main/java/.../ProductController.java`
- `application-service/src/main/java/.../LoanApplication.java`
- `application-service/src/main/java/.../ApplicationDecisionListener.java`
- `application-service/src/main/java/.../DocumentVerifiedListener.java`
- `document-service/src/main/java/.../DocumentController.java`
- `document-service/src/main/java/.../DocumentService.java`
- `admin-service/src/main/java/.../AdminController.java`
- `admin-service/src/main/java/.../AdminService.java`
- `admin-service/src/main/java/.../DecisionRequest.java`
- `config-repo/api-gateway.yml`

## 14. Notes

- Swagger UI may continue to show older schemas until the affected services are restarted and docs are refreshed.
- Gateway route changes from Config Server require the gateway to reload the latest config.
- Some older tests still target removed endpoints and may need to be updated separately.
