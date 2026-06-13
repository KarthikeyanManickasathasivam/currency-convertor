# XChange — Currency Exchange Service

Real-time currency conversion platform with MFA-secured authentication, live exchange rates, and an admin approval workflow for high-value transactions.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.3.x · Spring Security 6 · Spring Data JPA |
| Language | Java 21 LTS (virtual threads, records, pattern matching) |
| Database | PostgreSQL 16 (Amazon RDS Multi-AZ in production) |
| Cache | Spring Cache — in-memory locally, Redis 7.x (ElastiCache) on AWS |
| Auth | JWT RS256 · Email-based MFA (OTP via Amazon SES) |
| Resilience | Resilience4j — circuit breaker, retry, rate limiter |
| API Docs | SpringDoc OpenAPI 2 (Swagger UI) |
| Frontend | Angular 18 · Angular Material · Tailwind CSS 3 |
| Build | Maven 3.9 (backend) · Angular CLI 18 (frontend) |
| Testing | JUnit 5 · Mockito · Testcontainers · Cypress |
| Deployment (POC) | AWS Elastic Beanstalk — JAR upload, single-instance EC2, no Docker |
| Deployment (Prod) | Amazon ECS Fargate — containerized, ALB, Blue/Green, auto-scaling |
| CI/CD | AWS CodePipeline + CodeBuild |
| Security | AWS WAF (CommonRuleSet, SQLi, KnownBadInputs + rate-limit) |
| IaC | Terraform 1.7 |

---

## Features

- **MFA Login** — Email/password + 6-digit OTP (printed to console in local dev)
- **Live Rate Preview** — Rate shown instantly as you select currency pair
- **Currency Conversion** — 10 supported currencies; auto-approves < $100, queues ≥ $100 for admin review
- **Admin Dashboard** — Approve/reject pending transactions, manage users, manage exchange rates, view system logs
- **Profile Management** — Update username and password
- **Circuit Breaker** — Falls back to last cached rate when external API is unavailable
- **Swagger UI** — All endpoints documented and testable at `/swagger-ui.html`

---

## Local Development

### Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java | 21 | Amazon Corretto 21 recommended |
| Maven | 3.9.x | `mvn -v` to check |
| PostgreSQL | 16 | Running locally on `localhost:5432` |
| Node.js | 20 LTS | Node 23 has peer-dep issues with Angular 18 |
| Angular CLI | 18 | `npm install -g @angular/cli@18` |

### 1 — Database setup (one-time)

```sql
CREATE DATABASE currency_exchange;
```

Default credentials assumed: `postgres` / `postgres`. Override via env vars if different (see Config section).

### 2 — Run the backend

```bash
# From project root
mvn spring-boot:run
```

Spring Boot starts on **http://localhost:8080**.  
Flyway migrations run automatically on startup.  
OTP codes are printed to the console — no email service needed locally.

### 3 — Run the frontend

```bash
cd frontend
npm install
npm start          # proxies /api → localhost:8080
```

Angular dev server starts on **http://localhost:4200**.

### Frontend-only mode (no Java needed)

If Java 21 isn't installed yet, run the frontend against the built-in Express mock backend:

```bash
cd frontend
npm install
npm run start:mock   # starts mock API on :3001 + Angular on :4200
```

**Mock test accounts** (any password, OTP is always `123456`):

| Email | Role |
|---|---|
| `user@example.com` | USER |
| `admin@example.com` | ADMIN |

---

## Configuration

### Backend profiles

| Profile | When used | Config file |
|---|---|---|
| `local` (default) | Local dev | `application-local.yml` |
| `aws` | Production | `application-aws.yml` |

Set the active profile:
```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

### Key config values (`application-local.yml`)

```yaml
spring:
  datasource:
    url:      jdbc:postgresql://localhost:5432/currency_exchange
    username: postgres
    password: postgres
  cache:
    type: simple          # in-memory; no Redis needed locally

transaction:
  approval:
    threshold: 100        # transactions >= $100 require admin approval
