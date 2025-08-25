package sqs

import (
	"log"
	"os"

	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/aws/session"
	sqs "github.com/aws/aws-sdk-go/service/sqs"
)

func SendMessage(message string) {

	sess, err := session.NewSession(&aws.Config{
		Region:   aws.String(os.Getenv("AWS_REGION")),
		Endpoint: aws.String("http://localhost:4566"),
	})
	if err != nil {
		log.Fatalf("Erro ao criar sessão: %v", err)
	}

	svc := sqs.New(sess)

	result, err := svc.SendMessage(&sqs.SendMessageInput{
		MessageBody: aws.String(message),
		QueueUrl:    aws.String(os.Getenv("SQS_URL")),
	})
	if err != nil {
		log.Fatalf("Erro ao enviar mensagem: %v", err)
	}

	log.Printf("Mensagem enviada com sucesso: %v", result)

}
