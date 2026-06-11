variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-south-1"
}

variable "project_name" {
  description = "Project name prefix used in all resource names"
  type        = string
  default     = "xchange"
}

variable "environment" {
  description = "Deployment environment (prod / staging)"
  type        = string
  default     = "prod"
}

# ── Database ──────────────────────────────────────────────────────────────────

variable "db_username" {
  description = "RDS master username"
  type        = string
  sensitive   = true
}

variable "db_password" {
  description = "RDS master password (min 16 chars)"
  type        = string
  sensitive   = true
}

# ── Email / SES ───────────────────────────────────────────────────────────────

variable "ses_smtp_username" {
  description = "SES SMTP username (from IAM → SES SMTP Credentials)"
  type        = string
  sensitive   = true
}

variable "ses_smtp_password" {
  description = "SES SMTP password"
  type        = string
  sensitive   = true
}

variable "email_from_address" {
  description = "Verified SES sender address used in all outbound emails"
  type        = string
  default     = "no-reply@yourdomain.com"   # TODO: replace with your domain
}

variable "transaction_admin_email" {
  description = "Admin email to notify when a transaction needs approval"
  type        = string
  default     = "admin@yourdomain.com"       # TODO: replace with actual admin email
}

# ── JWT ───────────────────────────────────────────────────────────────────────

variable "jwt_private_key" {
  description = "Base64-encoded RSA private key for signing JWT (RS256)"
  type        = string
  sensitive   = true
}

variable "jwt_public_key" {
  description = "Base64-encoded RSA public key for verifying JWT (RS256)"
  type        = string
  sensitive   = true
}

# ── External APIs ─────────────────────────────────────────────────────────────

variable "alpha_vantage_api_key" {
  description = "Alpha Vantage API key (fallback exchange rate source)"
  type        = string
  sensitive   = true
}

# ── Lambda ────────────────────────────────────────────────────────────────────

variable "lambda_jar_path" {
  description = "Path to the Spring Boot fat JAR built by: mvn clean package -DskipTests"
  type        = string
  default     = "../target/currency-exchange-1.0.0.jar"   # matches Maven artifactId-version
}

# ── Alerting ──────────────────────────────────────────────────────────────────

variable "alert_email" {
  description = "Email address to receive CloudWatch alarm notifications"
  type        = string
  default     = "ops@yourdomain.com"   # TODO: replace with your ops email
}
