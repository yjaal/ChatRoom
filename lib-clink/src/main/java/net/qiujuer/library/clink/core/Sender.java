package net.qiujuer.library.clink.core;

import java.io.Closeable;
import java.io.IOException;
import net.qiujuer.library.clink.core.IoArgs.IOArgsEventListener;

public interface Sender extends Closeable {

    boolean sendAsync(IoArgs args, IOArgsEventListener listner) throws IOException;

}
