package main

import (
	"Tel-To-Zap-Go/src/bot"
	"Tel-To-Zap-Go/src/infra/config"
	"log"
)

func main() {
	_, err := config.LoadFromEnv()
	if err != nil {
		log.Fatalf("Erro ao carregar .env config: %v", err)
	}

	log.Printf("Bot inicializado!")

	go bot.StartBot()
	select {}
}
