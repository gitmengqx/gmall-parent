package com.atguigu.gmall.common.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.entity.GmallCorrelationData;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class RabbitService {
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RedisTemplate redisTemplate;

    //过期时间：分钟
    public static final int OBJECT_TIMEOUT = 10;

    /**
     *  发送消息
     * @param exchange 交换机
     * @param routingKey 路由键
     * @param message 消息
     */
    public boolean sendMessage(String exchange, String routingKey, Object message) {
        // convertAndSend 发送消息
//        rabbitTemplate.convertAndSend(exchange, routingKey, message);
        GmallCorrelationData correlationData = new GmallCorrelationData();
        String correlationId = UUID.randomUUID().toString();
        correlationData.setId(correlationId);
        correlationData.setMessage(message);
        correlationData.setExchange(exchange);
        correlationData.setRoutingKey(routingKey);
        // 通过缓存将发送的消息队列存储起来
        redisTemplate.opsForValue().set(correlationId, JSON.toJSONString(correlationData), OBJECT_TIMEOUT, TimeUnit.MINUTES);
        //
        rabbitTemplate.convertAndSend(exchange,routingKey,message,correlationData);

        return true;
    }

    public boolean sendDelayMessage(String exchange, String routingKey, Object message, int delayTime){
        GmallCorrelationData correlationData = new GmallCorrelationData();
        String correlationId = UUID.randomUUID().toString();
        correlationData.setId(correlationId);
        correlationData.setMessage(message);
        correlationData.setExchange(exchange);
        correlationData.setRoutingKey(routingKey);
        correlationData.setDelay(true);
        correlationData.setDelayTime(delayTime);

        // 将数据放到缓存中
        redisTemplate.opsForValue().set(correlationId, JSON.toJSONString(correlationData), OBJECT_TIMEOUT, TimeUnit.MINUTES);

        // 发送数据
        rabbitTemplate.convertAndSend(exchange, routingKey, message, message1 -> {
            message1.getMessageProperties().setDelay(delayTime*1000);
            return message1;
        },correlationData);

        return true;
    }
}
