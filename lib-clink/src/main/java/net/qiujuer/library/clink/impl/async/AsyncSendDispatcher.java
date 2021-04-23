package net.qiujuer.library.clink.impl.async;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.IoArgs.IOArgsEventListener;
import net.qiujuer.library.clink.core.SendDispatcher;
import net.qiujuer.library.clink.core.SendPacket;
import net.qiujuer.library.clink.core.Sender;
import net.qiujuer.library.clink.utils.CloseUtils;

/**
 * 异步发送转发
 *
 * @author YJ
 * @date 2021/4/19
 **/
public class AsyncSendDispatcher implements SendDispatcher {

    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    private final Sender sender;
    private final Queue<SendPacket> queue = new ConcurrentLinkedQueue();
    private final AtomicBoolean isSending = new AtomicBoolean();

    /**
     * 当前要发送的数据包
     */
    private SendPacket packetTmp;
    /**
     * 之所以这样做，是因为当前要发送的数据包可能比IoArgs缓存要大
     */
    private IoArgs ioArgs = new IoArgs();
    /**
     * 当前要发送数据包大小
     */
    private int total;
    /**
     * 当前数据包发送的进度
     */
    private int pos;

    public AsyncSendDispatcher(Sender sender) {
        this.sender = sender;
    }

    @Override
    public void send(SendPacket packet) {
        // 将消息存入到队列中
        queue.offer(packet);
        // 如果当前不是发送中，则将状态设置为发送中
        if (isSending.compareAndSet(false, true)) {
            sendNextPacket();
        }
    }

    private void sendNextPacket() {
        SendPacket tmp = packetTmp;
        // 如果拿到的数据包不为空，则将其关闭，可能是之前未关闭
        if (tmp != null) {
            CloseUtils.close(tmp);
        }

        // 从队列中获取一个包进行发送
        SendPacket packet = packetTmp = takePacket();
        if (packet == null) {
            // 若获取到到包为空，则将发送状态设置为false
            isSending.set(false);
            return;
        }

        // 发送之前设置要发送数据包大小以及发送起始位置
        total = packet.length();
        pos = 0;

        // 发送当前数据包
        sendCurPacket();
    }

    /**
     * 具体的发送方法
     */
    private void sendCurPacket() {
        IoArgs args = ioArgs;
        args.startWriting();
        if (pos >= total) {
            // 已经发送完了
            isSending.set(false);
            return;
        } else if (pos == 0) {
            // 第一个包，将数据长度写在包头
            args.writeLen(total);
        }
        // 将实际数据写入
        byte[] bytes = packetTmp.bytes();
        int count = args.readFrom(bytes, pos);
        pos += count;
        // 完成封装
        args.finishWriting();

        // 真正开始发送，同时传入一个进度回调类
        try {
            sender.sendAsync(args, ioArgsEventListener);
        } catch (IOException e) {
            closeAndNotify();
        }
    }

    /**
     * 关闭自己并通知调用方
     */
    private void closeAndNotify() {
        CloseUtils.close();
    }

    private final IOArgsEventListener ioArgsEventListener = new IOArgsEventListener() {
        @Override
        public void onStarted(IoArgs args) {

        }

        @Override
        public void onCompleted(IoArgs args) {
            // 继续发送当前包
            sendCurPacket();
        }
    };

    private SendPacket takePacket() {
        SendPacket packet = queue.poll();
        if (packet != null && packet.isCanceled()) {
            // 已取消的消息不用发送
            return takePacket();
        }
        return packet;
    }

    @Override
    public void cancel(SendPacket packet) {

    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            isSending.set(false);
            SendPacket packet = this.packetTmp;
            if (packet != null) {
                this.packetTmp = null;
                CloseUtils.close(packet);
            }
        }
    }
}
