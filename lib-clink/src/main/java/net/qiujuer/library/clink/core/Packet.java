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
public abstract class Packet<Stream extends Closeable> implements Closeable {

    /**
     * BYTE类型
     */
    public static final byte TYPE_MEMORY_BYTES = 1;
    /**
     * String类型
     */
    public static final byte TYPE_MEMORY_STRING = 2;
    /**
     * File类型
     */
    public static final byte TYPE_STREAM_FILE = 3;
    /**
     * 长链接流类型
     */
    public static final byte TYPE_STREAM_DIRECT = 4;

    protected long length;

    private Stream stream;

    public abstract byte type();

    public long length() {
        return length;
    }

    protected abstract Stream createStream();

    protected void closeStream(Stream stream) throws IOException {
        stream.close();
    }

    public final Stream open() {
        if (stream == null) {
            stream = createStream();
        }
        return stream;
    }

    @Override
    public final void close() throws IOException {
        if (!Objects.isNull(stream)) {
            closeStream(stream);
            stream = null;
        }
    }

    /**
     * 头部额外信息，用于携带额外到校验信息等
     */
    public byte[] headerInfo() {

        return null;
    }
}
