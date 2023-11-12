package com.hmdp.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * @Author: apsuadwf
 * @Date: 2023/11/12 11:20
 */
@SpringBootTest
public class HuToolTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    public void JSONUtilToBeanTest(){
//        System.out.println(redisTemplate == null);
//        redisTemplate.opsForValue().set("123123", "");
//        String shopJsonAfterLock = redisTemplate.opsForValue().get("123123");
//        System.out.println("shopJsonAfterLock->"+shopJsonAfterLock);
//        boolean notBlank = StrUtil.isNotBlank(shopJsonAfterLock);
//        System.out.println(notBlank);
//        Shop shop = JSONUtil.toBean(shopJsonAfterLock, Shop.class,true);
//        System.out.println(shop);
    }
}
