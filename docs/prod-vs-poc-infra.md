# POC vs Production Infrastructure — Currency Exchange App

**Region:** ap-south-1 (Mumbai)
**Stack:** Spring Boot 3.3.x · Java 21 · Angular 18 · PostgreSQL 16 · Redis 7.x · Amazon SES

This document compares the minimal POC/assignment setup against a production-grade AWS deployment. For each layer it explains *what* changes and *why* the change matters — not just a list of services.

---

## 1. Compute Layer

| | POC | Production |
|---|---|---|
| Platform | Elastic Beanstalk, single instance | ECS Fargate, 2–8 tasks |
| Instance/Size | t3.micro (2 vCPU, 1 GB RAM) | 0.5 vCPU / 1 GB per task (scalable) |
| Availability | Single AZ | Multi-AZ task placement |
| Auto-scaling | None | Target-tracking on CPU ≥ 60% |
| Deployment | Rolling (brief downtime) | Blue/Green via CodeDeploy |

**Why it matters:**

A single t3.micro is a single point of failure. If the EC2 instance goes down — for any reason, including an AWS hardware event — the entire app is offline. There is no standby, no failover.

Under real traffic, a t3.micro with Spring Boot + JVM overhead + connection pools saturates quickly. JVM alone needs ~300–400 MB at startup; add Tomcat threads and HikariCP and the 1 GB limit becomes a hard ceiling. The instance will start swapping to disk and response times blow up.

ECS Fargate removes the EC2 management burden entirely — no patching, no SSH, no AMIs. Tasks are placed across two Availability Zones, so an AZ outage takes out at most half your capacity (ECS replaces the lost tasks in the healthy AZ automatically). Auto-scaling adds tasks when CPU climbs, handling traffic spikes without manual intervention.

Connection pooling also behaves better across multiple smaller tasks than one large instance: each Fargate task runs HikariCP with a smaller pool (e.g., 10 connections), and the aggregate pool across 4 tasks (40 connections) is well within RDS limits. A single bloated instance tends to open hundreds of connections and hit `max_connections` on PostgreSQL.

---

## 2. Database — RDS PostgreSQL

| | POC | Production |
|---|---|---|
| Instance | db.t3.micro | db.t3.medium (or db.r6g.large for high traffic) |
| Availability | Single-AZ | Multi-AZ (standby in second AZ) |
| Storage | 20 GB gp2 | 50 GB gp3, auto-scaling enabled |
| Backups | 1-day automated backup | 7-day automated backups + point-in-time recovery |
| Encryption | Optional | Encryption at rest (AES-256), in-transit TLS enforced |
| Read Replicas | None | 1 read replica for reporting/admin queries |

**Why it matters:**

**Multi-AZ** means AWS maintains a synchronous standby replica in a second Availability Zone. Every write to the primary is synchronously replicated before the write is acknowledged. If the primary fails — hardware fault, OS crash, AZ outage — RDS automatically promotes the standby and updates the DNS endpoint. Your application reconnects to the same endpoint. Failover takes 60–120 seconds, which is far better than the hours it would take to restore from a snapshot.

On a single-AZ setup, an AZ failure means your data is unreachable until AWS recovers the hardware. For a financial application holding transaction records, that is unacceptable.

**db.t3.micro** runs out of memory quickly. PostgreSQL's `shared_buffers` default is 128 MB; on a 1 GB instance that leaves almost nothing for sort operations, join buffers, and the OS page cache. Under moderate query load, you will see `OOM killed` events or extreme query latency due to constant page faults. db.t3.medium (2 vCPU, 4 GB) gives the database room to actually cache hot data.

**Point-in-time recovery** lets you restore to any second within the backup window — useful if a bug causes data corruption and you need to roll back to "just before the bad deploy" rather than "yesterday's snapshot."

**gp3 over gp2**: gp3 provides 3,000 IOPS baseline with no extra cost and you can provision more IOPS independently of storage size. gp2 ties IOPS to storage (3 IOPS/GB) so small databases are I/O-constrained.

---

## 3. Cache — ElastiCache Redis

| | POC | Production |
|---|---|---|
| Instance | cache.t3.micro | cache.r6g.large |
| Topology | Single node | Primary + replica in second AZ |
| Encryption | None | TLS in-transit + AES-256 at-rest |
| Failover | None (manual intervention) | Automatic failover to replica |
| Auth | None | Redis AUTH token required |

