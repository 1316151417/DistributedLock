package com.tal.zj.test;

import com.tal.zj.distributedLock.DistributedLock;
import com.tal.zj.distributedLock.zookeeper.DistributedLockImpl;
import com.tal.zj.distributedLock.zookeeper.ZookeeperClient;
import org.junit.Test;

public class DistributedLockTest {
    @Test
    public void zookeeperTest() {
        ZookeeperClient client = new ZookeeperClient("59.110.167.187:2181", 5000);
        String lockPath = "/lockPath";
        DistributedLock lock1 = new DistributedLockImpl(client, lockPath);
        DistributedLock lock2 = new DistributedLockImpl(client, lockPath);

        new Thread(() -> {
            try {
                lock1.lock();
                System.out.println("thread 1 get lock...");
                Thread.sleep(5000);
                System.out.println("thread 1 unLock...");
                lock1.unLock();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        new Thread(() -> {
            try {
                lock2.lock();
                System.out.println("thread 2 get lock...");
                Thread.sleep(5000);
                System.out.println("thread 2 unLock...");
                lock2.unLock();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        try {
            Thread.sleep(20000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
}
