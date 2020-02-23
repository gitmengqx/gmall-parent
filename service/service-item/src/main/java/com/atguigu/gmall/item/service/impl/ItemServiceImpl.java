package com.atguigu.gmall.item.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.item.service.ItemService;
import com.atguigu.gmall.list.client.ListFeignClient;
import com.atguigu.gmall.model.product.BaseCategoryView;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.model.product.SpuSaleAttr;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@Service
public class ItemServiceImpl implements ItemService {

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    @Autowired
    private ListFeignClient listFeignClient;

    @Override
    public Map<String, Object> getBySkuId(Long skuId) {
        Map<String, Object> result = new HashMap<>();
//        // 通过skuId 查询skuInfo
//        SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
//        // 销售属性-销售属性值回显并锁定
//        List<SpuSaleAttr> spuSaleAttrList =  productFeignClient.getSpuSaleAttrListCheckBySku(skuInfo.getId(), skuInfo.getSpuId());
//
//        //根据spuId 查询map 集合属性
//        Map skuValueIdsMap =  productFeignClient.getSkuValueIdsMap(skuInfo.getSpuId());
//
//        //获取商品最新价格
//        BigDecimal skuPrice = productFeignClient.getSkuPrice(skuId);
//
//        String valuesSkuJson = JSON.toJSONString(skuValueIdsMap);
//
//        BaseCategoryView categoryView = productFeignClient.getCategoryView(skuInfo.getCategory3Id());
//
//        result.put("categoryView",categoryView);
//        // 获取价格
//        result.put("price",skuPrice);
//        // 保存valuesSkuJson
//        result.put("valuesSkuJson",valuesSkuJson);
//        // 保存数据
//        result.put("spuSaleAttrList",spuSaleAttrList);
//        // 保存skuInfo
//        result.put("skuInfo",skuInfo);
//        CompletableFuture.supplyAsync()

        // 通过skuId 查询skuInfo
        CompletableFuture<SkuInfo> skuCompletableFuture  = CompletableFuture.supplyAsync(() -> {
            SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
            result.put("skuInfo", skuInfo);
            return skuInfo;
        }, threadPoolExecutor);

        // 销售属性-销售属性值回显并锁定
        CompletableFuture<Void> spuSaleAttrCompletableFuture  = skuCompletableFuture.thenAcceptAsync(skuInfo -> {
            List<SpuSaleAttr> spuSaleAttrList = productFeignClient.getSpuSaleAttrListCheckBySku(skuInfo.getId(), skuInfo.getSpuId());
            result.put("spuSaleAttrList", spuSaleAttrList);
        }, threadPoolExecutor);

        //根据spuId 查询map 集合属性
        // 销售属性-销售属性值回显并锁定
        CompletableFuture<Void> skuValueIdsMapCompletableFuture  = skuCompletableFuture.thenAcceptAsync(skuInfo -> {
            Map skuValueIdsMap = productFeignClient.getSkuValueIdsMap(skuInfo.getSpuId());
            String valuesSkuJson = JSON.toJSONString(skuValueIdsMap);
            // 保存valuesSkuJson
            result.put("valuesSkuJson", valuesSkuJson);
        }, threadPoolExecutor);

        // 获取商品的最新价格
        CompletableFuture<Void> skuPriceCompletableFuture  = CompletableFuture.runAsync(() -> {
            BigDecimal skuPrice = productFeignClient.getSkuPrice(skuId);
            result.put("price", skuPrice);
        }, threadPoolExecutor);

        // 获取分类信息
        CompletableFuture<Void> categoryViewCompletableFuture = skuCompletableFuture.thenAcceptAsync(skuInfo -> {
            BaseCategoryView categoryView = productFeignClient.getCategoryView(skuInfo.getCategory3Id());
            //分类信息
            result.put("categoryView", categoryView);
        }, threadPoolExecutor);

        // 更新热度排名
        CompletableFuture<Void> incrHotScoreCompletableFuture = CompletableFuture.runAsync(() -> {
            listFeignClient.incrHotScore(skuId);
        }, threadPoolExecutor);
        CompletableFuture.allOf(skuCompletableFuture, spuSaleAttrCompletableFuture, skuValueIdsMapCompletableFuture,skuPriceCompletableFuture, categoryViewCompletableFuture,incrHotScoreCompletableFuture).join();
        return result;

    }

    public static void main(String[] args) {
//        CompletableFuture.supplyAsync(()->{
//            //int i = 1 / 0 ;
//            return "hello";
//
//        }).whenComplete((t,u)->{
//            System.out.println(t);
//            System.out.println(u);
//        });
        CompletableFuture.supplyAsync(() -> {
            return "hello";
        }).thenApplyAsync(t -> {
            return t + " world!";
        }).thenCombineAsync(CompletableFuture.completedFuture(" CompletableFuture"), (t, u) -> {
            return t + u;
        }).whenComplete((t, u) -> {
            System.out.println(t);
        });




    }
}
