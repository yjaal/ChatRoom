package net.qiujuer.library.clink.box;

import java.io.ByteArrayOutputStream;
import net.qiujuer.library.clink.core.ReceivePacket;

/**
 * 字节包
 *
 * @author YJ
 * @date 2021/5/8
 **/
public abstract class AbsByteArrayReceivePacket<Entity> extends ReceivePacket<ByteArrayOutputStream, Entity> {

    public AbsByteArrayReceivePacket(long len) {
        super(len);
    }

    protected final ByteArrayOutputStream createStream() {
        return new ByteArrayOutputStream((int) length);
    }
}
