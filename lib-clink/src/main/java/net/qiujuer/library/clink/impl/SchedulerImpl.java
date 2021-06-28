package net.qiujuer.library.clink.impl;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.qiujuer.library.clink.core.Scheduler;
import net.qiujuer.library.clink.impl.IoSelectorProvider.IoProviderThreadFactory;

/**
 * 调度器实现
 *
 * @author YJ
 * @date 2021/6/28
 **/
public class SchedulerImpl implements Scheduler {

    private final ScheduledExecutorService pool;

    public SchedulerImpl(int poolSize) {
        this.pool = Executors.newScheduledThreadPool(poolSize,
            new IoProviderThreadFactory("Scheduler-Thread-"));
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable runnable, long delay, TimeUnit unit) {
        return pool.schedule(runnable, delay, unit);
    }

    @Override
    public void close() throws IOException {
        pool.shutdownNow();
    }
}
