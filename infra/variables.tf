variable "aws_region" {
  description = "The AWS region to deploy resources in."
  type        = string
  default     = "us-east-1"
}

variable "project" {
  description = "Nome do projeto, usado nas tags de todos os recursos."
  type        = string
  default     = "bunny"
}

variable "email_max_receive_count" {
  description = "Quantas vezes uma mensagem é entregue antes de ir para a DLQ."
  type        = number
  default     = 3

  validation {
    condition     = var.email_max_receive_count >= 1 && var.email_max_receive_count <= 1000
    error_message = "O SQS aceita de 1 a 1000 entregas."
  }
}

variable "dlq_retention_days" {
  description = "Por quantos dias a DLQ guarda as mensagens que falharam."
  type        = number
  default     = 14

  validation {
    condition     = var.dlq_retention_days >= 1 && var.dlq_retention_days <= 14
    error_message = "O SQS guarda mensagens por no máximo 14 dias."
  }
}

variable "sender_email" {
  description = "Email do remetente, que será usado para enviar mensagens via SES."
  type        = string
}

variable "github_repo" {
  description = "Repositório (dono/nome) que pode fazer deploy pelo GitHub Actions."
  type        = string
  default     = "marcelopinotti/bunny"
}
