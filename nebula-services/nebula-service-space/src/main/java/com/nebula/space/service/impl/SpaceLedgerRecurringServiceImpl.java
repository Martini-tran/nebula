package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.me.LedgerRecurringSaveRequest;
import com.nebula.space.entity.SpaceLedgerCategory;
import com.nebula.space.entity.SpaceLedgerEntry;
import com.nebula.space.entity.SpaceLedgerRecurring;
import com.nebula.space.mapper.SpaceLedgerCategoryMapper;
import com.nebula.space.mapper.SpaceLedgerEntryMapper;
import com.nebula.space.mapper.SpaceLedgerRecurringMapper;
import com.nebula.space.service.SpaceLedgerRecurringService;
import com.nebula.space.vo.me.LedgerRecurringVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 周期账单服务实现
 */
@Service
@RequiredArgsConstructor
public class SpaceLedgerRecurringServiceImpl implements SpaceLedgerRecurringService {

    private final SpaceLedgerRecurringMapper recurringMapper;
    private final SpaceLedgerEntryMapper entryMapper;
    private final SpaceLedgerCategoryMapper categoryMapper;

    @Override
    public List<LedgerRecurringVO> list() {
        Long userId = requireUserId();
        return recurringMapper.selectList(
                        new LambdaQueryWrapper<SpaceLedgerRecurring>()
                                .eq(SpaceLedgerRecurring::getUserId, userId)
                                .orderByAsc(SpaceLedgerRecurring::getId)
                ).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LedgerRecurringVO create(LedgerRecurringSaveRequest req) {
        Long userId = requireUserId();
        if (req == null || !StringUtils.hasText(req.getNote())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "名称不能为空");
        }
        if (req.getAmount() == null || req.getDirection() == null || req.getCategoryId() == null || req.getDay() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "金额、收支、分类、每月几号都要填");
        }
        SpaceLedgerRecurring r = new SpaceLedgerRecurring();
        r.setUserId(userId);
        r.setNote(req.getNote().trim());
        r.setAmount(req.getAmount());
        r.setDirection(req.getDirection());
        r.setCategoryId(req.getCategoryId());
        r.setDayOfMonth(req.getDay());
        r.setActive(Boolean.FALSE.equals(req.getActive()) ? 0 : 1);
        r.setStartMonth(StringUtils.hasText(req.getStartMonth()) ? req.getStartMonth() : YearMonth.now().toString());
        checkCategory(userId, r.getCategoryId(), r.getDirection());
        recurringMapper.insert(r);
        fill(userId);
        return toVO(r);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LedgerRecurringVO update(Long id, LedgerRecurringSaveRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        Long userId = requireUserId();
        SpaceLedgerRecurring r = requireRecurring(id, userId);
        if (req.getNote() != null) {
            if (!StringUtils.hasText(req.getNote())) {
                throw new BizException(HttpStatus.BAD_REQUEST, "名称不能为空");
            }
            r.setNote(req.getNote().trim());
        }
        if (req.getAmount() != null) {
            r.setAmount(req.getAmount());
        }
        if (req.getDirection() != null) {
            r.setDirection(req.getDirection());
        }
        if (req.getCategoryId() != null) {
            r.setCategoryId(req.getCategoryId());
        }
        if (req.getDay() != null) {
            r.setDayOfMonth(req.getDay());
        }
        if (req.getActive() != null) {
            boolean resume = req.getActive() && r.getActive() == 0;
            r.setActive(req.getActive() ? 1 : 0);
            if (resume) {
                // 暂停期间的月份不补：当作已经生成到上个月，本月到日子了照常记
                String lastMonth = YearMonth.now().minusMonths(1).toString();
                if (r.getFilledThrough() == null || r.getFilledThrough().compareTo(lastMonth) < 0) {
                    r.setFilledThrough(lastMonth);
                }
            }
        }
        checkCategory(userId, r.getCategoryId(), r.getDirection());
        // 审计填充是严格模式，已有值不会覆盖，这里显式刷新
        r.setUpdateTime(LocalDateTime.now());
        recurringMapper.updateById(r);
        fill(userId);
        return toVO(r);
    }

