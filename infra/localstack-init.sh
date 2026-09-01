#!/usr/bin/env bash
# P0.4: cria as filas SQS no LocalStack com Dead Letter Queue (DLQ)
# configurada, além da fila opcional de status de entrega (P3.3).
#
# Uso:
#   docker run --rm -it -d -p 4566:4566 localstack/localstack start
#   ./infra/localstack-init.sh
set -euo pipefail

ENDPOINT="${LOCALSTACK_ENDPOINT:-http://localhost:4566}"
REGION="${AWS_REGION:-us-east-1}"

MAIN_QUEUE="tel-bot-queue"
DLQ_QUEUE="tel-bot-queue-dlq"
STATUS_QUEUE="tel-bot-status-queue"
MAX_RECEIVE_COUNT="${MAX_RECEIVE_COUNT:-5}"

aws() {
  command aws --endpoint-url="$ENDPOINT" --region "$REGION" "$@"
}

echo "Criando DLQ ($DLQ_QUEUE)..."
aws sqs create-queue --queue-name "$DLQ_QUEUE"

DLQ_URL=$(aws sqs get-queue-url --queue-name "$DLQ_QUEUE" --query 'QueueUrl' --output text)
DLQ_ARN=$(aws sqs get-queue-attributes --queue-url "$DLQ_URL" --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)

echo "Criando fila principal ($MAIN_QUEUE) com redrive policy para a DLQ..."
REDRIVE_POLICY="{\"deadLetterTargetArn\":\"$DLQ_ARN\",\"maxReceiveCount\":\"$MAX_RECEIVE_COUNT\"}"
aws sqs create-queue \
  --queue-name "$MAIN_QUEUE" \
  --attributes "RedrivePolicy=$(printf '%s' "$REDRIVE_POLICY" | sed 's/"/\\"/g')"

echo "Criando fila de status de entrega ($STATUS_QUEUE, opcional/P3.3)..."
aws sqs create-queue --queue-name "$STATUS_QUEUE"

echo
echo "Filas criadas:"
aws sqs list-queues --query 'QueueUrls' --output table

cat <<EOF

Depois de esgotadas $MAX_RECEIVE_COUNT tentativas, mensagens problemáticas
da fila "$MAIN_QUEUE" caem automaticamente na DLQ "$DLQ_QUEUE".

Para inspecionar a DLQ:
  aws --endpoint-url=$ENDPOINT sqs receive-message \\
    --queue-url $ENDPOINT/000000000000/$DLQ_QUEUE \\
    --attribute-names All --message-attribute-names All
EOF
