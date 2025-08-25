package com.consumertelo.consumer_to_zap.service;

import com.consumertelo.consumer_to_zap.dto.MessageDTO;
import com.consumertelo.consumer_to_zap.integration.whatsapp.WhatsAppService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MessageProcessingService {
    private static final Logger log = LoggerFactory.getLogger(MessageProcessingService.class);
    private final WhatsAppService whatsAppService;
    private final ObjectMapper objectMapper;
    
    private final Set<String> processedMessages = ConcurrentHashMap.newKeySet();

    public MessageProcessingService(WhatsAppService whatsAppService, ObjectMapper objectMapper){
        this.whatsAppService = whatsAppService;
        this.objectMapper = objectMapper;
    }

    public void process(String jsonPayload){
        try{
            log.info("Iniciando processamento de mensagens ...");
            
            String messageHash = String.valueOf(jsonPayload.hashCode());
            
            if (processedMessages.contains(messageHash)) {
                log.warn("Mensagem duplicada, ignorando: {}", messageHash);
                return;
            }
            
            MessageDTO messageDTO = objectMapper.readValue(jsonPayload, MessageDTO.class);

            String numberWhatsApp = "whatsapp:" + messageDTO.getTo();
            whatsAppService.sendMessage(numberWhatsApp, messageDTO.getText());

            processedMessages.add(messageHash);
            
            log.info("Mensagem processada com sucesso: {}", jsonPayload);
        } catch (Exception e){
            log.error("Falha ao processar a mensagem: {} - Erro: {}", jsonPayload, e.getMessage(), e);
        }
    }
}
