# Functional & Non-Functional Requirements

## Functional Requirements

| ID | Requirement | Design Components |
|----|-------------|-------------------|
| FR-01 | Users must convert currencies in real-time with live exchange rates | ExchangeRateController, ExchangeRateService, ExternalApiClient, CacheService, TransactionService |
| FR-02 | Users must authenticate via username/password with JWT tokens | AuthController, AuthService, JwtTokenProvider, JwtAuthFilter, SecurityConfig |
| FR-03 | Admin must manage exchange rates independently (CRUD) | ExchangeRateController (admin endpoints), ExchangeRateService, CacheInvalidation |
| FR-04 | All conversions must be logged as immutable transaction records | TransactionService, TransactionRepository, AOP LoggingAspect |
| FR-05 | UI must auto-refresh exchange rates at configurable intervals | Angular PollingService, RxJS interval + switchMap, ExchangeRateComponent |
| FR-06 | System must cache exchange rates with 5-minute TTL | Spring Cache + Redis (Lettuce), CacheService, @Cacheable |
| FR-07 | Admin must view all users, transactions, and system logs | AdminDashboard Angular module, UserController, TransactionController, LogController |
| FR-08 | System events must be logged with structured JSON format | EventLogService, AOP LoggingAspect, Logback JSON encoder, CloudWatch |
| FR-09 | Users must update their own profile (username, password) | UserController, UserService, BCryptPasswordEncoder |
| FR-10 | RBAC must be enforced on every API endpoint | Spring Security @PreAuthorize, JwtAuthFilter, SecurityConfig FilterChain |
| FR-11 | Users must complete MFA (email OTP) during login before JWT issuance | MfaController, MfaService, OtpGenerator, EmailService (SES), Redis OTP store (`mfa:otp:{userId}`, 5-min TTL), MfaVerifyComponent (Angular) |
| FR-12 | Transactions ≥ $100 (configurable) require admin approval with email notifications | TransactionApprovalController, TransactionApprovalService, NotificationService (SES), Transaction entity (status, approvedBy, approvalDate), ApprovalComponent (Angular Admin), config: `transaction.approval.threshold=100` |

## Non-Functional Requirements

| ID | Requirement | Design Component |
|----|-------------|-----------------|
| NFR-01 | API response < 200ms (P95) for cache hits | Redis, Lettuce, cache-aside pattern, HikariCP |
| NFR-02 | API response < 2s (P95) for cache misses | WebClient non-blocking, Resilience4j timeout |
| NFR-03 | 99.9% uptime SLA | ECS Multi-AZ, RDS Multi-AZ, ElastiCache replica |
| NFR-04 | 500 concurrent users, auto-scale to 2000 | ECS auto-scaling, API Gateway throttling, Redis cache-first |
| NFR-05 | Code coverage > 80% | JUnit 5, Mockito, Testcontainers, JaCoCo, SonarQube |
| NFR-06 | TLS 1.3 in transit, AES-256 at rest | ACM, RDS/ElastiCache encryption, WAF |
| NFR-07 | Rate limiting: 100 req/min per user | Bucket4j + Redis token bucket |
| NFR-08 | Structured JSON logs in CloudWatch | SLF4J + Logback JSON encoder, MDC correlation IDs |

## Key Architecture Decisions

| ID | Decision | Rationale |
|----|----------|-----------|
| AD-01 | Stateless JWT over session-based auth | Horizontal scaling on ECS without sticky sessions |
| AD-02 | Cache-aside over write-through for Redis | Full control over TTL and fallback on cache miss |
| AD-03 | Circuit breaker (Resilience4j) over simple retry | Prevents cascading failures; serves cached fallback |
| AD-04 | PostgreSQL over MongoDB | ACID for financial records; schema enforcement |
| AD-05 | ECS Fargate over EC2 | Serverless; no instance management; pay-per-use |
| AD-06 | WebClient over RestTemplate | Non-blocking; works with virtual threads; RestTemplate deprecated |
| AD-07 | Flyway over Liquibase | SQL-native; simpler config; better PostgreSQL support |
| AD-08 | BCrypt cost 12 over Argon2 | Industry standard; Spring Security native; ~250ms balance |
| AD-09 | RS256 over HS256 for JWT | Asymmetric keys; public key distribution; microservices-ready |
| AD-10 | Angular Material over custom UI | Accessible; consistent; saves ~30% frontend dev time |