**Why it matters:**

Redis in this app is not just a performance cache. It holds:
- **MFA OTP codes** — 6-digit codes with 5-minute TTL, verified on every login
- **JWT blacklist** — invalidated tokens stored here until expiry
- **Exchange rate cache** — 5-minute TTL on live rate data

If Redis goes down on the POC setup, the consequences are severe:
1. Users cannot complete MFA — the OTP code is gone. Login is broken for everyone mid-session.
2. Tokens that were explicitly logged out (blacklisted) are no longer blacklisted. Any attacker holding a stolen JWT can now use it freely until it expires (up to 15 minutes for access tokens).
3. Every request hits the external exchange rate API directly, which likely trips the rate limiter and causes the circuit breaker to open.

With a primary + replica topology, Redis replicates asynchronously to the replica. If the primary fails, ElastiCache promotes the replica automatically (typically in under 60 seconds). The application reconnects and OTP + blacklist data is intact (minus at most a few seconds of writes).

**Encryption matters** because OTP codes and session state are security-sensitive. Sending them unencrypted inside a VPC is acceptable for dev, but violates PCI-DSS and most enterprise security policies. TLS in-transit and encryption at-rest are table stakes for financial apps.

**cache.t3.micro** (0.5 GB) fills up fast under any real load. cache.r6g.large (13 GB) is memory-optimized and sized to hold the full working set without eviction.

---

## 4. Networking — VPC

| | POC | Production |
|---|---|---|
| Subnets | 1 public + 1 private | 3-tier: public (ALB), private-app (ECS), private-data (RDS/Redis) across 2 AZs |
| AZ coverage | Single AZ | 2 AZs minimum |
| NAT Gateway | 1 (single AZ) | 1 per AZ |
| Flow Logs | Off | VPC Flow Logs → CloudWatch Logs |
| Network ACLs | Default | Custom NACLs — deny unexpected traffic patterns |

**Why it matters:**

The 3-tier subnet model is defence in depth. Even if an attacker compromises the ALB layer (public subnet), they cannot directly reach the database — the private-data subnet only accepts connections from the private-app subnet on the PostgreSQL port (5432) and Redis port (6379).

In the POC, if the single NAT Gateway fails or the single private subnet has an issue, all outbound traffic (external exchange rate API calls, SES email) stops. In production, each AZ has its own NAT Gateway so an AZ-level failure only affects tasks in that AZ; the other AZ keeps running normally.

VPC Flow Logs capture all accepted and rejected traffic at the network interface level. This is required for security incident investigation ("did anything unexpected try to reach the database?") and is often required by compliance audits (SOC 2, PCI-DSS).

Separating ALB, ECS, and data tiers into distinct subnets also makes security group rules explicit and auditable — you can see exactly which resource is allowed to talk to which on which port, with no ambiguity.

---

## 5. Security

| | POC | Production |
|---|---|---|
| WAF | None | AWS WAF on CloudFront: SQLi, XSS, rate-based rules, IP reputation list |
| Credentials | Plaintext in env vars / terraform.tfvars | AWS Secrets Manager, referenced by ARN |
| TLS | HTTP on Elastic Beanstalk (no cert) | HTTPS everywhere — ACM cert on ALB and CloudFront |
| Audit | None | CloudTrail (all API calls), AWS Config (resource compliance) |
| Compliance checks | None | AWS Config rules — MFA on IAM, no public S3 buckets, encryption enabled |

**Why it matters:**

**WAF** sits in front of CloudFront and inspects every HTTP request before it reaches your backend. The managed rule sets block:
- **SQL injection** — payloads like `' OR 1=1--` in query parameters or JSON bodies
- **XSS** — script injection in form fields
- **Rate-based rules** — a single IP making 2,000 requests in 5 minutes gets auto-blocked; this stops credential stuffing attacks on the `/auth/login` endpoint
- **IP reputation lists** — known malicious IPs, Tor exit nodes, botnet sources

Without WAF, these attacks reach Spring Security and your application code. Spring Security and Jakarta validation catch many of them, but WAF stops them at the edge before they consume any application resources.

