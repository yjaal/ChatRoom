package net.qiujuer.lesson.sample.server.handle;


import java.io.File;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;
import net.qiujuer.lesson.sample.foo.Foo;
import net.qiujuer.library.clink.box.StringReceivePacket;
import net.qiujuer.library.clink.core.Connector;
import net.qiujuer.library.clink.core.Packet;
import net.qiujuer.library.clink.core.ReceivePacket;
import net.qiujuer.library.clink.utils.CloseUtils;

/**
 * <p>这个类就是一个接收发送消息的处理类，TCP server一方面接收来自某个客户端发送的消息
 * <p>一方面又将这个消息转发出去，当然不能发给自己
 */
public class ClientHandler extends Connector {

    private final File cachePath;

    private final ConnectorCloseChain closeChain = new DefaultPrintConnectorCloseChain();

    private final Executor deliveryPool;

    private final ConnectorStringPacketChain strPacketChain = new DefaultNonConnectorStringPacketChain();

    /**
     * 自身的一个描述信息
     */
    private final String clientInfo;

    public ClientHandler(SocketChannel socketChannel, File cachePath, Executor deliveryPool) throws IOException {
        this.cachePath = cachePath;
        this.clientInfo = socketChannel.getRemoteAddress().toString();
        this.deliveryPool = deliveryPool;
        setup(socketChannel);
    }

    public void exit() {
        CloseUtils.close(this);
        closeChain.handle(this, this);
    }

    @Override
    public void onChannelClosed(SocketChannel channel) {
        super.onChannelClosed(channel);
        closeChain.handle(this, this);
    }

    @Override
    protected File createNewReceiveFile() {
        return Foo.createRandomTemp(cachePath);
    }

    @Override
    protected void onReceivedPacket(ReceivePacket packet) {
        super.onReceivedPacket(packet);
        switch (packet.type()) {
            case Packet.TYPE_MEMORY_STRING:
                deliveryStringPacket((StringReceivePacket) packet);
                break;
            default:
                System.out.println("New Packet:" + packet.type() + "-" + packet.length());
        }
    }

    private void deliveryStringPacket(StringReceivePacket packet) {
        deliveryPool.execute(() -> strPacketChain.handle(this, packet));
    }

    public String getClientInfo() {
        return clientInfo;
    }

    public ConnectorStringPacketChain getStringPacketChain() {
        return strPacketChain;
    }

    public ConnectorCloseChain getCloseChain() {
        return closeChain;
    }
}
