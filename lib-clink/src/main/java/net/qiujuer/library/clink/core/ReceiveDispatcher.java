package net.qiujuer.library.clink.core;

import java.io.Closeable;

/**
 * 接收调度
 * <p>把一份或多份IoArgs组合成一份Packet
 *
 * @author YJ
 * @date 2021/4/19
 **/
public interface ReceiveDispatcher extends Closeable {

    void start();

    void stop();

    interface ReceivePacketCallback {

        void onReceivePacketCompleted(ReceivePacket packet);
    }
}
