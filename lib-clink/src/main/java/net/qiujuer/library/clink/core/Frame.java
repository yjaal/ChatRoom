package net.qiujuer.library.clink.core;

import java.io.IOException;

/**
 * 分片到帧
 *
 * @author YJ
 * @date 2021/5/12
 **/
public abstract class Frame {

    /**
     * 帧头占6个字节
     */
    public static final int FRAME_HEADER_LEN = 6;
    /**
     * 最大长度：2^16-1
     */
    private static final int MAX_CAPACITY = (2 << 15) - 1;

    /**
     * 报文头
     */
    private static final byte TYPE_PACKET_HEADER = 11;
    /**
     * 报文体
     */
    private static final byte TYPE_PACKET_ENTITY = 12;

    /**
     * 发送取消
     */
    private static final byte TYPE_COMMAND_SEND_CANCEL = 41;
    /**
     * 接收拒绝
     */
    private static final byte TYPE_COMMAND_RECEIVE_REJECT = 42;

    private static final byte FLAG_NONE = 0;


    protected final byte[] header = new byte[FRAME_HEADER_LEN];

    public Frame(int len, byte type, byte flag, short identifier) {
        if (len < 0 || len > MAX_CAPACITY) {
            throw new RuntimeException("");
        }

        // 唯一标志判断
        if (identifier < 1 || identifier > 255) {
            throw new RuntimeException("");
        }

        // 长度len为int，有4个字节，我们的头中只有两个字节
        // 这里向右移动8位，将低8位移除，强制转化位byte，只保留低8位，这就是长度的第一个段
        header[0] = (byte) (len >> 8);
        // 强制转化位byte，只保留低8位，这就是长度的第二个段
        header[1] = (byte) len;

        header[2] = type;
        header[3] = flag;

        header[4] = (byte) identifier;

        // 保留部分，暂时不做其他操作
        header[5] = 0;
    }

    public Frame(byte[] header) {
        System.arraycopy(header, 0, this.header, 0, FRAME_HEADER_LEN);
    }

    public int getBodyLen() {
        //Java 总是把 byte 当做有符处理；我们可以通过将其和 0xFF（一个byte） 进行二进制与得到它的无符值
        return ((((int) header[0]) & 0xFF) << 8) | (((int) header[1]) & 0xFF);
    }

    public byte getBodyType() {
        return header[2];
    }

    public byte getBodyFlag() {
        return header[3];
    }

    public short getBodyIdentifier() {
        return (short) (((short) header[4]) & 0xFF);
    }

    /**
     * 帧的消费
     */
    public abstract boolean handle(IoArgs args) throws IOException;

    // 在传输过程中，如果一个文件很大，那么如果直接将文件数据分片为多个帧存入到内存
    // 这样其实会消耗很大的内存，其实在发送的时候只有那一帧在发送，而其他帧则在等待
    // 所以可以先发送前面一帧，前面一帧发送完了之后再构建后面一帧，而同时可以先让头
    // 帧发送完成之后再构建数据帧
    public abstract Frame nextFrame();
}
