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
import java.util.concurrent.TimeUnit;
import net.qiujuer.lesson.sample.foo.Foo;
import net.qiujuer.lesson.sample.foo.handle.ConnectorCloseChain;
import net.qiujuer.lesson.sample.foo.handle.ConnectorHandler;
import net.qiujuer.lesson.sample.foo.handle.ConnectorHandlerChain;
import net.qiujuer.lesson.sample.foo.handle.ConnectorStringPacketChain;
import net.qiujuer.lesson.sample.server.ServerAcceptor.AcceptListener;
import net.qiujuer.lesson.sample.server.audio.AudioRoom;
import net.qiujuer.library.clink.box.StringReceivePacket;
import net.qiujuer.library.clink.core.Connector;
import net.qiujuer.library.clink.core.schedule.IdleTimeoutScheduleJob;
import net.qiujuer.library.clink.utils.CloseUtils;

/**
 * 一个TCP server可能会有多个 ClientHandler 处理相关消息
 */
public class TCPServer implements AcceptListener, Group.GroupMsgAdapter {

    private final int port;
    private final List<ConnectorHandler> connectorHandlers = new ArrayList<>();
    private ServerSocketChannel server;
    private ServerAcceptor acceptor;
    private final File cachePath;
    private final ServerStatistics statistics = new ServerStatistics();
    private final Map<String, Group> groups = new HashMap<>();

    public TCPServer(int port, File cachePath) {
        this.cachePath = cachePath;
        this.port = port;
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

        // 这里不能直接移除，因为在关闭消费时会移除，如果直接移除，则在关闭当时候会报异常
        ConnectorHandler[] connectorHandlerArr;
        synchronized (connectorHandlers) {
            connectorHandlerArr = connectorHandlers.toArray(new ConnectorHandler[0]);
            connectorHandlers.clear();

            // 这是一个多线程操作，这里使用同步
            for (ConnectorHandler connectorHandler : connectorHandlerArr) {
                connectorHandler.exit();
            }
        }
        CloseUtils.close(server);
    }


    void broadcast(String str) {
        str = "系统通知: " + str;
        ConnectorHandler[] connectorHandlerArr;
        synchronized (connectorHandlers) {
            connectorHandlerArr = connectorHandlers.toArray(new ConnectorHandler[0]);
            // 这是一个多线程操作，这里使用同步
            for (ConnectorHandler connectorHandler : connectorHandlerArr) {
                sendMsg2Client(connectorHandler, str);
            }
        }
    }

    @Override
    public void sendMsg2Client(ConnectorHandler handler, String msg) {
        handler.send(msg);
        statistics.sendSize++;
    }

    Object[] getStatusString() {

        return new String[]{
            "客户端数量: " + connectorHandlers.size(),
            "发送数量: " + statistics.sendSize,
            "接收数量: " + statistics.receiveSize
        };
    }

    @Override
    public void onNewSocketArrived(SocketChannel socketChannel) {
        try {
            // 客户端构建异步线程
            System.out.println("构建对该客户端的异步处理线程");
            ConnectorHandler connectorHandler = new ConnectorHandler(socketChannel, cachePath);
            System.out.println(connectorHandler.getClientInfo() + ": Connected");

            connectorHandler.getStringPacketChain()
                .appendLast(statistics.statisticsChain())
                .appendLast(new ParseCommandConnectorStrPacket())
                .appendLast(new ParseAudioStreamCommandStringPacketChain());

            connectorHandler.getCloseChain()
                .appendLast(new RemoveAudioQueueOnConnectorClosedChain())
                .appendLast(new RemoveQueueOnCloseConnect());

            // 服务端的心跳
            // 当发现新当socket连接时将心跳调度任务设置进去，每20s发送一次心跳
            IdleTimeoutScheduleJob job = new IdleTimeoutScheduleJob(20,
                TimeUnit.SECONDS, connectorHandler);
            connectorHandler.schedule(job);

            // 这里相当于把这个连接保存在本地内存中
            synchronized (connectorHandlers) {
                connectorHandlers.add(connectorHandler);
                System.out.println("当前客户端数量:" + connectorHandlers.size());
            }

            // 会送客户端在服务器的唯一标志
            sendMsg2Client(connectorHandler, Foo.COMMAND_INFO_NAME + connectorHandler.getKey().toString());
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("客户端连接异常：" + e.getMessage());
        }
    }

