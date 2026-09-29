package com.nebula.space.service;

import com.nebula.space.dto.me.LedgerBudgetSaveRequest;
import com.nebula.space.dto.me.LedgerCategorySaveRequest;
import com.nebula.space.dto.me.LedgerEntryQuery;
import com.nebula.space.dto.me.LedgerEntrySaveRequest;
import com.nebula.space.vo.me.LedgerBudgetVO;
import com.nebula.space.vo.me.LedgerCategoryVO;
import com.nebula.space.vo.me.LedgerEntryVO;

import java.util.List;

/**
 * 记账服务：分类、流水、月预算，只操作当前登录用户自己的数据
 *
 * <p>只记个人日常收支，不对接银行、不做理财；金额一律以「分」为单位的整数。
 * 备注里的关键词归类、自然语言解析（「午饭 32」）都在前端（utils/ledgerParser.ts）。</p>
 */
public interface SpaceLedgerService {

    /**
     * 分类；第一次读取时按默认分类初始化
     */
    List<LedgerCategoryVO> listCategories();

    LedgerCategoryVO updateCategory(Long id, LedgerCategorySaveRequest req);

    /**
     * 流水，新的在前；取之前先把周期账单补到今天
     */
    List<LedgerEntryVO> listEntries(LedgerEntryQuery query);

    LedgerEntryVO createEntry(LedgerEntrySaveRequest req);

    LedgerEntryVO updateEntry(Long id, LedgerEntrySaveRequest req);

    void deleteEntry(Long id);

    /**
     * 某月预算；这个月没设时沿用之前最近的一个月，都没有则为空预算
     */
    LedgerBudgetVO getBudget(String month);

    /**
     * 设过预算的每个月（导出用），按月份
     */
    List<LedgerBudgetVO> listBudgets();

    /**
     * 保存某月预算（整份覆盖）
     */
    LedgerBudgetVO saveBudget(String month, LedgerBudgetSaveRequest req);
}
