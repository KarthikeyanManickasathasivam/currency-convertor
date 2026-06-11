# AWS Migration Plan — Lambda (Serverless)
### POC / Assignment Version

> This plan is optimized for a working demo, not production. Every place where a real deployment would do something different is called out with a **🔴 PROD** marker. Everything you actually need to do is marked **✅ POC**.

---

## Architecture

```
Browser
  │
  ▼
CloudFront  ──────────────────────  S3 (Angular SPA)
  │
  └── /api/*  →  API Gateway (HTTP API)
                        │
                        ▼
                   Lambda  (Spring Boot JAR, Java 21)
                   [inside VPC]
                        │
              ┌─────────┼──────────┐
              ▼         ▼          ▼
            RDS      ElastiCache  NAT Gateway → Internet
         PostgreSQL    Redis           │
                                  ├── Amazon SES
                                  └── Alpha Vantage API
```

---

## POC vs Production — At a Glance

| Area | ✅ What we do (POC) | 🔴 What prod needs |
|---|---|---|
| RDS instance | `db.t3.micro`, single-AZ | `db.t3.medium`, Multi-AZ |
| RDS storage | 20 GB | 50 GB, auto-scaling |
| Redis | `cache.t3.micro`, single node, no encryption | Replica in 2nd AZ, encryption in-transit |
| Lambda memory | 512 MB | 1024 MB |
| WAF | Skipped | WAF with SQLi, XSS, rate-based rules on CloudFront |
| CloudWatch alarms | Skipped | Alarms on latency, error rate, RDS CPU, Redis memory |
| Custom domain + ACM cert | Skipped — use default CloudFront URL | Custom domain, ACM cert, Route 53 |
| SES | Sandbox mode (send only to verified emails) | Production access approved |
| Multi-AZ VPC | 1 private subnet | 2 private subnets across 2 AZs |
| Backups | Default (1 day) | 7-day automated backups, snapshot before migrations |
| Secrets management | Plain env vars in Lambda | AWS Secrets Manager or Parameter Store |
| CI/CD | Manual JAR upload | CodePipeline triggered on git push |

---

## Prerequisites

```bash
# AWS CLI v2 — https://aws.amazon.com/cli/
aws --version

# Terraform 1.7+ — https://developer.hashicorp.com/terraform/install
terraform --version

# Configure your AWS credentials
aws configure
# Region: ap-south-1  |  Output format: json

# Verify it works
aws sts get-caller-identity
```

---

## Step 1 — One-Time Manual Setup

### 1A. Create Terraform State Bucket

```bash
aws s3 mb s3://xchange-tfstate-yourname --region ap-south-1
```

Update `terraform/main.tf` line 13:
```hcl
bucket = "xchange-tfstate-yourname"
```

### 1B. Verify Your Email in SES

> **✅ POC:** Verify a single email address — takes 2 minutes.  
> **🔴 PROD:** Verify the full domain + request production access (takes 24–48 hrs).

1. AWS Console → **SES** → Verified Identities → Create Identity → **Email Address**
2. Enter your email → click the verification link in your inbox
3. Also verify the **recipient** email (sandbox restricts sending to unverified addresses)

### 1C. Create SES SMTP Credentials

1. AWS Console → **SES** → SMTP Settings → **Create SMTP Credentials**
2. Save the username and password immediately — password shown only once

### 1D. Generate JWT Keys

```bash
# Run in Git Bash (OpenSSL comes with Git for Windows)
openssl genrsa -out private.pem 2048
openssl rsa -in private.pem -pubout -out public.pem
base64 -w 0 private.pem > jwt_private_key.txt
base64 -w 0 public.pem  > jwt_public_key.txt
```

### 1E. Get Alpha Vantage API Key (Free)

Register at `https://www.alphavantage.co/support/#api-key` — free tier is fine.

---

## Step 2 — Simplify Terraform for POC

The existing `terraform/` is already written. You just need to scale it down for POC costs.

Make these changes before running `terraform apply`:

**`terraform/rds.tf`** — change instance to micro, disable Multi-AZ:
```hcl
instance_class    = "db.t3.micro"   # was db.t3.medium
multi_az          = false           # was true
allocated_storage = 20              # was 50
backup_retention_period = 1         # was 7
```

**`terraform/elasticache.tf`** — disable encryption, single node:
```hcl
# Remove or comment out:
# at_rest_encryption_enabled  = true
# transit_encryption_enabled  = true
# num_cache_clusters          = 2
```

**`terraform/lambda.tf`** — reduce memory:
```hcl
memory_size = 512   # was 1024 — fine for a POC
```

**`terraform/waf.tf`** — skip entirely for POC, comment out the CloudFront WAF association in `terraform/frontend.tf`.

**`terraform/monitoring.tf`** — skip entirely for POC.

