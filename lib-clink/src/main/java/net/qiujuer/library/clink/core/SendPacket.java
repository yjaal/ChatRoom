package net.qiujuer.library.clink.core;

/**
 * 发送包定义
 *
 * @author YJ
 * @date 2021/4/19
 **/
public abstract class SendPacket extends Packet{

    /**
     * 是否已取消
     */
    private boolean isCanceled;

    public abstract byte[] bytes();

    public boolean isCanceled() {
        return isCanceled;
    }
}
