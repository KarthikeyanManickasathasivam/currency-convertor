# Cloud Migration Guide — XChange on AWS

This guide explains every AWS service the app uses, what role each plays, how they connect to each other, and the exact steps to deploy. All infrastructure is already defined in `terraform/`.

---

## Architecture Overview

```
Internet
    │
    ▼
┌─────────────────────────────────────────────────────────┐
│  CloudFront (CDN)                                       │
│  → serves Angular SPA from S3                          │
│  → forwards /api/* to API Gateway                      │
└─────────────────────────────────────────────────────────┘
    │ /api/*
    ▼
┌─────────────────────────────────────────────────────────┐
│  API Gateway (HTTP API)                                 │
│  → single $default route catches all requests          │
│  → proxies to Lambda                                   │
└─────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────┐
│  AWS Lambda  [inside VPC — private subnets]             │
│  Spring Boot JAR + SnapStart (Java 17)                 │
│  Profile: aws  │  Memory: 1024 MB  │  Timeout: 30s    │
└──────┬──────────────────────┬───────────────────────────┘
       │                      │
       ▼                      ▼
┌─────────────┐      ┌──────────────────┐
│  RDS        │      │  ElastiCache     │
│  PostgreSQL │      │  Redis           │
│  (private   │      │  (private        │
│  subnet)    │      │  subnet)         │
└─────────────┘      └──────────────────┘
                              
       ┌──────────────────────┐
       │  Amazon SES          │
       │  (email OTP +        │
       │  notifications)      │
       └──────────────────────┘
```

**Key rule: Lambda is inside the VPC. RDS and ElastiCache are only reachable from inside the VPC. SES is a managed service reached via the internet (Lambda uses a NAT Gateway or VPC endpoint for this).**

---

## Service-by-Service Breakdown

---

### 1. Amazon RDS — PostgreSQL 16

**What it does in this app:** Stores all persistent data — users, transactions, exchange rates, audit logs.

**Why RDS over a self-managed database:** Automated backups (7-day retention), Multi-AZ failover, storage auto-scaling, and encryption at rest — all configured in `terraform/rds.tf`.

**Configuration in `application-aws.yml`:**
```yaml
spring:
  datasource:
    url: ${DB_URL}         # set in Lambda env vars
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      minimum-idle: 1
      maximum-pool-size: 5  # keep low — Lambda concurrency controls scaling
```

**Connection string format:**
```
jdbc:postgresql://<rds-endpoint>:5432/currency_exchange
```
The `<rds-endpoint>` looks like: `xchange-postgres.abc123.ap-south-1.rds.amazonaws.com`

**Where to get the endpoint:** After `terraform apply`, run:
```bash
terraform output rds_endpoint
```

**Network access:** RDS security group (`xchange-rds-sg`) allows port 5432 **only from the Lambda security group**. It is not publicly accessible.

**Flyway migrations:** Run automatically at Spring Boot startup. No manual SQL needed.

**What to change in code:** Nothing. The `aws` Spring profile already has the correct JDBC config.

---

### 2. Amazon ElastiCache — Redis 7

**What it does in this app:**
- Caches exchange rates (5-minute TTL) — the `rates` cache key used by `@Cacheable` in `ExchangeRateService`
- Stores OTP codes (5-minute TTL) — used by `RedisOtpService` (`@Profile("aws")`)
- JWT token blacklist (for logout) — used by `RedisTokenBlacklistService` (`@Profile("aws")`)
- Rate limiting counters — used by `RateLimitFilter` with `RedisRateLimiterService` (`@Profile("aws")`)

**Why Redis:** The `local` profile uses `ConcurrentHashMap` and `ConcurrentMapCacheManager` (in-memory). Those don't survive Lambda cold starts and can't be shared across concurrent Lambda instances. Redis solves both problems.

**Configuration in `application-aws.yml`:**
```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT:6379}
      ssl:
        enabled: true      # ElastiCache in-transit encryption
      timeout: 2000ms
  cache:
    type: redis
    redis:
      time-to-live: 300000  # 5 minutes in ms
```

**Connection:** Redis uses a hostname, not a JDBC URL. The host looks like:  
`xchange-redis.abc123.0001.apse1.cache.amazonaws.com`

**Where to get the host:** After `terraform apply`, run:
```bash
terraform output -json | grep redis
```
Or in the AWS Console: ElastiCache → Clusters → click cluster → copy the **Primary Endpoint**.

**Network access:** ElastiCache security group (`xchange-elasticache-sg`) allows port 6379 **only from the Lambda security group**. Never expose Redis publicly.

