package net.qiujuer.lesson.sample.server.handle;


import java.io.File;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import net.qiujuer.lesson.sample.foo.Foo;
import net.qiujuer.library.clink.core.Connector;
import net.qiujuer.library.clink.core.Packet;
import net.qiujuer.library.clink.core.ReceivePacket;
import net.qiujuer.library.clink.utils.CloseUtils;

/**
 * <p>这个类就是一个接收发送消息的处理类，TCP server一方面接收来自某个客户端发送的消息
 * <p>一方面又将这个消息转发出去，当然不能发给自己
 */
public class ClientHandler extends Connector {

    private final ClientHandlerCallback clientHandlerCallback;

    private final File cachePath;

    /**
     * 自身的一个描述信息
     */
    private final String clientInfo;

    public ClientHandler(SocketChannel socketChannel, ClientHandlerCallback clientHandlerCallback
        , File cachePath) throws IOException {

        this.cachePath = cachePath;
        this.clientHandlerCallback = clientHandlerCallback;
        this.clientInfo = socketChannel.getRemoteAddress().toString();

        System.out.println("新客户端连接：" + clientInfo);

        setup(socketChannel);
    }

    public void exit() {
        CloseUtils.close(this);
        System.out.println("客户端已退出：" + clientInfo);
    }

    @Override
    public void onChannelClosed(SocketChannel channel) {
        super.onChannelClosed(channel);
        this.exitBySelf();
    }

    @Override
    protected File createNewReceiveFile() {
        return Foo.createRandomTemp(cachePath);
    }

    @Override
    protected void onReceivedPacket(ReceivePacket packet) {
        super.onReceivedPacket(packet);
        if (packet.type() == Packet.TYPE_MEMORY_STRING) {
            String msg = (String) packet.entity();
            // 这里的屏幕打印会影响性能
//            System.out.println(key.toString() + ":" + msg);
            // 转发
            clientHandlerCallback.onNewMsgArrived(this, msg);
        }
    }

    private void exitBySelf() {
        exit();
        clientHandlerCallback.onSelfClosed(this);
    }

    public interface ClientHandlerCallback {

        // 自身关闭时的一个通知
        void onSelfClosed(ClientHandler handler);

        // 收到消息时通知
        void onNewMsgArrived(ClientHandler handler, String msg);
    }
}
