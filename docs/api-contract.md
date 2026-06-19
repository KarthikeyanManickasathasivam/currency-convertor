# REST API Contract

Base URL: `/api`

---

## Authentication APIs

### POST /api/auth/register
- **Auth**: Public
- **Request**: `{ "username": "john", "email": "john@example.com", "password": "P@ssw0rd1" }`
- **Validation**: username: 3–50 chars; email: valid format; password: 8–100 chars
- **Success**: `201 { "userId": "<uuid>", "username": "john", "email": "john@example.com", "role": "USER", "isActive": true, "createdAt": "...", "updatedAt": "..." }`
- **Error**: `409` if username or email already exists

### POST /api/auth/login
- **Auth**: Public
- **Request**: `{ "email": "john@example.com", "password": "P@ssw0rd1" }`
- **Validation**: email: @Email @NotBlank; password: @NotBlank
- **Success (MFA required)**: `202 Accepted` (empty body) — OTP emailed via SES, stored in Redis (5-min TTL). JWT is NOT issued yet.
- **Success (MFA bypass)**: `200 { "accessToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 900, "role": "USER" }` + refresh cookie set
- **Error**: `401` invalid credentials

### POST /api/auth/mfa/verify
- **Auth**: Public (but requires valid email from the login step)
- **Request**: `{ "email": "john@example.com", "otp": "482916" }`
- **Validation**: email: @Email @NotBlank; otp: @NotBlank, exactly 6 digits (`\d{6}`)
- **Success**: `200 { "accessToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 900, "role": "USER" }` + refresh token as HTTP-only Secure cookie
- **Error**: `401` invalid/expired OTP
- **Flow**: Verify OTP against Redis key `mfa:otp:{email}`. On success, delete OTP from Redis, issue JWT with `mfaVerified=true` claim.

### POST /api/auth/refresh
- **Auth**: Refresh token in HTTP-only cookie (`refresh_token`)
- **Success**: `200 { "accessToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 900, "role": "USER" }`
- **Error**: `401` expired/invalid refresh token

### POST /api/auth/logout
- **Auth**: Bearer token required
- **Success**: `204 No Content` — Token JTI added to Redis blacklist; refresh cookie cleared

---

## Exchange Rate APIs

### GET /api/rates
- **Auth**: USER, ADMIN
- **Response**: `200 [ { "id": 1, "fromCurrency": "USD", "toCurrency": "EUR", "rate": 0.92, "lastUpdated": "...", "source": "API", "isActive": true } ]`
- **Notes**: Returns all active rates as a plain list (not paginated).

### GET /api/rates/{from}/{to}
- **Auth**: USER, ADMIN
- **Path Params**: `from`, `to` — ISO 4217 currency codes (e.g. `USD`, `EUR`)
- **Response**: `200 { "id": 1, "fromCurrency": "USD", "toCurrency": "EUR", "rate": 0.92, "lastUpdated": "...", "source": "API", "isActive": true }`
- **Notes**: Cache-first (Redis). Returns the current rate for a specific pair.

### GET /api/rates/admin/all
- **Auth**: ADMIN only
- **Query Params**: Spring Pageable (`?page=0&size=20&sort=lastUpdated,desc`)
- **Response**: `200 { "content": [...], "totalPages": 5, "totalElements": 98 }` (Spring Page)

### POST /api/rates/admin
- **Auth**: ADMIN only
- **Request**: `{ "fromCurrency": "USD", "toCurrency": "JPY", "rate": 149.50 }`
- **Validation**: fromCurrency/toCurrency: 3 uppercase letters; rate: positive, max 18 integer / 8 decimal digits
- **Success**: `201 { created RateResponse }` — Invalidates Redis cache
- **Error**: `409` if currency pair already exists

### PUT /api/rates/admin/{id}
- **Auth**: ADMIN only
- **Request**: `{ "rate": 150.25 }`
- **Success**: `200 { updated RateResponse }` — Sets source=MANUAL, invalidates cache key
- **Error**: `404` not found

### DELETE /api/rates/admin/{id}
- **Auth**: ADMIN only
- **Success**: `204 No Content` — Soft delete (isActive=false), invalidates cache
- **Error**: `404` not found

---

## Transaction APIs

### POST /api/transactions
- **Auth**: USER, ADMIN
- **Request**: `{ "fromCurrency": "USD", "toCurrency": "EUR", "amount": 100.00 }`
- **Validation**: fromCurrency/toCurrency: 3 uppercase letters; amount: min 0.01, max 18 integer / 2 decimal digits
- **Success**: `201 { "transactionId": "<uuid>", "userId": "<uuid>", "fromCurrency": "USD", "toCurrency": "EUR", "amount": 100.00, "convertedAmount": 92.00, "rate": 0.92, "transactionDate": "...", "status": "APPROVED", "approvedBy": null, "approvalDate": null, "approvalThreshold": 100.00 }`
- **Notes**: If amount ≥ approval threshold (default $100, configurable), status will be `PENDING_APPROVAL` and admin is notified via SES.

### GET /api/transactions
- **Auth**: USER, ADMIN
- **Query Params**: Spring Pageable (`?page=0&size=20&sort=transactionDate,desc`)
- **Response**: `200 { "content": [...], "totalPages": 5, "totalElements": 98 }` (Spring Page)
- **Notes**: Returns only the authenticated user's own transactions.

---

## User APIs

