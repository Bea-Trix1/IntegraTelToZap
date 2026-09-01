package com.consumertelo.consumer_to_zap.config;

import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.net.URI;
import java.time.Duration;

/**
 * Configuração do client SQS. Separada por profile (P2.3/S4): o profile
 * "local" usa credenciais estáticas e aponta para o LocalStack; o profile
 * "prod" nunca usa credenciais estáticas nem endpoint override — resolve
 * a identidade via IAM Role (DefaultCredentialsProvider), evitando que
 * configuração "de mentira" vá parar em produção por engano.
 */
@Configuration
public class SqsConfig {

    @Value("${aws.region}")
    private String region;

    @Bean
    @Profile("local")
    public SqsAsyncClient localSqsAsyncClient(
            @Value("${aws.sqs.endpoint-override}") String endpoint) {
        return SqsAsyncClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .build();
    }

    @Bean
    @Profile("prod")
    public SqsAsyncClient prodSqsAsyncClient() {
        return SqsAsyncClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public SqsMessageListenerContainerFactory<Object> defaultSqsListenerContainerFactory(SqsAsyncClient sqsAsyncClient) {
        return SqsMessageListenerContainerFactory.builder()
                .sqsAsyncClient(sqsAsyncClient)
                .configure(options -> options
                        .maxConcurrentMessages(1)
                        .maxMessagesPerPoll(1)
                        .pollTimeout(Duration.ofSeconds(20))
                )
                .build();
    }

    /** Usado por {@code StatusNotificationService} (P3.3) para publicar na fila de status. */
    @Bean
    public SqsTemplate sqsTemplate(SqsAsyncClient sqsAsyncClient) {
        return SqsTemplate.builder()
                .sqsAsyncClient(sqsAsyncClient)
                .build();
    }
}
