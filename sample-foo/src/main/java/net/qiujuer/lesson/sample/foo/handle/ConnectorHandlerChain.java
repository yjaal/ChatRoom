package net.qiujuer.lesson.sample.foo.handle;

/**
 * 客户端连接责任链
 *
 * @author YJ
 * @date 2021/6/25
 **/
public abstract class ConnectorHandlerChain<Model> {

    private ConnectorHandlerChain<Model> next;

    /**
     * 在尾部添加
     */
    public ConnectorHandlerChain<Model> appendLast(ConnectorHandlerChain<Model> newChain) {
        // 这里定义一个规则：一个子类只能被添加一次，避免循环，getClass返回类信息
        if (this == newChain || this.getClass().equals(newChain.getClass())) {
            return this;
        }

        synchronized (this) {
            if (next == null) {
                next = newChain;
                return newChain;
            }
        }

        return next.appendLast(newChain);
    }

    /**
     * 处理方法
     */
    boolean handle(ConnectorHandler handler, Model model) {
        ConnectorHandlerChain<Model> next = this.next;

        // 自己消费
        if (consume(handler, model)) {
            return true;
        }

        // 子节点的消费情况
        boolean childConsumeRes = (next != null && next.handle(handler, model));
        if (childConsumeRes) {
            return true;
        }

        // 子节点没有消费，那么再次进行消费
        return this.consumeAgain(handler, model);
    }

    public synchronized boolean remove(Class<? extends ConnectorHandlerChain<Model>> clazz) {
        if (this.getClass().equals(clazz)) {
            return false;
        }

        synchronized (this) {
            if (next == null) {
                return false;
            } else if (next.getClass().equals(clazz)) {
                this.next = next.next;
                return true;
            } else {
                return next.remove(clazz);
            }
        }
    }

    protected abstract boolean consume(ConnectorHandler handler, Model model);

    /**
     * 刚开始自己没有消费，然后后面节点也没有消费，则再次消费，比如此客户端不在任何一个群里
     */
    protected boolean consumeAgain(ConnectorHandler handler, Model model) {
        return false;
    }
}
