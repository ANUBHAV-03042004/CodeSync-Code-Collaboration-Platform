package com.notificationservice.codesync.config;

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
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class NotificationRabbitConfig {

    public static final String NOTIFICATION_EXCHANGE      = "codesync.notification.exchange";
    public static final String NOTIFICATION_EMAIL_QUEUE   = "codesync.notification.email.queue";
    public static final String NOTIFICATION_INAPP_QUEUE   = "codesync.notification.inapp.queue";
    public static final String NOTIFICATION_DLQ           = "codesync.notification.dlq";
    public static final String DEAD_LETTER_EXCHANGE       = "codesync.dlx";
    public static final String NOTIFICATION_EMAIL_KEY     = "notification.email";
    public static final String NOTIFICATION_INAPP_KEY     = "notification.inapp";

    @Value("${spring.rabbitmq.host:localhost}")
    private String host;

    @Value("${spring.rabbitmq.port:5671}")
    private int port;

    @Value("${spring.rabbitmq.username:guest}")
    private String username;

    @Value("${spring.rabbitmq.password:guest}")
    private String password;

    @Value("${spring.rabbitmq.virtual-host:mrlnqfcg}")
    private String virtualHost;

    @Value("${spring.rabbitmq.ssl.enabled:false}")
    private boolean sslEnabled;

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

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        // FIX: prevent RabbitAdmin from declaring queues/exchanges on startup.
        // Without this, a slow TLS handshake crashes the context before
        // the readiness probe passes, causing Eureka to mark the service DOWN.
        admin.setAutoStartup(false);
        return admin;
    }

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
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE).durable(true).build();
    }

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
        return QueueBuilder.durable(NOTIFICATION_DLQ).build();
    }

    @Bean
    public Binding notificationEmailBinding(Queue notificationEmailQueue,
                                             TopicExchange notificationExchange) {
        return BindingBuilder.bind(notificationEmailQueue)
                .to(notificationExchange).with(NOTIFICATION_EMAIL_KEY);
    }

    @Bean
    public Binding notificationInAppBinding(Queue notificationInAppQueue,
                                             TopicExchange notificationExchange) {
        return BindingBuilder.bind(notificationInAppQueue)
                .to(notificationExchange).with(NOTIFICATION_INAPP_KEY);
    }

    @Bean
    public Binding notificationDlqBinding(Queue notificationDeadLetterQueue,
                                           DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(notificationDeadLetterQueue)
                .to(deadLetterExchange).with("notification.dead");
    }

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
        // FIX: do not start consumers on context init — prevents Eureka DOWN
        // caused by slow TLS handshake crashing before readiness probe passes.
        factory.setAutoStartup(false);
        return factory;
    }
}
