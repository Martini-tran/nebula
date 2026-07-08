package com.nebula.blog.admin.controller;

import com.nebula.blog.dto.admin.SeriesIterationAppendRequest;
import com.nebula.blog.service.BlogSeriesIterationService;
import com.nebula.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 迭代链回调消费端（编排 webhook）
 * 接收 manager 的 IterationDriver 每轮 advance 成功后的回调，把产物落成博客文章（草稿）并挂到系列目录。
 * 见 docs/编排回调Webhook设计.md 第七章。
 *
 * <p><b>鉴权（W1）</b>：本接口是服务间回调，无用户登录态，W1 暂不加 {@code @SaCheckPermission}
 * （鉴权留 W3：headers/secret 签名）。生产接入前应在网关或此处补内部 token 校验。
 *
 * @author nebula
 */
@RestController
@RequestMapping("/admin/blog/webhook")
@RequiredArgsConstructor
public class SeriesIterationWebhookController {

    private final BlogSeriesIterationService iterationService;

    /**
     * 落一篇迭代链产出的文章（幂等）。返回文章 id。
     */
    @PostMapping("/series-append")
    public R<Long> seriesAppend(@RequestBody SeriesIterationAppendRequest req) {
        return R.success("append success", iterationService.append(req));
    }
}
