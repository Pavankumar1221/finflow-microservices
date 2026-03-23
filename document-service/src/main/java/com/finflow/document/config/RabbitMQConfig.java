package com.finflow.document.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Listen to Application Service events
    public static final String APP_SUBMITTED_QUEUE   = "application.submitted.queue";

    // Publish documents verified event
    public static final String DOC_EXCHANGE          = "document.exchange";
    public static final String DOC_VERIFIED_QUEUE    = "documents.verified.queue";
    public static final String DOC_VERIFIED_ROUTING  = "documents.verified";

    @Bean
    public Queue applicationSubmittedQueue() {
        return new Queue(APP_SUBMITTED_QUEUE, true);
    }

    @Bean
    public DirectExchange documentExchange() {
        return new DirectExchange(DOC_EXCHANGE);
    }

    @Bean
    public Queue documentsVerifiedQueue() {
        return new Queue(DOC_VERIFIED_QUEUE, true);
    }

    @Bean
    public Binding documentsVerifiedBinding() {
        return BindingBuilder.bind(documentsVerifiedQueue())
                .to(documentExchange())
                .with(DOC_VERIFIED_ROUTING);
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
