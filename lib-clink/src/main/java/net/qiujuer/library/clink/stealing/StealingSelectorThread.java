package net.qiujuer.library.clink.stealing;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import net.qiujuer.library.clink.core.IoProvider;
import net.qiujuer.library.clink.utils.CloseUtils;

/**
 * 可窃取任务的线程
 *
 * @author YJ
 * @date 2021/7/13
 **/
public abstract class StealingSelectorThread extends Thread {

    /**
     * 允许的操作
     */
    private static final int VALID_OPS = SelectionKey.OP_READ | SelectionKey.OP_WRITE;
    private Selector selector;

    /**
     * 是否还处于运行中
     */
    private volatile boolean isRunning = true;

    /**
     * 已就绪任务队列
     */
    private final LinkedBlockingQueue<IoTask> readyTaskQueue = new LinkedBlockingQueue<>();

    /**
     * 待注册的任务队列
     */
    private final LinkedBlockingQueue<IoTask> registerTaskQueue = new LinkedBlockingQueue<>();

    /**
     * 单次就绪的任务缓存，随后一次性加入到就绪队列中
     */
    private final List<IoTask> onceReadyTaskCache = new ArrayList<>(200);
    /**
     * 饱和度量
     */
    private AtomicLong saturatingCapacity = new AtomicLong();

    /**
     * 用于多线程协同的service
     */
    private volatile StealingService stealingService;

    public StealingSelectorThread(Selector selector) {
        this.selector = selector;
    }

    /**
     * 将通道注册到当前到Selector中
     */
    public boolean register(SocketChannel channel, int ops,
        IoProvider.HandleProviderCallback callback) {
        if (channel.isOpen()) {
            IoTask ioTask = new IoTask(channel, ops, callback);
            registerTaskQueue.offer(ioTask);
            return true;
        }
        return false;
    }

    /**
     * 取消注册，原理类似于注册操作在对列中添加一份取消注册到任务并将副本变量清空
     */
    public void unRegister(SocketChannel channel) {
        SelectionKey selectionKey = channel.keyFor(selector);
        if (selectionKey != null && selectionKey.attachment() != null) {
            // 关闭前可使用Attach简单判断是否已处于队列中
            selectionKey.attach(null);
            // 添加取消操作
            IoTask ioTask = new IoTask(channel, 0, null);
            registerTaskQueue.offer(ioTask);
        }
    }

    /**
     * 消费当前待注册的通道任务
     *
     * @param readyTaskQueue 待注册待通道
     */
    private void consumeRegisterTodoTasks(final LinkedBlockingQueue<IoTask> readyTaskQueue) {
        final Selector selector = this.selector;
        IoTask registerTask = registerTaskQueue.poll();
        while (registerTask != null) {
            try {
                final SocketChannel channel = registerTask.channel;
                int ops = registerTask.ops;
                if (ops == 0) {
                    // Cancel
                    SelectionKey key = channel.keyFor(selector);
                    if (key != null) {
                        key.cancel();
                    }
                } else if ((ops & ~VALID_OPS) == 0) {
                    SelectionKey key = channel.keyFor(selector);
                    if (key == null) {
                        // 为空表明需要注册
                        key = channel.register(selector, ops, new KeyAttachment());
                    } else {
                        key.interestOps(key.interestOps() | ops);
                    }

                    Object attachment = key.attachment();
                    if (attachment instanceof KeyAttachment) {
                        ((KeyAttachment) attachment).attach(ops, registerTask);
                    } else {
                        // 外部关闭，直接取消
                        key.cancel();
                    }
                }
            } catch (ClosedChannelException | CancelledKeyException | ClosedSelectorException e) {
            } finally {
                registerTask = registerTaskQueue.poll();
            }
        }
    }

    /**
     * 将单次就绪的任务缓存加入到总队列中
     */
    private void joinTaskQueue(final LinkedBlockingQueue<IoTask> readyTaskQueue,
        final List<IoTask> onceReadyTaskCache) {
        readyTaskQueue.addAll(onceReadyTaskCache);
    }

