package net.qiujuer.library.clink.box;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import net.qiujuer.library.clink.core.SendPacket;

/**
 * 文件发送包
 *
 * @author YJ
 * @date 2021/4/25
 **/
public class FileSendPacket extends SendPacket<FileInputStream> {

    private final File file;

    public FileSendPacket(File file) {
        this.file = file;
        this.length = file.length();
    }

    @Override
    public byte type() {
        return TYPE_STREAM_FILE;
    }

    @Override
    protected FileInputStream createStream() {
        try {
            return new FileInputStream(file);
        } catch (FileNotFoundException e) {
            return null;
        }
    }
}
