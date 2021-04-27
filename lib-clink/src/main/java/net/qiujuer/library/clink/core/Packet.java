package net.qiujuer.library.clink.core;

import java.io.Closeable;
import java.io.IOException;
import java.util.Objects;

/**
 * 公共的数据封装
 * <p>提供了类型以及基本长度定义
 *
 * @author YJ
 * @date 2021/4/19
 **/
public abstract class Packet<T extends Closeable> implements Closeable {

    /**
     * 比如字符串、文件等等类型，一个字节足够
     */
    protected byte type;

    protected long length;

    private T stream;

    public byte type() {
        return type;
    }

    public long length() {
        return length;
    }

    protected abstract T createStream();

    protected void closeStream(T stream) throws IOException {
        stream.close();
    }

    public final T open() {
        return createStream();
    }

    @Override
    public final void close() throws IOException {
        if (!Objects.isNull(stream)) {
            closeStream(stream);
            stream = null;
        }
    }
}
