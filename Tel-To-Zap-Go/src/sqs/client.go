package sqs

import (
	"context"
	"encoding/json"
	"fmt"
	"log"

	"github.com/aws/aws-sdk-go-v2/aws"
	awsconfig "github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/credentials"
	"github.com/aws/aws-sdk-go-v2/service/sqs"
	"github.com/aws/aws-sdk-go-v2/service/sqs/types"
)

// GatewayOptions configura a conexão com o SQS. Endpoint é opcional: vazio
// faz o SDK resolver o endpoint real da AWS via cadeia de configuração
// padrão (permitindo IAM Role em produção); setado (ex.: LocalStack)
// sobrescreve o endpoint e usa credenciais estáticas — só deve ser usado
// em desenvolvimento local.
type GatewayOptions struct {
	Region      string
	Endpoint    string
	AccessKeyID string
	SecretKey   string
}

// Gateway mantém um único client SQS (AWS SDK v2) reutilizável para todas
// as filas usadas pelo serviço, evitando recriar sessão/cliente a cada
// chamada.
type Gateway struct {
	api *sqs.Client
}

// NewGateway inicializa o client SQS uma única vez por processo.
func NewGateway(ctx context.Context, opts GatewayOptions) (*Gateway, error) {
	var cfgOpts []func(*awsconfig.LoadOptions) error
	cfgOpts = append(cfgOpts, awsconfig.WithRegion(opts.Region))

	if opts.Endpoint != "" {
		cfgOpts = append(cfgOpts, awsconfig.WithCredentialsProvider(
			credentials.NewStaticCredentialsProvider(opts.AccessKeyID, opts.SecretKey, ""),
		))
	}

	cfg, err := awsconfig.LoadDefaultConfig(ctx, cfgOpts...)
	if err != nil {
		return nil, fmt.Errorf("erro ao carregar configuração AWS: %w", err)
	}

	api := sqs.NewFromConfig(cfg, func(o *sqs.Options) {
		if opts.Endpoint != "" {
			o.BaseEndpoint = aws.String(opts.Endpoint)
		}
	})

	return &Gateway{api: api}, nil
}

// Queue retorna um client dedicado a uma fila específica, reaproveitando
// o client SQS subjacente.
func (g *Gateway) Queue(url string) *Client {
	return &Client{api: g.api, queueURL: url}
}

// Client opera sobre uma fila SQS específica.
type Client struct {
	api      *sqs.Client
	queueURL string
}

// SendMessage serializa a mensagem em JSON (nunca por concatenação de
// string) e publica na fila configurada. Retorna erro em vez de derrubar
// o processo, para que uma falha pontual não mate o bot inteiro.
func (c *Client) SendMessage(ctx context.Context, msg Message) error {
	body, err := json.Marshal(msg)
	if err != nil {
		return fmt.Errorf("erro ao serializar mensagem: %w", err)
	}

	out, err := c.api.SendMessage(ctx, &sqs.SendMessageInput{
		MessageBody: aws.String(string(body)),
		QueueUrl:    aws.String(c.queueURL),
	})
	if err != nil {
		return fmt.Errorf("erro ao enviar mensagem para o SQS: %w", err)
	}

	log.Printf("Mensagem enviada com sucesso (id=%s, sqsMessageId=%s)", msg.ID, aws.ToString(out.MessageId))
	return nil
}

// ReceiveMessages busca até maxMessages mensagens, aguardando até
// waitTimeSeconds por long polling.
func (c *Client) ReceiveMessages(ctx context.Context, maxMessages int32, waitTimeSeconds int32) ([]types.Message, error) {
	out, err := c.api.ReceiveMessage(ctx, &sqs.ReceiveMessageInput{
		QueueUrl:            aws.String(c.queueURL),
		MaxNumberOfMessages: maxMessages,
		WaitTimeSeconds:     waitTimeSeconds,
	})
	if err != nil {
		return nil, fmt.Errorf("erro ao receber mensagens do SQS: %w", err)
	}
	return out.Messages, nil
}

// DeleteMessage confirma (ack) o processamento de uma mensagem recebida.
func (c *Client) DeleteMessage(ctx context.Context, receiptHandle string) error {
	_, err := c.api.DeleteMessage(ctx, &sqs.DeleteMessageInput{
		QueueUrl:      aws.String(c.queueURL),
		ReceiptHandle: aws.String(receiptHandle),
	})
	if err != nil {
		return fmt.Errorf("erro ao confirmar mensagem no SQS: %w", err)
	}
	return nil
}
