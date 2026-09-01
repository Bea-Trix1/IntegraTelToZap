package config

import "testing"

func TestValidate_MissingRequiredFields(t *testing.T) {
	cases := []struct {
		name string
		cfg  EnvConfig
	}{
		{"sem token telegram", EnvConfig{AWSRegion: "us-east-1", SQSURL: "http://x"}},
		{"sem região aws", EnvConfig{TelegramToken: "t", SQSURL: "http://x"}},
		{"sem url sqs", EnvConfig{TelegramToken: "t", AWSRegion: "us-east-1"}},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if err := tc.cfg.validate(); err == nil {
				t.Fatalf("esperava erro de validação para: %s", tc.name)
			}
		})
	}
}

func TestValidate_OK(t *testing.T) {
	cfg := EnvConfig{TelegramToken: "t", AWSRegion: "us-east-1", SQSURL: "http://x"}
	if err := cfg.validate(); err != nil {
		t.Fatalf("não esperava erro: %v", err)
	}
}

func TestParseChatIDs(t *testing.T) {
	ids, err := parseChatIDs(" 123, 456 ,789")
	if err != nil {
		t.Fatalf("erro inesperado: %v", err)
	}
	want := []int64{123, 456, 789}
	if len(ids) != len(want) {
		t.Fatalf("esperava %v, obteve %v", want, ids)
	}
	for i := range want {
		if ids[i] != want[i] {
			t.Fatalf("esperava %v, obteve %v", want, ids)
		}
	}
}

func TestParseChatIDs_Empty(t *testing.T) {
	ids, err := parseChatIDs("")
	if err != nil {
		t.Fatalf("erro inesperado: %v", err)
	}
	if ids != nil {
		t.Fatalf("esperava nil, obteve %v", ids)
	}
}

func TestParseChatIDs_Invalid(t *testing.T) {
	if _, err := parseChatIDs("abc"); err == nil {
		t.Fatal("esperava erro para chat_id inválido")
	}
}
