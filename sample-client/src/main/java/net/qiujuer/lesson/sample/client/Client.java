package net.qiujuer.lesson.sample.client;


import static net.qiujuer.lesson.sample.foo.Foo.COMMAND_EXIT;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import net.qiujuer.lesson.sample.client.bean.ServerInfo;
import net.qiujuer.lesson.sample.foo.Foo;
import net.qiujuer.lesson.sample.foo.handle.ConnectorCloseChain;
import net.qiujuer.lesson.sample.foo.handle.ConnectorHandler;
import net.qiujuer.library.clink.box.FileSendPacket;
import net.qiujuer.library.clink.core.Connector;
import net.qiujuer.library.clink.core.IoContext;
import net.qiujuer.library.clink.core.schedule.IdleTimeoutScheduleJob;
import net.qiujuer.library.clink.impl.IoSelectorProvider;
import net.qiujuer.library.clink.impl.SchedulerImpl;
import net.qiujuer.library.clink.utils.CloseUtils;

/**
 * <p>由于依赖其他工程，后面需要打成jar包运行
 * 这里验证的时候需要启动一个服务器，多个客户端
 */
public class Client {

    public static void main(String[] args) throws IOException {
        File cachePath = Foo.getCacheDir("client");
        IoContext.setup()
            .ioProvider(new IoSelectorProvider())
            .schedule(new SchedulerImpl(1))
            .start();

        // 启动UDP搜索服务，发送广播消息
        ServerInfo info = UDPSearcher.searchServer(10000);
        System.out.println("Server:" + info);

        if (info != null) {
            TCPClient tcpClient = null;
            try {
                // 连接TCP服务
                tcpClient = TCPClient.startWith(info, cachePath);
                if (Objects.isNull(tcpClient)) {
                    return;
                }
                // 当服务器端关闭连接后，客户端需要对其进行处理
                tcpClient.getCloseChain().appendLast(new ConnectorCloseChain() {
                    @Override
                    protected boolean consume(ConnectorHandler handler, Connector connector) {
                        CloseUtils.close(System.in);
                        return true;
                    }
                });
                // 一开始需要将客户端当心跳添加进去
                IdleTimeoutScheduleJob job = new IdleTimeoutScheduleJob(10,
                    TimeUnit.SECONDS, tcpClient);
                tcpClient.schedule(job);
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
            // 直接停止
            if (null == str || COMMAND_EXIT.equalsIgnoreCase(str)) {
                break;
            }
            if (str.length() == 0) {
                continue;
            }
            // 自定义文件发送格式 --file url
            if (str.startsWith("--file")) {
                String[] arr = str.split(" ");
                if (arr.length >= 2) {
                    String filePath = arr[1];
                    File file = new File(filePath);
                    if (file.exists() && file.isFile()) {
                        FileSendPacket fileSendPacket = new FileSendPacket(file);
                        tcpClient.send(fileSendPacket);
                        continue;
                    }
                }
            }

            // 字符串发送
            tcpClient.send(str);
        } while (true);
    }
}
