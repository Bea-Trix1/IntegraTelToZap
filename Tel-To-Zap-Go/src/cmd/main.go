package main

import (
	"context"
	"log"
	"os/signal"
	"sync"
	"syscall"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"

	"Tel-To-Zap-Go/src/bot"
	"Tel-To-Zap-Go/src/infra/config"
	appsqs "Tel-To-Zap-Go/src/sqs"
	"Tel-To-Zap-Go/src/statuspoller"
	"Tel-To-Zap-Go/src/store"
)

func main() {
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	cfg, err := config.LoadFromEnv()
	if err != nil {
		log.Fatalf("Erro ao carregar .env config: %v", err)
	}

	gateway, err := appsqs.NewGateway(ctx, appsqs.GatewayOptions{
		Region:      cfg.AWSRegion,
		Endpoint:    cfg.SQSEndpoint,
		AccessKeyID: "test",
		SecretKey:   "test",
	})
	if err != nil {
		log.Fatalf("Erro ao inicializar client SQS: %v", err)
	}

	destinations, err := store.NewFileStore(cfg.DestinationsFile)
	if err != nil {
		log.Fatalf("Erro ao carregar armazenamento de destinos: %v", err)
	}

	deps := bot.Deps{
		Cfg:          cfg,
		Queue:        gateway.Queue(cfg.SQSURL),
		Destinations: destinations,
	}

	log.Printf("Bot inicializado!")

	var wg sync.WaitGroup
	wg.Add(1)
	go func() {
		defer wg.Done()
		if err := bot.StartBot(ctx, deps); err != nil {
			log.Printf("Bot encerrado com erro: %v", err)
		}
	}()

	if cfg.StatusQueueURL != "" {
		telegramBot, err := tgbotapi.NewBotAPI(cfg.TelegramToken)
		if err != nil {
			log.Fatalf("Erro ao inicializar bot para poller de status: %v", err)
		}

		wg.Add(1)
		go func() {
			defer wg.Done()
			statuspoller.Run(ctx, telegramBot, gateway.Queue(cfg.StatusQueueURL))
		}()
	}

	<-ctx.Done()
	log.Print("Sinal de encerramento recebido, finalizando...")
	wg.Wait()
	log.Print("Encerrado.")
}
