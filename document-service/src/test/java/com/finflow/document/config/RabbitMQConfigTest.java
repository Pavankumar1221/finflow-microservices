package com.finflow.document.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RabbitMQConfigTest {

    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    void applicationSubmittedQueue_ShouldReturnQueue() {
        Queue queue = config.applicationSubmittedQueue();
        assertEquals(RabbitMQConfig.APP_SUBMITTED_QUEUE, queue.getName());
    }

    @Test
    void documentExchange_ShouldReturnDirectExchange() {
        DirectExchange exchange = config.documentExchange();
        assertEquals(RabbitMQConfig.DOC_EXCHANGE, exchange.getName());
    }

    @Test
    void documentsVerifiedQueue_ShouldReturnQueue() {
        Queue queue = config.documentsVerifiedQueue();
        assertEquals(RabbitMQConfig.DOC_VERIFIED_QUEUE, queue.getName());
    }

    @Test
    void documentsVerifiedBinding_ShouldReturnBinding() {
        Binding binding = config.documentsVerifiedBinding();
        assertEquals(RabbitMQConfig.DOC_VERIFIED_ROUTING, binding.getRoutingKey());
        assertEquals(Binding.DestinationType.QUEUE, binding.getDestinationType());
        assertEquals(RabbitMQConfig.DOC_VERIFIED_QUEUE, binding.getDestination());
        assertEquals(RabbitMQConfig.DOC_EXCHANGE, binding.getExchange());
    }

    @Test
    void jsonMessageConverter_ShouldReturnJackson() {
        MessageConverter converter = config.jsonMessageConverter();
        assertNotNull(converter);
        assertTrue(converter instanceof Jackson2JsonMessageConverter);
    }
}
