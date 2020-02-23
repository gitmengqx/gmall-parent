package com.atguigu.gmall.common.config;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.entity.GmallCorrelationData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;

/**
 * @Description 消息发送确认
 * <p>
 * ConfirmCallback  只确认消息是否正确到达 Exchange 中
 * ReturnCallback   消息没有正确到达队列时触发回调，如果正确到达队列不执行
 * <p>
 * 1. 如果消息没有到exchange,则confirm回调,ack=false
 * 2. 如果消息到达exchange,则confirm回调,ack=true
 * 3. exchange到queue成功,则不回调return
 * 4. exchange到queue失败,则回调return
 *
 */
@Component
@Slf4j
public class MQProducerAckConfig implements RabbitTemplate.ConfirmCallback, RabbitTemplate.ReturnCallback {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RedisTemplate redisTemplate;
    // 被@PostConstruct修饰的方法会在服务器加载Servlet的时候运行，并且只会被服务器执行一次。PostConstruct在构造函数之后执行，init（）方法之前执行。
    // Constructor(构造方法) -> @Autowired(依赖注入) -> @PostConstruct(注释的方法)
    @PostConstruct
    public void init() {
        rabbitTemplate.setConfirmCallback(this);            //指定 ConfirmCallback
        rabbitTemplate.setReturnCallback(this);             //指定 ReturnCallback
    }

    // 每次 mq 发送到交换机的时候回调该方法
    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        if (ack) {
            log.info("消息发送成功：" + JSON.toJSONString(correlationData));
        } else {
            log.info("消息发送失败：" + cause + " 数据：" + JSON.toJSONString(correlationData));
            // 重试方法
            this.addRetry(correlationData);
        }
    }

    // 重试发送消息
    private void addRetry(CorrelationData correlationData) {
        GmallCorrelationData gmallCorrelationData = (GmallCorrelationData) correlationData;
        // 获取到重试次数 默认初始化0
        int retryCount = gmallCorrelationData.getRetryCount();
        // 初始化值3
        if (retryCount >= MqConst.RETRY_COUNT){
            log.error("消息发送失败："+JSON.toJSONString(correlationData));
        } else {
            // 次数加 1
            retryCount+=1;
            gmallCorrelationData.setRetryCount(retryCount);
            // 放入list 缓存 mq:list, correlationData 的字符串，有个定时任务从list 中获取数据 重复。
            redisTemplate.opsForList().leftPush(MqConst.MQ_KEY_PREFIX,JSON.toJSONString(correlationData));
            // 更新次数 +1
            redisTemplate.opsForValue().set(gmallCorrelationData.getId(), JSON.toJSONString(correlationData));
        }
    }

    // 交换机到队列中的详细步骤！ 只是发送失败的时候调用！
    @Override
    public void returnedMessage(Message message, int replyCode, String replyText, String exchange, String routingKey) {
        // 反序列化对象输出
        System.out.println("消息主体: " + new String(message.getBody()));
        System.out.println("应答码: " + replyCode);
        System.out.println("描述：" + replyText);
        System.out.println("消息使用的交换器 exchange : " + exchange);
        System.out.println("消息使用的路由键 routing : " + routingKey);

        //====================================================================================================
        System.out.println("CorrelationId  -> " + message.getMessageProperties().getHeaders().get("spring_returned_message_correlation"));
        String correlationId = (String)message.getMessageProperties().getHeaders().get("spring_returned_message_correlation");

        if (!StringUtils.isEmpty(correlationId)){

            GmallCorrelationData correlationData = JSON.parseObject((String)redisTemplate.opsForValue().get(correlationId),GmallCorrelationData.class);
            this.addRetry(correlationData);

        }

    }

}

