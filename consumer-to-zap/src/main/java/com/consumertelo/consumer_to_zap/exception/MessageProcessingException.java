package com.consumertelo.consumer_to_zap.exception;

/**
 * Sinaliza falha no processamento de uma mensagem da fila, distinguindo
 * explicitamente entre erro permanente (payload inválido — não adianta
 * reprocessar) e erro transiente (falha momentânea da Twilio — vale
 * reprocessar). Ver P0.3.
 */
public class MessageProcessingException extends RuntimeException {

    private final boolean retryable;

    public MessageProcessingException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public MessageProcessingException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    /** {@code true} se a mensagem deve voltar para a fila para nova tentativa. */
    public boolean isRetryable() {
        return retryable;
    }
}
