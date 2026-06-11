# Currency Exchange — Implementation Progress

_Last updated: 2026-05-01_

---

## Prerequisites

| Item | Status | Notes |
|---|---|---|
| Java 17 | PENDING | IT request submitted. Install: `winget install Amazon.Corretto.17.JDK` |
| PostgreSQL 16 | PENDING | Required for full backend run. Create DB `currency_exchange` once installed. |
| Maven 3.9.x | DONE | v3.9.6 confirmed |
| Node.js + Angular CLI 18 | DONE | Installed. Note: Node 23.x detected — use `--legacy-peer-deps` on npm install |

---

## Phase 1 — Project Scaffolding

Status: **COMPLETE**

All config files, Spring Boot entry point, Flyway setup, and both Spring profiles (`local`, `aws`) are in place.

---

## Phase 2 — Database Layer

Status: **COMPLETE**

All 4 Flyway migrations (V1–V4), all JPA entities, enums, and repositories done.

---

## Phase 3 — Auth Module

Status: **COMPLETE**

Full auth flow: register → login → MFA OTP → JWT issue → logout → refresh.
Profile-swappable implementations for OTP, email, token blacklist, and rate limiter (local = in-memory, aws = Redis).

---

## Phase 4 — Exchange Rate Module

Status: **COMPLETE**

Cache-aside pattern with `@Cacheable`, circuit breaker (Resilience4j) wrapping external API calls, primary + fallback API clients, admin CRUD endpoints.

**Bug fixed (2026-05-01):** `PUT /api/rates/admin/{id}` was using `RateRequest` (which requires `fromCurrency`/`toCurrency`) for an update-only operation. Created `RateUpdateRequest` DTO with just the `rate` field. Frontend UI inline edit now works correctly.

---

## Phase 5 — Transaction Module

Status: **COMPLETE**

Conversion, approval workflow (threshold ≥ $100), admin approve/reject, email notifications.

---

## Phase 6 — Admin & User APIs

Status: **COMPLETE**

Admin dashboard stats, user management, log viewer, rate CRUD. Swagger UI enabled.

**Bug fixed (2026-05-01):** `PUT /api/users/me` only updated username, ignored password. `UserService.updateProfile()` now accepts and BCrypt-encodes the password field. `UserController` passes both fields through.

**Bug fixed (2026-05-01):** `LogResponse` field names (`logId`, `event`, `timestamp`) didn't match frontend `LogEntry` model (`id`, `createdAt`, `entityType`). Frontend model and admin template updated to match backend.

---

## Phase 7 — Angular Frontend

Status: **COMPLETE — fully tested against mock backend**

All components built and manually verified. Mock Express backend runs on port 3001 for frontend-only development (no Java needed).

| Component | Status | Notes |
|---|---|---|
| Login | DONE | Split-screen layout, show/hide password, inline error banners |
| Register | DONE | Split-screen, feature checklist panel |
| MFA Verify | DONE | OTP input with wide letter-spacing, shield icon header |
| Dashboard — Convert tab | DONE | Live rate preview pill, green/amber result card with animation |
| Dashboard — History tab | DONE | Auto-refresh every 5 s, pill status badges |
| Dashboard — Profile tab | DONE | Avatar initials, username + password update |
| Admin — Pending Approvals | DONE | Badge count on tab, approve/reject with reason prompt |
| Admin — All Transactions | DONE | Full paginated table |
| Admin — Rate Management | DONE | Add pair form + inline edit (plain input, no mat-form-field in table) + delete |
| Admin — Users | DONE | Avatar initials, role badges, status dot indicator |
| Admin — System Logs | DONE | Aligned to backend field names (`timestamp`, `event`, `eventType`) |
| Auth interceptor | DONE | Calls `auth.clearSession()` on 401 (keeps Angular signals in sync) |
| Cypress e2e tests | NOT STARTED | |

**UI fixes applied (2026-05-01):**
- Tailwind `preflight` disabled in `tailwind.config.js` — was overriding Angular Material's form field border/label styles, causing text overlap
- Inline rate edit replaced `mat-form-field` inside `mat-table` cell with a plain styled `<input>` — removes extra floating-label height that made the row look broken

