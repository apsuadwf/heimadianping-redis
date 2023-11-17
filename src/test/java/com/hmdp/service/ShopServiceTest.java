package com.hmdp.service;

import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * @Author: apsuadwf
 * @Date: 2023/11/17 16:48
 */
@SpringBootTest
public class ShopServiceTest {

    @Autowired
    private ShopServiceImpl shopService;

    @Test
    public void saveShop2RedisTest(){
        shopService.saveShop2Redis(1L,30L);
    }

}
