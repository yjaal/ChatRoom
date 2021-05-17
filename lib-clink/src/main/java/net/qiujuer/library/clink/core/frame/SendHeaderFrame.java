package net.qiujuer.library.clink.core.frame;

import java.io.IOException;
import net.qiujuer.library.clink.core.Frame;
import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.SendPacket;

/**
 * 发送的头帧
 *
 * @author YJ
 * @date 2021/5/12
 **/
public class SendHeaderFrame extends AbstractSendPacketFrame{

    private static final int PACKET_HEADER_FRAME_MIN_LENGTH = 6;

    private final byte[] body;

    /**
     * 头帧也是一帧，其帧头保存一些基本信息，而将要发送包中的基本信息保存在自己的帧体当中
     */
    public SendHeaderFrame(short identifier, SendPacket packet) {
        super(PACKET_HEADER_FRAME_MIN_LENGTH,
            Frame.TYPE_PACKET_HEADER,
            Frame.FLAG_NONE,
            identifier,
            packet);
        final long packetLen = packet.length();
        final byte packetType = packet.type();
        final byte[] packetHeaderInfo = packet.headerInfo();
        body = new byte[bodyRemaining];

        // body的前5个字节保留包的长度
        header[0] = (byte) (packetLen >> 32);
        header[1] = (byte) (packetLen >> 24);
        header[2] = (byte) (packetLen >> 16);
        header[3] = (byte) (packetLen >> 8);
        header[4] = (byte) (packetLen);

        header[5] = packetType;

        if (packetHeaderInfo != null) {
            System.arraycopy(packetHeaderInfo, 0,
                body, PACKET_HEADER_FRAME_MIN_LENGTH, packetHeaderInfo.length);

        }
    }

    @Override
    public Frame nextFrame() {
        return null;
    }

    @Override
    protected int consumeBody(IoArgs args) throws IOException {
        int count = bodyRemaining;
        int offeset = body.length - count;
        return args.readFrom(body, offeset, count);
    }
}
