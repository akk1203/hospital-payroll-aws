#!/bin/sh
set -eu

PARAMS="EnvironmentName=${ENVIRONMENT_NAME} CognitoDomainPrefix=${COGNITO_DOMAIN_PREFIX}"
if [ -n "${GOOGLE_CLIENT_ID:-}" ]; then
  PARAMS="${PARAMS} GoogleClientId=${GOOGLE_CLIENT_ID} GoogleClientSecret=${GOOGLE_CLIENT_SECRET}"
fi

sam deploy \
  --template-file template.yaml \
  --stack-name "${APP_STACK_NAME}" \
  --resolve-s3 \
  --capabilities CAPABILITY_IAM \
  --no-confirm-changeset \
  --no-fail-on-empty-changeset \
  --parameter-overrides ${PARAMS}
