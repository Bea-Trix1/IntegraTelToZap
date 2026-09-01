package com.consumertelo.consumer_to_zap.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * S1/S2/S6: nega tudo por padrão; libera explicitamente apenas o mínimo
 * necessário (health check simplificado e os webhooks da Twilio, que têm
 * sua própria validação de assinatura em vez de API Key).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ApiKeyAuthFilter apiKeyAuthFilter,
            TwilioWebhookValidationFilter twilioWebhookValidationFilter
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, authException) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        // S6: health público, mas sem detalhes internos (show-details: never).
                        .requestMatchers("/actuator/health").permitAll()
                        // S2: webhooks da Twilio são autenticados por assinatura, não por API Key.
                        .requestMatchers("/webhooks/twilio/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(twilioWebhookValidationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
