package com.atguigu.gmall.order.receiver;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.model.enums.OrderStatus;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.order.service.OrderService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Map;

@Configuration
public class OrderReceiver {

    // 接收消息处理内容
    @Autowired
    private OrderService orderService;

    @RabbitListener(queues = MqConst.QUEUE_ORDER_CANCEL)
    public void orderCancel(Long orderId, Message message, Channel channel) throws IOException {
        if (null != orderId) {
            //防止重复消费
            OrderInfo orderInfo = orderService.getById(orderId);
            if (null != orderInfo && orderInfo.getOrderStatus().equals(ProcessStatus.UNPAID.getOrderStatus().name())) {
                // 取消订单
                orderService.execExpiredOrder(orderId);
            }
        }
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_PAYMENT_PAY),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_PAYMENT_PAY),
            key = {MqConst.ROUTING_PAYMENT_PAY}
    ))
    public void paySuccess(Long orderId, Message message, Channel channel) throws IOException {
        // 支付成功！
        if (orderId!=null){
            // 防止重复消费
            OrderInfo orderInfo = orderService.getById(orderId);
            if (orderInfo!=null && orderInfo.getOrderStatus().equals(OrderStatus.UNPAID.name())){
                // 支付成功！ 修改订单状态为已支付
                orderService.updateOrderStatus(orderId, ProcessStatus.PAID);
                // 发送消息，通知仓库
                orderService.sendOrderStatus(orderId);
            }
        }
        // 单个确认消息
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_WARE_ORDER, durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_WARE_ORDER),
            key = {MqConst.ROUTING_WARE_ORDER}
    ))
    public void updateOrderStatus(String msgJson, Message message, Channel channel) throws IOException {
        if (!StringUtils.isEmpty(msgJson)){
            Map<String,Object> map = JSON.parseObject(msgJson, Map.class);
            String orderId = (String)map.get("orderId");
            String status = (String)map.get("status");
            if ("DEDUCTED".equals(status)){
                // 减库存成功！ 修改订单状态为已支付
                orderService.updateOrderStatus(Long.parseLong(orderId), ProcessStatus.WAITING_DELEVER);
            }else {
                orderService.updateOrderStatus(Long.parseLong(orderId), ProcessStatus.STOCK_EXCEPTION);
            }
        }
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

}
