package com.nebula.uid.utils;

/**
 * 带值枚举接口，配合 {@link EnumUtils} 实现枚举值与对象的相互转换。
 *
 * <p>派生自 baidu/uid-generator (Apache License 2.0)</p>
 *
 * @author nebula
 */
public interface ValuedEnum<T> {

    /**
     * 获取枚举绑定的值
     *
     * @return 枚举值
     */
    T value();
}
