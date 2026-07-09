package kr.ac.kopo.ttd.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public DirectExchange ttdExchange(@Value("${app.rabbitmq.exchange}") String exchange) {
        return new DirectExchange(exchange);
    }

    @Bean
    public Queue gradingQueue(@Value("${app.rabbitmq.queue.grading}") String queue) {
        return QueueBuilder.durable(queue).build();
    }

    @Bean
    public Binding gradingBinding(
            Queue gradingQueue, DirectExchange ttdExchange,
            @Value("${app.rabbitmq.routing-key.grading}") String routingKey) {
        return BindingBuilder.bind(gradingQueue).to(ttdExchange).with(routingKey);
    }
}