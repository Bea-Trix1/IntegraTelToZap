package com.consumertelo.consumer_to_zap.integration.whatsapp.impl;

import com.consumertelo.consumer_to_zap.integration.whatsapp.WhatsAppService;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TwilioWhatsAppService implements WhatsAppService {
    private static final Logger log = LoggerFactory.getLogger(TwilioWhatsAppService.class);
    @Value("${twilio.account.sid}")
    private String accountSid;

    @Value("${twilio.auth.token}")
    private String authToken;

    @Value("${twilio.whatsapp.number}")
    private String twilioWhatsappNumber;

    @Override
    public void sendMessage(String to, String body) {
        Twilio.init(accountSid, authToken);

        Message.creator(
                new PhoneNumber(to),
                new PhoneNumber(twilioWhatsappNumber),
                body
        ).create();

        log.info("Mensagem enviada para: {}", to);
    }
}
