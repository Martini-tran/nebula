package com.nebula.space.bookmark;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 书签 AI 整理的提示词与回复解析。只管文本，不碰数据库和模型调用
 */
public final class BookmarkAiPrompts {

    public static final String ACTION_FOLDER = "folder";
    public static final String ACTION_TAGS = "tags";
    public static final String ACTION_TITLE = "title";
    public static final String ACTION_DESCRIPTION = "description";
    public static final Set<String> ACTIONS = Set.of(ACTION_FOLDER, ACTION_TAGS, ACTION_TITLE, ACTION_DESCRIPTION);

    public static final String SYSTEM = "你是书签整理助手，帮用户整理浏览器书签。只输出一个 JSON 对象，不要输出任何其他文字。";

    private static final ObjectMapper JSON = new ObjectMapper();

    private BookmarkAiPrompts() {
    }

    /**
     * 交给 AI 的一条书签
     *
     * @param folder 当前目录的完整路径，未分类为「未分类」
     */
    public record Item(String title, String url, String description, String folder, List<String> tags) {
    }

    /**
     * 书签整理（归目录 / 打标签 / 改标题 / 补描述）的提示词
     *
     * @param folderPaths 现有目录的完整路径
     * @param tagNames    现有标签名
     * @param items       待整理的书签，序号从 1 开始
     * @param actions     要做的事
     */
    public static String suggest(List<String> folderPaths, List<String> tagNames, List<Item> items, Set<String> actions) {
        StringBuilder sb = new StringBuilder();
        if (actions.contains(ACTION_FOLDER)) {
            sb.append("现有目录（每行一个完整路径，层级用「 / 」分隔）：\n");
            sb.append(folderPaths.isEmpty() ? "（还没有目录）" : String.join("\n", folderPaths)).append("\n\n");
        }
        if (actions.contains(ACTION_TAGS)) {
            sb.append("现有标签：").append(tagNames.isEmpty() ? "（还没有标签）" : String.join("、", tagNames)).append("\n\n");
        }
        sb.append("待整理的书签（每行一个 JSON，i 是序号）：\n");
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            ObjectNode line = JSON.createObjectNode();
            line.put("i", i + 1);
            line.put("title", clip(item.title(), 120));
            line.put("url", clip(item.url(), 200));
            if (item.description() != null && !item.description().isBlank()) {
                line.put("desc", clip(item.description(), 120));
            }
            if (actions.contains(ACTION_FOLDER)) {
                line.put("folder", item.folder());
            }
            if (actions.contains(ACTION_TAGS) && !item.tags().isEmpty()) {
                line.putPOJO("tags", item.tags());
            }
            sb.append(line).append('\n');
        }

        sb.append("\n要做的事：\n");
        List<String> fields = new ArrayList<>();
        if (actions.contains(ACTION_FOLDER)) {
            sb.append("- folder：给每条书签选最合适的目录，写完整路径。优先用现有目录；现有目录都不合适时可以新建，"
                    + "新目录最好挂在现有目录下、名称简短（2~8 个字），同类书签共用同一个新目录，不要一条书签建一个目录。"
                    + "当前目录已经合适的，原样写回当前目录。每条书签都要写 folder。\n");
            fields.add("\"folder\":\"前端 / 构建工具\"");
        }
        if (actions.contains(ACTION_TAGS)) {
            sb.append("- tags：按网页内容给每条书签 1~3 个标签。优先用现有标签，没有合适的才新建，新标签 2~6 个字。已有的标签不用重复写。"
                    + "「常用」「待读」「收藏」这类表示个人使用情况的标签你无从判断，不要用。\n");
            fields.add("\"tags\":[\"文档\"]");
        }
        if (actions.contains(ACTION_TITLE)) {
            sb.append("- title：标题冗长、带站点口号或「首页」「官网」之类的噪音、或看不出内容时，改成简洁的名称（专有名词保留原文）；"
                    + "标题已经合适的不要输出 title。\n");
            fields.add("\"title\":\"Vite 官方文档\"");
        }
        if (actions.contains(ACTION_DESCRIPTION)) {
            sb.append("- description：只给没有 desc 的书签写，一句话（不超过 40 个字）说明这个网页是什么；已有 desc 的不要输出 description。\n");
            fields.add("\"description\":\"基于原生 ESM 的前端构建工具\"");
        }
        sb.append("\n输出 JSON，格式：{\"items\":[{\"i\":1,").append(String.join(",", fields)).append("}]}\n");
        sb.append("每条书签输出一项，i 与上面的序号对应；只输出上面列出的字段。");
        return sb.toString();
    }

    /**
     * 目录结构重排的提示词
     *
     * @param folderLines {@link FolderPlanner#describe()} 的结果
     * @param hint        用户的额外要求，可空
     */
    public static String plan(List<String> folderLines, String hint) {
        StringBuilder sb = new StringBuilder();
        sb.append("下面是用户的书签目录树，每行：编号 | 完整路径（层级用「 / 」分隔） | 直接放在这个目录里的书签数 | 几条书签标题示例。\n");
        sb.append(String.join("\n", folderLines)).append("\n\n");
        sb.append("请给出重整目录结构的方案：分类清晰、同类合并、层级不超过 3 层、名称简短统一。"
                + "只改确实需要改的地方；结构已经合理的就少改，完全合理时 ops 为空数组。\n");
        if (hint != null && !hint.isBlank()) {
            sb.append("用户的额外要求：").append(hint.trim()).append('\n');
        }
        sb.append("""

                可用的操作（引用已有目录用上面的 F 编号）：
                - {"op":"create","key":"N1","parent":"F2","name":"工程化"}：新建目录。parent 为空字符串表示顶层；key 依次用 N1、N2…，后面的操作可以用它引用这个新目录
                - {"op":"rename","folder":"F3","name":"新名称"}：改名
                - {"op":"move","folder":"F5","parent":"N1"}：把目录连同子目录和书签移到另一个目录下；parent 为空字符串表示移到顶层
                - {"op":"merge","folder":"F7","into":"F3"}：把 F7 里的书签和子目录全部并入 F3，然后删掉 F7
                每个操作带一个 reason 字段，一句话说明理由（不超过 30 个字）。同一级下不能有重名目录。

                输出 JSON，格式：{"summary":"一两句话概括这次调整","ops":[...]}，按执行顺序排列，最多 30 个操作。""");
        return sb.toString();
    }

    /**
     * 从模型回复里取出 JSON 对象：容忍 ```json 代码块和前后多余的文字
     *
     * @return 解析失败返回 null
     */
    public static JsonNode parse(String content) {
        if (content == null) {
            return null;
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonNode node = JSON.readTree(content.substring(start, end + 1));
            return node != null && node.isObject() ? node : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /** 取标量字段的文本（数字也转成文本），没有、为 null 或是数组对象时为 null */
    public static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() || value.isContainerNode() ? null : value.asText();
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) + "…" : trimmed;
    }
}
