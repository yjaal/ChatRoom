package net.qiujuer.lesson.sample.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.qiujuer.lesson.sample.client.bean.ServerInfo;

/**
 * <p>测试类，可以使用JvirtualVM进行测试
 * java -cp jar包 执行的类的包+类名称
 */
public class ClientTest {

    private static boolean done;

    public static void main(String[] args) throws IOException {
        // 启动UDP搜索服务，发送广播消息
        ServerInfo info = UDPSearcher.searchServer(10000);
        System.out.println("Server:" + info);

        if (Objects.isNull(info)) {
            return;
        }

        // 当前的连接数量
        int size = 0;
        List<TCPClient> tcpClients = new ArrayList<>(size);
        // 创建多个客户端
        for (int i = 0; i < 10; i++) {
            try {
                TCPClient tcpClient = TCPClient.startWith(info);
                if (Objects.isNull(tcpClient)) {
                    System.out.println("连接异常");
                    continue;
                }
                tcpClients.add(tcpClient);
                System.out.println("连接成功" + (++size));
            } catch (IOException e) {
                System.out.println("连接异常");
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.in.read();
        Thread thread = new Thread(() -> {
            while (!done) {
                tcpClients.forEach(tcpClient -> {
                    tcpClient.send("");
                    try {
                        Thread.sleep(1000);
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
        tcpClients.forEach(tcpClient -> tcpClient.exit());
    }
}
