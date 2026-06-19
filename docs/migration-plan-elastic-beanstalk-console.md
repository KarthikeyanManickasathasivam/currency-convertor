# AWS Migration Plan — Elastic Beanstalk (AWS Console, No Terraform)
### POC / Assignment Version

> This plan creates every AWS resource manually through the AWS Console (and occasional CLI commands). No Terraform is required. Every place where a real deployment would do something different is called out with a **🔴 PROD** marker. Everything you actually need to do is marked **✅ POC**.

---

## Architecture

```
Browser
  │
  ▼
CloudFront (HTTPS CDN)
  ├── /          → S3 bucket (Angular SPA)
  └── /api/*     → Elastic Beanstalk URL (Spring Boot on EC2 t3.micro)
                        │
              ┌─────────┼──────────┐
              ▼         ▼          ▼
            RDS       ElastiCache  Internet (via public subnet IGW)
         PostgreSQL     Redis      └── SES, Alpha Vantage API
         (private)    (private)
```

---

## Prerequisites

### Tools to install locally

| Tool | Version | Install |
|---|---|---|
| AWS CLI | v2 | https://aws.amazon.com/cli/ |
| EB CLI | latest | `pip install awsebcli` |
| Java 21 | already at `C:\Program Files\OpenLogic\jdk-21.0.3.9-hotspot` | — |
| Maven | 3.9.x | https://maven.apache.org/download.cgi |
| Node / npm | 18+ | https://nodejs.org/ |
| Angular CLI | 18 | `npm install -g @angular/cli` |

### AWS credentials

```bash
aws configure
# AWS Access Key ID: <your key>
# AWS Secret Access Key: <your secret>
# Default region name: ap-south-1
# Default output format: json
```

Verify:
```bash
aws sts get-caller-identity
```

---

## Step 1 — One-Time Manual Preparation

These must be done before creating any AWS resources.

### 1a. Verify email addresses in SES

1. AWS Console → **Simple Email Service** → **Verified identities** → **Create identity**
2. Choose **Email address**, enter `karthikeyan.manickasathasivam@cognizant.com`
3. Click the verification link in your inbox
4. Repeat for any additional recipient email addresses

✅ POC: Both `EMAIL_FROM_ADDRESS` and `TRANSACTION_ADMIN_EMAIL` must be verified.
🔴 PROD: Verify the entire domain and request SES production access (sandbox removal).

### 1b. Create SES SMTP credentials

1. AWS Console → **SES** → **SMTP Settings** → **Create SMTP Credentials**
2. Enter an IAM user name (e.g., `xchange-ses-smtp`)
3. Click **Create** — a CSV with `smtp_username` and `smtp_password` will download
4. **Save this CSV** — you will need these values in Step 6

### 1c. Generate JWT RSA keys

Run in Git Bash or WSL:

```bash
# genpkey produces PKCS8 format (BEGIN PRIVATE KEY) — required by JwtService.
# Do NOT use `openssl genrsa` here — it produces PKCS1 (BEGIN RSA PRIVATE KEY)
# which causes "extra data at the end" / InvalidKeySpecException on startup.
openssl genpkey -algorithm RSA -out private_pkcs8.pem -pkeyopt rsa_keygen_bits:2048
openssl pkey -in private_pkcs8.pem -pubout -out public.pem

# Verify formats:
head -1 private_pkcs8.pem   # must say: -----BEGIN PRIVATE KEY-----
head -1 public.pem          # must say: -----BEGIN PUBLIC KEY-----

# Extract the INNER base64 only (DER body — no headers, no newlines).
# ⚠️  Do NOT use `base64 -w 0 private_pkcs8.pem` — that wraps the entire PEM
# in another layer of base64 and causes "extra data at the end" errors on Java 25+.
grep -v "^-----" private_pkcs8.pem | tr -d '\n'   # → JWT_PRIVATE_KEY value
grep -v "^-----" public.pem | tr -d '\n'           # → JWT_PUBLIC_KEY value
```

Keep `private_pkcs8.pem` safe — never commit it.

> **If you already have keys**, re-generate fresh ones rather than converting. The
> `openssl pkcs8 -topk8` conversion sometimes preserves trailing ASN.1 attributes
> that Java 25's strict DER parser rejects with "extra data at the end".

### 1d. Get an Alpha Vantage API key

Free tier: https://www.alphavantage.co/support/#api-key

