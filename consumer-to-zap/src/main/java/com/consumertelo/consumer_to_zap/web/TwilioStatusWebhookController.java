package com.consumertelo.consumer_to_zap.web;

import com.consumertelo.consumer_to_zap.service.StatusNotificationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * P3.3: recebe o callback de status de entrega da Twilio
 * (sent/delivered/failed/read). Protegido por
 * {@link com.consumertelo.consumer_to_zap.config.TwilioWebhookValidationFilter}
 * (S2) — só requisições com assinatura Twilio válida chegam até aqui.
 */
@RestController
public class TwilioStatusWebhookController {

    private final StatusNotificationService statusNotificationService;

    public TwilioStatusWebhookController(StatusNotificationService statusNotificationService) {
        this.statusNotificationService = statusNotificationService;
    }

    @PostMapping(value = "/webhooks/twilio/status", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> receiveStatus(
            @RequestParam(required = false) Long chatId,
            @RequestParam(required = false) String messageId,
            @RequestParam("MessageStatus") String messageStatus,
            @RequestParam(value = "ErrorCode", required = false) String errorCode
    ) {
        statusNotificationService.notify(chatId, messageId, messageStatus, errorCode);
        return ResponseEntity.ok().build();
    }
}
