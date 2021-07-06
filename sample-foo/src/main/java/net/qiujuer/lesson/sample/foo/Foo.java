package net.qiujuer.lesson.sample.foo;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class Foo {

    private static final String CACHE_DIR = "cache";

    public static final String COMMAND_EXIT = "00bye00";
    public static final String COMMAND_GROUP_JOIN = "--m g join";
    public static final String COMMAND_GROUP_LEAVE = "--m g leave";
    public static final String DEFAULT_GROUP_NAME = "GG";

    /**
     * 绑定Stream到一个命令链接（带参数）
     */
    public static final String COMMAND_CONNECTOR_BIND = "--m c bind ";

    /**
     * 创建对话房间
     */
    public static final String COMMAND_AUDIO_CREATE_ROOM = "--m a create";

    /**
     * 加入对话房间（带参数）最后有空格
     */
    public static final String COMMAND_AUDIO_JOIN_ROOM = "--m a join ";

    /**
     * 主动离开对话房间
     */
    public static final String COMMAND_AUDIO_LEAVE_ROOM = "--m a leave";

    /**
     * 回送服务器傻姑娘的唯一标志（带参数）
     */
    public static final String COMMAND_INFO_NAME = "--i server ";

    /**
     * 回送语音群名（带参数）
     */
    public static final String COMMAND_INFO_AUDIO_ROOM = "--i a room ";

    /**
     * 回送语音开始（带参数）
     */
    public static final String COMMAND_INFO_AUDIO_START = "--i a start ";

    /**
     * 回送语音结束
     */
    public static final String COMMAND_INFO_AUDIO_STOP = "--i a stop";

    /**
     * 回送语音异常
     */
    public static final String COMMAND_INFO_AUDIO_ERROR = "--i a error";

    /**
     * 创建一个缓存的目录
     */
    public static File getCacheDir(String dir) {
        String path =
            System.getProperty("user.dir") + File.separator + CACHE_DIR + File.separator + dir;
        File file = new File(path);
        if (!file.exists()) {
            if (!file.mkdirs()) {
                throw new RuntimeException("目录创建失败");
            }
        }
        return file;
    }

    /**
     * 创建一个真实的临时文件
     */
    public static File createRandomTemp(File parent) {
        String filename = UUID.randomUUID().toString() + ".tmp";
        File file = new File(parent, filename);
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file;
    }


}
