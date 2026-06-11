# AWS Migration Plan — Elastic Beanstalk (JAR-based, No Docker)
### POC / Assignment Version

> This plan is optimized for a working demo, not production. Every place where a real deployment would do something different is called out with a **🔴 PROD** marker. Everything you actually need to do is marked **✅ POC**.

---

## Why Elastic Beanstalk for this POC?

| Criteria | Elastic Beanstalk (this plan) | ECS Fargate | Lambda |
|---|---|---|---|
| Cold starts | None — EC2 is always warm | None | Yes — first request is slow |
| Docker required | No — upload JAR directly | Yes | No (but cold starts) |
| Free tier eligible | Yes — t3.micro EC2 | No | Yes (but cold starts) |
| Complexity | Low — EB handles ALB, health checks, restarts | High — ECR, task defs, ALB Terraform | Medium — API Gateway + VPC config |
| Redeploy | Upload new JAR | Push new image + update service | Upload new JAR |
| ALB cost | None — single-instance has public IP | ~$18/month | None |

Elastic Beanstalk is the sweet spot for this assignment: no Docker overhead, free-tier EC2, and EB manages the platform layer (OS patching, JVM process supervision, CloudWatch logs, health checks) automatically.

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

**Key point:** Single-instance EB puts the EC2 in the **public subnet** with a direct public IP. No ALB, no NAT Gateway needed for the app itself — saving ~$18/month (ALB) and ~$4/month (NAT). RDS and ElastiCache stay in private subnets, reachable because EB is inside the same VPC.

---

## POC vs Production — At a Glance

| Area | ✅ What we do (POC) | 🔴 What prod needs |
|---|---|---|
| EB environment type | SingleInstance (public IP, no ALB) | LoadBalanced, 2–4 instances |
| RDS instance | `db.t3.micro`, single-AZ, no deletion protection | `db.t3.medium`, Multi-AZ, deletion_protection = true |
| Redis | `cache.t3.micro`, 1 node, no encryption | Replication group, 2 nodes, Multi-AZ, encryption in-transit |
| NAT Gateway | Not needed (EB in public subnet) | Required if EB in private subnet |
| HTTPS on EB | None — CloudFront terminates TLS | ACM cert on ALB listener |
| Secrets management | Env vars in EB config / tfvars | AWS Secrets Manager + IAM roles |
| Custom domain | Skipped — use CloudFront default domain | Route 53 + ACM |
| WAF | Skip `waf.tf` | WAF on CloudFront |
| Monitoring | Skip `monitoring.tf` | CloudWatch alarms, SNS |
| CI/CD | Manual `eb deploy` | CodePipeline → CodeBuild → EB deploy |

---

## Step 1 — Prerequisites

### Tools to install

| Tool | Version | Install |
|---|---|---|
| AWS CLI | v2 | https://aws.amazon.com/cli/ |
| EB CLI | latest | `pip install awsebcli` |
| Terraform | >= 1.7 | https://developer.hashicorp.com/terraform/install |
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

Verify it works:
```bash
aws sts get-caller-identity
```

---

## Step 2 — One-Time Manual Setup

These four things cannot be automated by Terraform and must be done once in the AWS Console (or CLI) before you run `terraform apply`.

### 2a. Verify email addresses in SES

SES Sandbox mode only allows sending to verified addresses.

1. AWS Console → **Simple Email Service** → **Verified identities** → **Create identity**
2. Verify `karthikeyan.manickasathasivam@cognizant.com` (your from-address and admin email)
3. Check your inbox and click the verification link

✅ POC: Verify the specific email addresses you will use. Both `EMAIL_FROM_ADDRESS` and `TRANSACTION_ADMIN_EMAIL` must be verified.
🔴 PROD: Verify the entire domain instead and request SES production access (sandbox removal).

### 2b. Create SES SMTP credentials

1. AWS Console → **SES** → **SMTP Settings** → **Create SMTP Credentials**
2. This creates an IAM user and downloads a CSV with the SMTP username + password
3. Save these — they go into `terraform.tfvars` as `ses_smtp_username` and `ses_smtp_password`

### 2c. Generate JWT RSA keys

