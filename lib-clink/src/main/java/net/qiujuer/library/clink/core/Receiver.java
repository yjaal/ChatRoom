package net.qiujuer.library.clink.core;

import java.io.Closeable;
import java.io.IOException;
import net.qiujuer.library.clink.core.IoArgs.IOArgsEventListener;

public interface Receiver extends Closeable {

    /**
     * 设置监听
     */
    void setReceiveListener(IOArgsEventListener listener);

    /**
     * 传入IoArgs，因为每次传输到数据大小不一样，所以需要业务层设置好了之后传入进来
     */
    boolean receiveAsync(IoArgs args) throws IOException;

}
