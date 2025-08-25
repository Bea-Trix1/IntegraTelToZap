package config

import (
	"errors"
	"os"

	"github.com/joho/godotenv"
)

type EnvConfig struct {
	TelegramToken string
	AWSRegion     string
	SQSURL        string
	SEUNUMERO     string
}

func LoadFromEnv() (*EnvConfig, error) {
	if err := godotenv.Load("../../.env"); err != nil {
		if err := godotenv.Load(); err != nil {
		}
	}

	cfg := &EnvConfig{
		TelegramToken: os.Getenv("TELEGRAM_TOKEN"),
		AWSRegion:     os.Getenv("AWS_REGION"),
		SQSURL:        os.Getenv("SQS_URL"),
		SEUNUMERO:     os.Getenv("SEU_NUMERO"),
	}

	if err := cfg.ValidateWithDefaults(); err != nil {
		return nil, err
	}

	return cfg, nil
}

func (e *EnvConfig) ValidateWithDefaults() error {
	if e.TelegramToken == "" {
		return errors.New("TELEGRAM_TOKEN não pode ser vazio")
	}
	if e.AWSRegion == "" {
		return errors.New("AWS_REGION não pode ser vazio")
	}
	if e.SQSURL == "" {
		return errors.New("SQS_URL não pode ser vazio")
	}
	if e.SEUNUMERO == "" {
		return errors.New("SEU_NUMERO não pode ser vazio")
	}
	return nil
}
