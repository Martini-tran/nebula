package com.nebula.uid.utils;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 命名线程工厂，未指定线程名时根据调用方类名自动生成名称。
 *
 * @author nebula
 */
@Slf4j
@Getter
@Setter
public class NamingThreadFactory implements ThreadFactory {

    /** 线程名前缀 */
    private String name;

    /** 是否守护线程 */
    private boolean daemon;

    /** 未捕获异常处理器 */
    private UncaughtExceptionHandler uncaughtExceptionHandler;

    /** 不同前缀的命名序列 */
    private final ConcurrentHashMap<String, AtomicLong> sequences;

    public NamingThreadFactory() {
        this(null, false, null);
    }

    public NamingThreadFactory(String name) {
        this(name, false, null);
    }

    public NamingThreadFactory(String name, boolean daemon) {
        this(name, daemon, null);
    }

    public NamingThreadFactory(String name, boolean daemon, UncaughtExceptionHandler handler) {
        this.name = name;
        this.daemon = daemon;
        this.uncaughtExceptionHandler = handler;
        this.sequences = new ConcurrentHashMap<>();
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(r);
        thread.setDaemon(this.daemon);

        String prefix = this.name;
        if (prefix == null || prefix.isBlank()) {
            prefix = getInvoker(2);
        }
        thread.setName(prefix + "-" + getSequence(prefix));

        if (this.uncaughtExceptionHandler != null) {
            thread.setUncaughtExceptionHandler(this.uncaughtExceptionHandler);
        } else {
            thread.setUncaughtExceptionHandler(
                    (t, e) -> log.error("unhandled exception in thread: {}:{}", t.getId(), t.getName(), e));
        }

        return thread;
    }

    /**
     * 获取调用方的简单类名
     */
    private String getInvoker(int depth) {
        StackTraceElement[] stes = new Exception().getStackTrace();
        if (stes.length > depth) {
            String className = stes[depth].getClassName();
            int lastDot = className.lastIndexOf('.');
            return lastDot < 0 ? className : className.substring(lastDot + 1);
        }
        return getClass().getSimpleName();
    }

    private long getSequence(String invoker) {
        AtomicLong r = this.sequences.computeIfAbsent(invoker, k -> new AtomicLong(0));
        return r.incrementAndGet();
    }
}
