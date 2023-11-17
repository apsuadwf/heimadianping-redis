package com.hmdp.utils;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @Author: apsuadwf
 * @Date: 2023/11/16 15:50
 */
@Slf4j
@Component
public class CacheClient {

    @Autowired
    private final StringRedisTemplate redisTemplate;

    private final ExecutorService CACHE_REBUILD_EXECUTOR = new ThreadPoolExecutor(
            2,
            5,
            3,
            TimeUnit.SECONDS,
            new LinkedBlockingDeque<>(3),
            Executors.defaultThreadFactory(),
            new ThreadPoolExecutor.DiscardOldestPolicy());

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.redisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit){
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }
    public void setWithLogicalExpire(String key, Object value, Long time , TimeUnit unit){
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入redis
        redisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }
    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallBack,Long time,TimeUnit unit) {
        // 构建缓存key
        String key = keyPrefix + id;
        // 1.从redis查询缓存
        String json = redisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isNotBlank(json)) {
            // 3.存在直接返回
            return JSONUtil.toBean(json, type);
        }
        // 判断是否为空值
        if (json != null) {
            // 空值直接返回错误信息
            return null;
        }

        // 4.不存在，根据id查询数据库
        R r = dbFallBack.apply(id);
        if (r == null) {
            // 将空值缓存进redis
            redisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 6.存在，写入redis
        this.set(key,r,time,unit);
        return r;
    }

    public <R,ID> R queryWithLogicalExpire(String keyPrefix,ID id,Class<R> type,Function<ID,R> dbFallBack,Long time,TimeUnit unit) {
        // 构建缓存key
        String key = keyPrefix + id;
        // 1.从redis查询缓存
        String json = redisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isBlank(json)) {
            // 3.不存在直接返回
            return null;
        }
        // 4. 命中，需要先吧JSON反序列化为对象

        RedisData<R> redisData = JSONUtil.toBean(json, new TypeReference<RedisData<R>>() {}, false);
        R r = redisData.getData();
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5. 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.1. 未过期，直接返回店铺信息
            return r;
        }
        // 5.2. 已过期，缓存重建
        // 6.缓存重建
        // 6.1.获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 6.2.判断是否获取成功
        if (isLock) {
            // 6.3.成功,开启独立线程,实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 查询数据库
                    R queryR = dbFallBack.apply(id);
                    RedisData<R> rRedisData = new RedisData<>();
                    // 写入Redis
                    this.setWithLogicalExpire(key,queryR,time,unit);
                } catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    // 释放锁
                    releaseLock(lockKey);
                }
            });

        }
        // 6.4.返回过期的商铺信息
        return r;
    }

    /**
     * 尝试获取锁
     *
     * @param lockKey key
     * @return 获取锁结果
     */
    private boolean tryLock(String lockKey) {
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 删除锁
     *
     * @param lockKey key
     */
    private void releaseLock(String lockKey) {
        redisTemplate.delete(lockKey);
    }
}
