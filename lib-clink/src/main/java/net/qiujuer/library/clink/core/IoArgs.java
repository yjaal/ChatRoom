package net.qiujuer.library.clink.core;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * 这里主要是对buffer进行封装
 */
public class IoArgs {

    private int limit = 256;
    private byte[] byteBuffer = new byte[256];
    private ByteBuffer buffer = ByteBuffer.wrap(byteBuffer);

    /**
     * 表示本次读取了多大数据，从bytes中读，往buffer中写
     */
    public int readFrom(byte[] bytes, int offset) {
        // 当前可以读取的数据和当前缓存可以容纳的数据之间取较小的
        int size = Math.min(bytes.length - offset, buffer.remaining());
        buffer.put(bytes, offset, size);
        return size;
    }

    /**
     * 从buffer中读，往bytes中写
     */
    public int writeTo(byte[] bytes, int offset) {
        int size = Math.min(bytes.length - offset, buffer.remaining());
        buffer.get(bytes, offset, size);
        return size;
    }

    /**
     * 从channel中读数据
     */
    public int readFrom(SocketChannel channel) throws IOException {
        startWriting();
        // 当前读取到了多少数据，注意这里应该读取多少数据是由limit控制的，我们可以设置每次读取的数据量
        int bytesProduced = 0;
        while (buffer.hasRemaining()) {
            int len = channel.read(buffer);
            if (len < 0) {
                throw new EOFException();
            }
            bytesProduced += len;
        }

        finishWriting();
        return bytesProduced;
    }

    /**
     * 写数据到channel中
     */
    public int writeTo(SocketChannel channel) throws IOException {

        // 当前读取到了多少数据，注意这里应该读取多少数据是由limit控制的，我们可以设置每次读取的数据量
        int bytesProduced = 0;
        while (buffer.hasRemaining()) {
            int len = channel.write(buffer);
            if (len < 0) {
                throw new EOFException();
            }
            bytesProduced += len;
        }
        return bytesProduced;
    }

    /**
     * 开始写入数据
     */
    public void startWriting() {
        // 先清理
        buffer.clear();
        // 定义容纳区间
        buffer.limit(limit);
    }

    /**
     * 完成写入数据
     */
    public void finishWriting() {
        // 将状态还原
        buffer.flip();
    }

    /**
     * 设置单次写操作的缓存容纳空间
     */
    public void limit(int limit) {
        this.limit = limit;
    }

    /**
     * 设置写入的长度，相当于数据的包头
     */
    public void writeLen(int total) {
        buffer.putInt(total);
    }

    public int readLen() {
        return buffer.getInt();
    }

    public int capacity() {
        return buffer.capacity();
    }


    public interface IOArgsEventListener {

        void onStarted(IoArgs args);

        void onCompleted(IoArgs args);
    }


}
