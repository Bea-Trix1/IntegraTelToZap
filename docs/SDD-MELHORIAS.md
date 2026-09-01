# SDD — Plano de Implementação de Melhorias (IntegraTelToZap)

**Tipo:** Software Design Document (spec-driven)
**Baseado em:** `docs/ANALISE-TECNICA.md`
**Status:** proposta — nenhum item foi implementado ainda. Aguardando decisão sobre quais ondas executar.

---

## 0. Como usar este documento

Cada item tem: **Problema → Design proposto → Critérios de aceite → Arquivos afetados → Esforço**.
Os itens estão agrupados em ondas (P0 a P3). Cada onda é independente e pode ser aprovada/implementada separadamente. Não é necessário implementar tudo — o objetivo é você escolher o que entra em cada PR.

---

## Onda P0 — Segurança e correção (bloqueadores)

### P0.1 — Corrigir injeção de JSON no producer Go

**Problema:** payload SQS montado por concatenação de string em `bot/tgbot.go`, permitindo que o próprio texto do usuário injete/sobrescreva campos JSON (ex.: redirecionar `to` para outro número).

**Design:**
- Definir `type SqsMessage struct { From, To, Text string }` com tags `json:"from"`, `json:"to"`, `json:"text"`.
- Serializar com `encoding/json.Marshal`, nunca concatenação.
- `sqs.SendMessage` passa a receber a struct (ou `[]byte` já serializado), não mais montar string.

**Critérios de aceite:**
- Enviar mensagem contendo `"`, `\`, `{`, `}` no texto do Telegram não altera o `to` nem quebra o JSON recebido pelo consumer.
- Teste unitário cobrindo caracteres de escape e emoji/unicode.

**Arquivos:** `Tel-To-Zap-Go/src/bot/tgbot.go`, `Tel-To-Zap-Go/src/sqs/sqs.go` (novo tipo compartilhado, ex. `src/sqs/message.go`).

**Esforço:** pequeno (S).

---

### P0.2 — Remover `.env` do controle de versão

**Problema:** `Tel-To-Zap-Go/.env` está rastreado pelo Git; `.gitignore` não cobre `.env`.

**Design:**
- `git rm --cached Tel-To-Zap-Go/.env`.
- Adicionar `.env` e `*.env` (exceto `.env.example`) ao `.gitignore`.
- Criar `Tel-To-Zap-Go/.env.example` com chaves sem valores (documentação de quais vars são necessárias), mantendo o README como guia.
- (Opcional, se algum valor real algum dia foi commitado no histórico) avaliar necessidade de rotação de credenciais — não se aplica hoje pois os valores estão vazios.

**Critérios de aceite:** `git ls-files | grep -c '\.env$'` retorna 0; `.env.example` presente e documentado.

**Arquivos:** `.gitignore`, `Tel-To-Zap-Go/.env` (remover do tracking), `Tel-To-Zap-Go/.env.example` (novo).

**Esforço:** trivial (XS).

---

### P0.3 — Não perder mensagens em falha (consumer Java)

**Problema:** exceções em `MessageProcessingService.process()` são engolidas; o listener sempre confirma (ack) a mensagem, mesmo em erro — perda silenciosa, sem DLQ.

**Design:**
- Diferenciar **erro permanente** (JSON malformado, `to`/`text` inválido → não adianta reprocessar) de **erro transiente** (timeout Twilio, 5xx, rate limit → vale reprocessar).
- Erro transiente: relançar exceção (ou usar `AcknowledgementMode` manual) para que a mensagem volte à fila e seja reentregue; após N tentativas, cair na DLQ (ver P0.4).
- Erro permanente: logar com nível `ERROR` incluindo motivo estruturado, mandar para DLQ imediatamente (ou tópico de "mensagens inválidas"), e confirmar (não fica reprocessando infinitamente).
- Extrair uma exceção customizada `MessageProcessingException(transient: boolean)` para tornar essa decisão explícita no código.

**Critérios de aceite:**
- Teste unitário: falha simulada da Twilio (mock lançando exceção) resulta em **não-ack** e a mensagem é redirecionada para retry/DLQ, não desaparece.
- Teste unitário: JSON malformado vai direto para fila de erro, sem loop infinito de redelivery.

**Arquivos:** `MessageProcessingService.java`, `MessageConsumer.java`, novo `MessageProcessingException.java`.

**Esforço:** médio (M).

---

### P0.4 — Configurar Dead Letter Queue

**Problema:** `tel-bot-queue` não tem DLQ associada.

**Design:**
- Criar `tel-bot-queue-dlq` no LocalStack/AWS (documentar comando `aws sqs create-queue` + `RedrivePolicy` com `maxReceiveCount` ex.: 5).
- Atualizar `SqsConfig`/infra (LocalStack init script ou Terraform, se adotado) para amarrar a fila principal à DLQ.
- Documentar no README como inspecionar a DLQ.

**Critérios de aceite:** mensagem que falha 5x é movida automaticamente para a DLQ e pode ser inspecionada via `aws sqs receive-message` na fila de erro.

**Arquivos:** script de setup de infra (`Tel-To-Zap-Go/README.md` / novo `infra/localstack-init.sh`), `docs`.

**Esforço:** pequeno (S).

---

## Onda P1 — Confiabilidade e correção de configuração

### P1.1 — Reaproveitar sessão/cliente AWS no Go (producer)

**Problema:** `session.NewSession` + `sqs.New` recriados a cada envio (`sqs.go`).

**Design:** inicializar o cliente SQS uma vez (ex. em `sqs.NewClient(cfg)` chamado a partir de `main.go`), injetá-lo no pacote `bot` (evitar variáveis globais implícitas — passar como dependência explícita).

**Critérios de aceite:** cliente SQS criado uma única vez por execução do processo; benchmark/log confirma reuso.

**Arquivos:** `sqs.go`, `main.go`, `bot/tgbot.go` (assinatura passa a receber o client).

**Esforço:** pequeno (S).

---

### P1.2 — Corrigir endpoint SQS hardcoded

**Problema:** `sqs.go:16` usa `http://localhost:4566` fixo, ignorando configuração de ambiente.

