package net.qiujuer.library.clink.impl.async;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.IoArgs.IOArgsEventProcessor;
import net.qiujuer.library.clink.core.ReceiveDispatcher;
import net.qiujuer.library.clink.core.ReceivePacket;
import net.qiujuer.library.clink.core.Receiver;
import net.qiujuer.library.clink.utils.CloseUtils;

/**
 * 异步接收转发
 *
 * @author YJ
 * @date 2021/4/21
 **/
public class AsyncReceiveDispatcher implements ReceiveDispatcher, IOArgsEventProcessor,
    AsyncPacketWriter.PacketProvider {

    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final Receiver receiver;
    private final ReceivePacketCallback callback;
    private final AsyncPacketWriter writer = new AsyncPacketWriter(this);

    public AsyncReceiveDispatcher(Receiver receiver, ReceivePacketCallback callback) {
        this.receiver = receiver;
        // 将监听设置进去
        this.receiver.setReceiveListener(this);
        this.callback = callback;
    }

    @Override
    public void start() {
        registerReceive();
    }

    private void registerReceive() {
        try {
            receiver.postReceiveAsync();
        } catch (IOException e) {
            closeAndNotify();
        }
    }

    private void closeAndNotify() {
        CloseUtils.close(this);
    }

    @Override
    public void stop() {

    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            writer.close();
        }
    }

    /**
     * 网络接收准备就绪，此时可以读取数据，需要返回一个容器用于容纳数据
     */
    @Override
    public IoArgs provideIoArgs() {
        IoArgs ioArgs = writer.takeIoArgs();
        // 最开始写入之前需要调用开始写入方法
        ioArgs.startWriting();
        return ioArgs;
    }

    @Override
    public void onConsumeFailed(IoArgs args, Exception e) {
        e.printStackTrace();
    }

    @Override
    public void onConsumeCompleted(IoArgs args) {
        // 已经被关闭了
        if (isClosed.get()) {
            return;
        }
        // 消费数据之前标识args数据填充完成，改变未可读取数据状态，然后进行消费
        args.finishWriting();

        // 有数据则循环消费
        do {
            writer.consumeIoArgs(args);
        } while (args.remained() && !isClosed.get());

        // 继续接收
        registerReceive();
    }

    @Override
    public ReceivePacket takePacket(byte type, long len, byte[] headInfo) {
        return callback.onReceivedNewPacket(type, len);
    }

    @Override
    public void completedPacket(ReceivePacket packet, boolean isSucceed) {
        CloseUtils.close(packet);
        callback.onReceivePacketCompleted(packet);
    }
}
