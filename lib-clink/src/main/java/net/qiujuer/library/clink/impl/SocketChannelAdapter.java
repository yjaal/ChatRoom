package net.qiujuer.library.clink.impl;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.IoArgs.IOArgsEventListner;
import net.qiujuer.library.clink.core.IoProvider;
import net.qiujuer.library.clink.core.IoProvider.HandleInputCallback;
import net.qiujuer.library.clink.core.IoProvider.HandleOutputCallback;
import net.qiujuer.library.clink.core.Receiver;
import net.qiujuer.library.clink.core.Sender;
import net.qiujuer.library.clink.utils.CloseUtils;
import sun.reflect.generics.scope.Scope;

/**
 * channel拥有发送、接收和关闭的功能
 */
public class SocketChannelAdapter implements Sender, Receiver, Closeable {

    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    private final SocketChannel channel;
    private final IoProvider ioProvider;
    /**
     * 监听通道关闭，进行回调
     */
    private final OnChannelStatusChangedListener listener;

    private IOArgsEventListner receiverIoEventListener;
    private IOArgsEventListner sendIoEventListener;

    public SocketChannelAdapter(SocketChannel channel, IoProvider ioProvider,
        OnChannelStatusChangedListener listener) throws IOException {
        this.channel = channel;
        this.ioProvider = ioProvider;
        this.listener = listener;

        channel.configureBlocking(false);
    }

    @Override
    public boolean receiveAsync(IOArgsEventListner listner) throws IOException {
        if (isClosed.get()) {
            throw new IOException("当前通道已关闭");
        }
        receiverIoEventListener = listner;
        return ioProvider.registerInput(channel, inputCallback);
    }

    @Override
    public boolean sendAsync(IoArgs args, IOArgsEventListner listner) throws IOException {
        if (isClosed.get()) {
            throw new IOException("当前通道已关闭");
        }
        sendIoEventListener = listner;
        // 将需要发送的内容设置进去
        outputCallback.setAttach(args);
        return ioProvider.registerOutput(channel, outputCallback);
    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            ioProvider.unRegisterInput(channel);
            ioProvider.unRegisterOutput(channel);
            CloseUtils.close(channel);
            listener.onChannelClosed(channel);
        }
    }

    private final IoProvider.HandleInputCallback inputCallback = new HandleInputCallback() {
        @Override
        protected void canProviderInput() {
            if (!isClosed.get()) {
                return;
            }
            IoArgs args = new IoArgs();
            IoArgs.IOArgsEventListner listner = SocketChannelAdapter.this.receiverIoEventListener;
            if (!Objects.isNull(listner)) {
                listner.onStarted(args);
            }
            // 具体的读取操作
            try {
                // 有读取到值
                if (args.read(channel) > 0 && !Objects.isNull(listner)) {
                    // 读取后进行回调
                    listner.onCompleted(args);
                } else {
                    throw new IOException("当前信息无法被读取");
                }
            } catch (IOException e) {
                CloseUtils.close(SocketChannelAdapter.this);
            }
        }
    };

    private final IoProvider.HandleOutputCallback outputCallback = new HandleOutputCallback() {
        @Override
        protected void canProviderOutput(Object attach) {
            if (!isClosed.get()) {
                return;
            }
            // todo
            sendIoEventListener.onCompleted(null);
        }
    };

    public interface OnChannelStatusChangedListener {

        void onChannelClosed(SocketChannel channel);
    }
}
