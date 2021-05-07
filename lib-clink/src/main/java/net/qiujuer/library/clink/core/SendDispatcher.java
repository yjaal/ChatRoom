package net.qiujuer.library.clink.core;

import java.io.Closeable;

/**
 * 发送调度
 * <p>缓存所有需要发送的数据，通过队列对数据进行发送实现对数据对基本包装
 * <p>同时也相当于Connector和IoArgs之间对一个媒介，因为具体发送数据是由IoArgs做的
 *
 * @author YJ
 * @date 2021/4/19
 **/
public interface SendDispatcher extends Closeable {

    /**
     * 发送
     */
    void send(SendPacket packet);

    /**
     * 取消
     */
    void cancel(SendPacket packet);
}
