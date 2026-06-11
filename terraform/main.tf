terraform {
  required_version = ">= 1.7.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Remote state — create this S3 bucket manually before running terraform init
  # Replace "your-terraform-state-bucket" with your actual bucket name
  backend "s3" {
    bucket = "your-terraform-state-bucket"   # TODO: create this bucket first
    key    = "xchange/terraform.tfstate"
    region = "ap-south-1"
  }
}

# Primary region provider
provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "Terraform"
    }
  }
}

# us-east-1 alias — required for CloudFront WAF (WAF for CloudFront must be in us-east-1)
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"

  default_tags {
    tags = {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "Terraform"
    }
  }
}
