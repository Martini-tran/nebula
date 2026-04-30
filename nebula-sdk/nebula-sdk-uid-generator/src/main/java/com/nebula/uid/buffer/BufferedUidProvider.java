package com.nebula.uid.buffer;

import java.util.List;

/**
 * 在指定秒内批量生产 UID 的提供者，支持 Lambda。
 *
 * <p>派生自 baidu/uid-generator (Apache License 2.0)</p>
 *
 * @author nebula
 */
@FunctionalInterface
public interface BufferedUidProvider {

    /**
     * 在指定秒内提供该秒可用的全部 UID
     *
     * @param momentInSecond 秒级时间戳
     * @return UID 列表
     */
    List<Long> provide(long momentInSecond);
}