**Design:** adicionar `SQSEndpoint` (opcional) em `EnvConfig`; se vazio, usar SDK default (endpoint real da AWS); se setado (dev/LocalStack), usar o override. Nunca hardcode.

**Critérios de aceite:** rodar com `SQS_ENDPOINT` vazio aponta para AWS real; setado, aponta para LocalStack — sem alterar código.

**Arquivos:** `infra/config/config.go`, `sqs/sqs.go`.

**Esforço:** trivial (XS).

---

### P1.3 — Não derrubar o processo em erro de envio SQS

**Problema:** `log.Fatalf` em `sqs.go:19,29` mata o processo inteiro por falha pontual.

**Design:** trocar `log.Fatalf` por `log.Printf` + retorno de `error`; `bot/tgbot.go` decide o que fazer (retry simples com backoff, ou apenas logar e seguir para a próxima mensagem do Telegram).

**Critérios de aceite:** simular falha de rede no envio ao SQS não derruba o bot; próximas mensagens do Telegram continuam sendo processadas.

**Arquivos:** `sqs/sqs.go`, `bot/tgbot.go`.

**Esforço:** pequeno (S).

---

### P1.4 — Graceful shutdown no producer Go

**Problema:** `main.go` usa `select{}` sem tratar `SIGINT`/`SIGTERM`.

**Design:** usar `context.Context` + `signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)`; propagar o contexto para o polling do bot e para o cliente SQS; ao cancelar, parar `GetUpdatesChan` de forma limpa e logar shutdown.

**Critérios de aceite:** `SIGTERM` no processo encerra o polling do Telegram e finaliza o processo em até N segundos, sem mensagens "em voo" perdidas de forma abrupta (best-effort).

**Arquivos:** `cmd/main.go`, `bot/tgbot.go`.

**Esforço:** médio (M).

---

### P1.5 — `Twilio.init()` uma única vez

**Problema:** reinicializado a cada mensagem em `TwilioWhatsAppService.sendMessage()`.

**Design:** mover `Twilio.init(accountSid, authToken)` para um método anotado `@PostConstruct` (ou usar `TwilioRestClient` explícito injetado como bean), executado uma vez na subida do contexto Spring.

