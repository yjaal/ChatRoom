package net.qiujuer.library.clink.core;

import java.io.Closeable;
import java.io.IOException;
import net.qiujuer.library.clink.core.IoArgs.IOArgsEventProcessor;

public interface Receiver extends Closeable {

    void setReceiveListener(IOArgsEventProcessor ioArgsProcessor);

    boolean postReceiveAsync() throws IOException;

    long getLastReadTime();
}
