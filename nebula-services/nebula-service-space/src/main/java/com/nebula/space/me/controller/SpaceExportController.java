package com.nebula.space.me.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.nebula.space.service.SpaceExportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 前台「导出全部数据」控制器
 *
 * <p>/me/** 只校验登录，数据按当前用户隔离，不走后台权限码。</p>
 */
@RestController
@RequestMapping("/me/export")
@SaCheckLogin
@RequiredArgsConstructor
public class SpaceExportController {

    private final SpaceExportService exportService;
    /**
     * 与接口返回用同一个 JsonMapper：ID 是字符串、时间是「yyyy-MM-dd HH:mm:ss」，和页面里看到的一致
     */
    private final JsonMapper jsonMapper;

    /**
     * 下载一份 JSON。先把数据取齐再写响应头，取数出错时仍按普通接口返回错误信息；
     * 用 octet-stream 下发，前端看到 json 类型就知道是出错了（与文件柜下载一致）
     */
    @GetMapping
    public void export(HttpServletResponse response) throws IOException {
        Map<String, Object> snapshot = exportService.snapshot();
        String filename = "nebula-space_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".json";
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        jsonMapper.writerWithDefaultPrettyPrinter().writeValue(response.getOutputStream(), snapshot);
    }
}
