package com.nebula.uid.worker;

import com.nebula.uid.utils.ValuedEnum;

/**
 * 节点类型枚举。
 * <ul>
 *   <li>CONTAINER：容器化节点（如 Docker）</li>
 *   <li>ACTUAL：物理机/虚拟机节点</li>
 * </ul>
 *
 * @author nebula
 */
public enum WorkerNodeType implements ValuedEnum<Integer> {

    /**
     * 容器化节点（如 Docker）
     */
    CONTAINER(1),
    /**
     * 物理机/虚拟机节点
     */
    ACTUAL(2);

    /**
     * 节点类型值
     */
    private final Integer type;

    /**
     * 构造节点类型
     *
     * @param type 节点类型值
     */
    WorkerNodeType(Integer type) {
        this.type = type;
    }

    @Override
    public Integer value() {
        return type;
    }
}
