# AWS Migration Plan — ECS Fargate (Containerized)
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
  └── /api/*  →  ALB (Application Load Balancer)
                        │
                        ▼
                  ECS Fargate Cluster
                  ┌─────────────────┐
                  │ Task 1 │ Task 2 │  2 tasks, single-AZ (POC)
                  │ Spring │ Spring │  0.25 vCPU, 512 MB RAM each
                  └─────────────────┘
                  [inside VPC — private subnet]
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
| Task size | 0.25 vCPU, 512 MB | 0.5 vCPU, 1 GB |
| Task count | 2 tasks, single-AZ | 2–8 tasks, Multi-AZ, auto-scaling |
| Redis | `cache.t3.micro`, no encryption | Replica in 2nd AZ, encryption in-transit |
| Deployment | Manual `docker push` + `ecs update-service` | CodePipeline → CodeBuild → Blue/Green via CodeDeploy |
| ALB | HTTP only (port 80) | HTTPS with ACM certificate + HTTP→HTTPS redirect |
| WAF | Skipped | WAF on CloudFront |
| Custom domain | Skipped — use ALB DNS directly | Route 53 + ACM cert |
| CloudWatch alarms | Skipped | Alarms on task count, CPU, RDS, Redis |
| Secrets | Plain env vars in task definition | AWS Secrets Manager |
| CI/CD | Manual push | CodePipeline auto-triggered on git push |
| Container registry | ECR (same for both) | ECR + image scanning + lifecycle policy |

---

## Why ECS Fargate over Lambda for this app?

- **No cold starts** — tasks are always running, respond immediately
- **Normal DB connection pooling** — HikariCP works as expected (Lambda's per-invocation lifecycle limits pooling)
- **Easier to debug** — `docker exec` into a task, standard container logs
- **Closer to a real enterprise deployment** — most companies run containerized workloads on ECS

> **🔴 PROD advantage:** Supports Blue/Green deployments with zero downtime, auto-scales from 2–8 tasks based on CPU, and integrates naturally with a full CI/CD pipeline.

---

## Prerequisites

```bash
# AWS CLI v2
aws --version

# Terraform 1.7+
terraform --version

# Docker Desktop — https://www.docker.com/products/docker-desktop/
docker --version

# Configure AWS credentials
aws configure
# Region: ap-south-1  |  Output: json

aws sts get-caller-identity
```

---

## Step 1 — Add the Dockerfile

Create `Dockerfile` in the project root (`currency-convertor/`):

```dockerfile
# Stage 1: Build
FROM maven:3.9-amazoncorretto-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# Stage 2: Runtime
FROM amazoncorretto:21-alpine
WORKDIR /app
COPY --from=build /app/target/currency-exchange-1.0.0.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

**Test locally before deploying:**
```bash
docker build -t xchange-api:local .
docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=local xchange-api:local
# Visit http://localhost:8080/actuator/health → {"status":"UP"}
```

> **🔴 PROD:** Add a non-root user in the Dockerfile (`adduser appuser`) and switch to it before `ENTRYPOINT`. Run containers as non-root for security.

---

## Step 2 — One-Time Manual Setup

### 2A. Create Terraform State Bucket

```bash
aws s3 mb s3://xchange-tfstate-yourname --region ap-south-1
```

Update `terraform/main.tf`:
```hcl
bucket = "xchange-tfstate-yourname"
```

### 2B. Verify Your Email in SES

> **✅ POC:** Verify a single email address.  
> **🔴 PROD:** Verify full domain + request production access.

1. AWS Console → SES → Verified Identities → Create Identity → **Email Address**
2. Click the verification link in your inbox
3. Also verify the recipient email (SES sandbox restricts outbound to verified addresses only)

### 2C. Create SES SMTP Credentials

1. AWS Console → SES → SMTP Settings → **Create SMTP Credentials**
2. Save the username and password immediately

### 2D. Generate JWT Keys

```bash
openssl genrsa -out private.pem 2048
openssl rsa -in private.pem -pubout -out public.pem
base64 -w 0 private.pem > jwt_private_key.txt
base64 -w 0 public.pem  > jwt_public_key.txt
```

### 2E. Get Alpha Vantage API Key (Free)

`https://www.alphavantage.co/support/#api-key`

---

## Step 3 — Terraform Files for ECS Fargate

The existing `terraform/` targets Lambda. Create these new files in the same folder.  
Keep the existing `vpc.tf`, `rds.tf`, `elasticache.tf`, `ses.tf`, `frontend.tf`, `variables.tf`, `main.tf` — they are reused as-is.

**Scale down `terraform/rds.tf` for POC:**
```hcl
instance_class          = "db.t3.micro"   # was db.t3.medium
multi_az                = false           # was true
allocated_storage       = 20              # was 50
backup_retention_period = 1               # was 7
```

**Scale down `terraform/elasticache.tf` for POC:**
```hcl
# Use single node, disable encryption for POC simplicity
node_type          = "cache.t3.micro"
num_cache_clusters = 1
# Comment out: at_rest_encryption_enabled, transit_encryption_enabled
```

**Skip for POC:** `terraform/waf.tf`, `terraform/monitoring.tf` (comment out or delete).

### New file: `terraform/ecr.tf`

```hcl
resource "aws_ecr_repository" "app" {
  name                 = "${var.project_name}-api"
  image_tag_mutability = "MUTABLE"
}

output "ecr_repository_url" {
  value = aws_ecr_repository.app.repository_url
}
```

### New file: `terraform/ecs.tf`

```hcl
# ── Cluster ──────────────────────────────────────────────────────────────────

resource "aws_ecs_cluster" "main" {
  name = "${var.project_name}-cluster"
}

# ── IAM: Task Execution Role ──────────────────────────────────────────────────

resource "aws_iam_role" "ecs_execution" {
  name = "${var.project_name}-ecs-exec-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{ Effect = "Allow", Principal = { Service = "ecs-tasks.amazonaws.com" }, Action = "sts:AssumeRole" }]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_execution" {
  role       = aws_iam_role.ecs_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# ── IAM: Task Role (app permissions) ─────────────────────────────────────────

resource "aws_iam_role" "ecs_task" {
  name = "${var.project_name}-ecs-task-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{ Effect = "Allow", Principal = { Service = "ecs-tasks.amazonaws.com" }, Action = "sts:AssumeRole" }]
  })
}

resource "aws_iam_role_policy" "ecs_task_ses" {
  name = "ses-send"
  role = aws_iam_role.ecs_task.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{ Effect = "Allow", Action = ["ses:SendEmail", "ses:SendRawEmail"], Resource = "*" }]
  })
}

# ── CloudWatch Log Group ──────────────────────────────────────────────────────

resource "aws_cloudwatch_log_group" "ecs" {
  name              = "/ecs/${var.project_name}"
  retention_in_days = 7   # short retention for POC — PROD: 90 days
}

# ── Security Group for ECS Tasks ──────────────────────────────────────────────

resource "aws_security_group" "ecs_tasks" {
  name   = "${var.project_name}-ecs-sg"
  vpc_id = aws_vpc.main.id

  ingress {
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }
  egress {
    from_port = 0; to_port = 0; protocol = "-1"; cidr_blocks = ["0.0.0.0/0"]
  }
}

# ── Task Definition ───────────────────────────────────────────────────────────

resource "aws_ecs_task_definition" "app" {
  family                   = "${var.project_name}-api"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "256"    # 0.25 vCPU — POC only. PROD: 512
  memory                   = "512"    # 512 MB — POC only. PROD: 1024

  execution_role_arn = aws_iam_role.ecs_execution.arn
  task_role_arn      = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([{
    name      = "api"
    image     = "${aws_ecr_repository.app.repository_url}:latest"
    essential = true

    portMappings = [{ containerPort = 8080, protocol = "tcp" }]

    environment = [
      { name = "SPRING_PROFILES_ACTIVE",  value = "aws" },
      { name = "DB_URL",                  value = "jdbc:postgresql://${aws_db_instance.postgres.endpoint}/currency_exchange" },
      { name = "DB_USERNAME",             value = var.db_username },
      { name = "DB_PASSWORD",             value = var.db_password },
      { name = "REDIS_HOST",              value = aws_elasticache_cluster.redis.cache_nodes[0].address },
      { name = "REDIS_PORT",              value = "6379" },
      { name = "SES_SMTP_USERNAME",       value = var.ses_smtp_username },
      { name = "SES_SMTP_PASSWORD",       value = var.ses_smtp_password },
      { name = "JWT_PRIVATE_KEY",         value = var.jwt_private_key },
      { name = "JWT_PUBLIC_KEY",          value = var.jwt_public_key },
      { name = "EMAIL_FROM_ADDRESS",      value = var.email_from_address },
      { name = "TRANSACTION_ADMIN_EMAIL", value = var.transaction_admin_email },
      { name = "ALPHA_VANTAGE_API_KEY",   value = var.alpha_vantage_api_key }
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.ecs.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "ecs"
      }
    }

    healthCheck = {
      command     = ["CMD-SHELL", "wget -qO- http://localhost:8080/actuator/health || exit 1"]
      interval    = 30; timeout = 5; retries = 3; startPeriod = 60
    }
  }])
}

# ── ECS Service ───────────────────────────────────────────────────────────────
# POC: rolling update deployment, single AZ
# PROD: Blue/Green via CodeDeploy across 2 AZs

resource "aws_ecs_service" "app" {
  name            = "${var.project_name}-api"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = 2
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = [aws_subnet.private_a.id]   # single AZ for POC. PROD: both AZs
    security_groups  = [aws_security_group.ecs_tasks.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = "api"
    container_port   = 8080
  }

  depends_on = [aws_lb_listener.http]
}
```

### New file: `terraform/alb.tf`

```hcl
# POC: HTTP only on port 80 — no SSL cert needed
# PROD: HTTPS on 443 with ACM certificate, redirect HTTP → HTTPS

resource "aws_security_group" "alb" {
  name   = "${var.project_name}-alb-sg"
  vpc_id = aws_vpc.main.id

  ingress {
    from_port = 80; to_port = 80; protocol = "tcp"; cidr_blocks = ["0.0.0.0/0"]
  }
  egress {
    from_port = 0; to_port = 0; protocol = "-1"; cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_lb" "main" {
  name               = "${var.project_name}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = [aws_subnet.public_a.id, aws_subnet.public_b.id]
}

resource "aws_lb_target_group" "app" {
  name        = "${var.project_name}-tg"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    path                = "/actuator/health"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 30
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
}

output "alb_url" {
  value = "http://${aws_lb.main.dns_name}"
}
```

---

## Step 4 — Fill In Secrets

```bash
cd terraform
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform/terraform.tfvars`:
```hcl
db_username             = "xchange_admin"
db_password             = "Assignment2024!"
ses_smtp_username       = "AKIA..."
ses_smtp_password       = "..."
email_from_address      = "you@gmail.com"         # verified SES sender
transaction_admin_email = "you@gmail.com"
jwt_private_key         = "<contents of jwt_private_key.txt>"
jwt_public_key          = "<contents of jwt_public_key.txt>"
alpha_vantage_api_key   = "YOUR_KEY"
alert_email             = "you@gmail.com"
```

> **🔴 PROD:** Secrets go in AWS Secrets Manager. Task definition references them with `valueFrom` instead of plain `value`. Never store credentials in Terraform state files.

---

## Step 5 — Deploy Infrastructure

```bash
cd terraform
terraform init
terraform plan
terraform apply   # ~10–15 minutes. Type "yes"
```

**Note these outputs:**
```bash
terraform output
# ecr_repository_url  → push Docker images here
# alb_url             → backend API endpoint
# cloudfront_url      → frontend URL
```

---

## Step 6 — Build and Push the Docker Image

```bash
# Authenticate Docker to ECR
aws ecr get-login-password --region ap-south-1 | \
  docker login --username AWS --password-stdin <ecr_repository_url>

# Build
docker build -t xchange-api:latest .

# Tag and push
docker tag xchange-api:latest <ecr_repository_url>:latest
docker push <ecr_repository_url>:latest
```

---

## Step 7 — Start the ECS Service

```bash
# Force ECS to pull the new image and restart tasks
aws ecs update-service \
  --cluster xchange-cluster \
  --service xchange-api \
  --force-new-deployment \
  --region ap-south-1

# Wait until both tasks are running and healthy (~2 minutes)
aws ecs wait services-stable \
  --cluster xchange-cluster \
  --services xchange-api \
  --region ap-south-1
```

**Verify:**
```bash
curl http://<alb_url>/actuator/health
# {"status":"UP"}

curl http://<alb_url>/api/rates
# JSON array — first call runs Flyway migrations
```

> **🔴 PROD:** Each code change goes through CodePipeline: git push → CodeBuild builds image → CodeDeploy does Blue/Green swap → zero downtime. Here we just re-run the docker push + update-service commands manually.

---

## Step 8 — Deploy the Frontend

**Set the API URL** — edit `frontend/src/environments/environment.prod.ts`:
```typescript
export const environment = {
  production: true,
  apiUrl: 'http://<alb_url>'   // from terraform output
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

**Update CORS** in `terraform/ecs.tf` task definition environment — add:
```hcl
{ name = "ALLOWED_ORIGINS", value = "https://<cloudfront_url>" }
```

And verify `SecurityConfig.java` uses this env var (or hardcode the CloudFront URL temporarily for the POC).

Then: `terraform apply`

---

## Step 9 — Quick Verification

```
□ https://<cloudfront_url>  loads Angular login page
□ Register → Login → OTP email arrives → Dashboard
□ Convert < $100 → APPROVED
□ Convert ≥ $100 → PENDING_APPROVAL
□ Admin approves → email sent
□ ECS Console: 2 tasks in RUNNING state
□ CloudWatch logs: /ecs/xchange — no errors
```

---

## Redeploying After a Code Change (Manual for POC)

```bash
# 1. Build and push new image
mvn clean package -DskipTests
docker build -t <ecr_repository_url>:latest .
docker push <ecr_repository_url>:latest

# 2. Trigger rolling update
aws ecs update-service \
  --cluster xchange-cluster \
  --service xchange-api \
  --force-new-deployment \
  --region ap-south-1
```

> **🔴 PROD:** This is replaced entirely by CodePipeline. A `git push` to main triggers the full build→test→Blue/Green deploy pipeline automatically.

---

## Estimated Cost for POC

| Service | POC Config | Monthly |
|---|---|---|
| ECS Fargate | 2 tasks × 0.25 vCPU × 512 MB | ~$10 |
| ALB | 1 ALB, minimal traffic | ~$18 |
| RDS | db.t3.micro, single-AZ, 20 GB | ~$15 |
| ElastiCache | cache.t3.micro, single node | ~$12 |
| NAT Gateway | ~1 GB data | ~$4 |
| ECR | < 1 GB storage | ~$0 |
| S3 + CloudFront | Minimal | ~$1 |
| SES | < 1,000 emails | ~$0 |
| **Total** | | **~$60/month** |

> **Note:** ALB has a minimum charge (~$18/month) regardless of traffic — this is the main cost driver for ECS vs Lambda for a POC. Lambda is cheaper at low traffic.

> **Tear down when not using:** `terraform destroy` removes everything. Restore with `terraform apply`.

---

## Deployment Order Summary

```
1.  Add Dockerfile to project root
2.  Create S3 state bucket
3.  Verify email(s) in SES
4.  Create SES SMTP credentials
5.  Generate JWT keys
6.  Get Alpha Vantage API key
7.  Create terraform/ecr.tf, ecs.tf, alb.tf
8.  Scale down rds.tf, elasticache.tf for POC
9.  Fill terraform.tfvars
10. terraform init + apply
11. docker build → docker push to ECR
12. aws ecs update-service --force-new-deployment
13. Update environment.prod.ts → ng build:prod → s3 sync
14. Update CORS → terraform apply
15. Test end-to-end
```
