package com.nebula.scribe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.exception.BizException;
import com.nebula.scribe.dto.LoreSaveRequest;
import com.nebula.scribe.entity.ScribeLoreEntry;
import com.nebula.scribe.entity.ScribeWork;
import com.nebula.scribe.enums.LoreKind;
import com.nebula.scribe.mapper.ScribeLoreEntryMapper;
import com.nebula.scribe.service.ScribeLoreService;
import com.nebula.scribe.service.ScribeWorkGuard;
import com.nebula.scribe.util.JsonLists;
import com.nebula.scribe.vo.LoreEntryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设定库服务实现
 *
 * <p>名称唯一只在服务层校验：软删除的行还占着名字，唯一索引会让回收站里的条目挡住新建。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScribeLoreServiceImpl implements ScribeLoreService {

    private final ScribeLoreEntryMapper loreMapper;
    private final ScribeWorkGuard workGuard;

    @Override
    public List<LoreEntryVO> list(Long workId, String kind) {
        ScribeWork work = workGuard.requireOwnedWork(workId);
        // 非法类型按「不筛选」处理，与前端「全部」选项的空串语义一致
        String safeKind = LoreKind.isValid(kind) ? kind : null;
        List<ScribeLoreEntry> entries = loreMapper.selectList(new LambdaQueryWrapper<ScribeLoreEntry>()
                // 详细设定可能很长，列表不取
                .select(ScribeLoreEntry.class, f -> !"detail".equals(f.getColumn()))
                .eq(ScribeLoreEntry::getWorkId, work.getId())
                .eq(safeKind != null, ScribeLoreEntry::getKind, safeKind)
                .orderByDesc(ScribeLoreEntry::getPinned)
                .orderByDesc(ScribeLoreEntry::getUpdateTime)
                .orderByDesc(ScribeLoreEntry::getId));
        return entries.stream().map(this::toVO).toList();
    }

    @Override
    public LoreEntryVO detail(Long workId, Long entryId) {
        return toVO(requireOwnedEntry(workId, entryId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoreEntryVO create(Long workId, LoreSaveRequest req) {
        validateForm(req);
        ScribeWork work = workGuard.requireOwnedWork(workId);
        String name = req.getName().trim();
        ensureNameFree(work.getId(), name, null);

        ScribeLoreEntry entry = new ScribeLoreEntry();
        entry.setWorkId(work.getId());
        entry.setKind(req.getKind());
        entry.setName(name);
        entry.setAliases(writeAliases(req.getAliases(), name));
        entry.setSummary(trimToNull(req.getSummary()));
        entry.setDetail(trimToNull(req.getDetail()));
        entry.setTags(JsonLists.write(req.getTags()));
        entry.setPinned(Boolean.TRUE.equals(req.getPinned()));
        loreMapper.insert(entry);
        log.info("设定已创建, entryId={}, workId={}, kind={}", entry.getId(), work.getId(), entry.getKind());
        return toVO(entry);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoreEntryVO update(Long workId, Long entryId, LoreSaveRequest req) {
        validateForm(req);
        ScribeLoreEntry current = requireOwnedEntry(workId, entryId);
        String name = req.getName().trim();
        ensureNameFree(current.getWorkId(), name, current.getId());

        // 用 set 显式写列：updateById 默认跳过 null，清空可选字段会静默失效
        LambdaUpdateWrapper<ScribeLoreEntry> wrapper = new LambdaUpdateWrapper<ScribeLoreEntry>()
                .eq(ScribeLoreEntry::getId, current.getId())
                .eq(ScribeLoreEntry::getWorkId, current.getWorkId())
                .set(ScribeLoreEntry::getKind, req.getKind())
                .set(ScribeLoreEntry::getName, name)
                .set(ScribeLoreEntry::getAliases, writeAliases(req.getAliases(), name))
                .set(ScribeLoreEntry::getSummary, trimToNull(req.getSummary()))
                .set(ScribeLoreEntry::getDetail, trimToNull(req.getDetail()))
                .set(ScribeLoreEntry::getTags, JsonLists.write(req.getTags()))
                .set(ScribeLoreEntry::getPinned, Boolean.TRUE.equals(req.getPinned()));
        // 传空实体以触发 update_by/update_time 自动填充
        loreMapper.update(new ScribeLoreEntry(), wrapper);
        log.info("设定已修改, entryId={}, workId={}", current.getId(), current.getWorkId());
        return toVO(loreMapper.selectById(current.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long workId, Long entryId) {
        ScribeLoreEntry current = requireOwnedEntry(workId, entryId);
        ScribeLoreEntry patch = new ScribeLoreEntry();
        patch.setId(current.getId());
        patch.setDeleteTime(LocalDateTime.now());
        loreMapper.updateById(patch);
        loreMapper.deleteById(current.getId());
        log.info("设定已移入回收站, entryId={}, workId={}", current.getId(), current.getWorkId());
    }

    // ----------------------------------------------------------------- 内部工具

    private void validateForm(LoreSaveRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        if (!LoreKind.isValid(req.getKind())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "设定类型取值不合法");
        }
        if (!StringUtils.hasText(req.getName())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "设定名称不能为空");
        }
    }

    /**
     * 同一作品内名称不能重复，不分类型；回收站里的条目不占名字
     */
    private void ensureNameFree(Long workId, String name, Long selfId) {
        Long taken = loreMapper.selectCount(new LambdaQueryWrapper<ScribeLoreEntry>()
                .eq(ScribeLoreEntry::getWorkId, workId)
                .eq(ScribeLoreEntry::getName, name)
                .ne(selfId != null, ScribeLoreEntry::getId, selfId));
        if (taken != null && taken > 0) {
            throw new BizException(HttpStatus.CONFLICT, "本作品已有名为「" + name + "」的设定");
        }
    }

    /**
     * 先过作品归属，再按 work_id 取条目；别人的与不存在一律 404
     */
    private ScribeLoreEntry requireOwnedEntry(Long workId, Long entryId) {
        ScribeWork work = workGuard.requireOwnedWork(workId);
        ScribeLoreEntry entry = entryId == null ? null : loreMapper.selectOne(new LambdaQueryWrapper<ScribeLoreEntry>()
                .eq(ScribeLoreEntry::getId, entryId)
                .eq(ScribeLoreEntry::getWorkId, work.getId()));
        if (entry == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "设定条目不存在");
        }
        return entry;
    }

    /**
     * 别名与名称相同没有意义，顺手去掉
     */
    private static String writeAliases(List<String> aliases, String name) {
        return JsonLists.write(JsonLists.clean(aliases).stream().filter(a -> !a.equals(name)).toList());
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private LoreEntryVO toVO(ScribeLoreEntry entry) {
        LoreEntryVO vo = new LoreEntryVO();
        vo.setId(entry.getId());
        vo.setWorkId(entry.getWorkId());
        vo.setKind(entry.getKind());
        vo.setName(entry.getName());
        vo.setAliases(JsonLists.read(entry.getAliases()));
        vo.setSummary(entry.getSummary());
        vo.setDetail(entry.getDetail());
        vo.setTags(JsonLists.read(entry.getTags()));
        vo.setPinned(Boolean.TRUE.equals(entry.getPinned()));
        vo.setUpdateTime(entry.getUpdateTime());
        return vo;
    }
}
