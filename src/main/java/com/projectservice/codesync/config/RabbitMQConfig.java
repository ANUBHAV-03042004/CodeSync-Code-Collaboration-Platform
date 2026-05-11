package com.projectservice.codesync.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    @Bean
    public TopicExchange collabExchange() {
        return new TopicExchange(RabbitMQConstants.COLLAB_EXCHANGE);
    }

    @Bean
    public Queue collabEventQueue() {
        return QueueBuilder.durable(RabbitMQConstants.COLLAB_EVENT_QUEUE).build();
    }

    @Bean
    public Binding collabEventBinding(Queue collabEventQueue, TopicExchange collabExchange) {
        return BindingBuilder.bind(collabEventQueue).to(collabExchange).with(RabbitMQConstants.COLLAB_SESSION_KEY);
    }
}
