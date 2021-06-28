package net.qiujuer.lesson.sample.server;

import net.qiujuer.lesson.sample.server.handle.ClientHandler;
import net.qiujuer.lesson.sample.server.handle.ConnectorStringPacketChain;
import net.qiujuer.library.clink.box.StringReceivePacket;

/**
 * 统计类：统计接收和发送的数据量
 */
public class ServerStatistics {

    long receiveSize;
    long sendSize;

    ConnectorStringPacketChain statisticsChain() {
        return new StatisticsConnectorStringPacketChain();
    }

    class StatisticsConnectorStringPacketChain extends ConnectorStringPacketChain {

        @Override
        protected boolean consume(ClientHandler handler, StringReceivePacket packet) {
            // 只统计，不消费
            receiveSize++;
            return false;
        }
    }
}
