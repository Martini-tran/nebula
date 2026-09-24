package com.nebula.scribe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.exception.BizException;
import com.nebula.scribe.dto.ChapterCreateRequest;
import com.nebula.scribe.dto.ChapterSaveRequest;
import com.nebula.scribe.entity.ScribeChapter;
import com.nebula.scribe.entity.ScribeVolume;
import com.nebula.scribe.entity.ScribeWork;
import com.nebula.scribe.enums.ChapterStatus;
import com.nebula.scribe.mapper.ScribeChapterMapper;
import com.nebula.scribe.mapper.ScribeVolumeMapper;
import com.nebula.scribe.mapper.ScribeWorkMapper;
import com.nebula.scribe.service.ScribeChapterService;
import com.nebula.scribe.service.ScribeWorkGuard;
import com.nebula.scribe.util.WordCounter;
import com.nebula.scribe.vo.ChapterDetailVO;
import com.nebula.scribe.vo.ChapterListVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 章节服务实现
 *
 * <p>章节不冗余 user_id：每个操作先按「作品 id + 当前用户」取作品，再把章节限定在该作品下。
 * 作品上的章节数、累计字数随章节增删改在同一事务里增量维护。
 * 目录结构（卷的增删、整棵树重排）在 {@link ScribeVolumeServiceImpl}。</p>
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
    private final ScribeVolumeMapper volumeMapper;
    private final ScribeWorkMapper workMapper;
    private final ScribeWorkGuard workGuard;

    @Override
    public List<ChapterListVO> list(Long workId) {
        ScribeWork work = workGuard.requireOwnedWork(workId);
        // 目录不需要正文，longtext 不查出来
        List<ScribeChapter> rows = chapterMapper.selectList(new LambdaQueryWrapper<ScribeChapter>()
                .select(ScribeChapter.class, f -> !"content".equals(f.getColumn()))
                .eq(ScribeChapter::getWorkId, work.getId())
                .orderByAsc(ScribeChapter::getSortOrder)
                .orderByAsc(ScribeChapter::getId));
        return rows.stream().map(ScribeConverters::toChapterListVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChapterDetailVO create(Long workId, ChapterCreateRequest req) {
        ScribeWork work = workGuard.requireOwnedWork(workId);
        Long volumeId = resolveVolume(work.getId(), req == null ? null : req.getVolumeId());
        // 排序值只在卷内有意义：取同一卷（无卷时即整部作品）的末尾
        ScribeChapter last = chapterMapper.selectOne(new LambdaQueryWrapper<ScribeChapter>()
                .select(ScribeChapter::getId, ScribeChapter::getSortOrder)
                .eq(ScribeChapter::getWorkId, work.getId())
                .eq(volumeId != null, ScribeChapter::getVolumeId, volumeId)
                .isNull(volumeId == null, ScribeChapter::getVolumeId)
                .orderByDesc(ScribeChapter::getSortOrder)
                .last("LIMIT 1"));

        ScribeChapter chapter = new ScribeChapter();
        chapter.setWorkId(work.getId());
        chapter.setVolumeId(volumeId);
        String title = req == null ? null : req.getTitle();
        // 网文章号跨卷连续，所以按全书章节数命名
        chapter.setTitle(StringUtils.hasText(title) ? title.trim() : "第" + (safe(work.getChapterCount()) + 1) + "章");
        chapter.setSortOrder(last == null ? SORT_STEP : safe(last.getSortOrder()) + SORT_STEP);
        chapter.setStatus(ChapterStatus.OUTLINE.getCode());
        chapter.setContent("");
        chapter.setWordCount(0);
        chapter.setRevision(0L);
        chapterMapper.insert(chapter);

        adjustWorkCounters(work.getId(), 1, 0);
        log.info("章节已创建, chapterId={}, workId={}, volumeId={}", chapter.getId(), work.getId(), volumeId);
        return ScribeConverters.toChapterDetailVO(chapter);
    }

    @Override
    public ChapterDetailVO detail(Long workId, Long chapterId) {
        return ScribeConverters.toChapterDetailVO(requireOwnedChapter(workId, chapterId));
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
        return ScribeConverters.toChapterDetailVO(chapterMapper.selectById(current.getId()));
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
     * 确定新章节所属的卷，守住「有卷则每章必属某卷，无卷则全部为空」的不变式：
     * 有卷时不指定就放最后一卷，指定了必须是本作品的卷；无卷时不能指定。
     */
    private Long resolveVolume(Long workId, Long requested) {
        if (requested != null) {
            ScribeVolume volume = volumeMapper.selectOne(new LambdaQueryWrapper<ScribeVolume>()
                    .eq(ScribeVolume::getId, requested)
                    .eq(ScribeVolume::getWorkId, workId));
            if (volume == null) {
                throw new BizException(HttpStatus.NOT_FOUND, "卷不存在");
            }
            return volume.getId();
        }
        ScribeVolume lastVolume = volumeMapper.selectOne(new LambdaQueryWrapper<ScribeVolume>()
                .select(ScribeVolume::getId)
                .eq(ScribeVolume::getWorkId, workId)
                .orderByDesc(ScribeVolume::getSortOrder)
                .orderByDesc(ScribeVolume::getId)
                .last("LIMIT 1"));
        return lastVolume == null ? null : lastVolume.getId();
    }

    private ScribeChapter requireOwnedChapter(Long workId, Long chapterId) {
        ScribeWork work = workGuard.requireOwnedWork(workId);
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

}
