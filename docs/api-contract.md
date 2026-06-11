# REST API Contract

Base URL: `/api`

## Authentication APIs

### POST /api/auth/register
- **Auth**: Public
- **Request**: `{ "username": "john", "email": "john@example.com", "password": "P@ssw0rd1" }`
- **Validation**: username: 3-50 chars, alphanumeric; email: valid format; password: min 8 chars, 1 uppercase, 1 digit, 1 special
- **Success**: `201 { "userId": 1, "username": "john", "role": "USER" }`
- **Error**: `409` if username or email already exists

### POST /api/auth/login
- **Auth**: Public
- **Request**: `{ "username": "john", "password": "P@ssw0rd1" }`
- **Validation**: username: @NotBlank; password: @NotBlank
- **Success**: `200 { "userId": 1, "mfaRequired": true, "message": "OTP sent to registered email" }` — OTP emailed via SES, stored in Redis (5-min TTL). JWT is NOT issued at this step.
- **Error**: `401` invalid credentials

### POST /api/auth/mfa-verify
- **Auth**: Public (but requires valid userId from login step)
- **Request**: `{ "userId": 1, "otpCode": "482916" }`
- **Validation**: otpCode: @NotBlank, exactly 6 digits; max 3 attempts per OTP
- **Success**: `200 { "accessToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 900 }` + refresh token as HTTP-only Secure cookie
- **Error**: `401` invalid/expired OTP; `429` max attempts exceeded (3 tries)
- **Flow**: Verify OTP against Redis key `mfa:otp:{userId}`. On success, delete OTP from Redis, issue JWT with `mfaVerified=true` claim.

### POST /api/auth/logout
- **Auth**: Bearer token required
- **Success**: `200 { "message": "Logged out successfully" }` — Token JTI added to Redis blacklist; refresh cookie cleared

### POST /api/auth/refresh
- **Auth**: Refresh token in HTTP-only cookie
- **Success**: `200 { "accessToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 900 }`
- **Error**: `401` expired/invalid refresh token

## Exchange Rate APIs

### GET /api/exchange-rate?from=USD&to=EUR&amount=100
- **Auth**: USER, ADMIN
- **Response**: `200 { "from": "USD", "to": "EUR", "rate": 0.92, "amount": 100, "convertedAmount": 92.00, "status": "APPROVED", "transactionId": 123, "timestamp": "..." }`
- **Notes**: Core conversion endpoint. Cache-first (Redis). Logs transaction. If amount ≥ $100, status will be "PENDING_APPROVAL" and admin is notified via SES.
- **Validation**: from/to: valid ISO 4217 codes; amount: positive, max 18 digits

### GET /api/exchange-rates
- **Auth**: USER (read-only), ADMIN (full CRUD)
- **Response**: `200 [ { "id": 1, "fromCurrency": "USD", "toCurrency": "EUR", "rate": 0.92, "lastUpdated": "...", "source": "API" } ]`

### POST /api/exchange-rates
- **Auth**: ADMIN only
- **Request**: `{ "fromCurrency": "USD", "toCurrency": "JPY", "rate": 149.50 }`
- **Success**: `201 { created rate object }` — Invalidates Redis cache
- **Error**: `409` if currency pair already exists

### PUT /api/exchange-rates/{id}
- **Auth**: ADMIN only
- **Request**: `{ "rate": 150.25 }`
- **Success**: `200 { updated rate }` — Sets source=MANUAL, invalidates cache key
- **Error**: `404` not found

### DELETE /api/exchange-rates/{id}
- **Auth**: ADMIN only
- **Success**: `204 No Content` — Soft delete (isActive=false), invalidates cache
- **Error**: `404` not found

## Transaction APIs

### GET /api/transactions
- **Auth**: USER (own transactions only), ADMIN (all transactions)
- **Query Params**: `?page=0&size=20&startDate=2026-01-01&endDate=2026-12-31&status=PENDING_APPROVAL`
- **Response**: `200 { "content": [...], "totalPages": 5, "totalElements": 98 }` (Spring Page)

### GET /api/transactions/{id}/approve
- **Auth**: ADMIN only
- **Method**: PUT
- **Response**: `200 { "transactionId": 123, "status": "APPROVED", "approvedBy": "admin1", "approvalDate": "..." }` — Sends approval email to user via SES

### GET /api/transactions/{id}/reject
- **Auth**: ADMIN only
- **Method**: PUT
- **Response**: `200 { "transactionId": 123, "status": "REJECTED", "approvedBy": "admin1", "approvalDate": "..." }` — Sends rejection email to user via SES

## User APIs

### GET /api/users
- **Auth**: ADMIN only
- **Response**: `200 [ { "userId": 1, "username": "john", "email": "john@example.com", "role": "USER", "isActive": true, "createdAt": "..." } ]`

### GET /api/users/{id}
- **Auth**: USER (self only), ADMIN (any)
- **Response**: `200 { user details }`

### PUT /api/users/{id}
- **Auth**: USER (self only), ADMIN (any)
- **Request**: `{ "username": "newname", "email": "new@example.com" }` (password change requires current password)

### DELETE /api/users/{id}
- **Auth**: ADMIN only
- **Response**: `204` — Soft delete (isActive=false)

## Log APIs

### GET /api/logs
- **Auth**: ADMIN only
- **Query Params**: `?page=0&size=50&eventType=LOGIN&startDate=...&endDate=...`
- **Response**: `200 { "content": [...], "totalPages": 10 }`

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

## JWT Token Structure

```json
{
  "header": { "alg": "RS256", "typ": "JWT" },
  "payload": {
    "sub": "42",
    "username": "john",
    "role": "USER",
    "mfaVerified": true,
    "iat": 1714380000,
    "exp": 1714380900
  }
}
```
- Access token: 15-min expiry
- Refresh token: 7-day expiry, HTTP-only Secure SameSite=Strict cookie
- Signing: RS256 (2048-bit RSA), private key in AWS Secrets Manager
