resource "aws_dynamodb_table" "emails" {
  name         = "emails"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "id"

  deletion_protection_enabled = true

  point_in_time_recovery {
    enabled = true
  }

  attribute {
    name = "id"
    type = "S" # S = String, N = Number, B = Binary
  }
}

resource "aws_dynamodb_table" "users" {
  name         = "users"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "id"

  deletion_protection_enabled = true

  point_in_time_recovery {
    enabled = true
  }

  attribute {
    name = "id"
    type = "S"
  }
}
