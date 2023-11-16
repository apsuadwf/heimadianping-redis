package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
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

}
