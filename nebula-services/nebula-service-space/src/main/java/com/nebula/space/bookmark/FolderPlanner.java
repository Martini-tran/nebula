package com.nebula.space.bookmark;

import com.nebula.space.entity.SpaceBookmarkFolder;
import com.nebula.space.vo.admin.FolderAiPlanVO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 目录重排方案的校验器：在内存里放一份目录树，按顺序模拟 AI 给的每个操作，
 * 不合法的（引用不存在的目录、移进自己的子目录、同级重名……）直接丢掉，合法的改动内存树并转成前端能执行的操作。
 *
 * <p>给 AI 看的目录用 F1、F2… 编号而不是数据库 ID，免得它编造 ID；方案里新建的目录由 AI 用 N1、N2… 编号。</p>
 */
public final class FolderPlanner {

    static final int NAME_MAX = 50;
    private static final Pattern NEW_KEY = Pattern.compile("N\\d{1,3}");

    /**
     * AI 给出的一个原始操作
     */
    public record RawOp(String op, String key, String folder, String parent, String into, String name, String reason) {
    }

    private static final class Node {
        final String key;
        final Long id;
        String name;
        /** 上级的编号，顶层为 null */
        String parent;
        int bookmarks;
        boolean removed;

        Node(String key, Long id, String name, String parent, int bookmarks) {
            this.key = key;
            this.id = id;
            this.name = name;
            this.parent = parent;
            this.bookmarks = bookmarks;
        }
    }

    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Map<String, List<String>> samples = new HashMap<>();

    /**
     * @param folders 用户的全部目录
     * @param counts  每个目录里直接放着的书签数
     * @param titles  每个目录里的几条书签标题，给 AI 看目录装的是什么
     */
    public FolderPlanner(List<SpaceBookmarkFolder> folders, Map<Long, Integer> counts, Map<Long, List<String>> titles) {
        Map<Long, List<SpaceBookmarkFolder>> byParent = new HashMap<>();
        Map<Long, SpaceBookmarkFolder> byId = new HashMap<>();
        for (SpaceBookmarkFolder f : folders) {
            byId.put(f.getId(), f);
        }
        for (SpaceBookmarkFolder f : folders) {
            // 上级找不到的当顶层，免得整枝丢掉
            long parent = f.getParentId() != null && byId.containsKey(f.getParentId()) ? f.getParentId() : 0L;
            byParent.computeIfAbsent(parent, k -> new ArrayList<>()).add(f);
        }
        Comparator<SpaceBookmarkFolder> order = Comparator
                .comparing((SpaceBookmarkFolder f) -> f.getSortOrder() == null ? 0 : f.getSortOrder())
                .thenComparing(SpaceBookmarkFolder::getId);
        byParent.values().forEach(list -> list.sort(order));
        walk(byParent, 0L, null, counts, titles);
    }

    private void walk(Map<Long, List<SpaceBookmarkFolder>> byParent, long parentId, String parentKey,
                      Map<Long, Integer> counts, Map<Long, List<String>> titles) {
        for (SpaceBookmarkFolder f : byParent.getOrDefault(parentId, List.of())) {
            String key = "F" + (nodes.size() + 1);
            nodes.put(key, new Node(key, f.getId(), f.getName(), parentKey, counts.getOrDefault(f.getId(), 0)));
            samples.put(key, titles.getOrDefault(f.getId(), List.of()));
            walk(byParent, f.getId(), key, counts, titles);
        }
    }

    /**
     * 给 AI 看的目录清单，一行一个：编号 | 完整路径 | 书签数 | 标题示例
     */
    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        for (Node node : nodes.values()) {
            List<String> titles = samples.getOrDefault(node.key, List.of());
            lines.add(node.key + " | " + path(node) + " | " + node.bookmarks + " 条"
                    + (titles.isEmpty() ? "" : " | " + String.join("；", titles)));
        }
        return lines;
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    /**
     * 模拟执行一个操作
     *
     * @return 转换后的操作；不合法返回 null，内存树不变
     */
    public FolderAiPlanVO.Op apply(RawOp raw) {
        if (raw == null || raw.op() == null) {
            return null;
        }
        return switch (raw.op().trim().toLowerCase(Locale.ROOT)) {
            case "create" -> create(raw);
            case "rename" -> rename(raw);
            case "move" -> move(raw);
            case "merge" -> merge(raw);
            default -> null;
        };
    }

    private FolderAiPlanVO.Op create(RawOp raw) {
        String key = raw.key() == null ? "" : raw.key().trim().toUpperCase(Locale.ROOT);
        String name = cleanName(raw.name());
        if (!NEW_KEY.matcher(key).matches() || nodes.containsKey(key) || name == null) {
            return null;
        }
        Node parent = null;
        if (!isRoot(raw.parent())) {
            parent = find(raw.parent());
            if (parent == null) {
                return null;
            }
        }
        String parentKey = parent == null ? null : parent.key;
        if (nameTaken(parentKey, name, null)) {
            return null;
        }
        Node node = new Node(key, null, name, parentKey, 0);
        nodes.put(key, node);
        FolderAiPlanVO.Op op = op("create", raw);
        op.setKey(key);
        op.setParent(ref(parent));
        op.setName(name);
        op.setAfter(path(node));
        return op;
    }

