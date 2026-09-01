# Tel-To-Zap-Go

Tel-To-Zap-Go é uma aplicação que integra um bot do Telegram com uma fila SQS da AWS. O bot recebe mensagens do Telegram (texto, foto, áudio ou documento) e as envia para uma fila SQS (simulada com LocalStack), permitindo o processamento assíncrono das mensagens.

O consumer dessas mensagens é o serviço `consumer-to-zap` (Java), que consome da fila e envia para o WhatsApp via Twilio.

## Configuração rápida

```bash
cp .env.example .env
# preencha TELEGRAM_TOKEN e os demais valores
go mod tidy
go run ./src/cmd
```

Veja todas as variáveis de ambiente (obrigatórias e opcionais) comentadas em [`.env.example`](.env.example).

## Comandos do bot

- `/destino +5511999999999` — configura o número de WhatsApp de destino para a conversa atual (persistido em `DESTINATIONS_FILE`, padrão `destinations.json`).
- Qualquer outra mensagem de texto, foto, áudio ou documento é encaminhada para o destino configurado.

Se `SEU_NUMERO` estiver definido, ele é usado como destino padrão para conversas que ainda não rodaram `/destino`.

## Controle de acesso

Configure `ALLOWED_CHAT_IDS` (chat_ids separados por vírgula) para restringir quem pode operar o bot. Sem essa variável, qualquer usuário do Telegram que fale com o bot pode usá-lo.

## Status de entrega (opcional)

Se `STATUS_QUEUE_URL` estiver configurado (apontando para a mesma fila usada pelo `consumer-to-zap`), o bot faz polling dessa fila e notifica o usuário do Telegram quando a Twilio confirma entrega, falha ou leitura da mensagem.

## SQS com LocalStack

### Passos para Configuração e Execução

1. **Inicie o LocalStack**:

    ```sh
    docker run --rm -it -d -p 4566:4566 localstack/localstack start
    ```

2. **Crie as filas SQS no LocalStack** (fila principal + DLQ + fila de status), usando o script em `../infra/localstack-init.sh`:

    ```sh
    ../infra/localstack-init.sh
    ```

### Verificação das Mensagens na Fila SQS

```sh
aws sqs receive-message --endpoint-url http://localhost:4566 --queue-url http://localhost:4566/000000000000/tel-bot-queue --attribute-names All --message-attribute-names All
```

## Testes

```sh
go build ./...
go vet ./...
go test ./...
```
