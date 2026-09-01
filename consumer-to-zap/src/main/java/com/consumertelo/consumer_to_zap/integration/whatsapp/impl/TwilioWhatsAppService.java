package com.consumertelo.consumer_to_zap.integration.whatsapp.impl;

import com.consumertelo.consumer_to_zap.integration.whatsapp.PermanentWhatsAppException;
import com.consumertelo.consumer_to_zap.integration.whatsapp.TransientWhatsAppException;
import com.consumertelo.consumer_to_zap.integration.whatsapp.WhatsAppService;
import com.twilio.Twilio;
import com.twilio.exception.ApiConnectionException;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;

@Service
public class TwilioWhatsAppService implements WhatsAppService {
    private static final Logger log = LoggerFactory.getLogger(TwilioWhatsAppService.class);

    @Value("${twilio.account.sid}")
    private String accountSid;

    @Value("${twilio.auth.token}")
    private String authToken;

    @Value("${twilio.whatsapp.number}")
    private String twilioWhatsappNumber;

    /** P1.5: inicializa o SDK Twilio uma única vez, na subida do contexto. */
    @PostConstruct
    void init() {
        Twilio.init(accountSid, authToken);
    }

    @Override
    @Retry(name = "whatsapp")
    @CircuitBreaker(name = "whatsapp")
    public String sendMessage(String to, String body, String mediaUrl, URI statusCallbackUrl) {
        try {
            MessageCreator creator = Message.creator(
                    new PhoneNumber(to),
                    new PhoneNumber(twilioWhatsappNumber),
                    body == null ? "" : body
            );

            if (mediaUrl != null && !mediaUrl.isBlank()) {
                creator.setMediaUrl(List.of(URI.create(mediaUrl)));
            }
            if (statusCallbackUrl != null) {
                creator.setStatusCallback(statusCallbackUrl);
            }

            Message message = creator.create();
            log.info("Mensagem enviada para: {} (sid={})", to, message.getSid());
            return message.getSid();
        } catch (ApiConnectionException e) {
            // Falha de rede ao falar com a Twilio — vale tentar de novo.
            throw new TransientWhatsAppException("Falha de conexão com a Twilio", e);
        } catch (ApiException e) {
            if (e.getStatusCode() >= 500) {
                throw new TransientWhatsAppException("Erro do lado da Twilio (status " + e.getStatusCode() + ")", e);
            }
            // 4xx: número inválido, mensagem rejeitada etc. — não adianta repetir.
            throw new PermanentWhatsAppException("Twilio rejeitou a mensagem (status " + e.getStatusCode() + ")", e);
        } catch (RuntimeException e) {
            // Falha não classificada: tratamos como transiente por segurança,
            // para não descartar silenciosamente uma mensagem por um bug
            // desconhecido — o limite de tentativas do SQS/DLQ (P0.4) evita
            // loop infinito.
            throw new TransientWhatsAppException("Falha inesperada ao enviar WhatsApp", e);
        }
    }
}
