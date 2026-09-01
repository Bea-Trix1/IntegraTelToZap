# IntegraTelToZap

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.2-green)
![Go](https://img.shields.io/badge/Go-1.24-blue)
![AWS SQS](https://img.shields.io/badge/AWS%20SQS-LocalStack-yellow)
![Twilio](https://img.shields.io/badge/Twilio-WhatsApp%20API-red)

## 📋 Sobre o Projeto

O **IntegraTelToZap** é um sistema de integração que conecta Telegram ao WhatsApp através de uma arquitetura baseada em filas de mensagens. O projeto consiste em dois microserviços que trabalham em conjunto:

### 🏗️ Arquitetura

![Diagrama de Arquitetura](diagrama.drawio)

> 📊 **Diagrama interativo**: Abra o arquivo [`diagrama.drawio`](diagrama.drawio) no [Draw.io](https://app.diagrams.net/) para visualização detalhada.

#### 🔄 Fluxo de Dados:

1. **👤 Usuário** envia mensagem no **📱 Bot Telegram**
2. **🔄 Tel-To-Go** (Go) recebe via Telegram API e publica na fila SQS
3. **☁️ SQS Queue** (`tel-bot-queue`) armazena a mensagem, com DLQ (`tel-bot-queue-dlq`) para falhas
4. **⚙️ Tel-To-Zap** (Java) consome da fila SQS e envia via Twilio
5. **💬 WhatsApp** entrega mensagem ao **👥 Receptor**
6. **🔁 Status de entrega** (opcional): a Twilio notifica o consumer, que publica na fila `tel-bot-status-queue`, consumida de volta pelo bot para avisar o usuário no Telegram

#### 📋 Componentes:

| Componente | Tecnologia | Função |
|------------|------------|--------|
| **Bot Telegram** | Telegram Bot API | Interface de entrada para usuários |
| **Tel-To-Go** | Go 1.24 + AWS SDK v2 | Producer — recebe do Telegram, envia para SQS, notifica status de volta |
| **SQS Queue** | LocalStack (porta 4566) | Filas `tel-bot-queue` (+ DLQ) e `tel-bot-status-queue` |
| **Tel-To-Zap** | Spring Boot + Java 21 | Consumer — processa, envia WhatsApp, expõe webhook de status |
| **WhatsApp** | Twilio WhatsApp API | Entrega final ao destinatário |

### 🎯 Funcionalidades

- **Bot Telegram (Go)**: recebe mensagens (texto, foto, áudio, documento) do Telegram e as envia para uma fila SQS
- **Destino por conversa**: cada chat configura seu próprio número de WhatsApp com `/destino +55DDDNUMERO`
- **Allow-list**: opcionalmente restringe quem pode operar o bot (`ALLOWED_CHAT_IDS`)
- **Consumer WhatsApp (Java)**: consome da fila SQS, valida o payload, deduplica e envia via Twilio
- **Retry + Circuit Breaker**: falhas transientes da Twilio são reprocessadas automaticamente; falhas em cascata abrem o circuito
- **Dead Letter Queue**: mensagens que falham permanentemente não ficam em loop nem se perdem
- **Status de entrega**: webhook autenticado da Twilio fecha o loop, avisando o usuário no Telegram
- **Autenticação**: endpoints internos exigem token de acesso; webhooks da Twilio são validados por assinatura
- **Observabilidade**: métricas Prometheus e health check via Actuator

## 🚀 Tecnologias Utilizadas

### Tel-To-Zap-Go (Producer)
- **Go 1.24**
- **Telegram Bot API**
- **AWS SDK for Go v2**
- **LocalStack** (para desenvolvimento local)

### Consumer-To-Zap (Consumer)
- **Java 21**
- **Spring Boot 3.3.2**
- **Spring Cloud AWS SQS**
- **Spring Security** (API Key para endpoints internos)
- **Resilience4j** (retry + circuit breaker)
- **Caffeine** (deduplicação com TTL)
- **Micrometer + Prometheus** (métricas)
- **Twilio Java SDK**
- **Maven**

## 📁 Estrutura do Projeto

```
IntegraTelToZap/
├── Tel-To-Zap-Go/                  # Serviço Go - Bot Telegram
│   ├── src/
│   │   ├── bot/                    # Lógica do bot Telegram (allow-list, /destino, mídia)
│   │   ├── sqs/                    # Client SQS (AWS SDK v2) e contrato de mensagem
│   │   ├── store/                  # Persistência do destino por conversa
│   │   ├── statuspoller/           # Consumo da fila de status de entrega
│   │   ├── infra/config/           # Configurações
│   │   └── cmd/                    # Entrada da aplicação
│   ├── go.mod
│   ├── .env.example
│   └── Dockerfile
│
├── consumer-to-zap/                # Serviço Java - Consumer WhatsApp
│   ├── src/main/java/
│   │   └── com/consumertelo/consumer_to_zap/
│   │       ├── consumer/           # Consumer SQS
│   │       ├── service/            # Lógica de negócio (dedup, validação, status)
│   │       ├── integration/        # Integração Twilio (retry/circuit breaker)
│   │       ├── dto/                # DTOs
│   │       ├── exception/          # Erros transientes vs. permanentes
│   │       ├── web/                # Webhook de status da Twilio
│   │       └── config/             # Segurança, SQS, profiles
│   ├── src/main/resources/
│   │   ├── application.yml         # Configuração comum
│   │   ├── application-local.yml   # Profile de desenvolvimento (LocalStack)
│   │   └── application-prod.yml    # Profile de produção (IAM Role)
│   ├── pom.xml
│   └── Dockerfile
│
├── infra/
│   └── localstack-init.sh          # Cria as filas SQS + DLQ no LocalStack
│
├── .github/workflows/ci.yml        # Build + testes automatizados (Go e Java)
└── docker-compose.yml              # Orquestra LocalStack + os dois serviços
```

## ⚙️ Pré-requisitos

- **Docker** e **Docker Compose**
- **Java 21+**
- **Go 1.24+**
- **Maven 3.9+**
- **AWS CLI** (para configuração do LocalStack)
- **Conta Twilio** (para WhatsApp Business API)
- **Bot Telegram** (criado via @BotFather)

## 🛠️ Configuração

### 1. LocalStack (AWS SQS Local)

```bash
# Iniciar LocalStack
docker run --rm -it -d -p 4566:4566 localstack/localstack start

# Criar as filas (principal + DLQ + status), já com redrive policy
./infra/localstack-init.sh
```

### 2. Configuração do Bot Telegram

1. Acesse [@BotFather](https://t.me/BotFather) no Telegram
2. Crie um novo bot com `/newbot`
3. Guarde o token gerado

### 3. Configuração do Twilio

1. Crie uma conta no [Twilio](https://www.twilio.com/)
2. Configure o WhatsApp Business API
3. Obtenha: Account SID, Auth Token e número do WhatsApp

### 4. Variáveis de Ambiente

Nenhum segredo é versionado no repositório. Copie os arquivos de exemplo e preencha os valores reais.

#### Tel-To-Zap-Go

```bash
cp Tel-To-Zap-Go/.env.example Tel-To-Zap-Go/.env
```

Veja todas as variáveis (obrigatórias e opcionais — allow-list, fila de status, arquivo de destinos) comentadas em [`Tel-To-Zap-Go/.env.example`](Tel-To-Zap-Go/.env.example).

#### Consumer-To-Zap

Configuração via variáveis de ambiente (não versionadas), lidas por [`application.yml`](consumer-to-zap/src/main/resources/application.yml):

```bash
export TWILIO_ACCOUNT_SID=seu_account_sid_twilio
export TWILIO_AUTH_TOKEN=seu_auth_token_twilio
export TWILIO_WHATSAPP_NUMBER="whatsapp:+1415523xxxx"
export APP_ACCESS_TOKEN=um-token-forte-para-os-endpoints-internos
# Opcional (P3.3): URL pública HTTPS deste serviço, para a Twilio chamar de volta
export TWILIO_STATUS_CALLBACK_BASE_URL=
# Opcional (P3.3): mesma fila de status configurada no producer Go
export STATUS_QUEUE_URL=
```

O profile ativo por padrão é `local` (aponta para o LocalStack com credenciais de teste). Em produção, defina `SPRING_PROFILES_ACTIVE=prod` — nesse profile o serviço nunca usa credenciais estáticas, resolvendo a identidade AWS via IAM Role.

## 🚀 Como Executar

### 1. Executar Consumer Java (Spring Boot)
```bash
cd consumer-to-zap
mvn clean verify
mvn spring-boot:run
```

### 2. Executar Producer Go (Bot Telegram)
```bash
cd Tel-To-Zap-Go
go mod tidy
go run ./src/cmd
```

### 3. Usando Docker Compose (recomendado)

Um único comando sobe LocalStack (com as filas já criadas automaticamente),
o consumer Java e o producer Go:

```bash
cp .env.example .env   # preencha TELEGRAM_TOKEN, TWILIO_*, APP_ACCESS_TOKEN
docker compose up --build
```

- LocalStack só é considerado "pronto" (`healthy`) depois de criar as filas
  `tel-bot-queue` (+ DLQ) e `tel-bot-status-queue` — os outros dois serviços
  esperam esse healthcheck antes de subir, então não há corrida entre
  "fila ainda não existe" e "app já tentando consumir".
- Consumer Java fica em `http://localhost:8081` (`/actuator/health` público,
  `/actuator/prometheus` exige `X-API-Key: $APP_ACCESS_TOKEN`).
- Producer Go conecta no Telegram e já começa a escutar mensagens — não
  expõe porta HTTP.
- Para reconstruir depois de alterar código: `docker compose up --build`.
- Para derrubar tudo: `docker compose down` (adicione `-v` para também
  apagar o volume de dados do LocalStack).

### 4. Usando Docker isoladamente (Opcional)
```bash
# Consumer Java
cd consumer-to-zap
docker build -t consumer-to-zap .
docker run -p 8081:8081 --env-file .env consumer-to-zap

# Producer Go
cd Tel-To-Zap-Go
docker build -t tel-to-zap-go .
docker run --env-file .env tel-to-zap-go
```

## 📱 Como Usar

1. **Configure o destino**: envie `/destino +5511999999999` no chat com o bot (ou defina `SEU_NUMERO` como fallback padrão)
2. **Envie uma mensagem** (texto, foto, áudio ou documento) para o bot do Telegram
3. **O bot valida** se o chat está autorizado (se `ALLOWED_CHAT_IDS` estiver configurado) e envia para a fila SQS
4. **O consumer Java** valida, deduplica e processa a mensagem da fila
5. **A mensagem é enviada** para o WhatsApp via Twilio
6. **(Opcional) Status de entrega**: quando a fila de status está configurada, o bot avisa no Telegram se a mensagem foi entregue/falhou

### Contrato de Mensagem (fila principal)

```json
{
  "messageId": "uuid-gerado-pelo-producer",
  "chatId": 123456789,
  "from": "produtor-go",
  "to": "+5511999999999",
  "text": "Mensagem recebida do Telegram",
  "mediaUrl": "https://api.telegram.org/file/bot<token>/... (opcional)",
  "mediaType": "image/jpeg (opcional)"
}
```

O payload é sempre serializado via `encoding/json` (nunca por concatenação de string), evitando que o conteúdo do usuário injete ou sobrescreva campos do JSON.

### Contrato de Status (fila de status, opcional)

```json
{
  "chatId": 123456789,
  "messageId": "uuid-original",
  "status": "delivered",
  "errorCode": "opcional"
}
```

## 🔒 Segurança

- **Endpoints internos** (ex.: `/actuator/prometheus`) exigem o header `X-API-Key` com o valor de `APP_ACCESS_TOKEN`. `/actuator/health` fica público, mas sem detalhes internos.
- **Webhook da Twilio** (`/webhooks/twilio/status`) é validado pela assinatura `X-Twilio-Signature` — requisições forjadas são rejeitadas com `403` antes de qualquer processamento.
- **Allow-list do bot**: configure `ALLOWED_CHAT_IDS` para restringir quem pode operar o bot Telegram.
- **Produção**: use o profile `prod` (credenciais AWS via IAM Role, nunca estáticas) e um secrets manager (AWS Secrets Manager/Parameter Store) para os segredos — nunca committe `.env` nem tokens reais.

## 🔧 Desenvolvimento

### CI

Todo push/PR roda build + testes de ambos os serviços via GitHub Actions ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)).

### Logs e Monitoramento

- **Go Service**: logs estruturados do recebimento de mensagens e do poller de status
- **Java Service**: logs de processamento SQS, envio para WhatsApp, e métricas em `/actuator/prometheus` (`messages.processed`, `messages.failed`, `messages.duplicated`)
- **SQS**: monitoramento via LocalStack; mensagens com falha permanente vão para `tel-bot-queue-dlq`

## 🐛 Troubleshooting

### Problemas Comuns

1. **Erro de conexão SQS**: verifique se o LocalStack está rodando na porta 4566 e se `./infra/localstack-init.sh` foi executado
2. **Bot não responde**: verifique o token do Telegram e se o `chat_id` está na `ALLOWED_CHAT_IDS` (se configurada)
3. **Mensagens não chegam no WhatsApp**: verifique credenciais Twilio e se o número de destino está em formato E.164 (`+5511999999999`)
4. **`401` nos endpoints internos**: confira o header `X-API-Key`
5. **`403` no webhook da Twilio**: a URL configurada na Twilio precisa ser HTTPS e bater exatamente com a URL pública do serviço
6. **Erro de dependências Java**: execute `mvn clean install`
7. **Erro de módulos Go**: execute `go mod tidy`

### Verificar Status dos Serviços

```bash
# LocalStack
curl http://localhost:4566/health

# Consumer Java (público, sem detalhes)
curl http://localhost:8081/actuator/health

# Métricas (requer API key)
curl -H "X-API-Key: $APP_ACCESS_TOKEN" http://localhost:8081/actuator/prometheus

# Verificar fila SQS
aws --endpoint-url=http://localhost:4566 sqs get-queue-attributes --queue-url http://localhost:4566/000000000000/tel-bot-queue --attribute-names All

# Verificar a DLQ
aws --endpoint-url=http://localhost:4566 sqs receive-message --queue-url http://localhost:4566/000000000000/tel-bot-queue-dlq --attribute-names All
```

## 📄 Licença

Este projeto está sob a licença MIT. Veja o arquivo [LICENSE](LICENSE) para mais detalhes.

---

⭐ Se este projeto foi útil para você, considere dar uma estrela!
