package net.qiujuer.library.clink.core.frame;

import java.io.IOException;
import net.qiujuer.library.clink.core.Frame;
import net.qiujuer.library.clink.core.IoArgs;

/**
 * 取消发送的帧
 *
 * @author YJ
 * @date 2021/5/12
 **/
public class CancelSendFrame extends AbstractSendFrame {

    public CancelSendFrame(short identifier) {
        super(0, Frame.TYPE_COMMAND_SEND_CANCEL, Frame.FLAG_NONE, identifier);
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
