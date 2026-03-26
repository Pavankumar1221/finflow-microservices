package com.finflow.application.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RabbitMQConfigTest {

    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    void applicationExchange_ShouldReturnDirectExchange() {
        DirectExchange exchange = config.applicationExchange();
        assertNotNull(exchange);
        assertEquals(RabbitMQConfig.EXCHANGE, exchange.getName());
    }

    @Test
    void decisionExchange_ShouldReturnDirectExchange() {
        DirectExchange exchange = config.decisionExchange();
        assertNotNull(exchange);
        assertEquals(RabbitMQConfig.DECISION_EXCHANGE, exchange.getName());
    }

    @Test
    void applicationSubmittedQueue_ShouldReturnQueue() {
        Queue queue = config.applicationSubmittedQueue();
        assertNotNull(queue);
        assertEquals(RabbitMQConfig.QUEUE, queue.getName());
        assertTrue(queue.isDurable());
    }

    @Test
    void decisionQueue_ShouldReturnQueue() {
        Queue queue = config.decisionQueue();
        assertNotNull(queue);
        assertEquals(RabbitMQConfig.DECISION_QUEUE, queue.getName());
        assertTrue(queue.isDurable());
    }

    @Test
    void applicationBinding_ShouldReturnBinding() {
        Binding binding = config.applicationBinding();
        assertNotNull(binding);
        assertEquals(RabbitMQConfig.ROUTING, binding.getRoutingKey());
        assertEquals(RabbitMQConfig.QUEUE, binding.getDestination());
        assertEquals(RabbitMQConfig.EXCHANGE, binding.getExchange());
    }

    @Test
    void decisionBinding_ShouldReturnBinding() {
        Binding binding = config.decisionBinding();
        assertNotNull(binding);
        assertEquals(RabbitMQConfig.DECISION_ROUTING, binding.getRoutingKey());
        assertEquals(RabbitMQConfig.DECISION_QUEUE, binding.getDestination());
        assertEquals(RabbitMQConfig.DECISION_EXCHANGE, binding.getExchange());
    }

    @Test
    void jsonMessageConverter_ShouldReturnJackson() {
        MessageConverter converter = config.jsonMessageConverter();
        assertNotNull(converter);
        assertTrue(converter instanceof Jackson2JsonMessageConverter);
    }

    @Test
    void rabbitTemplate_ShouldBeConfigured() {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        RabbitTemplate template = config.rabbitTemplate(connectionFactory);
        assertNotNull(template);
        assertTrue(template.getMessageConverter() instanceof Jackson2JsonMessageConverter);
        assertEquals(connectionFactory, template.getConnectionFactory());
    }
}
