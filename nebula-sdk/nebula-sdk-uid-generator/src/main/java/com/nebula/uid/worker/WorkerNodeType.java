package com.nebula.uid.worker;

import com.nebula.uid.utils.ValuedEnum;

/**
 * 节点类型
 * <ul>
 *   <li>CONTAINER：容器化节点（如 Docker）</li>
 *   <li>ACTUAL：物理/虚拟机节点</li>
 * </ul>
 *
 * <p>派生自 baidu/uid-generator (Apache License 2.0)</p>
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