**What to change in code:** Nothing. Spring Boot automatically activates `RedisOtpService`, `RedisTokenBlacklistService`, and Redis cache when `SPRING_PROFILES_ACTIVE=aws`.

---

### 3. Amazon SES — Simple Email Service

**What it does in this app:**
- Sends OTP codes to users at login
- Sends "Transaction Pending Approval" notification to admin
- Sends "Approved/Rejected" notification to the user

**Why SES over a generic SMTP provider:** Native AWS integration, pay-per-email pricing (~$0.10/1000 emails), built-in bounce/complaint handling.

**Configuration in `application-aws.yml`:**
```yaml
spring:
  mail:
    host: email-smtp.ap-south-1.amazonaws.com
    port: 587
    username: ${SES_SMTP_USERNAME}
    password: ${SES_SMTP_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
```

**How to set up SES (must be done manually — not fully automatable via Terraform):**

**Step 1 — Verify your sender identity**

Option A — Verify a domain (recommended for production):
1. AWS Console → SES → Verified Identities → Create Identity → Domain
2. Enter `yourdomain.com`
3. SES gives you DNS records (CNAME for DKIM, TXT for domain verification)
4. Add those records in your DNS provider (Route 53, Cloudflare, etc.)
5. Wait for Status = Verified (can take up to 72 hours for DNS propagation)

Option B — Verify a single email address (easiest for testing):
1. AWS Console → SES → Verified Identities → Create Identity → Email Address
2. Enter your email — you'll receive a verification link
3. Click it → status becomes Verified

**Step 2 — Create SMTP credentials**
1. AWS Console → SES → SMTP Settings → Create SMTP Credentials
2. This creates an IAM user and generates SMTP username/password
3. Save these immediately — the password is only shown once
4. These become `SES_SMTP_USERNAME` and `SES_SMTP_PASSWORD` in Lambda env vars

**Step 3 — Move out of SES sandbox (for production)**
- New AWS accounts are in SES Sandbox: can only send to verified emails
- Submit a support request: AWS Console → SES → Account Dashboard → Request Production Access
- Fill in: use case, expected volume, bounce handling — approval takes 24–48 hours
- Until approved, add every recipient email as a verified identity for testing

**Network:** SES SMTP endpoint is public — Lambda calls it over the internet via the NAT Gateway (not through VPC).

**What to change in code:** The `SesEmailService` class (`@Profile("aws")`) is already implemented and will activate automatically.

---

### 4. AWS Lambda — Spring Boot JAR

**What it does:** Runs the entire Spring Boot backend as a serverless function.

**How it works:** API Gateway receives an HTTP request and invokes the Lambda. The Lambda Web Adapter (or the container handler) translates the API Gateway event into a standard HTTP servlet request that Spring Boot understands. Spring Boot handles it normally and returns a response.

**Lambda settings (in `terraform/lambda.tf`):**
- Runtime: `java17`
- Memory: 1024 MB (Spring Boot needs at least 768 MB; 1024 MB is reliable)
- Timeout: 30 seconds
- SnapStart: enabled on Published Versions (eliminates cold start — takes a JVM snapshot after init)

**How to build the JAR:**
```bash
# From project root
mvn clean package -DskipTests

# The deployable JAR is at:
target/currency-exchange-1.0.0.jar
```

**How to deploy:**
```bash
# Via Terraform (set the path to the JAR)
terraform apply -var="lambda_jar_path=../target/currency-exchange-1.0.0.jar"

# Or update just the Lambda function code after initial deploy:
aws lambda update-function-code \
  --function-name xchange-api \
  --zip-file fileb://target/currency-exchange-1.0.0.jar
```

**SnapStart — publish a version after each deploy:**
```bash
aws lambda publish-version --function-name xchange-api
```
SnapStart only works on published versions, not `$LATEST`. API Gateway should point to the alias/version, not `$LATEST`.

**Environment variables on Lambda** (all set via Terraform or AWS Console):

| Variable | Value | Where it comes from |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `aws` | Hardcoded in terraform/lambda.tf |
| `DB_URL` | `jdbc:postgresql://<rds-endpoint>/currency_exchange` | Terraform output |
| `DB_USERNAME` | your RDS username | terraform.tfvars |
| `DB_PASSWORD` | your RDS password | terraform.tfvars |
| `REDIS_HOST` | `<elasticache-endpoint>` | Terraform output |
| `REDIS_PORT` | `6379` | Hardcoded |
| `SES_SMTP_USERNAME` | from SES SMTP credentials | Manual setup |
| `SES_SMTP_PASSWORD` | from SES SMTP credentials | Manual setup |
| `JWT_PRIVATE_KEY` | RSA private key (base64) | Generated (see below) |
| `JWT_PUBLIC_KEY` | RSA public key (base64) | Generated (see below) |
| `EMAIL_FROM_ADDRESS` | `no-reply@yourdomain.com` | Your verified SES domain |
| `TRANSACTION_ADMIN_EMAIL` | `admin@yourdomain.com` | Your admin's email |
| `ALPHA_VANTAGE_API_KEY` | your API key | alphavantage.co |

