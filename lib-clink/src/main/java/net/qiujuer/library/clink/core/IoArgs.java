package net.qiujuer.library.clink.core;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;

/**
 * 这里主要是对buffer进行封装,一定要注意网络读写和本地读写是相反的，比如在发送的时候readFrom从
 * <P> channel或者bytes中读取数据到buffer中，然后进行网络发送
 * <p> 而通过writeTo方法又从buffer中获取数据写入到channel或者bytes进行数据接收
 */
public class IoArgs {

    private int limit = 256;
    private ByteBuffer buffer = ByteBuffer.allocate(limit);

    /**
     * 表示本次读取了多大数据，从channel中读，往buffer中写
     */
    public int readFrom(ReadableByteChannel channel) throws IOException {

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
     * 从buffer中读，往channel中写
     */
    public int writeTo(WritableByteChannel channel) throws IOException {
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
        this.limit = Math.min(limit, buffer.capacity());
    }

    /**
     * 设置写入的长度，相当于数据的包头
     */
    public void writeLen(int total) {
        // 这里就相当于将头和数据部分分开了
        startWriting();
        buffer.putInt(total);
        finishWriting();
    }

    public int readLen() {
        return buffer.getInt();
    }

    public int capacity() {
        return buffer.capacity();
    }

    public boolean remained() {
        // 返回当前buffer可存储空间是否大于0
        return buffer.remaining() > 0;
    }

    /**
     * 从bytes数组中进行消费, bytes-->buffer
     */
    public int readFrom(byte[] bytes, int offset, int count) {
        int size = Math.min(count, buffer.remaining());
        if (size <= 0) {
            return 0;
        }
        buffer.put(bytes, offset, size);
        return size;
    }

    /**
     * 从buffer数组中进行读取, buffer-->bytes
     */
    public int writeTo(byte[] bytes, int offset, int count) {
        int size = Math.min(count, buffer.remaining());
        if (size <= 0) {
            return 0;
        }
        buffer.get(bytes, offset, size);
        return size;
    }


    /**
     * 数据提供者、处理者，数据的生产或者消费提供方
     */
    public interface IOArgsEventProcessor {

        IoArgs provideIoArgs();

        void onConsumeFailed(IoArgs args, Exception e);

        void onConsumeCompleted(IoArgs args);
    }
}
