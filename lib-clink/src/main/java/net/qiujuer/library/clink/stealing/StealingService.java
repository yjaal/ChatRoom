package net.qiujuer.library.clink.stealing;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.IntFunction;

/**
 * 窃取调度服务
 *
 * @author YJ
 * @date 2021/7/15
 **/
public class StealingService {

    /**
     * 当任务队列数量低于安全值时，不可窃取
     */
    private final int minSafetyThreshold;

    /**
     * 线程集合
     */
    private final StealingSelectorThread[] threads;

    /**
     * 对应的任务对垒
     */
    private final Queue<IoTask>[] queues;

    /**
     * 结束标志
     */
    private volatile boolean isTerminated = false;

    public StealingService(StealingSelectorThread[] threads, int minSafetyThreshold) {
        this.threads = threads;
        this.minSafetyThreshold = minSafetyThreshold;
        this.queues = Arrays.stream(threads)
            .map(StealingSelectorThread::getReadyTaskQueue)
            .toArray((IntFunction<Queue<IoTask>[]>) ArrayBlockingQueue[]::new);
    }

    /**
     * 窃取一个任务，排除自己，从他人队列窃取一个任务
     */
    IoTask steal(final Queue<IoTask> excludeQueue) {
        final int minSafetyThreshold = this.minSafetyThreshold;
        final Queue<IoTask>[] queues = this.queues;
        for (Queue<IoTask> queue : queues) {
            if (queue == excludeQueue) {
                continue;
            }
            int size = queue.size();
            if (size > minSafetyThreshold) {
                IoTask poll = queue.poll();
                if (poll != null) {
                    return poll;
                }
            }
        }
        return null;
    }

    /**
     * 获取一个不繁忙的线程
     */
    public StealingSelectorThread getNoBusyThread() {
        StealingSelectorThread targetThread = null;
        long targetKeyCount = Long.MAX_VALUE;
        for (StealingSelectorThread thread : threads) {
            long saturatingCapacity = thread.getSaturatingCapacity();
            if (saturatingCapacity != -1 && saturatingCapacity < targetKeyCount) {
                targetKeyCount = saturatingCapacity;
                targetThread = thread;
            }
        }
        return targetThread;
    }

    /**
     * 执行任务
     */
    public void execute(IoTask task) {

    }

    /**
     * 是否已结束
     */
    public boolean isTerminated() {
        return isTerminated;
    }

    /**
     * 结束操作
     */
    public void shutdown() {
        if (isTerminated) {
            return;
        }
        isTerminated = true;
        for (StealingSelectorThread thread : threads) {
            thread.exit();
        }
    }
}
