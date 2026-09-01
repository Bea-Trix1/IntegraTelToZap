package com.consumertelo.consumer_to_zap.config;

import com.twilio.security.RequestValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * S2: valida a assinatura {@code X-Twilio-Signature} em webhooks da
 * Twilio antes de qualquer processamento de negócio, para garantir que a
 * requisição realmente veio da Twilio (e não foi forjada por terceiros).
 * Aplica-se apenas às rotas {@code /webhooks/twilio/**}.
 */
@Component
public class TwilioWebhookValidationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TwilioWebhookValidationFilter.class);
    private static final String SIGNATURE_HEADER = "X-Twilio-Signature";

    private final RequestValidator requestValidator;

    public TwilioWebhookValidationFilter(@Value("${twilio.auth.token}") String authToken) {
        this.requestValidator = new RequestValidator(authToken);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/webhooks/twilio/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String signature = request.getHeader(SIGNATURE_HEADER);
        String fullUrl = reconstructFullUrl(request);
        Map<String, String> bodyParams = extractBodyParams(request);

        if (signature == null || !requestValidator.validate(fullUrl, bodyParams, signature)) {
            log.warn("Assinatura Twilio inválida ou ausente para {}", request.getRequestURI());
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Assinatura Twilio inválida");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String reconstructFullUrl(HttpServletRequest request) {
        StringBuilder url = new StringBuilder(request.getRequestURL());
        if (request.getQueryString() != null) {
            url.append('?').append(request.getQueryString());
        }
        return url.toString();
    }

    /** Params do corpo do POST, excluindo os que vieram pela query string da URL. */
    private Map<String, String> extractBodyParams(HttpServletRequest request) {
        Set<String> queryParamNames = new HashSet<>();
        if (request.getQueryString() != null) {
            for (String pair : request.getQueryString().split("&")) {
                queryParamNames.add(pair.split("=")[0]);
            }
        }

        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (!queryParamNames.contains(key) && values.length > 0) {
                params.put(key, values[0]);
            }
        });
        return params;
    }
}
