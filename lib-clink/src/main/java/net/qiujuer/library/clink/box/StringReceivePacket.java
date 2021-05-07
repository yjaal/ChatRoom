package net.qiujuer.library.clink.box;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import net.qiujuer.library.clink.core.ReceivePacket;

/**
 * 字符串接收包定义
 *
 * @author YJ
 * @date 2021/4/19
 **/
public class StringReceivePacket extends ReceivePacket<ByteArrayOutputStream> {

    private String msg;

    public StringReceivePacket(int len) {
        this.length = len;
    }

    /**
     * 获取到接收的字符串
     */
    public String string() {
        return msg;
    }

    @Override
    protected ByteArrayOutputStream createStream() {
        return new ByteArrayOutputStream((int) length);
    }

    @Override
    protected void closeStream(ByteArrayOutputStream stream) throws IOException {
        super.closeStream(stream);
        msg = new String(stream.toByteArray());
    }
}