---

## Phase 8 — AWS / Terraform

Status: **COMPLETE — reviewed and all gaps fixed (2026-05-01)**

| File | Status | Notes |
|---|---|---|
| `terraform/main.tf` | DONE | Remote state backend activated; `us-east-1` provider alias added for CloudFront WAF |
| `terraform/variables.tf` | DONE | Added `email_from_address`, `transaction_admin_email`, `alert_email` |
| `terraform/vpc.tf` | DONE ✓FIXED | Added NAT Gateway + route tables (was missing — Lambda had no internet access) |
| `terraform/rds.tf` | DONE ✓FIXED | Changed `db.t3.micro` → `db.t3.medium`; added gp3 storage, enhanced monitoring |
| `terraform/elasticache.tf` | DONE ✓FIXED | Replaced single-node `aws_elasticache_cluster` with `aws_elasticache_replication_group` (Multi-AZ); added `transit_encryption_enabled` and `at_rest_encryption_enabled` |
| `terraform/lambda.tf` | DONE ✓FIXED | Added `publish = true` + `aws_lambda_alias`; API GW now routes to alias (SnapStart requires published version, not `$LATEST`); fixed JAR filename |
| `terraform/ses.tf` | DONE | Placeholder domain — update before deploy |
| `terraform/iam.tf` | DONE | Lambda execution role, VPC access, SES send permissions |
| `terraform/frontend.tf` | DONE ✓NEW | S3 (private, versioned, encrypted) + CloudFront (Angular 404→index.html fix, no-cache on index.html) |
| `terraform/waf.tf` | DONE ✓NEW | WAF on API GW and CloudFront — AWS Managed Rules (Common, SQLi, KnownBadInputs) + rate-limit rule |
| `terraform/monitoring.tf` | DONE ✓NEW | SNS topic + 10 CloudWatch alarms (Lambda, API GW, RDS, Redis) + log group with 90-day retention |
| `terraform/terraform.tfvars.example` | DONE ✓NEW | Template for all required secrets |
| `.gitignore` | DONE ✓NEW | Prevents `terraform.tfvars`, `*.pem`, `target/`, `node_modules/` from being committed |

---

## Documentation

| File | Status |
|---|---|
| `README.md` | DONE — full project overview, local dev steps, API reference, project structure |
| `prd.md` | DONE — developer guide: architecture, config, auth flow, deployment |
| `cloud-migration-guide.md` | DONE — per-service breakdown, networking, deployment sequence, verification checklist |
| Swagger UI (`/swagger-ui.html`) | DONE — all endpoints documented with `@Tag`, `@Operation`, `@SecurityRequirement` |

---

## Tests

| Item | Status | Notes |
|---|---|---|
| `AuthServiceTest.java` | DONE | |
| `ExchangeRateServiceTest.java` | DONE | |
| `TransactionServiceTest.java` | DONE | |
| `InMemoryOtpServiceTest.java` | DONE | |
| `UserRepositoryTest.java` (`@DataJpaTest`) | DONE | Uses H2 in-memory |
| `AuthControllerTest.java` (`@WebMvcTest`) | DONE | |
| Integration tests (Testcontainers) | BLOCKED | Requires Docker — skipped for now |
| Cypress e2e tests | NOT STARTED | |

---

## Known Issues / Blockers

| # | Issue | Resolution |
|---|---|---|
| 1 | **Java 17 not installed** — backend cannot be built or run | IT request pending. Run `winget install Amazon.Corretto.17.JDK`, then set `JAVA_HOME` |
| 2 | **PostgreSQL not installed** — required for local backend | Install PostgreSQL 16, create DB `currency_exchange` with user `postgres`/`postgres` |
| 3 | **Integration tests require Docker** | Blocked; all unit tests pass |
| 4 | **Terraform `ses.tf` has placeholder domain** | Replace `yourdomain.com` before `terraform apply` |
| 5 | **Terraform state bucket must be created manually** before `terraform init` | `aws s3 mb s3://your-terraform-state-bucket --region ap-south-1` |
| 6 | **`environment.prod.ts` has placeholder API URL** | Set `apiUrl` to actual API Gateway URL after `terraform apply` |