```

### Environment variables (AWS profile)

```
DB_URL                  jdbc:postgresql://<rds-endpoint>:5432/currency_exchange
DB_USERNAME             <rds-username>
DB_PASSWORD             <rds-password>
REDIS_HOST              <elasticache-endpoint>
REDIS_PORT              6379
SES_SMTP_USERNAME       <ses-smtp-user>
SES_SMTP_PASSWORD       <ses-smtp-password>
JWT_PRIVATE_KEY         <rsa-private-key-base64>
JWT_PUBLIC_KEY          <rsa-public-key-base64>
ALPHA_VANTAGE_API_KEY   <api-key>
```

---

## API Reference

Swagger UI: **http://localhost:8080/swagger-ui.html**  
OpenAPI JSON: **http://localhost:8080/api-docs**

### Quick reference

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Register new user |
| POST | `/api/auth/login` | Public | Initiate login (triggers OTP) |
| POST | `/api/auth/mfa/verify` | Public | Verify OTP → receive JWT |
| POST | `/api/auth/logout` | Bearer | Invalidate token |
| GET | `/api/rates` | Bearer | List all active rates |
| GET | `/api/rates/{from}/{to}` | Bearer | Get specific pair rate |
| POST | `/api/transactions` | USER | Submit conversion |
| GET | `/api/transactions` | USER | My transaction history |
| GET | `/api/admin/dashboard` | ADMIN | Dashboard statistics |
| GET | `/api/admin/transactions/pending` | ADMIN | Pending approvals |
| POST | `/api/admin/transactions/{id}/approve` | ADMIN | Approve transaction |
| POST | `/api/admin/transactions/{id}/reject` | ADMIN | Reject transaction |
| GET | `/api/admin/users` | ADMIN | List all users |
| DELETE | `/api/admin/users/{id}` | ADMIN | Deactivate user |
| POST | `/api/rates/admin` | ADMIN | Create exchange rate |
| PUT | `/api/rates/admin/{id}` | ADMIN | Update exchange rate |
| DELETE | `/api/rates/admin/{id}` | ADMIN | Delete exchange rate |
| GET | `/api/admin/logs` | ADMIN | System audit logs |
| GET | `/api/users/me` | Bearer | My profile |
| PUT | `/api/users/me` | Bearer | Update profile |

---

## Project Structure

```
currency-convertor/
├── src/main/java/com/exchange/
│   ├── controller/        REST endpoints (thin layer, delegates to services)
│   ├── service/           Business logic, @Transactional boundaries
│   ├── repository/        Spring Data JPA repositories
│   ├── model/             JPA entities: User, ExchangeRate, Transaction, Log
│   ├── dto/
│   │   ├── request/       Inbound DTOs with Jakarta validation
│   │   └── response/      Outbound DTOs (entities never exposed directly)
│   ├── config/            SecurityConfig, OpenApiConfig, WebClientConfig
│   ├── filter/            JwtAuthFilter, RateLimitFilter, CorrelationIdFilter
│   ├── integration/       ExternalRateApiClient (primary + AlphaVantage fallback)
│   ├── exception/         GlobalExceptionHandler + custom exceptions
│   └── aspect/            LoggingAspect, PerformanceMonitorAspect
├── src/main/resources/
│   ├── application.yml          Base config
│   ├── application-local.yml    Local profile
│   ├── application-aws.yml      AWS profile (all values via env vars)
│   └── db/migration/            Flyway SQL migrations (V1–V4)
├── frontend/
│   ├── src/app/
│   │   ├── auth/          Login, Register, MFA components
│   │   ├── dashboard/     User dashboard (convert, history, profile)
│   │   ├── admin/         Admin dashboard (approvals, users, rates, logs)
│   │   └── shared/        Services, models, interceptors
│   ├── mock/server.js     Express mock backend for frontend-only dev
│   └── proxy.mock.json    Proxy config for mock mode
├── terraform/             AWS infrastructure (EB/ECS, RDS, ElastiCache, SES, CloudFront)
├── docs/                  Architecture, API contract, data model, requirements
├── prd.md                 Full developer guide
└── progress.md            Implementation status tracker
```

---

## Running Tests

```bash
# Backend unit + integration tests
mvn test

# Frontend unit tests
cd frontend && ng test

# Note: integration tests use Testcontainers (requires Docker)
```

---

## AWS Deployment

Two deployment paths are supported. The POC uses Elastic Beanstalk (no Docker, simplest path). Production targets ECS Fargate (containerized, auto-scaling, zero-downtime Blue/Green).

### Option A — Elastic Beanstalk (POC / Current)

Architecture: `CloudFront → Elastic Beanstalk (EC2 t3.micro, public subnet) → RDS + ElastiCache (private subnets)`

No Docker required. EB manages OS patching, JVM supervision, health checks, and CloudWatch logs automatically.

```bash
# 1. Provision infrastructure
cd terraform
terraform init
terraform apply   # creates VPC, RDS, ElastiCache, SES, S3, CloudFront, EB environment

