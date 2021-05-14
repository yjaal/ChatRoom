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
    byte headerRemaining = Frame.FRAME_HEADER_LEN;

    int bodyRemaining;

    public AbstractSendFrame(int len, byte type, byte flag, short identifier) {
        super(len, type, flag, identifier);
        bodyRemaining = len;
    }

    /**
     * 数据消费
     */
    @Override
    public boolean handle(IoArgs args) throws IOException {
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
    }

    protected abstract int consumeBody(IoArgs args) throws IOException;

    protected abstract byte consumeHeader(IoArgs args) throws IOException;
}
