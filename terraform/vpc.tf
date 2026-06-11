resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags = { Name = "${var.project_name}-vpc" }
}

# ── Subnets ───────────────────────────────────────────────────────────────────

resource "aws_subnet" "private_a" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.1.0/24"
  availability_zone = "${var.aws_region}a"
  tags = { Name = "${var.project_name}-private-a" }
}

resource "aws_subnet" "private_b" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.2.0/24"
  availability_zone = "${var.aws_region}b"
  tags = { Name = "${var.project_name}-private-b" }
}

resource "aws_subnet" "public_a" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.10.0/24"
  availability_zone       = "${var.aws_region}a"
  map_public_ip_on_launch = true
  tags = { Name = "${var.project_name}-public-a" }
}

# ── Internet Gateway (public internet access for the VPC) ─────────────────────

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
  tags = { Name = "${var.project_name}-igw" }
}

# Route table for the public subnet → internet via IGW
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }
  tags = { Name = "${var.project_name}-public-rt" }
}

resource "aws_route_table_association" "public_a" {
  subnet_id      = aws_subnet.public_a.id
  route_table_id = aws_route_table.public.id
}

# ── NAT Gateway (allows private subnets to reach internet — needed by Lambda) ─
# Lambda in private subnets must reach SES (email) and external rate APIs.

resource "aws_eip" "nat" {
  domain     = "vpc"
  depends_on = [aws_internet_gateway.main]
  tags = { Name = "${var.project_name}-nat-eip" }
}

resource "aws_nat_gateway" "main" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public_a.id   # NAT Gateway itself sits in the public subnet
  depends_on    = [aws_internet_gateway.main]
  tags = { Name = "${var.project_name}-nat-gw" }
}

# Route table for private subnets → internet via NAT Gateway
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id
  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.main.id
  }
  tags = { Name = "${var.project_name}-private-rt" }
}

resource "aws_route_table_association" "private_a" {
  subnet_id      = aws_subnet.private_a.id
  route_table_id = aws_route_table.private.id
}

resource "aws_route_table_association" "private_b" {
  subnet_id      = aws_subnet.private_b.id
  route_table_id = aws_route_table.private.id
}

# ── Security Groups ───────────────────────────────────────────────────────────

resource "aws_security_group" "lambda" {
  name        = "${var.project_name}-lambda-sg"
  vpc_id      = aws_vpc.main.id
  description = "Lambda function security group"

  # Lambda initiates all connections outbound — no inbound rules needed
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project_name}-lambda-sg" }
}

resource "aws_security_group" "rds" {
  name        = "${var.project_name}-rds-sg"
  vpc_id      = aws_vpc.main.id
  description = "RDS PostgreSQL — only reachable from Lambda"

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.lambda.id]
    description     = "PostgreSQL from Lambda"
  }

  tags = { Name = "${var.project_name}-rds-sg" }
}

resource "aws_security_group" "elasticache" {
  name        = "${var.project_name}-elasticache-sg"
  vpc_id      = aws_vpc.main.id
  description = "ElastiCache Redis — only reachable from Lambda"

  ingress {
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.lambda.id]
    description     = "Redis from Lambda"
  }

  tags = { Name = "${var.project_name}-elasticache-sg" }
}

# ── Outputs ───────────────────────────────────────────────────────────────────

output "nat_gateway_ip" {
  value       = aws_eip.nat.public_ip
  description = "Public IP of NAT Gateway — whitelist this on external rate APIs if needed"
}
