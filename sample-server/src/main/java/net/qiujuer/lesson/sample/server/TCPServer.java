package net.qiujuer.lesson.sample.server;

import java.net.InetSocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.qiujuer.lesson.sample.server.handle.ClientHandler;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import net.qiujuer.library.clink.utils.CloseUtils;

/**
 * 一个TCP server可能会有多个 ClientHandler 处理相关消息
 */
public class TCPServer implements ClientHandler.ClientHandlerCallback {

    private final int port;
    private ClientListener mListener;
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
            System.out.println("服务器信息：" + serverSocketChannel.getLocalAddress());

            // 启动客户端监听
            ClientListener listener = this.mListener = new ClientListener();
            listener.start();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public void stop() {
        if (mListener != null) {
            mListener.exit();
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
        // 消息打印到屏幕
        System.out.println("Received-" + handler.getClientInfo() + " : " + msg);

        // 这里新起一个线程执行,这里就是一次转发，先接收到消息，然后转发到其他客户端
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
            System.out.println("服务器准备就绪～");
            // 等待客户端连接
            do {
                // 得到客户端
                try {
                    // 理论上一直阻塞，表明客户端状态是唤醒的直接返回
                    if (selector.select() == 0) {
                        // 同时此时是一个完成状态
                        if (done) {
                            return;
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
                            ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
                            // 非阻塞状态拿到客户端连接
                            SocketChannel socketChannel = serverChannel.accept();

                            try {
                                // 客户端构建异步线程
                                ClientHandler clientHandler = new ClientHandler(socketChannel, TCPServer.this);
                                // 之前交给ClientHandler处理是直接打印再屏幕上，后面优化为通知回TCP Server
                                // 这里其实就是读取从客户端收到的数据
                                clientHandler.readToPrint();

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
