# WAF for API Gateway — NFR-06: SQL injection, XSS, rate-based rules
resource "aws_wafv2_web_acl" "api" {
  name  = "${var.project_name}-api-waf"
  scope = "REGIONAL"   # REGIONAL for API Gateway; CLOUDFRONT must be us-east-1

  default_action { allow {} }

  # Rule 1: AWS Managed — common web exploits (SQLi, XSS, path traversal)
  rule {
    name     = "AWSManagedRulesCommonRuleSet"
    priority = 1
    override_action { none {} }
    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "CommonRuleSet"
      sampled_requests_enabled   = true
    }
  }

  # Rule 2: AWS Managed — known bad inputs (Log4Shell, SSRF, Spring4Shell)
  rule {
    name     = "AWSManagedRulesKnownBadInputsRuleSet"
    priority = 2
    override_action { none {} }
    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesKnownBadInputsRuleSet"
        vendor_name = "AWS"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "KnownBadInputs"
      sampled_requests_enabled   = true
    }
  }

  # Rule 3: AWS Managed — SQL injection
  rule {
    name     = "AWSManagedRulesSQLiRuleSet"
    priority = 3
    override_action { none {} }
    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesSQLiRuleSet"
        vendor_name = "AWS"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "SQLiRuleSet"
      sampled_requests_enabled   = true
    }
  }

  # Rule 4: Rate limiting — 300 requests per 5 minutes per IP (WAF adds a first layer;
  # application-level Bucket4j rate limiting is the second layer)
  rule {
    name     = "RateLimitPerIP"
    priority = 4
    action { block {} }
    statement {
      rate_based_statement {
        limit              = 300
        aggregate_key_type = "IP"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "RateLimitPerIP"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${var.project_name}-api-waf"
    sampled_requests_enabled   = true
  }

  tags = { Name = "${var.project_name}-api-waf" }
}

# Attach WAF to the API Gateway stage
resource "aws_wafv2_web_acl_association" "api" {
  resource_arn = aws_apigatewayv2_stage.default.arn
  web_acl_arn  = aws_wafv2_web_acl.api.arn
}

# WAF for CloudFront — must be in us-east-1 (CloudFront is a global service)
# This is a separate provider alias; add to main.tf if you want CloudFront WAF:
#
#   provider "aws" {
#     alias  = "us_east_1"
#     region = "us-east-1"
#   }
#
# Then reference it in aws_wafv2_web_acl with provider = aws.us_east_1 and scope = "CLOUDFRONT"

resource "aws_wafv2_web_acl" "frontend" {
  name     = "${var.project_name}-frontend-waf"
  scope    = "CLOUDFRONT"
  provider = aws.us_east_1

  default_action { allow {} }

  rule {
    name     = "AWSManagedRulesCommonRuleSet"
    priority = 1
    override_action { none {} }
    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "FrontendCommonRuleSet"
      sampled_requests_enabled   = true
    }
  }

  rule {
    name     = "FrontendRateLimit"
    priority = 2
    action { block {} }
    statement {
      rate_based_statement {
        limit              = 500
        aggregate_key_type = "IP"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "FrontendRateLimit"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${var.project_name}-frontend-waf"
    sampled_requests_enabled   = true
  }

  tags = { Name = "${var.project_name}-frontend-waf" }
}