---

## Step 2 — Create the VPC and Networking

> ✅ POC: Create a custom VPC so that RDS and ElastiCache stay in private subnets while the EB EC2 instance lives in the public subnet (no NAT Gateway needed).

### 2a. Create the VPC

1. AWS Console → **VPC** → **Your VPCs** → **Create VPC**
2. Fill in:
   - **Name tag:** `xchange-vpc`
   - **IPv4 CIDR:** `10.0.0.0/16`
   - **Tenancy:** Default
3. Click **Create VPC**

### 2b. Create subnets

You need **one public subnet** (for EB) and **two private subnets** (for RDS and ElastiCache — RDS requires at least two AZs for a subnet group).

Go to **VPC → Subnets → Create subnet** and create each one:

| Name | CIDR | Availability Zone | Type |
|---|---|---|---|
| `xchange-public-a` | `10.0.10.0/24` | `ap-south-1a` | Public |
| `xchange-private-a` | `10.0.1.0/24` | `ap-south-1a` | Private |
| `xchange-private-b` | `10.0.2.0/24` | `ap-south-1b` | Private |

For each: select the VPC you just created, enter the name tag, CIDR, and AZ.

**Enable auto-assign public IP for the public subnet:**
- Select `xchange-public-a` → **Actions** → **Edit subnet settings**
- Check **Enable auto-assign public IPv4 address** → **Save**

### 2c. Create an Internet Gateway

1. **VPC → Internet Gateways → Create internet gateway**
2. Name: `xchange-igw` → **Create**
3. Select it → **Actions → Attach to VPC** → select `xchange-vpc` → **Attach**

### 2d. Create a public route table

1. **VPC → Route Tables → Create route table**
2. Name: `xchange-public-rt`, VPC: `xchange-vpc` → **Create**
3. Select it → **Routes** tab → **Edit routes** → **Add route**
   - Destination: `0.0.0.0/0`
   - Target: select **Internet Gateway** → `xchange-igw`
   - **Save changes**
4. **Subnet associations** tab → **Edit subnet associations** → select `xchange-public-a` → **Save**

> The two private subnets can use the default (main) route table — they only need to reach each other and the EB instance within the VPC, not the internet.

---

## Step 3 — Create Security Groups

Go to **VPC → Security Groups → Create security group** for each one below.

### 3a. Elastic Beanstalk EC2 security group

- **Name:** `xchange-beanstalk-sg`
- **VPC:** `xchange-vpc`
- **Inbound rules:**

| Type | Protocol | Port | Source | Description |
|---|---|---|---|---|
| HTTP | TCP | 80 | `0.0.0.0/0` | HTTP from CloudFront |
| Custom TCP | TCP | 5000 | `0.0.0.0/0` | Spring Boot / EB Corretto default port |

- **Outbound:** leave default (all traffic allowed)
- Click **Create security group**

### 3b. RDS security group

- **Name:** `xchange-rds-sg`
- **VPC:** `xchange-vpc`
- **Inbound rules:**

| Type | Protocol | Port | Source | Description |
|---|---|---|---|---|
| PostgreSQL | TCP | 5432 | `xchange-beanstalk-sg` (select the SG) | PostgreSQL from EB EC2 |

- **Outbound:** leave default
- Click **Create security group**

### 3c. ElastiCache security group

- **Name:** `xchange-elasticache-sg`
- **VPC:** `xchange-vpc`
- **Inbound rules:**

| Type | Protocol | Port | Source | Description |
|---|---|---|---|---|
| Custom TCP | TCP | 6379 | `xchange-beanstalk-sg` (select the SG) | Redis from EB EC2 |

- **Outbound:** leave default
- Click **Create security group**

---

## Step 4 — Create RDS PostgreSQL

> ⚠️ **You must complete Step 4a before Step 4b.** The RDS creation wizard will ask you to select a DB subnet group — it will not let you proceed without one.

### 4a. Create a DB subnet group

> **Why 2 AZs?** AWS requires every DB subnet group to span at least 2 availability zones — this is a hard AWS requirement even when the RDS instance itself is single-AZ (no Multi-AZ). You created these two private subnets in Step 2b specifically for this purpose.