**AWS Secrets Manager** replaces every plaintext credential in your infrastructure. Instead of `DB_PASSWORD=mypassword` in an environment variable or a tfvars file, your ECS task definition contains `secretsFrom: arn:aws:secretsmanager:...`. ECS fetches the secret at task startup and injects it as an env var — the plaintext never appears in Terraform state, CloudFormation templates, or CodeBuild logs.

Critically, Secrets Manager supports **automatic rotation** for RDS passwords. It rotates the password on a schedule, updates the secret, and your app picks up the new value on the next task restart — zero manual intervention, zero risk of an old credential lingering in a leaked file.

**CloudTrail** logs every AWS API call — who assumed which IAM role, who called `GetSecretValue`, who modified a security group. This is the audit trail that proves (or disproves) "was this action authorized?" after a security incident.

---

## 6. Frontend Hosting

| | POC | Production |
|---|---|---|
| Hosting | S3 + CloudFront | S3 + CloudFront + Route 53 + ACM cert |
| Domain | Default CloudFront domain (`*.cloudfront.net`) | Custom domain (`app.yourcompany.com`) |
| S3 Versioning | Off | On — protects against accidental overwrites |
| CloudFront Logging | Off | Access logs → S3, 90-day retention |
| Cache Invalidation | Manual | Automated via CodePipeline on deploy |

**Why it matters:**

A default CloudFront domain (`d1abc123.cloudfront.net`) works technically but is not acceptable for a real product. Users do not trust URLs they do not recognise. A custom domain requires Route 53 + ACM; once configured, ACM automatically renews the TLS certificate before it expires — no manual renewal, no cert expiry incidents.

S3 versioning means every deployment of the Angular build creates a new version of each file in S3 rather than overwriting in place. If a bad build goes out, you can roll back to the previous version instantly from the S3 console.

CloudFront access logs record every request to the SPA: IP address, user agent, requested URL, cache hit/miss, status code. For compliance and security investigations, this data is essential.

---

## 7. CI/CD Pipeline

| | POC | Production |
|---|---|---|
| Source | Local filesystem | GitHub (or CodeCommit) — PR-gated |
| Build | `mvn package` locally | CodeBuild: Maven build + Angular build |
| Tests | Run manually (or skipped) | Unit tests + integration tests gate the pipeline |
| Deploy | `eb deploy` from local machine | CodeDeploy Blue/Green on ECS |
| Rollback | Redeploy previous JAR manually | Instant traffic shift back to Blue environment |
| Frontend deploy | `aws s3 sync` manually | CodePipeline uploads to S3 + CloudFront invalidation |

**Why it matters:**

Manual deployments have two fundamental problems. First, they cause downtime. `eb deploy` performs a rolling restart that takes the app offline for 30–60 seconds. For a financial app, that is unacceptable.

Second, there is no test gate. A developer can `eb deploy` a build with failing tests. In production, the CodePipeline gate means a failing test suite stops the deployment before anything reaches prod.

**Blue/Green deployment** works by maintaining two identical ECS environments (Blue = current prod, Green = new version). Traffic is shifted from Blue to Green only after health checks pass. If something goes wrong, you shift traffic back to Blue immediately — zero redeployment required, near-instant rollback. Compare this to the POC where a bad deploy requires re-uploading and redeploying the previous JAR.

The pipeline also provides a complete audit trail: every deployment records who triggered it, which git commit was deployed, which tests passed, and what the deployment outcome was.

---

## 8. Secrets Management

| | POC | Production |
|---|---|---|
| DB credentials | Plaintext in `terraform.tfvars` or EB env vars | Secrets Manager — encrypted, referenced by ARN |
| Redis auth | None | Secrets Manager AUTH token |
| JWT private key | File on EC2 or hardcoded | Secrets Manager binary secret |
| SES SMTP creds | Plaintext env var | Secrets Manager |
| Rotation | Manual | Automatic rotation for RDS (Secrets Manager Lambda rotator) |
| Audit | None | Every `GetSecretValue` call logged in CloudTrail |

**Why it matters:**

If `terraform.tfvars` is ever committed to git (even briefly), your database password is in git history forever — rotating the password is not enough, because the commit history must be purged from every fork and clone. This happens more often than it should.

AWS Secrets Manager stores credentials encrypted using a KMS key. Access is controlled by IAM policy — only the ECS task's IAM role can call `GetSecretValue` for its own secrets. A developer's laptop cannot fetch the production DB password even if they have AWS console access, unless the IAM policy explicitly allows it.

