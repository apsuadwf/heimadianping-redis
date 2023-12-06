package com.hmdp.util;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Author: apsuadwf
 * @Date: 2023/12/04 21:30
 */

@SpringBootTest
public class InitiateAction {
    @Autowired
    private IShopService shopService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void loadShopDate() {
        // 查询店铺信息
        List<Shop> list = shopService.list();
        // 把店铺按照TypeId分组
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 分批写入redis
        map.forEach((typeId, shopList) -> {
            String key = "shop:geo:" + typeId;
            // 写入redis GEOADD key 经度 纬度 member
            // 将shopList转换成geoLocationList
            List<RedisGeoCommands.GeoLocation<String>> geoLocationList = shopList.stream()
                    .map(shop -> new RedisGeoCommands.GeoLocation<>(shop.getId().toString(),
                            new Point(shop.getX(), shop.getY())))
                    .collect(Collectors.toList());
            System.out.println(geoLocationList.size());
            System.out.println(geoLocationList);
            // 批量写入Redis
            Long count = redisTemplate.opsForGeo().add(key, geoLocationList);
            System.out.println("插入成功" + count + "条");
        });
    }
}
