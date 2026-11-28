output "email_lambda_role_arn" {
  value = aws_iam_role.email_lambda.arn
}

output "emails_table_name" {
  value = aws_dynamodb_table.emails.name
}

output "email_queue_url" {
  value = aws_sqs_queue.email.url
}

output "user_service_role_arn" {
  value = aws_iam_role.user_service.arn
}

output "github_deploy_role_arn" {
  value = aws_iam_role.github_deploy.arn
}
