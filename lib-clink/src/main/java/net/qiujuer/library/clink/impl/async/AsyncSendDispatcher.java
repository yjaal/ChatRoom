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


    public AsyncSendDispatcher(Sender sender) {
        this.sender = sender;
        // 设置监听就是自己
        sender.setSendListener(this);
    }

    @Override
    public void send(SendPacket packet) {
        // 将消息存入到队列中
        queue.offer(packet);
        // 请求发送
        requestSend();
    }

    @Override
    public void sendHeartbeat() {
        // 如果队列中本身就有数据，那就没必要发送心跳帧了，因为发送数据本身就可以完成心跳的作用
        if (queue.size() <= 0) {
            boolean addRes = packetReader.requestSendHeartbeatFrame();
            if (addRes) {
                requestSend();
            }
        }
    }

    /**
     * 请求网络发送
     */
    private void requestSend() {
        synchronized (isSending) {
            // 如果正在发送或者已关闭则暂时不发送
            if (isSending.get() || isClosed.get()) {
                return;
            }

            // 如果有数据则进行发送
            if (packetReader.requestTakePacker()) {
                try {
                    isSending.set(true);
                    boolean isSucc = sender.postSendAsync();
                    if (!isSucc) {
                        isSending.set(false);
                    }
                } catch (IOException e) {
                    closeAndNotify();
                }
            }
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
        SendPacket packet = queue.poll();
        // 发送完了
        if (packet == null) {
            return null;
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
        ret = queue.remove(packet);
        // 表示正常从队列中移除了
        if (ret) {
            packet.cancel();
            return;
        }
        // 这里表示包已经从队列中拿出来了，那么需要做一个真正的包取消
        packetReader.cancel(packet);
    }

    @Override
    public void close() {
        if (isClosed.compareAndSet(false, true)) {
            // 这里一般是异常导致到关闭
            packetReader.close();
            queue.clear();
            synchronized (isSending) {
                isSending.set(false);
            }
        }
    }

    /**
     * 将数据封装到IoArgs中去
     */
    @Override
    public IoArgs provideIoArgs() {
        // 填充一份数据
        return isClosed.get() ? null : packetReader.fillData();
    }

    @Override
    public void onConsumeFailed(IoArgs args, Exception e) {
        e.printStackTrace();
        synchronized (isSending) {
            isSending.set(false);
        }
        // 继续请求发送当前的数据
        requestSend();
    }

    @Override
    public void onConsumeCompleted(IoArgs args) {
        synchronized (isSending) {
            isSending.set(false);
        }
        // 继续请求发送当前的数据
        requestSend();
    }
}
