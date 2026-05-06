package com.nebula.uid.utils;

import java.io.Serial;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 缓存行填充版本的 {@link AtomicLong}，用于避免伪共享（False Sharing）问题。
 * 典型 CPU 缓存行为 64 字节，通过填充 6 个 long（48 字节）将一个 long 值
 * 独占一个缓存行：64 字节 = 8（对象头引用） + 6 * 8（填充） + 8（实际值）。
 *
 * @author nebula
 */
public class PaddedAtomicLong extends AtomicLong {

    @Serial
    private static final long serialVersionUID = -3415778863941386253L;

    /**
     * 填充 6 个 long（48 字节），volatile 防止 JIT 优化掉
     */
    public volatile long p1, p2, p3, p4, p5, p6 = 7L;

    /**
     * 无参构造，初始值为 0
     */
    public PaddedAtomicLong() {
        super();
    }

    /**
     * 指定初始值构造
     *
     * @param initialValue 初始值
     */
    public PaddedAtomicLong(long initialValue) {
        super(initialValue);
    }

    /**
     * 防止 JVM 因为认为填充字段未被使用而进行 GC 优化清理
     */
    public long sumPaddingToPreventOptimization() {
        return p1 + p2 + p3 + p4 + p5 + p6;
    }
}
