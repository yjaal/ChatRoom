package net.qiujuer.library.clink.core.frame;

import java.io.IOException;
import net.qiujuer.library.clink.core.Frame;
import net.qiujuer.library.clink.core.IoArgs;

/**
 * 接收帧抽象类
 *
 * @author YJ
 * @date 2021/5/12
 **/
public abstract class AbstractReceiveFrame extends Frame {

    // 帧体可读写区域大小
    volatile int bodyRemaining;

    public AbstractReceiveFrame(byte[] header) {
        super(header);
        bodyRemaining = getBodyLen();
    }

    @Override
    public boolean handle(IoArgs args) throws IOException {
        if (bodyRemaining == 0) {
            // 已读取所有数据
            return true;
        }
        // 这里只是消费了帧体，因为在调度的时候就需要将帧头处理掉
        bodyRemaining -= consumeBody(args);
        return bodyRemaining == 0;
    }

    /**
     * 这个其实对于接收来说是不需要的，因为对于接收方来说所有的帧都是从网络接收的，不需要自己构建下一帧
     */
    @Override
    public Frame nextFrame() {
        return null;
    }

    @Override
    public int getConsumableLen() {
        // 头部是保证已接收完的
        return bodyRemaining;
    }

    abstract int consumeBody(IoArgs args) throws IOException;
}
