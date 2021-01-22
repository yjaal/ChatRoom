package net.qiujuer.library.clink.core;

import java.io.Closeable;
import java.io.IOException;

public interface Receiver extends Closeable {

    boolean receiveAsync(IoArgs.IOArgsEventListner listner) throws IOException;

}
