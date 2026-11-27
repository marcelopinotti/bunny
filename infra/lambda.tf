locals {
  email_lambda_zip = "${path.module}/../email-service/target/email-service-0.0.1-SNAPSHOT-lambda.zip"
}

resource "aws_lambda_function" "email" {
  function_name    = "${var.project}-email-service"
  role             = aws_iam_role.email_lambda.arn
  handler          = "com.marcelodev.emailservice.consumer.SqsConsumer::handleRequest"
  runtime          = "java21"
  memory_size      = 1024
  timeout          = 30
  filename         = local.email_lambda_zip
  source_code_hash = filebase64sha256(local.email_lambda_zip)
  publish          = true # cada deploy vira uma versão congelada (1, 2, 3...); o SnapStart só existe nelas

  snap_start { # NOVO: no publish a AWS roda o init uma vez e guarda a foto da memória
    apply_on = "PublishedVersions"
  }

  environment {
    variables = {
      EMAIL_FROM           = aws_sesv2_email_identity.sender.email_identity
      DYNAMODB_EMAIL_TABLE = aws_dynamodb_table.emails.name
    }
  }
}

# apelido fixo que o SQS vai chamar; o Terraform move para a versão nova a cada deploy
resource "aws_lambda_alias" "email_live" {
  name             = "live"
  function_name    = aws_lambda_function.email.function_name
  function_version = aws_lambda_function.email.version
}

# o "listener" da AWS; consulta a fila e chama o alias live (nunca a função, ou perde o SnapStart)
resource "aws_lambda_event_source_mapping" "email_sqs" {
  event_source_arn        = aws_sqs_queue.email.arn
  function_name           = aws_lambda_alias.email_live.arn
  batch_size              = 10
  function_response_types = ["ReportBatchItemFailures"] # sem isso a AWS ignora o batchItemFailures do SqsConsumer

  scaling_config {
    maximum_concurrency = 2 # SES no sandbox aceita 1 email/s
  }
}
