package net.qiujuer.library.clink.core;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.UUID;
import net.qiujuer.library.clink.core.IoArgs.IOArgsEventListner;
import net.qiujuer.library.clink.impl.SocketChannelAdapter;

/**
 * 用于标识服务端和客户端的某个连接
 */
public class Connector implements Closeable, SocketChannelAdapter.OnChannelStatusChangedListener {

    /**
     * 这里用于标识连接的唯一性
     */
    private UUID key = UUID.randomUUID();
    private SocketChannel channel;
    private Sender sender;
    private Receiver receiver;

    public void setup(SocketChannel channel) throws IOException {
        this.channel = channel;

        IoContext context = IoContext.get();
        SocketChannelAdapter adapter = new SocketChannelAdapter(channel, context.getIoProvider(), this);

        // 读写都有SocketChannelAdapter完成,，同时还可以通过回调完成通道关闭后要处理的事情
        this.sender = adapter;
        this.receiver = adapter;

        readNextMsg();
    }

    private void readNextMsg() {
        if (!Objects.isNull(receiver)) {
            try {
                receiver.receiveAsync(echoReceiveListener);
            } catch (IOException e) {
                System.out.println("开始接收数据异常" + e.getMessage());
            }
        }
    }

    @Override
    public void close() throws IOException {

    }

    private IoArgs.IOArgsEventListner echoReceiveListener = new IOArgsEventListner() {
        @Override
        public void onStarted(IoArgs args) {

        }

        @Override
        public void onCompleted(IoArgs args) {
            // 打印
            onReceiveNewMsg(args.bufferString());
            // 开始读取下一条数据
            readNextMsg();
        }
    };

    protected void onReceiveNewMsg(String str) {
        System.out.println(key.toString() + ": " + str);
    }

    @Override
    public void onChannelClosed(SocketChannel channel) {

    }
}
