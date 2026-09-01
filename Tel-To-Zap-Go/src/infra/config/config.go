package config

import (
	"errors"
	"os"
	"strconv"
	"strings"

	"github.com/joho/godotenv"
)

// EnvConfig contém toda a configuração do serviço, carregada de variáveis
// de ambiente (ou de um arquivo .env em desenvolvimento).
type EnvConfig struct {
	TelegramToken string
	AWSRegion     string
	SQSURL        string
	// SQSEndpoint é opcional: vazio faz o SDK resolver o endpoint real da
	// AWS (produção, via IAM Role); setado (ex.: LocalStack) sobrescreve
	// o endpoint para desenvolvimento local.
	SQSEndpoint string
	// StatusQueueURL é opcional: fila usada pelo consumer Java (P3.3) para
	// publicar status de entrega da Twilio de volta ao bot.
	StatusQueueURL string
	// DefaultRecipient é o número de WhatsApp usado quando o chat não tem
	// destino configurado via /destino (P3.2). Pode ficar vazio — nesse
	// caso todo chat precisa configurar seu próprio destino antes de usar.
	DefaultRecipient string
	// AllowedChatIDs, se não vazio, restringe quem pode operar o bot
	// (S3/P3.1). Vazio = qualquer usuário do Telegram pode usar o bot.
	AllowedChatIDs []int64
	// DestinationsFile é o caminho do arquivo de persistência do mapeamento
	// chat -> destino (P3.2).
	DestinationsFile string
}

func LoadFromEnv() (*EnvConfig, error) {
	if err := godotenv.Load("../../.env"); err != nil {
		_ = godotenv.Load()
	}

	allowedChatIDs, err := parseChatIDs(os.Getenv("ALLOWED_CHAT_IDS"))
	if err != nil {
		return nil, err
	}

	destinationsFile := os.Getenv("DESTINATIONS_FILE")
	if destinationsFile == "" {
		destinationsFile = "destinations.json"
	}

	cfg := &EnvConfig{
		TelegramToken:    os.Getenv("TELEGRAM_TOKEN"),
		AWSRegion:        os.Getenv("AWS_REGION"),
		SQSURL:           os.Getenv("SQS_URL"),
		SQSEndpoint:      os.Getenv("SQS_ENDPOINT"),
		StatusQueueURL:   os.Getenv("STATUS_QUEUE_URL"),
		DefaultRecipient: os.Getenv("SEU_NUMERO"),
		AllowedChatIDs:   allowedChatIDs,
		DestinationsFile: destinationsFile,
	}

	if err := cfg.validate(); err != nil {
		return nil, err
	}

	return cfg, nil
}

func (e *EnvConfig) validate() error {
	if e.TelegramToken == "" {
		return errors.New("TELEGRAM_TOKEN não pode ser vazio")
	}
	if e.AWSRegion == "" {
		return errors.New("AWS_REGION não pode ser vazio")
	}
	if e.SQSURL == "" {
		return errors.New("SQS_URL não pode ser vazio")
	}
	return nil
}

func parseChatIDs(raw string) ([]int64, error) {
	if strings.TrimSpace(raw) == "" {
		return nil, nil
	}

	parts := strings.Split(raw, ",")
	ids := make([]int64, 0, len(parts))
	for _, p := range parts {
		p = strings.TrimSpace(p)
		if p == "" {
			continue
		}
		id, err := strconv.ParseInt(p, 10, 64)
		if err != nil {
			return nil, errors.New("ALLOWED_CHAT_IDS contém um chat_id inválido: " + p)
		}
		ids = append(ids, id)
	}
	return ids, nil
}
