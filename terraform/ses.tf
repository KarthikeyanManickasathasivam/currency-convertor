# Verify your sender domain/email in SES before deployment
# Replace with your actual domain or email address

resource "aws_ses_domain_identity" "main" {
  domain = "yourdomain.com"  # TODO: replace with your domain
}

resource "aws_ses_domain_dkim" "main" {
  domain = aws_ses_domain_identity.main.domain
}

output "ses_dkim_tokens" {
  value       = aws_ses_domain_dkim.main.dkim_tokens
  description = "Add these as CNAME records in your DNS to verify DKIM for SES"
}
