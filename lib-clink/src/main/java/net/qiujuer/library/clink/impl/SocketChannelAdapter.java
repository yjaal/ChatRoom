package net.qiujuer.library.clink.impl;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.IoArgs.IOArgsEventProcessor;
import net.qiujuer.library.clink.core.IoProvider;
import net.qiujuer.library.clink.core.Receiver;
import net.qiujuer.library.clink.core.Sender;
import net.qiujuer.library.clink.impl.exceptions.EmptyIoArgsException;
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

    private final AbsProviderCallback inputCallback;
    private final AbsProviderCallback outputCallback;

    public SocketChannelAdapter(SocketChannel channel, IoProvider ioProvider,
        OnChannelStatusChangedListener listener) throws IOException {
        this.channel = channel;
        this.ioProvider = ioProvider;
        this.listener = listener;
        inputCallback = new InputProvideCallback(channel, SelectionKey.OP_READ, ioProvider);
        outputCallback = new OutputProvideCallback(channel, SelectionKey.OP_WRITE, ioProvider);
    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            ioProvider.unRegister(channel);
            CloseUtils.close(channel);
            listener.onChannelClosed(channel);
        }
    }

    @Override
    public void setReceiveListener(IOArgsEventProcessor receiverProcessor) {
        this.inputCallback.eventProcessor = receiverProcessor;
    }

    @Override
    public void setSendListener(IOArgsEventProcessor sendProcessor) {
        this.outputCallback.eventProcessor = sendProcessor;
    }

    @Override
    public void postReceiveAsync() throws Exception {
        if (isClosed.get() || !channel.isOpen()) {
            throw new IOException("当前通道已关闭");
        }
        // 进行callback状态检查，判断是否由于上一次未读取或写入完成而处于自循环状态
        inputCallback.checkAttachIsNull();
        ioProvider.register(inputCallback);
    }

    @Override
    public void postSendAsync() throws Exception {
        if (isClosed.get() || !channel.isOpen()) {
            throw new IOException("当前通道已关闭");
        }
        // 进行callback状态检查，判断是否由于上一次未读取或写入完成而处于自循环状态
        outputCallback.checkAttachIsNull();
        ioProvider.register(outputCallback);
    }

    @Override
    public long getLastWriterTime() {
        return this.outputCallback.lastActiveTime;
    }

    @Override
    public long getLastReadTime() {
        return this.inputCallback.lastActiveTime;
    }

    public interface OnChannelStatusChangedListener {

        void onChannelClosed(SocketChannel channel);
    }

    class InputProvideCallback extends AbsProviderCallback {

        InputProvideCallback(SocketChannel channel, int ops,
            IoProvider ioProvider) {
            super(channel, ops, ioProvider);
        }

        @Override
        protected int consumeIoArgs(IoArgs args, SocketChannel channel) throws IOException {
            return args.readFrom(channel);
        }
    }

    class OutputProvideCallback extends AbsProviderCallback {

        OutputProvideCallback(SocketChannel channel, int ops,
            IoProvider ioProvider) {
            super(channel, ops, ioProvider);
        }

        @Override
        protected int consumeIoArgs(IoArgs args, SocketChannel channel) throws IOException {
            return args.writeTo(channel);
        }
    }

    abstract class AbsProviderCallback extends IoProvider.HandleProviderCallback {

        volatile IoArgs.IOArgsEventProcessor eventProcessor;
        volatile long lastActiveTime;

        AbsProviderCallback(SocketChannel channel, int ops, IoProvider ioProvider) {
            super(channel, ops, ioProvider);
        }

        @Override
        public void fireThrowable(Throwable e) {
            final IoArgs.IOArgsEventProcessor processor = eventProcessor;
            if (processor == null || processor.onConsumeFailed(e)) {
                CloseUtils.close(SocketChannelAdapter.this);
            }
        }

        protected abstract int consumeIoArgs(IoArgs args, SocketChannel channel) throws IOException;

        @Override
        protected final boolean onProviderIo(IoArgs args) {
            if (isClosed.get()) {
                return false;
            }

            final IoArgs.IOArgsEventProcessor processor = eventProcessor;
            // 表示可能被取消掉了
            if (processor == null) {
                return false;
            }
            lastActiveTime = System.currentTimeMillis();

            if (args == null) {
                // 如果为空，则请求获取一份，如果不为空，则是上一次未发送完的
                args = processor.provideIoArgs();
            }

            // 具体的发送操作
            try {
                if (args == null) {
                    throw new EmptyIoArgsException("ProvideIoArgs is null");
                }

                int count = consumeIoArgs(args, channel);
                // 检查是否还有未消费的数据 以及是否需要将所有数据都消费掉
                if (args.remained() && (count == 0 || args.isNeedConsumeRemaining())) {
                    // 还有数据输出，则再次注册
                    attach = args;
                    return true;
                } else {
                    // 读取后进行回调
                    attach = null;
                    return processor.onConsumeCompleted(args);
                }
            } catch (IOException e) {
                if (processor.onConsumeFailed(e)) {
                    CloseUtils.close();
                }
                return false;
            }
        }
    }
}
