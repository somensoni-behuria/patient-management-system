#!/usr/bin/env bash
#
# Deploys the Patient Management System CDK stack to a local LocalStack instance.
#
# Prerequisites:
#   - Docker running, with LocalStack started:  localstack start -d
#     (MSK and ECS require LocalStack Pro; VPC + RDS work on community.)
#   - Node.js + AWS CDK Toolkit for LocalStack:  npm i -g aws-cdk-local aws-cdk
#   - Java 21 + Maven (or the repo's mvn) to compile the CDK app.
#
# Usage:  ./localstack-deploy.sh
set -euo pipefail

cd "$(dirname "$0")"

echo "==> Bootstrapping CDK against LocalStack"
cdklocal bootstrap

echo "==> Deploying stack 'localstack'"
cdklocal deploy --require-approval never

echo "==> Deploy complete. Inspect resources, e.g.:"
echo "    awslocal ec2 describe-vpcs"
echo "    awslocal rds describe-db-instances"
