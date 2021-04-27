package net.qiujuer.library.clink.box;

import java.io.File;
import java.io.FileInputStream;
import net.qiujuer.library.clink.core.SendPacket;

/**
 * 文件发送包
 *
 * @author YJ
 * @date 2021/4/25
 **/
public class FileSendPacket extends SendPacket<FileInputStream> {

    public FileSendPacket(File file) {
        this.length = file.length();
    }

    @Override
    protected FileInputStream createStream() {
        return null;
    }
}
