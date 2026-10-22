package com.bili.translator.util;

/**
 * 子进程执行超时。
 */
public class ProcessTimeoutException extends RuntimeException {

    public ProcessTimeoutException(String message) {
        super(message);
    }
}
