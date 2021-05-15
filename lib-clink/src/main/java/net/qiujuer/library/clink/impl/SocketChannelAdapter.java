package net.qiujuer.library.clink.impl;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.IoArgs.IOArgsEventProcessor;
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

    private IOArgsEventProcessor receiverProcessor;
    private IOArgsEventProcessor sendProcessor;

    public SocketChannelAdapter(SocketChannel channel, IoProvider ioProvider,
        OnChannelStatusChangedListener listener) throws IOException {
        this.channel = channel;
        this.ioProvider = ioProvider;
        this.listener = listener;

        channel.configureBlocking(false);
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

    @Override
    public void setReceiveListener(IOArgsEventProcessor receiverProcessor) {
        this.receiverProcessor = receiverProcessor;
    }

    @Override
    public boolean postReceiveAsync() throws IOException {
        if (isClosed.get()) {
            throw new IOException("当前通道已关闭");
        }
        return ioProvider.registerInput(channel, inputCallback);
    }

    @Override
    public void setSendListener(IOArgsEventProcessor sendProcessor) {
        this.sendProcessor = sendProcessor;
    }

    @Override
    public boolean postSendAsync() throws IOException {
        if (isClosed.get()) {
            throw new IOException("当前通道已关闭");
        }
        return ioProvider.registerOutput(channel, outputCallback);
    }

    public interface OnChannelStatusChangedListener {

        void onChannelClosed(SocketChannel channel);
    }


    private final HandleInputCallback inputCallback = new HandleInputCallback() {
        @Override
        protected void canProviderInput() {
            if (isClosed.get()) {
                return;
            }

            IOArgsEventProcessor processor = receiverProcessor;
            IoArgs args = processor.provideIoArgs();
            // 具体的读取操作
            try {
                System.out.println("处理接收到客户端的数据进行处理");
                if (args == null) {
                    processor.onConsumeFailed(null, new IOException("Provider IoArgs is null"));
                } else if (args.readFrom(channel) > 0) {
                    // 读取后进行回调
                    processor.onConsumeCompleted(args);
                } else {
                    processor.onConsumeFailed(args, new IOException("当前信息无法被读取"));
                }
            } catch (IOException e) {
                CloseUtils.close(SocketChannelAdapter.this);
            }
        }
    };

    private final IoProvider.HandleOutputCallback outputCallback = new HandleOutputCallback() {
        @Override
        protected void canProviderOutput() {
            if (isClosed.get()) {
                return;
            }
            // 这里的sendProcessor是传入的
            IoArgs.IOArgsEventProcessor processor = sendProcessor;
            IoArgs args = processor.provideIoArgs();

            // 具体的发送操作
            try {
                if (args.writeTo(channel) > 0) {
                    // 读取后进行回调
                    processor.onConsumeCompleted(args);
                } else {
                    processor.onConsumeFailed(args, new IOException("当前信息无法被写入"));
                }
            } catch (IOException e) {
                CloseUtils.close(SocketChannelAdapter.this);
            }
        }
    };
}
