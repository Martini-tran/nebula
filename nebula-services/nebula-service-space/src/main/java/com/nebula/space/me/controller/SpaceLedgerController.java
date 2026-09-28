package com.nebula.space.me.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.nebula.common.core.domain.R;
import com.nebula.space.dto.me.LedgerBudgetSaveRequest;
import com.nebula.space.dto.me.LedgerCategorySaveRequest;
import com.nebula.space.dto.me.LedgerEntryQuery;
import com.nebula.space.dto.me.LedgerEntrySaveRequest;
import com.nebula.space.dto.me.LedgerRecurringSaveRequest;
import com.nebula.space.service.SpaceLedgerRecurringService;
import com.nebula.space.service.SpaceLedgerService;
import com.nebula.space.vo.me.LedgerBudgetVO;
import com.nebula.space.vo.me.LedgerCategoryVO;
import com.nebula.space.vo.me.LedgerEntryVO;
import com.nebula.space.vo.me.LedgerRecurringVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 前台记账控制器：分类、流水、周期账单、月预算
 *
 * <p>/me/** 只校验登录，数据按当前用户隔离，不走后台权限码。金额一律以「分」为单位。</p>
 */
@RestController
@RequestMapping("/me/ledger")
@SaCheckLogin
@RequiredArgsConstructor
public class SpaceLedgerController {

    private final SpaceLedgerService ledgerService;
    private final SpaceLedgerRecurringService recurringService;

    /**
     * 分类（第一次读取时按默认分类初始化）
     */
    @GetMapping("/categories")
    public R<List<LedgerCategoryVO>> categories() {
        return R.success(ledgerService.listCategories());
    }

    /**
     * 修改分类：名称、图标、颜色、关键词、排序
     */
    @PutMapping("/categories/{id}")
    public R<LedgerCategoryVO> updateCategory(@PathVariable Long id, @RequestBody @Valid LedgerCategorySaveRequest req) {
        return R.success(ledgerService.updateCategory(id, req));
    }

    /**
     * 流水，新的在前；取之前先把周期账单补到今天
     */
    @GetMapping("/entries")
    public R<List<LedgerEntryVO>> entries(LedgerEntryQuery query) {
        return R.success(ledgerService.listEntries(query));
    }

    /**
     * 记一笔
     */
    @PostMapping("/entries")
    public R<LedgerEntryVO> createEntry(@RequestBody @Valid LedgerEntrySaveRequest req) {
        return R.success(ledgerService.createEntry(req));
    }

    /**
     * 改一笔：不传的字段不动
     */
    @PutMapping("/entries/{id}")
    public R<LedgerEntryVO> updateEntry(@PathVariable Long id, @RequestBody @Valid LedgerEntrySaveRequest req) {
        return R.success(ledgerService.updateEntry(id, req));
    }

    /**
     * 删一笔（周期账单生成的删掉后不会再补回来）
     */
    @DeleteMapping("/entries/{id}")
    public R<Void> deleteEntry(@PathVariable Long id) {
        ledgerService.deleteEntry(id);
        return R.success();
    }

    /**
     * 周期账单
     */
    @GetMapping("/recurring")
    public R<List<LedgerRecurringVO>> recurring() {
        return R.success(recurringService.list());
    }

    /**
     * 新建周期账单；本月那天已经过了的话，这笔马上补上
     */
    @PostMapping("/recurring")
    public R<LedgerRecurringVO> createRecurring(@RequestBody @Valid LedgerRecurringSaveRequest req) {
        return R.success(recurringService.create(req));
    }

    /**
     * 修改、暂停或恢复周期账单；恢复时暂停期间的月份不补
     */
    @PutMapping("/recurring/{id}")
    public R<LedgerRecurringVO> updateRecurring(@PathVariable Long id, @RequestBody @Valid LedgerRecurringSaveRequest req) {
        return R.success(recurringService.update(id, req));
    }

    /**
     * 删除周期账单，已记下的流水保留
     */
    @DeleteMapping("/recurring/{id}")
    public R<Void> deleteRecurring(@PathVariable Long id) {
        recurringService.delete(id);
        return R.success();
    }

    /**
     * 某月预算（YYYY-MM）；没设时沿用之前最近的一个月
     */
    @GetMapping("/budgets/{month}")
    public R<LedgerBudgetVO> budget(@PathVariable String month) {
        return R.success(ledgerService.getBudget(month));
    }

    /**
     * 保存某月预算，之后的月份没设时沿用
     */
    @PutMapping("/budgets/{month}")
    public R<LedgerBudgetVO> saveBudget(@PathVariable String month, @RequestBody @Valid LedgerBudgetSaveRequest req) {
        return R.success(ledgerService.saveBudget(month, req));
    }
}
