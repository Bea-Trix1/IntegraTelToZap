package com.consumertelo.consumer_to_zap.integration.whatsapp;

/** Falha definitiva (número inválido, mensagem rejeitada) — não adianta repetir. */
public class PermanentWhatsAppException extends RuntimeException {
    public PermanentWhatsAppException(String message, Throwable cause) {
        super(message, cause);
    }
}
