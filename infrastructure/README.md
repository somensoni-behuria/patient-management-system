# Infrastructure (AWS CDK → LocalStack)

Standalone AWS CDK (Java) app that provisions the cloud topology for the Patient Management
System: a VPC, three RDS PostgreSQL instances, an MSK (Kafka) cluster, and an ECS/Fargate service
per microservice (see `src/main/java/com/pm/stack/LocalStack.java`).

It is **not** part of the Spring reactor — build/synth it independently.

## Prerequisites

- Docker running, with LocalStack: `localstack start -d`
  (VPC + RDS work on LocalStack community; **MSK and ECS require LocalStack Pro**.)
- Node.js + CDK toolkits: `npm i -g aws-cdk aws-cdk-local`
- Java 21 + Maven

## Deploy

```bash
./localstack-deploy.sh
```

Synth only (no deploy):

```bash
cdklocal synth        # or: mvn -q compile exec:java
```

Inspect:

```bash
awslocal ec2 describe-vpcs
awslocal rds describe-db-instances
```

For everyday local development use the root `docker-compose.yml` instead — this module is the
infrastructure-as-code reference.
