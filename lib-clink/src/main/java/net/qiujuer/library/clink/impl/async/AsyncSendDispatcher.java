package net.qiujuer.library.clink.impl.async;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.IoArgs.IOArgsEventProcessor;
import net.qiujuer.library.clink.core.SendDispatcher;
import net.qiujuer.library.clink.core.SendPacket;
import net.qiujuer.library.clink.core.Sender;
import net.qiujuer.library.clink.impl.async.AsyncPacketReader.PacketProvider;
import net.qiujuer.library.clink.impl.exceptions.EmptyIoArgsException;
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
    private final BlockingQueue<SendPacket<?>> queue = new ArrayBlockingQueue<>(16);
    private final AtomicBoolean isSending = new AtomicBoolean();
    private final AsyncPacketReader packetReader = new AsyncPacketReader(this);


    public AsyncSendDispatcher(Sender sender) {
        this.sender = sender;
        // 设置监听就是自己
        sender.setSendListener(this);
    }

    @Override
    public void send(SendPacket packet) {
        try {
            // 将消息存入到队列中
            queue.put(packet);
            // 请求发送
            requestSend(false);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void sendHeartbeat() {
        // 如果队列中本身就有数据，那就没必要发送心跳帧了，因为发送数据本身就可以完成心跳的作用
        if (!queue.isEmpty()) {
            if (packetReader.requestSendHeartbeatFrame()) {
                requestSend(false);
            }
        }
    }

    /**
     * 请求网络发送
     *
     * @param callFromIoConsume 表示是否是从IO消费流程中调用的，因为还有心跳发送调用
     */
    private void requestSend(boolean callFromIoConsume) {
        synchronized (this.isSending) {
            final AtomicBoolean isRegisterSending = this.isSending;
            final boolean oldState = isRegisterSending.get();
            if (isClosed.get() || (oldState && !callFromIoConsume)) {
                return;
            }

            if (callFromIoConsume && !oldState) {
                throw new IllegalStateException("消费过程中，却不是运行状态，异常");
            }
            // 如果有数据则进行发送
            if (packetReader.requestTakePacker()) {
                try {
                    isRegisterSending.set(true);
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
    public boolean onConsumeFailed(Throwable e) {
        if (e instanceof EmptyIoArgsException) {
            // 继续请求发送当前的数据
            requestSend(true);
            return false;
        } else {
            CloseUtils.close(this);
            return true;
        }
    }

    @Override
    public boolean onConsumeCompleted(IoArgs args) {
        synchronized (isSending) {
            AtomicBoolean isRegisterSending = this.isSending;
            final boolean isRunning = !isClosed.get();

            if (!isRegisterSending.get() && isRunning) {
                throw new IllegalStateException("未注册却是运行状态，不应该发生");
            }
            isRegisterSending.set(isRunning && packetReader.requestTakePacker());

            return isRegisterSending.get();
        }
    }
}
