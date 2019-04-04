package com.tal.zj.distributedLock.zookeeper;

import com.tal.zj.distributedLock.DistributedLock;
import com.tal.zj.distributedLock.exception.LockException;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.Stat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @Author ZhouJie
 * @Date 2019/4/4 18:06
 * @Description 分布式锁（基于zookeeper）
 */
public class DistributedLockImpl implements DistributedLock {

    private final ZookeeperClient client;
    private final String lockPath;

    /**
     * 线程不安全的分布式锁
     * @param client Zookeeper客户端
     * @param lockPath 锁路径
     */
    public DistributedLockImpl(ZookeeperClient client, String lockPath) {
        this.client = client;
        this.lockPath = lockPath;
        syncPoint = new CountDownLatch(1);
    }

    private CountDownLatch syncPoint;
    private boolean holdsLock;
    private String currentNode;
    private String currentNodePath;
    private String watchedNodePath;
    private LockWatcher watcher;

    @Override
    public synchronized void lock() throws LockException {
        if (holdsLock) {
            throw new LockException("Error, already holding a lock. Call unlock first!");
        }
        try {
            prepare();
            watcher.checkForLock();
            syncPoint.await();
            if (!holdsLock) {
                throw new LockException("Error, couldn't acquire the lock!");
            }
        } catch (InterruptedException ie){
            cancelAttempt();
            throw new LockException("Error, thread got interrupted!");
        } catch (Exception e) {
            cancelAttempt();
            throw new LockException(e);
        }
    }

    @Override
    public synchronized boolean tryLock(long timeout, TimeUnit unit) throws LockException {
        if (holdsLock) {
            throw new LockException("Error, already holding a lock. Call unlock first!");
        }
        try {
            prepare();
            watcher.checkForLock();
            boolean success = syncPoint.await(timeout, unit);
            if (!success) {
                return false;
            }
            if (!holdsLock) {
                throw new LockException("Error, couldn't acquire the lock!");
            }
        } catch (InterruptedException ie){
            cancelAttempt();
            throw new LockException("Error, thread got interrupted!");
        } catch (Exception e) {
            cancelAttempt();
            throw new LockException(e);
        }
        return true;
    }

    @Override
    public synchronized void unLock() throws LockException {
        if (!holdsLock || currentNodePath == null) {
            throw new LockException("Error, neither attempting to lock nor holding a lock!");
        }
        cleanup();
    }

    private void prepare() throws Exception {
        //确保父节点被创建
        Stat exists = client.get().exists(lockPath, false);
        if (exists == null) {
            //防止并发创建，异常捕获
            try {
                client.get().create(lockPath, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            } catch (Exception e) {
                System.out.println("Node existed when trying to ensure path.");
            }
        }
        //创建临时有序节点
        currentNodePath = client.get().create(lockPath + "/member_", null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
        currentNode = currentNodePath.substring(currentNodePath.lastIndexOf("/") + 1);
        //初始化参数
        syncPoint = new CountDownLatch(1);
        watcher = new LockWatcher();
    }

    private synchronized void cleanup() {
        if (currentNodePath != null) {
            //删除当前的临时有序节点
            try {
                Stat exists = client.get().exists(currentNodePath, false);
                if (exists != null) {
                    client.get().delete(currentNodePath, -1);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        //初始化参数
        syncPoint = new CountDownLatch(1);
        holdsLock = false;
        currentNode = null;
        currentNodePath = null;
        watchedNodePath = null;
        watcher = null;
    }

    private synchronized void cancelAttempt() {
        cleanup();
        syncPoint.countDown();
    }

    private class LockWatcher implements Watcher {

        private synchronized void checkForLock() {
            try {
                //获取父节点的所有子节点
                List<String> children = client.get().getChildren(lockPath, null);
                if (children == null || children.isEmpty()) {
                    throw new LockException("Error, member list is empty!");
                }
                //子节点排序
                children.sort(String::compareTo);
                //获取当前节点的顺序
                int index = children.indexOf(currentNode);
                if (index == 0) {
                    //若为第一个，则获取锁成功
                    holdsLock = true;
                    syncPoint.countDown();
                } else {
                    //若不为第一个，则监视上一个节点的删除事件
                    watchedNodePath = lockPath + "/" + children.get(index - 1);
                    Stat watchedNodeStat = client.get().exists(watchedNodePath, this);
                    if (watchedNodeStat == null) {
                        checkForLock();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                cancelAttempt();
            }
        }

        @Override
        public void process(WatchedEvent event) {
            if (!event.getPath().equals(watchedNodePath)) {
                return;
            }
            if (event.getType() == Event.EventType.None) {
                switch (event.getState()) {
                    case SyncConnected:
                        break;
                    case Expired:
                        cancelAttempt();
                        break;
                }
            } else if (event.getType() == Event.EventType.NodeDeleted) {
                checkForLock();
            } else {
                System.out.println(String.format("Unexpected ZK event: %s", event.getType().name()));
            }
        }
    }
}
