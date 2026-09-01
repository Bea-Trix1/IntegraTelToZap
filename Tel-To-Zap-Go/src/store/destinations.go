// Package store guarda o mapeamento chat_id do Telegram -> número de
// WhatsApp de destino (P3.2), permitindo que cada conversa configure seu
// próprio destinatário via comando /destino em vez de um único número
// fixo para todo o bot.
package store

import (
	"encoding/json"
	"os"
	"sync"
)

// DestinationStore é seguro para uso concorrente e persiste em um arquivo
// JSON local. Para múltiplas instâncias do bot em produção, o mesmo
// contrato (Get/Set) pode ser reimplementado sobre DynamoDB sem alterar
// os chamadores.
type DestinationStore struct {
	mu   sync.RWMutex
	path string
	data map[int64]string
}

// NewFileStore carrega (se existir) ou inicializa o arquivo de destinos.
func NewFileStore(path string) (*DestinationStore, error) {
	s := &DestinationStore{path: path, data: make(map[int64]string)}

	raw, err := os.ReadFile(path)
	if os.IsNotExist(err) {
		return s, nil
	}
	if err != nil {
		return nil, err
	}
	if len(raw) == 0 {
		return s, nil
	}
	if err := json.Unmarshal(raw, &s.data); err != nil {
		return nil, err
	}
	return s, nil
}

// Get retorna o número de WhatsApp configurado para o chat, se houver.
func (s *DestinationStore) Get(chatID int64) (string, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	number, ok := s.data[chatID]
	return number, ok
}

// Set associa um número de WhatsApp ao chat e persiste em disco.
func (s *DestinationStore) Set(chatID int64, number string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.data[chatID] = number
	return s.persistLocked()
}

func (s *DestinationStore) persistLocked() error {
	raw, err := json.MarshalIndent(s.data, "", "  ")
	if err != nil {
		return err
	}
	tmp := s.path + ".tmp"
	if err := os.WriteFile(tmp, raw, 0o600); err != nil {
		return err
	}
	return os.Rename(tmp, s.path)
}
