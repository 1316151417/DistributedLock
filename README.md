# DistributedLock
基于数据库、Redis、Zookeeper的分布式锁，目前只支持Zookeeper。
<hr />
注意：<br />
1、分布式锁只能获取一次，多次获取将抛出异常。<br />
2、由于网络等原因，获取、释放锁可能会失败，失败后将自动释放锁，开发者需要自己去实现重试机制。<br />
<hr />
代码示例：
<br />
<pre>
    ZookeeperClient client = new ZookeeperClient("127.0.0.1:2181", 5000);
    String lockPath = "/lockPath";
    DistributedLock lock = new DistributedLockImpl(client, lockPath);
    try {
            lock.lock();
    } catch (LockException e) {
        e.printStackTrace();
    } finally {
        try {
            lock.unLock();
        } catch (LockException e) {
            throw new RuntimeException(e);
        }
    }
</pre>
