# DistributedLock
基于数据库、Redis、Zookeeper的分布式锁，目前只支持Zookeeper。
<hr />
注意：<br />
1、分布式锁一个系统只能获取一次，多次获取将抛出LockException，系统内请使用JDK的锁（JUC包）。<br />
2、获取、释放锁可能会失败，需要开发者自己去实现重试机制。<br />
<span><font color="#FF0000">危险：由于网络等原因，释放锁可能会失败（抛出运行时异常），开发者自己去实现重试机制。</font></span><br />
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
            e.printStackTrace();
        }
    }
</pre>
