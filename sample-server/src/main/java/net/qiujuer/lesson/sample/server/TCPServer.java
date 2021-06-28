package net.qiujuer.lesson.sample.server;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.qiujuer.lesson.sample.foo.Foo;
import net.qiujuer.lesson.sample.server.ServerAcceptor.AcceptListener;
import net.qiujuer.lesson.sample.server.handle.ClientHandler;
import net.qiujuer.lesson.sample.server.handle.ConnectorCloseChain;
import net.qiujuer.lesson.sample.server.handle.ConnectorStringPacketChain;
import net.qiujuer.library.clink.box.StringReceivePacket;
import net.qiujuer.library.clink.core.Connector;
import net.qiujuer.library.clink.utils.CloseUtils;

/**
 * 一个TCP server可能会有多个 ClientHandler 处理相关消息
 */
public class TCPServer implements AcceptListener, Group.GroupMsgAdapter {

    private final int port;
    private final List<ClientHandler> clientHandlerList = new ArrayList<>();
    /**
     * 消息转发线程池
     */
    private final ExecutorService deliveryPool;
    private ServerSocketChannel server;
    private ServerAcceptor acceptor;
    private final File cachePath;
    private final ServerStatistics statistics = new ServerStatistics();
    private final Map<String, Group> groups = new HashMap<>();

    public TCPServer(int port, File cachePath) {
        this.cachePath = cachePath;
        this.port = port;
        this.deliveryPool = Executors.newSingleThreadExecutor();
        this.groups.put(Foo.DEFAULT_GROUP_NAME, new Group(Foo.DEFAULT_GROUP_NAME, this));
    }

    public boolean start() {
        try {
            ServerAcceptor acceptor = new ServerAcceptor(this);
            // 打开一个通道
            ServerSocketChannel server = ServerSocketChannel.open();
            // 配置通道为非阻塞
            server.configureBlocking(false);
            // 绑定一个本地的端口
            server.socket().bind(new InetSocketAddress(port));
            // 注册客户端连接到达的监听
            server.register(acceptor.getSelector(), SelectionKey.OP_ACCEPT);

            this.server = server;
            this.acceptor = acceptor;

            // 线程启动
            acceptor.start();
            if (acceptor.awaitRunning()) {
                System.out.println("服务器准备就绪---");
                System.out.println("服务器信息：" + server.getLocalAddress().toString());
            } else {
                System.out.println("启动异常");
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public void stop() {

        if (acceptor != null) {
            acceptor.exit();
        }

        synchronized (clientHandlerList) {
            // 这是一个多线程操作，这里使用同步
            for (ClientHandler clientHandler : clientHandlerList) {
                clientHandler.exit();
            }
            clientHandlerList.clear();
        }
        CloseUtils.close(server);
        deliveryPool.shutdown();
    }


    void broadcast(String str) {
        str = "系统通知: " + str;
        synchronized (clientHandlerList) {
            for (ClientHandler clientHandler : clientHandlerList) {
                sendMsg2Client(clientHandler, str);
            }
        }
    }

    @Override
    public void sendMsg2Client(ClientHandler handler, String msg) {
        handler.send(msg);
        statistics.sendSize++;
    }

    Object[] getStatusString() {

        return new String[]{
            "客户端数量: " + clientHandlerList.size(),
            "发送数量: " + statistics.sendSize,
            "接收数量: " + statistics.receiveSize
        };
    }

    @Override
    public void onNewSocketArrived(SocketChannel socketChannel) {
        try {
            // 客户端构建异步线程
            System.out.println("构建对该客户端的异步处理线程");
            ClientHandler clientHandler = new ClientHandler(socketChannel, cachePath, deliveryPool);
            System.out.println(clientHandler.getClientInfo() + ": Connected");

            clientHandler.getStringPacketChain()
                .appendLast(statistics.statisticsChain())
                .appendLast(new ParseCommandConnectorStrPacket());

            clientHandler.getCloseChain().appendLast(new RemoveQueueOnCloseConnect());
            // 这里相当于把这个连接保存在本地内存中
            synchronized (clientHandlerList) {
                clientHandlerList.add(clientHandler);
                System.out.println("当前客户端数量:" + clientHandlerList.size());
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("客户端连接异常：" + e.getMessage());
        }
    }

    private class RemoveQueueOnCloseConnect extends ConnectorCloseChain {

        @Override
        protected boolean consume(ClientHandler handler, Connector connector) {
            synchronized (clientHandlerList) {
                clientHandlerList.remove(handler);
                // 从群聊中移除
                Group group = groups.get(Foo.DEFAULT_GROUP_NAME);
                group.removeMember(handler);
            }
            return true;
        }
    }

    private class ParseCommandConnectorStrPacket extends ConnectorStringPacketChain {

        @Override
        protected boolean consume(ClientHandler handler, StringReceivePacket packet) {
            String str = packet.entity();
            if (str.startsWith(Foo.COMMAND_GROUP_JOIN)) {
                Group group = groups.get(Foo.DEFAULT_GROUP_NAME);
                if (group.addMember(handler)) {
                    sendMsg2Client(handler, "Join Group: " + group.getName());
                }
                return true;
            } else if (str.startsWith(Foo.COMMAND_GROUP_LEAVE)) {
                Group group = groups.get(Foo.DEFAULT_GROUP_NAME);
                if (group.removeMember(handler)) {
                    sendMsg2Client(handler, "Leave Group: " + group.getName());
                }
                return true;
            }
            return false;
        }

        /**
         * 二次消费
         */
        @Override
        protected boolean consumeAgain(ClientHandler handler, StringReceivePacket packet) {
            // 捡漏模式，当第一次未消费，又没有加入到群，自然没有后续的节点消费
            // 此时进行二次消费，返回发送过来的消息
            sendMsg2Client(handler, packet.entity());
            return true;
        }
    }
}
