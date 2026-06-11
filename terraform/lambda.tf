resource "aws_lambda_function" "api" {
  function_name = "${var.project_name}-api"
  filename      = var.lambda_jar_path
  handler       = "com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler::handleRequest"
  runtime       = "java21"
  role          = aws_iam_role.lambda_exec.arn
  timeout       = 30
  memory_size   = 1024

  # publish = true is required for SnapStart — it creates a numbered version on each deploy
  publish = true

  snap_start {
    apply_on = "PublishedVersions"
  }

  vpc_config {
    subnet_ids         = [aws_subnet.private_a.id, aws_subnet.private_b.id]
    security_group_ids = [aws_security_group.lambda.id]
  }

  environment {
    variables = {
      SPRING_PROFILES_ACTIVE    = "aws"
      DB_URL                    = "jdbc:postgresql://${aws_db_instance.postgres.endpoint}/currency_exchange"
      DB_USERNAME               = var.db_username
      DB_PASSWORD               = var.db_password
      REDIS_HOST                = aws_elasticache_replication_group.redis.primary_endpoint_address
      REDIS_PORT                = "6379"
      SES_SMTP_USERNAME         = var.ses_smtp_username
      SES_SMTP_PASSWORD         = var.ses_smtp_password
      JWT_PRIVATE_KEY           = var.jwt_private_key
      JWT_PUBLIC_KEY            = var.jwt_public_key
      ALPHA_VANTAGE_API_KEY     = var.alpha_vantage_api_key
      EMAIL_FROM_ADDRESS        = var.email_from_address
      TRANSACTION_ADMIN_EMAIL   = var.transaction_admin_email
    }
  }

  tags = { Name = "${var.project_name}-api" }
}

# Alias pointing to the published version — API Gateway must invoke the alias,
# not $LATEST, for SnapStart to take effect
resource "aws_lambda_alias" "live" {
  name             = "live"
  function_name    = aws_lambda_function.api.function_name
  function_version = aws_lambda_function.api.version
}

# ── API Gateway (HTTP API v2) ─────────────────────────────────────────────────

resource "aws_apigatewayv2_api" "main" {
  name          = "${var.project_name}-api-gw"
  protocol_type = "HTTP"

  cors_configuration {
    allow_origins     = ["https://${aws_cloudfront_distribution.frontend.domain_name}"]
    allow_methods     = ["GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"]
    allow_headers     = ["Content-Type", "Authorization", "X-Correlation-ID"]
    allow_credentials = true
    max_age           = 3600
  }
}

resource "aws_apigatewayv2_integration" "lambda" {
  api_id = aws_apigatewayv2_api.main.id

  # Integrate with the alias ARN so SnapStart applies
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_alias.live.invoke_arn
  payload_format_version = "2.0"
}

resource "aws_apigatewayv2_route" "proxy" {
  api_id    = aws_apigatewayv2_api.main.id
  route_key = "$default"
  target    = "integrations/${aws_apigatewayv2_integration.lambda.id}"
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.main.id
  name        = "$default"
  auto_deploy = true

  # NFR-04: throttle to protect Lambda and downstream services
  default_route_settings {
    throttling_burst_limit = 500
    throttling_rate_limit  = 1000
  }
}

# Allow API Gateway to invoke the Lambda alias
resource "aws_lambda_permission" "apigateway" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  # Permission must be on the alias, not the function, to work with SnapStart versioning
  function_name = aws_lambda_alias.live.arn
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.main.execution_arn}/*/*"
}

output "api_gateway_url" {
  value       = aws_apigatewayv2_stage.default.invoke_url
  description = "API Gateway endpoint — set this as apiUrl in frontend environment.prod.ts"
}
