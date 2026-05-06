package com.nebula.uid.worker;

import com.nebula.uid.impl.DefaultUidGenerator;

/**
 * Worker Id 分配器接口，{@link DefaultUidGenerator} 启动时调用此接口分配 workerId。
 *
 * @author nebula
 */
public interface WorkerIdAssigner {

    /**
     * 为当前节点分配 workerId
     *
     * @return 分配到的 workerId
     */
    long assignWorkerId();
}