**Critérios de aceite:** teste garante que `Twilio.init` é chamado apenas uma vez mesmo processando múltiplas mensagens (via spy/verify).

**Arquivos:** `TwilioWhatsAppService.java`.

**Esforço:** trivial (XS).

---

### P1.6 — Deduplicação robusta e limitada em memória

**Problema:** `Set<String>` de hash cresce sem limite e não é distribuído.

**Design (curto prazo, sem infra nova):** trocar por `Caffeine` cache com TTL (ex. 24h) e tamanho máximo, chaveado por um `messageId` real (ver P1.7) em vez de `hashCode()`.
**Design (médio prazo, se houver >1 instância do consumer):** mover dedup para o SQS nativo (fila FIFO + `MessageDeduplicationId`) ou um store externo (Redis/DynamoDB com TTL).

**Critérios de aceite:** memória do dedup não cresce ilimitadamente (bounded cache); reenvio da mesma mensagem dentro do TTL é ignorado; após expirar TTL, uma redelivery legítima antiga não é bloqueada para sempre.

**Arquivos:** `MessageProcessingService.java`, `pom.xml` (dependência Caffeine).

**Esforço:** médio (M).

---

### P1.7 — Adicionar `messageId` ao contrato de mensagem

**Problema:** dedup depende de hash do JSON cru; não há identificador estável gerado pelo producer.

**Design:** producer Go gera um UUID por mensagem (`messageId`) e inclui no payload; consumer usa esse campo para dedup (P1.6) e para correlação de logs (P1.8 / M8 da análise).

**Critérios de aceite:** todo payload no SQS tem `messageId` único; consumer loga esse ID em toda a cadeia de processamento.

**Arquivos:** `sqs/message.go` (Go), `MessageDTO.java` (Java).

**Esforço:** pequeno (S).

---

### P1.8 — Validação do payload no consumer

**Problema:** `MessageDTO` não valida formato de `to` (E.164) nem `text` vazio antes de chamar a Twilio (custo por chamada).

**Design:** usar `jakarta.validation` (`spring-boot-starter-validation`) com `@NotBlank` em `text`, `@Pattern(regexp = "^\\+[1-9]\\d{7,14}$")` em `to`; validar explicitamente em `MessageProcessingService` antes de chamar `WhatsAppService`, tratando falha de validação como erro permanente (ver P0.3).

**Critérios de aceite:** payload com `to` fora do formato E.164 não gera chamada à Twilio; erro de validação é logado e roteado como erro permanente.

**Arquivos:** `MessageDTO.java`, `pom.xml`, `MessageProcessingService.java`.

**Esforço:** pequeno (S).

---

## Onda S — Autenticação, Autorização e Gestão de Segredos

> Onda transversal, referenciada a partir de P2.4 (Actuator), P3.2 (API de destinatário) e P3.3 (webhook de status): nenhum endpoint HTTP novo deve ser implementado sem os itens correspondentes desta onda já resolvidos. Não é opcional — hoje o projeto não tem nenhuma dependência de segurança (`spring-security` ausente do `pom.xml`), então cada endpoint HTTP futuro nasce público a menos que isto seja tratado primeiro.

### S1 — Adicionar Spring Security + autenticação por token (API Key/Bearer) no consumer Java

**Problema:** não existe `spring-boot-starter-security` no projeto; qualquer endpoint HTTP adicionado (Actuator, webhooks, futura API) fica público por padrão.

**Design:**
- Adicionar `spring-boot-starter-security` ao `pom.xml`.
- Definir um filtro simples de autenticação por **API Key** via header (`X-API-Key` ou `Authorization: Bearer <token>`) para endpoints internos/administrativos — não é necessário OAuth2 completo para o escopo atual do projeto (dois serviços internos, sem usuários finais autenticando diretamente na API).
- O token de acesso (`APP_ACCESS_TOKEN` ou similar) é gerado uma vez, armazenado como segredo (ver S5) e comparado com `SecureCompare`/`MessageDigest.isEqual` para evitar timing attack — nunca `String.equals`.
- `SecurityFilterChain` explícito: nega tudo por padrão (`anyRequest().authenticated()`), com exceções explícitas e mínimas apenas onde fizer sentido (ex.: `/actuator/health` pode ficar liberado só para status simples "UP/DOWN" sem detalhes, os demais exigem token).
- Endpoints de webhook (Twilio) usam autenticação própria (S2), não o mesmo token de API interna.

