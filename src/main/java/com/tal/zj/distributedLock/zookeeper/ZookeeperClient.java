package com.tal.zj.distributedLock.zookeeper;

import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @Author ZhouJie
 * @Date 2019/4/4 18:08
 * @Description zookeeper客户端
 */
public class ZookeeperClient {

    private final String connectString;
    private final int sessionTimeout;
    /**
     *
     * @param connectString 连接字符串，多个以逗号隔开，例如ip1::port1,ip2:port2,ip3:port3
     * @param sessionTimeout 会话超时时间
     */
    public ZookeeperClient(String connectString, int sessionTimeout) {
        this.connectString = connectString;
        this.sessionTimeout = sessionTimeout;
    }

    private class SessionState {
        public SessionState(long sessionId, byte[] sessionPassword) {
            this.sessionId = sessionId;
            this.sessionPassword = sessionPassword;
        }
        private long sessionId;
        private byte[] sessionPassword;
    }
    private SessionState sessionState;

    private volatile ZooKeeper zooKeeper;

    /**
     * 获取Zookeeper客户端
     * @return Zookeeper客户端
     * @throws Exception
     */
    public ZooKeeper get() throws Exception {
        return get(0, null);
    }

    /**
     * 获取Zookeeper客户端
     * @param timeout 超时时间数值
     * @param timeUnit 超时时间单位
     * @return Zookeeper客户端
     * @throws Exception
     */
    public ZooKeeper get(long timeout, TimeUnit timeUnit) throws Exception {
        //双重锁校验懒加载
        if (zooKeeper == null) {
            synchronized (this) {
                if (zooKeeper == null) {
                    CountDownLatch connected = new CountDownLatch(1);
                    Watcher watcher = (event) -> {
                        switch (event.getType()) {
                            case None:
                                switch (event.getState()) {
                                    case SyncConnected:
                                        System.out.println("Zookeeper connected...");
                                        connected.countDown();
                                        break;
                                    case Expired:
                                        System.out.println("Zookeeper session expired...");
                                        close();
                                        break;
                                }
                        }
                    };
                    zooKeeper = (sessionState == null)
                            ? new ZooKeeper(connectString, sessionTimeout, watcher)
                            : new ZooKeeper(connectString, sessionTimeout, watcher, sessionState.sessionId, sessionState.sessionPassword);
                    if (timeout > 0) {
                        if (!connected.await(timeout, timeUnit)) {
                            throw new TimeoutException("Timed out waiting for a ZooKeeper connection...");
                        }
                    } else {
                        connected.await();
                    }
                    sessionState = new SessionState(zooKeeper.getSessionId(), zooKeeper.getSessionPasswd());
                }
            }
        }
        return zooKeeper;
    }

    /**
     * 关闭Zookeeper客户端
     */
    public void close() {
        //双重锁校验关闭连接
        if (zooKeeper != null) {
            synchronized (this) {
                if (zooKeeper != null) {
                    try {
                        zooKeeper.close();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        zooKeeper = null;
                        sessionState = null;
                    }
                }
            }
        }
    }
}
