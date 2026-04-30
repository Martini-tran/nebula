package com.nebula.uid;

import com.nebula.uid.exception.UidGenerateException;

/**
 * 全局唯一 ID 生成器接口
 *
 * 
 *
 * @author nebula
 */
public interface UidGenerator {

    /**
     * 生成一个全局唯一 ID
     *
     * @return UID
     * @throws UidGenerateException 生成失败时抛出
     */
    long getUID() throws UidGenerateException;

    /**
     * 解析 UID 各字段（时间戳、workerId、序列号等）用于排查
     *
     * @param uid 待解析的 UID
     * @return 解析后的可读字符串
     */
    String parseUID(long uid);
}
