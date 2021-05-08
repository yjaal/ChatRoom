package net.qiujuer.library.clink.core;

import java.io.OutputStream;

/**
 * 接收包定义
 *
 * @author YJ
 * @date 2021/4/19
 **/
public abstract class ReceivePacket<T extends OutputStream> extends Packet<T> {

    /**
     * 是否已取消
     */
    private boolean isCanceled;

    public boolean isCanceled() {
        return isCanceled;
    }

    protected abstract T createStream();
}
