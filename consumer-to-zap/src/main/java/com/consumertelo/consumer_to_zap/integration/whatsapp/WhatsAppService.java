package com.consumertelo.consumer_to_zap.integration.whatsapp;

public interface WhatsAppService {
    void sendMessage(String to, String body);
}
