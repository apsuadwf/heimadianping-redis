package com.hmdp.utils;

/**
 * @Author: apsuadwf
 * @Date: 2023/11/20 20:03
 */
public interface ILock {
    /**
     * 尝试获取搜
     * @param timeoutSec 锁持有的超时时间,过期后自动释放锁
     * @return true代表获取锁成功;false 代表获取锁失败
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