Run these commands (Git Bash or WSL on Windows):

```bash
openssl genrsa -out private.pem 2048
openssl rsa -in private.pem -pubout -out public.pem

# Base64-encode for tfvars (single line, no line breaks)
base64 -w 0 private.pem   # copy output → jwt_private_key
base64 -w 0 public.pem    # copy output → jwt_public_key
```

Keep `private.pem` secure — never commit it.

### 2d. Get an Alpha Vantage API key

Free tier: https://www.alphavantage.co/support/#api-key — sign up and copy the key.

### 2e. Create the Terraform state S3 bucket

```bash
aws s3api create-bucket \
  --bucket your-terraform-state-bucket \
  --region ap-south-1 \
  --create-bucket-configuration LocationConstraint=ap-south-1
```

Then update `terraform/main.tf` — replace `"your-terraform-state-bucket"` with the actual bucket name you just created.

---

## Step 3 — Tune Existing Terraform Files for POC

You do not need to rewrite these files — just apply small changes to scale down for POC.

### `terraform/rds.tf` — Scale down to t3.micro, single-AZ

Change these four lines:

```hcl
# Change:
instance_class        = "db.t3.medium"
multi_az              = true
deletion_protection   = true
monitoring_interval   = 60

# To:
instance_class        = "db.t3.micro"    # ✅ POC: free-tier eligible
multi_az              = false             # ✅ POC: single-AZ, saves ~$15/month
deletion_protection   = false             # ✅ POC: allows easy teardown
monitoring_interval   = 0                 # ✅ POC: skip enhanced monitoring
```

Also remove the `monitoring_role_arn` line and the two `aws_iam_role.rds_monitoring` / `aws_iam_role_policy_attachment.rds_monitoring` resources (they are only needed when `monitoring_interval > 0`).

### `terraform/elasticache.tf` — Single node, no Multi-AZ

```hcl
# Change:
num_cache_clusters         = 2
automatic_failover_enabled = true
multi_az_enabled           = true
transit_encryption_enabled = true

# To:
num_cache_clusters         = 1             # ✅ POC: single node
automatic_failover_enabled = false          # ✅ POC: required when num_cache_clusters = 1
multi_az_enabled           = false          # ✅ POC
transit_encryption_enabled = false          # ✅ POC: simplifies Redis URL (no TLS)
```

### `terraform/vpc.tf` — Update security groups to allow EB

The existing security groups reference `aws_security_group.lambda`. Add EB's security group as an additional source (the `beanstalk.tf` below creates `aws_security_group.beanstalk`).

Add an ingress rule to `aws_security_group.rds`:
```hcl
ingress {
  from_port       = 5432
  to_port         = 5432
  protocol        = "tcp"
  security_groups = [aws_security_group.beanstalk.id]
  description     = "PostgreSQL from Elastic Beanstalk EC2"
}
```

Add an ingress rule to `aws_security_group.elasticache`:
```hcl
ingress {
  from_port       = 6379
  to_port         = 6379
  protocol        = "tcp"
  security_groups = [aws_security_group.beanstalk.id]
  description     = "Redis from Elastic Beanstalk EC2"
}
```

### `terraform/frontend.tf` — Remove WAF dependency

The current `frontend.tf` references `aws_wafv2_web_acl.frontend.arn` (from `waf.tf`). Since we are skipping `waf.tf`, remove that reference:

```hcl
# Remove this line from aws_cloudfront_distribution:
web_acl_id = aws_wafv2_web_acl.frontend.arn
```

### Skip entirely for POC

- `terraform/waf.tf` — Do not run. No changes needed, just exclude from `terraform apply` using `-target` or leave it and delete it temporarily.
- `terraform/monitoring.tf` — Do not run. Skip CloudWatch alarms and SNS for now.
- `terraform/lambda.tf` — Not used in this deployment.

✅ POC: Run `terraform apply` with `-target` flags to exclude waf and monitoring (see Step 6).

---

## Step 4 — Create `terraform/beanstalk.tf`

Create this new file in the `terraform/` directory:

