package net.qiujuer.library.clink.core;

import java.io.Closeable;

/**
 * 公共的数据封装
 * <p>提供了类型以及基本长度定义
 *
 * @author YJ
 * @date 2021/4/19
 **/
public abstract class Packet implements Closeable {

    /**
     * 比如字符串、文件等等类型，一个字节足够
     */
    protected byte type;

    protected int length;

    public byte type() {
        return type;
    }

    public int length() {
        return length;
    }
}
