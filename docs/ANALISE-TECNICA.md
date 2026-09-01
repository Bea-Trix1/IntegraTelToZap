# Análise Técnica — IntegraTelToZap

**Data:** 2026-08-23
**Escopo:** `Tel-To-Zap-Go` (Go 1.23, producer/bot Telegram) e `consumer-to-zap` (Java 21 + Spring Boot 3.3.2, consumer WhatsApp)
**Autor:** Revisão automatizada (Claude)

> Este documento é só diagnóstico. Nenhuma mudança de código foi feita. As ações propostas estão detalhadas e priorizadas no `docs/SDD-MELHORIAS.md`.

---

## 1. Visão geral da arquitetura

```
Telegram → Tel-To-Zap-Go (producer) → SQS (tel-bot-queue) → consumer-to-zap (Java) → Twilio → WhatsApp
```

Arquitetura simples e correta na concepção (desacoplamento via fila), mas a implementação atual está em estágio de protótipo: sem testes reais, sem observabilidade, sem tratamento de falha ponta-a-ponta, com um problema de segurança e um bug de correção que merecem atenção antes de qualquer feature nova.

---

## 2. Achados por severidade

### 🔴 Crítico

| # | Item | Local | Descrição |
|---|------|-------|-----------|
| C1 | **Injeção de JSON por concatenação de string** | `Tel-To-Zap-Go/src/bot/tgbot.go:34` | O payload SQS é montado com concatenação de string (`` `{"from":...,"text":"` + update.Message.Text + `"}` ``). Qualquer usuário do Telegram pode enviar texto contendo `"`, `\` ou até um `to` fabricado (`", "to":"+5511999999999", "text":"x`) e quebrar o JSON ou **sequestrar o campo `to`**, fazendo o sistema enviar WhatsApp para um número arbitrário à custa da conta Twilio do dono do bot. Isso é abuso de terceiros pagando a conta, não só um bug de parsing. |
| C2 | **Arquivo `.env` versionado no Git** | `Tel-To-Zap-Go/.env` (rastreado pelo git, confirmado via `git ls-files`) | O `.gitignore` não exclui `.env`. Hoje o arquivo está com valores vazios, mas o hábito é perigoso: o primeiro dev que testar localmente e commitar por engano vaza `TELEGRAM_TOKEN` real no histórico do Git (que é permanente). |
| C3 | **Perda silenciosa de mensagens no consumer** | `MessageProcessingService.process()` | O `catch (Exception e)` apenas loga o erro e retorna. Como o `@SqsListener` não relança a exceção, o Spring Cloud AWS **confirma (ack) a mensagem mesmo em falha** — ela é removida da fila sem ir para uma Dead Letter Queue. Qualquer falha transiente do Twilio (timeout, rate limit, 5xx) resulta em mensagem perdida para sempre, sem re-tentativa nem rastro. |

### 🟠 Alto

| # | Item | Local | Descrição |
|---|------|-------|-----------|
| A1 | **Sem Dead Letter Queue (DLQ)** | Infra SQS | Nenhuma DLQ configurada para `tel-bot-queue`. Mensagens malformadas ("poison pill") ou com falha permanente ficam presas em loop de redelivery até expirar, sem alerta. |
| A2 | **Deduplicação frágil e sem limite de memória** | `MessageProcessingService.processedMessages` | Usa `hashCode()` (32 bits, colisões possíveis) de string, guardado em `ConcurrentHashSet` **sem TTL/eviction** — cresce indefinidamente (memory leak) e não sobrevive a restart nem funciona com mais de uma instância do consumer (não é dedup distribuído). |
| A3 | **`Twilio.init()` a cada mensagem** | `TwilioWhatsAppService.sendMessage()` | Reinicializa o SDK Twilio (parsing de credenciais, singleton estático) a cada mensagem processada, ao invés de uma vez na inicialização — overhead evitável e não thread-safe em cenário de concorrência futura. |
| A4 | **Nova sessão AWS a cada envio** | `Tel-To-Zap-Go/src/sqs/sqs.go` | `session.NewSession()` e `sqs.New(sess)` são recriados a cada chamada de `SendMessage`. Deveriam ser criados uma vez e reutilizados (cliente SQS é thread-safe e caro de inicializar). |
| A5 | **`log.Fatalf` dentro de goroutine de negócio** | `sqs.go:19,29` | Um erro de rede pontual ao enviar para o SQS chama `os.Exit`, **derrubando o processo inteiro do bot** por causa de uma única mensagem, ao invés de logar e continuar. |
| A6 | **Endpoint SQS hardcoded** | `sqs.go:16` (`http://localhost:4566`) | Ignora a variável `SQSURL`/config de endpoint — o serviço não tem como apontar para AWS real em produção sem alterar código-fonte, apesar do README descrever deploy em EC2. |
| A7 | **Sem graceful shutdown** | `Tel-To-Zap-Go/src/cmd/main.go:18` (`select{}`) | Não há tratamento de `SIGINT`/`SIGTERM`, nem fechamento do canal de updates do Telegram — em containers/EC2 isso significa mensagens em voo perdidas em todo deploy/restart. |
| A8 | **README menciona recursos que não existem no código** | Raiz | README cita `Dockerfile` para os dois serviços, `docker-compose`, endpoint `/actuator/health` — nenhum dos três existe no repositório (sem `Dockerfile`, sem `docker-compose.yml`, sem dependência `spring-boot-starter-actuator` no `pom.xml`). Documentação desalinhada com a realidade do projeto. |
| A9 | **Nenhum teste real** | Ambos serviços | Go: zero arquivos `_test.go`. Java: apenas `contextLoads()` vazio. Nenhuma cobertura para o fluxo de negócio (parsing, dedup, integração Twilio, handler do bot). |

