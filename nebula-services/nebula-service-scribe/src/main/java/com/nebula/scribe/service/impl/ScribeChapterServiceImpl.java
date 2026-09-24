package com.nebula.scribe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.scribe.dto.ChapterCreateRequest;
import com.nebula.scribe.dto.ChapterSaveRequest;
import com.nebula.scribe.dto.ChapterSortRequest;
import com.nebula.scribe.entity.ScribeChapter;
import com.nebula.scribe.entity.ScribeWork;
import com.nebula.scribe.enums.ChapterStatus;
import com.nebula.scribe.mapper.ScribeChapterMapper;
import com.nebula.scribe.mapper.ScribeWorkMapper;
import com.nebula.scribe.service.ScribeChapterService;
import com.nebula.scribe.util.WordCounter;
import com.nebula.scribe.vo.ChapterDetailVO;
import com.nebula.scribe.vo.ChapterListVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 章节服务实现
 *
 * <p>章节不冗余 user_id：每个操作先按「作品 id + 当前用户」取作品，再把章节限定在该作品下。
 * 作品上的章节数、累计字数随章节增删改在同一事务里增量维护。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScribeChapterServiceImpl implements ScribeChapterService {

    /**
     * 排序值间隔：中间插入时取相邻两值的中点，不必整表重排
     */
    static final int SORT_STEP = 1000;

    private final ScribeChapterMapper chapterMapper;
    private final ScribeWorkMapper workMapper;

    @Override
    public List<ChapterListVO> list(Long workId) {
        ScribeWork work = requireOwnedWork(workId);
        // 目录不需要正文，longtext 不查出来
        List<ScribeChapter> rows = chapterMapper.selectList(new LambdaQueryWrapper<ScribeChapter>()
                .select(ScribeChapter.class, f -> !"content".equals(f.getColumn()))
                .eq(ScribeChapter::getWorkId, work.getId())
                .orderByAsc(ScribeChapter::getSortOrder)
                .orderByAsc(ScribeChapter::getId));
        return rows.stream().map(c -> fillListFields(c, new ChapterListVO())).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChapterDetailVO create(Long workId, ChapterCreateRequest req) {
        ScribeWork work = requireOwnedWork(workId);
        ScribeChapter last = chapterMapper.selectOne(new LambdaQueryWrapper<ScribeChapter>()
                .select(ScribeChapter::getId, ScribeChapter::getSortOrder)
                .eq(ScribeChapter::getWorkId, work.getId())
                .orderByDesc(ScribeChapter::getSortOrder)
                .last("LIMIT 1"));

        ScribeChapter chapter = new ScribeChapter();
        chapter.setWorkId(work.getId());
        String title = req == null ? null : req.getTitle();
        chapter.setTitle(StringUtils.hasText(title) ? title.trim() : "第" + (safe(work.getChapterCount()) + 1) + "章");
        chapter.setSortOrder(last == null ? SORT_STEP : safe(last.getSortOrder()) + SORT_STEP);
        chapter.setStatus(ChapterStatus.OUTLINE.getCode());
        chapter.setContent("");
        chapter.setWordCount(0);
        chapter.setRevision(0L);
        chapterMapper.insert(chapter);

        adjustWorkCounters(work.getId(), 1, 0);
        log.info("章节已创建, chapterId={}, workId={}", chapter.getId(), work.getId());
        return toDetailVO(chapter);
    }

    @Override
    public ChapterDetailVO detail(Long workId, Long chapterId) {
        return toDetailVO(requireOwnedChapter(workId, chapterId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChapterDetailVO save(Long workId, Long chapterId, ChapterSaveRequest req) {
        if (req == null || req.getRevision() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "修订号不能为空");
        }
        if (req.getTitle() != null && !StringUtils.hasText(req.getTitle())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "章节标题不能为空");
        }
        if (req.getStatus() != null && !ChapterStatus.isValid(req.getStatus())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "章节状态取值不合法");
        }
        ScribeChapter current = requireOwnedChapter(workId, chapterId);
        if (!Objects.equals(current.getRevision(), req.getRevision())) {
            throw conflict();
        }

        boolean contentChanged = req.getContent() != null;
        int words = contentChanged ? WordCounter.count(req.getContent()) : safe(current.getWordCount());
        String status = req.getStatus();
        // 大纲状态下第一次写进正文，自动进入草稿，省得作者手动切
        if (status == null && contentChanged && words > 0
                && ChapterStatus.OUTLINE.getCode().equals(current.getStatus())) {
            status = ChapterStatus.DRAFTING.getCode();
        }

        LambdaUpdateWrapper<ScribeChapter> wrapper = new LambdaUpdateWrapper<ScribeChapter>()
                .eq(ScribeChapter::getId, current.getId())
                .eq(ScribeChapter::getWorkId, current.getWorkId())
                // 修订号比对放进 where：两处同时保存时只有一个能成功
                .eq(ScribeChapter::getRevision, req.getRevision())
                .set(req.getTitle() != null, ScribeChapter::getTitle, trim(req.getTitle()))
                .set(req.getSynopsis() != null, ScribeChapter::getSynopsis, trimToNull(req.getSynopsis()))
                .set(status != null, ScribeChapter::getStatus, status)
                .set(contentChanged, ScribeChapter::getContent, req.getContent())
                .set(contentChanged, ScribeChapter::getWordCount, words)
                .setSql("revision = revision + 1");
        // 传空实体以触发 update_by/update_time 自动填充
        if (chapterMapper.update(new ScribeChapter(), wrapper) == 0) {
            throw conflict();
        }

        int delta = words - safe(current.getWordCount());
        adjustWorkCounters(current.getWorkId(), 0, delta);
        return toDetailVO(chapterMapper.selectById(current.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ChapterListVO> sort(Long workId, ChapterSortRequest req) {
        ScribeWork work = requireOwnedWork(workId);
        List<Long> ids = req == null ? null : req.getIds();
        if (ids == null || ids.isEmpty()) {
            throw new BizException(HttpStatus.BAD_REQUEST, "章节顺序不能为空");
        }
        Set<Long> existing = new HashSet<>(chapterMapper.selectList(new LambdaQueryWrapper<ScribeChapter>()
                        .select(ScribeChapter::getId)
                        .eq(ScribeChapter::getWorkId, work.getId()))
                .stream().map(ScribeChapter::getId).toList());
        // 必须恰好是本作品的全部章节：少传会让漏掉的章节排序错乱，多传可能夹带别人的章节
        if (ids.size() != existing.size() || !existing.equals(new HashSet<>(ids))) {
            throw new BizException(HttpStatus.CONFLICT, "章节列表已变化，请刷新后再排序");
        }

        for (int i = 0; i < ids.size(); i++) {
            chapterMapper.update(new ScribeChapter(), new LambdaUpdateWrapper<ScribeChapter>()
                    .eq(ScribeChapter::getId, ids.get(i))
                    .eq(ScribeChapter::getWorkId, work.getId())
                    .set(ScribeChapter::getSortOrder, (i + 1) * SORT_STEP));
        }
        log.info("章节已重排, workId={}, count={}", work.getId(), ids.size());
        return list(work.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long workId, Long chapterId) {
        ScribeChapter current = requireOwnedChapter(workId, chapterId);
        ScribeChapter patch = new ScribeChapter();
        patch.setId(current.getId());
        patch.setDeleteTime(LocalDateTime.now());
        chapterMapper.updateById(patch);
        chapterMapper.deleteById(current.getId());

        adjustWorkCounters(current.getWorkId(), -1, -safe(current.getWordCount()));
        log.info("章节已移入回收站, chapterId={}, workId={}", current.getId(), current.getWorkId());
    }

    // ----------------------------------------------------------------- 内部工具

    /**
     * 按「id + 当前用户」取作品；别人的作品与不存在一律 404
     */
    private ScribeWork requireOwnedWork(Long workId) {
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

    private ScribeChapter requireOwnedChapter(Long workId, Long chapterId) {
        ScribeWork work = requireOwnedWork(workId);
        ScribeChapter chapter = chapterId == null ? null : chapterMapper.selectOne(new LambdaQueryWrapper<ScribeChapter>()
                .eq(ScribeChapter::getId, chapterId)
                .eq(ScribeChapter::getWorkId, work.getId()));
        if (chapter == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "章节不存在");
        }
        return chapter;
    }

    /**
     * 增量维护作品的章节数与累计字数；GREATEST 兜底，避免历史数据不一致时减成负数。
     * 同时刷新作品的 update_time，让「最近编辑」排序反映写作动态。
     */
    private void adjustWorkCounters(Long workId, int chapterDelta, int wordDelta) {
        if (chapterDelta == 0 && wordDelta == 0) {
            return;
        }
        LambdaUpdateWrapper<ScribeWork> wrapper = new LambdaUpdateWrapper<ScribeWork>().eq(ScribeWork::getId, workId);
        if (chapterDelta != 0) {
            wrapper.setSql("chapter_count = GREATEST(chapter_count + {0}, 0)", chapterDelta);
        }
        if (wordDelta != 0) {
            wrapper.setSql("word_count = GREATEST(word_count + {0}, 0)", wordDelta);
        }
        workMapper.update(new ScribeWork(), wrapper);
    }

    private static BizException conflict() {
        return new BizException(HttpStatus.CONFLICT, "此章已在别处修改，请刷新后再保存");
    }

    private static int safe(Integer value) {
        return value == null ? 0 : value;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private ChapterDetailVO toDetailVO(ScribeChapter chapter) {
        ChapterDetailVO vo = fillListFields(chapter, new ChapterDetailVO());
        vo.setContent(chapter.getContent() == null ? "" : chapter.getContent());
        vo.setRevision(chapter.getRevision());
        return vo;
    }

    private <T extends ChapterListVO> T fillListFields(ScribeChapter chapter, T vo) {
        vo.setId(chapter.getId());
        vo.setWorkId(chapter.getWorkId());
        vo.setVolumeId(chapter.getVolumeId());
        vo.setTitle(chapter.getTitle());
        vo.setSortOrder(chapter.getSortOrder());
        vo.setStatus(chapter.getStatus());
        vo.setWordCount(chapter.getWordCount());
        vo.setSynopsis(chapter.getSynopsis());
        vo.setUpdateTime(chapter.getUpdateTime());
        return vo;
    }
}
