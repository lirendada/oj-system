package com.liren.judge.config;

import com.liren.common.core.constant.Constants;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    /**
     * 声明队列，防止 Consumer 启动时队列不存在报错
     */
    @Bean("judgeQueue")
    public Queue judgeQueue() {
        return QueueBuilder.durable(Constants.JUDGE_QUEUE).build();
    }

    @Bean("judgeExchange")
    public DirectExchange judgeExchange() {
        return ExchangeBuilder.directExchange(Constants.JUDGE_EXCHANGE).build();
    }

    @Bean("judgeBinding")
    public Binding binding(@Qualifier("judgeQueue") Queue queue,
                           @Qualifier("judgeExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(Constants.JUDGE_ROUTING_KEY);
    }
}