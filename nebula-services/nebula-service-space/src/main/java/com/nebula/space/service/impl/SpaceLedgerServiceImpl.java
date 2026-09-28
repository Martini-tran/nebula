package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.me.LedgerBudgetSaveRequest;
import com.nebula.space.dto.me.LedgerCategorySaveRequest;
import com.nebula.space.dto.me.LedgerEntryQuery;
import com.nebula.space.dto.me.LedgerEntrySaveRequest;
import com.nebula.space.entity.SpaceLedgerBudget;
import com.nebula.space.entity.SpaceLedgerCategory;
import com.nebula.space.entity.SpaceLedgerEntry;
import com.nebula.space.entity.SpaceLedgerRecurring;
import com.nebula.space.mapper.SpaceLedgerBudgetMapper;
import com.nebula.space.mapper.SpaceLedgerCategoryMapper;
import com.nebula.space.mapper.SpaceLedgerEntryMapper;
import com.nebula.space.mapper.SpaceLedgerRecurringMapper;
import com.nebula.space.service.SpaceLedgerRecurringService;
import com.nebula.space.service.SpaceLedgerService;
import com.nebula.space.vo.me.LedgerBudgetVO;
import com.nebula.space.vo.me.LedgerCategoryVO;
import com.nebula.space.vo.me.LedgerEntryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 记账服务实现
 */
