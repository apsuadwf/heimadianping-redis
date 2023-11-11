package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Result queryTypeList() {
        // 1.查询redis中是否存在
        List<String> shopTypeJson = redisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY, 0, -1);
        // 2.判断是否存在
        if (Objects.nonNull(shopTypeJson) && !shopTypeJson.isEmpty()) {
            // 3.存在转换后返回
            List<ShopType> shopTypeList = shopTypeJson.stream()
                    .map((s) -> JSONUtil.toBean(s, HashMap.class))
                    .map((map) -> BeanUtil.fillBeanWithMap(map, new ShopType(), false)
                    ).collect(Collectors.toList());
            log.info("redis -> shopTypeList -> {}",shopTypeJson);
            return Result.ok(shopTypeList);
        }
        // 4.不存在，先查询数据库
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        // 5.判断是否存在
        if (Objects.isNull(shopTypeList) || shopTypeList.isEmpty()) {
            return Result.fail("店铺类型不存在！");
        }

        // 6.存在，先存入redis中
        shopTypeJson = shopTypeList.stream().map(JSONUtil::toJsonStr).collect(Collectors.toList());
        log.info("mysql -> shopTypeList -> {}",shopTypeJson);
        redisTemplate.opsForList().leftPushAll(CACHE_SHOP_TYPE_KEY,shopTypeJson);
        redisTemplate.expire(CACHE_SHOP_TYPE_KEY,CACHE_NULL_TTL, TimeUnit.HOURS);
        return Result.ok(shopTypeList);
    }
}
