package net.qiujuer.library.clink.box;

import java.io.ByteArrayInputStream;
import net.qiujuer.library.clink.core.SendPacket;

/**
 * 字节发送包
 *
 * @author YJ
 * @date 2021/5/8
 **/
public class BytesSendPacket extends SendPacket<ByteArrayInputStream> {

    private final byte[] bytes;

    public BytesSendPacket(byte[] bytes) {
        this.bytes = bytes;
        this.length = bytes.length;
    }

    @Override
    public byte type() {
        return TYPE_MEMORY_BYTES;
    }

    @Override
    protected ByteArrayInputStream createStream() {
        return new ByteArrayInputStream(bytes);
    }
}
