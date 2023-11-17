package com.hmdp.util;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * @Author: apsuadwf
 * @Date: 2023/11/17 22:09
 */
@SpringBootTest
public class CacheClientTest {
    @Autowired
    private CacheClient cacheClient;
    @Autowired
    private ShopServiceImpl shopService;

    @Test
    void SaveShopTest(){
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY+1L,shop,10L, TimeUnit.SECONDS);
    }
}
