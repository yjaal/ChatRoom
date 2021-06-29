package net.qiujuer.library.clink.core.frame;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import net.qiujuer.library.clink.core.Frame;
import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.SendPacket;

/**
 * 直流类型输出Frame
 *
 * @author YJ
 * @date 2021/6/29
 **/
public class SendDirectEntityFrame extends AbstractSendPacketFrame {

    private final ReadableByteChannel channel;

    public SendDirectEntityFrame(int available, short identifier, ReadableByteChannel channel,
        SendPacket<?> packet) {
        super(Math.min(available, Frame.MAX_CAPACITY),
            Frame.TYPE_PACKET_ENTITY,
            Frame.FLAG_NONE,
            identifier, packet);
        this.channel = channel;
    }

    public static Frame buildEntityFrame(SendPacket<?> packet, short identifier) {
        int available = packet.available();
        if (available <= 0) {
            // 如果没有可发送的数据，那么直接发送取消帧
            return new CancelSendFrame(identifier);
        }
        // 构建首帧
        InputStream in = packet.open();
        ReadableByteChannel channel = Channels.newChannel(in);
        return new SendDirectEntityFrame(available, identifier, channel, packet);
    }

    @Override
    protected int consumeBody(IoArgs args) throws IOException {
        if (packet == null) {
            // 已终止当前帧，则填充假数据
            return args.fillEmpty(bodyRemaining);
        }
        // 从通道中读取
        return args.readFrom(channel);
    }

    @Override
    protected Frame buildNextFrame() {
        // 直流类型
        int available = packet.available();
        if (available <= 0) {
            return new CancelSendFrame(getBodyIdentifier());
        }
        // 下一帧
        return new SendDirectEntityFrame(available, getBodyIdentifier(), channel, packet);
    }
}
