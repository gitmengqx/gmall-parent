package com.atguigu.gmall.activity.service.impl;

import com.atguigu.gmall.activity.mapper.SeckillGoodsMapper;
import com.atguigu.gmall.activity.service.SeckillGoodsService;
import com.atguigu.gmall.activity.util.CacheHelper;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.common.util.MD5;
import com.atguigu.gmall.model.activity.OrderRecode;
import com.atguigu.gmall.model.activity.SeckillGoods;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class SeckillGoodsServiceImpl implements SeckillGoodsService {

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public List<SeckillGoods> findAll() {
        // 从缓存中直接获取数据即可
        List list = redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).values();
        return list;
    }

    @Override
    public SeckillGoods getSeckillGoods(Long skuId) {
        SeckillGoods seckillGoods = (SeckillGoods) redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).get(skuId.toString());
        return seckillGoods;
    }

    @Override
    public void seckillOrder(Long skuId, String userId) {
        //产品状态位， 1：可以秒杀 0：秒杀结束
        String state = (String) CacheHelper.get(skuId.toString());
        if("0".equals(state)) {
            //已售罄
            return;
        }

        //判断用户是否下单 setnx
        boolean isExist = redisTemplate.opsForValue().setIfAbsent(RedisConst.SECKILL_USER + userId, skuId, RedisConst.SECKILL__TIMEOUT, TimeUnit.SECONDS);
        if (!isExist) {
            return;
        }
        // mq处理高并发，list 保证原子性！
        //获取队列中的商品，如果能够获取，则商品存在，可以下单
        String goodsId = (String) redisTemplate.boundListOps(RedisConst.SECKILL_STOCK_PREFIX + skuId).rightPop();
        if (StringUtils.isEmpty(goodsId)) {
            //商品售罄，更新状态位
            redisTemplate.convertAndSend("seckillpush", skuId+":0");
            //已售罄
            return;
        }

        //订单记录
        OrderRecode orderRecode = new OrderRecode();
        orderRecode.setUserId(userId);
        orderRecode.setSeckillGoods(this.getSeckillGoods(skuId));
        orderRecode.setNum(1);

        //订单数据存入Reids
        redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).put(orderRecode.getUserId(), orderRecode);
        //更新库存
        this.updateStockCount(orderRecode.getSeckillGoods().getSkuId());

    }

    private void updateStockCount(Long skuId) {
        //更新库存，批量更新，用于页面显示，以实际扣减库存为准
        // hash ---- 要被秒杀的商品
        // list ---- 库存数量
        Long stockCount = redisTemplate.boundListOps(RedisConst.SECKILL_STOCK_PREFIX + skuId).size();
        if (stockCount % 2 == 0) {
            //商品卖完,同步数据库
            SeckillGoods seckillGoods = this.getSeckillGoods(skuId);
            seckillGoods.setStockCount(stockCount.intValue());
            seckillGoodsMapper.updateById(seckillGoods);

            //更新缓存
            redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).put(seckillGoods.getSkuId().toString(), seckillGoods);

        }
    }


    /***
     * 根据用户ID查看订单信息
     * @param userId
     * @return
     */
    @Override
    public Result checkOrder(Long skuId, String userId) {
        // 用户在缓存中存在，有机会秒杀到商品
        boolean isExist =redisTemplate.hasKey(RedisConst.SECKILL_USER + userId);
        if (isExist) {
            //判断用户是否正在排队
            //判断用户是否下单
            boolean isHasKey = redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).hasKey(userId);
            if (isHasKey) {
                //抢单成功
                OrderRecode orderRecode = (OrderRecode) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).get(userId);
                // 秒杀成功！
                return Result.build(orderRecode, ResultCodeEnum.SECKILL_SUCCESS);
            }
        }

        //判断是否下单
        boolean isExistOrder = redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).hasKey(userId);
        if(isExistOrder) {
            String orderId = (String)redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).get(userId);
            return Result.build(orderId, ResultCodeEnum.SECKILL_ORDER_SUCCESS);
        }

        String state = (String) CacheHelper.get(skuId.toString());
        if("0".equals(state)) {
            //已售罄 抢单失败
            return Result.build(null, ResultCodeEnum.SECKILL_FAIL);
        }

        //正在排队中
        return Result.build(null, ResultCodeEnum.SECKILL_RUN);
    }

}
