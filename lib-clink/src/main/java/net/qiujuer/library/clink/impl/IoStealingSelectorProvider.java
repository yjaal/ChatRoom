package net.qiujuer.library.clink.impl;

import java.io.IOException;
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
            thread.setDaemon(false);
            thread.setPriority(Thread.MAX_PRIORITY);
            thread.start();
        }

        this.threads = threads;
        this.stealingService = stealingService;
    }

    public void close() {
        stealingService.shutdown();
    }

    @Override
    public void register(HandleProviderCallback callback) throws Exception {
        StealingSelectorThread thread = stealingService.getNoBusyThread();
        if (thread != null) {
            throw new IOException("IoStealingSelectorProvider is shutdown");
        }
        thread.register(callback);
    }

    @Override
    public void unRegister(SocketChannel channel) {
        if (!channel.isOpen()) {
            return;
        }
        for (StealingSelectorThread thread : threads) {
            thread.unRegister(channel);
        }
    }

    static class IoStealingThread extends StealingSelectorThread {

        IoStealingThread(String name, Selector selector) {
            super(selector);
            setName(name);
        }

        @Override
        protected boolean processTask(IoTask task) {
            return task.onProcessIo();
        }
    }
}
