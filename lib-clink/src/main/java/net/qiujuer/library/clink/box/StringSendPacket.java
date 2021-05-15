package net.qiujuer.library.clink.box;

/**
 * 字符串发送包定义
 *
 * @author YJ
 * @date 2021/4/19
 **/
public class StringSendPacket extends BytesSendPacket {

    /**
     * 字符串发送时其实就是byte数组，所以直接得到byte数据，并按照Byte的发送方式进行发送
     */
    public StringSendPacket(String msg) {
        super(msg.getBytes());
    }

    public byte type() {
        return TYPE_MEMORY_STRING;
    }
}
