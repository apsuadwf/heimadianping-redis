package com.hmdp.service.impl;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constants.SystemConstants;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

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

    private final ExecutorService CACHE_REBUILD_EXECUTOR = new ThreadPoolExecutor(
            2,
            5,
            3,
            TimeUnit.SECONDS,
            new LinkedBlockingDeque<>(3),
            Executors.defaultThreadFactory(),
            new ThreadPoolExecutor.DiscardOldestPolicy());

    @Override
    public Result queryById(Long id) {

        // 缓存穿透
        // Shop shop = queryWithPassThrough(id);

        // 互斥锁解决缓存击穿
//         Shop shop = queryWithMutex(id);

        // 逻辑过期时间
//        Shop shop = queryWithLogicalExpire(id);
        // 缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES)
        if (shop == null) {
            Result.fail("店铺数据不存在！");
        }
        return Result.ok(shop);
    }

    public Shop queryWithLogicalExpire(Long id) {
        // 构建缓存key
        String key = CACHE_SHOP_KEY + id;
        // 1.从redis查询缓存
        String shopJson = redisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            // 3.不存在直接返回
            return null;
        }
        // 4. 命中，需要先吧JSON反序列化为对象
        RedisData<Shop> redisData = JSONUtil.toBean(shopJson, new TypeReference<RedisData<Shop>>() {
        }, false);
        Shop shop = redisData.getData();
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5. 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.1. 未过期，直接返回店铺信息
            return shop;
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
                    this.saveShop2Redis(id, LOCK_SHOP_TTL);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    releaseLock(lockKey);
                }
            });

        }
        // 6.4.返回过期的商铺信息
        return shop;
    }

    public void saveShop2Redis(Long id, Long expireSeconds) {
        // 1.查询店铺数据
        Shop shop = getById(id);
        // 2.封装逻辑过期时间
        RedisData<Shop> redisData = new RedisData<>();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 3.写入Redis
        redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
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
                while (!isLock && retryCount < MAX_RETRY_COUNT) {
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

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 不需要坐标查询,按数据库查询
            // 根据类型分页查询
            Page<Shop> page = lambdaQuery()
                    .eq(Shop::getTypeId, typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        // 计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        String key = SHOP_GEO_KEY + typeId;
        // 查询redis 按照距离排序 分页 结果:shopId distance
        // GEOSEARCH BYLONLAT x y BYRADIUS 10 WITHDISTANCE
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo()
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                                .includeDistance()
                                .limit(end));
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();

        // 已经到最后一页,跳过个数(from)超过数据个数,说明最后一页已经到底
        if (from > list.size()) {
            // 截取可能出现空的情况
            return Result.ok("数据到底啦");
        }

        List<String> shopIdList = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        // 截取 from - end 部分
        list.stream().skip(from).forEach(r -> {
            // 获取店铺Id
            String shopIdStr = r.getContent().getName();
            shopIdList.add(shopIdStr);
            // 获取距离
            Distance distance = r.getDistance();
            distanceMap.put(shopIdStr, distance);
        });

        // 截取可能出现空的情况
//        if (shopIdList.size() < 1){
//            return Result.ok("数据到底啦");
//        }

        // 根据id查询Shop
        List<Shop> shopList = lambdaQuery()
                .in(Shop::getId, shopIdList)
                .list()
                .stream()
                // 按照idList的顺序排序结果
                .sorted(Comparator.comparingLong(shop -> shopIdList.indexOf(shop.getId().toString())))
                // 设置distance
                .peek(shop -> shop.setDistance(distanceMap.get(shop.getId().toString()).getValue()))
                .collect(Collectors.toList());
        // 返回结果
        return Result.ok(shopList);
    }
}
