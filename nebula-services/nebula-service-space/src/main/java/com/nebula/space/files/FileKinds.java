package com.nebula.space.files;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 文件分成几类：侧栏的类型筛选与容量条都按这个分，规则与前端 utils/files.ts 的 kindOf 一致
 */
public final class FileKinds {

    /** 容量条的顺序 */
    public static final List<String> ALL = List.of("pdf", "image", "doc", "sheet", "zip", "other");

    private static final Set<String> IMAGE = Set.of("jpg", "jpeg", "png", "gif", "webp", "svg", "heic");
    private static final Set<String> SHEET = Set.of("xls", "xlsx", "csv", "numbers");
    private static final Set<String> DOC = Set.of("doc", "docx", "txt", "md", "ppt", "pptx", "pages", "rtf", "html");
    private static final Set<String> ZIP = Set.of("zip", "rar", "7z", "tar", "gz");

    private FileKinds() {
    }

    public static String extOf(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static String kindOf(String name, String mime) {
        String ext = extOf(name);
        String m = mime == null ? "" : mime;
        if ("pdf".equals(ext) || "application/pdf".equals(m)) {
            return "pdf";
        }
        if (m.startsWith("image/") || IMAGE.contains(ext)) {
            return "image";
        }
        if (SHEET.contains(ext)) {
            return "sheet";
        }
        if (DOC.contains(ext) || m.startsWith("text/")) {
            return "doc";
        }
        if (ZIP.contains(ext)) {
            return "zip";
        }
        return "other";
    }
}
