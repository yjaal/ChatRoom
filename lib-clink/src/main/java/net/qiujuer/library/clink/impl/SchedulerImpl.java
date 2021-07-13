package net.qiujuer.library.clink.impl;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.qiujuer.library.clink.core.Scheduler;

/**
 * 调度器实现
 *
 * @author YJ
 * @date 2021/6/28
 **/
public class SchedulerImpl implements Scheduler {

    /**
     * 定时任务，可以重复执行，同时可以随时更改执行时间
     */
    private final ScheduledExecutorService pool;

    private final ExecutorService deliveryPool;

    public SchedulerImpl(int poolSize) {
        this.pool = Executors.newScheduledThreadPool(poolSize,
            new NameableThreadFactory("Scheduler-Thread-"));
        this.deliveryPool = Executors.newFixedThreadPool(4,
            new NameableThreadFactory("Delivery-Thread-"));
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable runnable, long delay, TimeUnit unit) {
        return pool.schedule(runnable, delay, unit);
    }

    /**
     * 这里只是一个简单的消息调度转发
     */
    @Override
    public void delivery(Runnable runnable) {
        deliveryPool.execute(runnable);
    }

    @Override
    public void close() throws IOException {
        pool.shutdownNow();
        deliveryPool.shutdownNow();
    }
}
