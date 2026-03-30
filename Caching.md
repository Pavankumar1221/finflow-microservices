# Caching Implementation and Verification Guide

This document describes the current in-memory caching setup in FinFlow and lists the active endpoints that are cached or that evict cache entries.

## Overview

FinFlow uses Spring's simple in-memory caching.

- Provider: `ConcurrentMapCacheManager`
- Shared config: [application.yml](D:/FinFlow/config-repo/application.yml)
- Cache type: `spring.cache.type: simple`
- Caching enabled in:
  - `auth-service`
  - `application-service`
  - `document-service`
  - `admin-service`

## 1. Auth Service

Base path: `/auth`

### Cached Read Endpoints

| Method | Endpoint | Cache Name | Cache Key |
|---|---|---|---|
| GET | `/auth/my` | `users-cache` | `#userId` |

### Cache Eviction Endpoints

| Method | Endpoint | Cache Name | Eviction |
|---|---|---|---|
| PUT | `/auth/internal/users/{id}` | `users-cache` | key `#id` |

### Notes

- `/auth/my` resolves the current authenticated user from the security context.
- The old `GET /auth/users/{id}` endpoint is no longer part of the public API and should not be used for cache verification.

## 2. Application Service

Base path: `/applications`

### Cached Read Endpoints

| Method | Endpoint | Cache Name | Cache Key |
|---|---|---|---|
| GET | `/applications/{id}` | `applications-cache` | `#appId` |
| GET | `/applications/{id}/status` | `applications-cache` | `'status-' + #appId` |
| GET | `/applications/my` | `applications-cache` | `'applicant-' + #applicantId + '-' + #pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort.toString()` |
| GET | `/applications` | `applications-cache` | `'all-' + #pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort.toString()` |

### Cache Eviction Endpoints

| Method | Endpoint | Cache Name | Eviction |
|---|---|---|---|
| POST | `/applications/draft` | `applications-cache` | `allEntries = true` |
| PUT | `/applications/{id}/personal` | `applications-cache` | `allEntries = true` |
| PATCH | `/applications/{id}/personal` | `applications-cache` | `allEntries = true` |
| PUT | `/applications/{id}/employment` | `applications-cache` | `allEntries = true` |
| PATCH | `/applications/{id}/employment` | `applications-cache` | `allEntries = true` |
| PUT | `/applications/{id}/loan-details` | `applications-cache` | `allEntries = true` |
| PATCH | `/applications/{id}/loan-details` | `applications-cache` | `allEntries = true` |
| POST | `/applications/{id}/submit` | `applications-cache` | `allEntries = true` |
| DELETE | `/applications/{id}` | `applications-cache` | `allEntries = true` |

### Async Cache Eviction

The following asynchronous consumers also clear `applications-cache` after status changes:

- document verification listener after `DOCUMENTS_VERIFIED`
- admin decision listener after `APPROVED` or `REJECTED`

This is important because application status changes are event-driven and not performed by a public status mutation endpoint.

## 3. Product Endpoint

Base path: `/products`

### Cached Read Endpoints

- None

### Cache Eviction Endpoints

- None

### Notes

- `/products` is active and routed through the gateway, but it is not currently cached.

## 4. Document Service

Base path: `/documents`

### Cached Read Endpoints

| Method | Endpoint | Cache Name | Cache Key |
|---|---|---|---|
| GET | `/documents/application/{applicationId}` | `documents-cache` | `'app-' + #applicationId` |
| GET | `/documents/{id}` | `documents-cache` | `#documentId` |

### Cache Eviction Endpoints

| Method | Endpoint | Cache Name | Eviction |
|---|---|---|---|
| POST | `/documents/upload` | `documents-cache` | `allEntries = true` |
| DELETE | `/documents/{id}` | `documents-cache` | `allEntries = true` |
| PUT | `/documents/internal/{id}/status` | `documents-cache` | `allEntries = true` |

### Notes

- `GET /documents/{id}/view`
- `GET /documents/{id}/download`
- `GET /documents/{id}/history`

These endpoints are not directly annotated as cached endpoints, but they rely on document lookup flows that use cached document metadata internally.

## 5. Admin Service

Base path: `/admin`

### Cached Read Endpoints

| Method | Endpoint | Cache Name | Cache Key |
|---|---|---|---|
| GET | `/admin/applications/{id}/decision` | `decisions-cache` | `#applicationId` |

### Cache Eviction Endpoints

| Method | Endpoint | Cache Name | Eviction |
|---|---|---|---|
| POST | `/admin/applications/{id}/decision` | `decisions-cache` | `allEntries = true` |
| POST | `/admin/applications/{id}/decision` | `reports-cache` | `allEntries = true` |

### Notes

- The old separate endpoints:
  - `POST /admin/applications/{id}/approve`
  - `POST /admin/applications/{id}/reject`

  are no longer the active public endpoints.

## 6. How To Verify Manually

Use the same request twice against a cached read endpoint.

Expected behavior:

1. First request:
   - normal DB/service execution
2. Second identical request:
   - reduced SQL or reduced backend work
3. Perform a matching write operation:
   - cache is evicted
4. Call the same GET again:
   - fresh DB/service execution happens again

### Recommended Verification Targets

- `GET /auth/my`
- `GET /applications/{id}`
- `GET /applications/{id}/status`
- `GET /applications/my?page=0&size=10`
- `GET /applications?page=0&size=10`
- `GET /documents/application/{applicationId}`
- `GET /documents/{id}`
- `GET /admin/applications/{id}/decision`

### Best Signals To Watch

- service console logs
- SQL logs
- repeated read behavior before and after mutation

## 7. Summary

Current active caches in FinFlow:

- `users-cache`
- `applications-cache`
- `documents-cache`
- `decisions-cache`
- `reports-cache` for eviction handling in admin flows

Current active cached public GET endpoints:

- `GET /auth/my`
- `GET /applications/{id}`
- `GET /applications/{id}/status`
- `GET /applications/my`
- `GET /applications`
- `GET /documents/application/{applicationId}`
- `GET /documents/{id}`
- `GET /admin/applications/{id}/decision`
