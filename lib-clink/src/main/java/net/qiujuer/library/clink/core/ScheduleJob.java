package net.qiujuer.library.clink.core;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 定时任务执行
 *
 * @author YJ
 * @date 2021/6/28
 **/
public abstract class ScheduleJob implements Runnable {

    protected final long idleTimeoutMilliseconds;

    protected final Connector connector;

    private volatile Scheduler scheduler;

    private volatile ScheduledFuture scheduledFuture;

    public ScheduleJob(long idleTimeout, TimeUnit unit, Connector connector) {
        this.idleTimeoutMilliseconds = unit.toMillis(idleTimeout);
        this.connector = connector;
    }

    synchronized void schedule(Scheduler scheduler) {
        this.scheduler = scheduler;
        this.schedule(idleTimeoutMilliseconds);
    }

    protected synchronized void schedule(long idleTimeoutMilliseconds) {
        if (scheduler != null) {
            this.scheduledFuture = scheduler.schedule(this, idleTimeoutMilliseconds,
                TimeUnit.MILLISECONDS);
        }
    }

    synchronized void unSchedule() {
        if (scheduler != null) {
            scheduler = null;
        }
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
            scheduledFuture = null;
        }
    }
}
