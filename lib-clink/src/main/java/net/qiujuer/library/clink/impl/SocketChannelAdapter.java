package net.qiujuer.library.clink.impl;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.IoArgs.IOArgsEventProcessor;
import net.qiujuer.library.clink.core.IoProvider;
import net.qiujuer.library.clink.core.IoProvider.HandleProviderCallback;
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
        // 进行callback状态检查，判断是否由于上一次未读取或写入完成而处于自循环状态
        inputCallback.checkAttachIsNull();
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
        // 进行callback状态检查，判断是否由于上一次未读取或写入完成而处于自循环状态
        outputCallback.checkAttachIsNull();
        return ioProvider.registerOutput(channel, outputCallback);
    }

    public interface OnChannelStatusChangedListener {

        void onChannelClosed(SocketChannel channel);
    }


    private final HandleProviderCallback inputCallback = new HandleProviderCallback() {
        @Override
        protected void onProviderIo(IoArgs args) {
            if (isClosed.get()) {
                return;
            }

            IOArgsEventProcessor processor = receiverProcessor;
            if (args == null) {
                args = processor.provideIoArgs();
            }
            // 具体的读取操作
            try {
                System.out.println("处理接收到客户端的数据进行处理");
                if (args == null) {
                    processor.onConsumeFailed(null, new IOException("Provider IoArgs is null"));
                } else {
                    int count = args.readFrom(channel);
                    if (count == 0) {
                        System.out.println("Current read zero data");
                    }
                    if (args.remained()) {
                        attach = args;
                        ioProvider.registerInput(channel, this);
                    } else {
                        attach = null;
                        processor.onConsumeCompleted(args);
                    }
                }
            } catch (IOException e) {
                CloseUtils.close(SocketChannelAdapter.this);
            }
        }
    };

    /**
     * 这里当网络可以发送当时候，我们获取一个IoArgs，然后将数据写入到通道中进行发送，
     * <p> 但是当数据还没有发送完毕的时候，网卡可能将资源让给了另外一个通道，此时IoArgs
     * <p> 在写入的时候可能返回0，这个0不代表写完成，也有可能是网卡没把资源给当前通道，
     * <p> 此时我们应该放弃输出，而是再次注册selector，表明还有数据没有发送完成。而
     * <P> 同时需要将当前无法发送的通道占用的线程放弃掉，让给其他通道
     */
    private final HandleProviderCallback outputCallback = new HandleProviderCallback() {
        @Override
        protected void onProviderIo(IoArgs args) {
            if (isClosed.get()) {
                return;
            }
            // 这里的sendProcessor是传入的
            IoArgs.IOArgsEventProcessor processor = sendProcessor;
            if (args == null) {
                // 如果为空，则请求获取一份，如果不为空，则是上一次未发送完的
                args = processor.provideIoArgs();
            }

            // 具体的发送操作
            try {
                if (args == null) {
                    processor.onConsumeFailed(null, new IOException("消费失败"));
                } else {
                    int count = args.writeTo(channel);
                    if (count == 0) {
                        // 表明当前是无法发送数据的
                        System.out.println("Current write zero data");
                    }
                    if (args.remained()) {
                        // 还有数据输出，则再次注册
                        attach = args;
                        ioProvider.registerOutput(channel, this);
                    } else {
                        // 读取后进行回调
                        attach = null;
                        processor.onConsumeCompleted(args);
                    }
                }
            } catch (IOException e) {
                CloseUtils.close(SocketChannelAdapter.this);
            }
        }
    };
}