1. AWS Console → **RDS** → **Subnet groups** (left sidebar) → **Create DB subnet group**
2. Fill in:
   - **Name:** `xchange-db-subnet-group`
   - **Description:** `Private subnets for RDS`
   - **VPC:** `xchange-vpc`
   - **Availability zones:** select `ap-south-1a` **and** `ap-south-1b`
   - **Subnets:** select `xchange-private-a` (10.0.1.0/24) and `xchange-private-b` (10.0.2.0/24)
3. Click **Create**
4. ✅ Confirm it appears in the subnet group list before continuing

### 4b. Create the RDS instance

> **Prerequisite:** Step 4a must be done first. The `xchange-db-subnet-group` you just created will appear in the DB subnet group dropdown below.

1. **RDS → Databases → Create database**
2. Settings:

| Setting | Value |
|---|---|
| Creation method | Standard create |
| Engine | PostgreSQL |
| Engine version | 16.3 (or latest 16.x) |
| Template | **Free tier** ✅ POC |
| DB instance identifier | `xchange-postgres` |
| Master username | `xchange_admin` |
| Master password | (choose 16+ char password, save it) | - i kept as Postgrespass-1
| Instance class | `db.t3.micro` ✅ POC |
| Storage type | gp2 |
| Allocated storage | 20 GB |
| Storage autoscaling | Disable |
| Multi-AZ | **No** ✅ POC |
| VPC | `xchange-vpc` |
| DB subnet group | `xchange-db-subnet-group` |
| Public access | **No** |
| VPC security group | Remove default → add `xchange-rds-sg` |
| Database name | `currency_exchange` |
| Backup retention | 1 day |
| Deletion protection | **Disable** ✅ POC |

3. Click **Create database** — takes ~5 minutes.
4. Once available, copy the **Endpoint** (e.g., `xchange-postgres.xxxx.ap-south-1.rds.amazonaws.com`) — needed in Step 6.

🔴 PROD: `db.t3.medium`, Multi-AZ, deletion protection enabled, 7-day backup retention.

---

## Step 5 — Create ElastiCache Redis

### 5a. Create a cache subnet group

1. **ElastiCache → Subnet Groups → Create subnet group**
2. Fill in:
   - **Name:** `xchange-cache-subnet-group`
   - **VPC:** `xchange-vpc`
   - **Availability zones:** `ap-south-1a`, `ap-south-1b`
   - **Subnets:** select `xchange-private-a` and `xchange-private-b`
3. Click **Create**

### 5b. Create the Redis cluster

1. **ElastiCache → Redis OSS caches → Create Redis OSS cache**
2. Settings:

| Setting | Value |
|---|---|
| Deployment option | **Design your own cache** |
| Creation method | **Standard create** |
| Cluster mode | Disabled ✅ POC |
| Name | `xchange-redis` |
| Engine version | 7.1 |
| Port | 6379 |
| Parameter group | `default.redis7` |
| Node type | `cache.t3.micro` ✅ POC |
| Number of replicas | **0** ✅ POC |
| Multi-AZ | **Disabled** ✅ POC |
| Subnet group | `xchange-cache-subnet-group` |
| Security groups | `xchange-elasticache-sg` |
| Encryption at rest | Disable ✅ POC |
| Encryption in transit | **Disable** ✅ POC (simplifies Redis URL — no TLS) |
| Backup | Disable ✅ POC |

3. Click **Create** — takes ~3 minutes.
4. Once available, copy the **Primary endpoint** hostname (without the `:6379` port) — needed in Step 6.

🔴 PROD: Replication group with 2 nodes, Multi-AZ, encryption in-transit and at-rest enabled.

---

## Step 6 — Create the IAM Role for EB EC2

Elastic Beanstalk EC2 instances need an instance profile to call AWS services.

1. AWS Console → **IAM → Roles → Create role**
2. **Trusted entity type:** AWS service
3. **Use case:** EC2 → **Next**
4. Attach these managed policies (search and check each):
   - `AWSElasticBeanstalkWebTier`
   - `AWSElasticBeanstalkWorkerTier`

   > `AWSElasticBeanstalkMulticontainerDocker` is NOT needed — this is a Java/Corretto deployment, no Docker.
5. **Role name:** `xchange-eb-ec2-role` → **Create role**
6. Now create the **Instance Profile**:
   - Go to **IAM → Instance profiles** (if not visible, use the CLI):
   ```bash
   aws iam create-instance-profile --instance-profile-name xchange-eb-ec2-profile
   aws iam add-role-to-instance-profile \
     --instance-profile-name xchange-eb-ec2-profile \
     --role-name xchange-eb-ec2-role
   ```

