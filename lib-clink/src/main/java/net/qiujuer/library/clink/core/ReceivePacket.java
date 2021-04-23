package net.qiujuer.library.clink.core;

/**
 * 接收包定义
 *
 * @author YJ
 * @date 2021/4/19
 **/
public abstract class ReceivePacket extends Packet {

    public abstract void save(byte[] bytes, int count);

}
