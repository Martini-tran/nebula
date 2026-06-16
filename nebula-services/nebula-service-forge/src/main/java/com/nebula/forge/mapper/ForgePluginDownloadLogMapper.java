package com.nebula.forge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.forge.entity.ForgePluginDownloadLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 插件下载日志表 Mapper
 *
 * @author nebula
 */
@Mapper
public interface ForgePluginDownloadLogMapper extends BaseMapper<ForgePluginDownloadLog> {
}
