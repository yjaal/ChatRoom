package net.qiujuer.lesson.sample.client;


import net.qiujuer.lesson.sample.client.bean.ServerInfo;

import java.io.IOException;

/**
 * <p>由于依赖其他工程，后面需要达成jar包运行
 * 这里验证的时候需要启动一个服务器，多个客户端
 */
public class Client {

    public static void main(String[] args) {
        // 启动UDP搜索服务，发送广播消息
        ServerInfo info = UDPSearcher.searchServer(10000);
        System.out.println("Server:" + info);

        if (info != null) {
            try {
                // 连接TCP服务
                TCPClient.linkWith(info);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
