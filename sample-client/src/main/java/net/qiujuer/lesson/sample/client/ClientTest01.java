package net.qiujuer.lesson.sample.client;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.qiujuer.lesson.sample.client.bean.ServerInfo;
import net.qiujuer.lesson.sample.foo.Foo;
import net.qiujuer.lesson.sample.foo.handle.ConnectorCloseChain;
import net.qiujuer.lesson.sample.foo.handle.ConnectorHandler;
import net.qiujuer.library.clink.core.Connector;
import net.qiujuer.library.clink.core.IoContext;
import net.qiujuer.library.clink.impl.IoSelectorProvider;
import net.qiujuer.library.clink.impl.SchedulerImpl;
import net.qiujuer.library.clink.utils.CloseUtils;

/**
 * 12-性能提升
 * <p>测试类，可以使用JvirtualVM进行测试
 * java -cp jar包 执行的类的包+类名称
 */
public class ClientTest01 {

    /**
     * 不考虑发送消耗，并发量：2000*4/400*1000 = 2w/s
     */
    private static final int CLIENT_SIZE = 2000;
    /**
     * 并发4个线程
     */
    private static final int SEND_THREAD_SIZE = 4;
    /**
     * 每个线程间隔400ms
     */
    private static final int SEND_THREAD_DELAY = 400;

    private static boolean done;

    public static void main(String[] args) throws IOException {
        // 启动UDP搜索服务，发送广播消息
        ServerInfo info = UDPSearcher.searchServer(10000);
        System.out.println("Server:" + info);
        if (Objects.isNull(info)) {
            return;
        }

        File cachePath = Foo.getCacheDir("client/test");
        IoContext.setup()
            .ioProvider(new IoSelectorProvider())
            .schedule(new SchedulerImpl(1))
            .start();

        // 当前的连接数量
        int size = 0;
        List<TCPClient> tcpClients = new ArrayList<>(CLIENT_SIZE);

        // 关闭时移除
        final ConnectorCloseChain closeChain = new ConnectorCloseChain() {
            @Override
            protected boolean consume(ConnectorHandler handler, Connector connector) {
                // noinspection SuspiciousMethodCalls
                tcpClients.remove(handler);
                if (tcpClients.size() == 0) {
                    CloseUtils.close(System.in);
                }
                return false;
            }
        };

        // 创建多个客户端
        for (int i = 0; i < CLIENT_SIZE; i++) {
            try {
                TCPClient tcpClient = TCPClient.startWith(info, cachePath, false);
                if (Objects.isNull(tcpClient)) {
                    // 当连接异常的时候其实后面的连接出现异常的概率也非常大，这里改为直接抛异常
                    throw new NullPointerException();
                }
                // 添加关闭链式节点
                tcpClient.getCloseChain().appendLast(closeChain);
                tcpClients.add(tcpClient);
                System.out.println("连接成功" + (++size));
            } catch (IOException | NullPointerException e) {
                System.out.println("连接异常");
                break;
            }
        }

        // 输入任意字符开始发送消息
        System.in.read();
        Runnable runnable = () -> {
            while (!done) {
                TCPClient[] copyClients = tcpClients.toArray(new TCPClient[0]);
                for (TCPClient client : copyClients) {
                    client.send("Hello~~");
                }
                if (SEND_THREAD_DELAY > 0) {
                    try {
                        Thread.sleep(SEND_THREAD_DELAY);
                    } catch (InterruptedException e) {
                    }
                }
            }
        };

        List<Thread> threads = new ArrayList<>(SEND_THREAD_SIZE);
        for (int i = 0; i < SEND_THREAD_SIZE; i++) {
            Thread t = new Thread(runnable);
            t.start();
            threads.add(t);
        }

        // 这里通过输入来结束发送
        System.in.read();
        done = true;
        // 客户端结束操作
        TCPClient[] copyClients = tcpClients.toArray(new TCPClient[0]);
        for (TCPClient client : copyClients) {
            client.exit();
        }

        // 关闭框架结束操作
        IoContext.close();
        // 强制接收处于等待的线程
        for (Thread t : threads) {
            try {
                t.interrupt();
            } catch (Exception e) {
            }
        }
    }
}
