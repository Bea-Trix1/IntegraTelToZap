package com.consumertelo.consumer_to_zap.dto;

import lombok.Data;

/**
 * Espelha o contrato {@code sqs.StatusMessage} do producer Go — publicada
 * na fila de status (P3.3) para o bot notificar o usuário do Telegram.
 */
@Data
public class StatusMessageDTO {
    private Long chatId;
    private String messageId;
    private String status;
    private String errorCode;
}
