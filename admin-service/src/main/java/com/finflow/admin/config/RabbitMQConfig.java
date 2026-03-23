package com.finflow.admin.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Listen to document verified events
    public static final String DOC_VERIFIED_QUEUE   = "documents.verified.queue";

    // Publish decision made event
    public static final String DECISION_EXCHANGE    = "application.decision.exchange";
    public static final String DECISION_QUEUE       = "application.decision.queue";
    public static final String DECISION_ROUTING     = "application.decision";

    @Bean
    public Queue documentsVerifiedQueue() {
        return new Queue(DOC_VERIFIED_QUEUE, true);
    }

    @Bean
    public DirectExchange decisionExchange() {
        return new DirectExchange(DECISION_EXCHANGE);
    }

    @Bean
    public Queue decisionMadeQueue() {
        return new Queue(DECISION_QUEUE, true);
    }

    @Bean
    public Binding decisionBinding() {
        return BindingBuilder.bind(decisionMadeQueue())
                .to(decisionExchange())
                .with(DECISION_ROUTING);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
