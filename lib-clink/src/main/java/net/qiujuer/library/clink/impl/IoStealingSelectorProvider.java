package net.qiujuer.library.clink.impl;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import net.qiujuer.library.clink.core.IoProvider;
import net.qiujuer.library.clink.stealing.IoTask;
import net.qiujuer.library.clink.stealing.StealingSelectorThread;

/**
 * 可窃取任务的IoProvider
 *
 * @author YJ
 * @date 2021/7/13
 **/
public  class IoStealingSelectorProvider implements IoProvider {

    private final StealingSelectorThread thread;

    public IoStealingSelectorProvider(int poolSize) throws IOException {
        Selector selector = Selector.open();
        thread = new StealingSelectorThread(selector) {

            @Override
            protected boolean processTask(IoTask task) {
                task.providerCallback.run();
                return false;
            }
        };
        thread.start();
    }

    @Override
    public boolean registerInput(SocketChannel channel, HandleProviderCallback callback) {
        // 这里首先要进行注册
        return thread.register(channel, SelectionKey.OP_READ, callback);
    }

    @Override
    public boolean registerOutput(SocketChannel channel, HandleProviderCallback callback) {
        // 这里首先要进行注册
        return thread.register(channel, SelectionKey.OP_WRITE, callback);
    }

    @Override
    public void unRegisterInput(SocketChannel channel) {
        thread.unRegister(channel);
    }

    @Override
    public void unRegisterOutput(SocketChannel channel) {
    }

    public void close() {
        thread.exit();
    }
}
