package net.qiujuer.library.clink.impl.async;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.IoArgs.IOArgsEventProcessor;
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
public class AsyncSendDispatcher implements SendDispatcher, IOArgsEventProcessor {

    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    private final Sender sender;
    private final Queue<SendPacket<?>> queue = new ConcurrentLinkedQueue();
    private final AtomicBoolean isSending = new AtomicBoolean();

    /**
     * 当前要发送的数据包
     */
    private SendPacket<?> packetTmp;

    /**
     * 当前通道
     */
    private ReadableByteChannel channel;

    /**
     * 之所以这样做，是因为当前要发送的数据包可能比IoArgs缓存要大
     */
    private IoArgs ioArgs = new IoArgs();
    /**
     * 当前要发送数据包大小
     */
    private long total;
    /**
     * 当前数据包发送的进度
     */
    private long pos;

    public AsyncSendDispatcher(Sender sender) {
        this.sender = sender;
        // 设置监听就是自己
        sender.setSendListener(this);
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
        if (pos >= total) {
            // 已经发送完了
            this.completedPacket(pos == total);
            this.sendNextPacket();
            return;
        }

        try {
            sender.postSendAsync();
        } catch (IOException e) {
            closeAndNotify();
        }
    }

    private void completedPacket(boolean isSucceed) {
        SendPacket packet = this.packetTmp;
        if (packet == null) {
            return;
        }
        CloseUtils.close(packet, channel);

        this.packetTmp = null;
        this.channel = null;
        total = 0;
        pos = 0;

    }

    /**
     * 关闭自己并通知调用方
     */
    private void closeAndNotify() {
        CloseUtils.close();
    }


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
            // 这里一般是异常导致到关闭
            this.completedPacket(false);
        }
    }

    /**
     * 将数据封装到IoArgs中去
     */
    @Override
    public IoArgs provideIoArgs() {
        IoArgs args = ioArgs;
        if (channel == null) {
            // 发送数据包创建一个channel，我们可以从通道中获取数据
             channel = Channels.newChannel(packetTmp.open());
            // 包头写入包到大小
            args.limit(4);
            // 这里暂时强转int，如果包大小超过int最大值，则会出现数据丢失到情况
            args.writeLen((int) packetTmp.length());
        } else {
            args.limit((int) Math.min(args.capacity(), total - pos));
            try {
                int count = args.readFrom(channel);
                pos += count;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        return args;
    }

    @Override
    public void onConsumeFailed(IoArgs args, Exception e) {
        e.printStackTrace();
    }

    @Override
    public void onConsumeCompleted(IoArgs args) {
        this.sendCurPacket();
    }
}
