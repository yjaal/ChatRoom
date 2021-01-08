package net.qiujuer.lesson.sample.server.handle;


import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
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

    private final SocketChannel socketChannel;
    private final ClientReadHandler readHandler;
    private final ClientWriteHandler writeHandler;
    private final ClientHandlerCallback clientHandlerCallback;
    /**
     * 自身的一个描述信息
     */
    private final String clientInfo;

    public ClientHandler(SocketChannel socketChannel, ClientHandlerCallback clientHandlerCallback) throws IOException {
        this.socketChannel = socketChannel;
        // 设置非阻塞
        socketChannel.configureBlocking(false);

        Selector rSelector = Selector.open();
        socketChannel.register(rSelector, SelectionKey.OP_READ);
        // 读取流
        this.readHandler = new ClientReadHandler(rSelector);

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
        readHandler.exit();
        writeHandler.exit();
        CloseUtils.close(socketChannel);
        System.out.println("客户端已退出：" + clientInfo);
    }

    public void send(String str) {
        writeHandler.send(str);
    }

    public void readToPrint() {
        readHandler.start();
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

    class ClientReadHandler extends Thread {

        private boolean done = false;
        private final Selector selector;
        private final ByteBuffer byteBuffer;

        ClientReadHandler(Selector selector) {
            this.selector = selector;
            this.byteBuffer = ByteBuffer.allocate(256);
        }

        @Override
        public void run() {
            try {
                do {
                    // 从客户端拿到一条数据
                    if (selector.select() == 0) {
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

                        if (key.isReadable()) {
                            SocketChannel rChannel = (SocketChannel) key.channel();
                            byteBuffer.clear();
                            int rCount = rChannel.read(byteBuffer);
                            if (rCount > 0) {
                                // 需要丢弃换行符
                                String str = new String(byteBuffer.array(), 0, rCount);
                                clientHandlerCallback.onNewMsgArrived(ClientHandler.this, str);
                            } else {
                                System.out.println("客户端已无法读取数据！");
                                // 退出当前客户端
                                ClientHandler.this.exitBySelf();
                                break;
                            }

                        }
                    }
                } while (!done);
            } catch (Exception e) {
                if (!done) {
                    System.out.println("连接异常断开");
                    ClientHandler.this.exitBySelf();
                }
            } finally {
                // 连接关闭
                done = true;
                // 唤醒
                selector.wakeup();
                CloseUtils.close(selector);
            }
        }

        void exit() {
            done = true;
            CloseUtils.close(selector);
        }
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
