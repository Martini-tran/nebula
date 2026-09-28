package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.me.NoteQuery;
import com.nebula.space.dto.me.NoteSaveRequest;
import com.nebula.space.entity.SpaceNote;
import com.nebula.space.mapper.SpaceNoteMapper;
import com.nebula.space.service.SpaceNoteService;
import com.nebula.space.vo.me.NoteStatsVO;
import com.nebula.space.vo.me.NoteVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 随手记服务实现
 *
 * <p>寿命规则：</p>
 * <ul>
 *     <li>新笔记是临时笔记，到期日 = 今天 + 寿命；编辑正文或取消置顶重新计时（续期不会把更晚的到期日提前）</li>
 *     <li>可手动指定到期日，指定后笔记变为临时笔记</li>
 *     <li>置顶 = 长期笔记（到期日置空）；第一次加标签自动转长期</li>
 *     <li>过了到期日自动归档（不删除），在读取时顺手扫一遍，不另起定时任务；从归档恢复重新变成临时笔记</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SpaceNoteServiceImpl implements SpaceNoteService {

    /**
     * 未带偏好时的临时笔记寿命（天），与前端 NOTE_TTL_DAYS 一致
     */
    static final int DEFAULT_TTL_DAYS = 7;

    private static final String DEFAULT_COLOR = "plain";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> TAG_LIST = new TypeReference<>() {
    };

    private final SpaceNoteMapper noteMapper;

    @Override
    public List<NoteVO> list(NoteQuery query) {
        Long userId = requireUserId();
        archiveExpired(userId);

        NoteQuery q = query == null ? new NoteQuery() : query;
        String view = StringUtils.hasText(q.getView()) ? q.getView() : "all";
        LambdaQueryWrapper<SpaceNote> wrapper = new LambdaQueryWrapper<SpaceNote>()
                .eq(SpaceNote::getUserId, userId)
                .eq(SpaceNote::getArchived, "archived".equals(view) ? 1 : 0)
                .eq("temporary".equals(view), SpaceNote::getPinned, 0)
                .eq("pinned".equals(view), SpaceNote::getPinned, 1);
        if (StringUtils.hasText(q.getTag())) {
            // tags 存 JSON 数组，按带引号的整词匹配，避免「学习」命中「学习笔记」
            wrapper.like(SpaceNote::getTags, writeJson(q.getTag().trim()));
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            wrapper.and(w -> w.like(SpaceNote::getContent, kw).or().like(SpaceNote::getTags, kw));
        }
        wrapper.orderByDesc(SpaceNote::getUpdateTime).orderByDesc(SpaceNote::getId);
        return noteMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    @Override
    public NoteVO detail(Long id) {
        Long userId = requireUserId();
        archiveExpired(userId);
        return toVO(requireNote(id, userId));
    }

    @Override
    public NoteStatsVO stats() {
        Long userId = requireUserId();
        archiveExpired(userId);

        List<SpaceNote> notes = noteMapper.selectList(
                new LambdaQueryWrapper<SpaceNote>()
                        .select(SpaceNote::getId, SpaceNote::getPinned, SpaceNote::getArchived,
                                SpaceNote::getExpireDate, SpaceNote::getTags)
                        .eq(SpaceNote::getUserId, userId)
        );
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        long live = 0;
        long pinned = 0;
        long dueTomorrow = 0;
        Map<String, Long> tagCount = new LinkedHashMap<>();
        for (SpaceNote note : notes) {
            if (isTrue(note.getArchived())) {
                continue;
            }
            live++;
            if (isTrue(note.getPinned())) {
                pinned++;
            } else if (note.getExpireDate() != null && !note.getExpireDate().isAfter(tomorrow)) {
                dueTomorrow++;
            }
            readTags(note.getTags()).forEach(tag -> tagCount.merge(tag, 1L, Long::sum));
        }

        NoteStatsVO vo = new NoteStatsVO();
        vo.setAll(live);
        vo.setPinned(pinned);
        vo.setTemporary(live - pinned);
        vo.setArchived(notes.size() - live);
        vo.setDueTomorrow(dueTomorrow);
        vo.setTags(tagCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .map(e -> new NoteStatsVO.TagCount(e.getKey(), e.getValue()))
                .toList());
        return vo;
    }

    @Override
    public NoteVO create(NoteSaveRequest req) {
        Long userId = requireUserId();
        NoteSaveRequest r = req == null ? new NoteSaveRequest() : req;
        List<String> tags = normalizeTags(r.getTags());
        boolean longByDefault = r.getTtlDays() != null && r.getTtlDays() == 0;
        boolean pinned = r.getExpireDate() == null
                && (Boolean.TRUE.equals(r.getPinned()) || !tags.isEmpty() || longByDefault);

        SpaceNote note = new SpaceNote();
        note.setUserId(userId);
        note.setContent(r.getContent() == null ? "" : r.getContent());
        note.setColor(StringUtils.hasText(r.getColor()) ? r.getColor() : DEFAULT_COLOR);
        note.setPinned(pinned ? 1 : 0);
        note.setExpireDate(pinned ? null
                : r.getExpireDate() != null ? r.getExpireDate() : LocalDate.now().plusDays(ttlDays(r)));
        note.setArchived(0);
        note.setTags(writeJson(tags));
        noteMapper.insert(note);
        return toVO(note);
    }

    @Override
    public NoteVO update(Long id, NoteSaveRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        Long userId = requireUserId();
        SpaceNote note = requireNote(id, userId);
        boolean wasArchived = isTrue(note.getArchived());
        boolean hadTags = !readTags(note.getTags()).isEmpty();

        if (req.getContent() != null) {
            note.setContent(req.getContent());
        }
        if (StringUtils.hasText(req.getColor())) {
            note.setColor(req.getColor());
        }
        boolean pinned = req.getPinned() != null ? req.getPinned() : isTrue(note.getPinned());
        if (req.getTags() != null) {
            List<String> tags = normalizeTags(req.getTags());
            note.setTags(writeJson(tags));
            if (!tags.isEmpty() && !hadTags) {
                pinned = true;
            }
        }
        if (req.getArchived() != null) {
            note.setArchived(req.getArchived() ? 1 : 0);
        }

        LocalDate renewed = LocalDate.now().plusDays(ttlDays(req));
        if (Boolean.FALSE.equals(req.getArchived()) && wasArchived) {
            // 从归档恢复：重新变成临时笔记
            pinned = false;
            note.setExpireDate(renewed);
        } else if (pinned) {
            note.setExpireDate(null);
        } else if (Boolean.FALSE.equals(req.getPinned()) || req.getContent() != null) {
            // 取消置顶，或编辑了临时笔记：寿命重新计时，手动选过的更晚日期保留
            LocalDate current = note.getExpireDate();
            note.setExpireDate(current != null && current.isAfter(renewed) ? current : renewed);
        }
        if (req.getExpireDate() != null) {
            // 手动指定到期日优先，笔记随之变为临时笔记
            pinned = false;
            note.setExpireDate(req.getExpireDate());
        }
        note.setPinned(pinned ? 1 : 0);
        // 审计填充是严格模式，已有值不会覆盖，这里显式刷新
        note.setUpdateTime(LocalDateTime.now());
        noteMapper.updateById(note);
        return toVO(note);
    }

    @Override
    public void delete(Long id) {
        Long userId = requireUserId();
        SpaceNote note = requireNote(id, userId);
        noteMapper.deleteById(note.getId());
    }

    // ----------------------------------------------------------------- 内部工具

    /**
     * 到期的临时笔记转归档；保留原更新时间，归档不算一次编辑
     */
    private void archiveExpired(Long userId) {
        noteMapper.update(null, new LambdaUpdateWrapper<SpaceNote>()
                .set(SpaceNote::getArchived, 1)
                .setSql("update_time = update_time")
                .eq(SpaceNote::getUserId, userId)
                .eq(SpaceNote::getArchived, 0)
                .eq(SpaceNote::getPinned, 0)
                .lt(SpaceNote::getExpireDate, LocalDate.now()));
    }

    /**
     * 临时笔记寿命：偏好为 0（默认长期）时，取消置顶或恢复仍按默认寿命计时
     */
    private int ttlDays(NoteSaveRequest req) {
        Integer days = req.getTtlDays();
        return days == null || days <= 0 ? DEFAULT_TTL_DAYS : days;
    }

    private SpaceNote requireNote(Long id, Long userId) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "笔记ID不能为空");
        }
        SpaceNote note = noteMapper.selectOne(
                new LambdaQueryWrapper<SpaceNote>()
                        .eq(SpaceNote::getId, id)
                        .eq(SpaceNote::getUserId, userId)
        );
        if (note == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "笔记不存在或已删除");
        }
        return note;
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "未获取到当前用户");
        }
        return userId;
    }

    /**
     * 去空白、去重，保留输入顺序
     */
    private static List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        tags.stream().filter(Objects::nonNull).map(String::trim).filter(StringUtils::hasText).forEach(set::add);
        return new ArrayList<>(set);
    }

    private static List<String> readTags(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return JSON.readValue(json, TAG_LIST);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private static String writeJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(HttpStatus.BAD_REQUEST, "标签格式不正确");
        }
    }

    private static boolean isTrue(Integer flag) {
        return flag != null && flag == 1;
    }

    private NoteVO toVO(SpaceNote note) {
        NoteVO vo = new NoteVO();
        vo.setId(note.getId());
        vo.setContent(note.getContent());
        vo.setColor(note.getColor());
        vo.setPinned(isTrue(note.getPinned()));
        vo.setExpireDate(note.getExpireDate());
        vo.setArchived(isTrue(note.getArchived()));
        vo.setTags(readTags(note.getTags()));
        vo.setCreateTime(note.getCreateTime());
        vo.setUpdateTime(note.getUpdateTime());
        return vo;
    }
}