**Critérios de aceite:**
- Requisição a qualquer endpoint protegido sem header de autenticação retorna `401`.
- Requisição com token inválido retorna `401`; com token válido, `200`.
- Teste de integração (`@SpringBootTest` + `MockMvc`) cobrindo os três casos (sem token, token errado, token certo).

**Arquivos:** `pom.xml`, novo `config/SecurityConfig.java`, novo `config/ApiKeyAuthFilter.java`, `application.yml`.

**Esforço:** médio (M).

---

### S2 — Validar assinatura de webhooks da Twilio (`X-Twilio-Signature`)

**Problema:** o webhook de status de entrega (P3.3) e qualquer futuro webhook de mensagem recebida não têm, no design original, verificação de que a requisição realmente veio da Twilio.

**Design:**
- Usar `com.twilio.security.RequestValidator` (já disponível via SDK Twilio, já presente no `pom.xml`) com o `TWILIO_AUTH_TOKEN` para validar o header `X-Twilio-Signature` contra a URL completa + parâmetros do POST, em um filtro/interceptor dedicado só para as rotas `/webhooks/twilio/**`.
- Requisição sem assinatura válida é rejeitada com `403` **antes** de qualquer processamento de negócio.
- Essas rotas ficam de fora do filtro de API Key (S1) — usam essa validação de assinatura como mecanismo de autenticação próprio, conforme padrão recomendado pela própria Twilio.

**Critérios de aceite:**
- POST simulando o payload da Twilio sem o header correto (ou com assinatura forjada) é rejeitado com `403` e não chama `MessageProcessingService`/lógica de negócio.
- POST com assinatura válida (calculada com o auth token de teste) é aceito.

**Arquivos:** novo `config/TwilioWebhookValidationFilter.java`, controller do webhook (P3.3).

**Esforço:** pequeno (S).

---

### S3 — Autorização de usuário final no bot Telegram (allow-list + ativação)

**Problema:** qualquer pessoa que converse com o bot consegue disparar envio de WhatsApp; hoje não existe verificação de identidade de quem está operando o bot (ver também P3.1, que cobre a allow-list básica).

**Design:** este item complementa P3.1 do ângulo de autenticação:
- Allow-list de `chat_id` via env var (`ALLOWED_CHAT_IDS`), como já proposto em P3.1.
- Opcionalmente, um comando `/ativar <código>` onde `<código>` é um token de ativação de uso único gerado fora de banda (ex.: pelo operador do bot) — permite habilitar novos usuários sem redeploy, mantendo controle de quem pode operar o sistema.
- Toda tentativa de uso por `chat_id` não autorizado é logada (auditoria) e ignorada silenciosamente ou respondida com mensagem padrão, sem revelar detalhes internos.

**Critérios de aceite:** mensagem de `chat_id` fora da allow-list não gera envio para a fila SQS; tentativa é registrada em log com nível `WARN`.

**Arquivos:** `Tel-To-Zap-Go/src/bot/tgbot.go`, `infra/config/config.go`.

**Esforço:** pequeno (S) — mesmo esforço de P3.1, absorve o item.

---

### S4 — Autenticação de infraestrutura via IAM Role (produção) em vez de credenciais estáticas

**Problema:** `StaticCredentialsProvider.create("test", "test")` está fixo no `SqsConfig` do consumer Java; o producer Go também depende de credenciais estáticas via env var. Não há caminho para autenticação por identidade da máquina (IAM Role) em produção.

**Design:**
- Combina com P2.3 (perfis Spring): no profile `prod`, usar `DefaultCredentialsProvider` (Java) / `config.LoadDefaultConfig` (Go, via P2.1) — que resolvem automaticamente para a IAM Role da instância EC2/task, sem nenhuma credencial estática no código ou em env var.
- Política IAM de menor privilégio: o producer só precisa de `sqs:SendMessage` na fila específica; o consumer só precisa de `sqs:ReceiveMessage`, `sqs:DeleteMessage`, `sqs:GetQueueAttributes` na mesma fila (+ permissões equivalentes na DLQ, se aplicável).
- Profile `local`/`dev` continua usando `StaticCredentialsProvider("test","test")` para LocalStack — isso é aceitável **somente** fora de produção.

