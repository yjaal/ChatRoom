package net.qiujuer.library.clink.box;

import java.io.ByteArrayOutputStream;

/**
 * 字节接收包
 *
 * @author YJ
 * @date 2021/5/8
 **/
public class BytesReceivePacket extends AbsByteArrayReceivePacket<byte[]> {

    public BytesReceivePacket(long len) {
        super(len);
    }

    public byte type() {
        return TYPE_MEMORY_BYTES;
    }

    protected byte[] buildEntity(ByteArrayOutputStream stream) {
        return stream.toByteArray();
    }
}
