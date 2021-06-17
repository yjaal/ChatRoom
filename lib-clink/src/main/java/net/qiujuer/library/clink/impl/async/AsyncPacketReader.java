package net.qiujuer.library.clink.impl.async;

import java.io.Closeable;
import java.io.IOException;
import net.qiujuer.library.clink.core.Frame;
import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.SendPacket;
import net.qiujuer.library.clink.core.ds.BytePriorityNode;
import net.qiujuer.library.clink.core.frame.AbstractSendFrame;
import net.qiujuer.library.clink.core.frame.AbstractSendPacketFrame;
import net.qiujuer.library.clink.core.frame.CancelSendFrame;
import net.qiujuer.library.clink.core.frame.SendEntityFrame;
import net.qiujuer.library.clink.core.frame.SendHeaderFrame;

/**
 * 用于管理分片之后到数据接收
 *
 * @author YJ
 * @date 2021/5/12
 **/
public class AsyncPacketReader implements Closeable {

    private IoArgs args = new IoArgs();

    private final PacketProvider provider;

    /**
     * 保存帧队列
     */
    private volatile BytePriorityNode<Frame> node;

    /**
     * 单独定义一个队列长度
     */
    private volatile int nodeSize = 0;

    /**
     * 表示帧的唯一标识，从0到255
     */
    private short lastIdentifier = 0;

    AsyncPacketReader(PacketProvider provider) {
        this.provider = provider;
    }

    /**
     * 取消发送，如果当前packet已发送部分数据（就算是头数据），也应该在当前队列中发送一份取消发送到标志
     */
    synchronized void cancel(SendPacket packet) {
        if (nodeSize == 0) {
            return;
        }
        for (BytePriorityNode<Frame> curFrame = node, before = null; curFrame != null;
            before = curFrame, curFrame = curFrame.next) {
            Frame frame = curFrame.item;
            if (frame instanceof AbstractSendFrame) {
                AbstractSendPacketFrame absFrame = (AbstractSendPacketFrame) frame;
                // 如果刚好是当前packet
                if (absFrame.getPacket() == packet) {
                    boolean removable = absFrame.abort();
                    // 可以完美取消
                    if (removable) {
                        this.removeFrame(curFrame, before);
                        if (absFrame instanceof SendHeaderFrame) {
                            // 头帧，并且未发送任何数据，直接取消后不需要添加取消发送帧
                            break;
                        }
                    }
                    // 定义一个取消帧
                    CancelSendFrame cancelSendFrame = new CancelSendFrame(
                        absFrame.getBodyIdentifier());
                    appendNewFrame(cancelSendFrame);
                    // 意外终止，返回失败
                    provider.completedPacket(packet, false);

                    break;
                }
            }
        }
    }

    /**
     * 请求拿一个packet
     */
    boolean requestTakePacker() {
        synchronized (this) {
            // 这里如果大于1，就表示队列中有数据，当然后面如果支持并发，可以将这个值调大，比如同时可以获取10个帧进行
            // 发送，暂定为1
            if (nodeSize >= 1) {
                return true;
            }
        }

        SendPacket sendPacket = provider.takePacket();
        if (sendPacket != null) {
            short id = genIdentifier();
            // 从包中获取头帧
            SendHeaderFrame sendHeaderFrame = new SendHeaderFrame(id, sendPacket);
            this.appendNewFrame(sendHeaderFrame);
        }

        synchronized (this) {
            return nodeSize != 0;
        }
    }

    private synchronized void appendNewFrame(Frame frame) {
        BytePriorityNode<Frame> newFrameNode = new BytePriorityNode<>(frame);
        if (node != null) {
            // 使用优先级别添加到链表
            node.appendWithPriority(newFrameNode);
        } else {
            node = newFrameNode;
        }
        nodeSize++;
    }

    /**
     * 关闭当前reader，关闭时应关闭所有Frame对应的Packet
     */
    @Override
    public synchronized void close() {
        while (node != null) {
            Frame frame = node.item;
            if (frame instanceof AbstractSendFrame) {
                SendPacket packet = ((AbstractSendPacketFrame) frame).getPacket();
                provider.completedPacket(packet, false);
            }
            node = node.next;
        }

        nodeSize = 0;
        node = null;
    }

    IoArgs fillData() {
        Frame currentFrame = this.getCurrentFrame();
        if (currentFrame == null) {
            return null;
        }
        try {
            // 如果Frame不为空，则需要填充数据
            // 这里可能出现多个线程获取到同一个帧的并发问题，这里通过改造发送AsyncSendDispatcher#send
            if (currentFrame.handle(args)) {
                // 如果已经全部消费完，尝试去构建后面一帧
                Frame nextFrame = currentFrame.nextFrame();
                if (nextFrame != null) {
                    appendNewFrame(nextFrame);
                } else if (currentFrame instanceof SendEntityFrame) {
                    // 没有下一帧，同时是一个实体帧，表示发送完毕，通知发送完成
                    provider.completedPacket(((SendEntityFrame) currentFrame).getPacket(), true);
                }
                // 当前帧已经填充了数据，可以发送了，从链表中弹出
                this.popCurrentFrame();
            }
            // 返回已填充到数据
            return args;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private synchronized void popCurrentFrame() {
        node = node.next;
        nodeSize--;
        if (node == null) {
            // 请求一个新的packet
            requestTakePacker();
        }
    }

    private void removeFrame(BytePriorityNode<Frame> removeNode, BytePriorityNode<Frame> before) {
        if (before == null) {
            // 头节点
            node = removeNode.next;
        } else {
            before.next = removeNode.next;
        }
        nodeSize--;
        if (node == null) {
            // 请求一个新的packet
            requestTakePacker();
        }
    }

    private Frame getCurrentFrame() {
        if (node == null) {
            return null;
        }
        return node.item;
    }

    private short genIdentifier() {
        short identifier = ++lastIdentifier;
        if (identifier == 255) {
            identifier = 0;
        }
        return identifier;
    }

    interface PacketProvider {

        /**
         * 请求获取一个packet
         */
        SendPacket takePacket();

        /**
         * 发送完成
         */
        void completedPacket(SendPacket packet, boolean isSucceed);
    }
}
