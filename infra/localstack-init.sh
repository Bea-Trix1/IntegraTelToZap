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

# LocalStack não valida credenciais, mas o AWS CLI exige que *alguma*
# esteja configurada para montar a requisição.
export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-test}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-test}"

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
# file:// evita os problemas de quoting do parser shorthand do AWS CLI
# com JSON aninhado dentro de --attributes.
ATTRS_FILE=$(mktemp)
trap 'rm -f "$ATTRS_FILE"' EXIT
cat > "$ATTRS_FILE" <<JSON
{"RedrivePolicy": "{\"deadLetterTargetArn\":\"$DLQ_ARN\",\"maxReceiveCount\":\"$MAX_RECEIVE_COUNT\"}"}
JSON
aws sqs create-queue \
  --queue-name "$MAIN_QUEUE" \
  --attributes "file://$ATTRS_FILE"

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
