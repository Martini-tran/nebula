package com.nebula.space.files;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 要下载的内容：文件名、类型、大小（打包时未知为 null）和写出内容的方法
 *
 * <p>出错要在拿到这个对象之前抛出（这时还能回 JSON 错误），{@link Body#writeTo} 开始写之后响应头已发出。</p>
 */
public record FileDownload(String name, String mime, Long size, Body body) {

    @FunctionalInterface
    public interface Body {
        void writeTo(OutputStream out) throws IOException;
    }
}
