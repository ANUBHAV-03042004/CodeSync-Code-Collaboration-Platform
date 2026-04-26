package com.executionservice.codesync.config;

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

import static com.executionservice.codesync.config.RabbitMQConstants.*;

@Configuration
public class RabbitMQConfig {

    @Value("${spring.rabbitmq.host:localhost}")
    private String host;

    @Value("${spring.rabbitmq.port:5672}")
    private int port;

    @Value("${spring.rabbitmq.username:guest}")
    private String username;

    @Value("${spring.rabbitmq.password:guest}")
    private String password;

    @Value("${spring.rabbitmq.virtual-host:/}")
    private String virtualHost;

    @Value("${spring.rabbitmq.ssl.enabled:false}")
    private boolean sslEnabled;

    // ── Connection ────────────────────────────────────────────────────────────

    @Bean
    public ConnectionFactory connectionFactory() throws Exception {
        CachingConnectionFactory factory;

        if (sslEnabled) {
            com.rabbitmq.client.ConnectionFactory rabbitFactory =
                    new com.rabbitmq.client.ConnectionFactory();
            rabbitFactory.setHost(host);
            rabbitFactory.setPort(port);
            rabbitFactory.setUsername(username);
            rabbitFactory.setPassword(password);
            rabbitFactory.setVirtualHost(virtualHost.trim());
            rabbitFactory.setRequestedHeartbeat(30);
            rabbitFactory.setConnectionTimeout(5000);
            rabbitFactory.useSslProtocol();
            factory = new CachingConnectionFactory(rabbitFactory);
        } else {
            factory = new CachingConnectionFactory();
            factory.setHost(host);
            factory.setPort(port);
            factory.setUsername(username);
            factory.setPassword(password);
            factory.setVirtualHost(virtualHost.trim());
            factory.setRequestedHeartBeat(30);
            factory.setConnectionTimeout(5000);
        }

        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        factory.setPublisherReturns(true);
        return factory;
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

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.setAutoStartup(false);
        return admin;
    }

    // ── Dead-letter exchange ──────────────────────────────────────────────────

    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    // ── Execution exchange + queues ───────────────────────────────────────────

    @Bean
    public DirectExchange executionExchange() {
        return ExchangeBuilder.directExchange(EXECUTION_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue executionQueue() {
        return QueueBuilder.durable(EXECUTION_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "execution.dead")
                .withArgument("x-message-ttl", 60_000)
                .withArgument("x-max-priority", 5)
                .build();
    }

    @Bean
    public Queue executionResultQueue() {
        return QueueBuilder.durable(EXECUTION_RESULT_QUEUE)
                .withArgument("x-message-ttl", 300_000)
                .build();
    }

    @Bean
    public Queue executionDeadLetterQueue() {
        return QueueBuilder.durable(EXECUTION_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding executionBinding(Queue executionQueue, DirectExchange executionExchange) {
        return BindingBuilder.bind(executionQueue).to(executionExchange).with(EXECUTION_ROUTING_KEY);
    }

    @Bean
    public Binding executionResultBinding(Queue executionResultQueue, DirectExchange executionExchange) {
        return BindingBuilder.bind(executionResultQueue).to(executionExchange).with(EXECUTION_RESULT_KEY);
    }

    @Bean
    public Binding executionDlqBinding(Queue executionDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(executionDeadLetterQueue).to(deadLetterExchange).with("execution.dead");
    }

    // ── Notification exchange + queues ────────────────────────────────────────

    @Bean
    public TopicExchange notificationExchange() {
        return ExchangeBuilder.topicExchange(NOTIFICATION_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue notificationEmailQueue() {
        return QueueBuilder.durable(NOTIFICATION_EMAIL_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "notification.dead")
                .build();
    }

    @Bean
    public Queue notificationInAppQueue() {
        return QueueBuilder.durable(NOTIFICATION_INAPP_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "notification.dead")
                .build();
    }

    @Bean
    public Queue notificationDeadLetterQueue() {
        return QueueBuilder.durable(NOTIFICATION_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding notificationEmailBinding(Queue notificationEmailQueue, TopicExchange notificationExchange) {
        return BindingBuilder.bind(notificationEmailQueue).to(notificationExchange).with(NOTIFICATION_EMAIL_KEY);
    }

    @Bean
    public Binding notificationInAppBinding(Queue notificationInAppQueue, TopicExchange notificationExchange) {
        return BindingBuilder.bind(notificationInAppQueue).to(notificationExchange).with(NOTIFICATION_INAPP_KEY);
    }

    @Bean
    public Binding notificationDlqBinding(Queue notificationDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(notificationDeadLetterQueue).to(deadLetterExchange).with("notification.dead");
    }

    // ── Collab exchange + queue ───────────────────────────────────────────────

    @Bean
    public TopicExchange collabExchange() {
        return ExchangeBuilder.topicExchange(COLLAB_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue collabEventQueue() {
        return QueueBuilder.durable(COLLAB_EVENT_QUEUE)
                .withArgument("x-message-ttl", 10_000)
                .build();
    }

    @Bean
    public Binding collabSessionBinding(Queue collabEventQueue, TopicExchange collabExchange) {
        return BindingBuilder.bind(collabEventQueue).to(collabExchange).with(COLLAB_SESSION_KEY);
    }

    // ── Listener container factory ────────────────────────────────────────────

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
        return factory;
    }
}
