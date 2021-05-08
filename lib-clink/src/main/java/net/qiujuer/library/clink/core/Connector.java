package net.qiujuer.library.clink.core;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.UUID;
import net.qiujuer.library.clink.box.StringSendPacket;
import net.qiujuer.library.clink.impl.SocketChannelAdapter;
import net.qiujuer.library.clink.impl.async.AsyncReceiveDispatcher;
import net.qiujuer.library.clink.impl.async.AsyncSendDispatcher;

/**
 * 用于标识服务端和客户端的某个连接
 */
public class Connector implements Closeable, SocketChannelAdapter.OnChannelStatusChangedListener {

    /**
     * 这里用于标识连接的唯一性
     */
    private UUID key = UUID.randomUUID();
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

        // 启动接收
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

    @Override
    public void close() throws IOException {
        receiveDispatcher.close();
        sendDispatcher.close();
        sender.close();
        receiver.close();
        channel.close();
    }

    protected void onReceiveNewMsg(String str) {
        System.out.println("收到消息--> " + key.toString() + ": " + str);
    }

    protected void onReceivePacket(ReceivePacket packet) {
        System.out.println(
            "收到消息--> " + key.toString() + ": [New Packet]-Type:" + packet.type() + ":[Len:]"
                + packet.length);
    }

    @Override
    public void onChannelClosed(SocketChannel channel) {

    }

    private ReceiveDispatcher.ReceivePacketCallback receivePacketCallback = this::onReceivePacket;
}
