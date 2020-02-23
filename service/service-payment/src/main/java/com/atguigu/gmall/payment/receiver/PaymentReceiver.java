package com.atguigu.gmall.payment.receiver;


import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.payment.service.PaymentService;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PaymentReceiver {

    @Autowired
    private PaymentService paymentService;

    /**
     * 取消交易
     * @param orderId
     * @throws IOException
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_PAYMENT_CLOSE, durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_PAYMENT_CLOSE),
            key = {MqConst.ROUTING_PAYMENT_CLOSE}
    ))
    public void closePayment(Long orderId) throws IOException {
        if (null != orderId) {
            paymentService.closePayment(orderId);
        }
    }


}
