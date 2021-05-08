package net.qiujuer.library.clink.core;

import java.io.InputStream;

/**
 * 发送包定义
 *
 * @author YJ
 * @date 2021/4/19
 **/
public abstract class SendPacket<T extends InputStream> extends Packet<T> {

    /**
     * 是否已取消
     */
    private boolean isCanceled;

    public boolean isCanceled() {
        return isCanceled;
    }

    protected abstract T createStream();
}