**Critérios de aceite:** rodando com profile `prod`, nenhuma credencial estática é usada (auditável via log de qual `CredentialsProvider` foi resolvido); política IAM documentada com as ações mínimas necessárias.

**Arquivos:** `SqsConfig.java`, `sqs/sqs.go`, documento de política IAM (`docs/iam-policy.md` ou similar).

**Esforço:** pequeno (S), depende de P2.1 e P2.3.

---

### S5 — Gestão centralizada de segredos (Secrets Manager / Parameter Store)

**Problema:** `TELEGRAM_TOKEN`, `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN` e o futuro `APP_ACCESS_TOKEN` (S1) hoje vivem em variáveis de ambiente/`.env` sem cofre, sem rotação e sem controle de acesso granular.

**Design:**
- Produção: mover esses valores para **AWS Secrets Manager** (ou SSM Parameter Store com `SecureString`, opção mais barata); a aplicação Java já ganha suporte nativo via `spring-cloud-aws-starter-secrets-manager`; o producer Go busca via AWS SDK na inicialização (reaproveitando P1.1/P2.1).
- Local/dev: continua via `.env`/env vars normais (sem custo de infraestrutura para desenvolvimento).
- Documentar processo de rotação manual do `TWILIO_AUTH_TOKEN` e `TELEGRAM_TOKEN` (rotação automática fica fora de escopo inicial).
- Nenhum segredo deve aparecer em log — revisar todos os pontos de log (`@Value` fields, `os.Getenv` prints) para garantir que valores de token nunca são logados, nem em `DEBUG`.

**Critérios de aceite:** em profile `prod`, nenhuma credencial sensível é lida de env var/arquivo — todas vêm do Secrets Manager/Parameter Store; varredura manual dos logs não encontra nenhum valor de token/segredo impresso.

**Arquivos:** `pom.xml`, `SqsConfig.java`/novo `SecretsConfig.java`, `infra/config/config.go`, README (documentação do processo).

**Esforço:** médio (M).

---

### S6 — Restringir Actuator e exigir token nas métricas

**Problema:** o design original de P2.4 expõe `/actuator/health` e `/actuator/prometheus` sem nenhuma proteção.

**Design:**
- `/actuator/health` liberado publicamente, mas apenas com `management.endpoint.health.show-details: never` (retorna só `UP`/`DOWN`, sem detalhes internos).
- `/actuator/prometheus` e qualquer outro endpoint de Actuator exigem o token de API (S1) — configurado via `SecurityFilterChain` dedicado para `/actuator/**` (exceto `/actuator/health`).
- Endpoints de Actuator não essenciais (`/actuator/env`, `/actuator/beans`, etc.) desabilitados explicitamente via `management.endpoints.web.exposure.include` com allow-list mínima (`health,prometheus`).

**Critérios de aceite:** `/actuator/health` acessível sem token e sem vazar detalhes; `/actuator/prometheus` retorna `401` sem token; `/actuator/env` retorna `404` (não exposto).

**Arquivos:** `application.yml`, `config/SecurityConfig.java` (reaproveita S1).

**Esforço:** trivial (XS), depende de S1 e P2.4.

---

### S7 — TLS/HTTPS obrigatório em produção

**Problema:** README descreve deploy em EC2 sem menção a TLS; se os endpoints (Actuator, webhooks) forem expostos direto em HTTP, tokens e payloads trafegam em texto claro.

