package net.qiujuer.library.clink.impl;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.IoArgs.IOArgsEventListener;
import net.qiujuer.library.clink.core.IoProvider;
import net.qiujuer.library.clink.core.IoProvider.HandleInputCallback;
import net.qiujuer.library.clink.core.IoProvider.HandleOutputCallback;
import net.qiujuer.library.clink.core.Receiver;
import net.qiujuer.library.clink.core.Sender;
import net.qiujuer.library.clink.utils.CloseUtils;

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

    private IOArgsEventListener receiverIoEventListener;
    private IOArgsEventListener sendIoEventListener;

    /**
     * 这里定义后让外层传入
     */
    private IoArgs receiveArgsTmp;

    public SocketChannelAdapter(SocketChannel channel, IoProvider ioProvider,
        OnChannelStatusChangedListener listener) throws IOException {
        this.channel = channel;
        this.ioProvider = ioProvider;
        this.listener = listener;

        channel.configureBlocking(false);
    }

    @Override
    public void setReceiveListener(IOArgsEventListener listener) {
        this.receiverIoEventListener = listener;
    }

    @Override
    public boolean receiveAsync(IoArgs args) throws IOException {
        if (isClosed.get()) {
            throw new IOException("当前通道已关闭");
        }
        receiveArgsTmp = args;
        return ioProvider.registerInput(channel, inputCallback);
    }

    @Override
    public boolean sendAsync(IoArgs args, IOArgsEventListener listener) throws IOException {
        if (isClosed.get()) {
            throw new IOException("当前通道已关闭");
        }
        sendIoEventListener = listener;
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


    private final HandleInputCallback inputCallback = new HandleInputCallback() {
        @Override
        protected void canProviderInput() {
            if (isClosed.get()) {
                return;
            }

            IoArgs args = receiveArgsTmp;
            IOArgsEventListener listener = SocketChannelAdapter.this.receiverIoEventListener;
            listener.onStarted(args);
            // 具体的读取操作
            try {
                // 有读取到值
                System.out.println("处理接收到客户端的数据进行处理");
                if (args.readFrom(channel) > 0) {
                    // 读取后进行回调
                    listener.onCompleted(args);
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
            if (isClosed.get()) {
                return;
            }
            IoArgs args = getAttach();
            IoArgs.IOArgsEventListener listener = sendIoEventListener;
            listener.onStarted(args);

            // 具体的读取操作
            try {
                // 有读取到值
                System.out.println("处理接收到客户端的数据进行处理");
                if (args.writeTo(channel) > 0) {
                    // 读取后进行回调
                    listener.onCompleted(args);
                } else {
                    throw new IOException("当前信息无法被写入");
                }
            } catch (IOException e) {
                CloseUtils.close(SocketChannelAdapter.this);
            }
        }
    };

    public interface OnChannelStatusChangedListener {

        void onChannelClosed(SocketChannel channel);
    }
}
