package net.qiujuer.library.clink.core.frame;

import java.io.IOException;
import net.qiujuer.library.clink.core.IoArgs;

/**
 * 取消传输帧，接收实现
 *
 * @author YJ
 * @date 2021/6/7
 **/
public class CancelReceiveFrame extends AbstractReceiveFrame {

    public CancelReceiveFrame(byte[] header) {
        super(header);
    }

    @Override
    protected int consumeBody(IoArgs args) throws IOException{
        return 0;
    }
}
