package bot

import (
	"context"
	"log"
	"regexp"
	"slices"
	"strings"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"Tel-To-Zap-Go/src/infra/config"
	appsqs "Tel-To-Zap-Go/src/sqs"
	"Tel-To-Zap-Go/src/store"
)

// e164Pattern valida números no formato internacional exigido pela Twilio
// WhatsApp API (ex.: +5511999999999).
var e164Pattern = regexp.MustCompile(`^\+[1-9]\d{7,14}$`)

// Deps agrupa as dependências do bot, construídas uma única vez em
// main.go e reaproveitadas — nada aqui recria client/sessão a cada
// mensagem.
type Deps struct {
	Cfg          *config.EnvConfig
	Queue        *appsqs.Client
	Destinations *store.DestinationStore
}

// StartBot inicia o polling de updates do Telegram e processa cada
// mensagem até que o contexto seja cancelado (graceful shutdown).
func StartBot(ctx context.Context, deps Deps) error {
	bot, err := tgbotapi.NewBotAPI(deps.Cfg.TelegramToken)
	if err != nil {
		return err
	}

	log.Printf("Login autorizado no bot %s", bot.Self.UserName)

	u := tgbotapi.NewUpdate(0)
	u.Timeout = 60

	updates := bot.GetUpdatesChan(u)
	defer bot.StopReceivingUpdates()

	for {
		select {
		case <-ctx.Done():
			log.Print("Encerrando polling do bot Telegram")
			return nil
		case update, ok := <-updates:
			if !ok {
				return nil
			}
			if update.Message == nil {
				continue
			}
			handleMessage(ctx, bot, deps, update.Message)
		}
	}
}

func handleMessage(ctx context.Context, bot *tgbotapi.BotAPI, deps Deps, message *tgbotapi.Message) {
	chatID := message.Chat.ID

	if !isAuthorized(deps.Cfg.AllowedChatIDs, chatID) {
		log.Printf("Chat não autorizado tentou usar o bot: chat_id=%d", chatID)
		return
	}

	if strings.HasPrefix(message.Text, "/destino") {
		handleSetDestination(bot, deps, message)
		return
	}

	to, ok := resolveDestination(deps, chatID)
	if !ok {
		reply(bot, chatID, message.MessageID,
			"Nenhum destino configurado para esta conversa. Use /destino +55DDDNUMERO antes de enviar mensagens.")
		return
	}

	mediaURL, mediaType := extractMedia(bot, message)

	text := message.Text
	if text == "" {
		text = message.Caption
	}

	msg := appsqs.NewMessage("produtor-go", to, text)
	msg.MediaURL = mediaURL
	msg.MediaType = mediaType

	log.Printf("Enviando mensagem para fila SQS (id=%s, chat_id=%d)", msg.ID, chatID)

	if err := deps.Queue.SendMessage(ctx, msg); err != nil {
		log.Printf("Falha ao enviar mensagem para o SQS (id=%s): %v", msg.ID, err)
		reply(bot, chatID, message.MessageID, "Não foi possível encaminhar sua mensagem agora, tente novamente em instantes.")
	}
}

func handleSetDestination(bot *tgbotapi.BotAPI, deps Deps, message *tgbotapi.Message) {
	chatID := message.Chat.ID
	args := strings.TrimSpace(strings.TrimPrefix(message.Text, "/destino"))

	if !e164Pattern.MatchString(args) {
		reply(bot, chatID, message.MessageID, "Formato inválido. Use: /destino +5511999999999")
		return
	}

	if err := deps.Destinations.Set(chatID, args); err != nil {
		log.Printf("Erro ao salvar destino para chat_id=%d: %v", chatID, err)
		reply(bot, chatID, message.MessageID, "Não foi possível salvar o destino, tente novamente.")
		return
	}

	reply(bot, chatID, message.MessageID, "Destino configurado: "+args)
}

func resolveDestination(deps Deps, chatID int64) (string, bool) {
	if number, ok := deps.Destinations.Get(chatID); ok {
		return number, true
	}
	if deps.Cfg.DefaultRecipient != "" {
		return "+55" + deps.Cfg.DefaultRecipient, true
	}
	return "", false
}

func isAuthorized(allowed []int64, chatID int64) bool {
	if len(allowed) == 0 {
		return true
	}
	return slices.Contains(allowed, chatID)
}

// extractMedia resolve a URL pública de mídia (foto/documento/áudio) do
// Telegram para ser repassada à Twilio (P3.4). O Telegram serve arquivos
// via HTTPS usando o token do bot no path — essa URL é de curta duração e
// só permite download do arquivo, mas ainda assim não deve ser logada.
func extractMedia(bot *tgbotapi.BotAPI, message *tgbotapi.Message) (mediaURL, mediaType string) {
	var fileID, mimeType string

	switch {
	case len(message.Photo) > 0:
		fileID = message.Photo[len(message.Photo)-1].FileID
		mimeType = "image/jpeg"
	case message.Voice != nil:
		fileID = message.Voice.FileID
		mimeType = message.Voice.MimeType
	case message.Document != nil:
		fileID = message.Document.FileID
		mimeType = message.Document.MimeType
	default:
		return "", ""
	}

	url, err := bot.GetFileDirectURL(fileID)
	if err != nil {
		log.Printf("Erro ao resolver URL de mídia do Telegram: %v", err)
		return "", ""
	}

	if mimeType == "" {
		mimeType = "application/octet-stream"
	}

	return url, mimeType
}

func reply(bot *tgbotapi.BotAPI, chatID int64, replyTo int, text string) {
	msg := tgbotapi.NewMessage(chatID, text)
	msg.ReplyToMessageID = replyTo
	if _, err := bot.Send(msg); err != nil {
		log.Printf("Erro ao responder no Telegram: %v", err)
	}
}
