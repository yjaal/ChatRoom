package net.qiujuer.lesson.sample.foo;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.util.Objects;
import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.Timer;

/**
 * 页面显示
 *
 * @author YJ
 * @date 2021/6/18
 **/
public class FooGui extends JFrame {

    private Timer timer;
    private JLabel label;

    public FooGui(String name, Callback callback) {
        super(name);
        // 窗口模式设置
        JFrame.setDefaultLookAndFeelDecorated(true);

        // 创建及设置窗口
        setLayout(new BorderLayout());
        setMaximumSize(new Dimension(280, 160));
        setMinimumSize(new Dimension(280, 160));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // 界面上的文字
        label = new JLabel(name, SwingConstants.CENTER);
        add(label, BorderLayout.CENTER);

        // 循环刷新
        timer = new Timer(2000, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Object[] objs = callback.takeText();
                if (Objects.isNull(objs) || objs.length == 0) {
                    return;
                }
                StringBuilder text = new StringBuilder("<html>");
                for (int i = 0; i < objs.length; i++) {
                    text.append(objs[i].toString());
                    if (i != objs.length - 1) {
                        text.append("<br/>");
                    }
                }
                text.append("</html>");
                update(text.toString());
            }
        });
    }

    public void doShow() {
        javax.swing.SwingUtilities.invokeLater(() ->{

        });
    }



    public interface Callback {

        Object[] takeText();
    }
}
