# IntegraTelToZap

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.2-green)
![Go](https://img.shields.io/badge/Go-1.23.0-blue)
![AWS SQS](https://img.shields.io/badge/AWS%20SQS-LocalStack-yellow)
![Twilio](https://img.shields.io/badge/Twilio-WhatsApp%20API-red)

## 📋 Sobre o Projeto

O **IntegraTelToZap** é um sistema de integração que conecta Telegram ao WhatsApp através de uma arquitetura baseada em filas de mensagens. O projeto consiste em dois microserviços que trabalham em conjunto:

### 🏗️ Arquitetura

![Diagrama de Arquitetura](diagrama.drawio)

> 📊 **Diagrama interativo**: Abra o arquivo [`diagrama.drawio`](diagrama.drawio) no [Draw.io](https://app.diagrams.net/) para visualização detalhada.

#### 🔄 Fluxo de Dados:

1. **👤 Usuário** envia mensagem no **📱 Bot Telegram**
2. **🔄 Tel-To-Go** (EC2/Go) recebe via Telegram API
3. **☁️ SQS Queue** (`tel-bot-queue`) armazena mensagem JSON
4. **⚙️ Tel-To-Zap** (EC2/Java) consome da fila SQS
5. **💬 WhatsApp** entrega mensagem ao **👥 Receptor**

#### 📋 Componentes:

| Componente | Tecnologia | Função |
|------------|------------|--------|
| **Bot Telegram** | Telegram Bot API | Interface de entrada para usuários |
| **Tel-To-Go** | Go 1.23 + AWS SDK | Producer - Recebe e envia para SQS |
| **SQS Queue** | LocalStack (porta 4566) | Fila `tel-bot-queue` para mensagens |
| **Tel-To-Zap** | Spring Boot + Java 21 | Consumer - Processa e envia WhatsApp |
| **WhatsApp** | Twilio WhatsApp API | Entrega final ao destinatário |

### 🎯 Funcionalidades

- **Bot Telegram (Go)**: Recebe mensagens do Telegram e as envia para uma fila SQS
- **Consumer WhatsApp (Java)**: Consome mensagens da fila SQS e as envia para o WhatsApp via Twilio
- **Processamento Assíncrono**: Utiliza AWS SQS para garantir entrega confiável das mensagens
- **Integração WhatsApp**: Envia mensagens via Twilio WhatsApp Business API

## 🚀 Tecnologias Utilizadas

### Tel-To-Zap-Go (Producer)
- **Go 1.23.0**
- **Telegram Bot API**
- **AWS SDK for Go**
- **LocalStack** (para desenvolvimento local)

### Consumer-To-Zap (Consumer)
- **Java 21**
- **Spring Boot 3.3.2**
- **Spring Cloud AWS SQS**
- **Twilio Java SDK**
- **Maven**

## 📁 Estrutura do Projeto

```
IntegraTelToZap/
├── Tel-To-Zap-Go/              # Serviço Go - Bot Telegram
│   ├── src/
│   │   ├── bot/                # Lógica do bot Telegram
│   │   ├── sqs/                # Cliente AWS SQS
│   │   ├── infra/config/       # Configurações
│   │   └── cmd/                # Entrada da aplicação
│   ├── go.mod
│   └── Dockerfile
│
└── consumer-to-zap/            # Serviço Java - Consumer WhatsApp
    ├── src/main/java/
    │   └── com/consumertelo/consumer_to_zap/
    │       ├── consumer/       # Consumer SQS
    │       ├── service/        # Lógica de negócio
    │       ├── integration/    # Integração Twilio
    │       ├── dto/            # DTOs
    │       └── config/         # Configurações
    ├── pom.xml
    └── Dockerfile
```

## ⚙️ Pré-requisitos

- **Docker** e **Docker Compose**
- **Java 21+**
- **Go 1.23+**
- **Maven 3.6+**
- **AWS CLI** (para configuração do LocalStack)
- **Conta Twilio** (para WhatsApp Business API)
- **Bot Telegram** (criado via @BotFather)

## 🛠️ Configuração

### 1. LocalStack (AWS SQS Local)

```bash
# Iniciar LocalStack
docker run --rm -it -d -p 4566:4566 localstack/localstack start

# Criar fila SQS
aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name tel-bot-queue
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

#### Tel-To-Zap-Go (.env)
```env
TELEGRAM_TOKEN=seu_token_telegram_aqui
AWS_REGION=us-east-1
SQS_URL=http://localhost:4566/000000000000/tel-bot-queue
```

#### Consumer-To-Zap (application.yml)
```yaml
server:
  port: 8081

aws:
  sqs:
    queue:
      url: http://localhost:4566/000000000000/tel-bot-queue
    endpoint-override: http://localhost:4566
  region: us-east-1
  credentials:
    access-key: test
    secret-key: test

twilio:
  account:
    sid: seu_account_sid_twilio
  auth:
    token: seu_auth_token_twilio
  whatsapp:
    number: whatsapp:+1415523xxxx  # Seu número Twilio WhatsApp
```

## 🚀 Como Executar

### 1. Executar Consumer Java (Spring Boot)
```bash
cd consumer-to-zap
mvn clean install
mvn spring-boot:run
```

### 2. Executar Producer Go (Bot Telegram)
```bash
cd Tel-To-Zap-Go
go mod tidy
go run src/cmd/main.go
```

### 3. Usando Docker (Opcional)
```bash
# Consumer Java
cd consumer-to-zap
docker build -t consumer-to-zap .
docker run -p 8081:8081 consumer-to-zap

# Producer Go
cd Tel-To-Zap-Go
docker build -t tel-to-zap-go .
docker run tel-to-zap-go
```

## 📱 Como Usar

1. **Envie uma mensagem para o bot do Telegram**
2. **O bot receberá a mensagem e a enviará para a fila SQS**
3. **O consumer Java processará a mensagem da fila**
4. **A mensagem será enviada para o WhatsApp via Twilio**

### Fluxo de Dados

```json
{
  "from": "produtor-go",
  "to": "+55(DDD)+SeuNumero",
  "text": "Mensagem recebida do Telegram"
}
```

## 🔧 Desenvolvimento

### Estrutura de Mensagens

As mensagens seguem o formato JSON:

```json
{
  "from": "string",    // Origem da mensagem
  "to": "string",      // Número de destino (formato: +5511999999999)
  "text": "string"     // Conteúdo da mensagem
}
```

### Logs e Monitoramento

- **Go Service**: Logs detalhados do recebimento de mensagens do Telegram
- **Java Service**: Logs de processamento SQS e envio para WhatsApp
- **SQS**: Monitoramento via LocalStack dashboard

## 🐛 Troubleshooting

### Problemas Comuns

1. **Erro de conexão SQS**: Verifique se o LocalStack está rodando na porta 4566
2. **Bot não responde**: Verifique o token do Telegram
3. **Mensagens não chegam no WhatsApp**: Verifique credenciais Twilio
4. **Erro de dependências Java**: Execute `mvn clean install`
5. **Erro de módulos Go**: Execute `go mod tidy`

### Verificar Status dos Serviços

```bash
# LocalStack
curl http://localhost:4566/health

# Consumer Java
curl http://localhost:8081/actuator/health

# Verificar fila SQS
aws --endpoint-url=http://localhost:4566 sqs get-queue-attributes --queue-url http://localhost:4566/000000000000/tel-bot-queue --attribute-names All
```

## 📄 Licença

Este projeto está sob a licença MIT. Veja o arquivo [LICENSE](LICENSE) para mais detalhes.

---

⭐ Se este projeto foi útil para você, considere dar uma estrela!
