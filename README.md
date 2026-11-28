# Bunny - Serverless Event-Driven Email [![My Skills](https://skillicons.dev/icons?i=java,spring,aws,dynamodb,terraform,githubactions,docker,maven)](https://skillicons.dev)

Sistema de cadastro de usuários com envio de email de boas-vindas **orientado a eventos** e **100% serverless na AWS**. O `user-service` publica um evento no **SQS**, que aciona uma **Lambda Java com SnapStart**. A Lambda envia o email pelo **SES** e registra o status no **DynamoDB**, de forma **idempotente**, com **falha parcial de lote** e **dead-letter queue**. Toda a infraestrutura é provisionada com **Terraform** e o deploy é feito pelo **GitHub Actions via OIDC**, sem nenhuma access key.

## Desenho arquitetura

<img width="100%" alt="Arquitetura do Bunny na AWS" src="docs/arquitetura.png" />

## Índice

- [Funcionalidades](#funcionalidades)
- [Tecnologias](#tecnologias)
- [Serviços](#serviços)
- [Fluxos](#fluxos)
  - [Happy Path - Email enviado](#happy-path---email-enviado)
  - [Mensagem duplicada](#mensagem-duplicada)
  - [Falha no envio - DLQ](#falha-no-envio---dlq)
- [Patterns Implementados](#patterns-implementados)
- [Benchmarks - SnapStart](#benchmarks---snapstart)
- [Infraestrutura (Terraform)](#infraestrutura-terraform)
- [Schemas DynamoDB](#schemas-dynamodb)
- [API Endpoints](#api-endpoints)
- [Como Executar](#como-executar)
- [Variáveis de Ambiente](#variáveis-de-ambiente)

---

## Funcionalidades

- Cadastro, listagem e remoção de usuários com **email único** garantido por transação no DynamoDB.
- Evento de boas-vindas publicado no **SQS** com contrato em JSON puro.
- **Lambda Java 21 + Spring Boot** acionada pelo SQS em lotes de até 10 mensagens, com **SnapStart** (cold start de ~4,3 s para ~0,8 s).
- Envio pelo **Amazon SES** com **idempotência** pelo `eventId`: a mesma mensagem nunca gera dois emails.
- **Falha parcial de lote** (`ReportBatchItemFailures`): só as mensagens que falharam voltam para a fila.
- **Dead-letter queue** depois de 3 tentativas.
- **IAM com menor privilégio**: uma role por componente, cada uma restrita aos recursos que usa.
- **Infra como código** com Terraform (state remoto no S3 com lock nativo).
- **CI/CD com GitHub Actions + OIDC**: build, testes e `terraform apply` sem credencial fixa.

---

## Tecnologias

| Componente | Tecnologia |
|---|---|
| user-service | Java 21, Spring Boot 4.1 (Web MVC, Validation), AWS SDK v2 |
| email-service | Java 21, Spring Boot 4.1, AWS Lambda (`java21`, SnapStart, alias `live`) |
| Mensageria | Amazon SQS + dead-letter queue |
| Email | Amazon SES (API v2) |
| Banco | Amazon DynamoDB on-demand, AWS SDK v2 Enhanced Client (sem Spring Data/JPA) |
| Serialização | Jackson 3 |
| Infra como código | Terraform (AWS provider 6.x), backend S3 com `use_lockfile` |
| CI/CD | GitHub Actions + OIDC |
| Segurança | IAM roles com menor privilégio, STS AssumeRole |
| Observabilidade | CloudWatch Logs |
| Ambiente local | Docker Compose (DynamoDB Local) |
| Testes | JUnit 5, Mockito, MockMvc, DynamoDB Local |
| Build | Maven, `maven-assembly-plugin` (zip da Lambda) |

---

## Serviços

### user-service

**Porta:** 8081 | **Banco:** DynamoDB (`users`) | **Execução:** local, com a role `bunny-user-service`

Ponto de entrada do sistema. Expõe a API REST de usuários, grava o usuário e o marcador de email único na mesma transação e publica o evento `{eventId, ownerRef, to, subject, body}` na `email-queue`. Gera o `eventId`, que é a chave de idempotência de todo o fluxo.

```
controller → service → repository (DynamoDB)
                    └→ producer (SQS SendMessage)
```

### email-service

**Runtime:** AWS Lambda `java21` | **Banco:** DynamoDB (`emails`) | **Trigger:** SQS `email-queue`

Lambda acionada pelo event source mapping do SQS. O handler (`SqsConsumer`) é fino: sobe o Spring no init (capturado pelo SnapStart), desserializa cada mensagem, chama o `EmailService` e devolve só as mensagens que falharam.

```
SQS → SqsConsumer (RequestHandler<SQSEvent, SQSBatchResponse>)
        └→ EmailService → claim no DynamoDB (PENDING)
                        → SES SendEmail
                        → status SENT | FAILED
```

---

## Fluxos

### Happy Path - Email enviado

```
Client          user-service        DynamoDB           SQS            Lambda             SES
  │                  │                  │                 │                │                 │
  │ POST /users      │                  │                 │                │                 │
  │─────────────────>│                  │                 │                │                 │
  │                  │ TransactWrite    │                 │                │                 │
  │                  │ user + EMAIL#    │                 │                │                 │
  │                  │─────────────────>│                 │                │                 │
  │                  │ SendMessage (eventId)              │                │                 │
  │                  │───────────────────────────────────>│                │                 │
  │   201 Created    │                  │                 │                │                 │
  │<─────────────────│                  │                 │  lote (até 10) │                 │
  │                  │                  │                 │───────────────>│                 │
  │                  │                  │  PutItem PENDING (condicional)   │                 │
  │                  │                  │<─────────────────────────────────│                 │
  │                  │                  │                 │                │ SendEmail       │
  │                  │                  │                 │                │────────────────>│
  │                  │                  │  status = SENT  │                │                 │
  │                  │                  │<─────────────────────────────────│                 │
  │                  │                  │                 │ batchItemFailures = []           │
  │                  │                  │                 │<───────────────│                 │
```

### Mensagem duplicada

O SQS entrega *pelo menos uma vez*. Se o mesmo `eventId` chegar de novo, o `PutItem` condicional falha e a mensagem é descartada sem enviar outro email.

```
Lambda                                DynamoDB
  │ PutItem id=eventId                    │
  │ IF attribute_not_exists(id)           │
  │    OR statusEmail = FAILED            │
  │──────────────────────────────────────>│
  │   ConditionalCheckFailed (já SENT)    │
  │<──────────────────────────────────────│
  │ ignora: nenhum email enviado          │
```

### Falha no envio - DLQ

```
SQS email-queue          Lambda                 DynamoDB          email-dlq
  │ recepção 1             │                        │                  │
  │───────────────────────>│ SES falha              │                  │
  │                        │ status = FAILED ──────>│                  │
  │ batchItemFailures      │                        │                  │
  │<───────────────────────│                        │                  │
  │ (visibility timeout 180 s)                      │                  │
  │ recepção 2 e 3: FAILED permite novo claim, falha de novo           │
  │                                                                    │
  │ maxReceiveCount = 3 atingido: redrive ────────────────────────────>│
  │                                                  (retida 14 dias)  │
```

---

## Patterns Implementados

### 1. Idempotência pelo `eventId`

O produtor gera o `eventId`, que é a partition key da tabela `emails`. Antes de enviar, a Lambda faz um **claim** com `PutItem` condicional:

| Estado atual | Resultado do claim | Ação |
|---|---|---|
| não existe | grava `PENDING` | envia o email |
| `FAILED` | grava `PENDING` | tenta de novo |
| `PENDING` ou `SENT` | `ConditionalCheckFailed` | ignora a mensagem |

### 2. Partial Batch Response

O handler devolve um `SQSBatchResponse` só com os `messageId` que falharam. Sem isso, uma mensagem ruim faria o lote inteiro voltar para a fila e reenviaria emails que já tinham saído.

```
lote [m1, m2, m3] → m2 falha → batchItemFailures = [m2]
m1, m3: removidas da fila    m2: volta depois do visibility timeout
```

### 3. Dead-Letter Queue

`maxReceiveCount = 3` no redrive da `email-queue`. O **visibility timeout é 6× o timeout da Lambda** (180 s), derivado no Terraform (`6 * aws_lambda_function.email.timeout`), para o SQS não reentregar uma mensagem que ainda está sendo processada. A concorrência do event source mapping é limitada a 2, porque o SES no sandbox aceita 1 email/s.

### 4. Email único sem `UNIQUE`

O DynamoDB não tem constraint de unicidade. O usuário e um item marcador `EMAIL#<email>` são gravados na **mesma** `TransactWriteItems`, os dois com `attribute_not_exists(id)`:

```
┌───────────────────────────────────────────┐
│           TransactWriteItems              │
│                                           │
│  1. Put user        (id = UUID)           │
│  2. Put marcador    (id = EMAIL#email)    │
│     ambos: attribute_not_exists(id)       │
│                                           │
│  marcador já existe → transação cancelada │
│                     → 409 Conflict        │
└───────────────────────────────────────────┘
```

O email é normalizado (trim + minúsculas), e o delete remove os dois itens juntos, liberando o email.

### 5. Spring Boot em Lambda com SnapStart

O handler sobe o Spring com `WebApplicationType.NONE` no **construtor**, então o contexto inteiro entra no snapshot. O SQS chama sempre o alias `live`, porque o SnapStart só existe em versões publicadas. Nenhum UUID ou timestamp é gerado em campo estático ou no init: senão, todas as instâncias restauradas compartilhariam o mesmo valor.

### 6. Menor privilégio e CI sem segredo

| Identidade | Pode fazer | Só em |
|---|---|---|
| `bunny-email-service-lambda` | `sqs:ReceiveMessage/DeleteMessage`, `dynamodb:PutItem/GetItem`, `ses:SendEmail` | `email-queue`, tabela `emails`, identidade do remetente |
| `bunny-user-service` | `sqs:SendMessage`, `dynamodb:PutItem/GetItem/DeleteItem/Scan` | `email-queue`, tabela `users` |
| `bunny-github-deploy` | `terraform apply` | tokens OIDC de push na `main` deste repositório |

O GitHub Actions troca o token OIDC do job por uma credencial temporária via `AssumeRoleWithWebIdentity`. O `user-service` assume a própria role por um profile da AWS, sem usar a credencial de admin de quem desenvolve.

---

## Benchmarks - SnapStart

Medido no CloudWatch, Lambda `java21` com 1024 MB:

| | Sem SnapStart | Com SnapStart |
|---|---|---|
| Inicialização | 4266 ms (Init Duration) | **805 ms** (Restore Duration) |
| Duração cobrada no cold start | 4285 ms | **337 ms** |

**Custo:** com pouco tráfego, Lambda + SQS + DynamoDB on-demand fica em **~US$ 0–1/mês**, dentro da cota gratuita. Um RDS exigiria Lambda em VPC com NAT Gateway (~US$ 32/mês só o NAT).

---

## Infraestrutura (Terraform)

| Arquivo | Recursos |
|---|---|
| `versions.tf` | AWS provider 6.x, backend S3 com lock nativo |
| `provider.tf` | região, `default_tags` em todos os recursos |
| `sqs.tf` | `email-queue` com redrive para `email-dlq` |
| `dynamodb.tf` | tabelas `emails` e `users` (on-demand, PITR, deletion protection) |
| `ses.tf` | identidade do remetente |
| `iam.tf` | roles da Lambda e do `user-service` |
| `lambda.tf` | função, SnapStart, alias `live`, event source mapping |
| `github.tf` | provedor OIDC do GitHub, role de deploy |
| `outputs.tf` | ARNs das roles, nome da tabela, URL da fila |

---

## Schemas DynamoDB

### emails (email-service)

| Atributo | Tipo | Descrição |
|---|---|---|
| `id` | S (PK) | `eventId` gerado pelo produtor |
| `ownerRef` | S | id do usuário |
| `emailFrom` / `emailTo` | S | remetente e destinatário |
| `subject` / `text` | S | conteúdo |
| `sendDateEmail` | S | instante do envio |
| `statusEmail` | S | `PENDING` · `SENT` · `FAILED` |

### users (user-service)

| Item | `id` (PK) | Atributos |
|---|---|---|
| Usuário | UUID | `name`, `email`, `createdAt` |
| Marcador de unicidade | `EMAIL#<email normalizado>` | `userId` |

---

## API Endpoints

### user-service (:8081)

| Método | Rota | Respostas |
|---|---|---|
| `POST` | `/users` | **201** criado e evento publicado · **400** campos inválidos · **409** email já cadastrado |
| `GET` | `/users` | **200** lista de usuários |
| `DELETE` | `/users/{id}` | **204** removido · **404** não existe · **400** id não é UUID |

```bash
curl -X POST localhost:8081/users -H 'Content-Type: application/json' \
     -d '{"name":"Fulano","email":"fulano@exemplo.com"}'
```

### Contrato do evento (`email-queue`)

```json
{
  "eventId": "7b6792cc-2b70-4f96-9570-eb17f20a5397",
  "ownerRef": "06a7afc6-7cea-4f25-bf16-8b17f44d1590",
  "to": "fulano@exemplo.com",
  "subject": "Bem-vindo ao nosso sistema!",
  "body": "Olá Fulano, obrigado por se cadastrar em nosso sistema!"
}
```

---

## Como Executar

### Pré-requisitos

- Java 21, Docker, Terraform ≥ 1.10, AWS CLI v2
- Conta AWS com credencial de admin no `~/.aws`
- Bucket S3 para o state (nome em `infra/versions.tf`)

### 1. Build da Lambda

```bash
cd email-service && ./mvnw package     # gera target/*-lambda.zip
```

### 2. Provisionar a infraestrutura

```bash
cd infra
echo 'sender_email = "seu-email@exemplo.com"' > terraform.tfvars
terraform init
terraform apply
```

Confirme o email de verificação que o SES envia. Enquanto a conta estiver no **sandbox do SES**, o destinatário também precisa ser verificado.

### 3. Profile do user-service

No `~/.aws/config`, com o output `user_service_role_arn`:

```ini
[profile bunny-user-service]
role_arn = arn:aws:iam::<conta>:role/bunny-user-service
source_profile = default
region = us-east-1
```

### 4. Rodar o user-service

```bash
cp .env.example .env                   # preencha EMAIL_FROM e EMAIL_QUEUE_URL
docker compose up -d                   # DynamoDB Local
AWS_PROFILE=bunny-user-service ./user-service/mvnw -f user-service/pom.xml spring-boot:run \
  -Dspring-boot.run.workingDirectory=$PWD
```

Rode a partir da raiz do repositório: o `.env` e o `compose.yaml` são lidos com caminho relativo a ela.

### 5. Testar

```bash
# cria um usuário com um email verificado no SES
curl -X POST localhost:8081/users -H 'Content-Type: application/json' \
     -d '{"name":"Fulano","email":"email-verificado@exemplo.com"}'

# acompanha a Lambda
aws logs tail /aws/lambda/bunny-email-service --follow
```

### Testes automatizados

```bash
docker compose up -d
cd user-service && ./mvnw test
cd email-service && ./mvnw test
```

### Deploy contínuo

O workflow [`.github/workflows/deploy.yml`](.github/workflows/deploy.yml) roda a cada push na `main` que altere `email-service/` ou `infra/`: build, testes com DynamoDB Local como service container e `terraform apply`. Configure as variables do Actions `AWS_DEPLOY_ROLE_ARN` (output `github_deploy_role_arn`) e `SENDER_EMAIL`.

---

## Variáveis de Ambiente

### user-service

| Variável | Descrição |
|---|---|
| `AWS_REGION` | região da AWS |
| `AWS_PROFILE` | `bunny-user-service`, para assumir a role |
| `DYNAMODB_ENDPOINT` | `http://localhost:8000` para DynamoDB Local; vazio para a AWS |
| `DYNAMODB_USER_TABLE` | tabela de usuários (`users`) |
| `EMAIL_QUEUE_URL` | URL da `email-queue` (output `email_queue_url`) |

### email-service (Lambda)

| Variável | Descrição |
|---|---|
| `EMAIL_FROM` | remetente verificado no SES |
| `DYNAMODB_EMAIL_TABLE` | tabela de emails (`emails`) |

As variáveis da Lambda são definidas pelo Terraform em `lambda.tf`.