### 🟡 Médio

| # | Item | Descrição |
|---|------|-----------|
| M1 | **AWS SDK Go v1 (deprecated pela AWS em favor do v2)** | `aws-sdk-go v1.55.5` está em modo de manutenção. Migrar para `aws-sdk-go-v2` traz suporte a `context.Context`, melhor performance e é o caminho suportado a longo prazo. |
| M2 | **Sem `context.Context` em nenhuma chamada Go** | Nem no SQS, nem no polling do Telegram — impossibilita timeout/cancelamento centralizado. |
| M3 | **Sem circuit breaker / retry com backoff para Twilio** | Uma instabilidade da Twilio derruba mensagens em cascata sem proteção (Resilience4j ausente). |
| M4 | **Sem validação de payload (`MessageDTO`)** | Nenhuma verificação de formato E.164 para `to`, nem de tamanho/conteúdo de `text`, antes de chamar a API paga da Twilio. |
| M5 | **Credenciais estáticas "test"/"test" sem profiles Spring** | `application.yml` único, sem `application-prod.yml`; nada impede que a config "de mentira" vá para produção por engano. |
| M6 | **Pacote `Config` capitalizado** | `com.consumertelo.consumer_to_zap.Config` viola convenção Java (pacotes em minúsculo) — inconsistente com `consumer/`, `service/`, `dto/`. |
| M7 | **`spring-boot-starter-web` sem uso real** | Nenhum `@RestController`; dependência web completa presente sem justificar (poderia virar webhook de status de entrega da Twilio, ou ser removida). |
| M8 | **Sem observabilidade** | Nenhuma métrica (Micrometer/Prometheus), sem tracing distribuído (OpenTelemetry), sem correlação de ID de mensagem entre Go → SQS → Java → Twilio. |
| M9 | **Sem CI/CD** | Nenhum workflow em `.github/workflows`; build, lint e teste não são validados automaticamente em PRs. |
| M10 | **Fluxo unidirecional e a um único destino fixo** | `SEU_NUMERO` é uma env var fixa no serviço Go — não há mapeamento dinâmico usuário do Telegram → destinatário WhatsApp, nem resposta no sentido WhatsApp → Telegram. |
| M11 | **Sem controle de acesso no bot Telegram** | Qualquer pessoa que fale com o bot pode disparar envio de WhatsApp (e consumir sua cota paga da Twilio) — falta allow-list de `chat_id`/usuário autorizado. |
| M12 | **LICENSE referenciada mas ausente** | README aponta para arquivo `LICENSE` que não existe no repositório. |

### 🟢 Baixo / nice-to-have

- `go 1.23.0` no `go.mod`; ambiente atual roda `go1.24.7` — vale atualizar o `go.mod` para a toolchain vigente.
- DTOs Java usam Lombok `@Data`; poderiam ser `record` (Java 16+, idiomático em Java 21/25) reduzindo boilerplate e ganhando imutabilidade.
- Sem `golangci-lint`/`.golangci.yml` no Go, sem Checkstyle/Spotless no Java — sem padronização automatizada de estilo.
- Logs não estruturados (texto livre) em ambos os serviços — dificulta correlação em ferramentas tipo CloudWatch Insights/ELK.
- Nenhum rate limiting de saída para a Twilio (a API tem limites por número/segundo).

---

## 2.1 Segurança de acesso — autenticação, autorização e tokens

Análise adicional focada especificamente em quem pode falar com o sistema e como isso é (ou não é) verificado. Hoje o `consumer-to-zap` não expõe nenhum endpoint HTTP próprio (só o `spring-boot-starter-web` sem controllers), então a superfície de ataque atual é pequena — mas **todo o roadmap proposto (Actuator em P2.4, webhook de status em P3.3, API de destinatário em P3.2) adiciona endpoints HTTP sem que exista, hoje, nenhuma camada de autenticação no projeto** (nenhuma dependência `spring-security` no `pom.xml`). Se essas features forem implementadas na ordem do SDD sem tratar isso, o sistema passa a expor endpoints publicamente acessíveis e não autenticados.

