package net.qiujuer.library.clink.impl;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import net.qiujuer.library.clink.core.IoProvider;
import net.qiujuer.library.clink.utils.CloseUtils;

/**
 * IO服务提供方包括注册输入、输出管道，解除注册，关闭等操作
 */
public class IoSelectorProvider implements IoProvider {

    /**
     * 针对这个通道的锁
     */
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    /**
     * 是否处于某个过程判定锁，这里是针对selector的锁
     */
    private final AtomicBoolean inRegInput = new AtomicBoolean(false);
    private final AtomicBoolean inRegOutput = new AtomicBoolean(false);

    private final Selector readSelector;
    private final Selector writeSelector;

    private final HashMap<SelectionKey, Runnable> inputCallbackMap = new HashMap<>();
    private final HashMap<SelectionKey, Runnable> outputCallbackMap = new HashMap<>();

    private final ExecutorService inputHandlePool;
    private final ExecutorService outputHandlePool;

    public IoSelectorProvider() throws IOException {
        readSelector = Selector.open();
        writeSelector = Selector.open();

        inputHandlePool = Executors.newFixedThreadPool(4,
            new NameableThreadFactory("IoProvider-Input-Thread-"));
        outputHandlePool = Executors.newFixedThreadPool(4,
            new NameableThreadFactory("IoProvider-Output-Thread-"));

        // 开始输出输入的监听，启动读写，后面直接从map中获取相关线程进行执行
        startRead();
        startWrite();
    }

    /**
     * 这里可以看到我们使用一个单线程处理具体的管道开关获取，然后使用线程池处理具体的读操作，相当于启动线程开始监听
     */
    private void startRead() {
        Thread thread = new SelectorThread("Clink IoSelectorProvider ReadSelector Thread",
            inRegInput, readSelector, inputCallbackMap, inputHandlePool, isClosed,
            SelectionKey.OP_READ);
        thread.start();
    }

    private void startWrite() {
        Thread thread = new SelectorThread("Clink IoSelectorProvider WriteSelector Thread",
            inRegOutput, writeSelector, outputCallbackMap, outputHandlePool, isClosed,
            SelectionKey.OP_WRITE);
        thread.start();
    }

    @Override
    public boolean registerInput(SocketChannel channel, HandleProviderCallback callback) {
        // 这里首先要进行注册
        return registerSelection(channel, readSelector, SelectionKey.OP_READ, inRegInput,
            inputCallbackMap, callback) != null;
    }

    @Override
    public boolean registerOutput(SocketChannel channel, HandleProviderCallback callback) {
        // 这里首先要进行注册
        return registerSelection(channel, writeSelector, SelectionKey.OP_WRITE, inRegOutput,
            outputCallbackMap, callback) != null;
    }

    @Override
    public void unRegisterInput(SocketChannel channel) {
        unRegisterSelection(channel, readSelector, inputCallbackMap, inRegInput);
    }