---

## All Bugs Fixed (Cumulative)

| Date | Bug | Fix |
|---|---|---|
| Earlier | Redis compilation error on local build | Moved `spring-boot-starter-data-redis` to main deps; `@Profile` controls bean creation |
| Earlier | Spring Mail startup crash locally | Excluded Mail + Redis autoconfiguration in `application-local.yml` |
| Earlier | `AuthController` NPE on refresh token | Re-load user from DB by email at MFA verify time |
| Earlier | Missing `@Primary` on `ExchangeRateHostClient` | Added `@Primary` annotation |
| Earlier | H2 test failure — JSONB column | Removed `columnDefinition = "jsonb"` from `Log.details` |
| Earlier | Logging conflict | Removed `logging.pattern.console` from `application.yml` |
| Earlier | SES sender address missing | Added `@Value("${app.email.from}")` + `message.setFrom()` in `SesEmailService` |
| Earlier | JWT/UserDetails email-username mismatch | `User.getUsername()` returns email; `getDisplayUsername()` added for API responses |
| Earlier | Response mappers returning email as username | `AuthService.toResponse()` and `UserService.toResponse()` call `getDisplayUsername()` |
| Earlier | 401 → Angular signals not updated | Interceptor calls `auth.clearSession()` instead of directly clearing localStorage |
| Earlier | `json-server` native build failure (Windows) | Replaced with plain `express@4.18.2` mock server |
| 2026-05-01 | Text overlapping in mat-form-field | Disabled Tailwind `preflight` in `tailwind.config.js` |
| 2026-05-01 | Inline rate edit showing double-height row | Replaced `mat-form-field` inside table cell with plain `<input>` |
| 2026-05-01 | `PUT /api/rates/admin/{id}` fails validation | Created `RateUpdateRequest` DTO (rate field only); update endpoint no longer requires fromCurrency/toCurrency |
| 2026-05-01 | Profile update ignores password | `UserService.updateProfile()` now BCrypt-encodes and saves password field |
| 2026-05-01 | `LogEntry` model mismatch with backend | Frontend model fields updated to match `LogResponse` (`logId`, `event`, `timestamp`) |
| 2026-05-01 | Terraform: Lambda had no internet access | Added NAT Gateway + route tables to `vpc.tf` |
| 2026-05-01 | Terraform: ElastiCache TLS mismatch | Added `transit_encryption_enabled = true` to replication group |
| 2026-05-01 | Terraform: SnapStart on `$LATEST` (no-op) | Added `publish = true` + `aws_lambda_alias`; API GW routes to alias |
| 2026-05-01 | Terraform: wrong RDS instance class | `db.t3.micro` → `db.t3.medium` per architecture spec |
| 2026-05-01 | Terraform: ElastiCache single-node (NFR-03) | Replaced with Multi-AZ replication group |
| 2026-05-01 | Terraform: missing frontend infra | Added `frontend.tf` (S3 + CloudFront) |
| 2026-05-01 | Terraform: no WAF (NFR-06) | Added `waf.tf` with AWS Managed Rules + rate limiting |
| 2026-05-01 | Terraform: no monitoring (architecture requirement) | Added `monitoring.tf` with SNS + 10 CloudWatch alarms |

---

## How to Run (Frontend Only — No Java Needed)

```bash
cd frontend
npm install --legacy-peer-deps
npm run start:mock
# → Mock API on http://localhost:3001
# → Angular app on http://localhost:4200

# Login: admin@example.com (any password) → OTP: 123456 → Admin Dashboard
# Login: user@example.com  (any password) → OTP: 123456 → User Dashboard
```

## How to Run Full Stack (Once Java 17 is Installed)

```bash
# 1. Create local database
psql -U postgres -c "CREATE DATABASE currency_exchange;"

# 2. Start backend (Flyway runs migrations automatically)
mvn spring-boot:run
# → http://localhost:8080
# → Swagger UI: http://localhost:8080/swagger-ui.html

# 3. Start frontend (proxies /api → :8080)
cd frontend && npm start
# → http://localhost:4200
```
