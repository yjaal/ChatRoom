package net.qiujuer.lesson.sample.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.qiujuer.lesson.sample.server.handle.ClientHandler;
import net.qiujuer.library.clink.utils.CloseUtils;

/**
 * 一个TCP server可能会有多个 ClientHandler 处理相关消息
 */
public class TCPServer implements ClientHandler.ClientHandlerCallback {

    private final int port;
    private ClientListener listener;
    private final List<ClientHandler> clientHandlerList = new ArrayList<>();
    // 消息转发线程池
    private final ExecutorService forwardingThreadPool;
    private Selector selector;
    private ServerSocketChannel server;

    public TCPServer(int port) {
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
            ClientListener listener = this.listener = new ClientListener();
            listener.start();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public void stop() {
        if (listener != null) {
            listener.exit();
        }

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
    }

    @Override
    public synchronized void onSelfClosed(ClientHandler handler) {
        clientHandlerList.remove(handler);
    }

    @Override
    public void onNewMsgArrived(final ClientHandler handler, final String msg) {

        // 这里新起一个线程执行,这里就是一次转发，先接收到消息，然后转发到其他客户端
        System.out.println("服务端对其他客户端转发数据");
        forwardingThreadPool.execute(() -> {
            synchronized (TCPServer.this) {
                for (ClientHandler clientHandler : clientHandlerList) {
                    if (clientHandler.equals(handler)) {
                        // 如果是我自己发的消息，那么就进入到下一个处理
                        // 这里是因为要将消息发送到其他客户端中
                        continue;
                    }
                    clientHandler.send(msg);
                }
            }
        });
    }

    private class ClientListener extends Thread {

        private boolean done = false;

        @Override
        public void run() {
            Selector selector = TCPServer.this.selector;
            System.out.println("服务器监听准备就绪，准备接收客户端连接");
            // 等待客户端连接
            do {
                // 得到客户端
                try {
                    // 理论上一直阻塞，表明客户端状态是唤醒的直接返回
                    if (selector.select() == 0) {
                        // 同时此时是一个完成状态
                        if (done) {
                            break;
                        }
                        continue;
                    }
                    Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
                    while (iter.hasNext()) {
                        if (done) {
                            break;
                        }
                        SelectionKey key = iter.next();
                        iter.remove();

                        // 检查当前key是否是当前关注的客户端到达的状态
                        if (key.isAcceptable()) {
                            System.out.println("监听到客户端到达");
                            ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
                            // 非阻塞状态拿到客户端连接
                            SocketChannel socketChannel = serverChannel.accept();

                            try {
                                // 客户端构建异步线程
                                System.out.println("构建对该客户端的异步处理线程");
                                ClientHandler clientHandler = new ClientHandler(socketChannel, TCPServer.this);
                                // 这里相当于把这个连接保存在本地内存中
                                synchronized (TCPServer.this) {
                                    clientHandlerList.add(clientHandler);
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                                System.out.println("客户端连接异常：" + e.getMessage());
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } while (!done);
            System.out.println("服务器已关闭！");
        }

        void exit() {
            done = true;
            // 唤醒当前的阻塞
            selector.wakeup();
        }
    }
}