> **Note:** When you create an EB environment via the Console wizard, it usually offers to create the instance profile automatically. If the wizard creates `aws-elasticbeanstalk-ec2-role` for you, you can use that instead of the manual steps above.

---

## Step 7 — Create the Elastic Beanstalk Application and Environment

### 7a. Confirm the available Corretto 21 solution stack name

Before creating the environment, find the exact current stack name:

```bash
aws elasticbeanstalk list-available-solution-stacks \
  --query "SolutionStacks[?contains(@,'Corretto 21')]" \
  --output table
```

Note the most recent result (e.g., `64bit Amazon Linux 2023 v4.3.0 running Corretto 21`).

### 7b. Create the application

1. AWS Console → **Elastic Beanstalk → Applications → Create application**
2. **Application name:** `xchange-app`
3. Click **Create**

### 7c. Create the environment

1. Inside `xchange-app` → **Create environment**
2. **Environment tier:** Web server environment
3. **Environment name:** `xchange-env`
4. **Domain:** leave as default (auto-generated CNAME)
5. **Platform:**
   - Platform: **Java**
   - Platform branch: **Corretto 21 running on 64bit Amazon Linux 2023**
   - Platform version: (use the latest)
6. **Application code:** Upload your code (you will do this in Step 9 — for now choose **Sample application** to let the environment initialize)
7. Click **Next** → **Configure more options**

### 7d. Configure the environment (in "Configure more options")

Switch to **High availability** preset first, then immediately switch back to **Single instance** to get all the settings panels:

**Capacity (Instances):**
- Environment type: **Single instance**
- Instance type: `t3.micro`

**Instances:**
- EC2 key pair: (optional — add if you want SSH access for debugging)
- IAM instance profile: `xchange-eb-ec2-profile` (or `aws-elasticbeanstalk-ec2-role` if auto-created)

**Networking:**
- VPC: `xchange-vpc`
- Public IP address: **Enabled** ✅
- Instance subnets: `xchange-public-a`
- Database subnets: (leave empty — we created RDS separately)

**Security groups:**
- EC2 security groups: add `xchange-beanstalk-sg`

**Updates and deployments:**
- Deployment policy: All at once ✅ POC

**Environment properties** — add all of the following key-value pairs:

| Key | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `aws` |
| `SERVER_PORT` | `5000` |
| `JAVA_TOOL_OPTIONS` | `-Xms256m -Xmx512m` |
| `DB_URL` | `jdbc:postgresql://<rds-endpoint>/currency_exchange` |
| `DB_USERNAME` | `xchange_admin` |
| `DB_PASSWORD` | `<your rds password>` |
| `REDIS_HOST` | `<elasticache-primary-endpoint>` |
| `REDIS_PORT` | `6379` |
| `SES_SMTP_USERNAME` | `<from CSV in Step 1b>` |
| `SES_SMTP_PASSWORD` | `<from CSV in Step 1b>` |
| `EMAIL_FROM_ADDRESS` | `karthikeyan.manickasathasivam@cognizant.com` |
| `TRANSACTION_ADMIN_EMAIL` | `karthikeyan.manickasathasivam@cognizant.com` |
| `JWT_PRIVATE_KEY` | `<base64 output from Step 1c>` |
| `JWT_PUBLIC_KEY` | `<base64 output from Step 1c>` |
| `ALPHA_VANTAGE_API_KEY` | `<your key from Step 1d>` |
| `APP_CORS_ALLOWED_ORIGINS` | `*` (update after Step 11 with exact CloudFront URL) |
| `SES_SMTP_HOST` | `email-smtp.ap-south-1.amazonaws.com` (default for ap-south-1 — omit if using Mumbai region) |

> **Tip:** These environment properties map to `application-aws.yml`. The YAML reads them via `${VAR_NAME}` placeholders — the names must match exactly as shown above (uppercase with underscores).

> **Note — `APP_CORS_ALLOWED_ORIGINS=*`:** The app handles wildcard correctly at startup by using `allowedOriginPatterns` instead of `allowedOrigins`, which avoids the Spring Security restriction on wildcard + credentials. Update it to the exact CloudFront URL once Step 11 is complete.

