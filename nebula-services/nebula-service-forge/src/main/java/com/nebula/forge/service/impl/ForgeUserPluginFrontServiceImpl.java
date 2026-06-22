package com.nebula.forge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.domain.PageQuery;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import com.nebula.forge.dto.front.ForgePluginInstallRequest;
import com.nebula.forge.dto.front.ForgeUserPluginUpdateRequest;
import com.nebula.forge.entity.ForgePlugin;
import com.nebula.forge.entity.ForgeUserPlugin;
import com.nebula.forge.mapper.ForgePluginMapper;
import com.nebula.forge.mapper.ForgeUserPluginMapper;
import com.nebula.forge.service.ForgePluginFrontService;
import com.nebula.forge.service.ForgeUserPluginFrontService;
import com.nebula.forge.vo.front.ForgePluginFrontVO;
import com.nebula.forge.vo.front.ForgeUserPluginFrontVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 我的插件 / 安装上报前台服务实现（需登录）
 *
 * @author nebula
 */
@Service
@RequiredArgsConstructor
public class ForgeUserPluginFrontServiceImpl implements ForgeUserPluginFrontService {

    /** 插件状态：已上架 */
    private static final int PLUGIN_STATUS_ON = 1;
    /** 启用 */
    private static final int ENABLED = 1;
    /** 自动更新：开 */
    private static final int AUTO_UPDATE_ON = 1;

    private final ForgeUserPluginMapper userPluginMapper;
    private final ForgePluginMapper pluginMapper;
    private final ForgePluginFrontService pluginFrontService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void install(Long pluginId, ForgePluginInstallRequest req) {
        Long userId = requireLoginUserId();
        requireOnlinePlugin(pluginId);
        ForgePluginInstallRequest safe = req == null ? new ForgePluginInstallRequest() : req;

        ForgeUserPlugin existing = userPluginMapper.selectOne(
                new LambdaQueryWrapper<ForgeUserPlugin>()
                        .eq(ForgeUserPlugin::getUserId, userId)
                        .eq(ForgeUserPlugin::getPluginId, pluginId)
                        .last("limit 1"));
        if (existing == null) {
            ForgeUserPlugin row = new ForgeUserPlugin();
            row.setUserId(userId);
            row.setPluginId(pluginId);
            row.setVersionId(safe.getVersionId());
            row.setInstalledVersion(safe.getInstalledVersion());
            row.setEnabled(ENABLED);
            row.setAutoUpdate(safe.getAutoUpdate() == null ? AUTO_UPDATE_ON : safe.getAutoUpdate());
            row.setInstallTime(LocalDateTime.now());
            userPluginMapper.insert(row);
            // 仅首次安装累加安装量
            pluginMapper.update(null, new LambdaUpdateWrapper<ForgePlugin>()
                    .eq(ForgePlugin::getId, pluginId)
                    .setSql("install_count = install_count + 1"));
        } else {
            existing.setVersionId(safe.getVersionId());
            existing.setInstalledVersion(safe.getInstalledVersion());
            if (safe.getAutoUpdate() != null) {
                existing.setAutoUpdate(safe.getAutoUpdate());
            }
            existing.setInstallTime(LocalDateTime.now());
            userPluginMapper.updateById(existing);
        }
    }

    @Override
    public void uninstall(Long pluginId) {
        Long userId = requireLoginUserId();
        userPluginMapper.delete(
                new LambdaQueryWrapper<ForgeUserPlugin>()
                        .eq(ForgeUserPlugin::getUserId, userId)
                        .eq(ForgeUserPlugin::getPluginId, pluginId));
    }

    @Override
    public PageResult<ForgeUserPluginFrontVO> myPlugins(PageQuery query) {
        Long userId = requireLoginUserId();
        PageQuery safe = query == null ? new PageQuery() : query;
        Page<ForgeUserPlugin> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        Page<ForgeUserPlugin> result = userPluginMapper.selectPage(page,
                new LambdaQueryWrapper<ForgeUserPlugin>()
                        .eq(ForgeUserPlugin::getUserId, userId)
                        .orderByDesc(ForgeUserPlugin::getInstallTime));

        List<ForgeUserPlugin> records = result.getRecords();
        List<Long> pluginIds = records.stream().map(ForgeUserPlugin::getPluginId).toList();
        Map<Long, ForgePluginFrontVO> pluginById = pluginFrontService.toFrontVOByIds(pluginIds)
                .stream().collect(Collectors.toMap(ForgePluginFrontVO::getId, Function.identity()));

        List<ForgeUserPluginFrontVO> rows = records.stream()
                .map(up -> toVO(up, pluginById.get(up.getPluginId())))
                .toList();
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public void update(Long pluginId, ForgeUserPluginUpdateRequest req) {
        Long userId = requireLoginUserId();
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        ForgeUserPlugin existing = requireMine(userId, pluginId);
        if (req.getEnabled() != null) {
            existing.setEnabled(req.getEnabled());
        }
        if (req.getAutoUpdate() != null) {
            existing.setAutoUpdate(req.getAutoUpdate());
        }
        if (req.getConfigJson() != null) {
            existing.setConfigJson(req.getConfigJson());
        }
        if (req.getWindowStateJson() != null) {
            existing.setWindowStateJson(req.getWindowStateJson());
        }
        userPluginMapper.updateById(existing);
    }

    @Override
    public void touchUsed(Long pluginId) {
        Long userId = requireLoginUserId();
        userPluginMapper.update(null, new LambdaUpdateWrapper<ForgeUserPlugin>()
                .eq(ForgeUserPlugin::getUserId, userId)
                .eq(ForgeUserPlugin::getPluginId, pluginId)
                .set(ForgeUserPlugin::getLastUsedTime, LocalDateTime.now()));
    }

    // ============ 私有方法 ============

    private Long requireLoginUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return userId;
    }

    private void requireOnlinePlugin(Long pluginId) {
        ForgePlugin plugin = pluginId == null ? null : pluginMapper.selectById(pluginId);
        if (plugin == null || plugin.getStatus() == null || plugin.getStatus() != PLUGIN_STATUS_ON) {
            throw new BizException(HttpStatus.NOT_FOUND, "插件不存在或未上架");
        }
    }

    private ForgeUserPlugin requireMine(Long userId, Long pluginId) {
        ForgeUserPlugin row = pluginId == null ? null : userPluginMapper.selectOne(
                new LambdaQueryWrapper<ForgeUserPlugin>()
                        .eq(ForgeUserPlugin::getUserId, userId)
                        .eq(ForgeUserPlugin::getPluginId, pluginId)
                        .last("limit 1"));
        if (row == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "未安装该插件");
        }
        return row;
    }

    private ForgeUserPluginFrontVO toVO(ForgeUserPlugin up, ForgePluginFrontVO plugin) {
        ForgeUserPluginFrontVO vo = new ForgeUserPluginFrontVO();
        vo.setPlugin(plugin);
        vo.setPluginId(up.getPluginId());
        vo.setVersionId(up.getVersionId());
        vo.setInstalledVersion(up.getInstalledVersion());
        vo.setEnabled(up.getEnabled());
        vo.setAutoUpdate(up.getAutoUpdate());
        vo.setConfigJson(up.getConfigJson());
        vo.setWindowStateJson(up.getWindowStateJson());
        vo.setInstallTime(up.getInstallTime());
        vo.setLastUsedTime(up.getLastUsedTime());
        return vo;
    }
}
