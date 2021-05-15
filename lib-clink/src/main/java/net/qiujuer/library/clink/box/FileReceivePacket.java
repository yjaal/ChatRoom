package net.qiujuer.library.clink.box;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import net.qiujuer.library.clink.core.ReceivePacket;

/**
 * 文件接收包
 *
 * @author YJ
 * @date 2021/5/8
 **/
public class FileReceivePacket extends ReceivePacket<FileOutputStream, File> {

    private File file;

    public FileReceivePacket(long len, File file) {
        super(len);
        this.file = file;
    }

    @Override
    public byte type() {
        return TYPE_STREAM_FILE;
    }

    @Override
    protected File buildEntity(FileOutputStream stream) {
        return file;
    }

    @Override
    protected FileOutputStream createStream() {
        try {
            return new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            return null;
        }
    }
}
