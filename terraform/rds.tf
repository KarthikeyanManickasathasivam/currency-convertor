resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-db-subnet-group"
  subnet_ids = [aws_subnet.private_a.id, aws_subnet.private_b.id]
  tags = { Name = "${var.project_name}-db-subnet-group" }
}

resource "aws_db_instance" "postgres" {
  identifier     = "${var.project_name}-postgres"
  engine         = "postgres"
  engine_version = "16.3"

  # Architecture specifies db.t3.medium (not micro) for production load
  instance_class        = "db.t3.medium"
  allocated_storage     = 50   # architecture spec: 50GB gp3
  max_allocated_storage = 200
  storage_type          = "gp3"
  storage_encrypted     = true   # NFR-06: AES-256 at rest

  db_name  = "currency_exchange"
  username = var.db_username
  password = var.db_password

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  # NFR-03: 99.9% uptime requires Multi-AZ
  multi_az            = true
  publicly_accessible = false

  backup_retention_period   = 7
  backup_window             = "02:00-03:00"
  maintenance_window        = "sun:04:00-sun:05:00"
  auto_minor_version_upgrade = true

  skip_final_snapshot               = false
  final_snapshot_identifier         = "${var.project_name}-final-snapshot"
  deletion_protection               = true

  # Enable enhanced monitoring and performance insights
  monitoring_interval = 60
  monitoring_role_arn = aws_iam_role.rds_monitoring.arn

  tags = { Name = "${var.project_name}-postgres" }
}

# IAM role for RDS enhanced monitoring
resource "aws_iam_role" "rds_monitoring" {
  name = "${var.project_name}-rds-monitoring-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "monitoring.rds.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "rds_monitoring" {
  role       = aws_iam_role.rds_monitoring.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}

output "rds_endpoint" {
  value     = aws_db_instance.postgres.endpoint
  sensitive = true
  description = "RDS endpoint — use in DB_URL as: jdbc:postgresql://<endpoint>/currency_exchange"
}
