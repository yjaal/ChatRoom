package net.qiujuer.lesson.sample.server.handle;


import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import net.qiujuer.library.clink.core.Connector;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import sun.misc.Cleaner;

/**
 * <p>这个类就是一个接收发送消息的处理类，TCP server一方面接收来自某个客户端发送的消息
 * <p>一方面又将这个消息转发出去，当然不能发给自己
 */
public class ClientHandler {

    private final Connector connector;
    private final SocketChannel socketChannel;
    private final ClientWriteHandler writeHandler;
    private final ClientHandlerCallback clientHandlerCallback;

    /**
     * 自身的一个描述信息
     */
    private final String clientInfo;

    public ClientHandler(SocketChannel socketChannel, ClientHandlerCallback clientHandlerCallback) throws IOException {
        this.socketChannel = socketChannel;

        connector = new Connector() {
            @Override
            public void onChannelClosed(SocketChannel channel) {
                exitBySelf();
            }

            @Override
            protected void onReceiveNewMsg(String str) {
                clientHandlerCallback.onNewMsgArrived(ClientHandler.this, str);
            }
        };
        connector.setup(socketChannel);

        Selector wSelector = Selector.open();
        socketChannel.register(wSelector, SelectionKey.OP_WRITE);
        // 发送流
        this.writeHandler = new ClientWriteHandler(wSelector);

        this.clientHandlerCallback = clientHandlerCallback;
        this.clientInfo = socketChannel.getRemoteAddress().toString();
        System.out.println("新客户端连接：" + clientInfo);
    }

    public String getClientInfo() {
        return clientInfo;
    }

    public void exit() {
        CloseUtils.close(connector);
        writeHandler.exit();
        CloseUtils.close(socketChannel);
        System.out.println("客户端已退出：" + clientInfo);
    }

    public void send(String str) {
        writeHandler.send(str);
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

    class ClientWriteHandler {

        private boolean done = false;
        private final Selector selector;
        private final ByteBuffer byteBuffer;
        private final ExecutorService executorService;

        ClientWriteHandler(Selector selector) {
            this.selector = selector;
            byteBuffer = ByteBuffer.allocate(256);
            this.executorService = Executors.newSingleThreadExecutor();
        }

        void exit() {
            done = true;
            CloseUtils.close(selector);
            executorService.shutdownNow();
        }

        void send(String str) {
            if (done) {
                // 如果说自己已经完成了直接返回
                return;
            }
            executorService.execute(new WriteRunnable(str));
        }

        class WriteRunnable implements Runnable {

            private final String msg;

            WriteRunnable(String msg) {
                // 这里加上一个换行符主要是客户端在读取的时候会将换行符丢弃
                this.msg = msg + '\n';
            }

            @Override
            public void run() {
                if (ClientWriteHandler.this.done) {
                    return;
                }
                // 先将缓存清空
                byteBuffer.clear();
                byteBuffer.put(msg.getBytes());
                // 反转操作，其实就是指定发送数据时指定起始位置
                byteBuffer.flip();

                // 判断当前缓存中是否还有数据
                while (!done && byteBuffer.hasRemaining()) {
                    try {
                        int wLen = socketChannel.write(byteBuffer);
                        // 这里注意 wLen=0是合法的
                        if (wLen < 0) {
                            System.out.println("客户端无法发送数据");
                            ClientHandler.this.exitBySelf();
                            break;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
