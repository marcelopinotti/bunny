# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Bunny is a microservices study project made of two independent Spring Boot 4.1 / Java 21 Maven projects (there is no parent POM):

- `user-service` (port 8081) is the producer. It is meant to publish a "send email" event.
- `email-service` (port 8080) consumes that event from RabbitMQ (`email-queue`), sends the email over SMTP (`JavaMailSender`) and saves its status to DynamoDB.

Both services persist to DynamoDB through **AWS SDK v2 Enhanced Client** (no Spring Data, no JPA). Locally both use one shared DynamoDB Local on port 8000 from the root `compose.yaml`, with one table per service (`emails` and `users`). That compose also runs the shared RabbitMQ on port 5672.

Docs and code comments are in Portuguese. `docs/migracao-serverless.md` is the design doc and the source of truth for decisions. Read it before making architectural changes. `docs/arquitetura.drawio` holds the diagrams.

## Commands

The repo root (`bunny/`) is the only git repository. Run Maven from inside each service directory:

```bash
cd email-service && ./mvnw test                                       # all tests
cd email-service && ./mvnw test -Dtest=EmailModelRepositoryTest       # single test class
cd email-service && ./mvnw test -Dtest=EmailModelRepositoryTest#saveAndFind
docker compose up -d                                                  # from the repo root: DynamoDB Local + RabbitMQ (required by repository tests)
```

**Working-directory gotcha:** in each `application.yaml`, `spring.config.import` (`.env`) and `spring.docker.compose.file` (`compose.yaml`) are relative to the **repo root**. Run the apps from the repo root (the IntelliJ default), or `.env` will not load and docker-compose auto-start will not find the file. The `@SpringBootTest contextLoads` tests depend on this too. `spring-boot-docker-compose` starts the shared compose automatically when an app boots. It does this only when the app runs from the IDE or `spring-boot:run`, not from the packaged jar. `lifecycle-management: start-only` keeps one service's shutdown from stopping the other service's infrastructure.

Configuration comes only from the single root `.env` (git-ignored; the template is `.env.example`), shared by both services and by `compose.yaml`. The table names are `DYNAMODB_EMAIL_TABLE` and `DYNAMODB_USER_TABLE`. When `DYNAMODB_ENDPOINT` is set, the app runs in local mode: dummy credentials, plus email-service creates its table at startup in `DynamoDbConfig`. When it is empty, the app uses the default AWS credential chain and expects Terraform to have created the table.

## Architecture rules (from the migration plan)

The project is in **phase 1** (local, RabbitMQ on CloudAMQP). **Phase 2** moves email-service to Lambda triggered by SQS, with SES and DynamoDB, provisioned by Terraform. Write code so that switch stays cheap:

- **Keep the listener thin.** `EmailConsumer` only deserializes the message and calls `EmailService.send(dto)`. Business logic never goes in the `@RabbitListener`, because in phase 2 a Lambda handler replaces it.
- **Plain JSON message contract**, with no dependency on the `__TypeId__` header or Java serialization: `{eventId, ownerRef, to, subject, body}`.
- **The producer (user-service) generates `eventId`.** It is the DynamoDB partition key and the idempotency key, because SQS delivers at least once. Use `PutItem` with `attribute_not_exists`.
- No secrets in `application.yaml`. Everything uses `${ENV_VAR}`.
- Don't generate UUIDs or timestamps in static fields or at init time, because of SnapStart.
- Only add a DynamoDB GSI once a query actually needs it.

## Current state

The project is early: user-service has only `DynamoDbConfig`, and email-service has no `EmailService` or mail sending yet. Section 7 of `docs/migracao-serverless.md` lists known issues in the code. These include `RabbitMq.queue()` missing `@Bean`, `@RabbitListener` missing `queues`.
