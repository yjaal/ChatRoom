package net.qiujuer.library.clink.core;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 接收包定义
 *
 * @author YJ
 * @date 2021/4/19
 **/
public abstract class ReceivePacket<T extends OutputStream> extends Packet<T> {

    private T stream;

    /**
     * 是否已取消
     */
    private boolean isCanceled;

    public boolean isCanceled() {
        return isCanceled;
    }

    protected abstract T createStream();

    protected void closeStream() throws IOException {
        stream.close();
    }
}
