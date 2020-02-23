package com.atguigu.gmall.item.service;

import java.util.Map;

public interface ItemService {
    /**
     * 根据商品Id 查询map 集合
     * @param skuId
     * @return
     */
    Map<String, Object> getBySkuId(Long skuId);
}
