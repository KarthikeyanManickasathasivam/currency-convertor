# ── S3 Bucket (private — CloudFront is the only allowed reader) ──────────────

resource "aws_s3_bucket" "frontend" {
  bucket = "${var.project_name}-frontend-${var.environment}"
  tags   = { Name = "${var.project_name}-frontend" }
}

resource "aws_s3_bucket_public_access_block" "frontend" {
  bucket                  = aws_s3_bucket.frontend.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "frontend" {
  bucket = aws_s3_bucket.frontend.id
  versioning_configuration { status = "Enabled" }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "frontend" {
  bucket = aws_s3_bucket.frontend.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# ── CloudFront Origin Access Control ─────────────────────────────────────────

resource "aws_cloudfront_origin_access_control" "frontend" {
  name                              = "${var.project_name}-oac"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# S3 bucket policy: only CloudFront can read objects
resource "aws_s3_bucket_policy" "frontend" {
  bucket = aws_s3_bucket.frontend.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "AllowCloudFrontServicePrincipal"
      Effect    = "Allow"
      Principal = { Service = "cloudfront.amazonaws.com" }
      Action    = "s3:GetObject"
      Resource  = "${aws_s3_bucket.frontend.arn}/*"
      Condition = {
        StringEquals = {
          "AWS:SourceArn" = aws_cloudfront_distribution.frontend.arn
        }
      }
    }]
  })
}

# ── CloudFront Distribution ───────────────────────────────────────────────────

resource "aws_cloudfront_distribution" "frontend" {
  enabled             = true
  default_root_object = "index.html"
  price_class         = "PriceClass_All"
  web_acl_id          = aws_wafv2_web_acl.frontend.arn

  # S3 origin for the Angular SPA
  origin {
    domain_name              = aws_s3_bucket.frontend.bucket_regional_domain_name
    origin_id                = "S3-${var.project_name}"
    origin_access_control_id = aws_cloudfront_origin_access_control.frontend.id
  }

  default_cache_behavior {
    target_origin_id       = "S3-${var.project_name}"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true

    forwarded_values {
      query_string = false
      cookies { forward = "none" }
    }

    # Long TTL for hashed assets (main.js, styles.css); short for index.html
    min_ttl     = 0
    default_ttl = 3600
    max_ttl     = 86400
  }

  # index.html should not be aggressively cached (contains chunk hashes that change on deploy)
  ordered_cache_behavior {
    path_pattern           = "/index.html"
    target_origin_id       = "S3-${var.project_name}"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true

    forwarded_values {
      query_string = false
      cookies { forward = "none" }
    }

    min_ttl     = 0
    default_ttl = 0   # no caching for index.html
    max_ttl     = 0
  }

  # Angular client-side routing: return index.html for any 404 so the router handles it
  custom_error_response {
    error_code            = 404
    response_code         = 200
    response_page_path    = "/index.html"
    error_caching_min_ttl = 0
  }

  custom_error_response {
    error_code            = 403
    response_code         = 200
    response_page_path    = "/index.html"
    error_caching_min_ttl = 0
  }

  restrictions {
    geo_restriction { restriction_type = "none" }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
    # To use a custom domain with ACM certificate, replace with:
    # acm_certificate_arn      = aws_acm_certificate.frontend.arn
    # ssl_support_method       = "sni-only"
    # minimum_protocol_version = "TLSv1.2_2021"
  }

  tags = { Name = "${var.project_name}-cdn" }
}

# ── Outputs ───────────────────────────────────────────────────────────────────

output "cloudfront_url" {
  value       = "https://${aws_cloudfront_distribution.frontend.domain_name}"
  description = "Frontend URL — open this in the browser after deploying Angular build to S3"
}

output "s3_frontend_bucket" {
  value       = aws_s3_bucket.frontend.bucket
  description = "S3 bucket name — run: aws s3 sync dist/ s3://<bucket> --delete"
}

output "cloudfront_distribution_id" {
  value       = aws_cloudfront_distribution.frontend.id
  description = "CloudFront distribution ID — needed for cache invalidation after deploy"
}
