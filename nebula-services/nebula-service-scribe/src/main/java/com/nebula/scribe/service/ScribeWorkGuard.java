package com.nebula.scribe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.scribe.entity.ScribeWork;
import com.nebula.scribe.mapper.ScribeWorkMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 作品归属校验：卷、章节等子资源都不冗余 user_id，统一先过这一关
 */
@Component
@RequiredArgsConstructor
public class ScribeWorkGuard {

    private final ScribeWorkMapper workMapper;

    /**
     * 按「id + 当前用户」取作品；别人的作品与不存在一律 404，不暴露他人作品是否存在
     */
    public ScribeWork requireOwnedWork(Long workId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "未获取到当前用户");
        }
        ScribeWork work = workId == null ? null : workMapper.selectOne(new LambdaQueryWrapper<ScribeWork>()
                .eq(ScribeWork::getId, workId)
                .eq(ScribeWork::getUserId, userId));
        if (work == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "作品不存在");
        }
        return work;
    }
}
