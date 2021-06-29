package net.qiujuer.lesson.sample.client;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.qiujuer.lesson.sample.client.bean.ServerInfo;
import net.qiujuer.lesson.sample.foo.Foo;
import net.qiujuer.library.clink.core.IoContext;
import net.qiujuer.library.clink.impl.IoSelectorProvider;
import net.qiujuer.library.clink.impl.SchedulerImpl;

/**
 * <p>测试类，可以使用JvirtualVM进行测试
 * java -cp jar包 执行的类的包+类名称
 */
public class ClientTest {

    private static boolean done;

    public static void main(String[] args) throws IOException {

        File cachePath = Foo.getCacheDir("client/test");
        IoContext.setup()
            .ioProvider(new IoSelectorProvider())
            .schedule(new SchedulerImpl(1))
            .start();

        // 启动UDP搜索服务，发送广播消息
        ServerInfo info = UDPSearcher.searchServer(10000);
        System.out.println("Server:" + info);

        if (Objects.isNull(info)) {
            return;
        }

        // 当前的连接数量
        int size = 0;
        List<TCPClient> tcpClients = new ArrayList<>(4);
        // 创建多个客户端
        for (int i = 0; i < 200; i++) {
            try {
                TCPClient tcpClient = TCPClient.startWith(info, cachePath);
                if (Objects.isNull(tcpClient)) {
                    // 当连接异常的时候其实后面的连接出现异常的概率也非常大，这里改为直接抛异常
//                    System.out.println("连接异常");
//                    continue;
                    throw new NullPointerException();
                }
                tcpClients.add(tcpClient);
                System.out.println("连接成功" + (++size));
            } catch (IOException | NullPointerException e) {
                System.out.println("连接异常");
                break;
            }
        }
        System.in.read();
        Thread thread = new Thread(() -> {
            while (!done) {
                tcpClients.forEach(tcpClient -> {
                    tcpClient.send("Hello~");
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });
            }
        });
        thread.start();

        System.in.read();
        done = true;
        try {
            // 等待线程完成
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // 客户端结束操作
        tcpClients.forEach(TCPClient::exit);

        IoContext.close();
    }
}
