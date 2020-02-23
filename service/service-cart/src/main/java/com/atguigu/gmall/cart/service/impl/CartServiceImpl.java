package com.atguigu.gmall.cart.service.impl;

import com.atguigu.gmall.cart.mapper.CartInfoMapper;
import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class CartServiceImpl implements CartService {
    @Autowired
    private CartInfoMapper cartInfoMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private ProductFeignClient productFeignClient;

    // 定义获取购物车的key方法
    private String getCartKey(String userId){
        return RedisConst.USER_KEY_PREFIX+userId+RedisConst.USER_CART_KEY_SUFFIX;
    }
    @Override
    public void addToCart(Long skuId, String userId, Integer skuNum) {
        // 获取购物车的key
        String cartKey = getCartKey(userId);

        // 查看缓存是否有数据，没有数据则加载数据中的数据
        if (!redisTemplate.hasKey(cartKey)){
            loadCartCache(userId);
        }

        // 获取缓存对象
        QueryWrapper<CartInfo> cartInfoQueryWrapper = new QueryWrapper<>();
        cartInfoQueryWrapper.eq("sku_id",skuId).eq("user_id",userId);
        CartInfo cartInfoExist = cartInfoMapper.selectOne(cartInfoQueryWrapper);

        // 说明缓存中有数据
        if (cartInfoExist!=null){
            // 数量相加
            cartInfoExist.setSkuNum(cartInfoExist.getSkuNum()+skuNum);
            // 查询最新价格
            BigDecimal skuPrice = productFeignClient.getSkuPrice(skuId);
            cartInfoExist.setSkuPrice(skuPrice);
            // 更新数据
            cartInfoMapper.updateById(cartInfoExist);
        } else {
            CartInfo cartInfo1 = new CartInfo();
            SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
            cartInfo1.setSkuPrice(skuInfo.getPrice());
            cartInfo1.setCartPrice(skuInfo.getPrice());
            cartInfo1.setSkuNum(skuNum);
            cartInfo1.setSkuId(skuId);
            cartInfo1.setUserId(userId);
            cartInfo1.setImgUrl(skuInfo.getSkuDefaultImg());
            cartInfo1.setSkuName(skuInfo.getSkuName());
            // 添加数据库
            cartInfoMapper.insert(cartInfo1);
            cartInfoExist = cartInfo1;
        }
        // 更新缓存
        // hset(key,field,value)
        redisTemplate.boundHashOps(cartKey).put(skuId.toString(),cartInfoExist);
//        redisTemplate.opsForHash().put(cartKey,skuId.toString(),cartInfoExist);
        // 设置过期时间
        setCartKeyExpire(cartKey);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<CartInfo> getCartList(String userId, String userTempId) {
        // 创建集合
        List<CartInfo> cartInfoList = new ArrayList<>();
        // 未登录
        if (StringUtils.isEmpty(userId)) {
            cartInfoList = this.getCartList(userTempId);
        }
        // 已登录
        if (!StringUtils.isEmpty(userId)) {

            // 合并购物车
            List<CartInfo> cartInfoArrayList = this.getCartList(userTempId);
            if (cartInfoArrayList!=null && cartInfoArrayList.size()>0){
                // 合并购物车
                cartInfoList = this.mergeToCartList(cartInfoArrayList, userId);
                // 删除未登录购物车数据
                this.deleteCartList(userTempId);
            }

            if (CollectionUtils.isEmpty(cartInfoArrayList) || StringUtils.isEmpty(userTempId)){
                // 获取到登录数据
                cartInfoList = this.getCartList(userId);
            }
        }
        return cartInfoList;
    }

    @Override
    public void checkCart(String userId, Integer isChecked, Long skuId) {
        CartInfo cartInfo = new CartInfo();
        cartInfo.setIsChecked(isChecked);
        QueryWrapper queryWrapper = new QueryWrapper<CartInfo>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.eq("sku_id", skuId);
        cartInfoMapper.update(cartInfo, queryWrapper);

        // 修改缓存
        String cartKey = this.getCartKey(userId);
        // 获取缓存数据
        BoundHashOperations boundHashOperations = redisTemplate.boundHashOps(cartKey);
        if (boundHashOperations.hasKey(skuId.toString())){
            // 获取缓存数据
            CartInfo cartInfoUpd  = (CartInfo) boundHashOperations.get(skuId.toString());
            // 修改缓存数据
            cartInfoUpd.setIsChecked(isChecked);
            // 将赋值之后的对象写回缓存
            boundHashOperations.put(skuId.toString(),cartInfoUpd);

            // 设置过期时间
            this.setCartKeyExpire(cartKey);
        }

    }

    @Override
    public void deleteCart(Long skuId, String userId) {
        // 删除数据库，删除缓存
        QueryWrapper<CartInfo> cartInfoQueryWrapper = new QueryWrapper<>();
        cartInfoQueryWrapper.eq("sku_id",skuId).eq("user_id",userId);
        cartInfoMapper.delete(cartInfoQueryWrapper);

        // 获取缓存key
        String cartKey = this.getCartKey(userId);
        if (!redisTemplate.hasKey(cartKey)){
            loadCartCache(userId);
        }
        BoundHashOperations boundHashOperations = redisTemplate.boundHashOps(cartKey);

        boundHashOperations.delete(skuId.toString());

    }

    @Override
    public List<CartInfo> getCartCheckedList(String userId) {
        List<CartInfo> cartInfoList = new ArrayList<>();
        // 获取缓存的数据
        String cartKey = this.getCartKey(userId);
        List<CartInfo> cartCachInfoList = redisTemplate.opsForHash().values(cartKey);
        if (cartCachInfoList!=null && cartCachInfoList.size()>0){
            for (CartInfo cartInfo : cartCachInfoList) {
                if (cartInfo.getIsChecked().intValue()==1){
                    cartInfoList.add(cartInfo);
                }
            }
        }

        return cartInfoList;
    }

    // 删除未登录购物车
    private void deleteCartList(String userTempId) {
        // delete from userInfo where userId = ?userTempId
        QueryWrapper queryWrapper = new QueryWrapper<CartInfo>();
        queryWrapper.eq("user_id", userTempId);
        cartInfoMapper.delete(queryWrapper);

        // 删除缓存
        String cartKey = this.getCartKey(userTempId);
        redisTemplate.delete(cartKey);
    }

    // 合并购物车
    private List<CartInfo> mergeToCartList(List<CartInfo> cartInfoArrayList, String userId) {
        // 已经登录的数据
        List<CartInfo> cartInfoListLogin = this.getCartList(userId);
        // 什么意思？
        Map<Long, CartInfo> cartInfoMapLogin = cartInfoListLogin.stream().collect(Collectors.toMap(CartInfo::getSkuId, cartInfo -> cartInfo));

        for (CartInfo cartInfoNoLogin : cartInfoArrayList) {
            Long skuId = cartInfoNoLogin.getSkuId();
            CartInfo cartInfoLogin = cartInfoMapLogin.get(skuId);
            if (cartInfoMapLogin.containsKey(skuId)){
                cartInfoLogin = cartInfoMapLogin.get(skuId);
                // 数量相加
                cartInfoLogin.setSkuNum(cartInfoLogin.getSkuNum() + cartInfoNoLogin.getSkuNum());

                // 合并勾选 未登录选中的商品
                if (cartInfoNoLogin.getIsChecked().intValue() ==1){
                    cartInfoLogin.setIsChecked(1);
                }

                // 更新数据库
                cartInfoMapper.updateById(cartInfoLogin);
            }else {
                cartInfoNoLogin.setId(null);
                cartInfoNoLogin.setUserId(userId);
                cartInfoMapper.insert(cartInfoNoLogin);
            }
        }
        List<CartInfo> cartInfoList = loadCartCache(userId);
        return cartInfoList;
    }

    private List<CartInfo> getCartList(String userId) {
        List<CartInfo> cartInfoList = new ArrayList<>();
        if (StringUtils.isEmpty(userId)){
            return cartInfoList;
        }
        // 定义用户key
        String cartKey = this.getCartKey(userId);
        cartInfoList = redisTemplate.opsForHash().values(cartKey);
        if (cartInfoList!=null && cartInfoList.size()>0){
            cartInfoList.sort(new Comparator<CartInfo>() {
                @Override
                public int compare(CartInfo o1, CartInfo o2) {
                    return o1.getId().compareTo(o2.getId());
                }
            });
            return cartInfoList;
        }else {
            // 缓存中没用数据！
            cartInfoList = loadCartCache(userId);
            return cartInfoList;
        }
    }
    // 查询数据库并添加到缓存
    private List<CartInfo> loadCartCache(String userId) {
        // 根据userId 查询数据库
        QueryWrapper queryWrapper = new QueryWrapper<CartInfo>();
        queryWrapper.eq("user_id", userId);
        List<CartInfo> cartInfoList = cartInfoMapper.selectList(queryWrapper);

        if(cartInfoList==null || cartInfoList.size()==0){
            return cartInfoList;
        }
        // 将数据库中的数据查询并放入缓存
        HashMap<String, CartInfo> map = new HashMap<>();
        // 循环集合
        for (CartInfo cartInfo : cartInfoList) {
            // 查询一下最新价格
            BigDecimal skuPrice = productFeignClient.getSkuPrice(cartInfo.getSkuId());
            cartInfo.setSkuPrice(skuPrice);
            map.put(cartInfo.getSkuId().toString(), cartInfo);
        }
        // 获取key
        String cartKey = this.getCartKey(userId);
        redisTemplate.opsForHash().putAll(cartKey,map);
        // 设置过期时间
        this.setCartKeyExpire(cartKey);
        return cartInfoList;
    }

    // 过期时间
    private void setCartKeyExpire(String cartKey) {
        redisTemplate.expire(cartKey, RedisConst.USER_CART_EXPIRE, TimeUnit.SECONDS);
    }
}
