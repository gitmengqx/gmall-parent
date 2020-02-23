package com.atguigu.gmall.all.client.impl;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.all.client.ItemFeignClient;
import org.springframework.stereotype.Component;

@Component
public class ItemDegradeFeignClient implements ItemFeignClient {
    @Override
    public Result getItem(Long skuId) {
        return Result.ok();
    }
}
