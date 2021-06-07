package net.qiujuer.library.clink.core.frame;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import net.qiujuer.library.clink.core.Frame;
import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.SendPacket;

/**
 * 发送的数据帧
 *
 * @author YJ
 * @date 2021/5/12
 **/
public class SendEntityFrame extends AbstractSendPacketFrame {

    /**
     * 当前未消费长度
     */
    private final long unConsumeEntityLen;

    private ReadableByteChannel channel;

    SendEntityFrame(short identifier, long entityLen, ReadableByteChannel channel,
        SendPacket<?> packet) {
        super((int) Math.min(entityLen, Frame.MAX_CAPACITY),
            Frame.TYPE_PACKET_ENTITY,
            Frame.FLAG_NONE,
            identifier,
            packet);
        this.channel = channel;
        // 总长度减去当前body部分可以被消费的长度
        unConsumeEntityLen = entityLen - bodyRemaining;
    }

    @Override
    public Frame buildNextFrame() {
        // 消费完之后返回空
        if (unConsumeEntityLen == 0) {
            return null;
        }
        return new SendEntityFrame(getBodyIdentifier(), unConsumeEntityLen, channel,packet);
    }

    @Override
    protected int consumeBody(IoArgs args) throws IOException {
        if (packet == null) {
            // 为空则表示用户取消了，于是此帧被终止，包和channel都为空，则填充假数据
            return args.fillEmpty(bodyRemaining);
        }
        return args.readFrom(channel);
    }
}
