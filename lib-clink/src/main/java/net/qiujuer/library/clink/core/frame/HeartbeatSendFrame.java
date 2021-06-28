package net.qiujuer.library.clink.core.frame;

import java.io.IOException;
import net.qiujuer.library.clink.core.Frame;
import net.qiujuer.library.clink.core.IoArgs;

/**
 * 心跳包发送帧
 *
 * @author YJ
 * @date 2021/5/12
 **/
public class HeartbeatSendFrame extends AbstractSendFrame {

    /**
     * 心跳帧总共6字节
     */
    static final byte[] HEARTBEAT_DATA = new byte[]{0, 0, Frame.TYPE_COMMAND_HEARTBEAT, 0,
        0, 0};

    public HeartbeatSendFrame() {
        super(HEARTBEAT_DATA);
    }

    @Override
    public Frame nextFrame() {
        return null;
    }

    @Override
    protected int consumeBody(IoArgs args) throws IOException {
        return 0;
    }
}
