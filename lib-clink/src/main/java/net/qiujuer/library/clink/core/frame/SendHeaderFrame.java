package net.qiujuer.library.clink.core.frame;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import net.qiujuer.library.clink.core.Frame;
import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.SendPacket;

/**
 * 发送的头帧，其中包含帧头和帧体部分，帧体部分可以保存一些校验信息
 *
 * @author YJ
 * @date 2021/5/12
 **/
public class SendHeaderFrame extends AbstractSendPacketFrame{

    public static final int PACKET_HEADER_FRAME_MIN_LENGTH = 6;

    private final byte[] body;

    /**
     * 头帧也是一帧，其帧头保存一些基本信息，而将要发送包中的基本信息保存在自己的帧体当中
     */
    public SendHeaderFrame(short identifier, SendPacket packet) {
        // 构造方法中会初始化帧头相关内容
        super(PACKET_HEADER_FRAME_MIN_LENGTH,
            Frame.TYPE_PACKET_HEADER,
            Frame.FLAG_NONE,
            identifier,
            packet);
        // 包的长度
        final long packetLen = packet.length();
        final byte packetType = packet.type();
        final byte[] packetHeaderInfo = packet.headerInfo();
        body = new byte[bodyRemaining];

        // body的前5个字节保留包的长度，包长度保存在头帧的数据区中
        body[0] = (byte) (packetLen >> 32);
        body[1] = (byte) (packetLen >> 24);
        body[2] = (byte) (packetLen >> 16);
        body[3] = (byte) (packetLen >> 8);
        body[4] = (byte) (packetLen);

        // 第6个字节保存数据类型
        body[5] = packetType;

        // 如果packet头信息不为空，需要copy到头帧的body中去
        if (packetHeaderInfo != null) {
            System.arraycopy(packetHeaderInfo, 0,
                body, PACKET_HEADER_FRAME_MIN_LENGTH, packetHeaderInfo.length);

        }
    }

    @Override
    public Frame buildNextFrame() {
        InputStream stream = packet.open();
        ReadableByteChannel channel = Channels.newChannel(stream);
        return new SendEntityFrame(this.getBodyIdentifier(), packet.length(), channel, packet);
    }

    @Override
    protected int consumeBody(IoArgs args) throws IOException {
        int count = bodyRemaining;
        int offset = body.length - count;
        return args.readFrom(body, offset, count);
    }
}
