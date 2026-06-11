# Architecture Summary (from HLD)

## System Architecture (5-Tier)
Angular SPA (CloudFront) → API Gateway (JWT + throttling) → Spring Boot (ECS Fargate) → Redis Cache / External APIs / SES → PostgreSQL (RDS Multi-AZ)

## Security Configuration

### Spring Security Filter Chain Order
1. **CorsFilter** — Allow CloudFront domain only
2. **CorrelationIdFilter** — Generate UUID; set in MDC for logging
3. **RateLimitFilter** (Bucket4j) — 100 req/min per user; 10 req/min for /api/auth/*
4. **JwtAuthenticationFilter** — Extract Bearer token; validate RS256 signature/expiry; set SecurityContext
5. **UsernamePasswordAuthenticationFilter** — Default Spring Security
6. **ExceptionTranslationFilter** — Translate auth exceptions to HTTP responses
7. **FilterSecurityInterceptor** — Enforce @PreAuthorize

### Authentication Flow (with MFA)
1. Login Request (POST /api/auth/login)
2. BCrypt Verify (cost 12, ~250ms)
3. Generate 6-digit OTP → Store in Redis (`mfa:otp:{userId}`, 5-min TTL)
4. Send OTP via Amazon SES to user's email
5. User submits OTP (POST /api/auth/mfa-verify)
6. Verify OTP against Redis (max 3 attempts)
7. On success: issue JWT (RS256, 15-min) with `mfaVerified=true` + refresh cookie (7-day)
8. Delete OTP from Redis

### Transaction Approval Flow
1. User submits conversion request
2. Service checks `transaction.approval.threshold` (default: $100)
3. If amount ≥ threshold → status=PENDING_APPROVAL
4. Send email notification to admin via SES
5. Admin approves/rejects via PUT /api/transactions/{id}/approve or /reject
6. Send email notification to user via SES (approved/rejected)
7. Update transaction status + approvedBy + approvalDate

### RBAC Matrix
| Resource | USER | ADMIN |
|----------|------|-------|
| POST /api/auth/register, /login, /mfa-verify | Public | Public |
| GET /api/exchange-rate (conversion) | Allowed | Allowed |
| GET /api/exchange-rates | Read-only | Full CRUD |
| POST/PUT/DELETE /api/exchange-rates | Denied (403) | Allowed |
| GET /api/transactions | Own only | All |
| PUT /api/transactions/{id}/approve,reject | Denied | Allowed |
| GET/PUT /api/users/{id} | Self only | All |
| DELETE /api/users/{id} | Denied | Allowed |
| GET /api/logs | Denied | Allowed |

## Caching Strategy (Redis)
- **Pattern**: Cache-aside (application manages cache)
- **Rate cache**: `rate:{FROM}:{TO}` with 5-min TTL
- **MFA OTP**: `mfa:otp:{userId}` with 5-min TTL, max 3 attempts
- **Token blacklist**: `token:blacklist:{jti}` with TTL matching token expiry
- **Rate limiting**: `ratelimit:{ip}` managed by Bucket4j
- **Cache hit rate target**: ~95% in steady state
- **Invalidation**: Admin rate update → explicit key delete; Scheduled refresh → overwrite

## External API Integration
- **Primary**: exchangerate.host (170+ currencies, API key in query param)
- **Fallback**: Alpha Vantage (activated when primary circuit breaker trips)
- **Client**: Spring WebClient (non-blocking) wrapped with Resilience4j

### Resilience4j Config
```yaml
resilience4j:
  circuitbreaker:
    instances:
      rateApi:
        failureRateThreshold: 50
        slidingWindowSize: 10
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 3
  retry:
    instances:
      rateApi:
        maxAttempts: 3
        waitDuration: 500ms
  timelimiter:
    instances:
      rateApi:
        timeoutDuration: 3s
```
- **Fallback**: Return last cached rate with stale-data warning flag

## Amazon SES Integration
- **Purpose**: MFA OTP emails + transaction approval/rejection notifications
- **Auth**: IAM Role (ECS Task Role) — no API keys needed
- **Mode**: AWS SDK `SendEmail`
- **Triggers**: On login (MFA OTP), on transaction approval/rejection events

## Deployment Architecture
- **Region**: ap-south-1 (Mumbai)
- **VPC**: 10.0.0.0/16, public + private subnets across 2 AZs
- **ECS Fargate**: 2-8 tasks (0.5 vCPU, 1GB RAM each), Multi-AZ, rolling deploy
- **RDS**: db.t3.medium, PostgreSQL 16, Multi-AZ, 50GB gp3, auto-backups 7-day
- **ElastiCache**: cache.t3.micro, Redis 7.x, encryption in-transit, replica in 2nd AZ
- **API Gateway**: REST, JWT Authorizer, 1000 req/sec burst
- **CloudFront**: S3 origin, ACM SSL, custom domain
- **WAF**: SQL injection, XSS, rate-based rules
- **Monitoring**: CloudWatch dashboards + X-Ray tracing + alarms

## Monitoring & Alarms
| Metric | Threshold | Action |
|--------|-----------|--------|
| API P95 Latency | > 2000ms for 5 min | SNS notification |
| API Error Rate (5xx) | > 1% for 5 min | SNS + PagerDuty |
| ECS Task Count | < 2 tasks | SNS; ECS auto-replaces |
| RDS CPU | > 80% for 10 min | SNS |
| Redis Memory | > 75% | SNS; review TTLs |
| Cache Hit Ratio | < 80% for 15 min | SNS; investigate |

## Logging
- **Format**: Structured JSON via SLF4J + Logback
- **Fields**: timestamp, level, correlationId, logger, message, userId, path, method, statusCode, durationMs
- **Destination**: CloudWatch Logs (90-day retention → S3 Glacier archive)
- **Levels**: ERROR (exceptions), WARN (rate limits, stale cache), INFO (conversions, auth, admin), DEBUG (dev/staging only)

## Angular Frontend Modules
| Module | Components | Purpose |
|--------|-----------|---------|
| AppModule | AppComponent, Header, Footer | Root layout, routing, interceptors |
| AuthModule (lazy) | LoginComponent, RegisterComponent, MfaVerifyComponent | Auth pages with reactive forms |
| DashboardModule (lazy) | CurrencyConverterComponent, TransactionHistoryComponent, ProfileComponent | User features |
| AdminModule (lazy) | AdminDashboardComponent, RateManagementComponent, UserManagementComponent, LogViewerComponent, ApprovalComponent | Admin features |
| SharedModule | LoadingSpinner, ErrorAlert, Pagination, CurrencyDropdown | Reusable UI |

## Docker Configuration
```dockerfile
# Stage 1: Build
FROM maven:3.9-amazoncorretto-21 AS build
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests

# Stage 2: Runtime
FROM amazoncorretto:21-alpine
COPY --from=build /app/target/exchange-service.jar app.jar
EXPOSE 8080
HEALTHCHECK CMD curl -f http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:+UseZGC", "-Xmx768m", "-jar", "app.jar"]
```
Final image: < 200MB. ZGC for low-latency.

## Application Properties (key ones)
```yaml
# Custom app config
transaction:
  approval:
    threshold: 100  # USD amount; configurable

mfa:
  otp:
    length: 6
    ttl-minutes: 5
    max-attempts: 3

jwt:
  access-token-expiry: 900      # 15 minutes (seconds)
  refresh-token-expiry: 604800  # 7 days (seconds)

exchange-rate:
  cache-ttl-minutes: 5
  refresh-interval-minutes: 5
  primary-api-url: https://api.exchangerate.host/latest
  fallback-api-url: https://www.alphavantage.co/query
```
