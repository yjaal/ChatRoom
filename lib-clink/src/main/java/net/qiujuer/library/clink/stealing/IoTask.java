package net.qiujuer.library.clink.stealing;

import java.nio.channels.SocketChannel;
import net.qiujuer.library.clink.core.IoProvider;
import net.qiujuer.library.clink.core.IoProvider.HandleProviderCallback;

/**
 * 可以进行调度的任务封装，任务执行的回调、当前任务类型、任务对应的通道
 *
 * @author YJ
 * @date 2021/7/13
 **/
public class IoTask {

    public final SocketChannel channel;

    public final IoProvider.HandleProviderCallback providerCallback;

    public final int ops;

    public IoTask(SocketChannel channel, int ops,
        HandleProviderCallback providerCallback) {
        this.channel = channel;
        this.providerCallback = providerCallback;
        this.ops = ops;
    }
}
