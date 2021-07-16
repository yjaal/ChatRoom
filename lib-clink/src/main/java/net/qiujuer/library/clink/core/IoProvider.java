package net.qiujuer.library.clink.core;

import java.io.Closeable;
import java.nio.channels.SocketChannel;
import net.qiujuer.library.clink.stealing.IoTask;

public interface IoProvider extends Closeable {

    void register(HandleProviderCallback callback) throws Exception;

    void unRegister(SocketChannel channel);

    abstract class HandleProviderCallback extends IoTask implements Runnable {

        protected volatile IoArgs attach;

        private final IoProvider ioProvider;

        public HandleProviderCallback(SocketChannel channel, int ops, IoProvider ioProvider) {
            super(channel, ops);
            this.ioProvider = ioProvider;
        }

        @Override
        public void run() {
            final IoArgs attach = this.attach;
            this.attach = null;
            if (onProviderIo(this.attach)) {
                try {
                    ioProvider.register(this);
                } catch (Exception e) {
                    fireThrowable(e);
                }
            }
        }

        @Override
        public final boolean onProcessIo() {
            final IoArgs attach = this.attach;
            this.attach = null;
            return onProviderIo(attach);
        }

        /**
         * 需要返回当前状态
         */
        protected abstract boolean onProviderIo(IoArgs args);

        public void checkAttachIsNull(){
            if (attach != null) {
                throw new IllegalStateException("Current attach is not null");
            }
        }
    }
}
