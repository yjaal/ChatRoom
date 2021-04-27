package net.qiujuer.lesson.sample.client;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Objects;
import net.qiujuer.lesson.sample.client.bean.ServerInfo;
import net.qiujuer.library.clink.core.IoContext;
import net.qiujuer.library.clink.impl.IoSelectorProvider;

/**
 * <p>由于依赖其他工程，后面需要打成jar包运行
 * 这里验证的时候需要启动一个服务器，多个客户端
 */
public class Client {

    public static void main(String[] args) throws IOException {

        IoContext.setup().ioProvider(new IoSelectorProvider()).start();

        // 启动UDP搜索服务，发送广播消息
        ServerInfo info = UDPSearcher.searchServer(10000);
        System.out.println("Server:" + info);

        if (info != null) {
            TCPClient tcpClient = null;
            try {
                // 连接TCP服务
                tcpClient = TCPClient.startWith(info);
                if (Objects.isNull(tcpClient)) {
                    return;
                }
                write(tcpClient);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                tcpClient.exit();
            }
        }

        IoContext.close();
    }

    /**
     * 默认键盘输出
     */
    private static void write(TCPClient tcpClient) throws IOException {
        // 构建键盘输入流
        InputStream in = System.in;
        BufferedReader input = new BufferedReader(new InputStreamReader(in));
        do {
            // 键盘读取一行
            String str = input.readLine();
            // 发送到服务器
            tcpClient.send(str);
            tcpClient.send(str);
            tcpClient.send(str);
            tcpClient.send(str);

            if ("00bye00".equalsIgnoreCase(str)) {
                break;
            }
        } while (true);
    }
}