    private FolderAiPlanVO.Op rename(RawOp raw) {
        Node node = find(raw.folder());
        String name = cleanName(raw.name());
        if (node == null || name == null || name.equals(node.name) || nameTaken(node.parent, name, node.key)) {
            return null;
        }
        FolderAiPlanVO.Op op = op("rename", raw);
        op.setFolder(ref(node));
        op.setBefore(path(node));
        node.name = name;
        op.setName(name);
        op.setAfter(path(node));
        return op;
    }

    private FolderAiPlanVO.Op move(RawOp raw) {
        Node node = find(raw.folder());
        // 移到顶层要明确写空字符串，漏写 parent 不当作移到顶层
        if (node == null || raw.parent() == null) {
            return null;
        }
        Node parent = null;
        if (!isRoot(raw.parent())) {
            parent = find(raw.parent());
            // 不能移到自己或自己的子目录下
            if (parent == null || isSelfOrDescendant(parent, node)) {
                return null;
            }
        }
        String parentKey = parent == null ? null : parent.key;
        if (Objects.equals(node.parent, parentKey) || nameTaken(parentKey, node.name, node.key)) {
            return null;
        }
        FolderAiPlanVO.Op op = op("move", raw);
        op.setFolder(ref(node));
        op.setParent(ref(parent));
        op.setBefore(path(node));
        node.parent = parentKey;
        op.setAfter(path(node));
        return op;
    }

    private FolderAiPlanVO.Op merge(RawOp raw) {
        Node node = find(raw.folder());
        Node into = find(raw.into());
        // 并入的目录不能是自己或自己的子目录：删掉自己时它也没了
        if (node == null || into == null || isSelfOrDescendant(into, node)) {
            return null;
        }
        List<Node> children = childrenOf(node.key);
        for (Node child : children) {
            if (nameTaken(into.key, child.name, node.key)) {
                return null;
            }
        }
        FolderAiPlanVO.Op op = op("merge", raw);
        op.setFolder(ref(node));
        op.setInto(ref(into));
        op.setBefore(path(node));
        op.setAfter(path(into));
        op.setBookmarkCount(node.bookmarks);
        children.forEach(child -> child.parent = into.key);
        into.bookmarks += node.bookmarks;
        node.removed = true;
        return op;
    }

    // ----------------------------------------------------------------- 工具

    private static FolderAiPlanVO.Op op(String type, RawOp raw) {
        FolderAiPlanVO.Op op = new FolderAiPlanVO.Op();
        op.setOp(type);
        String reason = raw.reason() == null ? "" : raw.reason().trim();
        op.setReason(reason.length() > 100 ? reason.substring(0, 100) : reason);
        return op;
    }

    /** 已有目录用 ID，方案里新建的用编号，顶层为 "0" */
    private static String ref(Node node) {
        if (node == null) {
            return "0";
        }
        return node.id != null ? String.valueOf(node.id) : node.key;
    }

    private Node find(String ref) {
        if (ref == null) {
            return null;
        }
        Node node = nodes.get(ref.trim().toUpperCase(Locale.ROOT));
        return node == null || node.removed ? null : node;
    }

    private static boolean isRoot(String ref) {
        if (ref == null) {
            return true;
        }
        String value = ref.trim();
        return value.isEmpty() || "0".equals(value) || "root".equalsIgnoreCase(value);
    }

    private boolean isSelfOrDescendant(Node candidate, Node ancestor) {
        int guard = 0;
        for (Node cur = candidate; cur != null && guard++ <= nodes.size(); cur = cur.parent == null ? null : nodes.get(cur.parent)) {
            if (cur == ancestor) {
                return true;
            }
        }
        return false;
    }

    private List<Node> childrenOf(String key) {
        return nodes.values().stream().filter(n -> !n.removed && Objects.equals(n.parent, key)).toList();
    }

    /** 同一上级下有没有别的目录叫这个名字（与数据库排序规则一致，不分大小写） */
    private boolean nameTaken(String parentKey, String name, String excludeKey) {
        return nodes.values().stream().anyMatch(n -> !n.removed
                && Objects.equals(n.parent, parentKey)
                && !n.key.equals(excludeKey)
                && n.name.equalsIgnoreCase(name));
    }

    private String path(Node node) {
        List<String> names = new ArrayList<>();
        int guard = 0;
        for (Node cur = node; cur != null && guard++ <= nodes.size(); cur = cur.parent == null ? null : nodes.get(cur.parent)) {
            names.add(0, cur.name);
        }
        return String.join(" / ", names);
    }

    /** 目录名去首尾空白、压缩空白；空的、太长的、带斜杠的（路径分隔符）不要，返回 null */
    public static String cleanName(String name) {
        if (name == null) {
            return null;
        }
        String value = name.trim().replaceAll("\\s+", " ");
        if (value.isEmpty() || value.length() > NAME_MAX || value.contains("/")) {
            return null;
        }
        return value;
    }
}
