package net.qiujuer.library.clink.core.frame;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import net.qiujuer.library.clink.core.IoArgs;

/**
 * 接收的数据帧
 *
 * @author YJ
 * @date 2021/5/12
 **/
public class ReceiveEntityFrame extends AbstractReceiveFrame {

    private WritableByteChannel channel;

    ReceiveEntityFrame(byte[] header) {
        super(header);
    }

    public void bindPacketChannel(WritableByteChannel channel) {
        this.channel = channel;
    }

    @Override
    int consumeBody(IoArgs args) throws IOException {
        return channel == null ? args.setEmpty(bodyRemaining) : args.writeTo(channel);
    }
}
