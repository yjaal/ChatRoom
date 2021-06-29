package net.qiujuer.library.clink.core;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.qiujuer.library.clink.box.BytesReceivePacket;
import net.qiujuer.library.clink.box.FileReceivePacket;
import net.qiujuer.library.clink.box.StreamDirectReceivePacket;
import net.qiujuer.library.clink.box.StringReceivePacket;
import net.qiujuer.library.clink.box.StringSendPacket;
import net.qiujuer.library.clink.core.ReceiveDispatcher.ReceivePacketCallback;
import net.qiujuer.library.clink.impl.SocketChannelAdapter;
import net.qiujuer.library.clink.impl.async.AsyncReceiveDispatcher;
import net.qiujuer.library.clink.impl.async.AsyncSendDispatcher;
import net.qiujuer.library.clink.utils.CloseUtils;

/**
 * 用于标识服务端和客户端的某个连接
 */
public abstract class Connector implements Closeable,
    SocketChannelAdapter.OnChannelStatusChangedListener {

    /**
     * 这里用于标识连接的唯一性
     */
    protected UUID key = UUID.randomUUID();
    private SocketChannel channel;
    private Sender sender;
    private Receiver receiver;

    /**
     * 用户发送数据
     */
    private SendDispatcher sendDispatcher;

    /**
     * 用于接收数据
     */
    private ReceiveDispatcher receiveDispatcher;

    private final List<ScheduleJob> scheduleJobs = new ArrayList<>(4);

    public void setup(SocketChannel channel) throws IOException {

        this.channel = channel;

        IoContext context = IoContext.get();
        SocketChannelAdapter adapter = new SocketChannelAdapter(channel, context.getIoProvider(),
            this);

        // 读写都有SocketChannelAdapter完成,，同时还可以通过回调完成通道关闭后要处理的事情
        this.sender = adapter;
        this.receiver = adapter;

        // 实际发送接收还是由Sender和Receiver来做
        sendDispatcher = new AsyncSendDispatcher(sender);
        receiveDispatcher = new AsyncReceiveDispatcher(receiver, receivePacketCallback);

        // 启动接收，这里会对接收回调receivePacketCallback注册，当监听到有数据过来时，则会自动启动接收
        // 而发送回调则不同，发送回调是在具体发送时刻才会进行注册
        receiveDispatcher.start();
    }

    /**
     * 发送数据
     */
    public void send(String msg) {
        SendPacket packet = new StringSendPacket(msg);
        // 实际发送还是由IoArgs来完成，由sendDispatcher转发
        sendDispatcher.send(packet);
    }

    public void send(SendPacket packet) {
        // 实际发送还是由IoArgs来完成，由sendDispatcher转发
        sendDispatcher.send(packet);
    }

    @Override
    public void close() throws IOException {
        receiveDispatcher.close();
        sendDispatcher.close();
        sender.close();
        receiver.close();
        channel.close();
    }

    protected void onReceivedPacket(ReceivePacket packet) {
//        System.out.println(
//            "收到消息--> " + key.toString() + ": [New Packet]-Type:" + packet.type() + ", [Len:"
//                + packet.length + "]");
    }

    @Override
    public void onChannelClosed(SocketChannel channel) {
        synchronized (scheduleJobs) {
            scheduleJobs.forEach(ScheduleJob::unSchedule);
            scheduleJobs.clear();
        }
        CloseUtils.close(this);
    }

    /**
     * 创建临时接收文件
     */
    protected abstract File createNewReceiveFile(long length, byte[] headerInfo);

    /**
     * 创建直流
     */
    protected abstract OutputStream createNewReceiveDirectOutputStream(long length,
        byte[] headerInfo);

    private ReceiveDispatcher.ReceivePacketCallback receivePacketCallback =
        new ReceivePacketCallback() {
            @Override
            public ReceivePacket<?, ?> onReceivedNewPacket(byte type, long length,
                byte[] headerInfo) {
                switch (type) {
                    case Packet.TYPE_MEMORY_BYTES:
                        return new BytesReceivePacket(length);
                    case Packet.TYPE_MEMORY_STRING:
                        return new StringReceivePacket(length);
                    case Packet.TYPE_STREAM_FILE:
                        return new FileReceivePacket(length,
                            createNewReceiveFile(length, headerInfo));
                    case Packet.TYPE_STREAM_DIRECT:
                        return new StreamDirectReceivePacket(
                            createNewReceiveDirectOutputStream(length, headerInfo), length);
                    default:
                        throw new UnsupportedOperationException("发送数据的格式不支持");
                }
            }

            @Override
            public void onReceivePacketCompleted(ReceivePacket packet) {
                onReceivedPacket(packet);
            }

            @Override
            public void onReceiveHeartbeat() {
                // 收到心跳帧只是简单打印即可
                System.out.println(key.toString() + ":[Heartbeat]");
            }
        };

    public UUID getKey() {
        return key;
    }

    public long getLastActiveTime() {
        return Math.max(sender.getLastWriterTime(), receiver.getLastReadTime());
    }

    /**
     * 提供外部进行调度
     */
    public void schedule(ScheduleJob job) {
        synchronized (scheduleJobs) {
            if (scheduleJobs.contains(job)) {
                return;
            }
            // 执行具体调度
            Scheduler scheduler = IoContext.get().getScheduler();
            job.schedule(scheduler);
            scheduleJobs.add(job);
        }
    }

    public void fireIdleTimeoutEvent() {
        // 超时后发送一个心跳包
        sendDispatcher.sendHeartbeat();
    }

    public void fireExceptionCaught(Throwable e) {

    }
}
