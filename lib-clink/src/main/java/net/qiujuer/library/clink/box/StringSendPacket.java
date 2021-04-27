package net.qiujuer.library.clink.box;

import java.io.ByteArrayInputStream;
import net.qiujuer.library.clink.core.SendPacket;

/**
 * 字符串发送包定义
 *
 * @author YJ
 * @date 2021/4/19
 **/
public class StringSendPacket extends SendPacket<ByteArrayInputStream> {

    private final byte[] bytes;

    public StringSendPacket(String msg) {
        this.bytes = msg.getBytes();
        this.length = bytes.length;
    }

    @Override
    protected ByteArrayInputStream createStream() {
        return new ByteArrayInputStream(bytes);
    }
}
