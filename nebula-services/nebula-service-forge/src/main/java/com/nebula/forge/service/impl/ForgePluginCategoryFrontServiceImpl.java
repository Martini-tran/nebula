package com.nebula.forge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.common.file.service.SysFileService;
import com.nebula.forge.entity.ForgePluginCategory;
import com.nebula.forge.mapper.ForgePluginCategoryMapper;
import com.nebula.forge.service.ForgePluginCategoryFrontService;
import com.nebula.forge.vo.front.ForgePluginCategoryFrontVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 插件分类前台服务实现（只读）
 *
 * @author nebula
 */
@Service
@RequiredArgsConstructor
public class ForgePluginCategoryFrontServiceImpl implements ForgePluginCategoryFrontService {

    /** 分类状态：启用 */
    private static final int STATUS_ENABLED = 1;

    private final ForgePluginCategoryMapper categoryMapper;
    private final SysFileService sysFileService;

    @Override
    public List<ForgePluginCategoryFrontVO> list() {
        List<ForgePluginCategory> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<ForgePluginCategory>()
                        .eq(ForgePluginCategory::getStatus, STATUS_ENABLED)
                        .orderByAsc(ForgePluginCategory::getSortOrder)
                        .orderByDesc(ForgePluginCategory::getCreateTime));
        return categories.stream().map(this::toVO).toList();
    }

    private ForgePluginCategoryFrontVO toVO(ForgePluginCategory c) {
        ForgePluginCategoryFrontVO vo = new ForgePluginCategoryFrontVO();
        vo.setId(c.getId());
        vo.setCode(c.getCode());
        vo.setName(c.getName());
        vo.setDescription(c.getDescription());
        vo.setIconFileId(c.getIconFileId());
        vo.setIconUrl(safeFileUrl(c.getIconFileId()));
        vo.setSortOrder(c.getSortOrder());
        return vo;
    }

    /**
     * 将文件ID解析为可访问地址；ID为空或解析失败时返回 null，不影响主流程。
     */
    private String safeFileUrl(Long fileId) {
        if (fileId == null) {
            return null;
        }
        try {
            return sysFileService.getAccessUrl(fileId);
        } catch (Exception ex) {
            return null;
        }
    }
}
