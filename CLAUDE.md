# Currency Exchange Rate Service

## Project Overview
Real-time currency exchange rate service - enterprise-grade web app for instant, accurate currency conversion with live market data, MFA-secured authentication, and admin approval workflow for high-value transactions.

## Tech Stack
- **Language**: Java 21 LTS (use virtual threads, records, pattern matching)
- **Backend**: Spring Boot 3.3.x, Spring Security 6.x, Spring Data JPA, Hibernate 6.x
- **Frontend**: Angular 18, Angular Material, Tailwind CSS 3.x, TypeScript
- **Database**: PostgreSQL 16 (Amazon RDS Multi-AZ)
- **Cache**: Redis 7.x (Amazon ElastiCache) via Spring Cache + Lettuce
- **Email**: Amazon SES (MFA OTP delivery + transaction approval notifications)
- **Auth**: JWT RS256 (15-min access + 7-day refresh cookie) with mandatory email-based MFA
- **Resilience**: Resilience4j 2.x (circuit breaker, retry, rate limiter)
- **Build**: Maven 3.9.x (backend), Angular CLI (frontend)
- **Testing**: JUnit 5, Mockito, Testcontainers, Cypress
- **Deployment**: AWS Lambda (SnapStart) + API Gateway HTTP API — JAR packaged directly, no Docker
- **Frontend Hosting**: Amazon S3 (private, versioned, encrypted) + CloudFront CDN (Angular 404→index.html, no-cache on index.html)
- **Security**: AWS WAF (Managed Rules: CommonRuleSet, SQLi, KnownBadInputs + rate-limit rule) on API GW and CloudFront
- **CI/CD**: AWS CodePipeline + CodeBuild
- **IaC**: Terraform 1.7.x
- **Code Quality**: SonarQube 10.x (>80% coverage gate)
- **API Docs**: SpringDoc OpenAPI 2.x (Swagger)

## Architecture Decisions
1. **Stateless JWT + MFA** - JWT RS256 tokens with mandatory email OTP (6-digit, 5-min TTL in Redis). JWT not issued until MFA verification completes. No server-side sessions.
2. **Transaction Approval Workflow** - Conversions ≥ $100 (configurable: `transaction.approval.threshold`) get status=PENDING_APPROVAL. Admin approves/rejects via dashboard. Email notifications via SES to both admin and user.
3. **Cache-Aside Pattern** - Redis caches exchange rates (5-min TTL). App controls population/invalidation. ~95% cache hit rate in steady state.
4. **Circuit Breaker** - Resilience4j wraps external API calls. 50% failure threshold, 30s open wait, falls back to last cached rate.
5. **BCrypt cost 12** for passwords (~250ms hash time).
6. **RS256 over HS256** for JWT - asymmetric keys, microservices-ready.
7. **PostgreSQL** for ACID compliance on financial transaction records.
8. **AWS Lambda (SnapStart)** - serverless, no containers. Cold start mitigated by SnapStart (publishes a versioned snapshot). API Gateway HTTP API routes to a Lambda alias (required for SnapStart — `$LATEST` is not eligible). Lambda runtime: `java21`; memory: 1024 MB; timeout: 30 s.

## Package Structure
```
com.exchange.controller    - REST endpoints (thin, delegate to services)
com.exchange.service       - Business logic, @Transactional boundaries
com.exchange.repository    - Spring Data JPA repos with custom @Query
com.exchange.model         - JPA entities: User, ExchangeRate, Transaction, Log
com.exchange.dto.request   - Inbound DTOs with Jakarta validation
com.exchange.dto.response  - Outbound DTOs (never expose entities)
com.exchange.config        - SecurityConfig, RedisConfig, WebClientConfig, CorsConfig
com.exchange.filter        - JwtAuthFilter, RateLimitFilter, CorrelationIdFilter
com.exchange.integration   - ExternalApiClient interface + implementations
com.exchange.exception     - Custom exceptions + GlobalExceptionHandler
com.exchange.util          - JwtUtil, CurrencyValidator, DateUtil
com.exchange.aspect        - LoggingAspect, PerformanceMonitorAspect
```

## Coding Conventions
- Controllers: `@RestController` + `@RequestMapping` + `@PreAuthorize` for RBAC. Always return `ResponseEntity<T>`.
- Services: `@Service` + `@Transactional` for writes. Never return entities - map to DTOs.
- Entities: `@Entity` + `@Table` with explicit column names. Use `BigDecimal` for money/rates (NEVER double/float).
- Validation: Jakarta Bean Validation on all request DTOs (`@NotBlank`, `@Size`, `@Min`, `@Email`).
- Error Handling: `@ControllerAdvice` with `GlobalExceptionHandler`. Never leak stack traces. Standardized `ErrorResponse` format.
- Testing: Write unit tests alongside implementation. Use `@WebMvcTest` for controllers, `@DataJpaTest` for repos, Testcontainers for integration.
- Logging: SLF4J with `@Slf4j` (Lombok). Use structured JSON format. Include correlationId in all logs.
- DB Migrations: Flyway SQL files in `src/main/resources/db/migration/` named `V{N}__{description}.sql`.

## Reference Docs
All detailed specifications are in the `docs/` folder:
- `docs/CLAUDE.md` — This file (auto-read by Claude Code)
- `docs/data-model.md` — DDL, entity designs, all field definitions with JPA annotations
- `docs/api-contract.md` — Full REST API specification with request/response schemas
- `docs/functional-requirements.md` — FR-01 through FR-12 with design component mapping
- `docs/architecture-summary.md` — HLD summary: all architecture views, security, deployment, integration

## Implementation Order (Suggested)
1. Project scaffolding (Spring Boot + dependencies in pom.xml)
2. Flyway migrations (all 4 tables + indexes)
3. Entity classes (User, ExchangeRate, Transaction, Log)
4. Repositories (JpaRepository + custom queries)
5. Auth module (register, login, MFA OTP via SES, JWT generation)
6. Security config (filter chain, RBAC)
7. Exchange rate module (Redis caching, external API with circuit breaker)
8. Transaction module (conversion, approval workflow, notifications)
9. Admin APIs (user management, rate CRUD, log viewer, approval endpoints)
10. Angular frontend (Auth → Dashboard → Admin)
11. Terraform (AWS infrastructure — Lambda, API GW, RDS, ElastiCache, S3/CloudFront, WAF, monitoring)
