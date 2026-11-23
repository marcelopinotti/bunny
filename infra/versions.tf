terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"

    }
  }

  backend "s3" { # não pode ter variáveis, porque roda antes do provider
    bucket       = "bunny-tfstate-135692633062"
    key          = "bunny/terraform.tfstate"
    region       = "us-east-1"
    use_lockfile = true
  }
}