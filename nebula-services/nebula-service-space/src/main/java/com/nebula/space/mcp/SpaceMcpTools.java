package com.nebula.space.mcp;

import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.me.FocusSessionQuery;
import com.nebula.space.dto.me.MeetingQuery;
import com.nebula.space.dto.me.NoteQuery;
import com.nebula.space.dto.me.NoteSaveRequest;
import com.nebula.space.dto.me.ReportQuery;
import com.nebula.space.dto.me.ReportSaveRequest;
import com.nebula.space.dto.me.TaskQuery;
import com.nebula.space.dto.me.TaskSaveRequest;
import com.nebula.space.service.SpaceFocusService;
import com.nebula.space.service.SpaceMeetingService;
import com.nebula.space.service.SpaceNoteService;
import com.nebula.space.service.SpaceOverviewService;
import com.nebula.space.service.SpaceReportService;
import com.nebula.space.service.SpaceSearchService;
import com.nebula.space.service.SpaceTaskListService;
import com.nebula.space.service.SpaceTaskService;
import com.nebula.space.vo.me.ReportVO;
import com.nebula.space.vo.me.SearchResultVO;
import com.nebula.space.vo.me.TaskVO;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 个人空间的 MCP 工具：读材料、写日报周报、补记任务和笔记
 *
 * <p>工具直接调 service，数据按 UserContext（key 的所属用户）隔离。每个工具标明所需授权范围，
 * 调用时先由 {@link SpaceMcpScopeChecker} 校验。没有删除类工具，删除留给用户在页面上做。</p>
 *
 * <p>结果用与接口返回同一个 JsonMapper 序列化：ID 是字符串、时间是「yyyy-MM-dd HH:mm:ss」，和页面一致。
 * 业务校验失败（BizException、参数不对）以 isError 结果回给模型，让它自己改正后重试。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpaceMcpTools {

    /**
     * 初始化时下发给客户端的说明，Claude Code 会把它带进模型的上下文
     */
    static final String INSTRUCTIONS = """
            nebula 个人空间（任务、会议、随手记、专注、日报周报）。读写的都是这把 API Key 所属用户自己的数据。

            - 日期一律 yyyy-MM-dd，按用户所在时区的「今天」传，不要依赖服务器时间；ID 是字符串，原样传回。
            - 写日报：先 get_day 取当天材料（这一天已保存的日报也在里面），结合用户提供的信息整理，再 save_report 保存。
              已有日报不会被直接覆盖：要把已有内容与新内容合并后带 overwrite=true 再保存，不要丢掉用户手写的部分。
            - 日报正文是 Markdown，沿用页面「插入记录」的结构，没有内容的段落不写：
              ### 完成（有序列表；做这件事的专注时长可写在后面，如「（专注 1 小时 20 分）」）
              ### 会议（每条「会议名：决议」）
              ### 周X计划（有序列表，X 是下一天是周几；标题里必须有「计划」二字，周回顾靠它对照做到没有）
              ### 没做完（逾期的注明「逾期 N 天」）
            - 写周报：get_week_review 取材料，save_report 的 type=week、date=这一周的第一天；
              结构为 本周完成 / 会议决议 / 下周计划 / 风险与需要的支持。
            - 今天做完但没记成任务的工作，可以用 create_task 带 done=true 补记（完成时间记为当前时间）。
            - 没有删除类工具；要删除请让用户到页面上操作。
            """;

    private static final List<String> TASK_FIELDS =
            List.of("title", "listId", "dueDate", "dueTime", "priority", "estimateMin", "note");
    private static final List<String> NOTE_CREATE_FIELDS = List.of("content", "tags", "pinned");
    private static final List<String> NOTE_UPDATE_FIELDS = List.of("content", "tags", "pinned", "archived");

    private static final int DEFAULT_NOTE_LIMIT = 50;

    private final SpaceOverviewService overviewService;
    private final SpaceTaskService taskService;
    private final SpaceTaskListService taskListService;
    private final SpaceNoteService noteService;
    private final SpaceMeetingService meetingService;
    private final SpaceFocusService focusService;
    private final SpaceReportService reportService;
    private final SpaceSearchService searchService;
    private final SpaceMcpScopeChecker scopeChecker;
    private final JsonMapper jsonMapper;
    private final Validator validator;

    /**
     * 工具的性质，映射到 MCP 的 ToolAnnotations，客户端据此决定要不要先征得用户同意
     */
    private enum Kind {
        /** 只读 */
        READ,
        /** 新增，不改动已有数据 */
        ADD,
        /** 修改已有数据 */
        CHANGE
    }

    public List<SyncToolSpecification> specifications() {
        return List.of(
                // ---------------------------------------------------------------- 读
                tool("get_day", "一天的工作材料", SpaceMcpScopes.READ, Kind.READ,
                        "取某一天写日报要用的材料：这一天完成的任务、这一天到期和之前逾期没做完的、下一天到期的、"
                                + "这一天的会议（含正文）、专注记录与总分钟数、这一天写的笔记，以及这一天已保存的日报（没写过为 null）。写日报前先调它。",
                        McpInputSchema.object()
                                .date("date", "哪一天")
                                .required("date"),
                        this::getDay),
                tool("get_week_review", "一周的回顾材料", SpaceMcpScopes.READ, Kind.READ,
                        "取写周报要用的材料：这一周与上一周的会议、专注、习惯打卡（可做对比），所有没做完的任务与上一周以来完成的任务，"
                                + "这一周写的笔记，以及上一周保存的周报。",
                        McpInputSchema.object()
                                .date("start", "这一周的第一天（通常是周一）")
                                .required("start"),
                        args -> overviewService.weekReview(args.date("start"))),
                tool("list_tasks", "列出任务", SpaceMcpScopes.READ, Kind.READ,
                        "按视图列出任务。today、plan 视图以服务器日期为准，跨时区时优先用 get_day。",
                        McpInputSchema.object()
                                .oneOf("view", "inbox 收件箱 / today 今天（含过期）/ plan 未来 7 天 / done 已完成 / all 全部（默认）",
                                        "inbox", "today", "plan", "done", "all")
                                .id("listId", "只看某个清单"),
                        this::listTasks),
                tool("list_task_lists", "列出任务清单", SpaceMcpScopes.READ, Kind.READ,
                        "列出任务清单（ID、名称），新建任务时 listId 从这里取。",
                        McpInputSchema.object(),
                        args -> taskListService.list()),
                tool("list_notes", "列出随手记", SpaceMcpScopes.READ, Kind.READ,
                        "按条件列出随手记，最近改过的在前。",
                        McpInputSchema.object()
                                .oneOf("view", "all 未归档（默认）/ temporary 临时 / pinned 长期 / archived 已归档",
                                        "all", "temporary", "pinned", "archived")
                                .string("tag", "按标签名筛选")
                                .string("keyword", "关键词，匹配正文与标签")
                                .date("from", "创建日期起（含）")
                                .date("to", "创建日期止（含）")
                                .integer("limit", "最多返回几条，默认 " + DEFAULT_NOTE_LIMIT, 1, 200),
                        this::listNotes),
                tool("list_meetings", "列出会议", SpaceMcpScopes.READ, Kind.READ,
                        "列出日期范围内的会议（含正文，决议与待办写在正文里）。",
                        McpInputSchema.object()
                                .date("from", "起（含）")
                                .date("to", "止（含）")
                                .required("from", "to"),
                        this::listMeetings),
                tool("list_reports", "列出日报周报", SpaceMcpScopes.READ, Kind.READ,
                        "列出日报周报，新的在前；日期按日报当天 / 周报这一周的第一天筛选。",
                        McpInputSchema.object()
                                .oneOf("type", "day 日报 / week 周报，不传为全部", "day", "week")
                                .date("from", "起（含）")
                                .date("to", "止（含）"),
                        this::listReports),
                tool("get_report", "读一份日报或周报", SpaceMcpScopes.READ, Kind.READ,
                        "读某一天的日报，或某一周的周报（date 为这一周的第一天）。",
                        McpInputSchema.object()
                                .oneOf("type", "day 日报 / week 周报", "day", "week")
                                .date("date", "日报为当天，周报为这一周的第一天")
                                .required("type", "date"),
                        this::getReport),
                tool("search", "搜索", SpaceMcpScopes.READ, Kind.READ,
                        "在任务、清单、随手记、会议、日报周报里搜索候选。语法：t: 只搜任务（n: 随手记、m: 会议、r: 日报周报）、"
                                + "#标签或清单、@某人、is:open / is:done / is:overdue、after:2026-09-20 / before:（含当天）、\"精确短语\"。",
                        McpInputSchema.object()
                                .string("q", "搜索语句")
                                .required("q"),
                        this::search),

                // ---------------------------------------------------------------- 写日报周报
                tool("save_report", "保存日报或周报", SpaceMcpScopes.REPORT_WRITE, Kind.CHANGE,
                        "整份保存日报或周报（Markdown）。这一天 / 这一周已有内容时默认不覆盖，会返回已有内容："
                                + "合并后带 overwrite=true 再保存。正文不能为空。",
                        McpInputSchema.object()
                                .oneOf("type", "day 日报 / week 周报", "day", "week")
                                .date("date", "日报为当天，周报为这一周的第一天")
                                .string("content", "Markdown 正文")
                                .bool("overwrite", "已有内容时是否覆盖，默认 false")
                                .required("type", "date", "content"),
                        this::saveReport),

                // ---------------------------------------------------------------- 写任务、笔记
                tool("create_task", "新建任务", SpaceMcpScopes.WRITE, Kind.ADD,
                        "新建一条任务。补记已经做完的工作时带 done=true，任务会直接标为完成（完成时间记为当前时间）。",
                        taskSchema()
                                .bool("done", "建好后直接标为完成，默认 false")
                                .required("title"),
                        this::createTask),
                tool("update_task", "修改任务", SpaceMcpScopes.WRITE, Kind.CHANGE,
                        "修改任务，只改传了的字段。完成 / 撤销完成用 complete_task。",
                        taskSchema()
                                .id("id", "任务 ID")
                                .required("id"),
                        this::updateTask),
                tool("complete_task", "完成任务", SpaceMcpScopes.WRITE, Kind.CHANGE,
                        "把任务标为完成或撤销完成；重复任务完成时会生成下一次。",
                        McpInputSchema.object()
                                .id("id", "任务 ID")
                                .bool("done", "true 完成（默认）/ false 撤销完成")
                                .required("id"),
                        args -> taskService.complete(args.id("id"), args.bool("done", true))),
                tool("create_note", "新建随手记", SpaceMcpScopes.WRITE, Kind.ADD,
                        "新建一条随手记（Markdown）。带标签或 pinned=true 的是长期笔记，否则是临时笔记，过一段时间自动归档。",
                        McpInputSchema.object()
                                .string("content", "Markdown 正文")
                                .strings("tags", "标签名，最多 20 个")
                                .bool("pinned", "是否长期笔记")
                                .required("content"),
                        this::createNote),
                tool("update_note", "修改随手记", SpaceMcpScopes.WRITE, Kind.CHANGE,
                        "修改随手记，只改传了的字段；content 会整段替换正文。",
                        McpInputSchema.object()
                                .id("id", "笔记 ID")
                                .string("content", "Markdown 正文（整段替换）")
                                .strings("tags", "标签名（整组替换），最多 20 个")
                                .bool("pinned", "是否长期笔记")
                                .bool("archived", "归档 / 从归档恢复")
                                .required("id"),
                        this::updateNote)
        );
    }

    // ---------------------------------------------------------------- 工具实现

    private DayDigest getDay(McpToolArgs args) {
        LocalDate date = args.date("date");
        LocalDate next = date.plusDays(1);
        List<TaskVO> tasks = taskService.listForDays(date, next, date, date.atStartOfDay());

        MeetingQuery meetings = new MeetingQuery();
        meetings.setFrom(date);
        meetings.setTo(date);
        FocusSessionQuery sessions = new FocusSessionQuery();
        sessions.setFrom(date);
        sessions.setTo(date);
        NoteQuery notes = new NoteQuery();
        notes.setFrom(date);
        notes.setTo(date);

        return DayDigest.of(date, reportService.get("day", date), tasks,
                meetingService.list(meetings), focusService.list(sessions), noteService.list(notes));
    }

    private Object listTasks(McpToolArgs args) {
        TaskQuery query = new TaskQuery();
        query.setView(args.string("view"));
        query.setListId(args.optionalId("listId"));
        return taskService.list(query);
    }

    private Object listNotes(McpToolArgs args) {
        NoteQuery query = new NoteQuery();
        query.setView(args.string("view"));
        query.setTag(args.string("tag"));
        query.setKeyword(args.string("keyword"));
        query.setFrom(args.optionalDate("from"));
        query.setTo(args.optionalDate("to"));
        Integer limit = args.optionalInt("limit");
        query.setLimit(limit == null ? DEFAULT_NOTE_LIMIT : limit);
        return noteService.list(query);
    }

    private Object listMeetings(McpToolArgs args) {
        MeetingQuery query = new MeetingQuery();
        query.setFrom(args.date("from"));
        query.setTo(args.date("to"));
        return meetingService.list(query);
    }

    private Object listReports(McpToolArgs args) {
        ReportQuery query = new ReportQuery();
        query.setType(args.string("type"));
        query.setFrom(args.optionalDate("from"));
        query.setTo(args.optionalDate("to"));
        return reportService.list(query);
    }

    private Object getReport(McpToolArgs args) {
        String type = args.requiredString("type");
        LocalDate date = args.date("date");
        ReportVO report = reportService.get(type, date);
        return report != null ? report : reportLabel(type) + "还没写过（" + date + "）";
    }

    /**
     * 搜索服务还会召回稍后读、人物、书签；只读范围对外说明的是任务、笔记、会议、日报周报，这里只回这几块
     */
    private Object search(McpToolArgs args) {
        SearchResultVO result = searchService.search(args.requiredString("q"));
        Map<String, Object> picked = new LinkedHashMap<>();
        picked.put("tasks", result.getTasks());
        picked.put("lists", result.getLists());
        picked.put("notes", result.getNotes());
        picked.put("meetings", result.getMeetings());
        picked.put("reports", result.getReports());
        return picked;
    }

    private Object saveReport(McpToolArgs args) {
        String type = args.requiredString("type");
        LocalDate date = args.date("date");
        // 正文为空在 service 里等于删除，这里不给模型这条路
        String content = args.requiredString("content");
        ReportVO existing = reportService.get(type, date);
        if (existing != null && !args.bool("overwrite", false) && !Objects.equals(existing.getContent(), content)) {
            return error(reportLabel(type) + "（" + date + "）已有内容，最后修改于 " + existing.getUpdateTime()
                    + "，这次没有覆盖。请把下面的已有内容与新内容合并后，带 overwrite=true 重新保存，不要丢掉已有的部分。\n\n"
                    + existing.getContent());
        }
        ReportSaveRequest req = new ReportSaveRequest();
        req.setContent(content);
        validate(req);
        return reportService.save(type, date, req);
    }

    private Object createTask(McpToolArgs args) {
        TaskSaveRequest req = convert(args.pick(TASK_FIELDS), TaskSaveRequest.class);
        validate(req);
        TaskVO task = taskService.create(req);
        if (args.bool("done", false)) {
            return taskService.complete(task.getId(), true).getTask();
        }
        return task;
    }

    private Object updateTask(McpToolArgs args) {
        Long id = args.id("id");
        // 只转请求里出现了的字段：TaskSaveRequest 靠 setter 记下哪些字段要改
        TaskSaveRequest req = convert(args.pick(TASK_FIELDS), TaskSaveRequest.class);
        validate(req);
        return taskService.update(id, req);
    }

    private Object createNote(McpToolArgs args) {
        NoteSaveRequest req = convert(args.pick(NOTE_CREATE_FIELDS), NoteSaveRequest.class);
        validate(req);
        return noteService.create(req);
    }

    private Object updateNote(McpToolArgs args) {
        Long id = args.id("id");
        NoteSaveRequest req = convert(args.pick(NOTE_UPDATE_FIELDS), NoteSaveRequest.class);
        validate(req);
        return noteService.update(id, req);
    }

    // ---------------------------------------------------------------- 内部工具

    private static McpInputSchema taskSchema() {
        return McpInputSchema.object()
                .string("title", "标题，最长 200 字")
                .id("listId", "所属清单 ID，见 list_task_lists")
                .date("dueDate", "截止日，不传为收件箱")
                .time("dueTime", "截止时间，不传为全天")
                .integer("priority", "优先级 0 无 / 1 低 / 2 中 / 3 高", 0, 3)
                .integer("estimateMin", "预估分钟数", 1, 24 * 60)
                .string("note", "备注（Markdown）");
    }

    private SyncToolSpecification tool(String name, String title, String scope, Kind kind, String description,
                                       McpInputSchema inputSchema, Function<McpToolArgs, Object> handler) {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name(name)
                .title(title)
                .description(description + "\n需要授权范围：" + scope)
                .inputSchema(inputSchema.build())
                .annotations(new McpSchema.ToolAnnotations(title, kind == Kind.READ, kind == Kind.CHANGE,
                        kind == Kind.READ, false, null))
                .build();
        return new SyncToolSpecification(tool, (context, request) -> call(scope, request, handler));
    }

    CallToolResult call(String scope, McpSchema.CallToolRequest request, Function<McpToolArgs, Object> handler) {
        if (!scopeChecker.granted(scope)) {
            return error("这把 API Key 没有授权范围 " + scope + "，不能调用 " + request.name()
                    + "。需要的话请用户在后台「API 密钥」里给 key 加上该范围。");
        }
        try {
            Object result = handler.apply(new McpToolArgs(request.arguments()));
            if (result instanceof CallToolResult toolResult) {
                return toolResult;
            }
            String text = result instanceof String s ? s : jsonMapper.writeValueAsString(result);
            return CallToolResult.builder().addTextContent(text).build();
        } catch (BizException | IllegalArgumentException e) {
            return error(e.getMessage());
        } catch (RuntimeException e) {
            log.error("MCP 工具执行失败: tool={}", request.name(), e);
            return error("服务器内部错误，请稍后重试");
        }
    }

    private <T> T convert(Map<String, Object> fields, Class<T> type) {
        try {
            return jsonMapper.convertValue(fields, type);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("参数格式不对: " + e.getMessage());
        }
    }

    private void validate(Object request) {
        Set<ConstraintViolation<Object>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("；")));
        }
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder().isError(true).addTextContent(message).build();
    }

    private static String reportLabel(String type) {
        return "week".equals(type) ? "周报" : "日报";
    }
}
