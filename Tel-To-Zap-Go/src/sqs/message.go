package sqs

import "github.com/google/uuid"

// Message é o contrato compartilhado com o consumer Java. Nunca deve ser
// montado por concatenação de string: sempre serializar via encoding/json
// para evitar que o conteúdo do usuário quebre ou injete campos no JSON.
type Message struct {
	ID        string `json:"messageId"`
	ChatID    int64  `json:"chatId,omitempty"`
	From      string `json:"from"`
	To        string `json:"to"`
	Text      string `json:"text"`
	MediaURL  string `json:"mediaUrl,omitempty"`
	MediaType string `json:"mediaType,omitempty"`
}

// NewMessage cria uma Message com um messageId único (usado para
// deduplicação e correlação de logs ponta-a-ponta pelo consumer) e o
// chatId de origem (usado pelo consumer em P3.3 para notificar de volta o
// usuário do Telegram sobre o status de entrega).
func NewMessage(chatID int64, from, to, text string) Message {
	return Message{
		ID:     uuid.NewString(),
		ChatID: chatID,
		From:   from,
		To:     to,
		Text:   text,
	}
}

// StatusMessage é publicada pelo consumer Java (P3.3) na fila de status
// depois que a Twilio confirma entrega/falha de uma mensagem, fechando o
// loop de volta para o usuário do Telegram.
type StatusMessage struct {
	ChatID    int64  `json:"chatId"`
	MessageID string `json:"messageId"`
	Status    string `json:"status"`
	ErrorCode string `json:"errorCode,omitempty"`
}
