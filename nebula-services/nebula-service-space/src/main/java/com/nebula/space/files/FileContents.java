package com.nebula.space.files;

import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.exception.BizException;
import com.nebula.common.file.service.SysFileService;
import com.nebula.space.entity.SpaceFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 从公共文件存储取文件内容：单个文件原样给，文件夹打成 zip
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileContents {

    private static final String OCTET_STREAM = "application/octet-stream";

    private final SysFileService sysFileService;

    /**
     * 单个文件。先打开对象存储的流，文件丢了能在写响应前报错
     */
    public FileDownload single(SpaceFile f) {
        if (f.getSysFileId() == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "文件内容不存在");
        }
        InputStream in = sysFileService.download(f.getSysFileId());
        return new FileDownload(f.getName(), mimeOf(f), f.getSizeBytes(), out -> {
            try (in) {
                in.transferTo(out);
            }
        });
    }

    /**
     * 文件夹打包：压缩包里带上文件夹自己这一层，最近删除里的不打进去
     *
     * @param rows 这个用户的全部文件行
     */
    public FileDownload zip(SpaceFile folder, List<SpaceFile> rows) {
        List<SpaceFile> items = FileTree.descendants(rows, folder.getId()).stream()
                .filter(FileTree::alive)
                .toList();
        return new FileDownload(folder.getName() + ".zip", "application/zip", null, out -> {
            ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
            Set<String> used = new HashSet<>();
            zip.putNextEntry(new ZipEntry(folder.getName() + "/"));
            zip.closeEntry();
            for (SpaceFile f : items) {
                String path = FileTree.pathFrom(rows, folder, f);
                if (FileTree.isFolder(f)) {
                    if (used.add(path + "/")) {
                        zip.putNextEntry(new ZipEntry(path + "/"));
                        zip.closeEntry();
                    }
                    continue;
                }
                if (f.getSysFileId() == null) {
                    continue;
                }
                InputStream in;
                try {
                    in = sysFileService.download(f.getSysFileId());
                } catch (RuntimeException e) {
                    // 响应已经开始写了，丢一个文件总比整个包坏掉好
                    log.warn("打包时读取文件失败, spaceFileId={}, sysFileId={}", f.getId(), f.getSysFileId(), e);
                    continue;
                }
                try (in) {
                    zip.putNextEntry(new ZipEntry(unique(used, path)));
                    in.transferTo(zip);
                    zip.closeEntry();
                }
            }
            zip.finish();
        });
    }

    /**
     * 下载时给浏览器的类型。JSON 文件改成二进制流：前端靠 json 类型认出「下载接口返回的是错误」
     */
    private static String mimeOf(SpaceFile f) {
        String mime = f.getMime();
        if (!StringUtils.hasText(mime) || mime.toLowerCase().contains("json")) {
            return OCTET_STREAM;
        }
        return mime;
    }

    private static String unique(Set<String> used, String path) {
        if (used.add(path)) {
            return path;
        }
        int dot = path.lastIndexOf('.');
        int slash = path.lastIndexOf('/');
        String base = dot > slash ? path.substring(0, dot) : path;
        String ext = dot > slash ? path.substring(dot) : "";
        for (int i = 1; ; i++) {
            String candidate = base + " (" + i + ")" + ext;
            if (used.add(candidate)) {
                return candidate;
            }
        }
    }
}
