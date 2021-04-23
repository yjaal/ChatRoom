package net.qiujuer.library.clink.box;

import java.io.IOException;
import net.qiujuer.library.clink.core.ReceivePacket;

/**
 * 字符串接收包定义
 *
 * @author YJ
 * @date 2021/4/19
 **/
public class StringReceivePacket extends ReceivePacket {

    /**
     * 缓存
     */
    private byte[] buffer;

    /**
     * 存入时的坐标
     */
    private int pos;

    public StringReceivePacket(int len) {
        this.buffer = new byte[len];
        this.length = len;

    }

    @Override
    public void save(byte[] bytes, int count) {
        // 将bytes中的数据存入到缓存中，从位置pos开始存入
        System.arraycopy(bytes, 0, buffer, pos, count);
        pos += count;
    }

    /**
     * 获取到接收的字符串
     */
    public String string() {
        return new String(buffer);
    }

    @Override
    public void close() throws IOException {

    }
}
