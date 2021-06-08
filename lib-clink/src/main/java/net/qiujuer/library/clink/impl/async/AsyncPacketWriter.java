package net.qiujuer.library.clink.impl.async;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.HashMap;
import java.util.Map;
import net.qiujuer.library.clink.core.Frame;
import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.ReceivePacket;
import net.qiujuer.library.clink.core.frame.AbstractReceiveFrame;
import net.qiujuer.library.clink.core.frame.CancelReceiveFrame;
import net.qiujuer.library.clink.core.frame.ReceiveEntityFrame;
import net.qiujuer.library.clink.core.frame.ReceiveFrameFactory;
import net.qiujuer.library.clink.core.frame.ReceiveHeaderFrame;

/**
 * 用于管理分片之后到数据发送
 *
 * @author YJ
 * @date 2021/5/12
 **/
class AsyncPacketWriter implements Closeable {

    public final PacketProvider provider;

    private final Map<Short, PacketModel> packetMap = new HashMap<>();

    /**
     * 当前临时包
     */
    private final IoArgs args = new IoArgs();
    /**
     * 当前临时帧
     */
    private volatile Frame frameTmp;

    public AsyncPacketWriter(PacketProvider provider) {
        this.provider = provider;
    }

    synchronized void consumeIoArgs(IoArgs ioArgs) {
        if (frameTmp == null) {
            Frame tmp;
            do {
                tmp = buildNewFrame(args);
            } while (tmp == null && args.remained());
            if (tmp == null) {
                return;
            }
            frameTmp = tmp;
            if (!args.remained()) {
                return;
            }
        }
        Frame curFrame = frameTmp;
        do {
            try {
                // 消费完成
                if (curFrame.handle(args)) {
                    if (curFrame instanceof ReceiveHeaderFrame) {
                        ReceiveHeaderFrame headerFrame = (ReceiveHeaderFrame) curFrame;
                        // 接收到到新包
                        ReceivePacket receivePacket = provider.takePacket(
                            headerFrame.getPacketType(),
                            headerFrame.getPacketLen(),
                            headerFrame.getPacketHeaderInfo());
                        // 将新包存入Map中，这是一个头帧，可以直接添加
                        this.appendNewPacket(headerFrame.getBodyIdentifier(), receivePacket);
                    } else if (curFrame instanceof ReceiveEntityFrame) {
                        this.completedEntityFrame((ReceiveEntityFrame) curFrame);
                    }

                    // 这里表示当前帧满了，但是args可能还没消费完，
                    // 这里对args对循环消费放在AsyncReceiveDispatcher中
                    frameTmp = null;
                    break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } while (args.remained());
    }

    private void completedEntityFrame(ReceiveEntityFrame frame) {
        short identifier = frame.getBodyIdentifier();
        int len = frame.getBodyLen();
        synchronized (packetMap) {
            PacketModel packetModel = packetMap.get(identifier);
            packetModel.unReceivedLen -= len;
            if (packetModel.unReceivedLen <= 0) {
                provider.completedPacket(packetModel.packet, true);
                packetMap.remove(packetModel);
            }
        }
    }

    private void appendNewPacket(short identifier, ReceivePacket receivePacket) {
        synchronized (packetMap) {
            PacketModel packetModel = new PacketModel(receivePacket);
            packetMap.put(identifier, packetModel);
        }
    }

    private Frame buildNewFrame(IoArgs args) {
        AbstractReceiveFrame frame = ReceiveFrameFactory.createInstance(args);
        if (frame instanceof CancelReceiveFrame) {
            this.cancelReceivePacket(frame.getBodyIdentifier());
            return null;
        } else if (frame instanceof ReceiveEntityFrame) {
            // 如果是实体帧，则需要获取对应对通道
            WritableByteChannel channel = getPacketChannel(frame.getBodyIdentifier());
            ((ReceiveEntityFrame) frame).bindPacketChannel(channel);
            return frame;
        }
        return frame;
    }

    private WritableByteChannel getPacketChannel(short identifier) {
        synchronized (packetMap) {
            PacketModel packetModel = packetMap.get(identifier);
            return packetModel == null ? null : packetModel.channel;
        }
    }

    private void cancelReceivePacket(short identifier) {
        synchronized (packetMap) {
            PacketModel packetModel = packetMap.get(identifier);
            if (packetModel != null) {
                ReceivePacket packet = packetModel.packet;
                provider.completedPacket(packet, false);
            }
        }
    }

    public IoArgs takeIoArgs() {
        args.limit(frameTmp == null ? Frame.FRAME_HEADER_LEN : frameTmp.getConsumableLen());
        return args;
    }

    @Override
    public void close() throws IOException {
        synchronized (packetMap) {
            for (PacketModel model : packetMap.values()) {
                provider.completedPacket(model.packet, false);
            }
            packetMap.clear();
        }
    }

    interface PacketProvider {

        ReceivePacket takePacket(byte type, long len, byte[] headInfo);

        /**
         * 接收完成
         */
        void completedPacket(ReceivePacket packet, boolean isSucceed);
    }

    /**
     * 对接收对包进行封装，因为包可能不是一次性接收完成的
     */
    static class PacketModel {

        final ReceivePacket packet;

        final WritableByteChannel channel;

        volatile long unReceivedLen;

        PacketModel(ReceivePacket packet) {
            this.packet = packet;
            this.channel = Channels.newChannel((OutputStream) packet.open());
            this.unReceivedLen = packet.length();
        }
    }
}