**Generating JWT RSA keys:**
```bash
# Generate private key
openssl genrsa -out private.pem 2048

# Extract public key
openssl rsa -in private.pem -pubout -out public.pem

# Base64-encode for Lambda env var (single line)
base64 -w 0 private.pem   # → JWT_PRIVATE_KEY value
base64 -w 0 public.pem    # → JWT_PUBLIC_KEY value
```

**Network:** Lambda is in **private subnets** (`10.0.1.0/24`, `10.0.2.0/24`). It can reach RDS and ElastiCache because they're in the same VPC. It reaches SES and Alpha Vantage via a NAT Gateway (public internet traffic from private subnets).

> **Important:** If you don't add a NAT Gateway, Lambda in a private subnet cannot reach the internet (SES, Alpha Vantage API). Add one — `terraform/vpc.tf` should include a NAT Gateway resource if it doesn't already.

---

### 5. API Gateway (HTTP API v2)

**What it does:** The public entry point. Receives all HTTPS requests from the internet and invokes the Lambda function.

**Type used:** HTTP API (v2) — cheaper and faster than REST API (v1). Sufficient for this use case.

**Route:** A single `$default` catch-all route forwards everything to Lambda. Spring Boot's own `@RequestMapping` handles routing internally.

**CORS:** Configured in `terraform/lambda.tf` — update `allow_origins` to your actual CloudFront domain before applying:
```hcl
cors_configuration {
  allow_origins = ["https://your-cloudfront-domain.cloudfront.net"]
  ...
}
```

**Where to get the API Gateway URL:**
```bash
terraform output api_gateway_url
# Returns: https://abc123xyz.execute-api.ap-south-1.amazonaws.com
```

**This URL goes into the Angular frontend** as the API base URL (see Frontend section below).

---

### 6. VPC — Networking

**Why Lambda needs to be in the VPC:** RDS and ElastiCache are not publicly accessible. Lambda must be inside the same VPC to reach them.

**Subnet layout:**

| Subnet | CIDR | Purpose |
|---|---|---|
| private-a | `10.0.1.0/24` | Lambda, RDS, ElastiCache (AZ-a) |
| private-b | `10.0.2.0/24` | Lambda, RDS, ElastiCache (AZ-b) |
| public-a | `10.0.10.0/24` | NAT Gateway (needed for Lambda → internet) |

**Security groups and what they allow:**

```
Lambda SG (xchange-lambda-sg)
  └─ Outbound: all traffic (0.0.0.0/0)
  └─ Inbound: nothing (Lambda is invoked by API Gateway, not by inbound network traffic)

RDS SG (xchange-rds-sg)
  └─ Inbound port 5432: only from Lambda SG
  └─ Outbound: nothing needed

ElastiCache SG (xchange-elasticache-sg)
  └─ Inbound port 6379: only from Lambda SG
  └─ Outbound: nothing needed
```

**NAT Gateway (add to `terraform/vpc.tf` if missing):**
```hcl
resource "aws_eip" "nat" { domain = "vpc" }

resource "aws_nat_gateway" "main" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public_a.id
}

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id
  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.main.id
  }
}

resource "aws_route_table_association" "private_a" {
  subnet_id      = aws_subnet.private_a.id
  route_table_id = aws_route_table.private.id
}

resource "aws_route_table_association" "private_b" {
  subnet_id      = aws_subnet.private_b.id
  route_table_id = aws_route_table.private.id
}
```

---

### 7. S3 + CloudFront — Frontend Hosting

**What it does:** Serves the Angular SPA as static files. CloudFront is the CDN — it caches assets globally and handles HTTPS.

**This is not in the current Terraform files — add these resources:**

