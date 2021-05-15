package net.qiujuer.library.clink.core;

import java.io.OutputStream;

/**
 * 接收包定义
 *
 * @author YJ
 * @date 2021/4/19
 **/
public abstract class ReceivePacket<Stream extends OutputStream, Entity> extends Packet<Stream> {

    /**
     * 当前接收包最终的实体
     */
    private Entity entity;

    public ReceivePacket(long len) {
        this.length = len;
    }

    public Entity entity() {
        return entity;
    }

    protected abstract Entity buildEntity(Stream stream);

    protected abstract Stream createStream();

    protected final void closeStream(Stream stream) {
        entity = buildEntity(stream);
    }
}
