package net.qiujuer.library.clink.core.frame;

import net.qiujuer.library.clink.core.Frame;
import net.qiujuer.library.clink.core.IoArgs;

/**
 * 接收帧构建工厂
 *
 * @author YJ
 * @date 2021/6/8
 **/
public class ReceiveFrameFactory {

    public static AbstractReceiveFrame createInstance(IoArgs args) {
        byte[] buffer = new byte[Frame.FRAME_HEADER_LEN];
        args.writeTo(buffer, 0);
        byte type = buffer[2];
        switch (type) {
            case Frame.TYPE_PACKET_HEADER:
                return new ReceiveHeaderFrame(buffer);
            case Frame.TYPE_PACKET_ENTITY:
                return new ReceiveEntityFrame(buffer);
            // 如果是心跳帧，则直接使用单例对象
            case Frame.TYPE_COMMAND_HEARTBEAT:
                return HeartbeatReceiveFrame.INSTANCE;
            case Frame.TYPE_COMMAND_SEND_CANCEL:
                return new CancelReceiveFrame(buffer);
            default:
                throw new UnsupportedOperationException("Unsupported frame type: " + type);
        }
    }
}
