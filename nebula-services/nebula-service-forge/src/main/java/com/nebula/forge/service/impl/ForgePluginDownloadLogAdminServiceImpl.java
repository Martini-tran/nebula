package com.nebula.forge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.common.core.domain.PageResult;
import com.nebula.forge.dto.admin.ForgePluginDownloadLogPageQuery;
import com.nebula.forge.entity.ForgePluginDownloadLog;
import com.nebula.forge.mapper.ForgePluginDownloadLogMapper;
import com.nebula.forge.service.ForgePluginDownloadLogAdminService;
import com.nebula.forge.vo.admin.ForgePluginDownloadLogAdminVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 插件下载日志查询服务实现（管理员端，只读）
 *
 * @author nebula
 */
@Service
@RequiredArgsConstructor
public class ForgePluginDownloadLogAdminServiceImpl implements ForgePluginDownloadLogAdminService {

    private final ForgePluginDownloadLogMapper downloadLogMapper;

    @Override
    public PageResult<ForgePluginDownloadLogAdminVO> page(ForgePluginDownloadLogPageQuery query) {
        ForgePluginDownloadLogPageQuery safe = query == null ? new ForgePluginDownloadLogPageQuery() : query;
        Page<ForgePluginDownloadLog> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<ForgePluginDownloadLog> wrapper = new LambdaQueryWrapper<ForgePluginDownloadLog>()
                .eq(safe.getPluginId() != null, ForgePluginDownloadLog::getPluginId, safe.getPluginId())
                .eq(safe.getVersionId() != null, ForgePluginDownloadLog::getVersionId, safe.getVersionId())
                .eq(safe.getUserId() != null, ForgePluginDownloadLog::getUserId, safe.getUserId())
                .eq(safe.getResult() != null, ForgePluginDownloadLog::getResult, safe.getResult())
                .ge(safe.getStartTime() != null, ForgePluginDownloadLog::getCreateTime, safe.getStartTime())
                .le(safe.getEndTime() != null, ForgePluginDownloadLog::getCreateTime, safe.getEndTime())
                .orderByDesc(ForgePluginDownloadLog::getCreateTime);
        Page<ForgePluginDownloadLog> result = downloadLogMapper.selectPage(page, wrapper);
        return PageResult.of(result.getRecords().stream().map(this::toVO).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    private ForgePluginDownloadLogAdminVO toVO(ForgePluginDownloadLog d) {
        ForgePluginDownloadLogAdminVO vo = new ForgePluginDownloadLogAdminVO();
        vo.setId(d.getId());
        vo.setUserId(d.getUserId());
        vo.setPluginId(d.getPluginId());
        vo.setVersionId(d.getVersionId());
        vo.setClientVersion(d.getClientVersion());
        vo.setClientOs(d.getClientOs());
        vo.setIp(d.getIp());
        vo.setUserAgent(d.getUserAgent());
        vo.setResult(d.getResult());
        vo.setErrorMsg(d.getErrorMsg());
        vo.setCreateTime(d.getCreateTime());
        return vo;
    }
}
