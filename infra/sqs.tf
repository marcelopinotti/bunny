resource "aws_sqs_queue" "email" {
  name = "email-queue"
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.email_dlq.arn
    maxReceiveCount     = var.email_max_receive_count
  })
  visibility_timeout_seconds = 6 * aws_lambda_function.email.timeout

}


resource "aws_sqs_queue" "email_dlq" { # fila morta para mensagens que não puderam ser processadas
  name                      = "email-dlq"
  message_retention_seconds = var.dlq_retention_days * 24 * 60 * 60
}