`terraform/frontend.tf`:
```hcl
resource "aws_s3_bucket" "frontend" {
  bucket = "${var.project_name}-frontend"
}

resource "aws_s3_bucket_public_access_block" "frontend" {
  bucket                  = aws_s3_bucket.frontend.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_cloudfront_origin_access_control" "frontend" {
  name                              = "${var.project_name}-oac"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_distribution" "frontend" {
  origin {
    domain_name              = aws_s3_bucket.frontend.bucket_regional_domain_name
    origin_id                = "S3-${var.project_name}"
    origin_access_control_id = aws_cloudfront_origin_access_control.frontend.id
  }

  enabled             = true
  default_root_object = "index.html"

  default_cache_behavior {
    target_origin_id       = "S3-${var.project_name}"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    forwarded_values {
      query_string = false
      cookies { forward = "none" }
    }
  }

  # Handle Angular client-side routing — return index.html for 404s
  custom_error_response {
    error_code         = 404
    response_code      = 200
    response_page_path = "/index.html"
  }

  restrictions {
    geo_restriction { restriction_type = "none" }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }
}

output "cloudfront_url" {
  value = "https://${aws_cloudfront_distribution.frontend.domain_name}"
}
```

**How to build and deploy the frontend:**
```bash
# 1. Set the real API URL in environment.prod.ts
#    (replace with your API Gateway URL from terraform output)

# 2. Build
cd frontend
ng build --configuration production

# 3. Upload to S3
aws s3 sync dist/currency-exchange-frontend/ s3://xchange-frontend/ --delete

# 4. Invalidate CloudFront cache after each deploy
aws cloudfront create-invalidation \
  --distribution-id <your-distribution-id> \
  --paths "/*"
```

---

## Code Changes Required Before Cloud Deployment

### 1. Update `environment.prod.ts` with the real API URL

File: `frontend/src/environments/environment.prod.ts`
```typescript
export const environment = {
  production: true,
  apiUrl: 'https://<api-gateway-id>.execute-api.ap-south-1.amazonaws.com/api'
  //       ^^^^ replace with actual API Gateway URL from terraform output
};
```

### 2. Update CORS in `terraform/lambda.tf`

Replace the placeholder CloudFront domain:
```hcl
allow_origins = ["https://<your-cloudfront-domain>.cloudfront.net"]
```

### 3. Update SES domain in `terraform/ses.tf`

Replace the placeholder domain:
```hcl
resource "aws_ses_domain_identity" "main" {
  domain = "yourdomain.com"  # ← replace with your actual domain
}
```

### 4. Update `EMAIL_FROM_ADDRESS` and `TRANSACTION_ADMIN_EMAIL`

Set these in `terraform/lambda.tf` environment variables block:
```hcl
EMAIL_FROM_ADDRESS        = "no-reply@yourdomain.com"
TRANSACTION_ADMIN_EMAIL   = "admin@yourdomain.com"
```

### 5. Create `terraform/terraform.tfvars` for secrets

```hcl
# terraform/terraform.tfvars  (DO NOT commit this file — add to .gitignore)
db_username           = "xchange_admin"
db_password           = "YourSecurePassword123!"
ses_smtp_username     = "AKIAIOSFODNN7EXAMPLE"
ses_smtp_password     = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
jwt_private_key       = "<base64-encoded-private.pem>"
jwt_public_key        = "<base64-encoded-public.pem>"
alpha_vantage_api_key = "YOUR_API_KEY"
```

### 6. Add NAT Gateway to `terraform/vpc.tf` (if missing)

See the NAT Gateway block in the VPC section above. Without it, Lambda in a private subnet cannot call SES or Alpha Vantage.

---

## Deployment Sequence (Order Matters)

Run these steps in order. Some services depend on others being ready first.

```
Step 1 ─── VPC + Networking
Step 2 ─── RDS PostgreSQL        (needs VPC + subnets)
Step 3 ─── ElastiCache Redis     (needs VPC + subnets)
Step 4 ─── SES verification      (manual + DNS propagation — start early)
Step 5 ─── Build Spring Boot JAR
Step 6 ─── Lambda + API Gateway  (needs VPC, RDS endpoint, Redis endpoint, SES creds, JWT keys)
Step 7 ─── Build Angular + S3    (needs API Gateway URL)
Step 8 ─── CloudFront            (needs S3 bucket)
Step 9 ─── Update CORS in API GW (needs CloudFront URL)
```

### Full deployment commands

