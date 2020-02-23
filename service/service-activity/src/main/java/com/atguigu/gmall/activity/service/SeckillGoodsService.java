package com.atguigu.gmall.activity.service;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.activity.SeckillGoods;

import java.util.List;

public interface SeckillGoodsService {
    /**
     * 返回全部列表 查询当天全部的秒杀商品
     * @return
     */
    List<SeckillGoods> findAll();


    /**
     * 根据商品ID获取实体
     * @param skuId
     * @return
     */
    SeckillGoods getSeckillGoods(Long skuId);

    /**
     * 下单
     * @param skuId
     * @param userId
     */
    void seckillOrder(Long skuId, String userId);

    /***
     * 根据商品id与用户ID查看订单信息
     * @param skuId
     * @param userid
     * @return
     */
    Result checkOrder(Long skuId, String userid);

}
