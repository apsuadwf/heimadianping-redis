package com.hmdp.util;

import cn.hutool.core.thread.ThreadUtil;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @Author: apsuadwf
 * @Date: 2023/11/18 20:14
 */
@SpringBootTest
public class RedisIdWorkerTest {

    @Autowired
    private RedisIdWorker redisIdWorker;

    private ExecutorService executor = Executors.newFixedThreadPool(500);

    @Test
    void nextIdTest() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
//                System.out.println("id = " + id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            executor.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end-begin) + "ms");
    }
}