```hcl
# ── Elastic Beanstalk Application ─────────────────────────────────────────────

resource "aws_elastic_beanstalk_application" "app" {
  name        = "${var.project_name}-app"
  description = "Currency Exchange Spring Boot application"
}

# ── IAM Role for EC2 instances managed by EB ──────────────────────────────────

resource "aws_iam_role" "eb_ec2" {
  name = "${var.project_name}-eb-ec2-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
    }]
  })

  tags = { Name = "${var.project_name}-eb-ec2-role" }
}

resource "aws_iam_role_policy_attachment" "eb_ec2_web_tier" {
  role       = aws_iam_role.eb_ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AWSElasticBeanstalkWebTier"
}

resource "aws_iam_role_policy_attachment" "eb_ec2_multicontainer" {
  role       = aws_iam_role.eb_ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AWSElasticBeanstalkMulticontainerDocker"
}

resource "aws_iam_role_policy_attachment" "eb_ec2_worker_tier" {
  role       = aws_iam_role.eb_ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AWSElasticBeanstalkWorkerTier"
}

resource "aws_iam_instance_profile" "eb_ec2" {
  name = "${var.project_name}-eb-ec2-profile"
  role = aws_iam_role.eb_ec2.name
}

# ── Security Group for EB EC2 instance ────────────────────────────────────────

resource "aws_security_group" "beanstalk" {
  name        = "${var.project_name}-beanstalk-sg"
  vpc_id      = aws_vpc.main.id
  description = "Elastic Beanstalk EC2 instance"

  # Accept HTTP from CloudFront and direct browser (port 5000 is EB's default for Java)
  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "HTTP from internet (CloudFront)"
  }

  ingress {
    from_port   = 5000
    to_port     = 5000
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "Spring Boot direct port (EB Corretto platform default)"
  }

  # Allow all outbound: SES, Alpha Vantage, RDS, Redis, AWS APIs
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project_name}-beanstalk-sg" }
}

# ── Elastic Beanstalk Environment (Single Instance — no ALB) ──────────────────

resource "aws_elastic_beanstalk_environment" "env" {
  name                = "${var.project_name}-env"
  application         = aws_elastic_beanstalk_application.app.name
  # Amazon Linux 2023 with Corretto 21 (Java 21). Check current stack names at:
  # aws elasticbeanstalk list-available-solution-stacks | grep Corretto
  solution_stack_name = "64bit Amazon Linux 2023 v4.3.0 running Corretto 21"

  tier = "WebServer"

  # ── Environment type: single EC2 instance, no ALB ─────────────────────────
  setting {
    namespace = "aws:elasticbeanstalk:environment"
    name      = "EnvironmentType"
    value     = "SingleInstance"
  }

  # ── Instance type ──────────────────────────────────────────────────────────
  setting {
    namespace = "aws:autoscaling:launchconfiguration"
    name      = "InstanceType"
    value     = "t3.micro"
  }

  # ── IAM instance profile ───────────────────────────────────────────────────
  setting {
    namespace = "aws:autoscaling:launchconfiguration"
    name      = "IamInstanceProfile"
    value     = aws_iam_instance_profile.eb_ec2.name
  }

  # ── VPC — put EB in the public subnet so it can reach internet directly ────
  # (No NAT Gateway needed. RDS and Redis are in private subnets but reachable
  #  from the EB instance because they're in the same VPC.)
  setting {
    namespace = "aws:ec2:vpc"
    name      = "VPCId"
    value     = aws_vpc.main.id
  }

  setting {
    namespace = "aws:ec2:vpc"
    name      = "Subnets"
    value     = aws_subnet.public_a.id
  }

  setting {
    namespace = "aws:ec2:vpc"
    name      = "AssociatePublicIpAddress"
    value     = "true"
  }

  setting {
    namespace = "aws:autoscaling:launchconfiguration"
    name      = "SecurityGroups"
    value     = aws_security_group.beanstalk.id
  }

  # ── Health check ───────────────────────────────────────────────────────────
  setting {
    namespace = "aws:elasticbeanstalk:application"
    name      = "Application Healthcheck URL"
    value     = "/actuator/health"
  }

  # ── JVM options (Java 21 heap for t3.micro — 1 GB RAM, keep heap at 512m) ─
  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "JAVA_TOOL_OPTIONS"
    value     = "-Xms256m -Xmx512m"
  }

  # ── Spring profile ─────────────────────────────────────────────────────────
  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "SPRING_PROFILES_ACTIVE"
    value     = "aws"
  }

  # ── Database ───────────────────────────────────────────────────────────────
  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "DB_URL"
    value     = "jdbc:postgresql://${aws_db_instance.postgres.endpoint}/currency_exchange"
  }

  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "DB_USERNAME"
    value     = var.db_username
  }

  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "DB_PASSWORD"
    value     = var.db_password
  }

  # ── Redis ──────────────────────────────────────────────────────────────────
  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "REDIS_HOST"
    value     = aws_elasticache_replication_group.redis.primary_endpoint_address
  }

  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "REDIS_PORT"
    value     = "6379"
  }

  # ── SES / Email ────────────────────────────────────────────────────────────
  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "SES_SMTP_USERNAME"
    value     = var.ses_smtp_username
  }

  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "SES_SMTP_PASSWORD"
    value     = var.ses_smtp_password
  }

  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "EMAIL_FROM_ADDRESS"
    value     = var.email_from_address
  }

  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "TRANSACTION_ADMIN_EMAIL"
    value     = var.transaction_admin_email
  }

  # ── JWT ────────────────────────────────────────────────────────────────────
  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "JWT_PRIVATE_KEY"
    value     = var.jwt_private_key
  }

  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "JWT_PUBLIC_KEY"
    value     = var.jwt_public_key
  }

  # ── External APIs ──────────────────────────────────────────────────────────
  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "ALPHA_VANTAGE_API_KEY"
    value     = var.alpha_vantage_api_key
  }

  # ── CORS ───────────────────────────────────────────────────────────────────
  # Set to * initially — update to the exact CloudFront URL after Step 6
  # (Circular dependency: CloudFront URL is unknown until after terraform apply)
  # After you get the cloudfront_url output, update this value and re-run:
  #   terraform apply -target=aws_elastic_beanstalk_environment.env
  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "APP_CORS_ALLOWED_ORIGINS"
    value     = "*"
  }

  # ── Server port (Spring Boot default 8080, EB Corretto maps to 5000) ───────
  # EB Corretto platform sets SERVER_PORT automatically to 5000.
  # If your application-aws.yml hard-codes server.port=8080, override here:
  setting {
    namespace = "aws:elasticbeanstalk:application:environment"
    name      = "SERVER_PORT"
    value     = "5000"
  }

  tags = { Name = "${var.project_name}-eb-env" }
}

# ── Outputs ───────────────────────────────────────────────────────────────────

output "beanstalk_url" {
  value       = "http://${aws_elastic_beanstalk_environment.env.cname}"
  description = "Elastic Beanstalk endpoint — use as CloudFront /api/* origin"
}

output "beanstalk_cname" {
  value       = aws_elastic_beanstalk_environment.env.cname
  description = "EB CNAME — format: xchange-env.eba-xxxx.ap-south-1.elasticbeanstalk.com"
}
```