@Service
@RequiredArgsConstructor
public class SpaceLedgerServiceImpl implements SpaceLedgerService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<LinkedHashMap<String, Long>> AMOUNT_MAP = new TypeReference<>() {
    };

    /**
     * 默认分类：新用户第一次打开记账时建好，之后可以改名、改关键词
     */
    private static final List<SpaceLedgerCategory> DEFAULT_CATEGORIES = List.of(
            category("餐饮", "lucide:utensils", "#0d9488", "out", 1, "午饭", "晚饭", "早餐", "早饭", "外卖", "咖啡", "瑞幸", "星巴克", "奶茶", "聚餐", "食堂", "面包"),
            category("交通", "lucide:car-taxi-front", "#d97706", "out", 2, "打车", "地铁", "公交", "高铁", "火车", "机票", "加油", "停车", "滴滴", "单车"),
            category("住房", "lucide:house", "#4f46e5", "out", 3, "房租", "物业", "水费", "电费", "燃气", "宽带", "维修"),
            category("购物", "lucide:shopping-bag", "#7c3aed", "out", 4, "淘宝", "京东", "超市", "衣服", "鞋", "日用"),
            category("宠物", "lucide:cat", "#db2777", "out", 5, "猫粮", "猫砂", "宠物", "疫苗", "驱虫"),
            category("订阅", "lucide:tv", "#2563eb", "out", 6, "会员", "订阅", "icloud", "netflix", "spotify", "续费", "chatgpt", "claude"),
            category("医疗", "lucide:pill", "#dc2626", "out", 7, "药", "医院", "挂号", "体检", "牙"),
            category("其他", "lucide:package", "#9ca3af", "out", 99),
            category("工资", "lucide:wallet", "#16a34a", "in", 10, "工资", "薪水", "奖金", "年终"),
            category("其他收入", "lucide:banknote", "#65a30d", "in", 98, "卖出", "退款", "红包", "利息", "报销")
    );

    private final SpaceLedgerCategoryMapper categoryMapper;
    private final SpaceLedgerEntryMapper entryMapper;
    private final SpaceLedgerRecurringMapper recurringMapper;
    private final SpaceLedgerBudgetMapper budgetMapper;
    private final SpaceLedgerRecurringService recurringService;

    // ----------------------------------------------------------------- 分类

    @Override
    public List<LedgerCategoryVO> listCategories() {
        Long userId = requireUserId();
        List<SpaceLedgerCategory> list = selectCategories(userId);
        if (list.isEmpty()) {
            for (SpaceLedgerCategory d : DEFAULT_CATEGORIES) {
                SpaceLedgerCategory c = new SpaceLedgerCategory();
                c.setUserId(userId);
                c.setName(d.getName());
                c.setIcon(d.getIcon());
                c.setColor(d.getColor());
                c.setKind(d.getKind());
                c.setKeywords(d.getKeywords());
                c.setSortOrder(d.getSortOrder());
                try {
                    categoryMapper.insert(c);
                } catch (DuplicateKeyException e) {
                    // 两个请求同时初始化：另一个已经插进去了
                }
            }
            list = selectCategories(userId);
        }
        return list.stream().map(this::toVO).toList();
    }

    @Override
    public LedgerCategoryVO updateCategory(Long id, LedgerCategorySaveRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        Long userId = requireUserId();
        SpaceLedgerCategory c = requireCategory(userId, id);
        if (req.getName() != null) {
            if (!StringUtils.hasText(req.getName())) {
                throw new BizException(HttpStatus.BAD_REQUEST, "名称不能为空");
            }
            c.setName(req.getName().trim());
        }
        if (req.getIcon() != null) {
            c.setIcon(req.getIcon());
        }
        if (req.getColor() != null) {
            c.setColor(req.getColor());
        }
        if (req.getKeywords() != null) {
            c.setKeywords(writeJson(req.getKeywords().stream().map(String::trim).filter(StringUtils::hasText).distinct().toList()));
        }
        if (req.getSortOrder() != null) {
            c.setSortOrder(req.getSortOrder());
        }
        c.setUpdateTime(LocalDateTime.now());
        try {
            categoryMapper.updateById(c);
        } catch (DuplicateKeyException e) {
            throw new BizException(HttpStatus.BAD_REQUEST, "已经有叫「" + c.getName() + "」的分类了");
        }
        return toVO(c);
    }

    // ----------------------------------------------------------------- 流水

    @Override
    public List<LedgerEntryVO> listEntries(LedgerEntryQuery query) {
        Long userId = requireUserId();
        recurringService.fill(userId);
        LedgerEntryQuery q = query == null ? new LedgerEntryQuery() : query;
        return entryMapper.selectList(
                        new LambdaQueryWrapper<SpaceLedgerEntry>()
                                .eq(SpaceLedgerEntry::getUserId, userId)
                                .ge(q.getFrom() != null, SpaceLedgerEntry::getEntryDate, q.getFrom())
                                .le(q.getTo() != null, SpaceLedgerEntry::getEntryDate, q.getTo())
                                .orderByDesc(SpaceLedgerEntry::getEntryDate)
                                .orderByDesc(SpaceLedgerEntry::getCreateTime)
                                .orderByDesc(SpaceLedgerEntry::getId)
                ).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public LedgerEntryVO createEntry(LedgerEntrySaveRequest req) {
        Long userId = requireUserId();
        if (req == null || req.getAmount() == null || req.getDirection() == null || req.getCategoryId() == null || req.getDate() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "金额、收支、分类、日期都要填");
        }
        checkCategory(userId, req.getCategoryId(), req.getDirection());
        SpaceLedgerEntry e = new SpaceLedgerEntry();
        e.setUserId(userId);
        e.setAmount(req.getAmount());
        e.setDirection(req.getDirection());
        e.setCategoryId(req.getCategoryId());
        e.setEntryDate(req.getDate());
        e.setNote(req.getNote() == null ? "" : req.getNote().trim());
        // 撤销删除时带回原来的周期账单；不是自己的就不认
        if (req.getRecurringId() != null && ownsRecurring(userId, req.getRecurringId())) {
            e.setRecurringId(req.getRecurringId());
        }
        entryMapper.insert(e);
        return toVO(e);
    }

    @Override
    public LedgerEntryVO updateEntry(Long id, LedgerEntrySaveRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        Long userId = requireUserId();
        SpaceLedgerEntry e = requireEntry(userId, id);
        if (req.getAmount() != null) {
            e.setAmount(req.getAmount());
        }
        if (req.getDirection() != null) {
            e.setDirection(req.getDirection());
        }
        if (req.getCategoryId() != null) {
            e.setCategoryId(req.getCategoryId());
        }
        if (req.getDate() != null) {
            e.setEntryDate(req.getDate());
        }
        if (req.getNote() != null) {
            e.setNote(req.getNote().trim());
        }
        checkCategory(userId, e.getCategoryId(), e.getDirection());
        // 审计填充是严格模式，已有值不会覆盖，这里显式刷新
        e.setUpdateTime(LocalDateTime.now());
        entryMapper.updateById(e);
        return toVO(e);
    }

    @Override
    public void deleteEntry(Long id) {
        SpaceLedgerEntry e = requireEntry(requireUserId(), id);
        entryMapper.deleteById(e.getId());
    }

    // ----------------------------------------------------------------- 预算

    @Override
    public LedgerBudgetVO getBudget(String month) {
        Long userId = requireUserId();
        String m = requireMonth(month);
        SpaceLedgerBudget b = budgetMapper.selectOne(
                new LambdaQueryWrapper<SpaceLedgerBudget>()
                        .eq(SpaceLedgerBudget::getUserId, userId)
                        .le(SpaceLedgerBudget::getBudgetMonth, m)
                        .orderByDesc(SpaceLedgerBudget::getBudgetMonth)
                        .last("limit 1")
        );
        return toVO(m, b);
    }

    @Override
    public LedgerBudgetVO saveBudget(String month, LedgerBudgetSaveRequest req) {
        Long userId = requireUserId();
        String m = requireMonth(month);
        Map<String, Long> items = new LinkedHashMap<>();
        if (req != null && req.getItems() != null) {
            req.getItems().forEach((k, v) -> {
                if (v == null || v < 0 || v > 99_999_999_999L) {
                    throw new BizException(HttpStatus.BAD_REQUEST, "预算额度不正确");
                }
                if (v > 0) {
                    items.put(k, v);
                }
            });
        }
        Long total = req == null ? null : req.getTotal();
        SpaceLedgerBudget b = findBudget(userId, m);
        if (b == null) {
            b = new SpaceLedgerBudget();
            b.setUserId(userId);
            b.setBudgetMonth(m);
            b.setTotal(total);
            b.setItems(writeJson(items));
            try {
                budgetMapper.insert(b);
                return toVO(m, b);
            } catch (DuplicateKeyException e) {
                // 同时保存同一个月：另一份已插入，改为覆盖它
                b = findBudget(userId, m);
                if (b == null) {
                    throw e;
                }
            }
        }
        b.setTotal(total);
        b.setItems(writeJson(items));
        b.setUpdateTime(LocalDateTime.now());
        budgetMapper.updateById(b);
        return toVO(m, b);
    }

    // ----------------------------------------------------------------- 内部工具

    private static SpaceLedgerCategory category(String name, String icon, String color, String kind, int sort, String... keywords) {
        SpaceLedgerCategory c = new SpaceLedgerCategory();
        c.setName(name);
        c.setIcon(icon);
        c.setColor(color);
        c.setKind(kind);
        c.setKeywords(writeJson(List.of(keywords)));
        c.setSortOrder(sort);
        return c;
    }

    private List<SpaceLedgerCategory> selectCategories(Long userId) {
        return categoryMapper.selectList(
                new LambdaQueryWrapper<SpaceLedgerCategory>()
                        .eq(SpaceLedgerCategory::getUserId, userId)
                        .orderByAsc(SpaceLedgerCategory::getSortOrder)
                        .orderByAsc(SpaceLedgerCategory::getId)
        );
    }

    private SpaceLedgerCategory requireCategory(Long userId, Long id) {
        SpaceLedgerCategory c = id == null ? null : categoryMapper.selectOne(
                new LambdaQueryWrapper<SpaceLedgerCategory>()
                        .eq(SpaceLedgerCategory::getId, id)
                        .eq(SpaceLedgerCategory::getUserId, userId)
        );
        if (c == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "分类不存在");
        }
        return c;
    }

    /**
     * 分类得是自己的，收支方向也要对得上（支出记到「工资」上会让统计乱掉）
     */
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

    private boolean ownsRecurring(Long userId, Long recurringId) {
        return recurringMapper.selectCount(
                new LambdaQueryWrapper<SpaceLedgerRecurring>()
                        .eq(SpaceLedgerRecurring::getId, recurringId)
                        .eq(SpaceLedgerRecurring::getUserId, userId)
        ) > 0;
    }

    private SpaceLedgerEntry requireEntry(Long userId, Long id) {
        SpaceLedgerEntry e = id == null ? null : entryMapper.selectOne(
                new LambdaQueryWrapper<SpaceLedgerEntry>()
                        .eq(SpaceLedgerEntry::getId, id)
                        .eq(SpaceLedgerEntry::getUserId, userId)
        );
        if (e == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "这笔账不存在或已删除");
        }
        return e;
    }

    private SpaceLedgerBudget findBudget(Long userId, String month) {
        return budgetMapper.selectOne(
                new LambdaQueryWrapper<SpaceLedgerBudget>()
                        .eq(SpaceLedgerBudget::getUserId, userId)
                        .eq(SpaceLedgerBudget::getBudgetMonth, month)
        );
    }

    private static String requireMonth(String month) {
        try {
            return YearMonth.parse(month).toString();
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BizException(HttpStatus.BAD_REQUEST, "月份格式应为 YYYY-MM");
        }
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "未获取到当前用户");
        }
        return userId;
    }

    private static String writeJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(HttpStatus.BAD_REQUEST, "数据格式不正确");
        }
    }

    private static <T> T readJson(String json, TypeReference<T> type, T fallback) {
        if (!StringUtils.hasText(json)) {
            return fallback;
        }
        try {
            return JSON.readValue(json, type);
        } catch (JsonProcessingException e) {
            return fallback;
        }
    }

    private LedgerCategoryVO toVO(SpaceLedgerCategory c) {
        LedgerCategoryVO vo = new LedgerCategoryVO();
        vo.setId(c.getId());
        vo.setName(c.getName());
        vo.setIcon(c.getIcon());
        vo.setColor(c.getColor());
        vo.setKind(c.getKind());
        vo.setKeywords(readJson(c.getKeywords(), STRING_LIST, List.of()));
        vo.setSortOrder(c.getSortOrder());
        return vo;
    }

    private LedgerEntryVO toVO(SpaceLedgerEntry e) {
        LedgerEntryVO vo = new LedgerEntryVO();
        vo.setId(e.getId());
        vo.setAmount(BigDecimal.valueOf(e.getAmount()));
        vo.setDirection(e.getDirection());
        vo.setCategoryId(e.getCategoryId());
        vo.setDate(e.getEntryDate());
        vo.setNote(e.getNote());
        vo.setRecurringId(e.getRecurringId());
        vo.setCreateTime(e.getCreateTime() == null ? LocalDateTime.now() : e.getCreateTime());
        return vo;
    }

    private LedgerBudgetVO toVO(String month, SpaceLedgerBudget b) {
        LedgerBudgetVO vo = new LedgerBudgetVO();
        vo.setMonth(month);
        Map<String, BigDecimal> items = new LinkedHashMap<>();
        if (b != null) {
            vo.setTotal(b.getTotal() == null ? null : BigDecimal.valueOf(b.getTotal()));
            readJson(b.getItems(), AMOUNT_MAP, new LinkedHashMap<>()).forEach((k, v) -> items.put(k, BigDecimal.valueOf(v)));
        }
        vo.setItems(items);
        return vo;
    }
}