Automatic rotation is particularly valuable: Secrets Manager rotates the RDS master password on a configurable schedule (e.g., every 30 days), updates the secret value, and your application uses the new password on the next container restart. No human touches the password after initial setup.

---

## 9. Monitoring and Observability

| | POC | Production |
|---|---|---|
| Logs | CloudWatch Logs (default EB config) | Structured JSON logs, 90-day retention → S3 Glacier archive |
| Metrics | Basic EC2 metrics | Custom metrics: API latency P95, error rate, RDS CPU, Redis memory, ECS task count |
| Alarms | None | SNS → email + PagerDuty on breach |
| Tracing | None | AWS X-Ray — end-to-end request tracing |
| Dashboards | None | CloudWatch dashboard: business KPIs + infra health |
| Alerting thresholds | N/A | API latency P95 > 2s, error rate > 1%, RDS CPU > 80%, Redis memory > 75%, ECS task count < 2 |

**Why it matters:**

Without monitoring, you learn about production problems from user complaints. With monitoring, your alerting system tells you before most users notice.

Concrete examples for this app:
- **ECS task count < 2 alarm**: if a deployment bug causes tasks to crash-loop, ECS keeps restarting them but the count never stabilises. The alarm fires within minutes, before the load balancer starts returning 503s.
- **API latency P95 > 2s**: the exchange rate circuit breaker might open silently. Latency climbing tells you the external API is degraded before the circuit opens completely.
- **Redis memory > 75%**: warns before Redis starts evicting keys — losing OTP codes mid-session is a confusing failure mode that is hard to diagnose without this metric.

**X-Ray tracing** gives you a flame graph for any request: time spent in Spring controllers, time in JPA queries, time waiting for Redis, time calling the external exchange rate API. When a transaction takes 4 seconds, X-Ray shows you exactly where the 4 seconds went without needing to reproduce the issue.

Structured JSON logs (using Logback with logstash-logback-encoder) mean logs are queryable in CloudWatch Insights as structured data — `fields correlationId, userId, transactionId | filter level = "ERROR"` — rather than grepping unstructured text.

---

## 10. Cost Comparison

All prices are approximate for ap-south-1, low-to-medium traffic (steady state, not burst).

| Service | POC (Elastic Beanstalk) | Production (ECS Fargate) |
|---|---|---|
| Compute | $8/mo — 1x t3.micro EC2 (EB-managed) | $40–60/mo — 2–4 Fargate tasks (0.5 vCPU/1 GB each) |
| RDS PostgreSQL | $15/mo — db.t3.micro, single-AZ, 20 GB gp2 | $60–80/mo — db.t3.medium, Multi-AZ, 50 GB gp3 |
| ElastiCache Redis | $12/mo — cache.t3.micro, single node | $45–55/mo — cache.r6g.large, primary + replica |
| ALB | $0 (EB uses a shared LB or EC2 directly) | $18/mo — Application Load Balancer |
| NAT Gateway | $5/mo — 1 NAT Gateway, minimal data | $15/mo — 2 NAT Gateways (one per AZ) |
| CloudFront + S3 | $1–2/mo | $3–5/mo (with logging + versioning) |
| Secrets Manager | $0 | $2/mo — ~4 secrets at $0.40/secret/month |
| WAF | $0 | $10/mo — WebACL + managed rules |
| CloudWatch | $0 (basic only) | $10–15/mo — dashboards, alarms, log retention |
| Route 53 | $0 (no custom domain) | $1/mo — hosted zone |
| CodePipeline + CodeBuild | $0 (manual deploys) | $5–10/mo — pipeline + build minutes |
| **Total** | **~$41–43/mo** | **~$209–261/mo** |

**Cost summary:**

- POC: ~$40/month — viable for a demo or assignment, unacceptable for real financial data
- Production: ~$220/month at low-medium traffic — the delta is mostly Multi-AZ redundancy (RDS standby, Redis replica, second NAT Gateway) and the WAF/monitoring layer

At higher traffic, ECS auto-scaling adds tasks and RDS may need a read replica, pushing costs to $350–500/month. This is normal for a financial SaaS product and is offset by Savings Plans or Reserved Instances (RDS RI alone cuts the DB cost ~40%).

---