> **Note on solution_stack_name:** The exact string changes when AWS releases new platform versions. Before running `terraform apply`, confirm the current name:
> ```bash
> aws elasticbeanstalk list-available-solution-stacks --query "SolutionStacks[?contains(@,'Corretto 21')]"
> ```
> Use the most recent result and update `solution_stack_name` in `beanstalk.tf`.

---

## Step 5 — Fill in `terraform/terraform.tfvars`

Copy `terraform.tfvars.example` to `terraform.tfvars` and fill in all values. The file is in `.gitignore` — never commit it.

```hcl
aws_region   = "ap-south-1"
project_name = "xchange"
environment  = "poc"

# RDS credentials
db_username = "xchange_admin"
db_password = "ReplaceWith16PlusChars!"     # minimum 8 chars for RDS

# SES SMTP — from the CSV downloaded in Step 2b
ses_smtp_username       = "AKIAIOSFODNN7EXAMPLE"
ses_smtp_password       = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
email_from_address      = "karthikeyan.manickasathasivam@cognizant.com"
transaction_admin_email = "karthikeyan.manickasathasivam@cognizant.com"

# JWT RSA keys — base64 output from Step 2c
jwt_private_key = "LS0tLS1CRUdJTiBSU0EgUFJJVkFURSBLRVktLS0tLQ..."
jwt_public_key  = "LS0tLS1CRUdJTiBQVUJMSUMgS0VZLS0tLS0..."

# Alpha Vantage — from Step 2d
alpha_vantage_api_key = "YOUR_ALPHA_VANTAGE_KEY"

# Not used for EB deployment — kept to satisfy variables.tf
lambda_jar_path = "../target/currency-exchange-1.0.0.jar"
alert_email     = "karthikeyan.manickasathasivam@cognizant.com"
```

