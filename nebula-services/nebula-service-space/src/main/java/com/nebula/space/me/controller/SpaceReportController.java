package com.nebula.space.me.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.nebula.common.core.domain.R;
import com.nebula.space.dto.me.ReportQuery;
import com.nebula.space.dto.me.ReportSaveRequest;
import com.nebula.space.service.SpaceReportService;
import com.nebula.space.vo.me.ReportVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 前台日报周报控制器
 *
 * <p>/me/** 只校验登录，数据按当前用户隔离，不走后台权限码。</p>
 */
@RestController
@RequestMapping("/me/reports")
@SaCheckLogin
@RequiredArgsConstructor
public class SpaceReportController {

    private final SpaceReportService reportService;

    /**
     * 报告列表：新的在前，可按类型、日期筛选
     */
    @GetMapping
    public R<List<ReportVO>> list(ReportQuery query) {
        return R.success(reportService.list(query));
    }

    /**
     * 某天的日报 / 某周的周报，没写过返回 null
     */
    @GetMapping("/{type}/{date}")
    public R<ReportVO> get(@PathVariable String type, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return R.success(reportService.get(type, date));
    }

    /**
     * 整份覆盖保存；正文为空时删除并返回 null
     */
    @PutMapping("/{type}/{date}")
    public R<ReportVO> save(@PathVariable String type, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                            @RequestBody @Valid ReportSaveRequest req) {
        return R.success(reportService.save(type, date, req));
    }
}
