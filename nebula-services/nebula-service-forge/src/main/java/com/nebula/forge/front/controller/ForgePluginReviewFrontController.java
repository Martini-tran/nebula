package com.nebula.forge.front.controller;

import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import com.nebula.forge.controller.AbstractFrontController;
import com.nebula.forge.dto.front.ForgePluginReviewCreateRequest;
import com.nebula.forge.dto.front.ForgePluginReviewFrontPageQuery;
import com.nebula.forge.service.ForgePluginReviewFrontService;
import com.nebula.forge.vo.front.ForgePluginReviewFrontVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 插件评价前台控制器
 * <p>查看公开；发表/删除/点赞需登录。</p>
 *
 * @author nebula
 */
@RestController
@RequestMapping("/front")
@RequiredArgsConstructor
public class ForgePluginReviewFrontController extends AbstractFrontController {

    private final ForgePluginReviewFrontService reviewFrontService;

    /**
     * 查看插件评价列表（公开，分页）
     */
    @GetMapping("/plugins/{id}/reviews")
    public R<PageResult<ForgePluginReviewFrontVO>> page(@PathVariable Long id,
                                                        @ModelAttribute ForgePluginReviewFrontPageQuery query) {
        return R.success(reviewFrontService.page(id, query));
    }

    /**
     * 发表/更新我的评价
     */
    @PostMapping("/plugins/{id}/reviews")
    public R<ForgePluginReviewFrontVO> submit(@PathVariable Long id,
                                              @RequestBody @Valid ForgePluginReviewCreateRequest req) {
        return R.success(reviewFrontService.submit(id, req));
    }

    /**
     * 删除我对该插件的评价
     */
    @DeleteMapping("/plugins/{id}/reviews/mine")
    public R<Void> deleteMine(@PathVariable Long id) {
        reviewFrontService.deleteMine(id);
        return R.success();
    }

    /**
     * 给评价点赞
     */
    @PostMapping("/reviews/{reviewId}/like")
    public R<Void> like(@PathVariable Long reviewId) {
        reviewFrontService.like(reviewId);
        return R.success();
    }
}
