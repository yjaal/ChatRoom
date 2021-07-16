package net.qiujuer.library.clink.core;

import java.io.Closeable;
import net.qiujuer.library.clink.core.IoArgs.IOArgsEventProcessor;

public interface Sender extends Closeable {

    void setSendListener(IOArgsEventProcessor ioArgsProcessor);

    void postSendAsync() throws Exception;

    long getLastWriterTime();
}