### GET /api/users/me
- **Auth**: USER, ADMIN (authenticated user)
- **Response**: `200 { "userId": "<uuid>", "username": "john", "email": "john@example.com", "role": "USER", "isActive": true, "createdAt": "...", "updatedAt": "..." }`

### PUT /api/users/me
- **Auth**: USER, ADMIN (authenticated user)
- **Request**: `{ "username": "newname", "password": "NewP@ss1" }` — both fields optional
- **Response**: `200 { updated UserResponse }`

---

## Admin APIs

All endpoints under `/api/admin` require `ROLE_ADMIN`.

### GET /api/admin/dashboard
- **Auth**: ADMIN only
- **Response**: `200 { "totalUsers": 50, "activeUsers": 45, "totalTransactions": 980, "pendingApprovals": 3, "topFromCurrencies": ["USD", "EUR", "GBP", "JPY", "INR"] }`

### GET /api/admin/users
- **Auth**: ADMIN only
- **Query Params**: Spring Pageable (`?page=0&size=20`)
- **Response**: `200 { "content": [ { "userId": "<uuid>", "username": "john", "email": "john@example.com", "role": "USER", "isActive": true, "createdAt": "...", "updatedAt": "..." } ], "totalPages": ..., "totalElements": ... }`

### GET /api/admin/users/{id}
- **Auth**: ADMIN only
- **Path Param**: `id` — UUID
- **Response**: `200 { UserResponse }`

### DELETE /api/admin/users/{id}
- **Auth**: ADMIN only
- **Path Param**: `id` — UUID
- **Response**: `204 No Content` — Soft delete (isActive=false)

### GET /api/admin/transactions
- **Auth**: ADMIN only
- **Query Params**: Spring Pageable (`?page=0&size=20`)
- **Response**: `200 { "content": [...], "totalPages": ..., "totalElements": ... }` — All transactions across all users

### GET /api/admin/transactions/pending
- **Auth**: ADMIN only
- **Response**: `200 [ { TransactionResponse } ]` — All transactions with status=PENDING_APPROVAL

### POST /api/admin/transactions/{id}/approve
- **Auth**: ADMIN only
- **Path Param**: `id` — UUID
- **Response**: `200 { "transactionId": "<uuid>", "status": "APPROVED", "approvedBy": "<adminUuid>", "approvalDate": "...", ... }` — Sends approval email to user via SES

### POST /api/admin/transactions/{id}/reject
- **Auth**: ADMIN only
- **Path Param**: `id` — UUID
- **Request** (optional): `{ "reason": "Exceeds daily limit" }`
- **Response**: `200 { "transactionId": "<uuid>", "status": "REJECTED", "approvedBy": "<adminUuid>", "approvalDate": "...", ... }` — Sends rejection email to user via SES

### GET /api/admin/settings/approval-threshold
- **Auth**: ADMIN only
- **Response**: `200 { "threshold": 100.00, "updatedAt": "...", "updatedBy": "<adminUuid>" }`

### PUT /api/admin/settings/approval-threshold
- **Auth**: ADMIN only
- **Request**: `{ "threshold": 500.00 }`
- **Validation**: threshold: @NotNull, min 0.01, max 18 integer / 2 decimal digits
- **Response**: `200 { "threshold": 500.00, "updatedAt": "...", "updatedBy": "<adminUuid>" }`

### GET /api/admin/logs
- **Auth**: ADMIN only
- **Query Params**: Spring Pageable (`?page=0&size=50`)
- **Response**: `200 { "content": [ { "logId": 1, "event": "User logged in", "eventType": "LOGIN", "timestamp": "...", "userId": "<uuid>", "ipAddress": "...", "details": {...} } ], "totalPages": ... }`

### GET /api/admin/logs/type/{eventType}
- **Auth**: ADMIN only
- **Path Param**: `eventType` — e.g. `LOGIN`, `USER_REGISTERED`, `MFA_VERIFIED`, `LOGOUT`
- **Query Params**: Spring Pageable
- **Response**: `200 { "content": [...], "totalPages": ... }` — Logs filtered by event type, ordered by timestamp desc

---

## Standardized Error Response

All errors follow this format:
```json
{
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Validation failed",
  "fieldErrors": [{ "field": "username", "message": "must not be blank" }],
  "timestamp": "2026-04-29T10:30:00Z",
  "path": "/api/auth/register"
}
```

| Status | Code | When |
|--------|------|------|
| 400 | VALIDATION_ERROR | Invalid input |
| 401 | AUTHENTICATION_FAILED | Missing/invalid/expired JWT or OTP |
| 403 | ACCESS_DENIED | Insufficient role |
| 404 | RESOURCE_NOT_FOUND | Entity not found |
| 409 | CONFLICT | Duplicate (username, email, rate pair) |
| 429 | RATE_LIMIT_EXCEEDED | Too many requests (100/min per user) |
| 500 | INTERNAL_ERROR | Unexpected server error |
| 503 | SERVICE_UNAVAILABLE | External API down with no cached fallback |

---

## JWT Token Structure

```json
{
  "header": { "alg": "RS256", "typ": "JWT" },
  "payload": {
    "sub": "<uuid>",
    "username": "john",
    "role": "USER",
    "mfaVerified": true,
    "iat": 1714380000,
    "exp": 1714380900
  }
}
```
- Access token: 15-min expiry
- Refresh token: 7-day expiry, HTTP-only Secure SameSite=Strict cookie (`refresh_token`), path `/api/auth`
- Signing: RS256 (2048-bit RSA), private key in AWS Secrets Manager
