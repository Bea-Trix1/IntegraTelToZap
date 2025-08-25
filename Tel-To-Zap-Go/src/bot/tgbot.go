package bot

import (
	"Tel-To-Zap-Go/src/sqs"
	"log"
	"os"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"
)

func StartBot() {
	bot, err := tgbotapi.NewBotAPI(os.Getenv("TELEGRAM_TOKEN"))
	if err != nil {
		panic(err)
	}

	bot.Debug = true

	log.Printf("Login autorizado no bot %s", bot.Self.UserName)

	u := tgbotapi.NewUpdate(0)
	u.Timeout = 60

	updates := bot.GetUpdatesChan(u)

	for update := range updates {

		if update.Message != nil {

			msg := tgbotapi.NewMessage(update.Message.Chat.ID, update.Message.Text)
			msg.ReplyToMessageID = update.Message.MessageID

			log.Print("Enviando mensagem para fila sqs")
			jsonMessage := `{"from":"produtor-go","to":"+55` + os.Getenv("SEU_NUMERO") + `","text":"` + update.Message.Text + `"}`

			sqs.SendMessage(jsonMessage)
		}
	}

}
