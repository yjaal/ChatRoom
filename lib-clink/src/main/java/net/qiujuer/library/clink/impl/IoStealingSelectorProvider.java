package net.qiujuer.library.clink.impl;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import net.qiujuer.library.clink.core.IoProvider;
import net.qiujuer.library.clink.stealing.IoTask;
import net.qiujuer.library.clink.stealing.StealingSelectorThread;
import net.qiujuer.library.clink.stealing.StealingService;

/**
 * 可窃取任务的IoProvider
 *
 * @author YJ
 * @date 2021/7/13
 **/
public class IoStealingSelectorProvider implements IoProvider {

    private final StealingSelectorThread[] threads;

    private final StealingService stealingService;

    public IoStealingSelectorProvider(int poolSize) throws IOException {
        StealingSelectorThread[] threads = new StealingSelectorThread[poolSize];
        for (int i = 0; i < poolSize; i++) {
            Selector selector = Selector.open();
            threads[i] = new IoStealingThread("IoProvider-Thread-" + (i + 1), selector);
        }

        StealingService stealingService = new StealingService(threads, 10);
        for (StealingSelectorThread thread : threads) {
            thread.setStealingService(stealingService);
            thread.start();
        }

        this.threads = threads;
        this.stealingService = stealingService;
    }

    @Override
    public boolean registerInput(SocketChannel channel, HandleProviderCallback callback) {
        StealingSelectorThread thread = stealingService.getNoBusyThread();
        if (thread != null) {
            // 这里首先要进行注册
            return thread.register(channel, SelectionKey.OP_READ, callback);
        }
        return false;
    }

    @Override
    public boolean registerOutput(SocketChannel channel, HandleProviderCallback callback) {
        StealingSelectorThread thread = stealingService.getNoBusyThread();
        if (thread != null) {
            // 这里首先要进行注册
            return thread.register(channel, SelectionKey.OP_WRITE, callback);
        }
        return false;

    }

    @Override
    public void unRegisterInput(SocketChannel channel) {
        for (StealingSelectorThread thread : threads) {
            thread.unRegister(channel);
        }
    }

    @Override
    public void unRegisterOutput(SocketChannel channel) {
    }

    public void close() {
        stealingService.shutdown();
    }

    static class IoStealingThread extends StealingSelectorThread {

        IoStealingThread(String name, Selector selector) {
            super(selector);
            setName(name);
        }

        @Override
        protected boolean processTask(IoTask task) {
            task.providerCallback.run();
            return false;
        }
    }
}