    @Override
    public void unRegisterOutput(SocketChannel channel) {
        unRegisterSelection(channel, writeSelector, outputCallbackMap, inRegOutput);
    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            inputHandlePool.shutdown();
            outputHandlePool.shutdown();
            inputCallbackMap.clear();
            outputCallbackMap.clear();

            // Selector在调用close对时候会自动进行唤醒操作
            CloseUtils.close(readSelector, writeSelector);
        }
    }

    /**
     * 等待注册完成
     */
    private static void waitSelection(final AtomicBoolean locker) {
        synchronized (locker) {
            // 如果是锁定状态，则需要等待
            if (locker.get()) {
                try {
                    locker.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 通道的select、wakeup、register都需要获取到锁，而这里只是使用了一个locker进行控制，这对于多个线程来说是比较
     * <p>耗时的，而每次要发送数据都是先唤醒、注册，然后在获取到锁，然后将回调丢到线程池中进行处理，这样导致线程间切换
     * <p>消耗很大，导致性能较差，而上面我们在首次发送到时候先直接进行发送，如果发送遇到问题会进行注册，而不是一上来
     * <p>就进行注册，这样就会有一定的性能提升，因为一般情况下通道都是准备好了的，是可以直接进行发送的
     * <p>改进后就是将输出作为优先步骤，将注册等步骤放在了后面
     */
    private static SelectionKey registerSelection(SocketChannel channel, Selector selector,
        int regOps, AtomicBoolean locker, HashMap<SelectionKey, Runnable> map, Runnable runnable) {
//        System.out.println("注册相关selector并将回调对象存入缓存，以便线程池执行");
        synchronized (locker) {
            // 设置锁定状态
            locker.set(true);
            try {
                // 唤醒当前的selector，让selector不处于select()被选中的状态，以便下次可以
                // 再次选中
                selector.wakeup();
                SelectionKey key = null;
                if (channel.isRegistered()) {
//                    System.out.println("当前SelectionKey已注册过，这里修改其开关值");
                    // 查询是否已经注册过
                    key = channel.keyFor(selector);
                    // 已经注册过，那么只需要改变监听值就可以达到监听效果，不需要再次注册
                    if (!Objects.isNull(key)) {
                        key.interestOps(key.interestOps() | regOps);
                    }
                }
                // 表明之前没有注册过，这里进行注册
                if (Objects.isNull(key)) {
//                    System.out.println("当前SelectionKey还未注册过，这里进行注册");
                    key = channel.register(selector, regOps);
                    // 注册回调，注意，这里将回调存入了map，后面读取线程池inputHandlePool会从中取出执行
                    map.put(key, runnable);
                }

                return key;
            } catch (ClosedChannelException | CancelledKeyException | ClosedSelectorException e) {
                return null;
            } finally {
                // 接触锁定
                locker.set(false);
                try {
                    // 唤醒一个
                    locker.notify();
                } catch (Exception e) {

                }
            }
        }
    }

    private static void unRegisterSelection(SocketChannel channel, Selector selector,
        HashMap<SelectionKey, Runnable> map, AtomicBoolean locker) {
        synchronized (locker) {
            // 在selector遍历SelectionKey集合的时候可能有其他线程正在对集合做变更，此时应该
            // locker设置为true，表明其他线程需要等待，同时唤醒Selector让其立即返回
            locker.set(true);
            selector.wakeup();
            try {
                if (channel.isRegistered()) {
                    SelectionKey key = channel.keyFor(selector);
                    if (!Objects.isNull(key)) {
                        // 取消监听的方法，这里由于将读写操作分开了，所以这种取消方式也是可行的
                        // 否则必须用下面的那种方法，因为cancel是取消所有事件
                        key.cancel();
                        map.remove(key);
                    }
                }
            } finally {
                locker.set(false);
                try {
                    locker.notifyAll();
                } catch (Exception ignored) {

                }
            }
        }
    }

    /**
     * 处理具体的读操作
     *
     * @param key             开关
     * @param op              开关读写标志
     * @param callbackMap     回调map
     * @param inputHandlePool 读操作线程池
     */
    private static void handleSelection(SelectionKey key, int op,
        HashMap<SelectionKey, Runnable> callbackMap, ExecutorService inputHandlePool,
        AtomicBoolean locker) {
        synchronized (locker) {
            // 重点：这里通过状态互消取消当前selector的监听
            // 或者用key.cancel()
            try {
                // 在对key做变更对时候可能key已经被取消关闭了
                key.interestOps(key.interestOps() & ~op);
            } catch (CancelledKeyException e) {
                return;
            }
        }
        Runnable runnable = callbackMap.get(key);
        if (!Objects.isNull(runnable) && !inputHandlePool.isShutdown()) {
            // 线程池异步调度
            inputHandlePool.execute(runnable);
        }
    }

    static class SelectorThread extends Thread {

        private final AtomicBoolean locker;
        private final Selector selector;
        private final HashMap<SelectionKey, Runnable> callbackMap;
        private final ExecutorService pool;
        private final AtomicBoolean isClosed;
        private final int keyOps;

        public SelectorThread(String name, AtomicBoolean locker, Selector selector,
            HashMap<SelectionKey, Runnable> callbackMap, ExecutorService pool,
            AtomicBoolean isClosed, int keyOps) {
            super(name);
            this.locker = locker;
            this.selector = selector;
            this.callbackMap = callbackMap;
            this.pool = pool;
            this.isClosed = isClosed;
            this.keyOps = keyOps;
        }

        @Override
        public void run() {
            super.run();
            AtomicBoolean locker = this.locker;
            AtomicBoolean isClosed = this.isClosed;
            Selector selector = this.selector;
            int keyOps = this.keyOps;
            while (!isClosed.get()) {
                try {
                    if (selector.select() == 0) {
                        waitSelection(locker);
                        continue;
                    } else if (locker.get()) {
                        // 等待当前的操作执行完再继续
                        waitSelection(locker);
                    }
                    // 这里SelectionKey可能被取消了，所以使用迭代器
                    Set<SelectionKey> selectionKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iter = selectionKeys.iterator();
                    while (iter.hasNext()) {
                        SelectionKey selectionKey = iter.next();
                        if (selectionKey.isValid()) {
                            handleSelection(selectionKey, keyOps, callbackMap, pool, locker);
                        }
                        iter.remove();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ClosedSelectorException e) {
                    // Selector被关闭时做其他操作会抛出此异常，此时退出循环
                    break;
                }
            }
        }
    }
}
