package com.atguigu.gmall.order.controller;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.client.CartFeignClient;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.user.UserAddress;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.atguigu.gmall.user.client.UserFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/order")
public class OrderApiController {
    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private CartFeignClient cartFeignClient;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductFeignClient productFeignClient;

    @GetMapping("auth/trade")
    public Result<Map<String, Object>> trade(HttpServletRequest request) {
        // 获取用户Id
        String userId = AuthContextHolder.getUserId(request);
        // 根据用户Id 获取用户地址列表
        List<UserAddress> userAddressList = userFeignClient.findUserAddressListByUserId(userId);
        OrderInfo orderInfo = new OrderInfo();
        // 获取购物车列表
        List<CartInfo> cartCheckedList = cartFeignClient.getCartCheckedList(userId);
        ArrayList<OrderDetail> detailArrayList = new ArrayList<>();
        if (cartCheckedList!=null &&  cartCheckedList.size()>0){
            for (CartInfo cartInfo : cartCheckedList) {
                OrderDetail orderDetail = new OrderDetail();
                orderDetail.setSkuId(cartInfo.getSkuId());
                orderDetail.setSkuName(cartInfo.getSkuName());
                orderDetail.setImgUrl(cartInfo.getImgUrl());
                orderDetail.setSkuNum(cartInfo.getSkuNum());
                orderDetail.setOrderPrice(cartInfo.getCartPrice());

                detailArrayList.add(orderDetail);
            }
            orderInfo.setOrderDetailList(detailArrayList);
            orderInfo.sumTotalAmount();
        }
        Map<String, Object> result = new HashMap<>();
        result.put("userAddressList", userAddressList);
        result.put("detailArrayList", detailArrayList);
        // 保存总金额
        result.put("totalNum", detailArrayList.size());
        result.put("totalAmount", orderInfo.getTotalAmount());
        // 获取流水号
        String tradeNo = orderService.getTradeNo(userId);
        result.put("tradeNo", tradeNo);
        return Result.ok(result);
    }

    @PostMapping("auth/submitOrder")
    public Result submitOrder(@RequestBody OrderInfo orderInfo, HttpServletRequest request) {
        // 获取到用户Id
        String userId = AuthContextHolder.getUserId(request);
        orderInfo.setUserId(Long.parseLong(userId));

        // 获取前台页面的流水号
        String tradeNo = request.getParameter("tradeNo");

        // 调用服务层的比较方法
        boolean flag = orderService.checkTradeCode(userId, tradeNo);
        if (!flag) {
            // 比较失败！
            return Result.fail().message("不能重复提交订单！");
        }
        //  删除流水号
        orderService.deleteTradeNo(userId);

        // 验证库存：
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        for (OrderDetail orderDetail : orderDetailList) {
            // 验证库存：
            boolean result = orderService.checkStock(orderDetail.getSkuId(), orderDetail.getSkuNum());
            if (!result) {
                return Result.fail().message(orderDetail.getSkuName() + "库存不足！");
            }
            // 验证价格：
            BigDecimal skuPrice = productFeignClient.getSkuPrice(orderDetail.getSkuId());
            if (orderDetail.getOrderPrice().compareTo(skuPrice) != 0) {
                // 重新查询价格！
                cartFeignClient.loadCartCache(userId);
                return Result.fail().message(orderDetail.getSkuName() + "价格有变动！");
            }
        }

        // 验证通过，保存订单！
        Long orderId = orderService.saveOrderInfo(orderInfo);
        return Result.ok(orderId);
    }

    /**
     * 内部调用获取订单
     * @param orderId
     * @return
     */
    @GetMapping("inner/getOrderInfo/{orderId}")
    public OrderInfo getOrderInfo(@PathVariable(value = "orderId") Long orderId){
        OrderInfo orderInfo = orderService.getOrderInfo(orderId);
        return orderInfo;
    }


    /**
     * 拆单业务
     * @param request
     * @return
     */
    @RequestMapping("orderSplit")
    public String orderSplit(HttpServletRequest request){
        String orderId = request.getParameter("orderId");
        // [{"wareId":"1","skuIds":["2","10"]},{"wareId":"2","skuIds":["3"]}]
        String wareSkuMap = request.getParameter("wareSkuMap");

        // 拆单：获取到的子订单集合
        List<OrderInfo> subOrderInfoList = orderService.orderSplit(Long.parseLong(orderId),wareSkuMap);
        // 声明一个存储map的集合
        ArrayList<Map> mapArrayList = new ArrayList<>();
        // 生成子订单集合
        for (OrderInfo orderInfo : subOrderInfoList) {
            Map map = orderService.initWareOrder(orderInfo);
            // 添加到集合中！
            mapArrayList.add(map);
        }
        return JSON.toJSONString(mapArrayList);
    }
    /**
     * 秒杀提交订单，秒杀订单不需要做前置判断，直接下单
     * @param orderInfo
     * @return
     */
    @PostMapping("inner/seckill/submitOrder")
    public Long submitOrder(@RequestBody OrderInfo orderInfo) {
        Long orderId = orderService.saveOrderInfo(orderInfo);
        return orderId;
    }


}
