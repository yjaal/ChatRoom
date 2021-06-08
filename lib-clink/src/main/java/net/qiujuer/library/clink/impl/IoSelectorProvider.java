package net.qiujuer.library.clink.impl;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
            new IoProviderThreadFactory("IoProvider-Input-Thread-"));
        outputHandlePool = Executors.newFixedThreadPool(4,
            new IoProviderThreadFactory("IoProvider-Output-Thread-"));

        // 开始输出输入的监听，启动读写，后面直接从map中获取相关线程进行执行
        startRead();
        startWrite();
    }

    /**
     * 这里可以看到我们使用一个单线程处理具体的管道开关获取，然后使用线程池处理具体的读操作，相当于启动线程开始监听
     */
    private void startRead() {
        Thread thread = new Thread("Clink IoSelectorProvider ReadSelector Thread") {
            @Override
            public void run() {
                while (!isClosed.get()) {
                    try {
                        if (readSelector.select() == 0) {
                            waitSelection(inRegInput);
                            continue;
                        }
                        Set<SelectionKey> selectionKeys = readSelector.selectedKeys();
                        for (SelectionKey selectionKey : selectionKeys) {
                            if (selectionKey.isValid()) {
                                handleSelection(selectionKey, SelectionKey.OP_READ, inputCallbackMap, inputHandlePool);
                            }
                        }
                        selectionKeys.clear();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    private void startWrite() {
        Thread thread = new Thread("Clink IoSelectorProvider WriteSelector Thread") {
            @Override
            public void run() {
                while (!isClosed.get()) {
                    try {
                        if (writeSelector.select() == 0) {
                            waitSelection(inRegOutput);
                            continue;
                        }
                        Set<SelectionKey> selectionKeys = writeSelector.selectedKeys();
                        for (SelectionKey selectionKey : selectionKeys) {
                            if (selectionKey.isValid()) {
                                handleSelection(selectionKey, SelectionKey.OP_WRITE, outputCallbackMap, outputHandlePool);
                            }
                        }
                        selectionKeys.clear();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    @Override
    public boolean registerInput(SocketChannel channel, HandleInputCallback callback) {
        // 这里首先要进行注册
        return registerSelection(channel, readSelector, SelectionKey.OP_READ, inRegInput, inputCallbackMap, callback) != null;
    }

    @Override
    public boolean registerOutput(SocketChannel channel, HandleOutputCallback callback) {
        // 这里首先要进行注册
        return registerSelection(channel, writeSelector, SelectionKey.OP_WRITE, inRegOutput,
            outputCallbackMap, callback) != null;
    }

    @Override
    public void unRegisterInput(SocketChannel channel) {
        unRegisterSelection(channel, readSelector, inputCallbackMap);
    }

    @Override
    public void unRegisterOutput(SocketChannel channel) {
        unRegisterSelection(channel, writeSelector, outputCallbackMap);
    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            inputHandlePool.shutdown();
            outputHandlePool.shutdown();
            inputCallbackMap.clear();
            outputCallbackMap.clear();
            readSelector.wakeup();
            writeSelector.wakeup();

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

    private static SelectionKey registerSelection(SocketChannel channel, Selector selector, int regOps,
        AtomicBoolean locker, HashMap<SelectionKey, Runnable> map, Runnable runnable) {

        System.out.println("注册相关selector并将回调对象存入缓存，以便线程池执行");
        synchronized (locker) {
            // 设置锁定状态
            locker.set(true);
            try {
                // 唤醒当前的selector，让selector不处于select()被选中的状态，以便下次可以
                // 再次选中
                selector.wakeup();
                SelectionKey key = null;
                if (channel.isRegistered()) {
                    System.out.println("当前SelectionKey已注册过，这里修改其开关值");
                    // 查询是否已经注册过
                    key = channel.keyFor(selector);
                    // 已经注册过，那么只需要改变监听值就可以达到监听效果，不需要再次注册
                    if (!Objects.isNull(key)) {
                        key.interestOps(key.readyOps() | regOps);
                    }
                }
                // 表明之前没有注册过，这里进行注册
                if (Objects.isNull(key)) {
                    System.out.println("当前SelectionKey还未注册过，这里进行注册");
                    key = channel.register(selector, regOps);
                    // 注册回调，注意，这里将回调存入了map，后面读取线程池inputHandlePool会从中取出执行
                    map.put(key, runnable);
                }

                return key;
            } catch (ClosedChannelException e) {
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
        HashMap<SelectionKey, Runnable> map) {
        if (channel.isRegistered()) {
            SelectionKey key = channel.keyFor(selector);
            if (!Objects.isNull(key)) {
                // 取消监听的方法，这里由于将读写操作分开了，所以这种取消方式也是可行的
                // 否则必须用下面的那种方法，因为cancel是取消所有事件
                key.cancel();
                map.remove(key);
                selector.wakeup();
            }
        }
    }

    /**
     * 处理具体的读操作
     *
     * @param key              开关
     * @param opRead           开关读写标志
     * @param inputCallbackMap 回调map
     * @param inputHandlePool  读操作线程池
     */
    private void handleSelection(SelectionKey key, int opRead, HashMap<SelectionKey, Runnable> inputCallbackMap,
        ExecutorService inputHandlePool) {
        // 重点：这里通过状态互消取消当前selector的监听
        // 或者用key.cancel()
        key.interestOps(key.readyOps() & ~opRead);
        Runnable runnable = inputCallbackMap.get(key);
        if (!Objects.isNull(runnable) && !inputHandlePool.isShutdown()) {
            // 线程池异步调度
            inputHandlePool.execute(runnable);
        }
    }

    static class IoProviderThreadFactory implements ThreadFactory {

        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        IoProviderThreadFactory(String namePrefix) {
            SecurityManager s = System.getSecurityManager();
            this.group = (s != null) ? s.getThreadGroup() :
                Thread.currentThread().getThreadGroup();
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r,
                namePrefix + threadNumber.getAndIncrement(),
                0);
            if (t.isDaemon()) {
                t.setDaemon(false);
            }
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }
}