package net.qiujuer.lesson.sample.server.audio;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.qiujuer.lesson.sample.foo.handle.ConnectorHandler;

/**
 * 管道（房间）基本封装
 *
 * @author YJ
 * @date 2021/6/30
 **/
public class AudioRoom {

    private final String roomCode;

    /**
     * 房间只有两个客户端
     */
    private volatile ConnectorHandler handler1;
    private volatile ConnectorHandler handler2;

    public AudioRoom() {
        this.roomCode = getRandomString(5);
    }

    public String getRoomCode() {
        return roomCode;
    }

    public ConnectorHandler[] getConnectors() {
        List<ConnectorHandler> handlers = new ArrayList<>(2);
        if (handler1 != null) {
            handlers.add(handler1);
        }
        if (handler2 != null) {
            handlers.add(handler2);
        }
        return handlers.toArray(new ConnectorHandler[0]);
    }

    /**
     * 获取对方
     */
    public ConnectorHandler getTheOtherHandler(ConnectorHandler handler) {
        return (handler1 == handler || handler1 == null) ? handler2 : handler1;
    }

    /**
     * 房间是否可以聊天，是否具有两个客户端
     */
    public synchronized boolean isEnable() {
        return handler1 != null && handler2 != null;
    }

    /**
     * 生成一个简单的随机字符串
     */
    private static String getRandomString(final int len) {
        final String str = "123456789";
        final Random random = new Random();
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            int number = random.nextInt(str.length());
            sb.append(str.charAt(number));
        }
        return sb.toString();
    }
}