| # | Item | Descrição |
|---|------|-----------|
| S1 | **Nenhuma dependência de segurança no projeto** | `pom.xml` não tem `spring-boot-starter-security` nem qualquer filtro de autenticação. Qualquer endpoint HTTP adicionado no futuro (Actuator, webhook) nasce público por padrão. |
| S2 | **Webhook de status da Twilio (P3.3) sem validação de origem** | O design original do SDD não especifica autenticação. A Twilio assina cada requisição de webhook com o header `X-Twilio-Signature`; sem validar essa assinatura, qualquer terceiro pode forjar POSTs de "status de entrega" (ou, pior, forjar recebimento de mensagem no fluxo de volta WhatsApp→Telegram do item P3.3/6 da análise original). |
| S3 | **Bot Telegram sem autenticação de usuário final** | Hoje qualquer pessoa que converse com o bot consegue disparar envio de WhatsApp (já registrado como M11/A11 na análise original) — é um problema de autorização, não só de abuso de custo: não há verificação de identidade de quem está operando o bot. |
| S4 | **Credenciais AWS estáticas (`"test"/"test"`) sem diferenciação por ambiente** | Já registrado em M5, mas do ângulo de autenticação: não existe hoje nenhum caminho para autenticação via IAM Role (identidade da máquina) em produção — o código sempre espera credenciais estáticas via `StaticCredentialsProvider`. |
| S5 | **Segredos em variáveis de ambiente puras, sem cofre/rotação** | `TELEGRAM_TOKEN`, `TWILIO_AUTH_TOKEN` e `TWILIO_ACCOUNT_SID` são lidos diretamente de env vars/`.env`, sem integração com um secrets manager, sem rotação e sem controle de quem pode ler esses valores no ambiente de execução (EC2). |
| S6 | **Actuator (P2.4) exporia `/actuator/prometheus` e `/actuator/health` publicamente** | Métricas internas (volume de mensagens, taxa de erro) e detalhes de saúde da aplicação ficariam acessíveis sem autenticação a qualquer um que alcance a porta 8081. |
| S7 | **Sem HTTPS/TLS explícito documentado para produção** | README descreve deploy em EC2, mas não há menção a TLS/reverse proxy — se os endpoints (atuais ou futuros) forem expostos direto em HTTP, tokens/segredos trafegam em texto claro. |

O `docs/SDD-MELHORIAS.md` foi atualizado com uma nova onda (**Onda S — Autenticação, Autorização e Gestão de Segredos**) detalhando o design de correção para cada um desses pontos, incluindo o mecanismo de token de acesso (API Key/Bearer) para os endpoints internos.

---

## 3. Oportunidades de valor (features)

Além de corrigir os pontos acima, identifiquei funcionalidades que agregariam valor real ao produto:

1. **Roteamento dinâmico de destinatários** — permitir que cada usuário/chat do Telegram configure (via comando `/destino +55...`) para qual número de WhatsApp suas mensagens vão, ao invés de um único `SEU_NUMERO` fixo.
2. **Canal de volta (WhatsApp → Telegram)** — hoje o fluxo é unidirecional; um webhook Twilio de mensagens recebidas poderia publicar de volta no Telegram, fechando o loop de conversa.
3. **Status de entrega** — usar webhooks de status da Twilio (`sent`/`delivered`/`failed`/`read`) para dar feedback ao usuário do Telegram sobre o que realmente chegou.
4. **Painel/observabilidade mínima** — endpoint de métricas (mensagens processadas, taxa de erro, latência fila→WhatsApp) via Actuator + Micrometer, com dashboard Grafana opcional.
5. **Suporte a mídia** (fotos, áudio, documentos) — hoje só texto é tratado; Telegram e WhatsApp Business API suportam mídia, e é um ganho de funcionalidade significativo.
6. **Multi-tenant / múltiplos bots** — hoje é 1 bot Telegram : 1 número Twilio; a arquitetura de fila já comporta expandir para múltiplos pares bot/número com roteamento por metadado da mensagem.

Essas features **não** devem ser priorizadas antes da correção dos itens críticos/altos da seção 2 — a base precisa ficar confiável primeiro.

---

## 4. Resumo executivo

O projeto tem uma arquitetura de fila bem escolhida, mas a implementação atual é de **protótipo/MVP**, não de produção:

- Um bug de segurança real (injeção via JSON concatenado) permite abuso da conta Twilio do operador.
- Falhas de processamento são engolidas silenciosamente (mensagens perdidas sem DLQ).
- Não há testes, CI, observabilidade nem hardening de configuração entre ambientes.

O `docs/SDD-MELHORIAS.md` detalha o plano de implementação, priorizado em ondas (P0 segurança/correção → P1 confiabilidade → P2 performance/arquitetura → P3 features novas).
