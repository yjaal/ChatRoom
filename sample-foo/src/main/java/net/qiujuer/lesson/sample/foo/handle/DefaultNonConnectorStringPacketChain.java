package net.qiujuer.lesson.sample.foo.handle;

import net.qiujuer.library.clink.box.StringReceivePacket;

/**
 * 默认String接收节点，不做任何事情
 *
 * @author YJ
 * @date 2021/6/25
 **/
public class DefaultNonConnectorStringPacketChain extends ConnectorStringPacketChain {

    @Override
    protected boolean consume(ConnectorHandler handler, StringReceivePacket stringReceivePacket) {
        return false;
    }
}