    private class RemoveQueueOnCloseConnect extends ConnectorCloseChain {

        @Override
        protected boolean consume(ConnectorHandler handler, Connector connector) {
            synchronized (connectorHandlers) {
                connectorHandlers.remove(handler);
                // 从群聊中移除
                Group group = groups.get(Foo.DEFAULT_GROUP_NAME);
                group.removeMember(handler);
            }
            return true;
        }
    }

    private class ParseCommandConnectorStrPacket extends ConnectorStringPacketChain {

        @Override
        protected boolean consume(ConnectorHandler handler, StringReceivePacket packet) {
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
        protected boolean consumeAgain(ConnectorHandler handler, StringReceivePacket packet) {
            // 捡漏模式，当第一次未消费，又没有加入到群，自然没有后续的节点消费
            // 此时进行二次消费，返回发送过来的消息
            sendMsg2Client(handler, packet.entity());
            return true;
        }
    }

    // 音频命令控制与数据流传输链接映射表
    private final Map<ConnectorHandler, ConnectorHandler> audioCmdToStreamMap = new HashMap<>(128);
    private final Map<ConnectorHandler, ConnectorHandler> audioStreamToCmdMap = new HashMap<>(128);
    // 房间映射表，房间号-房间映射
    private final Map<String, AudioRoom> audioRoomMap = new HashMap<>(64);
    // 连接与房间的映射表，音频连接-房间的映射
    private final Map<ConnectorHandler, AudioRoom> audioStreamRoomMap = new HashMap<>(64);

    private class ParseAudioStreamCommandStringPacketChain extends ConnectorHandlerChain<StringReceivePacket> {

        @Override
        protected boolean consume(ConnectorHandler handler, StringReceivePacket packet) {
            String str = packet.entity();
            if (str.startsWith(Foo.COMMAND_CONNECTOR_BIND)) {
                // 绑定命令解析，也就是将音频流绑定到当前的命令流上
                String key = str.substring(Foo.COMMAND_CONNECTOR_BIND.length());
                ConnectorHandler audioStreamConnector = findConnectorFromKey(key);
                if (audioStreamConnector != null) {
                    // 添加绑定关系
                    audioCmdToStreamMap.put(handler, audioStreamConnector);
                    audioStreamToCmdMap.put(audioStreamConnector, handler);

                    // 转换为桥接模式
                    audioStreamConnector.change2bridge();
                }
            } else if (str.startsWith(Foo.COMMAND_AUDIO_CREATE_ROOM)) {
                // 创建房间操作
                ConnectorHandler audioStreamConnector = findAudioStreamConnector(handler);
                if (audioStreamConnector != null) {
                    // 随机创建房间
                    AudioRoom room = createNewRoom();
                    // 加入一个客户端
                    joinRoom(room, audioStreamConnector);
                    sendMsg2Client(handler, Foo.COMMAND_AUDIO_CREATE_ROOM + room.getRoomCode());
                }
            } else if (str.startsWith(Foo.COMMAND_AUDIO_LEAVE_ROOM)) {
                // 离开房间
                ConnectorHandler audioStreamConnector = findAudioStreamConnector(handler);
                if (audioStreamConnector != null) {
                    // 任意一人离开都销毁房间
                    dissovlveRoom(audioStreamConnector);
                    // 发送离开消息
                    sendMsg2Client(handler, Foo.COMMAND_INFO_AUDIO_STOP);
                }
            } else if (str.startsWith(Foo.COMMAND_AUDIO_JOIN_ROOM)) {
                // 加入房间的操作
                ConnectorHandler connectorHandler = findAudioStreamConnector(handler);
                // 取得房间号
                String roomCode = str.substring(Foo.COMMAND_AUDIO_JOIN_ROOM.length());
                AudioRoom room = audioRoomMap.get(roomCode);
                // 如果找到了房间就继续
                if (room != null && joinRoom(room, connectorHandler)) {
                    // 对方
                    ConnectorHandler otherHandler = room.getTheOtherHandler(connectorHandler);

                    // 相互搭建桥
                    otherHandler.bind2Bridge(connectorHandler.getSender());
                    connectorHandler.bind2Bridge(otherHandler.getSender());

                    // 成功加入房间
                    sendMsg2Client(handler, Foo.COMMAND_INFO_AUDIO_START);
                    // 给对方发送可以开始聊天的消息
                    sendStreamConnectorMsg(otherHandler, Foo.COMMAND_INFO_AUDIO_START);
                } else {
                    // 房间没找到，房间人员满
                    sendMsg2Client(handler, Foo.COMMAND_INFO_AUDIO_ERROR);
                }
            }
            return false;
        }

