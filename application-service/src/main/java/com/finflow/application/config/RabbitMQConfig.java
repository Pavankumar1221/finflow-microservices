package com.finflow.application.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE  = "application.exchange";
    public static final String QUEUE     = "application.submitted.queue";
    public static final String ROUTING   = "application.submitted";

    public static final String DECISION_EXCHANGE  = "application.decision.exchange";
    public static final String DECISION_QUEUE     = "application.decision.queue";
    public static final String DECISION_ROUTING   = "application.decision";

    @Bean
    public DirectExchange applicationExchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    public DirectExchange decisionExchange() {
        return new DirectExchange(DECISION_EXCHANGE);
    }

    @Bean
    public Queue decisionQueue() {
        return new Queue(DECISION_QUEUE, true);
    }

    @Bean
    public Binding decisionBinding() {
        return BindingBuilder.bind(decisionQueue())
                .to(decisionExchange())
                .with(DECISION_ROUTING);
    }

    @Bean
    public Queue applicationSubmittedQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public Binding applicationBinding() {
        return BindingBuilder.bind(applicationSubmittedQueue())
                .to(applicationExchange())
                .with(ROUTING);
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
