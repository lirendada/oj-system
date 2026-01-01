package com.liren.problem.config;

import com.liren.common.core.constant.Constants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    @Bean
    public Queue judgeQueue() {
        return new Queue(Constants.JUDGE_QUEUE, true);
    }

    @Bean
    public DirectExchange judgeExchange() {
        return new DirectExchange(Constants.JUDGE_EXCHANGE, true, false);
    }

    @Bean
    public Binding binding() {
        return BindingBuilder.bind(judgeQueue()).to(judgeExchange()).with(Constants.JUDGE_ROUTING_KEY);
    }
}
