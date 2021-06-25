package net.qiujuer.lesson.sample.server;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import net.qiujuer.library.clink.utils.CloseUtils;

/**
 * 针对客户端连接的处理
 *
 * @author YJ
 * @date 2021/6/25
 **/
public class ServerAcceptor extends Thread {

    private final AcceptListener listener;
    private final Selector selector;

    private boolean done = false;

    private final CountDownLatch latch = new CountDownLatch(1);

    ServerAcceptor(AcceptListener listener) throws IOException {
        super("Server-Acceptor-Thread");
        this.listener = listener;
        this.selector = Selector.open();
    }


    /**
     * 等待启动
     */
    private boolean awaitRunning() {
        try {
            latch.await();
        } catch (InterruptedException e) {
            return false;
        }
        return true;
    }

    @Override
    public void run() {

        latch.countDown();
        Selector selector = this.selector;
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
                        listener.onNewSocketArrived(socketChannel);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } while (!done);
        System.out.println("ServerAcceptor Finished！");
    }

    void exit() {
        done = true;
        CloseUtils.close(selector);
    }

    interface AcceptListener {
        void onNewSocketArrived(SocketChannel channel);
    }
}