## 11. Migration Path (POC to Production)

Migrating all at once is high-risk and expensive. The recommended order prioritises the highest-value changes first.

**Step 1 — CI/CD (CodePipeline)**
Biggest quality-of-life improvement with low infrastructure risk. Connect GitHub → CodeBuild → `eb deploy` first. You get automated tests gating deployments and a repeatable build process without changing any infrastructure.

**Step 2 — Move to ECS Fargate**
Containerise the Spring Boot app (multi-stage Dockerfile). Deploy to ECS with a single task initially (not yet Multi-AZ). This removes the Elastic Beanstalk abstraction and gives you direct control over the runtime environment. Update CodePipeline to deploy to ECS.

**Step 3 — Multi-AZ for RDS and Redis**
Take a snapshot of the POC RDS instance, restore it as a Multi-AZ instance. Upgrade the ElastiCache node to a primary + replica cluster. These are the two changes with the highest impact on data durability. Brief maintenance window required for RDS (< 30 min for snapshot restore + DNS update).

**Step 4 — WAF + Secrets Manager + HTTPS**
Migrate credentials from env vars to Secrets Manager. Attach an ACM certificate to the ALB (free). Enable WAF on CloudFront. Update the Angular environment to use the HTTPS ALB endpoint. These changes have zero application code impact.

**Step 5 — Full Monitoring + Alerting**
Create CloudWatch dashboards, configure alarms, enable X-Ray in the Spring Boot app (`spring-cloud-starter-aws-messaging` + X-Ray SDK), set up SNS topic for alerts. Configure structured JSON logging and 90-day retention. This step should be done last because you need the other layers stable before you can establish meaningful baselines for alarm thresholds.

---

## 12. Summary Comparison Table

| Dimension | POC | Production | Key Reason for Change |
|---|---|---|---|
| Compute | EB single-instance t3.micro | ECS Fargate 2–8 tasks, Multi-AZ | Single point of failure; no scaling |
| Compute availability | Single AZ | Multi-AZ task placement | AZ failure takes down POC entirely |
| Deployments | Manual `eb deploy`, ~60s downtime | Blue/Green, zero downtime + instant rollback | Downtime + human error in prod |
| Database | db.t3.micro, single-AZ | db.t3.medium, Multi-AZ, gp3 | Memory exhaustion; no HA failover |
| DB backups | 1-day backup | 7-day + point-in-time recovery | Financial data requires granular recovery |
| DB encryption | Optional | At-rest + in-transit TLS enforced | Compliance requirement |
| Redis | cache.t3.micro, single node | cache.r6g.large, primary + replica | OTP/JWT blacklist loss = security incident |
| Redis encryption | None | TLS in-transit + AES-256 at-rest | Security-critical data (OTPs, session state) |
| Redis failover | None | Automatic failover < 60s | Login breaks if Redis is down |
| VPC design | Single private subnet | 3-tier across 2 AZs | Defence in depth; regulatory compliance |
| NAT Gateway | 1 (single AZ) | 1 per AZ | Single NAT failure cuts all outbound traffic |
| VPC Flow Logs | Off | On → CloudWatch | Security audit and incident investigation |
| WAF | None | CloudFront WAF — SQLi, XSS, rate limiting | Attacks reach app code without WAF |
| Secrets | Plaintext in env vars / tfvars | AWS Secrets Manager + automatic rotation | Leaked tfvars = full DB compromise |
| TLS/HTTPS | HTTP only (no cert on EB) | HTTPS everywhere via ACM | Data in transit unencrypted |
| Audit logging | None | CloudTrail + AWS Config | Compliance and incident forensics |
| Frontend domain | Default CloudFront domain | Custom domain via Route 53 + ACM | Branding; automatic cert renewal |
| S3 versioning | Off | On | Instant rollback of bad frontend deploys |
| Monitoring | Basic CloudWatch logs | Dashboards + alarms + X-Ray tracing | Know about problems before users do |
| Alerting | None | SNS → email/PagerDuty on metric breach | Proactive incident response |
| Log retention | Default (short) | 90-day active → S3 Glacier | Compliance; historical debugging |
| CI/CD | Manual | CodePipeline: Source → Build → Test → Deploy | Test gate; audit trail; no human error |
| Cost | ~$40/month | ~$220/month | HA, security, and observability have a price |
