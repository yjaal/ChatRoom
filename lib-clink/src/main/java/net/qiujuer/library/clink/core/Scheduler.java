package net.qiujuer.library.clink.core;

import java.io.Closeable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 定时任务，检测心跳
 */
public interface Scheduler<T> extends Closeable {

    /**
     * 调度方法，可以传入延时时间，同时返回一个回调类
     */
    ScheduledFuture<T> schedule(Runnable runnable, long delay, TimeUnit unit);
}
