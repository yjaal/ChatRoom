package net.qiujuer.library.clink.impl.async;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import net.qiujuer.library.clink.box.StringReceivePacket;
import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.IoArgs.IOArgsEventListener;
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
public class AsyncReceiveDispatcher implements ReceiveDispatcher {

    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    private final Receiver receiver;

    private final ReceivePacketCallback callback;

    private IoArgs ioArgs = new IoArgs();
    private ReceivePacket receivePacketTmp;
    private byte[] buffer;
    private int total;
    private int pos;

    public AsyncReceiveDispatcher(Receiver receiver, ReceivePacketCallback callback) {
        this.receiver = receiver;
        // 将监听设置进去
        this.receiver.setReceiveListener(ioArgsEventListener);
        this.callback = callback;
    }

    @Override
    public void start() {
        registerReceive();
    }

    private void registerReceive() {
        try {
            receiver.receiveAsync(ioArgs);
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
            ReceivePacket packet = this.receivePacketTmp;
            if (packet != null) {
                this.receivePacketTmp = null;
                CloseUtils.close(packet);
            }
        }
    }

    /**
     * 解析数据到packet
     */
    private void assemblePacket(IoArgs args) {
        if (receivePacketTmp == null) {
            int length = args.readLen();
            receivePacketTmp = new StringReceivePacket(length);
            buffer = new byte[length];
            total = length;
            pos = 0;
        }
        int count = args.writeTo(buffer, 0);
        if (count > 0) {
            receivePacketTmp.save(buffer, count);
            pos += count;

            // 表示完成
            if (pos == total) {
                completedPacket();
                receivePacketTmp = null;
            }
        }
    }

    private void completedPacket() {
        ReceivePacket packet = this.receivePacketTmp;
        CloseUtils.close(packet);
        callback.onReceivePacketCompleted(packet);

    }

    private IOArgsEventListener ioArgsEventListener = new IOArgsEventListener() {
        @Override
        public void onStarted(IoArgs args) {
            // 本次可以接收到大小
            int receiveSize;
            if (receivePacketTmp == null) {
                receiveSize = 4;
            } else {
                receiveSize = Math.min(total - pos, args.capacity());
            }
            // 设置本次接收数据大小
            args.limit(receiveSize);
        }

        @Override
        public void onCompleted(IoArgs args) {
            assemblePacket(args);
            // 继续接收
            registerReceive();
        }


    };
}
