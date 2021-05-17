package net.qiujuer.library.clink.core.frame;

import java.io.IOException;
import net.qiujuer.library.clink.core.Frame;
import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.SendPacket;

public abstract class AbstractSendPacketFrame extends AbstractSendFrame {

    protected SendPacket<?> packet;

    public AbstractSendPacketFrame(int len, byte type, byte flag, short identifier, SendPacket<?> packet) {
        super(len, type, flag, identifier);
        this.packet = packet;
    }
}
