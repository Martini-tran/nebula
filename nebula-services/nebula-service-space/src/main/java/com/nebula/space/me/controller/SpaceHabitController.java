package com.nebula.space.me.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.nebula.common.core.domain.R;
import com.nebula.space.dto.me.HabitLogQuery;
import com.nebula.space.dto.me.HabitLogSetRequest;
import com.nebula.space.dto.me.HabitSaveRequest;
import com.nebula.space.service.SpaceHabitService;
import com.nebula.space.vo.me.HabitLogVO;
import com.nebula.space.vo.me.HabitVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 前台习惯与打卡控制器
 *
 * <p>/me/** 只校验登录，数据按当前用户隔离，不走后台权限码。</p>
 */
@RestController
@RequestMapping("/me")
@SaCheckLogin
@RequiredArgsConstructor
public class SpaceHabitController {

    private final SpaceHabitService habitService;

    /**
     * 习惯列表，默认不含已归档
     */
    @GetMapping("/habits")
    public R<List<HabitVO>> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return R.success(habitService.list(includeArchived));
    }

    /**
     * 新建习惯
     */
    @PostMapping("/habits")
    public R<HabitVO> create(@RequestBody @Valid HabitSaveRequest req) {
        return R.success(habitService.create(req));
    }

    /**
     * 局部保存：名称、图标、目标、频率、提醒、归档、排序等
     */
    @PutMapping("/habits/{id}")
    public R<HabitVO> update(@PathVariable Long id, @RequestBody @Valid HabitSaveRequest req) {
        return R.success(habitService.update(id, req));
    }

    /**
     * 删除习惯，连同打卡记录
     */
    @DeleteMapping("/habits/{id}")
    public R<Void> delete(@PathVariable Long id) {
        habitService.delete(id);
        return R.success();
    }

    /**
     * 写入某天的值（覆盖）；value = 0 取消打卡，返回 null
     */
    @PutMapping("/habits/{id}/logs/{date}")
    public R<HabitLogVO> setLog(@PathVariable Long id,
                                @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                @RequestBody @Valid HabitLogSetRequest req) {
        return R.success(habitService.setLog(id, date, req));
    }

    /**
     * 打卡记录：可按习惯、日期区间筛选
     */
    @GetMapping("/habit-logs")
    public R<List<HabitLogVO>> logs(HabitLogQuery query) {
        return R.success(habitService.logs(query));
    }
}
