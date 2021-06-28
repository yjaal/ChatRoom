package net.qiujuer.library.clink.core.schedule;

import java.util.concurrent.TimeUnit;
import net.qiujuer.library.clink.core.Connector;
import net.qiujuer.library.clink.core.ScheduleJob;

/**
 * 空闲调度定时任务
 *
 * @author YJ
 * @date 2021/6/28
 **/
public class IdleTimeoutScheduleJob extends ScheduleJob {

    public IdleTimeoutScheduleJob(long idleTimeout, TimeUnit unit, Connector connector) {
        super(idleTimeout, unit, connector);
    }

    @Override
    public void run() {
        long lastActiveTime = connector.getLastActiveTime();
        long idleTimeoutMilliseconds = this.idleTimeoutMilliseconds;
        long nextDelay = idleTimeoutMilliseconds - (System.currentTimeMillis() - lastActiveTime);

        if (nextDelay <= 0) {
            // 已超时则重新调度
            schedule(idleTimeoutMilliseconds);
            try {
                // 告知Connector已超时
                connector.fireIdleTimeoutEvent();
            } catch (Throwable e) {
                connector.fireExceptionCaught(e);
            }

        } else {
            schedule(nextDelay);
        }
    }
}
