# ── SNS Topic — all alarms publish here ──────────────────────────────────────

resource "aws_sns_topic" "alarms" {
  name = "${var.project_name}-alarms"
  tags = { Name = "${var.project_name}-alarms" }
}

resource "aws_sns_topic_subscription" "email" {
  topic_arn = aws_sns_topic.alarms.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

# ── Lambda Alarms ─────────────────────────────────────────────────────────────

resource "aws_cloudwatch_metric_alarm" "lambda_errors" {
  alarm_name          = "${var.project_name}-lambda-errors"
  alarm_description   = "Lambda error rate > 1% for 5 minutes"
  namespace           = "AWS/Lambda"
  metric_name         = "Errors"
  dimensions          = { FunctionName = aws_lambda_function.api.function_name }
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 5
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]
}

resource "aws_cloudwatch_metric_alarm" "lambda_duration_p95" {
  alarm_name          = "${var.project_name}-lambda-duration-p95"
  alarm_description   = "Lambda P95 duration > 2000ms for 5 minutes (NFR-02)"
  namespace           = "AWS/Lambda"
  metric_name         = "Duration"
  dimensions          = { FunctionName = aws_lambda_function.api.function_name }
  extended_statistic  = "p95"
  period              = 300
  evaluation_periods  = 1
  threshold           = 2000
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alarms.arn]
}

resource "aws_cloudwatch_metric_alarm" "lambda_throttles" {
  alarm_name          = "${var.project_name}-lambda-throttles"
  alarm_description   = "Lambda throttles detected — consider increasing concurrency"
  namespace           = "AWS/Lambda"
  metric_name         = "Throttles"
  dimensions          = { FunctionName = aws_lambda_function.api.function_name }
  statistic           = "Sum"
  period              = 60
  evaluation_periods  = 3
  threshold           = 10
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alarms.arn]
}

# ── API Gateway Alarms ────────────────────────────────────────────────────────

resource "aws_cloudwatch_metric_alarm" "apigw_5xx" {
  alarm_name          = "${var.project_name}-apigw-5xx"
  alarm_description   = "API Gateway 5xx error rate > 1% for 5 minutes"
  namespace           = "AWS/ApiGateway"
  metric_name         = "5XXError"
  dimensions          = { ApiId = aws_apigatewayv2_api.main.id }
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 10
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]
}

resource "aws_cloudwatch_metric_alarm" "apigw_latency_p95" {
  alarm_name          = "${var.project_name}-apigw-latency-p95"
  alarm_description   = "API Gateway P95 latency > 2000ms for 5 minutes"
  namespace           = "AWS/ApiGateway"
  metric_name         = "IntegrationLatency"
  dimensions          = { ApiId = aws_apigatewayv2_api.main.id }
  extended_statistic  = "p95"
  period              = 300
  evaluation_periods  = 1
  threshold           = 2000
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alarms.arn]
}

# ── RDS Alarms ────────────────────────────────────────────────────────────────

resource "aws_cloudwatch_metric_alarm" "rds_cpu" {
  alarm_name          = "${var.project_name}-rds-cpu"
  alarm_description   = "RDS CPU > 80% for 10 minutes"
  namespace           = "AWS/RDS"
  metric_name         = "CPUUtilization"
  dimensions          = { DBInstanceIdentifier = aws_db_instance.postgres.identifier }
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  threshold           = 80
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alarms.arn]
}

resource "aws_cloudwatch_metric_alarm" "rds_free_storage" {
  alarm_name          = "${var.project_name}-rds-free-storage"
  alarm_description   = "RDS free storage < 10GB"
  namespace           = "AWS/RDS"
  metric_name         = "FreeStorageSpace"
  dimensions          = { DBInstanceIdentifier = aws_db_instance.postgres.identifier }
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 1
  threshold           = 10000000000   # 10 GB in bytes
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alarms.arn]
}

resource "aws_cloudwatch_metric_alarm" "rds_connections" {
  alarm_name          = "${var.project_name}-rds-connections"
  alarm_description   = "RDS connections > 80 (Lambda pool is max 5 × concurrency)"
  namespace           = "AWS/RDS"
  metric_name         = "DatabaseConnections"
  dimensions          = { DBInstanceIdentifier = aws_db_instance.postgres.identifier }
  statistic           = "Average"
  period              = 60
  evaluation_periods  = 3
  threshold           = 80
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alarms.arn]
}

# ── ElastiCache Alarms ────────────────────────────────────────────────────────

resource "aws_cloudwatch_metric_alarm" "redis_memory" {
  alarm_name          = "${var.project_name}-redis-memory"
  alarm_description   = "Redis memory usage > 75% — review TTLs or scale up"
  namespace           = "AWS/ElastiCache"
  metric_name         = "DatabaseMemoryUsagePercentage"
  dimensions          = { ReplicationGroupId = aws_elasticache_replication_group.redis.id }
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 1
  threshold           = 75
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alarms.arn]
}

resource "aws_cloudwatch_metric_alarm" "redis_cache_hit_rate" {
  alarm_name          = "${var.project_name}-redis-cache-hit-rate"
  alarm_description   = "Redis cache hit rate < 80% for 15 minutes (target: ~95%)"
  namespace           = "AWS/ElastiCache"
  metric_name         = "CacheHitRate"
  dimensions          = { ReplicationGroupId = aws_elasticache_replication_group.redis.id }
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 3
  threshold           = 80
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alarms.arn]
}

# ── CloudWatch Log Group ──────────────────────────────────────────────────────

resource "aws_cloudwatch_log_group" "lambda" {
  name              = "/aws/lambda/${aws_lambda_function.api.function_name}"
  retention_in_days = 90   # 90-day retention as per architecture spec; archive to S3 Glacier after
  tags = { Name = "${var.project_name}-lambda-logs" }
}
