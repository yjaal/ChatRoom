package net.qiujuer.lesson.sample.client;


import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import net.qiujuer.lesson.sample.client.bean.ServerInfo;
import net.qiujuer.lesson.sample.foo.handle.ConnectorHandler;
import net.qiujuer.lesson.sample.foo.handle.ConnectorStringPacketChain;
import net.qiujuer.library.clink.box.StringReceivePacket;
import net.qiujuer.library.clink.utils.CloseUtils;

/**
 * @author joyang
 */
public class TCPClient extends ConnectorHandler {

    public TCPClient(SocketChannel socketChannel, File cachePath) throws IOException {
        super(socketChannel, cachePath);
        this.getStringPacketChain().appendLast(new PrintStrPacketChain());
    }

    static TCPClient startWith(ServerInfo info, File cachePath) throws IOException {
        SocketChannel socketChannel = SocketChannel.open();

        // 连接本地，端口2000
        socketChannel.connect(
            new InetSocketAddress(Inet4Address.getByName(info.getAddress()), info.getPort()));

        System.out.println("已发起服务器连接，并进入后续流程～");
        System.out.println("客户端信息：" + socketChannel.getLocalAddress().toString());
        System.out.println("服务器信息：" + socketChannel.getRemoteAddress().toString());

        try {
            return new TCPClient(socketChannel, cachePath);
        } catch (Exception e) {
            CloseUtils.close(socketChannel);
        }
        return null;
    }

    /**
     * 一个基本的打印消费
     */
    private class PrintStrPacketChain extends ConnectorStringPacketChain {

        @Override
        protected boolean consume(ConnectorHandler handler,
            StringReceivePacket stringReceivePacket) {
            String str = stringReceivePacket.entity();
            System.out.println(str);
            return true;
        }
    }
}
