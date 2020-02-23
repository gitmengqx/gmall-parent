package com.atguigu.gmall.product.service.impl;

import com.atguigu.gmall.common.config.RedissonConfig;
import com.atguigu.gmall.product.service.TestService;
import org.apache.commons.lang.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class TestServiceImpl implements TestService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Override
    public void testLock() {

        RLock lock = redissonClient.getLock("lock");
        lock.lock();
        String value = this.redisTemplate.opsForValue().get("num");
        if (StringUtils.isEmpty(value)){
            return;
        }
        int numValue = Integer.parseInt(value);
        redisTemplate.opsForValue().set("num",String.valueOf(++numValue));
        // 删除锁
//            redisTemplate.delete("lock");

        lock.unlock();
//        getRedis();
    }

    private void getRedis() {
        // String num = (String) redisTemplate.opsForValue().get("num");
        String uuid = UUID.randomUUID().toString().replace("-","");
        Boolean flag = this.redisTemplate.opsForValue().setIfAbsent("lock", uuid,5, TimeUnit.SECONDS);
        // 1=true
        if (flag){
            String value = this.redisTemplate.opsForValue().get("num");
            if (StringUtils.isEmpty(value)){
                return;
            }
            int numValue = Integer.parseInt(value);
            redisTemplate.opsForValue().set("num",String.valueOf(++numValue));
            // 删除锁
//            redisTemplate.delete("lock");
            String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

            redisTemplate.execute(new DefaultRedisScript<>(script), Arrays.asList("lock"),uuid);
        }else {
            try {
                Thread.sleep(1000);
                testLock();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
