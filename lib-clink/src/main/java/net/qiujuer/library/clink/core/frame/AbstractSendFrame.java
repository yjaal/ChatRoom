package net.qiujuer.library.clink.core.frame;

import java.io.IOException;
import net.qiujuer.library.clink.core.Frame;
import net.qiujuer.library.clink.core.IoArgs;

/**
 * 发送帧抽象类
 *
 * @author YJ
 * @date 2021/5/12
 **/
public abstract class AbstractSendFrame extends Frame {

    /**
     * 表示帧头还有多少没有被输出，帧头一般是一次性发送，所以这里直接赋值为固定值
     */
    volatile byte headerRemaining = Frame.FRAME_HEADER_LEN;

    volatile int bodyRemaining;

    AbstractSendFrame(int len, byte type, byte flag, short identifier) {
        super(len, type, flag, identifier);
        bodyRemaining = len;
    }

    AbstractSendFrame(byte[] header) {
        super(header);
    }

    /**
     * 数据消费，一个线程消费的时候不允许其他线程进来
     */
    @Override
    public synchronized boolean handle(IoArgs args) throws IOException {
        try {
            args.limit(headerRemaining + bodyRemaining);
            args.startWriting();

            // 帧头还未发送
            if (headerRemaining > 0 && args.remained()) {
                headerRemaining -= consumeHeader(args);
            }
            // 帧头已发送完，帧体还未发送完
            if (headerRemaining == 0 && args.remained() && bodyRemaining > 0) {
                bodyRemaining -= consumeBody(args);
            }
            return headerRemaining == 0 && bodyRemaining == 0;
        } finally {
            args.finishWriting();
        }
    }

    protected abstract int consumeBody(IoArgs args) throws IOException;

    /**
     * 头部消费基本是固定的，可以直接实现
     */
    private byte consumeHeader(IoArgs args) throws IOException {
        int count = headerRemaining;
        // 消费开始的坐标
        int offset = header.length - count;
        return (byte) args.readFrom(header, offset, count);
    }

    /**
     * 当前数据是否在发送中， 通过检测头部数据是否有发送进行检测
     */
    protected synchronized boolean isSending() {
        return headerRemaining < Frame.FRAME_HEADER_LEN;
    }

    @Override
    public int getConsumableLen() {
        return headerRemaining + bodyRemaining;
    }
}
