package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: apsuadwf
 * @Date: 2023/11/21 19:54
 */
@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient(){
        // 配置
        Config config = new Config();
        // 单节点模式
        config.useSingleServer().setAddress("redis://43.137.13.16:6379").setPassword("420_Xsbth");
        // 创建RedissonClient对象
        return Redisson.create(config);
    }
}
