package com.nebula.uid.worker;

import com.nebula.uid.utils.ValuedEnum;

/**
 * 节点类型
 * 
 *  CONTAINER：容器化节点（如 Docker）
 *  ACTUAL：物理/虚拟机节点
 * 
 *
 * 
 *
 * @author nebula
 */
public enum WorkerNodeType implements ValuedEnum<Integer> {

    CONTAINER(1),
    ACTUAL(2);

    private final Integer type;

    WorkerNodeType(Integer type) {
        this.type = type;
    }

    @Override
    public Integer value() {
        return type;
    }
}
