package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.jdbc.Null;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.constants.SystemConstants.MAX_RETRY_COUNT;
import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CacheClient cacheClient;
    @Override
    public Result queryById(Long id) {

        // 缓存穿透
        // Shop shop = queryWithPassThrough(id);

        // 互斥锁解决缓存击穿
        // Shop shop = queryWithMutex(id);
        // 缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            Result.fail("店铺数据不存在！");
        }
        return Result.ok(shop);
    }

    /**
     * 互斥锁查询方法
     *
     * @param id 店铺ID
     * @return 店铺详情
     */
    public Shop queryWithMutex(Long id) {
        // 构建缓存key
        String key = CACHE_SHOP_KEY + id;
        // 1.从redis查询缓存
        String shopJson = redisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3.存在直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 判断是否为缓存的空值
        if (shopJson != null) {
            // 已经缓存空值直接返回null
            return null;
        }
        // 4. 实现缓存重建
        // 4.1. 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        boolean isLock = false;

        try {
            // 重试次数
            int retryCount = 0;
            isLock = tryLock(lockKey);
            // 4.2. 判断是否获取成功
            if (!isLock) {
                // 4.3. 失败，则休眠并重试
                while (!isLock && retryCount < MAX_RETRY_COUNT){
                    Thread.sleep(20);
                    isLock = tryLock(lockKey);
                    retryCount++;
                }
                // 到达最大次数，抛出异常
                if (!isLock) {
                    log.error("Failed to acquire lock for id: {}", id);
                    return null;
                }
            }

            // 4.4. 获取锁成功再判断是否有缓存(DoubleCheck)
            String shopJsonAfterLock = redisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(shopJsonAfterLock)) {
                // 4.5. 存在直接返回
                shop = JSONUtil.toBean(shopJsonAfterLock, Shop.class);
                //log.info("redis -> shop (after lock) -> {}", shop)
                return shop;
            }
            // 判断是否为缓存的空值
            if (shopJsonAfterLock != null) {
                // 空值返回null
                return null;
            }

            // 4.不存在，根据id查询数据库
            shop = getById(id);

            //模拟重建的延时
            // Thread.sleep(200)

            if (shop == null) {
                // 将空值缓存进redis
                redisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 6.存在，写入redis
            redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
            log.info("mysql -> shop -> {}", shop);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            releaseLock(lockKey);
        }
        return shop;
    }

    /**
     * 缓存穿透查询方法
     *
     * @param id 店铺ID
     * @return 店铺详情
     */
    public Shop queryWithPassThrough(Long id) {
        // 构建缓存key
        String key = CACHE_SHOP_KEY + id;
        // 1.从redis查询缓存
        String shopJson = redisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3.存在直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            log.info("redis -> shop -> {}", shop);
            return shop;
        }
        // 判断是否为空值
        if (shopJson != null) {
            // 空值直接返回错误信息
            return null;
        }

        // 4.不存在，根据id查询数据库
        Shop shop = getById(key);
        if (shop == null) {
            // 将空值缓存进redis
            redisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 6.存在，写入redis
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        log.info("mysql -> shop -> {}", shop);
        return shop;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空!");
        }
        // 1.更新数据库数据
        updateById(shop);
        // 2.删除缓存
        redisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
