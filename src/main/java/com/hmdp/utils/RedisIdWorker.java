package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * RedisId生成器
 *
 * @Author: apsuadwf
 * @Date: 2023/11/18 19:26
 */
@Component
public class RedisIdWorker {

    /**
     * 开始时间戳 (2023-1-1 0:0:0)
     */
    private static final long BEGIN_TIMESTAMP = 1672531200L;

    /**
     * 序列号位数
     */
    private static final int COUNT_BITS = 32;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * id生成方法
     * @param keyPrefix 业务前缀
     * @return ID
     */
    public long nextId(String keyPrefix){
        // 1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSecond - BEGIN_TIMESTAMP;

        // 2.生成序列号
        // 2.1.获取当前日期,精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2.自增长
        Long count = redisTemplate.opsForValue().increment("incr:" + keyPrefix + ":" + date);
        // 检查返回值是否为 null
        if (count == null) {
            // 如果为 null，给一个默认值
            count = 0L;
        }
        // 3.拼接并返回
        return timeStamp << COUNT_BITS | count;
    }
}