**Design:**
- Documentar (não é código da aplicação, é infraestrutura) que o serviço Java em produção deve ficar atrás de um reverse proxy/load balancer com TLS terminando ali (ex.: ALB da AWS com certificado ACM, ou Nginx com Let's Encrypt), nunca exposto direto em HTTP na porta 8081 pública.
- Webhooks da Twilio exigem HTTPS por padrão — sem isso, `X-Twilio-Signature` (S2) nem é enviado corretamente pela Twilio.

**Critérios de aceite:** URL de webhook cadastrada na Twilio é `https://`; checklist de deploy documentado inclui "TLS configurado no proxy/LB" como item obrigatório antes de ir para produção.

**Arquivos:** `docs/SDD-MELHORIAS.md` (este item), README (seção de deploy).

**Esforço:** pequeno (S) — documentação + configuração de infra, não é código da aplicação.

---

## Onda P2 — Performance, arquitetura e qualidade

### P2.1 — Migrar Go de `aws-sdk-go` (v1) para `aws-sdk-go-v2`

**Problema:** SDK v1 está em modo manutenção; sem suporte nativo a `context.Context`.

**Design:** substituir `github.com/aws/aws-sdk-go` por `github.com/aws/aws-sdk-go-v2` + `.../service/sqs`; cliente construído com `config.LoadDefaultConfig(ctx, ...)`; todas chamadas passam a receber `context.Context` (reaproveita P1.4).

**Critérios de aceite:** `go build` sem dependência do SDK v1; envio de mensagem respeita `context.Context` (timeout configurável).

**Arquivos:** `go.mod`, `go.sum`, `sqs/sqs.go`.

**Esforço:** médio (M).

---

### P2.2 — Circuit breaker + retry com backoff para Twilio

**Problema:** nenhuma proteção contra instabilidade da API externa.

**Design:** adicionar `resilience4j-spring-boot3`; envolver `WhatsAppService.sendMessage` com `@Retry` (backoff exponencial, poucas tentativas) + `@CircuitBreaker`; métricas expostas via Actuator (depende de P2.4).

**Critérios de aceite:** falha simulada da Twilio (mock 5xx) aciona retry conforme política configurada; após N falhas consecutivas o circuito abre e novas chamadas falham rápido sem sobrecarregar a API externa.

**Arquivos:** `pom.xml`, `TwilioWhatsAppService.java`, `application.yml`.

**Esforço:** médio (M).

---

### P2.3 — Perfis Spring por ambiente

**Problema:** único `application.yml` com credenciais fake fixas, sem separação dev/prod.

**Design:** `application.yml` (defaults comuns) + `application-local.yml` (LocalStack, credenciais fake) + `application-prod.yml` (usa `DefaultCredentialsProvider`/IAM role da EC2, sem `endpoint-override`); ativar via `SPRING_PROFILES_ACTIVE`.

**Critérios de aceite:** subir com profile `prod` não referencia LocalStack nem credenciais estáticas; subir com `local` reproduz comportamento atual.

**Arquivos:** `application.yml` (split), `SqsConfig.java` (condicional por profile).

**Esforço:** pequeno (S).

---

### P2.4 — Observabilidade mínima (Actuator + Micrometer)

**Problema:** README já promete `/actuator/health`, mas a dependência não existe; sem métricas de negócio.

**Design:** adicionar `spring-boot-starter-actuator` + `micrometer-registry-prometheus`; expor `/actuator/health`, `/actuator/prometheus`; instrumentar contadores customizados (`messages.processed`, `messages.failed`, `messages.duplicated`) em `MessageProcessingService`.

**Critérios de aceite:** `/actuator/health` responde 200; `/actuator/prometheus` expõe as métricas customizadas.

**Arquivos:** `pom.xml`, `application.yml`, `MessageProcessingService.java`.

**Esforço:** pequeno (S).

---

### P2.5 — CI (build + lint + test) via GitHub Actions

**Problema:** sem `.github/workflows`; nada valida PRs automaticamente.

**Design:** workflow `ci.yml` com dois jobs paralelos:
- **java**: `mvn -B verify` (compila + roda testes) em `consumer-to-zap/`.
- **go**: `go build ./...`, `go vet ./...`, `go test ./...` em `Tel-To-Zap-Go/`.

Gatilho: `pull_request` e `push` na branch principal.

**Critérios de aceite:** PR aberto dispara os dois jobs e falha se build/teste quebrar.

**Arquivos:** `.github/workflows/ci.yml` (novo).

**Esforço:** pequeno (S).

---

### P2.6 — Suíte de testes de negócio

**Problema:** cobertura de teste praticamente nula nos dois serviços.

**Design (Java):** testes unitários com JUnit5 + Mockito para `MessageProcessingService` (dedup, erro transiente vs. permanente, validação) e `TwilioWhatsAppService` (mock do client Twilio). **Design (Go):** testes para serialização do payload (P0.1), para `config.LoadFromEnv` (casos de env faltando) e para o client SQS (usando interface + mock, sem chamar rede real).

**Critérios de aceite:** cobertura cobre os fluxos felizes e os de erro descritos nas ondas P0/P1; testes rodam em CI (P2.5).

**Arquivos:** `*_test.go` (Go), `src/test/java/...` (Java).

**Esforço:** médio-grande (M/L), pode ser fatiado por classe.

---

### P2.7 — Ajustes de estilo/estrutura

**Problema:** pacote `Config` (maiúsculo) quebra convenção Java; `spring-boot-starter-web` sem uso.

**Design:** renomear pacote para `config` (minúsculo); decidir entre (a) remover `spring-boot-starter-web` se nenhum endpoint HTTP for necessário, ou (b) mantê-lo e já aproveitar para o webhook de status da Twilio (Onda P3).

**Critérios de aceite:** build verde após rename; dependência web justificada ou removida.

**Arquivos:** `Config/` → `config/` (mover classe), `pom.xml`.

**Esforço:** trivial (XS).

---

## Onda P3 — Features de produto (após base estável)

### P3.1 — Controle de acesso no bot Telegram

**Problema:** qualquer usuário do Telegram pode gerar envio de WhatsApp (custo/abuso).

**Design:** allow-list de `chat_id` autorizados via env var (`ALLOWED_CHAT_IDS`); mensagens de chats não autorizados são ignoradas/respondidas com aviso, sem ir para a fila.

**Esforço:** pequeno (S).

---

### P3.2 — Destinatário dinâmico por conversa

**Problema:** `SEU_NUMERO` é fixo por deploy; não escala para múltiplos usuários/destinos.

**Design:** comando `/destino +55XXXXXXXXXXX` no bot grava a associação `chat_id → número WhatsApp` (store simples: SQLite/DynamoDB); `to` do payload passa a vir dessa associação em vez de env var fixa.

**Esforço:** grande (L) — inclui persistência nova.

---

### P3.3 — Webhook de status de entrega da Twilio

**Problema:** sem retorno se a mensagem foi de fato entregue/lida.

**Design:** endpoint REST (`spring-boot-starter-web` já disponível) recebendo callback de status da Twilio; opcionalmente republica status em uma fila/tópico para o bot notificar o usuário do Telegram.

**Esforço:** médio-grande (M/L).

---

### P3.4 — Suporte a mídia (foto/áudio/documento)

**Problema:** hoje só texto é propagado.

**Design:** producer Go detecta tipo de mídia do update do Telegram, faz upload/obtém URL pública (ou repassa via Twilio Media API), inclui `mediaUrl`/`mediaType` no payload; consumer usa `Message.creator(...).setMediaUrl(...)`.

**Esforço:** grande (L).

---

## 5. Roadmap sugerido

```
Onda P0 (segurança/correção) ─▶ Onda P1 (confiabilidade) ─▶ Onda S (auth/segredos) ─▶ Onda P2 (perf/qualidade/CI) ─▶ Onda P3 (features)
```

A **Onda S** foi posicionada antes da P2 porque P2.4 (Actuator) já expõe HTTP, e é pré-requisito obrigatório de P3.2 (API de destinatário) e P3.3 (webhook de status) — nenhum desses itens deve entrar em produção sem os controles de autenticação/segredos de S1–S7.

Recomendação: **não pular P0 nem a Onda S**, independentemente de quais features de P3 forem priorizadas depois — os itens P0 são risco de segurança/perda de dados ativos hoje, e a Onda S evita que o próprio roadmap de melhorias (P2/P3) introduza endpoints públicos sem autenticação.

## 6. Decisão

Este SDD é uma proposta. Aguardando você indicar quais ondas/itens deseja que eu implemente (posso seguir onda por onda, ou selecionar itens pontuais independente da onda).