# 2. Build the JAR
mvn clean package -Paws -DskipTests

# 3. Deploy to Elastic Beanstalk
eb init           # select region + application (one-time)
eb deploy         # uploads JAR, EB restarts the app

# 4. Deploy the frontend
cd frontend
npm run build:prod
aws s3 sync dist/frontend/ s3://<your-bucket-name>/ --delete
aws cloudfront create-invalidation --distribution-id <id> --paths "/*"
```

Set env vars in the EB console (Configuration → Software → Environment properties) or via `.ebextensions/env.config`. See the [full EB deployment guide](docs/migration-plan-elastic-beanstalk.md).

---

### Option B — ECS Fargate (Production Path)

Architecture: `CloudFront → ALB → ECS Fargate Cluster (2–8 tasks, private subnets) → RDS Multi-AZ + ElastiCache`

Requires Docker. Supports Blue/Green deployments, horizontal auto-scaling, and a full CI/CD pipeline.

```bash
# 1. Provision infrastructure
cd terraform
terraform init
terraform apply   # creates VPC, ECR, ECS cluster, ALB, RDS Multi-AZ, ElastiCache, SES, S3, CloudFront

# 2. Build and push the Docker image
mvn clean package -Paws -DskipTests
docker build -t currency-convertor .
docker tag currency-convertor:latest <account-id>.dkr.ecr.<region>.amazonaws.com/currency-convertor:latest
aws ecr get-login-password | docker login --username AWS --password-stdin <account-id>.dkr.ecr.<region>.amazonaws.com
docker push <account-id>.dkr.ecr.<region>.amazonaws.com/currency-convertor:latest

# 3. Update the ECS service (triggers a rolling / Blue/Green deploy)
aws ecs update-service --cluster currency-convertor --service backend --force-new-deployment

# 4. Deploy the frontend (same as EB option)
cd frontend
npm run build:prod
aws s3 sync dist/frontend/ s3://<your-bucket-name>/ --delete
aws cloudfront create-invalidation --distribution-id <id> --paths "/*"
```

Set secrets in AWS Secrets Manager and reference them from the ECS task definition. See the [full ECS Fargate deployment guide](docs/migration-plan-ecs-fargate.md).

---

### POC vs Production — Key Differences

| Area | Elastic Beanstalk (POC) | ECS Fargate (Prod) |
|---|---|---|
| Docker | Not required | Required |
| Compute | EC2 t3.micro (single instance) | 2–8 Fargate tasks, auto-scaling |
| Load balancer | None — direct public IP | ALB (~$18/month) |
| RDS | db.t3.micro, single-AZ | db.t3.medium, Multi-AZ |
| Redis | cache.t3.micro, 1 node | Replication group, 2 nodes, Multi-AZ |
| Deployments | `eb deploy` (rolling restart) | Blue/Green via CodeDeploy |
| Secrets | EB env vars / `.ebextensions` | AWS Secrets Manager |
| CI/CD | Manual | CodePipeline → CodeBuild → ECS deploy |

---

## Documentation

| File | Contents |
|---|---|
| [`prd.md`](prd.md) | Full developer guide — setup, architecture, deployment |
| [`progress.md`](progress.md) | Implementation status tracker |
| [`docs/migration-plan-elastic-beanstalk.md`](docs/migration-plan-elastic-beanstalk.md) | Step-by-step Elastic Beanstalk deployment (POC) |
| [`docs/migration-plan-ecs-fargate.md`](docs/migration-plan-ecs-fargate.md) | Step-by-step ECS Fargate deployment (Production) |
| [`docs/prod-vs-poc-infra.md`](docs/prod-vs-poc-infra.md) | POC vs Production infrastructure comparison |
| [`docs/api-contract.md`](docs/api-contract.md) | Complete REST API specification |
| [`docs/data-model.md`](docs/data-model.md) | Database schema and JPA entity design |
| [`docs/architecture-summary.md`](docs/architecture-summary.md) | System architecture overview |
| [`docs/functional-requirements.md`](docs/functional-requirements.md) | FR-01 through FR-12 |
