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
