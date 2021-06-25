package net.qiujuer.lesson.sample.server.handle;

import net.qiujuer.library.clink.core.Connector;

/**
 * 关闭链接链式结构
 *
 * @author YJ
 * @date 2021/6/25
 **/
public class DefaultPrintConnectorCloseChain extends ConnectorCloseChain {

    @Override
    protected boolean consume(ClientHandler handler, Connector connector) {
        System.out.println(handler.getClientInfo() + " :Exit, key: " + handler.getKey());
        return false;
    }
}
