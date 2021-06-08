package net.qiujuer.library.clink.core.frame;

import static net.qiujuer.library.clink.core.frame.SendHeaderFrame.PACKET_HEADER_FRAME_MIN_LENGTH;

import java.io.IOException;
import net.qiujuer.library.clink.core.IoArgs;

/**
 * 接收的头帧
 *
 * @author YJ
 * @date 2021/5/12
 **/
public class ReceiveHeaderFrame extends AbstractReceiveFrame {

    private final byte[] body;

    public ReceiveHeaderFrame(byte[] header) {
        super(header);
        this.body = new byte[bodyRemaining];
    }

    @Override
    int consumeBody(IoArgs args) throws IOException {
        int offset = body.length - bodyRemaining;
        return args.writeTo(body, offset);
    }

    public long getPacketLen() {
        return ((((long) body[0]) & 0xFFL) << 32)
            | ((((long) body[1]) & 0xFFL) << 24)
            | ((((long) body[2]) & 0xFFL) << 16)
            | ((((long) body[3]) & 0xFFL) << 8)
            | (((long) body[4]) & 0xFFL);
    }

    public byte getPacketType() {
        return body[5];
    }

    public byte[] getPacketHeaderInfo() {
        if (body.length > PACKET_HEADER_FRAME_MIN_LENGTH) {
            byte[] headerInfo = new byte[body.length - PACKET_HEADER_FRAME_MIN_LENGTH];
            System.arraycopy(body, PACKET_HEADER_FRAME_MIN_LENGTH, headerInfo, 0,
                headerInfo.length);
            return headerInfo;
        }
        return null;
    }
}