        /**
         * 通过key在全部链表中找到一个链接
         */
        private ConnectorHandler findConnectorFromKey(String key) {
            synchronized (connectorHandlers) {
                for (ConnectorHandler connectorHandler : connectorHandlers) {
                    if (connectorHandler.getKey().toString().equalsIgnoreCase(key)) {
                        return connectorHandler;
                    }
                }
            }
            return null;
        }

        /**
         * 通过音频命令控制连接寻找数据传输连接，未找到则发送错误
         */
        private ConnectorHandler findAudioStreamConnector(ConnectorHandler handler) {
            ConnectorHandler connectorHandler = audioCmdToStreamMap.get(handler);
            if (connectorHandler == null) {
                sendMsg2Client(handler, Foo.COMMAND_INFO_AUDIO_ERROR);
                return null;
            } else {
                return connectorHandler;
            }
        }
    }

    private AudioRoom createNewRoom() {
        AudioRoom room;
        do {
            room = new AudioRoom();
            // 随机生成的房间号可能重复
        } while (audioRoomMap.containsKey(room.getRoomCode()));
        // 添加到缓存列表
        audioRoomMap.put(room.getRoomCode(), room);
        return room;
    }

    /**
     * 销毁房间
     */
    private void dissovlveRoom(ConnectorHandler connectorHandler) {
        AudioRoom room = audioStreamRoomMap.get(connectorHandler);
        if (room == null) {
            return;
        }
        ConnectorHandler[] connectors = room.getConnectors();
        // 先将房间里面的链接全部退出解除
        for (ConnectorHandler connector : connectors) {
            // 解除桥接
            connector.unBind2Bridge();
            // 移除缓存
            audioStreamRoomMap.remove(connector);
            if (connector != connectorHandler) {
                // 退出房间 并 获取对方
                sendStreamConnectorMsg(connector, Foo.COMMAND_INFO_AUDIO_STOP);
            }
        }
        // 销毁房间
        audioRoomMap.remove(room.getRoomCode());
    }

    /**
     * 给链接流对应的命令控制连接发送消息
     */
    private void sendStreamConnectorMsg(ConnectorHandler connector, String msg) {
        if (connector != null) {
            ConnectorHandler audioCmdConnector = findAudioCmdConnector(connector);
            sendMsg2Client(audioCmdConnector, msg);
        }
    }

    /**
     * 通过音频数据传输流链接寻找命令控制链接
     */
    private ConnectorHandler findAudioCmdConnector(ConnectorHandler connector) {
        return audioStreamToCmdMap.get(connector);
    }

    /**
     * 加入房间
     */
    private boolean joinRoom(AudioRoom room, ConnectorHandler streamConnector) {
        if (room.enterRoom(streamConnector)) {
            audioStreamRoomMap.put(streamConnector, room);
            return true;
        }
        return false;
    }

    /**
     * 链接关闭时退出音频房间等操作
     */
    private class RemoveAudioQueueOnConnectorClosedChain extends ConnectorHandlerChain<Connector> {

        @Override
        protected boolean consume(ConnectorHandler handler, Connector connector) {
            if (audioCmdToStreamMap.containsKey(handler)) {
                // 命令链接断开
                audioCmdToStreamMap.remove(handler);
            } else if (audioStreamToCmdMap.containsKey(handler)) {
                // 流断开
                audioStreamToCmdMap.remove(handler);
                // 流断开时解散房间
                dissovlveRoom(handler);
            }
            return false;
        }
    }
}
