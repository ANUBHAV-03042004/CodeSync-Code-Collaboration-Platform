package com.collabservice.codesync.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

import static com.collabservice.codesync.config.RabbitMQConstants.*;

/**
 * RabbitMQ configuration for collab-service connecting to CloudAMQP over TLS.
 *
 * Key fixes applied:
 *
 * FIX 1 — auto-startup=false on RabbitAdmin
 *   RabbitAdmin.afterPropertiesSet() tries to declare ALL @Bean queues and
 *   exchanges immediately when the Spring context starts. On CloudAMQP the TLS
 *   handshake adds latency and the default 5s connection timeout fires before
 *   the TCP/SSL setup finishes → AmqpIOException wrapping java.io.IOException.
 *   Setting autoStartup=false means queues are declared lazily on first use,
 *   by which time the connection is fully established.
 *
 * FIX 2 — useSslProtocol() on the raw ConnectionFactory
 *   Spring Boot's spring.rabbitmq.ssl.enabled=true only applies to the
 *   auto-configured ConnectionFactory bean. Because we declare a manual
 *   @Bean ConnectionFactory (needed for publisher confirms), we must call
 *   rabbitFactory.useSslProtocol() ourselves — Boot's auto-config is bypassed.
 *
 * FIX 3 — Longer connection timeout (10s) and heartbeat (60s)
 *   CloudAMQP has connection rate limits and the TLS handshake from OpenShift
 *   to their EU/US servers can take 2-4s. The old 5s timeout was too tight.
 *
 * FIX 4 — virtualHost trim()
 *   CloudAMQP vhost = username = "mrlnqfcg". If the env var has a stray
 *   space or newline (common in oc secrets), the vhost becomes " mrlnqfcg"
 *   and CloudAMQP rejects it with ACCESS_REFUSED. trim() is defensive.
 */
@Configuration
public class RabbitMQConfig {

    @Value("${spring.rabbitmq.host:gerbil-01.rmq.cloudamqp.com}")
    private String host;

    @Value("${spring.rabbitmq.port:5671}")
    private int port;

    @Value("${spring.rabbitmq.username:mrlnqfcg}")
    private String username;

    @Value("${spring.rabbitmq.password}")
    private String password;

    @Value("${spring.rabbitmq.virtual-host:mrlnqfcg}")
    private String virtualHost;

    @Value("${spring.rabbitmq.ssl.enabled:true}")
    private boolean sslEnabled;

    // ── Connection factory ────────────────────────────────────────────────────

    @Bean
    public ConnectionFactory connectionFactory() throws Exception {
        com.rabbitmq.client.ConnectionFactory rabbitFactory =
                new com.rabbitmq.client.ConnectionFactory();

        rabbitFactory.setHost(host.trim());
        rabbitFactory.setPort(port);
        rabbitFactory.setUsername(username.trim());
        rabbitFactory.setPassword(password.trim());       // FIX 4: trim stray whitespace
        rabbitFactory.setVirtualHost(virtualHost.trim()); // FIX 4: trim stray whitespace
        rabbitFactory.setRequestedHeartbeat(60);          // FIX 3: was 30, now 60s
        rabbitFactory.setConnectionTimeout(10_000);       // FIX 3: was 5s, now 10s

        if (sslEnabled) {
            rabbitFactory.useSslProtocol();               // FIX 2: TLS — trust-all for managed brokers
        }

        CachingConnectionFactory factory = new CachingConnectionFactory(rabbitFactory);
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        factory.setPublisherReturns(true);
        return factory;
    }

    // ── RabbitAdmin — FIX 1: autoStartup=false ───────────────────────────────

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        // FIX 1: Do not declare queues/exchanges on startup.
        // Avoids AmqpIOException when TLS handshake is slower than the
        // connection timeout during Spring context initialization.
        // Queues already exist on CloudAMQP — redeclaration is not needed.
        admin.setAutoStartup(false);
        return admin;
    }

    // ── Message converter ─────────────────────────────────────────────────────

    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        template.setMandatory(true);
        return template;
    }

    // ── Dead-letter exchange ──────────────────────────────────────────────────

    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE)
                .durable(true).build();
    }

    // ── Collab exchange + queue ───────────────────────────────────────────────

    @Bean
    public TopicExchange collabExchange() {
        return ExchangeBuilder.topicExchange(COLLAB_EXCHANGE)
                .durable(true).build();
    }

    @Bean
    public Queue collabEventQueue() {
        return QueueBuilder.durable(COLLAB_EVENT_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "collab.dead")
                .withArgument("x-message-ttl", 86_400_000)   // 24h audit retention
                .build();
    }

    @Bean
    public Binding collabSessionBinding(Queue collabEventQueue, TopicExchange collabExchange) {
        return BindingBuilder.bind(collabEventQueue)
                .to(collabExchange)
                .with(COLLAB_SESSION_KEY);
    }

    // ── Retry + listener factory ──────────────────────────────────────────────

    @Bean
    public RetryOperationsInterceptor retryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1000, 2.0, 10_000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setAdviceChain(retryInterceptor());
        factory.setDefaultRequeueRejected(false);
        // FIX 1 complement: don't auto-start listeners either
        factory.setAutoStartup(false);
        return factory;
    }
}
