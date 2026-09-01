package com.consumertelo.consumer_to_zap.integration.whatsapp;

import java.net.URI;

public interface WhatsAppService {

    /**
     * Envia uma mensagem de WhatsApp, opcionalmente com mídia (P3.4) e
     * status-callback (P3.3). Retorna o SID da mensagem na Twilio.
     *
     * @throws TransientWhatsAppException falha momentânea — vale reprocessar.
     * @throws PermanentWhatsAppException falha definitiva — não reprocessar.
     */
    String sendMessage(String to, String body, String mediaUrl, URI statusCallbackUrl);
}
