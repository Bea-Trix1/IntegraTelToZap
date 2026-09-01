package sqs

import (
	"encoding/json"
	"testing"
)

// TestNewMessage_EscapesUserInput garante que o payload SQS é sempre
// serializado via encoding/json — nunca por concatenação de string — para
// que texto malicioso do usuário não sequestre outros campos do JSON
// (ver P0.1: injeção corrigida).
func TestNewMessage_EscapesUserInput(t *testing.T) {
	malicious := `", "to":"+5511999999999", "text":"hackeado`

	msg := NewMessage("produtor-go", "+5511888888888", malicious)

	body, err := json.Marshal(msg)
	if err != nil {
		t.Fatalf("erro ao serializar: %v", err)
	}

	var decoded Message
	if err := json.Unmarshal(body, &decoded); err != nil {
		t.Fatalf("JSON inválido gerado: %v", err)
	}

	if decoded.To != "+5511888888888" {
		t.Fatalf("campo 'to' foi sequestrado pelo texto do usuário: %q", decoded.To)
	}
	if decoded.Text != malicious {
		t.Fatalf("texto não preservado corretamente: %q", decoded.Text)
	}
}

func TestNewMessage_UniqueID(t *testing.T) {
	a := NewMessage("produtor-go", "+5511888888888", "oi")
	b := NewMessage("produtor-go", "+5511888888888", "oi")

	if a.ID == "" {
		t.Fatal("messageId não deveria ser vazio")
	}
	if a.ID == b.ID {
		t.Fatal("duas mensagens diferentes geraram o mesmo messageId")
	}
}

func TestNewMessage_UnicodeAndEmoji(t *testing.T) {
	msg := NewMessage("produtor-go", "+5511888888888", "olá 👋 \"citação\" \\barra")

	body, err := json.Marshal(msg)
	if err != nil {
		t.Fatalf("erro ao serializar: %v", err)
	}

	var decoded Message
	if err := json.Unmarshal(body, &decoded); err != nil {
		t.Fatalf("JSON inválido gerado: %v", err)
	}
	if decoded.Text != msg.Text {
		t.Fatalf("texto com unicode/emoji não preservado: got %q want %q", decoded.Text, msg.Text)
	}
}
