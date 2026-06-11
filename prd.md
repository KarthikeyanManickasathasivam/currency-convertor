# Currency Exchange Service — Developer Guide

## Overview

Real-time currency exchange service with live market data, MFA-secured authentication, and an admin approval workflow for high-value transactions. This guide covers local development setup, architecture decisions, API reference, testing, and AWS deployment.

---

## Table of Contents

1. [Tech Stack](#1-tech-stack)
2. [Local Development Setup](#2-local-development-setup)
3. [Project Structure](#3-project-structure)
4. [Architecture Decisions](#4-architecture-decisions)
5. [Authentication Flow](#5-authentication-flow)
6. [Transaction Approval Flow](#6-transaction-approval-flow)
7. [Caching Strategy](#7-caching-strategy)
8. [API Reference](#8-api-reference)
9. [Configuration Reference](#9-configuration-reference)
10. [Local vs AWS Profile Differences](#10-local-vs-aws-profile-differences)
11. [Running Tests](#11-running-tests)
12. [AWS Deployment](#12-aws-deployment)
13. [Troubleshooting](#13-troubleshooting)

---

## 1. Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 (Spring Boot 3.3.x minimum; upgrade to 21 for virtual threads) |
| Backend | Spring Boot 3.3.x, Spring Security 6.x, Spring Data JPA, Hibernate 6.x |
| Frontend | Angular 18, Angular Material, Tailwind CSS 3.x, TypeScript |
| Database | PostgreSQL 16 |
| Cache | Redis 7.x (AWS) / ConcurrentMapCacheManager (local) |
| Email | Amazon SES via SMTP (AWS) / console log (local) |
| Auth | JWT RS256 — 15-min access token + 7-day HTTP-only refresh cookie |
| MFA | Email OTP — 6-digit, 5-min TTL, max 3 attempts |
| Resilience | Resilience4j 2.x — circuit breaker, retry |
| Build | Maven 3.9.x (backend), Angular CLI 18 (frontend) |
| API Docs | SpringDoc OpenAPI 2.x (Swagger UI at `/swagger-ui.html`) |
| IaC | Terraform 1.7.x |
| Deployment | AWS Lambda + Lambda Web Adapter + SnapStart (Java 17) |

---

## 2. Local Development Setup

### Prerequisites

| Tool | Version | Install |
|---|---|---|
| Java | 17+ | `winget install Amazon.Corretto.17.JDK` |
| Maven | 3.9.x | https://maven.apache.org/download.cgi |
| PostgreSQL | 16 | https://www.postgresql.org/download/ |
| Node.js | 20.x LTS | https://nodejs.org |
| Angular CLI | 18 | `npm install -g @angular/cli@18` |

### Step-by-step

**1. Verify Java 17**
```bash
java -version   # must show 17.x or higher
mvn -version    # must show 3.9.x
```

**2. Create the local database**
```bash
psql -U postgres -c "CREATE DATABASE currency_exchange;"
```

**3. Configure environment (optional)**

The `local` Spring profile uses safe defaults — no `.env` file needed for basic startup:
- DB: `localhost:5432/currency_exchange`, user `postgres`, password `postgres`
- Cache: in-memory (no Redis required)
- Email: logged to console (no SES required)
- JWT keys: RSA pair auto-generated at startup (no config required)

Override any default via environment variables:
```bash
export DB_USERNAME=myuser
export DB_PASSWORD=mypassword
export ALPHA_VANTAGE_API_KEY=your_key   # optional; "demo" key used if absent
```

**4. Start the backend**
```bash
mvn spring-boot:run
```

Flyway applies all 4 migrations automatically on first run. Look for:
```
Successfully applied 4 migrations to schema "public"
```

**5. Verify**
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

**6. Start the frontend**
```bash
cd frontend
npm install
npm start          # ng serve --proxy-config proxy.conf.json
```

- App: http://localhost:4200
- All `/api/**` requests proxy to `http://localhost:8080` via `proxy.conf.json`

---

## 3. Project Structure

```
currency-convertor/
├── pom.xml
├── prd.md                           ← this file
├── progress.md                      ← implementation status tracker
├── .env.example                     ← all required env vars with descriptions
├── src/
│   ├── main/
│   │   ├── java/com/exchange/
│   │   │   ├── CurrencyExchangeApplication.java
│   │   │   ├── controller/
│   │   │   │   ├── AuthController           POST /api/auth/**
│   │   │   │   ├── ExchangeRateController   GET/POST/PUT/DELETE /api/exchange-rate(s)
│   │   │   │   ├── TransactionController    GET /api/transactions
│   │   │   │   ├── UserController           GET/PUT /api/users/me
│   │   │   │   └── AdminController          /api/admin/** (ADMIN role only)
│   │   │   ├── service/
│   │   │   │   ├── AuthService, JwtService
│   │   │   │   ├── ExchangeRateService, TransactionService
│   │   │   │   ├── UserService, LogService
│   │   │   │   ├── OtpService (interface)
│   │   │   │   ├── EmailService (interface)
│   │   │   │   ├── TokenBlacklistService (interface)
│   │   │   │   └── impl/
│   │   │   │       ├── InMemoryOtpService        @Profile("local")
│   │   │   │       ├── RedisOtpService            @Profile("aws")
│   │   │   │       ├── ConsoleEmailService        @Profile("local")
│   │   │   │       ├── SesEmailService            @Profile("aws")
│   │   │   │       ├── InMemoryTokenBlacklistService  @Profile("local")
│   │   │   │       └── RedisTokenBlacklistService     @Profile("aws")
│   │   │   ├── model/
│   │   │   │   ├── User, ExchangeRate, Transaction, Log
│   │   │   │   └── enums/  Role, TransactionStatus, Source
│   │   │   ├── repository/
│   │   │   │   └── UserRepository, ExchangeRateRepository,
│   │   │   │       TransactionRepository, LogRepository
│   │   │   ├── dto/
│   │   │   │   ├── request/   RegisterRequest, LoginRequest, MfaVerifyRequest,
│   │   │   │   │              ConversionRequest, RateRequest
│   │   │   │   └── response/  AuthResponse, UserResponse, RateResponse,
│   │   │   │                  TransactionResponse, LogResponse,
│   │   │   │                  DashboardStatsResponse, ErrorResponse
│   │   │   ├── config/
│   │   │   │   ├── SecurityConfig, WebClientConfig, OpenApiConfig
│   │   │   │   └── RedisConfig  @Profile("aws")
│   │   │   ├── filter/
│   │   │   │   └── JwtAuthFilter, RateLimitFilter, CorrelationIdFilter
│   │   │   ├── integration/
│   │   │   │   ├── ExternalRateApiClient (interface)
│   │   │   │   ├── ExchangeRateHostClient  @Primary (primary source)
│   │   │   │   └── AlphaVantageClient              (circuit breaker fallback)
│   │   │   ├── exception/
│   │   │   │   └── GlobalExceptionHandler + 4 custom exceptions
│   │   │   └── aspect/
│   │   │       └── LoggingAspect, PerformanceMonitorAspect
│   │   └── resources/
│   │       ├── application.yml             base config (all profiles)
│   │       ├── application-local.yml       local: in-memory cache, no Redis/mail
│   │       ├── application-aws.yml         aws: all values as ${ENV_VAR}
│   │       ├── logback-spring.xml          local=colorized console, aws=JSON
│   │       └── db/migration/
│   │           ├── V1__create_users.sql
│   │           ├── V2__create_exchange_rates.sql
│   │           ├── V3__create_transactions.sql
│   │           └── V4__create_logs.sql
│   └── test/
│       ├── java/com/exchange/
│       │   ├── service/      AuthServiceTest, ExchangeRateServiceTest, TransactionServiceTest
│       │   ├── controller/   AuthControllerTest (@WebMvcTest)
│       │   ├── repository/   UserRepositoryTest (@DataJpaTest + H2)
│       │   └── service/impl/ InMemoryOtpServiceTest
│       └── resources/
│           └── application-test.properties
├── frontend/
│   ├── angular.json, package.json, proxy.conf.json
│   └── src/app/
│       ├── auth/       login/, register/, mfa/ components
│       ├── dashboard/  dashboard.component.ts
│       ├── admin/      admin.component.ts
│       └── shared/     interceptors/, services/, models/
└── terraform/
    ├── main.tf, variables.tf
    ├── vpc.tf, rds.tf, elasticache.tf
    ├── lambda.tf, ses.tf, iam.tf
```

---

## 4. Architecture Decisions

| Decision | Choice | Why |
|---|---|---|
| Auth identity | Email (not username) as JWT subject | `UserDetailsService.loadByUsername()` and JWT subject must match — email is the unique login credential |
| Auth | Stateless JWT RS256 | Horizontal scaling without sticky sessions; asymmetric keys are microservices-ready |
| MFA | Email OTP, 6-digit, 5-min TTL | No authenticator app dependency; JWT issued only after MFA completes |
| Cache | Cache-aside (`@Cacheable`) | Full TTL control; clean fallback on cache miss; no stale writes |
| Circuit breaker | Resilience4j | Prevents cascading failures on external API outages; serves last cached rate as fallback |
| DB | PostgreSQL | ACID compliance for financial records; schema enforcement |
| Migrations | Flyway | SQL-native; simpler config; better PostgreSQL support than Liquibase |
| Password hashing | BCrypt cost 12 | ~250ms per hash; industry standard; Spring Security native |
| HTTP client | WebClient | Non-blocking I/O; RestTemplate is deprecated in Spring 6 |
| Deployment | Lambda + SnapStart | Serverless; SnapStart eliminates JVM cold-start on Java 17+ |
| Frontend | Angular Material | Accessible components; consistent design system |

---

## 5. Authentication Flow

```
Client                     Backend                    External
  |                           |                           |
  |-- POST /api/auth/login -->|                           |
  |                           |-- BCrypt verify (~250ms) --|
  |                           |-- Generate 6-digit OTP    |
  |                           |-- Store OTP (5-min TTL)   |
  |                           |-- Send OTP via email ----->|
  |<-- 200 { mfaRequired } --|                            |
  |                           |                           |
  |-- POST /api/auth/mfa-verify (otpCode) -------------->|
  |                           |-- Verify OTP (max 3 tries)|
  |                           |-- Delete OTP              |
  |<-- 200 { accessToken } + Set-Cookie: refreshToken ----|
```

**Key implementation details:**
- `User.getUsername()` returns `email` — this is the JWT subject and `loadByUsername()` key
- `User.getDisplayUsername()` returns the actual username field — used only in API responses
- Access token: 15-min expiry, sent as `Authorization: Bearer <token>` header
- Refresh token: 7-day expiry, HTTP-only Secure SameSite=Strict cookie (never readable by JS)
- Logout: token JTI added to blacklist (in-memory locally, Redis on AWS) with TTL = remaining token expiry

**Local dev:** OTP is printed to the console in a bordered log block — no email service required.

---

## 6. Transaction Approval Flow

```
POST /api/transactions
         |
         v
amount >= transaction.approval.threshold (default: $100 USD)?
         |
    YES  |   NO
         |    \
         v     v
  PENDING_APPROVAL    APPROVED immediately
         |
         v
  Admin notified by email (console log locally)
         |
  Admin: PUT /api/admin/transactions/{id}/approve
      OR PUT /api/admin/transactions/{id}/reject
         |
         v
  User notified by email (console log locally)
```

**Configure the threshold:**
```yaml
# application.yml
transaction:
  approval:
    threshold: 100   # change to adjust the trigger amount in USD
```

---

## 7. Caching Strategy

Spring Cache abstraction (`@Cacheable`, `@CacheEvict`) is used — the same annotations work with both backends:

| Profile | Implementation | TTL behaviour |
|---|---|---|
| `local` | `ConcurrentMapCacheManager` | No expiry (in-process, clears on restart) |
| `aws` | Redis via Lettuce | 5-minute TTL configured in `application-aws.yml` |

Cache key pattern: `rates::{FROM}::{TO}` (e.g. `rates::USD::EUR`)

Cache is evicted when an admin creates, updates, or deletes a rate via the API.

**Circuit breaker fallback order:**
1. Cache hit → return immediately
2. Cache miss → call exchangerate.host (primary, `@Primary`)
3. Primary fails → call Alpha Vantage (fallback)
4. Both fail → return last persisted rate from DB with stale-data flag
5. No DB record → throw `ServiceUnavailableException` (503)

---

## 8. API Reference

Full interactive docs: http://localhost:8080/swagger-ui.html

### Auth

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Create account |
| POST | `/api/auth/login` | Public | Validate credentials; triggers OTP email |
| POST | `/api/auth/mfa-verify` | Public | Submit OTP; returns JWT + refresh cookie |
| POST | `/api/auth/logout` | Bearer | Blacklist token + clear refresh cookie |
| POST | `/api/auth/refresh` | Refresh cookie | Issue new access token |

### Exchange Rates

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/exchange-rate?from=USD&to=EUR&amount=100` | USER/ADMIN | Convert currency; logs transaction |
| GET | `/api/exchange-rates` | USER/ADMIN | List all active rates |
| POST | `/api/exchange-rates` | ADMIN | Create rate pair (invalidates cache) |
| PUT | `/api/exchange-rates/{id}` | ADMIN | Update rate (sets source=MANUAL, invalidates cache) |
| DELETE | `/api/exchange-rates/{id}` | ADMIN | Soft-delete rate (isActive=false) |

### Transactions

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/transactions` | USER (own) / ADMIN (all) | List transactions, paginated |
| PUT | `/api/admin/transactions/{id}/approve` | ADMIN | Approve pending transaction |
| PUT | `/api/admin/transactions/{id}/reject` | ADMIN | Reject pending transaction |

### Users

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/users/me` | USER/ADMIN | Get own profile |
| PUT | `/api/users/me` | USER/ADMIN | Update own profile |
| GET | `/api/admin/users` | ADMIN | List all users |
| DELETE | `/api/admin/users/{id}` | ADMIN | Soft-delete user (isActive=false) |

### Logs & Admin Stats

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/admin/logs` | ADMIN | System logs, paginated + filterable |
| GET | `/api/admin/dashboard` | ADMIN | Stats: total users, transactions, pending approvals, top currencies |

### RBAC Summary

| Resource | USER | ADMIN |
|---|---|---|
| Auth endpoints | Public | Public |
| GET /api/exchange-rate (conversion) | Allowed | Allowed |
| GET /api/exchange-rates | Read-only | Full CRUD |
| POST/PUT/DELETE /api/exchange-rates | 403 | Allowed |
| GET /api/transactions | Own only | All |
| PUT /api/admin/transactions/*/approve,reject | 403 | Allowed |
| GET/PUT /api/users/me | Self only | Any user |
| DELETE /api/admin/users/* | 403 | Allowed |
| GET /api/admin/logs | 403 | Allowed |

### Standard Error Response

```json
{
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Validation failed",
  "fieldErrors": [{ "field": "email", "message": "must be a valid email" }],
  "timestamp": "2026-04-30T10:30:00Z",
  "path": "/api/auth/register"
}
```

| Status | Code | When |
|---|---|---|
| 400 | VALIDATION_ERROR | Invalid request body |
| 401 | AUTHENTICATION_FAILED | Bad/expired JWT or OTP |
| 403 | ACCESS_DENIED | Insufficient role |
| 404 | RESOURCE_NOT_FOUND | Entity not found |
| 409 | CONFLICT | Duplicate username/email/rate pair |
| 429 | RATE_LIMIT_EXCEEDED | > 100 req/min per user |
| 503 | SERVICE_UNAVAILABLE | External API down with no cached fallback |

---

## 9. Configuration Reference

### Key application.yml properties

```yaml
transaction:
  approval:
    threshold: 100          # USD; conversions >= this require admin approval

app:
  jwt:
    expiration: 900000      # access token TTL: 15 minutes (ms)
    refresh-expiration: 604800000  # refresh token TTL: 7 days (ms)
    issuer: currency-exchange-service

rate-limit:
  requests-per-minute: 100  # per IP/user

resilience4j:
  circuitbreaker:
    instances:
      rateApi:
        failure-rate-threshold: 50       # open after 50% failures
        sliding-window-size: 10          # evaluated over last 10 calls
        wait-duration-in-open-state: 30s # half-open probe interval
        permitted-number-of-calls-in-half-open-state: 3
  retry:
    instances:
      rateApi:
        max-attempts: 3
        wait-duration: 500ms
```

### AWS environment variables (application-aws.yml)

| Variable | Purpose |
|---|---|
| `DB_URL` | `jdbc:postgresql://<rds-endpoint>:5432/currency_exchange` |
| `DB_USERNAME` | RDS master username |
| `DB_PASSWORD` | RDS master password |
| `REDIS_HOST` | ElastiCache primary endpoint |
| `REDIS_PORT` | Default 6379 |
| `SES_SMTP_USERNAME` | SES SMTP access key ID |
| `SES_SMTP_PASSWORD` | SES SMTP secret access key |
| `JWT_PRIVATE_KEY` | Base64-encoded PKCS8 RSA private key |
| `JWT_PUBLIC_KEY` | Base64-encoded X.509 RSA public key |
| `EMAIL_FROM_ADDRESS` | SES-verified sender address |
| `TRANSACTION_ADMIN_EMAIL` | Admin notification recipient |
| `ALPHA_VANTAGE_API_KEY` | Alpha Vantage API key (fallback rate source) |
| `EXCHANGERATE_HOST_API_KEY` | exchangerate.host API key (primary rate source) |

**Generating RSA keys for JWT:**
```bash
openssl genrsa -out private.pem 2048
openssl pkcs8 -topk8 -inform PEM -in private.pem -out private_pkcs8.pem -nocrypt
openssl rsa -in private.pem -pubout -out public.pem
base64 -w 0 private_pkcs8.pem   # value for JWT_PRIVATE_KEY
base64 -w 0 public.pem          # value for JWT_PUBLIC_KEY
```

---

## 10. Local vs AWS Profile Differences

| Concern | `local` profile | `aws` profile |
|---|---|---|
| Activate | Default (no config needed) | `SPRING_PROFILES_ACTIVE=aws` |
| Database | `localhost:5432` hardcoded | RDS via `${DB_URL}` |
| Cache | ConcurrentMapCacheManager | Redis / ElastiCache |
| OTP storage | ConcurrentHashMap + scheduler | Redis key `otp::{email}`, 5-min TTL |
| Token blacklist | In-memory HashSet | Redis key `blacklist::{token}` with TTL |
| Email | Logged to console | Amazon SES SMTP |
| JWT keys | RSA pair auto-generated on startup | Injected via `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY` |
| Rate limiting | In-memory counter map | Redis-backed Bucket4j |
| Log format | Colorized console (human-readable) | Structured JSON → CloudWatch Logs |

**No code changes needed to move from local to AWS** — only environment variables differ.

---

## 11. Running Tests

```bash
# All tests
mvn test

# Single test class
mvn test -Dtest=AuthServiceTest

# With JaCoCo coverage report
mvn test jacoco:report
# Open: target/site/jacoco/index.html
```

Test configuration in `src/test/resources/application-test.properties`:
- Flyway disabled
- `ddl-auto=create-drop` — H2 creates schema from JPA entities
- Mail, Redis, Data Redis autoconfiguration excluded

| Test type | Framework | What it covers |
|---|---|---|
| Service unit tests | JUnit 5 + Mockito | Auth, ExchangeRate, Transaction business logic |
| OTP unit test | JUnit 5 | TTL expiry + eviction in InMemoryOtpService |
| Repository test | `@DataJpaTest` + H2 | UserRepository custom queries |
| Controller test | `@WebMvcTest` + MockMvc | AuthController HTTP contract |
| Integration tests | Testcontainers | Blocked — requires Docker |
| E2E tests | Cypress | Not started |

---

## 12. AWS Deployment

### Prerequisites
- Terraform 1.7.x installed
- AWS CLI configured: `aws configure` (region: `ap-south-1`)
- SES sender email verified in AWS console

### 1. Deploy infrastructure
```bash
cd terraform
terraform init
terraform plan
terraform apply
```

Note the outputs: RDS endpoint, ElastiCache endpoint, API Gateway URL.

### 2. Build the Lambda JAR
```bash
mvn package -DskipTests
# Output: target/currency-exchange-*.jar
```

### 3. Upload to Lambda
```bash
aws lambda update-function-code \
  --function-name currency-exchange \
  --zip-file fileb://target/currency-exchange-1.0.0.jar
```

### 4. Set environment variables on Lambda
```bash
aws lambda update-function-configuration \
  --function-name currency-exchange \
  --environment Variables="{
    SPRING_PROFILES_ACTIVE=aws,
    DB_URL=jdbc:postgresql://<rds-endpoint>:5432/currency_exchange,
    DB_USERNAME=exchange_user,
    DB_PASSWORD=<password>,
    REDIS_HOST=<elasticache-endpoint>,
    REDIS_PORT=6379,
    SES_SMTP_USERNAME=<ses-key>,
    SES_SMTP_PASSWORD=<ses-secret>,
    JWT_PRIVATE_KEY=<base64-private>,
    JWT_PUBLIC_KEY=<base64-public>,
    EMAIL_FROM_ADDRESS=no-reply@yourdomain.com,
    TRANSACTION_ADMIN_EMAIL=admin@yourdomain.com,
    ALPHA_VANTAGE_API_KEY=<key>
  }"
```

### 5. Enable SnapStart and publish version
```bash
# Enable SnapStart (one-time)
aws lambda put-function-event-invoke-config \
  --function-name currency-exchange \
  --snap-start ApplyOn=PublishedVersions

aws lambda publish-version --function-name currency-exchange
```

### 6. Deploy frontend
```bash
cd frontend
npm run build:prod
aws s3 sync dist/currency-exchange-frontend/ s3://your-frontend-bucket/ --delete
aws cloudfront create-invalidation --distribution-id <id> --paths "/*"
```

### 7. Verify
- API: `https://<api-gateway-url>/swagger-ui.html`
- Health: `https://<api-gateway-url>/actuator/health`
- Frontend: `https://<cloudfront-domain>`

### Lambda configuration reference

| Setting | Value |
|---|---|
| Runtime | Java 17 |
| Memory | 1024 MB |
| Timeout | 30 seconds |
| SnapStart | Enabled (`ApplyOn=PublishedVersions`) |
| VPC | Same VPC as RDS + ElastiCache |

---

## 13. Troubleshooting

### "release version 17 not supported" at compile time
Java 17 is not installed or `JAVA_HOME` points to Java 11.
```bash
java -version       # must show 17.x
echo $JAVA_HOME     # must point to JDK 17 install dir
```
Fix: `winget install Amazon.Corretto.17.JDK` then set `JAVA_HOME`.

### "Connection refused" on port 5432 at startup
PostgreSQL is not running.
```bash
net start postgresql-x64-16
psql -U postgres -c "CREATE DATABASE currency_exchange;"
```

### Mail/Redis connection error at startup (local profile)
The `application-local.yml` must exclude Mail and Redis autoconfiguration. Confirm these exclusions are present:
```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration
      - org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
```

### OTP not visible during login (local dev)
Watch console logs for a bordered block:
```
╔══════════════════════════════════════╗
  OTP for user@example.com : 482916
╚══════════════════════════════════════╝
```
If missing, confirm `SPRING_PROFILES_ACTIVE` is `local` (or not set — local is default).

### 401 Unauthorized after successful MFA verify
The JWT subject is the user's **email** (not the username field). Verify:
- `User.getUsername()` returns `email`
- `SecurityConfig.userDetailsService()` calls `userRepository.findByEmail()`
- `JwtAuthFilter` extracts subject from token and passes it to `loadUserByUsername()`

### API responses show email in the `username` field
The response mapper is calling `user.getUsername()` (which returns email) instead of `user.getDisplayUsername()`. Check `AuthService.toResponse()` and `UserService.toResponse()`.

### Circuit breaker open — rates returning stale data
External API is down. The app serves the last rate persisted in the database — this is expected degraded-mode behaviour. Check external API status. The circuit breaker probes again after 30s (`wait-duration-in-open-state`).

### Angular 404 on page refresh (production)
Client-side routing requires the server to redirect all paths to `index.html`. Configure CloudFront:
```
Error code 403 → Response page path: /index.html, Response code: 200
Error code 404 → Response page path: /index.html, Response code: 200
```
