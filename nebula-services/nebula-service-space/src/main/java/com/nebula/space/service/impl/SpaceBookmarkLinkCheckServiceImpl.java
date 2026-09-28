package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.bookmark.LinkChecker;
import com.nebula.space.dto.admin.BookmarkLinkCheckRequest;
import com.nebula.space.entity.SpaceBookmark;
import com.nebula.space.mapper.SpaceBookmarkMapper;
import com.nebula.space.service.SpaceBookmarkLinkCheckService;
import com.nebula.space.vo.admin.BookmarkLinkCheckVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 书签链接检查服务实现
 */
@Service
@RequiredArgsConstructor
public class SpaceBookmarkLinkCheckServiceImpl implements SpaceBookmarkLinkCheckService {

    /** 状态：0正常 1归档 2失效 */
    static final int STATUS_NORMAL = 0;
    static final int STATUS_ARCHIVED = 1;
    static final int STATUS_BROKEN = 2;

    private final SpaceBookmarkMapper bookmarkMapper;
    private final LinkChecker linkChecker;

    @Override
    public List<BookmarkLinkCheckVO> check(BookmarkLinkCheckRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "未获取到当前用户");
        }
        List<Long> ids = req == null || req.getBookmarkIds() == null ? List.of()
                : req.getBookmarkIds().stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, SpaceBookmark> owned = bookmarkMapper.selectList(new LambdaQueryWrapper<SpaceBookmark>()
                        .eq(SpaceBookmark::getUserId, userId)
                        .in(SpaceBookmark::getId, ids))
                .stream()
                .collect(Collectors.toMap(SpaceBookmark::getId, Function.identity()));
        List<SpaceBookmark> targets = ids.stream().map(owned::get).filter(Objects::nonNull).toList();
        List<SpaceBookmark> checkable = targets.stream().filter(b -> !isArchived(b)).toList();

        List<LinkChecker.Outcome> outcomes = linkChecker.checkAll(checkable.stream().map(SpaceBookmark::getUrl).toList());
        Map<Long, LinkChecker.Outcome> outcomeById = new HashMap<>();
        for (int i = 0; i < checkable.size(); i++) {
            outcomeById.put(checkable.get(i).getId(), outcomes.get(i));
        }

        LocalDateTime now = LocalDateTime.now();
        List<BookmarkLinkCheckVO> result = new ArrayList<>(targets.size());
        for (SpaceBookmark bookmark : targets) {
            LinkChecker.Outcome outcome = outcomeById.get(bookmark.getId());
            if (outcome == null || !save(bookmark, outcome, now)) {
                result.add(skipped(bookmark));
                continue;
            }
            int next = nextStatus(bookmark.getStatus(), outcome.verdict());
            BookmarkLinkCheckVO vo = new BookmarkLinkCheckVO();
            vo.setId(bookmark.getId());
            vo.setVerdict(outcome.verdict().name().toLowerCase(Locale.ROOT));
            vo.setReason(outcome.reason());
            vo.setStatus(next);
            vo.setChanged(next != bookmark.getStatus());
            result.add(vo);
        }
        return result;
    }

    static int nextStatus(int current, LinkChecker.Verdict verdict) {
        return switch (verdict) {
            case DEAD -> STATUS_BROKEN;
            case ALIVE -> STATUS_NORMAL;
            case UNKNOWN -> current;
        };
    }

    /**
     * 写回检查结果；检查期间书签被归档或删除了就不写，返回 false
     */
    private boolean save(SpaceBookmark bookmark, LinkChecker.Outcome outcome, LocalDateTime now) {
        LambdaUpdateWrapper<SpaceBookmark> update = new LambdaUpdateWrapper<SpaceBookmark>()
                .set(SpaceBookmark::getStatus, nextStatus(bookmark.getStatus(), outcome.verdict()))
                .set(SpaceBookmark::getCheckTime, now);
        // 已失效的这次没查清（比如超时），保留当初判它失效的原因
        if (outcome.verdict() != LinkChecker.Verdict.UNKNOWN || bookmark.getStatus() != STATUS_BROKEN) {
            update.set(SpaceBookmark::getCheckResult, outcome.reason());
        }
        // 检查不算修改书签：显式赋值让 update_time 不随 ON UPDATE 变
        update.setSql("update_time = update_time")
                .eq(SpaceBookmark::getId, bookmark.getId())
                .ne(SpaceBookmark::getStatus, STATUS_ARCHIVED);
        return bookmarkMapper.update(null, update) > 0;
    }

    private static boolean isArchived(SpaceBookmark bookmark) {
        return bookmark.getStatus() != null && bookmark.getStatus() == STATUS_ARCHIVED;
    }

    private static BookmarkLinkCheckVO skipped(SpaceBookmark bookmark) {
        BookmarkLinkCheckVO vo = new BookmarkLinkCheckVO();
        vo.setId(bookmark.getId());
        vo.setVerdict("skipped");
        vo.setReason("已归档，不检查");
        vo.setStatus(STATUS_ARCHIVED);
        vo.setChanged(false);
        return vo;
    }
}
