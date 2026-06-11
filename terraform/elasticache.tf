resource "aws_elasticache_subnet_group" "main" {
  name       = "${var.project_name}-cache-subnet-group"
  subnet_ids = [aws_subnet.private_a.id, aws_subnet.private_b.id]
}

# Use replication group (not aws_elasticache_cluster) for Multi-AZ support (NFR-03: 99.9% uptime)
resource "aws_elasticache_replication_group" "redis" {
  replication_group_id = "${var.project_name}-redis"
  description          = "Redis cache — rates, OTP, token blacklist, rate limiting"

  node_type            = "cache.t3.micro"
  engine_version       = "7.1"
  parameter_group_name = "default.redis7"
  port                 = 6379

  # Multi-AZ: 1 primary + 1 read replica in the second AZ
  num_cache_clusters         = 2
  automatic_failover_enabled = true
  multi_az_enabled           = true

  subnet_group_name  = aws_elasticache_subnet_group.main.name
  security_group_ids = [aws_security_group.elasticache.id]

  # NFR-06: encryption in-transit and at-rest (AES-256)
  transit_encryption_enabled = true
  at_rest_encryption_enabled = true

  # Keep 1 day of automatic backups
  snapshot_retention_limit = 1
  snapshot_window          = "03:00-04:00"

  tags = { Name = "${var.project_name}-redis" }
}

output "redis_endpoint" {
  value       = aws_elasticache_replication_group.redis.primary_endpoint_address
  sensitive   = true
  description = "Redis primary endpoint — use as REDIS_HOST in Lambda"
}
