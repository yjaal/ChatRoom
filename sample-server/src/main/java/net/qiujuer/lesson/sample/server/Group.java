package net.qiujuer.lesson.sample.server;

import java.util.ArrayList;
import java.util.List;
import net.qiujuer.lesson.sample.foo.handle.ConnectorHandler;
import net.qiujuer.lesson.sample.foo.handle.ConnectorStringPacketChain;
import net.qiujuer.library.clink.box.StringReceivePacket;

public class Group {

    private final String name;

    private final GroupMsgAdapter adapter;

    private final List<ConnectorHandler> members = new ArrayList<>();

    public Group(String name, GroupMsgAdapter adapter) {
        this.name = name;
        this.adapter = adapter;
    }

    public String getName() {
        return name;
    }

    /**
     * 加入群
     */
    boolean addMember(ConnectorHandler handler) {
        synchronized (members) {
            if (!members.contains(handler)) {
                members.add(handler);
                // 添加链式结构
                handler.getStringPacketChain().appendLast(new ForwardConnectorStrPacketChain());
                System.out.println("Group[ " + name + " ] add new member: " + handler.getClientInfo());
                return true;
            }
        }
        return false;
    }

    /**
     * 退出群
     */
    boolean removeMember(ConnectorHandler handler) {
        synchronized (members) {
            if (members.remove(handler)) {
                handler.getStringPacketChain().remove(ForwardConnectorStrPacketChain.class);
                System.out.println("Group[ " + name + " ] leave a member: " + handler.getClientInfo());
                return true;
            }
        }
        return false;
    }

    /**
     * 消息转发
     */
    private class ForwardConnectorStrPacketChain extends ConnectorStringPacketChain {
        @Override
        protected boolean consume(ConnectorHandler handler, StringReceivePacket packet) {
            // 消息转发
            synchronized (members) {
                for (ConnectorHandler member : members) {
                    if (member == handler) {
                        continue;
                    }
                    adapter.sendMsg2Client(member, packet.entity());
                }
                return true;
            }
        }
    }

    interface GroupMsgAdapter {
        void sendMsg2Client(ConnectorHandler handler, String msg);
    }
}