    /**
     * 消费待完成的任务
     */
    private void consumeTodoTasks(final LinkedBlockingQueue<IoTask> readyTaskQueue,
        LinkedBlockingQueue<IoTask> registerTaskQueue) {
        final AtomicLong saturatingCapacity = this.saturatingCapacity;
        // 循环把所有任务做完
        IoTask doTask = readyTaskQueue.poll();
        while (doTask != null) {
            saturatingCapacity.incrementAndGet();
            // 做任务
            if (processTask(doTask)) {
                // 做完工作后添加待注册的列表
                registerTaskQueue.offer(doTask);
            }
            // 下一个任务
            doTask = readyTaskQueue.poll();
        }
        // 上面就是把自己的任务先做完，然后窃取其他任务
        final StealingService stealingService = this.stealingService;
        if (stealingService != null) {
            doTask = stealingService.steal(readyTaskQueue);
            while (doTask != null) {
                saturatingCapacity.incrementAndGet();
                if (processTask(doTask)) {
                    registerTaskQueue.offer(doTask);
                }
                doTask = stealingService.steal(readyTaskQueue);
            }
        }
    }

    @Override
    public void run() {
        super.run();

        final Selector selector = this.selector;
        final LinkedBlockingQueue<IoTask> readyTaskQueue = this.readyTaskQueue;
        final LinkedBlockingQueue<IoTask> registerTaskQueue = this.registerTaskQueue;
        final List<IoTask> onceReadyTaskCache = this.onceReadyTaskCache;

        try {
            while (isRunning) {
                // 加入待注册列表的通道
                consumeRegisterTodoTasks(registerTaskQueue);

                // 检查一次
                if (selector.selectNow() == 0) {
                    Thread.yield();
                    continue;
                }
                // 处理已就绪的通道
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();
                //迭代已就绪的任务
                while (iterator.hasNext()) {
                    SelectionKey selectionKey = iterator.next();
                    Object attachmentObj = selectionKey.attachment();
                    // 检查有效性
                    if (selectionKey.isValid() && attachmentObj instanceof KeyAttachment) {
                        final KeyAttachment attachment = (KeyAttachment) attachmentObj;
                        try {
                            final int readyOps = selectionKey.readyOps();
                            int interestOps = selectionKey.interestOps();

                            // 是否可读
                            if ((readyOps & SelectionKey.OP_READ) != 0) {
                                onceReadyTaskCache.add(attachment.task4Readable);
                                interestOps = interestOps & ~SelectionKey.OP_READ;
                            }

                            // 是否可写
                            if ((readyOps & SelectionKey.OP_WRITE) != 0) {
                                onceReadyTaskCache.add(attachment.task4Readable);
                                interestOps = interestOps & ~SelectionKey.OP_WRITE;
                            }

                            // 取消已就绪的关注
                            selectionKey.interestOps(interestOps);
                        } catch (CancelledKeyException e) {
                            // 当前连接被取消、断开时直接移除相关任务
                            onceReadyTaskCache.remove(attachment.task4Readable);
                            onceReadyTaskCache.remove(attachment.task4Writable);
                        }
                    }
                    iterator.remove();
                }
                // 判断本次是否有待执行的任务
                if (!onceReadyTaskCache.isEmpty()) {
                    // 加入到总队列中
                    joinTaskQueue(readyTaskQueue, onceReadyTaskCache);
                    onceReadyTaskCache.clear();
                }

                // 消费总队列中的任务
                consumeTodoTasks(readyTaskQueue, registerTaskQueue);
            }
        } catch (ClosedSelectorException e1) {

        } catch (IOException e) {
            CloseUtils.close(selector);
        } finally {
            readyTaskQueue.clear();
            registerTaskQueue.clear();
            onceReadyTaskCache.clear();
        }
    }

    public void exit() {
        isRunning = false;
        CloseUtils.close(selector);
        interrupt();
    }

    public LinkedBlockingQueue<IoTask> getReadyTaskQueue() {
        return readyTaskQueue;
    }


    /**
     * 获取饱和度，暂时的饱和度量是使用任务执行的次数来定
     */
    public long getSaturatingCapacity() {
        if (selector.isOpen()) {
            return saturatingCapacity.get();
        }

        return -1;
    }

    /**
     * @param task 任务
     * @return 执行任务后是否需要再次添加该任务
     */
    protected abstract boolean processTask(IoTask task);

    public void setStealingService(StealingService stealingService){
        this.stealingService = stealingService;
    }

    /**
     * 用以注册时添加的附件
     */
    static class KeyAttachment {

        // 可读时执行的任务
        IoTask task4Readable;
        // 可写时执行的任务
        IoTask task4Writable;

        void attach(int ops, IoTask task) {
            if (ops == SelectionKey.OP_READ) {
                task4Readable = task;
            } else {
                task4Writable = task;
            }
        }
    }

}
