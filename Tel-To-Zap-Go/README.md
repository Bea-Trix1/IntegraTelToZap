# Tel-To-Zap-Go

Tel-To-Zap-Go é uma aplicação que integra um bot do Telegram com uma fila SQS da AWS. O bot recebe mensagens do Telegram e as envia para uma fila SQS(Simulada com LocalStack), permitindo o processamento assíncrono das mensagens. 

O Consumer dessas mensagens vai ser outro serviço Tel-To-Zap-Java, onde ele irá pegar essas mensagens e enviar para um grupo ou mensagem privada no Whatsapp.

# Token do Bot

Para integrar com o bot do telegram, você deve utilizar o @BotFather https://t.me/BotFather, para criar um novo bot. Pegue o TOKEN gerado, e passe em suas variaveis.

# SQS COM LOCALSTACK

### Passos para Configuração e Execução

1. **Inicie o LocalStack**: Certifique-se de que o LocalStack está em execução. Você pode usar o Docker. Execute o seguinte comando no terminal:

    ```sh
    docker run --rm -it -d -p 4566:4566 localstack/localstack start    
    ```

2. **Crie a fila SQS no LocalStack**: Use o AWS CLI para criar uma fila SQS no LocalStack. Execute o seguinte comando no terminal:

    ```sh
    aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name tel-bot-queue
    ```

3. **Arquivo `.env`**: Crie um arquivo `.env` na raiz do projeto com o seguinte conteúdo:

    ```plaintext
    TELEGRAM_TOKEN=seu_token_aqui
    AWS_REGION=us-east-1
    SQS_URL=http://localhost:4566/000000000000/tel-bot-queue
    ```


### Verificação das Mensagens na Fila SQS

Para verificar as mensagens que foram enviadas para a fila SQS, você pode usar o comando `aws sqs receive-message`:

```sh
aws sqs receive-message --endpoint-url http://localhost:4566 --queue-url http://localhost:4566/000000000000/tel-bot-queue --attribute-names All --message-attribute-names All