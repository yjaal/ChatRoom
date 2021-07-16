package net.qiujuer.library.clink.core;

import java.io.Closeable;
import net.qiujuer.library.clink.core.IoArgs.IOArgsEventProcessor;

public interface Receiver extends Closeable {

    void setReceiveListener(IOArgsEventProcessor ioArgsProcessor);

    void postReceiveAsync() throws Exception;

    long getLastReadTime();
}
