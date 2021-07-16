package net.qiujuer.library.clink.impl.exceptions;

import java.io.IOException;

/**
 * @author YJ
 * @date 2021/7/15
 **/
public class EmptyIoArgsException extends IOException {

    public EmptyIoArgsException(String msg) {
        super(msg);
    }
}
