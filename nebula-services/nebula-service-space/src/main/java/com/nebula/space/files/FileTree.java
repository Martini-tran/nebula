package com.nebula.space.files;

import com.nebula.space.entity.SpaceFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 在一个用户的全部文件行上做文件夹树的查找（行数不多，整份读出来在内存里算）
 */
public final class FileTree {

    private FileTree() {
    }

    public static boolean alive(SpaceFile f) {
        return f.getDeleteTime() == null;
    }

    public static boolean isFolder(SpaceFile f) {
        return f.getIsFolder() != null && f.getIsFolder() == 1;
    }

    public static SpaceFile find(List<SpaceFile> rows, Long id) {
        if (id == null) {
            return null;
        }
        return rows.stream().filter(f -> id.equals(f.getId())).findFirst().orElse(null);
    }

    public static List<SpaceFile> childrenOf(List<SpaceFile> rows, Long id) {
        return rows.stream().filter(f -> Objects.equals(f.getFolderId(), id)).toList();
    }

    /**
     * 文件夹里的全部内容（逐层往下，不含自己）
     */
    public static List<SpaceFile> descendants(List<SpaceFile> rows, Long id) {
        List<SpaceFile> out = new ArrayList<>();
        for (SpaceFile child : childrenOf(rows, id)) {
            out.add(child);
            if (isFolder(child)) {
                out.addAll(descendants(rows, child.getId()));
            }
        }
        return out;
    }

    /**
     * 自己加上文件夹里的全部内容
     */
    public static List<SpaceFile> withDescendants(List<SpaceFile> rows, SpaceFile f) {
        List<SpaceFile> out = new ArrayList<>();
        out.add(f);
        if (isFolder(f)) {
            out.addAll(descendants(rows, f.getId()));
        }
        return out;
    }

    /**
     * 相对 {@code root} 的路径（含 root 自己的名字），打包下载时当压缩包里的路径
     */
    public static String pathFrom(List<SpaceFile> rows, SpaceFile root, SpaceFile f) {
        StringBuilder path = new StringBuilder(f.getName());
        SpaceFile cur = f;
        while (cur != null && !Objects.equals(cur.getId(), root.getId())) {
            cur = find(rows, cur.getFolderId());
            if (cur != null) {
                path.insert(0, cur.getName() + "/");
            }
        }
        return path.toString();
    }
}