```bash
# ── 1. One-time init ────────────────────────────────────────────
cd terraform
terraform init

# ── 2. Deploy all infrastructure (except frontend bucket) ───────
terraform apply

# Note the outputs:
#   api_gateway_url  → used in environment.prod.ts
#   rds_endpoint     → confirm it's used in lambda.tf
#   ses_dkim_tokens  → add these as CNAME records in your DNS

# ── 3. Add DKIM records to DNS (from ses_dkim_tokens output) ────
# Do this immediately — DNS propagation takes time
# In your DNS provider add 3 CNAME records like:
#   abc123._domainkey.yourdomain.com → abc123.dkim.amazonses.com

# ── 4. Build the backend ────────────────────────────────────────
cd ..
mvn clean package -DskipTests

# ── 5. Deploy Lambda code ───────────────────────────────────────
aws lambda update-function-code \
  --function-name xchange-api \
  --zip-file fileb://target/currency-exchange-1.0.0.jar

# Wait for update to complete
aws lambda wait function-updated --function-name xchange-api

# Publish a version (required for SnapStart)
aws lambda publish-version --function-name xchange-api

# ── 6. Build and deploy frontend ────────────────────────────────
cd frontend

# Edit environment.prod.ts first — set apiUrl to API Gateway URL

npm run build:prod
aws s3 sync dist/currency-exchange-frontend/ s3://xchange-frontend/ --delete

# ── 7. Invalidate CDN cache ─────────────────────────────────────
aws cloudfront create-invalidation \
  --distribution-id <DISTRIBUTION_ID> \
  --paths "/*"
```

---

## Verification Checklist

After deployment, verify each layer in order:

```
□ RDS
  - Can Lambda reach it?
  - Test: invoke Lambda with a simple health endpoint request
  - Flyway migrations ran? Check logs in CloudWatch

□ ElastiCache  
  - Can Lambda reach it?
  - Test: call GET /api/rates twice — second call should hit cache (check CloudWatch logs for "served from cache")

□ SES
  - Domain verified? (SES Console → Verified Identities → Status = Verified)
  - SMTP credentials working? (try login flow — OTP email should arrive)
  - Not in sandbox? (if still in sandbox, add recipient emails as verified identities)

□ Lambda
  - No cold start errors? (check CloudWatch → Log Groups → /aws/lambda/xchange-api)
  - SnapStart enabled? (Lambda Console → Version → Snap Start = On)
  - Environment variables all set? (Lambda Console → Configuration → Environment Variables)

□ API Gateway
  - Returns 200 for GET /api/rates?
  - Returns 401 for GET /api/users/me without token?
  - CORS headers correct for CloudFront origin?

□ Frontend
  - CloudFront URL loads the app?
  - Login → MFA → Dashboard flow works end to end?
  - API calls use HTTPS (not HTTP)?
  - No mixed-content warnings in browser console?
```

---

## Cost Estimate (ap-south-1, low traffic)

| Service | Config | Est. monthly cost |
|---|---|---|
| Lambda | 1M requests, 1024 MB, avg 500ms | ~$2 |
| API Gateway (HTTP) | 1M requests | ~$1 |
| RDS PostgreSQL | db.t3.micro, Multi-AZ, 20 GB | ~$30 |
| ElastiCache Redis | cache.t3.micro, single node | ~$15 |
| NAT Gateway | 10 GB data | ~$5 |
| S3 + CloudFront | 1 GB storage, 10 GB transfer | ~$2 |
| SES | 10,000 emails | ~$1 |
| **Total** | | **~$56/month** |

> Biggest cost driver: RDS Multi-AZ. Switch to `multi_az = false` and `db.t3.micro` single-AZ for dev/staging to cut to ~$15/month.

---

## Common Problems and Fixes

**Lambda times out on first request (cold start)**
- Cause: SnapStart not publishing a version, or pointing API Gateway at `$LATEST`
- Fix: Run `aws lambda publish-version`, update API Gateway integration to point at the version ARN

**Lambda cannot connect to RDS**
- Cause: NAT Gateway missing, or Lambda not in the VPC, or wrong security group
- Fix: Confirm Lambda VPC config in Console → Lambda → Configuration → VPC. Confirm `xchange-rds-sg` has inbound rule from `xchange-lambda-sg`

**SES emails not sending**
- Cause: Account still in SES Sandbox, or domain not verified
- Fix: Verify sender domain and all recipient emails in SES Console, or request production access

**Angular app gets 403 from CloudFront on page refresh**
- Cause: CloudFront doesn't know about Angular client-side routes
- Fix: Add the `custom_error_response` block in the CloudFront Terraform resource (shown above) — returns `index.html` for 404s

**CORS errors in browser**
- Cause: `allow_origins` in API Gateway doesn't include CloudFront URL
- Fix: Update `cors_configuration` in `terraform/lambda.tf`, run `terraform apply`, do a full re-deploy

**Flyway migration fails on startup**
- Cause: DB not reachable, or migration already partially applied
- Fix: Check CloudWatch logs for the error. For a clean slate during testing: connect to RDS directly via a bastion host and run `DROP DATABASE currency_exchange; CREATE DATABASE currency_exchange;` then redeploy
