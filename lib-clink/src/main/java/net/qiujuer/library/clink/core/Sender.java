package net.qiujuer.library.clink.core;

import java.io.Closeable;
import java.io.IOException;

public interface Sender extends Closeable {

    boolean sendAsync(IoArgs args, IoArgs.IOArgsEventListner listner) throws IOException;

}
