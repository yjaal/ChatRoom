package net.qiujuer.library.clink.core;

import java.io.Closeable;
import java.io.IOException;
import net.qiujuer.library.clink.core.IoArgs.IOArgsEventProcessor;

public interface Sender extends Closeable {

    void setSendListener(IOArgsEventProcessor ioArgsProcessor);

    boolean postSendAsync() throws IOException;

    long getLastWriterTime();
}
