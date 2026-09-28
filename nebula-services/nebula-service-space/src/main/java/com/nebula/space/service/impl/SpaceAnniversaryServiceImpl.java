package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.me.AnniversarySaveRequest;
import com.nebula.space.entity.SpaceAnniversary;
import com.nebula.space.mapper.SpaceAnniversaryMapper;
import com.nebula.space.service.SpaceAnniversaryService;
import com.nebula.space.vo.me.AnniversaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 纪念日服务实现
 */
@Service
@RequiredArgsConstructor
public class SpaceAnniversaryServiceImpl implements SpaceAnniversaryService {

    private final SpaceAnniversaryMapper annivMapper;

    @Override
    public List<AnniversaryVO> list() {
        Long userId = requireUserId();
        return annivMapper.selectList(
                        new LambdaQueryWrapper<SpaceAnniversary>()
                                .eq(SpaceAnniversary::getUserId, userId)
                                .orderByAsc(SpaceAnniversary::getId)
                ).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public AnniversaryVO create(AnniversarySaveRequest req) {
        Long userId = requireUserId();
        if (req == null || !StringUtils.hasText(req.getTitle())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "名称不能为空");
        }
        if (req.getDate() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "日期不能为空");
        }
        SpaceAnniversary anniv = new SpaceAnniversary();
        anniv.setUserId(userId);
        anniv.setIcon("lucide:calendar");
        anniv.setAnnivType("countdown");
        anniv.setCalendar("solar");
        anniv.setCreateTask(0);
        anniv.setTaskTitle("");
        anniv.setNote("");
        anniv.setTag("");
        apply(anniv, req);
        annivMapper.insert(anniv);
        return toVO(anniv);
    }

    @Override
    public AnniversaryVO update(Long id, AnniversarySaveRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        SpaceAnniversary anniv = requireAnniv(id, requireUserId());
        if (req.has("title") && !StringUtils.hasText(req.getTitle())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "名称不能为空");
        }
        if (req.has("date") && req.getDate() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "日期不能为空");
        }
        apply(anniv, req);
        // 审计填充是严格模式，已有值不会覆盖，这里显式刷新
        anniv.setUpdateTime(LocalDateTime.now());
        annivMapper.updateById(anniv);
        return toVO(anniv);
    }

    @Override
    public void delete(Long id) {
        SpaceAnniversary anniv = requireAnniv(id, requireUserId());
        annivMapper.deleteById(anniv.getId());
    }

    // ----------------------------------------------------------------- 内部工具

    /**
     * 把请求里出现的字段写到实体上，再把互相矛盾的组合收拾干净（与前端表单的规则一致）
     */
    private void apply(SpaceAnniversary a, AnniversarySaveRequest req) {
        if (req.has("title")) {
            a.setTitle(req.getTitle().trim());
        }
        if (req.has("icon") && StringUtils.hasText(req.getIcon())) {
            a.setIcon(req.getIcon());
        }
        if (req.has("type") && req.getType() != null) {
            a.setAnnivType(req.getType());
        }
        if (req.has("date")) {
            a.setAnnivDate(req.getDate());
        }
        if (req.has("calendar") && req.getCalendar() != null) {
            a.setCalendar(req.getCalendar());
        }
        if (req.has("lunarMonth")) {
            a.setLunarMonth(req.getLunarMonth());
        }
        if (req.has("lunarDay")) {
            a.setLunarDay(req.getLunarDay());
        }
        if (req.has("remindDays")) {
            a.setRemindDays(req.getRemindDays());
        }
        if (req.has("createTask")) {
            a.setCreateTask(Boolean.TRUE.equals(req.getCreateTask()) ? 1 : 0);
        }
        if (req.has("taskTitle")) {
            a.setTaskTitle(req.getTaskTitle() == null ? "" : req.getTaskTitle().trim());
        }
        if (req.has("taskFor")) {
            a.setTaskFor(req.getTaskFor());
        }
        if (req.has("fileId")) {
            a.setFileId(StringUtils.hasText(req.getFileId()) ? req.getFileId() : null);
        }
        if (req.has("note")) {
            a.setNote(req.getNote() == null ? "" : req.getNote().trim());
        }
        if (req.has("tag")) {
            a.setTag(req.getTag() == null ? "" : req.getTag().trim());
        }
        // 只有每年重复的才能按农历；正数日没有「下一次」，不提醒也不生成任务
        if (!"annual".equals(a.getAnnivType())) {
            a.setCalendar("solar");
        }
        if ("lunar".equals(a.getCalendar())) {
            if (a.getLunarMonth() == null || a.getLunarDay() == null) {
                throw new BizException(HttpStatus.BAD_REQUEST, "农历纪念日要选农历月和日");
            }
        } else {
            a.setLunarMonth(null);
            a.setLunarDay(null);
        }
        if ("countup".equals(a.getAnnivType())) {
            a.setRemindDays(null);
        }
        if (a.getRemindDays() == null) {
            a.setCreateTask(0);
        }
    }

    private SpaceAnniversary requireAnniv(Long id, Long userId) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "纪念日ID不能为空");
        }
        SpaceAnniversary anniv = annivMapper.selectOne(
                new LambdaQueryWrapper<SpaceAnniversary>()
                        .eq(SpaceAnniversary::getId, id)
                        .eq(SpaceAnniversary::getUserId, userId)
        );
        if (anniv == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "纪念日不存在或已删除");
        }
        return anniv;
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "未获取到当前用户");
        }
        return userId;
    }

    private AnniversaryVO toVO(SpaceAnniversary a) {
        AnniversaryVO vo = new AnniversaryVO();
        vo.setId(a.getId());
        vo.setTitle(a.getTitle());
        vo.setIcon(a.getIcon());
        vo.setType(a.getAnnivType());
        vo.setDate(a.getAnnivDate());
        vo.setCalendar(a.getCalendar());
        vo.setLunarMonth(a.getLunarMonth());
        vo.setLunarDay(a.getLunarDay());
        vo.setRemindDays(a.getRemindDays());
        vo.setCreateTask(a.getCreateTask() != null && a.getCreateTask() == 1);
        vo.setTaskTitle(a.getTaskTitle());
        vo.setTaskFor(a.getTaskFor());
        vo.setFileId(a.getFileId());
        vo.setNote(a.getNote());
        vo.setTag(a.getTag());
        vo.setCreateTime(a.getCreateTime());
        return vo;
    }
}
