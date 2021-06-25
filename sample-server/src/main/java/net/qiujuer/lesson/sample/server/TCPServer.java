package net.qiujuer.lesson.sample.server;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.qiujuer.lesson.sample.server.ServerAcceptor.AcceptListener;
import net.qiujuer.lesson.sample.server.handle.ClientHandler;
import net.qiujuer.library.clink.utils.CloseUtils;

/**
 * 一个TCP server可能会有多个 ClientHandler 处理相关消息
 */
public class TCPServer implements ClientHandler.ClientHandlerCallback, AcceptListener {

    private final int port;
    private final List<ClientHandler> clientHandlerList = new ArrayList<>();
    // 消息转发线程池
    private final ExecutorService forwardingThreadPool;
    private Selector selector;
    private ServerSocketChannel server;

    private final File cachePath;

    // 未同步，可能不是那么准确
    private long receiveSize;
    private long sendSize;

    public TCPServer(int port, File cachePath) {
        this.cachePath = cachePath;
        this.port = port;
        this.forwardingThreadPool = Executors.newSingleThreadExecutor();
    }

    public boolean start() {
        try {

            // 新建一个选择器
            selector = Selector.open();
            // 打开一个通道
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            // 配置通道为非阻塞
            serverSocketChannel.configureBlocking(false);
            // 绑定一个本地的端口
            serverSocketChannel.socket().bind(new InetSocketAddress(port));
            // 注册客户端连接到达的监听
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            this.server = serverSocketChannel;
            System.out.println("服务器信息：" + serverSocketChannel.getLocalAddress().toString());

            // 启动客户端监听
            this.start();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public void stop() {
        CloseUtils.close(server);
        CloseUtils.close(selector);

        synchronized (TCPServer.this) {
            // 这是一个多线程操作，这里使用同步
            for (ClientHandler clientHandler : clientHandlerList) {
                clientHandler.exit();
            }
            clientHandlerList.clear();
        }
        forwardingThreadPool.shutdown();
    }

    public synchronized void broadcast(String str) {
        for (ClientHandler clientHandler : clientHandlerList) {
            clientHandler.send(str);
        }
        // 发送数量增加
        sendSize += clientHandlerList.size();
    }

    @Override
    public synchronized void onSelfClosed(ClientHandler handler) {
        clientHandlerList.remove(handler);
    }

    @Override
    public void onNewMsgArrived(final ClientHandler handler, final String msg) {
        // 这里新起一个线程执行,这里就是一次转发，先接收到消息，然后转发到其他客户端
//        System.out.println("服务端对其他客户端转发数据");
        receiveSize++;
        forwardingThreadPool.execute(() -> {
            synchronized (TCPServer.this) {
                for (ClientHandler clientHandler : clientHandlerList) {
                    if (clientHandler.equals(handler)) {
                        // 如果是我自己发的消息，那么就进入到下一个处理
                        // 这里是因为要将消息发送到其他客户端中
                        continue;
                    }
                    clientHandler.send(msg);
                    sendSize++;
                }
            }
        });
    }

    Object[] getStatusString() {

        return new String[]{
            "客户端数量: " + clientHandlerList.size(),
            "发送数量: " + sendSize,
            "接收数量: " + receiveSize
        };
    }


    @Override
    public void onNewSocketArrived(SocketChannel socketChannel) {
        try {
            // 客户端构建异步线程
            System.out.println("构建对该客户端的异步处理线程");
            ClientHandler clientHandler = new ClientHandler(socketChannel,
                TCPServer.this, cachePath);
            System.out.println(clientHandler.getClientInfo() + " :Connected");
            // 这里相当于把这个连接保存在本地内存中
            synchronized (TCPServer.this) {
                clientHandlerList.add(clientHandler);
                System.out.println("当前客户端数量:" + clientHandlerList.size());
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("客户端连接异常：" + e.getMessage());
        }
    }
}