---

## Step 6 — Run `terraform apply`

```bash
cd terraform

terraform init

# Plan first — review what will be created
terraform plan \
  -target=aws_vpc.main \
  -target=aws_db_instance.postgres \
  -target=aws_elasticache_replication_group.redis \
  -target=aws_elastic_beanstalk_application.app \
  -target=aws_elastic_beanstalk_environment.env \
  -target=aws_cloudfront_distribution.frontend \
  -target=aws_s3_bucket.frontend

# Apply the same targets (skips waf.tf and monitoring.tf)
terraform apply \
  -target=aws_vpc.main \
  -target=aws_db_instance.postgres \
  -target=aws_elasticache_replication_group.redis \
  -target=aws_elastic_beanstalk_application.app \
  -target=aws_elastic_beanstalk_environment.env \
  -target=aws_cloudfront_distribution.frontend \
  -target=aws_s3_bucket.frontend
```

After apply completes, note the outputs:

```
beanstalk_url          = "http://xchange-env.eba-xxxx.ap-south-1.elasticbeanstalk.com"
beanstalk_cname        = "xchange-env.eba-xxxx.ap-south-1.elasticbeanstalk.com"
cloudfront_url         = "https://d1234abcdef.cloudfront.net"
s3_frontend_bucket     = "xchange-frontend-poc"
rds_endpoint           = "xchange-postgres.xxxx.ap-south-1.rds.amazonaws.com:5432"
```

RDS takes ~5 minutes to become available. ElastiCache takes ~3 minutes. EB environment creation takes ~5 minutes.

---

## Step 7 — Build and Deploy the JAR

### 7a. Build the JAR

```bash
# From the project root (currency-convertor/)
JAVA_HOME="C:/Program Files/OpenLogic/jdk-21.0.3.9-hotspot" \
  mvn clean package -DskipTests

# Verify the JAR was created
ls -lh target/currency-exchange-1.0.0.jar
```

### 7b. Deploy — Method 1: EB CLI (recommended)

The EB CLI is the simplest way to deploy. Run this once from the project root to initialize:

```bash
cd /c/Users/175123/currency-convertor

eb init xchange-app \
  --region ap-south-1 \
  --platform "Corretto 21"

# Point EB CLI at the environment Terraform created
eb use xchange-env
```

Then deploy:

```bash
eb deploy --label "v1.0.0"
```

The EB CLI zips the JAR and uploads it automatically. Deployment takes ~2 minutes.

### 7b. Deploy — Method 2: AWS CLI (no EB CLI required)

```bash
# Upload the JAR to S3 first
aws s3 cp target/currency-exchange-1.0.0.jar \
  s3://your-terraform-state-bucket/deployments/currency-exchange-1.0.0.jar

# Create an application version
aws elasticbeanstalk create-application-version \
  --application-name xchange-app \
  --version-label "v1.0.0" \
  --source-bundle S3Bucket=your-terraform-state-bucket,S3Key=deployments/currency-exchange-1.0.0.jar \
  --region ap-south-1

# Update the environment to use the new version
aws elasticbeanstalk update-environment \
  --environment-name xchange-env \
  --version-label "v1.0.0" \
  --region ap-south-1
```

Watch the deployment:

```bash
aws elasticbeanstalk describe-events \
  --environment-name xchange-env \
  --region ap-south-1 \
  --query "Events[*].[EventDate,Severity,Message]" \
  --output table
```

