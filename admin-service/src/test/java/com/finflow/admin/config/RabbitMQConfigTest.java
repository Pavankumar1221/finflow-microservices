package com.finflow.admin.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

public class RabbitMQConfigTest {

    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    void jsonMessageConverter() {
        MessageConverter converter = config.jsonMessageConverter();
        assertNotNull(converter);
    }

    @Test
    void rabbitTemplate() {
        ConnectionFactory factory = mock(ConnectionFactory.class);
        assertNotNull(config.rabbitTemplate(factory));
    }

    @Test
    void queuesAndExchanges() {
        assertNotNull(config.documentsVerifiedQueue());
        assertNotNull(config.decisionExchange());
        assertNotNull(config.decisionMadeQueue());
        assertNotNull(config.decisionBinding());
    }
}