    @Override
    public void delete(Long id) {
        SpaceLedgerRecurring r = requireRecurring(id, requireUserId());
        recurringMapper.deleteById(r.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void fill(Long userId) {
        LocalDate today = LocalDate.now();
        List<SpaceLedgerRecurring> actives = recurringMapper.selectList(
                new LambdaQueryWrapper<SpaceLedgerRecurring>()
                        .eq(SpaceLedgerRecurring::getUserId, userId)
                        .eq(SpaceLedgerRecurring::getActive, 1)
        );
        for (SpaceLedgerRecurring r : actives) {
            List<YearMonth> months = monthsToFill(r.getStartMonth(), r.getFilledThrough(), r.getDayOfMonth(), today);
            if (months.isEmpty()) {
                continue;
            }
            // 先抢「已生成到哪个月」：同时打开两个页面时只有一个请求能改成功，另一个跳过，不会重复记
            String old = r.getFilledThrough();
            LambdaUpdateWrapper<SpaceLedgerRecurring> claim = new LambdaUpdateWrapper<SpaceLedgerRecurring>()
                    .eq(SpaceLedgerRecurring::getId, r.getId())
                    .set(SpaceLedgerRecurring::getFilledThrough, months.get(months.size() - 1).toString())
                    .set(SpaceLedgerRecurring::getUpdateTime, LocalDateTime.now());
            if (old == null) {
                claim.isNull(SpaceLedgerRecurring::getFilledThrough);
            } else {
                claim.eq(SpaceLedgerRecurring::getFilledThrough, old);
            }
            if (recurringMapper.update(null, claim) == 0) {
                continue;
            }
            for (YearMonth m : months) {
                SpaceLedgerEntry e = new SpaceLedgerEntry();
                e.setUserId(userId);
                e.setAmount(r.getAmount());
                e.setDirection(r.getDirection());
                e.setCategoryId(r.getCategoryId());
                e.setEntryDate(m.atDay(r.getDayOfMonth()));
                e.setNote(r.getNote());
                e.setRecurringId(r.getId());
                entryMapper.insert(e);
            }
        }
    }

    /**
     * 该补的月份：从开始月（或已生成的下一个月）起，到那天不晚于今天的最后一个月
     */
    static List<YearMonth> monthsToFill(String startMonth, String filledThrough, int day, LocalDate today) {
        YearMonth from = YearMonth.parse(startMonth);
        if (filledThrough != null) {
            YearMonth next = YearMonth.parse(filledThrough).plusMonths(1);
            if (next.isAfter(from)) {
                from = next;
            }
        }
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth m = from; !m.atDay(day).isAfter(today); m = m.plusMonths(1)) {
            months.add(m);
        }
        return months;
    }

    private void checkCategory(Long userId, Long categoryId, String direction) {
        SpaceLedgerCategory c = categoryMapper.selectOne(
                new LambdaQueryWrapper<SpaceLedgerCategory>()
                        .eq(SpaceLedgerCategory::getId, categoryId)
                        .eq(SpaceLedgerCategory::getUserId, userId)
        );
        if (c == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "分类不存在");
        }
        if (!c.getKind().equals(direction)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "分类和收支方向对不上");
        }
    }

    private SpaceLedgerRecurring requireRecurring(Long id, Long userId) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "周期账单ID不能为空");
        }
        SpaceLedgerRecurring r = recurringMapper.selectOne(
                new LambdaQueryWrapper<SpaceLedgerRecurring>()
                        .eq(SpaceLedgerRecurring::getId, id)
                        .eq(SpaceLedgerRecurring::getUserId, userId)
        );
        if (r == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "周期账单不存在或已删除");
        }
        return r;
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "未获取到当前用户");
        }
        return userId;
    }

    private LedgerRecurringVO toVO(SpaceLedgerRecurring r) {
        LedgerRecurringVO vo = new LedgerRecurringVO();
        vo.setId(r.getId());
        vo.setNote(r.getNote());
        vo.setAmount(BigDecimal.valueOf(r.getAmount()));
        vo.setDirection(r.getDirection());
        vo.setCategoryId(r.getCategoryId());
        vo.setDay(r.getDayOfMonth());
        vo.setActive(r.getActive() != null && r.getActive() == 1);
        vo.setStartMonth(r.getStartMonth());
        return vo;
    }
}