Wait for: `Successfully deployed new configuration to environment.`

Test the health endpoint:

```bash
curl http://xchange-env.eba-xxxx.ap-south-1.elasticbeanstalk.com/actuator/health
# Expected: {"status":"UP"}
```

---

## Step 8 — Deploy the Frontend

### 8a. Update the API URL

Edit `src/environments/environment.prod.ts` in the Angular project:

```typescript
export const environment = {
  production: true,
  apiUrl: 'https://d1234abcdef.cloudfront.net/api'  // CloudFront URL + /api
};
```

Do NOT use the EB URL directly here — all API calls go through CloudFront `/api/*`.

### 8b. Add the CloudFront `/api/*` behavior

The current `frontend.tf` only serves S3 assets. Add an ordered cache behavior to forward `/api/*` to the EB origin. Add this block to `aws_cloudfront_distribution.frontend` in `frontend.tf`:

```hcl
# API origin — points to Elastic Beanstalk
origin {
  domain_name = aws_elastic_beanstalk_environment.env.cname
  origin_id   = "EB-${var.project_name}"

  custom_origin_config {
    http_port              = 80
    https_port             = 443
    origin_protocol_policy = "http-only"   # EB single-instance has no HTTPS
    origin_ssl_protocols   = ["TLSv1.2"]
  }
}

# Forward /api/* to EB, cache nothing (it's a REST API)
ordered_cache_behavior {
  path_pattern           = "/api/*"
  target_origin_id       = "EB-${var.project_name}"
  viewer_protocol_policy = "https-only"
  allowed_methods        = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
  cached_methods         = ["GET", "HEAD"]
  compress               = false

  forwarded_values {
    query_string = true
    headers      = ["Authorization", "Content-Type", "Accept", "Origin"]
    cookies { forward = "all" }
  }

  min_ttl     = 0
  default_ttl = 0
  max_ttl     = 0
}
```

Then apply only the CloudFront change:
```bash
terraform apply -target=aws_cloudfront_distribution.frontend
```

### 8c. Update CORS in the backend

The Spring Boot `SecurityConfig` reads `${app.cors.allowed-origins}` which is populated from the `APP_CORS_ALLOWED_ORIGINS` environment variable (already wired in `application-aws.yml` and `beanstalk.tf` Step 4 — no code change needed). Once you have the CloudFront URL, update the value:

```bash
aws elasticbeanstalk update-environment \
  --environment-name xchange-env \
  --option-settings \
    Namespace=aws:elasticbeanstalk:application:environment,OptionName=APP_CORS_ALLOWED_ORIGINS,Value=https://d1234abcdef.cloudfront.net \
  --region ap-south-1
```

Or update the `setting` block already in `beanstalk.tf` and re-apply:
```hcl
setting {
  namespace = "aws:elasticbeanstalk:application:environment"
  name      = "APP_CORS_ALLOWED_ORIGINS"
  value     = "https://d1234abcdef.cloudfront.net"
}
```

`SecurityConfig.java` already reads this correctly — no source changes required:
```java
@Value("${app.cors.allowed-origins:http://localhost:4200}")
private String allowedOrigins;
// splits on comma, so multiple origins are supported: "https://a.com,https://b.com"
```

### 8d. Build the Angular app

```bash
cd frontend    # or wherever the Angular project lives
npm install
npm run build:prod
# Output goes to dist/currency-exchange/ or similar
```

### 8e. Sync to S3 and invalidate CloudFront

```bash
# Get the bucket name from terraform output
BUCKET=$(cd terraform && terraform output -raw s3_frontend_bucket)
CF_ID=$(cd terraform && terraform output -raw cloudfront_distribution_id)

# Upload — set correct MIME types and cache headers
aws s3 sync dist/currency-exchange/ s3://$BUCKET \
  --delete \
  --cache-control "public,max-age=31536000,immutable" \
  --exclude "index.html"

# index.html: no cache so users always get the latest version
aws s3 cp dist/currency-exchange/index.html s3://$BUCKET/index.html \
  --cache-control "no-cache,no-store,must-revalidate"

# Invalidate CloudFront so it serves the new files immediately
aws cloudfront create-invalidation \
  --distribution-id $CF_ID \
  --paths "/*"
```

