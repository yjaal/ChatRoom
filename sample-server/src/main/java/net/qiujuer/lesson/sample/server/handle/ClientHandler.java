package net.qiujuer.lesson.sample.server.handle;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.qiujuer.library.clink.core.Connector;
import net.qiujuer.library.clink.utils.CloseUtils;

/**
 * <p>这个类就是一个接收发送消息的处理类，TCP server一方面接收来自某个客户端发送的消息
 * <p>一方面又将这个消息转发出去，当然不能发给自己
 */
public class ClientHandler extends Connector {

    private final ClientHandlerCallback clientHandlerCallback;

    /**
     * 自身的一个描述信息
     */
    private final String clientInfo;

    public ClientHandler(SocketChannel socketChannel, ClientHandlerCallback clientHandlerCallback)
        throws IOException {

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
    protected void onReceiveNewMsg(String str) {
        super.onReceiveNewMsg(str);
        // 转发
        clientHandlerCallback.onNewMsgArrived(this, str);
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
