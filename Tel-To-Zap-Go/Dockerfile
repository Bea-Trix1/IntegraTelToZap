FROM golang:1.24 AS builder
WORKDIR /app

COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -o /tel-to-zap-go ./src/cmd

FROM gcr.io/distroless/static-debian12:nonroot
# /home/nonroot é gravável pelo usuário nonroot (uid 65532) por padrão na
# imagem distroless — necessário porque o bot persiste destinations.json
# (P3.2) no diretório de trabalho. /app, se usado como WORKDIR, seria
# criado como root e o processo não teria permissão de escrita nele.
WORKDIR /home/nonroot
COPY --from=builder --chown=nonroot:nonroot /tel-to-zap-go /home/nonroot/tel-to-zap-go

USER nonroot:nonroot
ENTRYPOINT ["/home/nonroot/tel-to-zap-go"]
