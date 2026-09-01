package com.consumertelo.consumer_to_zap.integration.whatsapp;

/** Falha momentânea (timeout, 5xx, rate limit) — vale a pena repetir. */
public class TransientWhatsAppException extends RuntimeException {
    public TransientWhatsAppException(String message, Throwable cause) {
        super(message, cause);
    }
}
