package net.qiujuer.lesson.sample.foo.handle;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.SocketChannel;
import net.qiujuer.lesson.sample.foo.Foo;
import net.qiujuer.library.clink.box.StringReceivePacket;
import net.qiujuer.library.clink.core.Connector;
import net.qiujuer.library.clink.core.IoContext;
import net.qiujuer.library.clink.core.Packet;
import net.qiujuer.library.clink.core.ReceivePacket;
import net.qiujuer.library.clink.utils.CloseUtils;

/**
 * <p>这个类就是一个接收发送消息的处理类，TCP server一方面接收来自某个客户端发送的消息
 * <p>一方面又将这个消息转发出去，当然不能发给自己
 */
public class ConnectorHandler extends Connector {

    private final File cachePath;

    /**
     * 这两个处理链只是作为一个头，后面方便添加相关具体当处理链，这里并不进行消费
     */
    private final ConnectorCloseChain closeChain = new DefaultPrintConnectorCloseChain();
    private final ConnectorStringPacketChain strPacketChain =
        new DefaultNonConnectorStringPacketChain();

    /**
     * 自身的一个描述信息
     */
    private final String clientInfo;

    public ConnectorHandler(SocketChannel socketChannel, File cachePath) throws IOException {
        this.cachePath = cachePath;
        this.clientInfo = socketChannel.getRemoteAddress().toString();
        setup(socketChannel);
    }

    public void exit() {
        CloseUtils.close(this);
    }

    @Override
    public void onChannelClosed(SocketChannel channel) {
        super.onChannelClosed(channel);
        closeChain.handle(this, this);
    }

    @Override
    protected File createNewReceiveFile(long length, byte[] headerInfo) {
        return Foo.createRandomTemp(cachePath);
    }

    @Override
    protected OutputStream createNewReceiveDirectOutputStream(long length, byte[] headerInfo) {
        // 默认创建一个内存存储ByteArrayOutputStream
        return new ByteArrayOutputStream();
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
        IoContext.get().getScheduler().delivery(() -> strPacketChain.handle(this, packet));
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
