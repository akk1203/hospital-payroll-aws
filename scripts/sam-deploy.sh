#!/bin/sh
set -eu

STATUS=$(aws cloudformation describe-stacks --stack-name "${APP_STACK_NAME}" --query "Stacks[0].StackStatus" --output text 2>/dev/null || true)
if [ "${STATUS}" = "ROLLBACK_COMPLETE" ] || [ "${STATUS}" = "ROLLBACK_FAILED" ]; then
  echo "Deleting ${APP_STACK_NAME} in ${STATUS} so it can be created again"
  aws cloudformation delete-stack --stack-name "${APP_STACK_NAME}"
  aws cloudformation wait stack-delete-complete --stack-name "${APP_STACK_NAME}"
fi

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
