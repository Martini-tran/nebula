package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.me.ReportQuery;
import com.nebula.space.dto.me.ReportSaveRequest;
import com.nebula.space.entity.SpaceReport;
import com.nebula.space.mapper.SpaceReportMapper;
import com.nebula.space.service.SpaceReportService;
import com.nebula.space.vo.me.ReportVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 日报周报服务实现
 */
@Service
@RequiredArgsConstructor
public class SpaceReportServiceImpl implements SpaceReportService {

    private static final Set<String> TYPES = Set.of("day", "week");

    private final SpaceReportMapper reportMapper;

    @Override
    public List<ReportVO> list(ReportQuery query) {
        Long userId = requireUserId();
        ReportQuery q = query == null ? new ReportQuery() : query;
        if (StringUtils.hasText(q.getType())) {
            checkType(q.getType());
        }
        return reportMapper.selectList(
                        new LambdaQueryWrapper<SpaceReport>()
                                .eq(SpaceReport::getUserId, userId)
                                .eq(StringUtils.hasText(q.getType()), SpaceReport::getReportType, q.getType())
                                .ge(q.getFrom() != null, SpaceReport::getPeriodStart, q.getFrom())
                                .le(q.getTo() != null, SpaceReport::getPeriodStart, q.getTo())
                                .orderByDesc(SpaceReport::getPeriodStart)
                                .orderByAsc(SpaceReport::getReportType)
                ).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public ReportVO get(String type, LocalDate date) {
        checkType(type);
        SpaceReport report = find(requireUserId(), type, requireDate(date));
        return report == null ? null : toVO(report);
    }

    @Override
    public ReportVO save(String type, LocalDate date, ReportSaveRequest req) {
        checkType(type);
        requireDate(date);
        Long userId = requireUserId();
        String content = req == null || req.getContent() == null ? "" : req.getContent();
        SpaceReport existing = find(userId, type, date);
        if (!StringUtils.hasText(content)) {
            if (existing != null) {
                reportMapper.deleteById(existing.getId());
            }
            return null;
        }
        if (existing == null) {
            SpaceReport report = new SpaceReport();
            report.setUserId(userId);
            report.setReportType(type);
            report.setPeriodStart(date);
            report.setContent(content);
            try {
                reportMapper.insert(report);
                return toVO(report);
            } catch (DuplicateKeyException e) {
                // 两个标签页同时第一次保存：另一份已插入，改为覆盖它
                existing = find(userId, type, date);
                if (existing == null) {
                    throw e;
                }
            }
        }
        existing.setContent(content);
        // 审计填充是严格模式，已有值不会覆盖，这里显式刷新
        existing.setUpdateTime(LocalDateTime.now());
        reportMapper.updateById(existing);
        return toVO(existing);
    }

    // ----------------------------------------------------------------- 内部工具

    private SpaceReport find(Long userId, String type, LocalDate date) {
        return reportMapper.selectOne(
                new LambdaQueryWrapper<SpaceReport>()
                        .eq(SpaceReport::getUserId, userId)
                        .eq(SpaceReport::getReportType, type)
                        .eq(SpaceReport::getPeriodStart, date)
        );
    }

    private static void checkType(String type) {
        if (!TYPES.contains(type)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "报告类型只能是 day 或 week");
        }
    }

    private static LocalDate requireDate(LocalDate date) {
        if (date == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "日期不能为空");
        }
        return date;
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "未获取到当前用户");
        }
        return userId;
    }

    private ReportVO toVO(SpaceReport report) {
        ReportVO vo = new ReportVO();
        vo.setId(report.getId());
        vo.setType(report.getReportType());
        vo.setDate(report.getPeriodStart());
        vo.setContent(report.getContent());
        vo.setUpdateTime(report.getUpdateTime() == null ? LocalDateTime.now() : report.getUpdateTime());
        return vo;
    }
}
