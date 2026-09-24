package com.nebula.scribe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.exception.BizException;
import com.nebula.scribe.dto.TocSortRequest;
import com.nebula.scribe.dto.VolumeSaveRequest;
import com.nebula.scribe.entity.ScribeChapter;
import com.nebula.scribe.entity.ScribeVolume;
import com.nebula.scribe.entity.ScribeWork;
import com.nebula.scribe.mapper.ScribeChapterMapper;
import com.nebula.scribe.mapper.ScribeVolumeMapper;
import com.nebula.scribe.service.ScribeVolumeService;
import com.nebula.scribe.service.ScribeWorkGuard;
import com.nebula.scribe.vo.TocVO;
import com.nebula.scribe.vo.VolumeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 卷与目录结构服务实现
 *
 * <p>不变式：作品要么没有卷（章节 volume_id 全为空），要么每一章都属于某一卷。
 * 建第一卷时收编、删卷时并入相邻卷、重排时校验整棵树，都是为了守住它。</p>
 *
 * <p>收编、并卷、重排只是调整目录结构，不算「编辑」：这些更新显式保留章节的 update_time，
 * 否则写作台「打开最近写过的一章」会被一次重排打乱。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScribeVolumeServiceImpl implements ScribeVolumeService {

    private static final int SORT_STEP = ScribeChapterServiceImpl.SORT_STEP;

    private final ScribeVolumeMapper volumeMapper;
    private final ScribeChapterMapper chapterMapper;
    private final ScribeWorkGuard workGuard;

    @Override
    public List<VolumeVO> list(Long workId) {
        ScribeWork work = workGuard.requireOwnedWork(workId);
        return listVolumes(work.getId()).stream().map(ScribeConverters::toVolumeVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VolumeVO create(Long workId, VolumeSaveRequest req) {
        ScribeWork work = workGuard.requireOwnedWork(workId);
        List<ScribeVolume> existing = listVolumes(work.getId());

        ScribeVolume volume = new ScribeVolume();
        volume.setWorkId(work.getId());
        String title = req == null ? null : req.getTitle();
        volume.setTitle(StringUtils.hasText(title) ? title.trim() : "第" + (existing.size() + 1) + "卷");
        volume.setSynopsis(req == null ? null : trimToNull(req.getSynopsis()));
        volume.setSortOrder(existing.isEmpty() ? SORT_STEP : safe(existing.getLast().getSortOrder()) + SORT_STEP);
        volumeMapper.insert(volume);

        if (existing.isEmpty()) {
            // 第一卷：现有章节整体划入，原排序值本就是作品内全序，直接沿用
            int adopted = chapterMapper.update(null, keepUpdateTime(new LambdaUpdateWrapper<ScribeChapter>()
                    .eq(ScribeChapter::getWorkId, work.getId())
                    .isNull(ScribeChapter::getVolumeId)
                    .set(ScribeChapter::getVolumeId, volume.getId())));
            log.info("首卷已创建并收编章节, volumeId={}, workId={}, chapters={}", volume.getId(), work.getId(), adopted);
        } else {
            log.info("卷已创建, volumeId={}, workId={}", volume.getId(), work.getId());
        }
        return ScribeConverters.toVolumeVO(volume);
    }

    @Override
    public VolumeVO update(Long workId, Long volumeId, VolumeSaveRequest req) {
        if (req == null || !StringUtils.hasText(req.getTitle())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "卷名不能为空");
        }
        ScribeVolume current = requireOwnedVolume(workId, volumeId);
        // 用 set 显式写列：updateById 默认跳过 null，清空梗概会静默失效
        volumeMapper.update(new ScribeVolume(), new LambdaUpdateWrapper<ScribeVolume>()
                .eq(ScribeVolume::getId, current.getId())
                .eq(ScribeVolume::getWorkId, current.getWorkId())
                .set(ScribeVolume::getTitle, req.getTitle().trim())
                .set(ScribeVolume::getSynopsis, trimToNull(req.getSynopsis())));
        return ScribeConverters.toVolumeVO(volumeMapper.selectById(current.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TocVO delete(Long workId, Long volumeId) {
        ScribeVolume current = requireOwnedVolume(workId, volumeId);
        List<ScribeVolume> volumes = listVolumes(current.getWorkId());
        int index = indexOf(volumes, current.getId());
        List<ScribeChapter> moving = listChapters(current.getWorkId(), current.getId());

        if (volumes.size() == 1) {
            // 删唯一一卷：回到无卷状态，章节顺序原样保留
            for (ScribeChapter c : moving) {
                moveChapter(c, null, c.getSortOrder());
            }
        } else if (index > 0) {
            // 并入上一卷末尾
            ScribeVolume target = volumes.get(index - 1);
            List<ScribeChapter> merged = new ArrayList<>(listChapters(current.getWorkId(), target.getId()));
            merged.addAll(moving);
            renumber(merged, target.getId());
        } else {
            // 删的是第一卷：并入下一卷开头
            ScribeVolume target = volumes.get(1);
            List<ScribeChapter> merged = new ArrayList<>(moving);
            merged.addAll(listChapters(current.getWorkId(), target.getId()));
            renumber(merged, target.getId());
        }

        ScribeVolume patch = new ScribeVolume();
        patch.setId(current.getId());
        patch.setDeleteTime(LocalDateTime.now());
        volumeMapper.updateById(patch);
        volumeMapper.deleteById(current.getId());
        log.info("卷已删除, volumeId={}, workId={}, movedChapters={}", current.getId(), current.getWorkId(), moving.size());
        return loadToc(current.getWorkId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TocVO sort(Long workId, TocSortRequest req) {
        ScribeWork work = workGuard.requireOwnedWork(workId);
        List<ScribeVolume> volumes = listVolumes(work.getId());
        List<ScribeChapter> chapters = listChapters(work.getId(), null);
        Map<Long, ScribeChapter> chapterById = chapters.stream()
                .collect(Collectors.toMap(ScribeChapter::getId, Function.identity()));
        List<TocSortRequest.VolumeNode> nodes = req == null || req.getVolumes() == null ? List.of() : req.getVolumes();
        List<Long> looseIds = req == null || req.getChapterIds() == null ? List.of() : req.getChapterIds();

        if (volumes.isEmpty()) {
            if (!nodes.isEmpty() || !sameMembers(looseIds, chapterById.keySet())) {
                throw tocChanged();
            }
            for (int i = 0; i < looseIds.size(); i++) {
                moveChapter(chapterById.get(looseIds.get(i)), null, (i + 1) * SORT_STEP);
            }
        } else {
            List<Long> volumeIds = nodes.stream().map(TocSortRequest.VolumeNode::getId).toList();
            List<Long> allChapterIds = nodes.stream()
                    .flatMap(n -> (n.getChapterIds() == null ? List.<Long>of() : n.getChapterIds()).stream())
                    .toList();
            Set<Long> existingVolumeIds = volumes.stream().map(ScribeVolume::getId).collect(Collectors.toSet());
            // 必须恰好是本作品的全部卷与章节：少传会让漏掉的项顺序错乱，多传可能夹带别人的数据
            if (!looseIds.isEmpty() || !sameMembers(volumeIds, existingVolumeIds)
                    || !sameMembers(allChapterIds, chapterById.keySet())) {
                throw tocChanged();
            }
            Map<Long, ScribeVolume> volumeById = volumes.stream()
                    .collect(Collectors.toMap(ScribeVolume::getId, Function.identity()));
            for (int v = 0; v < nodes.size(); v++) {
                TocSortRequest.VolumeNode node = nodes.get(v);
                ScribeVolume volume = volumeById.get(node.getId());
                int volumeSort = (v + 1) * SORT_STEP;
                if (!Objects.equals(volume.getSortOrder(), volumeSort)) {
                    volumeMapper.update(null, new LambdaUpdateWrapper<ScribeVolume>()
                            .eq(ScribeVolume::getId, volume.getId())
                            .set(ScribeVolume::getSortOrder, volumeSort));
                }
                List<Long> ids = node.getChapterIds() == null ? List.of() : node.getChapterIds();
                for (int i = 0; i < ids.size(); i++) {
                    moveChapter(chapterById.get(ids.get(i)), volume.getId(), (i + 1) * SORT_STEP);
                }
            }
        }
        log.info("目录已重排, workId={}, volumes={}, chapters={}", work.getId(), volumes.size(), chapters.size());
        return loadToc(work.getId());
    }

    // ----------------------------------------------------------------- 内部工具

    private ScribeVolume requireOwnedVolume(Long workId, Long volumeId) {
        ScribeWork work = workGuard.requireOwnedWork(workId);
        ScribeVolume volume = volumeId == null ? null : volumeMapper.selectOne(new LambdaQueryWrapper<ScribeVolume>()
                .eq(ScribeVolume::getId, volumeId)
                .eq(ScribeVolume::getWorkId, work.getId()));
        if (volume == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "卷不存在");
        }
        return volume;
    }

    private List<ScribeVolume> listVolumes(Long workId) {
        return volumeMapper.selectList(new LambdaQueryWrapper<ScribeVolume>()
                .eq(ScribeVolume::getWorkId, workId)
                .orderByAsc(ScribeVolume::getSortOrder)
                .orderByAsc(ScribeVolume::getId));
    }

    /**
     * 章节目录（不含正文）；volumeId 为空表示整部作品
     */
    private List<ScribeChapter> listChapters(Long workId, Long volumeId) {
        return chapterMapper.selectList(new LambdaQueryWrapper<ScribeChapter>()
                .select(ScribeChapter.class, f -> !"content".equals(f.getColumn()))
                .eq(ScribeChapter::getWorkId, workId)
                .eq(volumeId != null, ScribeChapter::getVolumeId, volumeId)
                .orderByAsc(ScribeChapter::getSortOrder)
                .orderByAsc(ScribeChapter::getId));
    }

    private TocVO loadToc(Long workId) {
        return new TocVO(
                listVolumes(workId).stream().map(ScribeConverters::toVolumeVO).toList(),
                listChapters(workId, null).stream().map(ScribeConverters::toChapterListVO).toList());
    }

    /**
     * 把一串章节按顺序归到目标卷，排序值重新按间隔铺开
     */
    private void renumber(List<ScribeChapter> ordered, Long volumeId) {
        for (int i = 0; i < ordered.size(); i++) {
            moveChapter(ordered.get(i), volumeId, (i + 1) * SORT_STEP);
        }
    }

    /**
     * 调整章节的卷与排序；值没变就不写，减少重排时的无效更新
     */
    private void moveChapter(ScribeChapter chapter, Long volumeId, Integer sortOrder) {
        if (Objects.equals(chapter.getVolumeId(), volumeId) && Objects.equals(chapter.getSortOrder(), sortOrder)) {
            return;
        }
        chapterMapper.update(null, keepUpdateTime(new LambdaUpdateWrapper<ScribeChapter>()
                .eq(ScribeChapter::getId, chapter.getId())
                .set(ScribeChapter::getVolumeId, volumeId)
                .set(ScribeChapter::getSortOrder, sortOrder)));
    }

    /**
     * 显式把 update_time 赋回原值，MySQL 就不会触发 ON UPDATE CURRENT_TIMESTAMP
     */
    private static LambdaUpdateWrapper<ScribeChapter> keepUpdateTime(LambdaUpdateWrapper<ScribeChapter> wrapper) {
        return wrapper.setSql("update_time = update_time");
    }

    private static boolean sameMembers(List<Long> ids, Set<Long> expected) {
        if (ids.stream().anyMatch(Objects::isNull)) {
            return false;
        }
        Set<Long> unique = new HashSet<>(ids);
        return unique.size() == ids.size() && unique.equals(expected);
    }

    private static int indexOf(List<ScribeVolume> volumes, Long id) {
        for (int i = 0; i < volumes.size(); i++) {
            if (volumes.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static BizException tocChanged() {
        return new BizException(HttpStatus.CONFLICT, "目录已变化，请刷新后再调整");
    }

    private static int safe(Integer value) {
        return value == null ? 0 : value;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
