package net.qiujuer.library.clink.impl.async;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import net.qiujuer.library.clink.box.StringReceivePacket;
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
public class AsyncReceiveDispatcher implements ReceiveDispatcher, IOArgsEventProcessor {

    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    private final Receiver receiver;

    private final ReceivePacketCallback callback;

    private IoArgs ioArgs = new IoArgs();
    private ReceivePacket<?, ?> receivePacketTmp;
    private WritableByteChannel packetChannel;
    private long total;
    private long pos;

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
            this.completedPacket(false);
        }
    }

    /**
     * 解析数据到packet
     */
    private void assemblePacket(IoArgs args) {
        if (receivePacketTmp == null) {
            int length = args.readLen();
            receivePacketTmp = new StringReceivePacket(length);
            packetChannel = Channels.newChannel(receivePacketTmp.open());
            total = length;
            pos = 0;
        }
        try {
            int count = args.writeTo(packetChannel);
            pos += count;
            // 表示完成
            if (pos == total) {
                completedPacket(true);
            }
        } catch (IOException e) {
            e.printStackTrace();
            completedPacket(false);
        }

    }

    private void completedPacket(boolean isSucceed) {
        ReceivePacket packet = this.receivePacketTmp;
        CloseUtils.close(packet);
        this.receivePacketTmp = null;

        WritableByteChannel channel = this.packetChannel;
        CloseUtils.close(channel);
        this.packetChannel = null;

        if (packet != null) {
            callback.onReceivePacketCompleted(packet);
        }
    }

    @Override
    public IoArgs provideIoArgs() {
        IoArgs args = ioArgs;
        // 本次可以接收到大小
        int receiveSize;
        if (receivePacketTmp == null) {
            receiveSize = 4;
        } else {
            receiveSize = (int) Math.min(total - pos, args.capacity());
        }
        // 设置本次接收数据大小
        args.limit(receiveSize);
        return args;
    }

    @Override
    public void onConsumeFailed(IoArgs args, Exception e) {
        e.printStackTrace();
    }

    @Override
    public void onConsumeCompleted(IoArgs args) {
        assemblePacket(args);
        // 继续接收
        registerReceive();
    }
}
