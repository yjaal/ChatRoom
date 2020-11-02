package net.qiujuer.lesson.sample.server;

import java.net.InetSocketAddress;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.qiujuer.lesson.sample.server.handle.ClientHandler;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * 一个TCP server可能会有多个 ClientHandler 处理相关消息
 */
public class TCPServer implements ClientHandler.ClientHandlerCallback{
    private final int port;
    private ClientListener mListener;
    private ClientListener listener;
    private final List<ClientHandler> clientHandlerList = new ArrayList<>();
    // 消息转发线程池
    private final ExecutorService forwardingThreadPool;
    private Selector selector;

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

            ClientListener listener = new ClientListener(port);
            mListener = listener;
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
        private ServerSocket server;
        private boolean done = false;

        private ClientListener(int port) throws IOException {
            server = new ServerSocket(port);
            System.out.println("服务器信息：" + server.getInetAddress() + " P:" + server.getLocalPort());
        }

        @Override
        public void run() {
            System.out.println("服务器准备就绪～");
            // 等待客户端连接
            do {
                // 得到客户端
                Socket client;
                try {
                    client = server.accept();
                } catch (IOException e) {
                    continue;
                }
                try {
                    // 客户端构建异步线程
                    ClientHandler clientHandler = new ClientHandler(client, TCPServer.this);
                    // 之前交给ClientHandler处理是直接打印再屏幕上，后面优化为通知回TCP Server
                    clientHandler.readToPrint();
                    synchronized (TCPServer.this) {
                        clientHandlerList.add(clientHandler);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    System.out.println("客户端连接异常：" + e.getMessage());
                }
            } while (!done);
            System.out.println("服务器已关闭！");
        }

        void exit() {
            done = true;
            try {
                server.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
