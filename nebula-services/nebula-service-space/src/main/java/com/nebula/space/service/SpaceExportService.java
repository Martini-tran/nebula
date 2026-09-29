package com.nebula.space.service;

import java.util.Map;

/**
 * 「导出全部数据」：当前用户各模块的数据打成一份 JSON
 */
public interface SpaceExportService {

    /**
     * 取齐各模块的数据；结构与列表接口一致（version 1，与前端早先拼的导出文件相同，另加 counts / total 与记账预算）
     *
     * <p>文件柜只有文件信息，文件本身在文件柜里下载；书签另有 Chrome 兼容 HTML 导出，不放进来。</p>
     */
    Map<String, Object> snapshot();
}
