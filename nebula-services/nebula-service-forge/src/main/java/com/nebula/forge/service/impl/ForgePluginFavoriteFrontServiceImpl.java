package com.nebula.forge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.domain.PageQuery;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import com.nebula.forge.entity.ForgePlugin;
import com.nebula.forge.entity.ForgePluginFavorite;
import com.nebula.forge.mapper.ForgePluginFavoriteMapper;
import com.nebula.forge.mapper.ForgePluginMapper;
import com.nebula.forge.service.ForgePluginFavoriteFrontService;
import com.nebula.forge.service.ForgePluginFrontService;
import com.nebula.forge.vo.front.ForgePluginFrontVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 插件收藏前台服务实现（需登录）
 *
 * @author nebula
 */
@Service
@RequiredArgsConstructor
public class ForgePluginFavoriteFrontServiceImpl implements ForgePluginFavoriteFrontService {

    /** 插件状态：已上架 */
    private static final int PLUGIN_STATUS_ON = 1;

    private final ForgePluginFavoriteMapper favoriteMapper;
    private final ForgePluginMapper pluginMapper;
    private final ForgePluginFrontService pluginFrontService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(Long pluginId) {
        Long userId = requireLoginUserId();
        requireOnlinePlugin(pluginId);
        // 唯一键 (plugin_id, user_id)：已收藏则幂等返回
        Long exists = favoriteMapper.selectCount(
                new LambdaQueryWrapper<ForgePluginFavorite>()
                        .eq(ForgePluginFavorite::getPluginId, pluginId)
                        .eq(ForgePluginFavorite::getUserId, userId));
        if (exists != null && exists > 0) {
            return;
        }
        ForgePluginFavorite favorite = new ForgePluginFavorite();
        favorite.setPluginId(pluginId);
        favorite.setUserId(userId);
        favoriteMapper.insert(favorite);
        pluginMapper.update(null, new LambdaUpdateWrapper<ForgePlugin>()
                .eq(ForgePlugin::getId, pluginId)
                .setSql("favorite_count = favorite_count + 1"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long pluginId) {
        Long userId = requireLoginUserId();
        int deleted = favoriteMapper.delete(
                new LambdaQueryWrapper<ForgePluginFavorite>()
                        .eq(ForgePluginFavorite::getPluginId, pluginId)
                        .eq(ForgePluginFavorite::getUserId, userId));
        if (deleted > 0) {
            pluginMapper.update(null, new LambdaUpdateWrapper<ForgePlugin>()
                    .eq(ForgePlugin::getId, pluginId)
                    .setSql("favorite_count = GREATEST(favorite_count - 1, 0)"));
        }
    }

    @Override
    public PageResult<ForgePluginFrontVO> myFavorites(PageQuery query) {
        Long userId = requireLoginUserId();
        PageQuery safe = query == null ? new PageQuery() : query;
        Page<ForgePluginFavorite> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        Page<ForgePluginFavorite> result = favoriteMapper.selectPage(page,
                new LambdaQueryWrapper<ForgePluginFavorite>()
                        .eq(ForgePluginFavorite::getUserId, userId)
                        .orderByDesc(ForgePluginFavorite::getCreateTime));
        List<Long> pluginIds = result.getRecords().stream()
                .map(ForgePluginFavorite::getPluginId).toList();
        List<ForgePluginFrontVO> rows = pluginFrontService.toFrontVOByIds(pluginIds);
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public boolean isFavorited(Long pluginId) {
        Long userId = UserContext.getUserId();
        if (userId == null || pluginId == null) {
            return false;
        }
        Long count = favoriteMapper.selectCount(
                new LambdaQueryWrapper<ForgePluginFavorite>()
                        .eq(ForgePluginFavorite::getPluginId, pluginId)
                        .eq(ForgePluginFavorite::getUserId, userId));
        return count != null && count > 0;
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
}
