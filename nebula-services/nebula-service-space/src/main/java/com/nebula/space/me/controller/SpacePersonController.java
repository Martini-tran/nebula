package com.nebula.space.me.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.nebula.common.core.domain.R;
import com.nebula.space.dto.me.PersonSaveRequest;
import com.nebula.space.service.SpacePersonService;
import com.nebula.space.vo.me.PersonVO;
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
 * 前台人物卡控制器
 *
 * <p>/me/** 只校验登录，数据按当前用户隔离，不走后台权限码。人物卡只是自己的备忘，不关联系统账号。</p>
 */
@RestController
@RequestMapping("/me/people")
@SaCheckLogin
@RequiredArgsConstructor
public class SpacePersonController {

    private final SpacePersonService personService;

    /**
     * 全部人物
     */
    @GetMapping
    public R<List<PersonVO>> list() {
        return R.success(personService.list());
    }

    /**
     * 添加人物；同名的不能重复添加
     */
    @PostMapping
    public R<PersonVO> create(@RequestBody @Valid PersonSaveRequest req) {
        return R.success(personService.create(req));
    }

    /**
     * 局部保存：不传的字段不动，传 null 清空生日、关掉联系提醒；承诺、联系记录等数组整份覆盖
     */
    @PutMapping("/{id}")
    public R<PersonVO> update(@PathVariable Long id, @RequestBody @Valid PersonSaveRequest req) {
        return R.success(personService.update(id, req));
    }

    /**
     * 删除人物卡
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        personService.delete(id);
        return R.success();
    }
}
