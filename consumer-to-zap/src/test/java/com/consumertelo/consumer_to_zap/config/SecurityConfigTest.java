package com.consumertelo.consumer_to_zap.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S1/S6: garante que endpoints internos exigem o token de acesso e que o
 * health check simplificado continua público.
 */
@SpringBootTest(properties = {
        "twilio.auth.token=test-auth-token",
        "twilio.account.sid=ACtest",
        "app.security.api-key=test-api-key"
})
@AutoConfigureMockMvc
@AutoConfigureObservability
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void health_isPublic_withoutApiKey() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void prometheus_requiresApiKey() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void prometheus_rejectsWrongApiKey() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").header("X-API-Key", "chave-errada"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void prometheus_acceptsValidApiKey() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").header("X-API-Key", "test-api-key"))
                .andExpect(status().isOk());
    }

    @Test
    void twilioWebhook_rejectsRequestWithoutSignature() throws Exception {
        mockMvc.perform(post("/webhooks/twilio/status")
                        .param("MessageStatus", "delivered"))
                .andExpect(status().isForbidden());
    }

    @Test
    void twilioWebhook_rejectsForgedSignature() throws Exception {
        mockMvc.perform(post("/webhooks/twilio/status")
                        .param("MessageStatus", "delivered")
                        .header("X-Twilio-Signature", "assinatura-forjada"))
                .andExpect(status().isForbidden());
    }
}
