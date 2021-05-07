package net.qiujuer.library.clink.core;

import java.io.IOException;
import java.io.InputStream;

/**
 * 发送包定义
 *
 * @author YJ
 * @date 2021/4/19
 **/
public abstract class SendPacket<T extends InputStream> extends Packet<T> {

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
