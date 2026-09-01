package store

import (
	"path/filepath"
	"testing"
)

func TestFileStore_SetAndGet(t *testing.T) {
	path := filepath.Join(t.TempDir(), "destinations.json")

	s, err := NewFileStore(path)
	if err != nil {
		t.Fatalf("erro ao criar store: %v", err)
	}

	if _, ok := s.Get(123); ok {
		t.Fatal("não deveria haver destino configurado ainda")
	}

	if err := s.Set(123, "+5511999999999"); err != nil {
		t.Fatalf("erro ao salvar destino: %v", err)
	}

	number, ok := s.Get(123)
	if !ok || number != "+5511999999999" {
		t.Fatalf("destino não persistido corretamente: %q, %v", number, ok)
	}
}

func TestFileStore_PersistsAcrossReload(t *testing.T) {
	path := filepath.Join(t.TempDir(), "destinations.json")

	s1, err := NewFileStore(path)
	if err != nil {
		t.Fatalf("erro ao criar store: %v", err)
	}
	if err := s1.Set(42, "+5511888888888"); err != nil {
		t.Fatalf("erro ao salvar: %v", err)
	}

	s2, err := NewFileStore(path)
	if err != nil {
		t.Fatalf("erro ao recarregar store: %v", err)
	}

	number, ok := s2.Get(42)
	if !ok || number != "+5511888888888" {
		t.Fatalf("destino não sobreviveu ao reload: %q, %v", number, ok)
	}
}