8. Click **Submit** — environment creation takes ~5 minutes.
9. Once status is **Green**, note the environment URL: `http://xchange-env.eba-xxxx.ap-south-1.elasticbeanstalk.com`

---

## Step 8 — Health Check Configuration

By default EB checks `/` for HTTP 200. Spring Boot's health endpoint is `/actuator/health`. Update it:

> ⚠️ **Single Instance vs Load Balanced:** The health check path location differs by environment type.
> - **Single Instance** (this POC) → it is under **Monitoring**
> - Load Balanced → it is under **Instance traffic and scaling** (ALB listener)

1. EB Console → **xchange-app-env** → **Configuration** → **Monitoring** → **Edit**
2. Under **Health check path**, set: `/actuator/health`
3. Click **Apply**

---

## Step 9 — Build and Deploy the JAR

### 9a. Build the JAR

```bash
# From the project root (currency-convertor/)
# Maven must use Java 21 — override JAVA_HOME if your Maven defaults to an older JDK.
# Check with: mvn -version   (look for "Java version: 21")
JAVA_HOME="C:/Program Files/OpenLogic/jdk-21.0.3.9-hotspot" mvn clean package -DskipTests
# JAR will be at: target/currency-exchange-1.0.0.jar
```

> **If `mvn -version` already shows Java 21**, omit the `JAVA_HOME=` prefix.

### 9b. Deploy via EB CLI (recommended)

Initialize the EB CLI once:

```bash
cd C:\Users\175123\currency-convertor

eb init xchange-app --region ap-south-1 --platform "Corretto 21"

# Point EB CLI at the environment we created
eb use xchange-env
```

Deploy:

```bash
eb deploy --label "v1.0.0"
```

The EB CLI zips the JAR and uploads it automatically. Deployment takes ~2 minutes.

### 9c. Deploy via AWS Console (alternative — no EB CLI)

1. EB Console → **xchange-env** → **Upload and deploy**
2. Click **Choose file** → select `target/currency-exchange-1.0.0.jar`
3. **Version label:** `v1.0.0`
4. Click **Deploy**

### 9d. Verify the health endpoint

```bash
curl http://xchange-env.eba-xxxx.ap-south-1.elasticbeanstalk.com/actuator/health
# Expected: {"status":"UP"}
```

---

## Step 10 — Create the S3 Bucket for the Frontend

1. AWS Console → **S3 → Create bucket**
2. Settings:

| Setting | Value |
|---|---|
| Bucket name | `xchange-frontend-poc` (must be globally unique) |
| Region | `ap-south-1` |
| Block all public access | **On** (CloudFront will be the only reader) |
| Versioning | Enable |
| Default encryption | SSE-S3 (AES-256) |

3. Click **Create bucket**

---

## Step 11 — Create the CloudFront Distribution

### 11a. Create the distribution

1. AWS Console → **CloudFront → Create a CloudFront distribution**

**Origin 1 — S3 (Angular SPA):**
- Origin domain: select `xchange-frontend-poc.s3.ap-south-1.amazonaws.com`
- Origin access: **Origin access control settings (recommended)**
  - Create new OAC → name `xchange-oac` → **Create**
- After creation, click **Copy policy** and go to S3 → your bucket → **Permissions → Bucket policy** → paste the policy

**Default cache behavior:**
- Viewer protocol policy: **Redirect HTTP to HTTPS*   *
- Cache policy: `CachingOptimized`
- Compress objects: Yes
- Allowed HTTP methods: GET, HEAD

