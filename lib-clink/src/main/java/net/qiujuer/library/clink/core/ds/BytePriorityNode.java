package net.qiujuer.library.clink.core.ds;

/**
 * 带优先级带节点，可用于构成链表，在发送的过程中可以按照优先级进行发送
 *
 * @author YJ
 * @date 2021/5/12
 **/
public class BytePriorityNode<Item> {

    public byte priority;

    public Item item;

    public BytePriorityNode<Item> next;

    public BytePriorityNode(Item item) {
        this.item = item;
    }

    /**
     * 按优先级追加到当前链表中
     */
    public void appendWithPriority(BytePriorityNode<Item> node) {
        if (next == null) {
            next = node;
        } else {
            BytePriorityNode<Item> after = this.next;
            if (after.priority < node.priority) {
                // 中间位置插入
                this.next = node;
                node.next = after;
            } else {
                after.appendWithPriority(node);
            }
        }
    }
}
