// Package statuspoller consome a fila de status de entrega publicada pelo
// consumer Java (P3.3) e notifica o usuário do Telegram sobre o resultado
// do envio ao WhatsApp, fechando o loop de feedback da conversa.
package statuspoller

import (
	"context"
	"encoding/json"
	"log"
	"time"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	appsqs "Tel-To-Zap-Go/src/sqs"
)

// Run faz polling da fila de status até o contexto ser cancelado. Uma
// falha pontual de rede é logada e a próxima iteração tenta de novo — não
// derruba o processo.
func Run(ctx context.Context, bot *tgbotapi.BotAPI, queue *appsqs.Client) {
	for {
		select {
		case <-ctx.Done():
			log.Print("Encerrando poller de status de entrega")
			return
		default:
		}

		messages, err := queue.ReceiveMessages(ctx, 10, 20)
		if err != nil {
			if ctx.Err() != nil {
				return
			}
			log.Printf("Erro ao consultar fila de status: %v", err)
			time.Sleep(5 * time.Second)
			continue
		}

		for _, m := range messages {
			if m.Body == nil {
				continue
			}

			var status appsqs.StatusMessage
			if err := json.Unmarshal([]byte(*m.Body), &status); err != nil {
				log.Printf("Status de entrega malformado, descartando: %v", err)
				_ = queue.DeleteMessage(ctx, *m.ReceiptHandle)
				continue
			}

			notifyUser(bot, status)

			if err := queue.DeleteMessage(ctx, *m.ReceiptHandle); err != nil {
				log.Printf("Erro ao confirmar status de entrega: %v", err)
			}
		}
	}
}

func notifyUser(bot *tgbotapi.BotAPI, status appsqs.StatusMessage) {
	if status.ChatID == 0 {
		return
	}

	icon := "ℹ️"
	switch status.Status {
	case "delivered", "read":
		icon = "✅"
	case "failed", "undelivered":
		icon = "❌"
	case "sent":
		icon = "📤"
	}

	text := icon + " Status da sua mensagem no WhatsApp: " + status.Status
	if status.ErrorCode != "" {
		text += " (código: " + status.ErrorCode + ")"
	}

	msg := tgbotapi.NewMessage(status.ChatID, text)
	if _, err := bot.Send(msg); err != nil {
		log.Printf("Erro ao notificar usuário sobre status de entrega: %v", err)
	}
}
