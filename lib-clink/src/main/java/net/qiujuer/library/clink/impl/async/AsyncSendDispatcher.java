package net.qiujuer.library.clink.impl.async;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.IoArgs.IOArgsEventProcessor;
import net.qiujuer.library.clink.core.SendDispatcher;
import net.qiujuer.library.clink.core.SendPacket;
import net.qiujuer.library.clink.core.Sender;
import net.qiujuer.library.clink.impl.async.AsyncPacketReader.PacketProvider;
import net.qiujuer.library.clink.utils.CloseUtils;

/**
 * 异步发送转发
 *
 * @author YJ
 * @date 2021/4/19
 **/
public class AsyncSendDispatcher implements SendDispatcher, IOArgsEventProcessor, PacketProvider {

    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    private final Sender sender;
    private final Queue<SendPacket<?>> queue = new ConcurrentLinkedQueue();
    private final AtomicBoolean isSending = new AtomicBoolean();
    private final AsyncPacketReader packetReader = new AsyncPacketReader(this);

    /**
     * 定义一个队列锁
     */
    private final Object queueLock = new Object();

    public AsyncSendDispatcher(Sender sender) {
        this.sender = sender;
        // 设置监听就是自己
        sender.setSendListener(this);
    }

    @Override
    public void send(SendPacket packet) {
        // 将消息存入到队列中
        synchronized (queueLock) {
            queue.offer(packet);
            // 如果当前不是发送中，则将状态设置为发送中
            if (isSending.compareAndSet(false, true)) {
                // 这里请求reader从这里获取一个packet，如果拿成功了，那么再来做网络发送的标志
                if (packetReader.requestTakePacker()) {
                    // 请求发送
                    requestSend();
                }
            }
        }
    }

    /**
     * 请求网络发送
     */
    private void requestSend() {
        try {
            sender.postSendAsync();
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


    @Override
    public SendPacket takePacket() {
        SendPacket packet;
        synchronized (queueLock) {
            packet = queue.poll();
            // 发送完了
            if (packet == null) {
                isSending.set(false);
                return null;
            }
        }

        if (packet.isCanceled()) {
            // 已取消的消息不用发送
            return takePacket();
        }
        return packet;
    }

    /**
     * 完成packet的发送
     */
    @Override
    public void completedPacket(SendPacket packet, boolean isSucceed) {
        CloseUtils.close(packet);
    }

    @Override
    public void cancel(SendPacket packet) {
        // 从队列中移除，但是有可能包正在被从队列中拿出的操作，这里需要做同步操作
        boolean ret;
        synchronized (queueLock) {
            ret = queue.remove(packet);
        }
        // 表示正常从队列中移除了
        if (ret) {
            packet.cancel();
            return;
        }
        // 这里表示包已经从队列中拿出来了，那么需要做一个真正的包取消
        packetReader.cancel(packet);
    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            isSending.set(false);
            // 这里一般是异常导致到关闭
            packetReader.close();
        }
    }

    /**
     * 将数据封装到IoArgs中去
     */
    @Override
    public IoArgs provideIoArgs() {
        // 填充一份数据
        return packetReader.fillData();
    }

    @Override
    public void onConsumeFailed(IoArgs args, Exception e) {
        if (args != null) {
            e.printStackTrace();
        } else {
            // todo
        }

    }

    @Override
    public void onConsumeCompleted(IoArgs args) {
        // 再次发起获取packet的请求，如果拿到了，则请求发送，否则就表示完成了
        if (packetReader.requestTakePacker()) {
            requestSend();
        }
    }
}