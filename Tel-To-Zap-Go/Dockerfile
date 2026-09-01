FROM golang:1.24 AS builder
WORKDIR /app

COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -o /tel-to-zap-go ./src/cmd

FROM gcr.io/distroless/static-debian12:nonroot
WORKDIR /app
COPY --from=builder /tel-to-zap-go /app/tel-to-zap-go

USER nonroot:nonroot
ENTRYPOINT ["/app/tel-to-zap-go"]
