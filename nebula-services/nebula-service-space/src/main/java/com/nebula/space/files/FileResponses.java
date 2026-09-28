package com.nebula.space.files;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 把 {@link FileDownload} 写到响应里：一律按附件下载，不让浏览器按内容猜类型
 */
public final class FileResponses {

    private FileResponses() {
    }

    public static void send(FileDownload download, HttpServletResponse response) throws IOException {
        response.setContentType(download.mime());
        if (download.size() != null) {
            response.setContentLengthLong(download.size());
        }
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(download.name(), StandardCharsets.UTF_8)
                .build()
                .toString());
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        download.body().writeTo(response.getOutputStream());
    }
}
