# FinFlow Logout Feature Guide

This document explains the simple backend logout feature added to FinFlow.

## 1. Why Logout Needed Special Handling

FinFlow uses JWT authentication.

That means:

- when a user logs in, they receive a JWT token
- that token stays valid until it expires
- simply "logging out" on the client side does not destroy the JWT on the server

Without blacklist logic:

- even after logout, the same JWT could still be used until expiry

## 2. Beginner-Friendly Solution Used Here

For this project, logout is implemented using an in-memory token blacklist.

No database table was added.

No Redis was added.

No new microservice was created.

This was intentionally kept simple for learning purposes.

## 3. How The Logout Flow Works

### Login

1. User calls `POST /auth/login`
2. `auth-service` returns a JWT
3. User pastes that JWT into Swagger `Authorize`
4. Secured endpoints work

### Logout

1. User calls `POST /auth/logout`
2. `auth-service` reads the current JWT from the `Authorization` header
3. `auth-service` stores that token in an in-memory blacklist until the token's expiry time
4. The old token is now treated as invalid

### After Logout

1. User may still see the old token inside Swagger `Authorize`
2. But when that token is used:
   - gateway validates the JWT
   - gateway also asks `auth-service` whether it is blacklisted
   - if blacklisted, gateway returns `401 Unauthorized`

So:

- Swagger UI does not automatically remove the token text
- but the token becomes unusable after logout

## 4. Current Endpoints Added

### Public Auth Endpoint

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/auth/logout` | Logout current user and invalidate current JWT |

### Internal Hidden Endpoint

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/auth/internal/token/blacklisted` | Internal gateway check for blacklist status |

## 5. Request and Response

### Logout Request

No body is needed.

You only send the current JWT in:

```http
Authorization: Bearer <token>
```

### Logout Response

```json
{
  "message": "Logged out successfully"
}
```

### Internal Blacklist Check Request

```json
{
  "token": "jwt-token-value"
}
```

### Internal Blacklist Check Response

```json
{
  "blacklisted": true
}
```

or

```json
{
  "blacklisted": false
}
```

## 6. Where Tokens Are Stored

Logged-out tokens are stored in memory inside `auth-service`.

Implementation style:

- in-memory map
- key = token string
- value = token expiry time

This behaves like a small in-memory blacklist cache.

## 7. Why In-Memory Was Chosen

This project is for learning, so the simplest useful approach was selected.

Advantages:

- easy to understand
- fast to implement
- no database schema changes
- no Redis dependency
- enough for development and testing

## 8. Limitation Of In-Memory Blacklist

Because blacklist is only in memory:

- if `auth-service` restarts, all blacklisted tokens are forgotten
- then old tokens become valid again until their original JWT expiry time

For a learning project, this is acceptable.

For production, you would usually use:

- database storage
or
- Redis

## 9. Gateway Role In Logout

Logout enforcement works because `api-gateway` now does two checks:

1. local JWT validation
2. internal blacklist check with `auth-service`

Only if both pass does the request continue.

## 10. How To Test In Swagger

### Step 1

Call:

- `POST /auth/login`

Copy the token.

### Step 2

Paste the token into Swagger `Authorize`.

### Step 3

Call any secured endpoint and confirm it works.

### Step 4

Call:

- `POST /auth/logout`

using the same token.

### Step 5

Without removing the token from Swagger, call a secured endpoint again.

Expected result:

- request should fail with `401 Unauthorized`

### Step 6

Login again and get a new token.

### Step 7

Paste the new token into Swagger `Authorize`.

Expected result:

- secured endpoints work again

## 11. Important Swagger Note

Swagger UI does not automatically clear the token from the `Authorize` popup after logout.

So after logout:

- token may still appear in the UI
- but backend will reject it

To continue testing after logout:

- login again
- paste the new token manually

## 12. Files Updated For This Feature

Main implementation areas:

- `auth-service/src/main/java/.../AuthController.java`
- `auth-service/src/main/java/.../AuthService.java`
- `auth-service/src/main/java/.../JwtUtil.java`
- `auth-service/src/main/java/.../TokenBlacklistService.java`
- `auth-service/src/main/java/.../TokenStatusRequest.java`
- `api-gateway/src/main/java/.../AuthenticationFilter.java`
- `api-gateway/src/main/java/.../AuthTokenStatusService.java`
- `api-gateway/src/main/java/.../WebClientConfig.java`

## 13. Summary

This logout feature now gives FinFlow a real backend logout mechanism.

It works by:

- blacklisting the current JWT on logout
- checking blacklist status on every secured request through the gateway

This is the simplest strong logout model suitable for your current backend-only learning project.
