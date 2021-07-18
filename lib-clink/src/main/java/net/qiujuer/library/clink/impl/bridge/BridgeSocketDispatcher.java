package net.qiujuer.library.clink.impl.bridge;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.IoArgs.IOArgsEventProcessor;
import net.qiujuer.library.clink.core.ReceiveDispatcher;
import net.qiujuer.library.clink.core.Receiver;
import net.qiujuer.library.clink.core.SendDispatcher;
import net.qiujuer.library.clink.core.SendPacket;
import net.qiujuer.library.clink.core.Sender;
import net.qiujuer.library.clink.impl.exceptions.EmptyIoArgsException;
import net.qiujuer.library.clink.utils.CloseUtils;
import net.qiujuer.library.clink.utils.plugin.CircularByteBuffer;

/**
 * 桥接调度器实现
 * <p>当前调度器同时实现了发送者和接收者的调度逻辑
 * <p>核心思想未：把接收者接收到的数据全部转发给发送者
 */
public class BridgeSocketDispatcher implements ReceiveDispatcher, SendDispatcher {

    /**
     * 环形缓冲区:数据暂存
     * <p>true表示阻塞，当填充满之后进行阻塞，等待消费之后再继续填充, false表示抛出异常
     */
    private final CircularByteBuffer mBuffer = new CircularByteBuffer(512, true);

    /**
     * 根据缓冲区得到的读取、写入通道
     */
    private final ReadableByteChannel readableByteChannel = Channels.newChannel(mBuffer.getInputStream());
    private final WritableByteChannel writableByteChannel = Channels.newChannel(mBuffer.getOutputStream());

    /**
     * 有数据则接收，无数据不强求填满，有多少返回多少
     */
    private final IoArgs receiveIoArgs = new IoArgs(256, false);
    private final Receiver receiver;

    /**
     * 当前是否处于发送中
     */
    private final AtomicBoolean isSending = new AtomicBoolean();

    /**
     * 默认全部都需要转发数据
     */
    private final IoArgs sendIoArgs = new IoArgs();
    private volatile Sender sender;

    public BridgeSocketDispatcher(Receiver receiver) {
        this.receiver = receiver;
    }

    /**
     * 绑定一个新的发送者，在绑定时，将老的发送者对应的调度设置为空
     */
    public void bindSender(Sender sender) {
        // 清理老的发送者回调
        final Sender oldSender = this.sender;
        if (oldSender != null) {
            oldSender.setSendListener(null);
        }
        // 清理操作
        synchronized (isSending) {
            isSending.set(false);
        }
        mBuffer.clear();

        this.sender = sender;
        if (sender != null) {
            sender.setSendListener(senderEventProcessor);
            requestSend();
        }
    }

    /**
     * 请求网络发送数据
     */
    private void requestSend() {
        synchronized (this.isSending) {
            final AtomicBoolean isRegisterSending = this.isSending;
            final boolean oldState = isRegisterSending.get();
            if (oldState) {
                return;
            }

            // 返回true表示当前有数据需要发送
            if (mBuffer.getAvailable() > 0) {
                try {
                    sender.postSendAsync();
                } catch (Exception e) {
                    e.printStackTrace();
                    CloseUtils.close(this);
                }
            } else {
                isRegisterSending.set(false);
            }
        }
    }

    /**
     * 外部初始化好了桥接调度器后需要调用此方法启动
     */
    @Override
    public void start() {
        receiver.setReceiveListener(receiverEventProcessor);
        registerReceive();
    }

    /**
     * 请求网络进行数据接收
     */
    private void registerReceive() {
        try {
            receiver.postReceiveAsync();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private final IoArgs.IOArgsEventProcessor receiverEventProcessor = new IOArgsEventProcessor() {
        @Override
        public IoArgs provideIoArgs() {
            receiveIoArgs.resetLimit();
            // 一份新的IoArgs需要调用一次开始写入数据的操作
            receiveIoArgs.startWriting();
            return receiveIoArgs;
        }

        @Override
        public boolean onConsumeFailed(Throwable e) {
            // args不可能未null，错误信息直接打印，并且关闭流程
            new RuntimeException(e).printStackTrace();
            return true;
        }

        @Override
        public boolean onConsumeCompleted(IoArgs args) {
            args.finishWriting();
            try {
                args.writeTo(writableByteChannel);
                requestSend();
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }


    };

    private final IoArgs.IOArgsEventProcessor senderEventProcessor = new IOArgsEventProcessor() {
        @Override
        public IoArgs provideIoArgs() {
            try {
                int available = mBuffer.getAvailable();
                IoArgs args = BridgeSocketDispatcher.this.sendIoArgs;
                if (available > 0) {
                    args.limit(available);
                    args.startWriting();
                    args.readFrom(readableByteChannel);
                    args.finishWriting();
                    return args;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public boolean onConsumeFailed(Throwable e) {
            if (e instanceof EmptyIoArgsException) {
                synchronized (isSending) {
                    isSending.set(false);
                    // 继续请求发送当前的数据
                    requestSend();
                    return false;
                }
            } else {
                return true;
            }
        }

        @Override
        public boolean onConsumeCompleted(IoArgs args) {
            // 设置当前发送状态
            synchronized (isSending) {
                isSending.set(false);
            }
            // 继续请求发送当前数据
            requestSend();
            return true;
        }
    };

    @Override
    public void stop() {
        // nothing
    }

    @Override
    public void send(SendPacket packet) {
        // nothing
    }

    @Override
    public void cancel(SendPacket packet) {
        // nothing
    }

    @Override
    public void sendHeartbeat() {
        // nothing
    }

    @Override
    public void close() throws IOException {
        // nothing
    }
}
