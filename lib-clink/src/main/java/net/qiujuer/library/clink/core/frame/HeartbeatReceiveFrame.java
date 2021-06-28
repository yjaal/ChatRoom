package net.qiujuer.library.clink.core.frame;

import java.io.IOException;
import net.qiujuer.library.clink.core.IoArgs;

/**
 * 心跳包接收帧
 *
 * @author YJ
 * @date 2021/6/7
 **/
public class HeartbeatReceiveFrame extends AbstractReceiveFrame {

    static final HeartbeatReceiveFrame INSTANCE = new HeartbeatReceiveFrame();

    private HeartbeatReceiveFrame() {
        super(HeartbeatSendFrame.HEARTBEAT_DATA);
    }

    /**
     * 心跳帧没有数据部分
     */
    @Override
    protected int consumeBody(IoArgs args) throws IOException {
        return 0;
    }
}