**Custom error responses:**
- HTTP error code: 403 → Response page path: `/index.html` → HTTP response code: 200
- HTTP error code: 404 → Response page path: `/index.html` → HTTP response code: 200
  (These are needed for Angular's client-side routing)

**Settings:**
- Default root object: `index.html`
- Price class: Use all edge locations (or limit to Asia for cost savings)

2. Click **Create distribution**

### 11b. Add the `/api/*` origin (Elastic Beanstalk)

After the distribution is created, go to its **Origins** tab → **Create origin**:

| Setting | Value |
|---|---|
| Origin domain | `xchange-env.eba-xxxx.ap-south-1.elasticbeanstalk.com` (your EB CNAME) |
| Protocol | HTTP only |
| HTTP port | 80 |
| Name | `EB-xchange` |

Then go to **Behaviors** tab → **Create behavior**:

| Setting | Value |
|---|---|
| Path pattern | `/api/*` |
| Origin | `EB-xchange` |
| Viewer protocol policy | HTTPS only |
| Allowed HTTP methods | GET, HEAD, OPTIONS, PUT, POST, PATCH, DELETE |
| Cache policy | `CachingDisabled` |
| Origin request policy | `AllViewerExceptHostHeader` |

> `AllViewerExceptHostHeader` forwards all request headers **and cookies** to EB. This is required — the app uses an HTTP-only cookie (`refreshToken`) for JWT refresh. If cookies are not forwarded, token refresh will fail silently.

> This policy also forwards query strings, which is needed for rate endpoints like `/api/rates?from=USD&to=EUR`.

3. Note the CloudFront distribution domain name: `https://d1234abcdef.cloudfront.net`

---

## Step 12 — Update CORS in the Backend

Now that you have the CloudFront URL, update the `APP_CORS_ALLOWED_ORIGINS` environment variable:

### Option A — AWS Console

1. EB Console → **xchange-env** → **Configuration** → **Environment properties**
2. Change `APP_CORS_ALLOWED_ORIGINS` from `*` to `https://d1234abcdef.cloudfront.net`
3. Click **Apply** — EB will restart the instance (~1 minute)

### Option B — AWS CLI

```bash
aws elasticbeanstalk update-environment \
  --environment-name xchange-env \
  --option-settings \
    Namespace=aws:elasticbeanstalk:application:environment,OptionName=APP_CORS_ALLOWED_ORIGINS,Value=https://d1234abcdef.cloudfront.net \
  --region ap-south-1
```

---

## Step 13 — Build and Deploy the Angular Frontend

### 13a. Update the API URL

Edit `frontend/src/environments/environment.prod.ts`:

```typescript
export const environment = {
  production: true,
  apiUrl: 'https://d1234abcdef.cloudfront.net/api'  // CloudFront URL + /api
};
```

### 13b. Build

```bash
cd frontend
npm install
npm run build:prod
# Output: dist/currency-exchange/  (or similar)
```

### 13c. Upload to S3

```bash
# Upload static assets (long cache)
aws s3 sync dist/currency-exchange/ s3://xchange-frontend-poc \
  --delete \
  --cache-control "public,max-age=31536000,immutable" \
  --exclude "index.html"

# Upload index.html (no cache — always serve latest)
aws s3 cp dist/currency-exchange/index.html s3://xchange-frontend-poc/index.html \
  --cache-control "no-cache,no-store,must-revalidate"
```

### 13d. Invalidate CloudFront cache

```bash
# Get your distribution ID from the console or:
CF_ID=$(aws cloudfront list-distributions \
  --query "DistributionList.Items[?contains(Origins.Items[0].DomainName,'xchange-frontend-poc')].Id" \
  --output text)

aws cloudfront create-invalidation \
  --distribution-id $CF_ID \
  --paths "/*"
```

---

## Step 14 — Verification Checklist

```
[ ] curl https://<cloudfront-url>/api/actuator/health  → {"status":"UP"}
[ ] Open https://<cloudfront-url> in browser → Angular app loads
[ ] Register a new user → confirmation email arrives (SES working)
[ ] Log in → MFA OTP email arrives → login succeeds (Redis OTP working)
[ ] Request a currency conversion < $100 → status = COMPLETED immediately
[ ] Request a conversion ≥ $100 → status = PENDING_APPROVAL
[ ] Log in as admin → approve the pending transaction → email notification sent
[ ] EB console → Health = Green (all checks passing)
[ ] CloudWatch → EB environment logs → no ERROR or FATAL lines on startup
[ ] RDS console → Instance status = Available
[ ] ElastiCache console → Cluster status = available
```

---

## Known Issues Fixed in This Codebase

These bugs were encountered and fixed during deployment — documented here so you don't re-investigate them.

| Symptom | Root cause | Fix applied |
|---|---|---|
| `java.io.IOException: extra data at the end` at startup | RSA key DER bytes had trailing ASN.1 data rejected by Java 25's strict parser | Generate fresh keys with `openssl genpkey`; use inner DER base64 (Step 1c) |
| `Illegal base64 character 2d` (0x2d = `-`) | EB console env var field strips newlines from pasted PEM, leaving bare `-` chars | Use inner DER base64 — no PEM headers to strip |
| `The dependencies ... form a cycle: jwtAuthFilter ↔ securityConfig` | `SecurityConfig` injected `JwtAuthFilter` as a constructor field; `JwtAuthFilter` depends on `UserDetailsService` which is a `@Bean` inside `SecurityConfig` | Moved `JwtAuthFilter` / `RateLimitFilter` from `SecurityConfig` constructor fields to `filterChain(...)` method parameters |

---

## Step 15 — Redeploying After Code Changes

### Backend (JAR) changes

```bash
# 1. Rebuild
mvn clean package -DskipTests

# 2a. EB CLI (recommended)
eb deploy --label "v1.0.1"

# 2b. OR via Console: EB → xchange-env → Upload and deploy → choose new JAR
```

### Frontend only

```bash
cd frontend
npm run build:prod
aws s3 sync dist/currency-exchange/ s3://xchange-frontend-poc --delete
aws cloudfront create-invalidation --distribution-id $CF_ID --paths "/*"
```

---

## Step 16 — Cost Estimate

All prices are `ap-south-1` (Mumbai) on-demand, ~730 hours/month.

| Resource | Spec | Monthly Cost |
|---|---|---|
| RDS PostgreSQL | `db.t3.micro`, single-AZ, 20 GB gp2 | ~$15 |
| ElastiCache Redis | `cache.t3.micro`, 1 node | ~$12 |
| EC2 (via EB) | `t3.micro`, always on | ~$0 (free tier, 1st year) / ~$8 after |
| S3 | < 1 GB frontend assets | ~$0.02 |
| CloudFront | < 10 GB transfer | ~$0.85 |
| SES | < 1,000 emails/month | ~$0.10 |
| **Total (free tier)** | | **~$28/month** |
| **Total (after free tier)** | | **~$36/month** |

---

## Step 17 — Cleanup (When Done)

To avoid ongoing charges, delete resources in this order:

1. **EB** → xchange-env → **Actions → Terminate environment**
2. **EB** → xchange-app → **Actions → Delete application**
3. **ElastiCache** → xchange-redis → **Delete** (no final snapshot needed for POC)
4. **RDS** → xchange-postgres → **Actions → Delete** (uncheck final snapshot)
5. **S3** → xchange-frontend-poc → empty bucket → delete bucket
6. **CloudFront** → disable distribution → wait → delete distribution
7. **VPC** → delete: security groups → subnets → route tables → internet gateway → VPC
8. **IAM** → delete `xchange-eb-ec2-role` and `xchange-eb-ec2-profile`
9. **SES** → delete verified identity (optional)

---

## Deployment Order Summary

```
1. One-time prep (Step 1)
   ├── Verify SES email
   ├── Create SES SMTP credentials
   ├── Generate JWT RSA keys
   └── Get Alpha Vantage key

2. VPC and networking (Step 2)
   ├── VPC (10.0.0.0/16)
   ├── Public subnet (10.0.10.0/24)
   ├── Private subnets x2 (10.0.1.0/24, 10.0.2.0/24)
   ├── Internet Gateway + route table
   └── Associate public subnet with public route table

3. Security groups (Step 3)
   ├── xchange-beanstalk-sg  (inbound: 80, 5000 from internet)
   ├── xchange-rds-sg        (inbound: 5432 from beanstalk-sg)
   └── xchange-elasticache-sg (inbound: 6379 from beanstalk-sg)

4. RDS PostgreSQL (Step 4)
   ├── DB subnet group (private-a + private-b)
   └── db.t3.micro, single-AZ, no public access

5. ElastiCache Redis (Step 5)
   ├── Cache subnet group (private-a + private-b)
   └── cache.t3.micro, 1 node, no TLS

6. IAM role + instance profile (Step 6)

7. Elastic Beanstalk application + environment (Step 7)
   ├── Platform: Corretto 21
   ├── Single instance, t3.micro
   ├── VPC: xchange-vpc, public subnet
   └── All environment properties set

8. Health check update (Step 8)

9. Build JAR + deploy to EB (Step 9)
   └── Verify /actuator/health = UP

10. S3 bucket for frontend (Step 10)

11. CloudFront distribution (Step 11)
    ├── S3 origin (Angular SPA)
    ├── EB origin (Spring Boot API)
    ├── Default behavior → S3
    └── /api/* behavior → EB

12. Update CORS allowed origins (Step 12)

13. Build Angular + upload to S3 + CloudFront invalidation (Step 13)

14. Verification checklist (Step 14)
```