> **🔴 PROD:** Keep all of those at original values. WAF protects against injection attacks. Multi-AZ gives failover. Monitoring catches outages before users do.

---

## Step 3 — Fill In Secrets

```bash
cd terraform
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform/terraform.tfvars`:
```hcl
db_username             = "xchange_admin"
db_password             = "Assignment2024!"        # any strong password
ses_smtp_username       = "AKIA..."               # from Step 1C
ses_smtp_password       = "..."                   # from Step 1C
email_from_address      = "you@gmail.com"         # your verified SES sender
transaction_admin_email = "you@gmail.com"         # same or different verified email
jwt_private_key         = "<contents of jwt_private_key.txt>"
jwt_public_key          = "<contents of jwt_public_key.txt>"
alpha_vantage_api_key   = "YOUR_KEY"
alert_email             = "you@gmail.com"
```

> **🔴 PROD:** Secrets go in AWS Secrets Manager, not plain text files. The `terraform.tfvars` file must never be committed to git.

---

## Step 4 — Deploy Infrastructure

```bash
cd terraform
terraform init
terraform plan     # review what will be created
terraform apply    # type "yes" — takes ~10 minutes
```

**Note the outputs — you need them in the next steps:**
```bash
terraform output
# api_gateway_url  → https://abc123.execute-api.ap-south-1.amazonaws.com
# cloudfront_url   → https://xyz789.cloudfront.net
```

---

## Step 5 — Deploy the Backend

```bash
# Build the JAR (from project root)
JAVA_HOME="C:/Program Files/OpenLogic/jdk-21.0.3.9-hotspot" \
  mvn clean package -DskipTests

# Upload to Lambda
aws lambda update-function-code \
  --function-name xchange-api \
  --zip-file fileb://target/currency-exchange-1.0.0.jar \
  --region ap-south-1

# Wait for upload to finish
aws lambda wait function-updated \
  --function-name xchange-api --region ap-south-1

# Publish a version (required for SnapStart)
aws lambda publish-version --function-name xchange-api --region ap-south-1
```

**Verify it works:**
```bash
curl https://<api_gateway_url>/actuator/health
# {"status":"UP"}

curl https://<api_gateway_url>/api/rates
# JSON array of rates (first call runs Flyway migrations — may take ~10s)
```

> **🔴 PROD:** Use CodePipeline to automate this on every git push. Add integration tests before deploying.

---

## Step 6 — Deploy the Frontend

**Set the real API URL** — edit `frontend/src/environments/environment.prod.ts`:
```typescript
export const environment = {
  production: true,
  apiUrl: 'https://<api_gateway_url>'
};
```

**Build and upload:**
```bash
cd frontend
npm run build:prod

aws s3 sync dist/currency-exchange-frontend/ \
  s3://xchange-frontend/ --delete --region ap-south-1

aws cloudfront create-invalidation \
  --distribution-id <DISTRIBUTION_ID> \
  --paths "/*"
```

**Update CORS** — edit `terraform/lambda.tf`, find `cors_configuration`:
```hcl
allow_origins = ["https://xyz789.cloudfront.net"]   # your cloudfront_url
```
Then: `terraform apply`

---

## Step 7 — Quick Verification

Open `https://<cloudfront_url>` in your browser:

```
□ Login page loads
□ Register a new user
□ Login → OTP arrives in email → enter it → Dashboard
□ Convert < $100 → APPROVED immediately
□ Convert ≥ $100 → PENDING_APPROVAL
□ Login as admin → approve it → notification email sent
```

---

## Estimated Cost for POC

| Service | POC Config | Monthly |
|---|---|---|
| Lambda | 512 MB, free tier covers first 1M req | ~$0–1 |
| API Gateway | First 1M requests free | ~$0–1 |
| RDS | db.t3.micro, single-AZ, 20 GB | ~$15 |
| ElastiCache | cache.t3.micro, single node | ~$12 |
| NAT Gateway | ~1 GB data | ~$4 |
| S3 + CloudFront | Minimal storage/transfer | ~$1 |
| SES | < 1,000 emails | ~$0 |
| **Total** | | **~$33/month** |

> **Stop resources when not using them** to avoid charges:  
> `terraform destroy` tears everything down. Run `terraform apply` again to restore.

---

## Deployment Order

```
1. Create S3 state bucket
2. Verify email(s) in SES
3. Create SES SMTP credentials
4. Generate JWT keys
5. Get Alpha Vantage key
6. Fill terraform.tfvars
7. Edit terraform files (scale down for POC)
8. terraform init + apply
9. mvn package + lambda update-function-code + publish-version
10. Update environment.prod.ts → ng build:prod → s3 sync
11. Update CORS → terraform apply
12. Test end-to-end
```
