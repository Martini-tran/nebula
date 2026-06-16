package com.nebula.forge.dto.admin;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 插件下载日志分页查询参数
 *
 * @author nebula
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ForgePluginDownloadLogPageQuery extends PageQuery {

    /**
     * 插件ID
     */
    private Long pluginId;

    /**
     * 版本ID
     */
    private Long versionId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 结果：1成功 0失败
     */
    private Integer result;

    /**
     * 起始时间（含）
     */
    private LocalDateTime startTime;

    /**
     * 结束时间（含）
     */
    private LocalDateTime endTime;
}
