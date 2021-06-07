package net.qiujuer.library.clink.core.frame;

import java.io.IOException;
import net.qiujuer.library.clink.core.Frame;
import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.SendPacket;

public abstract class AbstractSendPacketFrame extends AbstractSendFrame {

    protected volatile SendPacket<?> packet;

    public AbstractSendPacketFrame(int len, byte type, byte flag, short identifier,
        SendPacket<?> packet) {
        super(len, type, flag, identifier);
        this.packet = packet;
    }

    /**
     * 如果packet为空表示被终止，此时要对下一帧进行处理
     */
    @Override
    public final synchronized Frame nextFrame() {
        return packet == null ? null : this.buildNextFrame();
    }

    @Override
    public synchronized boolean handle(IoArgs args) throws IOException {
        if (packet == null && !isSending()) {
            // 发送被取消且未发送任何数据，直接返回true，表示结束发送
            return true;
        }
        return super.handle(args);
    }

    /**
     * 取消发送操作
     *
     * @return true：当前帧还没有发送任何数据；false：当前帧发送了部分数据
     */
    public final synchronized boolean abort() {
        boolean isSending = isSending();
        if (isSending) {
            this.fillDirtyDataOnAbort();
        }
        packet = null;
        return !isSending;
    }

    /**
     * 获取当前对应的发送packet
     */
    public synchronized SendPacket getPacket() {
        return packet;
    }

    /**
     * 填充假数据，可以由后面子类来翻盖实现，便于后面扩展实现
     */
    protected void fillDirtyDataOnAbort() {

    }

    protected abstract Frame buildNextFrame();
}