---

## Step 9 — Verification Checklist

Run through this after every deployment to confirm the stack is healthy.

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

## Step 10 — Redeploying After Code Changes

This is the main advantage of Elastic Beanstalk: redeploy in under 3 minutes.

```bash
# 1. Rebuild the JAR
JAVA_HOME="C:/Program Files/OpenLogic/jdk-21.0.3.9-hotspot" \
  mvn clean package -DskipTests

# 2. Deploy with EB CLI (from project root)
eb deploy --label "v1.0.1"
```

Or with AWS CLI:
```bash
aws s3 cp target/currency-exchange-1.0.0.jar \
  s3://your-terraform-state-bucket/deployments/currency-exchange-1.0.1.jar

aws elasticbeanstalk create-application-version \
  --application-name xchange-app \
  --version-label "v1.0.1" \
  --source-bundle S3Bucket=your-terraform-state-bucket,S3Key=deployments/currency-exchange-1.0.1.jar \
  --region ap-south-1

aws elasticbeanstalk update-environment \
  --environment-name xchange-env \
  --version-label "v1.0.1" \
  --region ap-south-1
```

For frontend-only changes:
```bash
npm run build:prod
aws s3 sync dist/currency-exchange/ s3://$BUCKET --delete
aws cloudfront create-invalidation --distribution-id $CF_ID --paths "/*"
```

---

## Step 11 — Cost Estimate

All prices are `ap-south-1` (Mumbai) on-demand rates, ~730 hours/month.

| Resource | Spec | Monthly Cost |
|---|---|---|
| RDS PostgreSQL | `db.t3.micro`, single-AZ, 20 GB gp3 | ~$15 |
| ElastiCache Redis | `cache.t3.micro`, 1 node | ~$12 |
| EC2 (via EB) | `t3.micro`, always on | ~$0 (free tier, 1st year) / ~$8 after |
| S3 | < 1 GB frontend assets | ~$0.02 |
| CloudFront | < 10 GB transfer | ~$0.85 |
| SES | < 1,000 emails/month | ~$0.10 |
| Data transfer | RDS + Redis in-VPC | ~$0 (same-AZ) |
| **Total (free tier)** | | **~$28/month** |
| **Total (after free tier)** | | **~$36/month** |

**Compared to ECS Fargate with ALB:** This setup saves ~$18/month (no ALB) and ~$4/month (no NAT Gateway) = **~$22/month cheaper** than the ECS plan for the same functionality.

✅ POC: Well within a typical AWS free-tier + low-cost budget.
🔴 PROD: Multi-AZ RDS (`db.t3.medium`) + Redis replication + ALB + NAT = ~$120–150/month.

---

## Step 12 — Deployment Order Summary

Follow this exact order to avoid dependency errors:

```
1. Manual setup (Step 2)
   ├── Verify SES email addresses
   ├── Create SES SMTP credentials
   ├── Generate JWT RSA keys
   ├── Get Alpha Vantage key
   └── Create Terraform state S3 bucket

2. Edit Terraform files (Step 3)
   ├── rds.tf          → t3.micro, single-AZ
   ├── elasticache.tf  → single node, no Multi-AZ
   ├── vpc.tf          → add EB security group ingress rules
   └── frontend.tf     → remove web_acl_id line

3. Create terraform/beanstalk.tf (Step 4)

4. Fill in terraform/terraform.tfvars (Step 5)

5. terraform init && terraform apply (Step 6)
   └── Wait ~10 min for RDS + ElastiCache + EB to become available

6. Build JAR and eb deploy (Step 7)
   └── Verify /actuator/health returns UP

7. Deploy frontend (Step 8)
   ├── Update environment.prod.ts with CloudFront URL
   ├── Add /api/* behavior to CloudFront in frontend.tf + terraform apply
   ├── Update CORS allowed origins
   ├── npm run build:prod
   └── aws s3 sync + CloudFront invalidation

8. Run verification checklist (Step 9)
```
