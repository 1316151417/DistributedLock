package com.tal.zj.distributedLock;

import com.tal.zj.distributedLock.exception.LockException;

import java.util.concurrent.TimeUnit;

/**
 * @Author ZhouJie
 * @Date 2019/4/4 18:02
 * @Description 分布式锁接口
 */
public interface DistributedLock {

    /**
     * 获取锁
     * @throws LockException
     */
    public void lock() throws LockException;

    /**
     * 尝试获取锁
     * @param timeout 超时时间数值
     * @param unit 超时时间单位
     * @return 是否成功获取锁
     * @throws LockException
     */
    public boolean tryLock(long timeout, TimeUnit unit) throws LockException;

    /**
     * 释放锁
     * @throws LockException
     */
    public void unLock() throws LockException;

}
