package net.qiujuer.lesson.sample.server;

import static net.qiujuer.lesson.sample.foo.Foo.COMMAND_EXIT;

import java.io.File;
import net.qiujuer.lesson.sample.foo.Foo;
import net.qiujuer.lesson.sample.foo.FooGui;
import net.qiujuer.lesson.sample.foo.constants.TCPConstants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import net.qiujuer.library.clink.core.IoContext;
import net.qiujuer.library.clink.impl.IoSelectorProvider;

/**
 * <p> 由于依赖其他工程，后面需要打成jar包运行
 * <p> 这里验证的时候需要启动一个服务器，多个客户端
 * <p> 在实际聊条软件中，一般大家都是连接服务器，在一个聊天室内，如果一个人发送一条消息，那么其他人都会收到消息
 * <p> 而消息是先被服务器收到，然后再转发给各个成员的，发送消息的那个人除外
 */
public class Server {

    public static void main(String[] args) throws IOException {

        File cachePath = Foo.getCacheDir("server");

        IoContext.setup().ioProvider(new IoSelectorProvider()).start();

        // 启动一个TCP监听服务，监听30401
        TCPServer tcpServer = new TCPServer(TCPConstants.PORT_SERVER, cachePath);
        // 启动监听
        boolean isSucceed = tcpServer.start();
        if (!isSucceed) {
            System.out.println("Start TCP server failed!");
            return;
        }

        // 启动一个UDP服务，用户监听广播消息，监听30201
        UDPProvider.start(TCPConstants.PORT_SERVER);

        // 启动Gui界面
        FooGui gui = new FooGui("Clink-Server", () -> tcpServer.getStatusString());
        gui.doShow();

        // 读取键盘输入，这里可以发送群消息
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
        String str;
        do {
            str = bufferedReader.readLine();
            if (null == str || COMMAND_EXIT.equalsIgnoreCase(str)) {
                break;
            }
            if (str.length() == 0) {
                continue;
            }
            // 向所有的TCP连接发送消息
            tcpServer.broadcast(str);
        } while (true);

        UDPProvider.stop();
        tcpServer.stop();

        IoContext.close();
        gui.doDismiss();
    }
}
