package com.nebula.space.mcp;

import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.exception.BizException;
import com.nebula.common.web.config.JacksonConfig;
import com.nebula.space.dto.me.ReportSaveRequest;
import com.nebula.space.dto.me.TaskSaveRequest;
import com.nebula.space.service.SpaceFocusService;
import com.nebula.space.service.SpaceMeetingService;
import com.nebula.space.service.SpaceNoteService;
import com.nebula.space.service.SpaceOverviewService;
import com.nebula.space.service.SpaceReportService;
import com.nebula.space.service.SpaceSearchService;
import com.nebula.space.service.SpaceTaskListService;
import com.nebula.space.service.SpaceTaskService;
import com.nebula.space.vo.me.PersonVO;
import com.nebula.space.vo.me.ReportVO;
import com.nebula.space.vo.me.SearchResultVO;
import com.nebula.space.vo.me.TaskCompleteVO;
import com.nebula.space.vo.me.TaskVO;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpaceMcpToolsTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 14);

    private SpaceTaskService taskService;
    private SpaceReportService reportService;
    private SpaceSearchService searchService;
    private SpaceMcpScopeChecker scopeChecker;
    private SpaceMcpTools tools;

    @BeforeEach
    void setUp() {
        taskService = mock(SpaceTaskService.class);
        reportService = mock(SpaceReportService.class);
        searchService = mock(SpaceSearchService.class);
        scopeChecker = mock(SpaceMcpScopeChecker.class);
        when(scopeChecker.granted(anyString())).thenReturn(true);
        JsonMapper jsonMapper = JsonMapper.builder().addModule(new JacksonConfig().nebulaJacksonModule()).build();
        tools = new SpaceMcpTools(mock(SpaceOverviewService.class), taskService, mock(SpaceTaskListService.class),
                mock(SpaceNoteService.class), mock(SpaceMeetingService.class), mock(SpaceFocusService.class),
                reportService, searchService, scopeChecker, jsonMapper,
                Validation.buildDefaultValidatorFactory().getValidator());
    }

    private CallToolResult call(String name, Map<String, Object> args) {
        SyncToolSpecification spec = tools.specifications().stream()
                .filter(s -> s.tool().name().equals(name))
                .findFirst()
                .orElseThrow();
        return spec.callHandler().apply(McpTransportContext.EMPTY, new McpSchema.CallToolRequest(name, args, null));
    }

    private static String text(CallToolResult result) {
        return ((McpSchema.TextContent) result.content().get(0)).text();
    }

    private static ReportVO report(String content) {
        ReportVO vo = new ReportVO();
        vo.setType("day");
        vo.setDate(DAY);
        vo.setContent(content);
        vo.setUpdateTime(LocalDateTime.of(2026, 10, 14, 18, 0));
        return vo;
    }

    private static TaskVO task(long id) {
        TaskVO vo = new TaskVO();
        vo.setId(id);
        vo.setTitle("补记");
        return vo;
    }

    @Test
    void toolWithoutScopeIsRefused() {
        when(scopeChecker.granted(SpaceMcpScopes.WRITE)).thenReturn(false);

        CallToolResult result = call("create_task", Map.of("title", "写周报"));

        assertTrue(result.isError());
        assertTrue(text(result).contains(SpaceMcpScopes.WRITE));
        verify(taskService, never()).create(any());
    }

    @Test
    void saveReportKeepsExistingContentUnlessOverwrite() {
        when(reportService.get("day", DAY)).thenReturn(report("### 完成\n1. 手写的一条"));

        CallToolResult result = call("save_report", Map.of("type", "day", "date", "2026-10-14", "content", "### 完成\n1. AI 写的"));

        assertTrue(result.isError());
        assertTrue(text(result).contains("手写的一条"));
        verify(reportService, never()).save(anyString(), any(), any());
    }

    @Test
    void saveReportOverwritesWhenAsked() {
        when(reportService.get("day", DAY)).thenReturn(report("旧的"));
        when(reportService.save(eq("day"), eq(DAY), any())).thenReturn(report("合并后"));

        CallToolResult result = call("save_report",
                Map.of("type", "day", "date", "2026-10-14", "content", "合并后", "overwrite", true));

        assertFalse(result.isError());
        ArgumentCaptor<ReportSaveRequest> captor = ArgumentCaptor.forClass(ReportSaveRequest.class);
        verify(reportService).save(eq("day"), eq(DAY), captor.capture());
        assertEquals("合并后", captor.getValue().getContent());
    }

    @Test
    void saveReportCreatesWhenAbsent() {
        when(reportService.save(eq("day"), eq(DAY), any())).thenReturn(report("新的"));

        CallToolResult result = call("save_report", Map.of("type", "day", "date", "2026-10-14", "content", "新的"));

        assertFalse(result.isError());
        assertTrue(text(result).contains("\"date\":\"2026-10-14\""));
    }

    @Test
    void saveReportRejectsBlankContentInsteadOfDeleting() {
        CallToolResult result = call("save_report", Map.of("type", "day", "date", "2026-10-14", "content", "  "));

        assertTrue(result.isError());
        verify(reportService, never()).save(anyString(), any(), any());
    }

    @Test
    void createTaskWithDoneCompletesIt() {
        when(taskService.create(any())).thenReturn(task(7L));
        TaskVO done = task(7L);
        done.setDone(true);
        when(taskService.complete(7L, true)).thenReturn(new TaskCompleteVO(done, null));

        CallToolResult result = call("create_task", Map.of("title", "补记", "done", true));

        assertFalse(result.isError());
        verify(taskService).complete(7L, true);
        // ID 与接口返回一致，按字符串给出
        assertTrue(text(result).contains("\"id\":\"7\""));
    }

    @Test
    void updateTaskOnlyCarriesGivenFields() {
        when(taskService.update(eq(5L), any())).thenReturn(task(5L));

        CallToolResult result = call("update_task", Map.of("id", "5", "dueDate", "2026-10-15"));

        assertFalse(result.isError());
        ArgumentCaptor<TaskSaveRequest> captor = ArgumentCaptor.forClass(TaskSaveRequest.class);
        verify(taskService).update(eq(5L), captor.capture());
        assertTrue(captor.getValue().has("dueDate"));
        assertFalse(captor.getValue().has("title"));
        assertEquals(LocalDate.of(2026, 10, 15), captor.getValue().getDueDate());
    }

    @Test
    void beanValidationFailureBecomesToolError() {
        CallToolResult result = call("create_task", Map.of("title", "x", "dueTime", "25:00"));

        assertTrue(result.isError());
        assertTrue(text(result).contains("dueTime"));
        verify(taskService, never()).create(any());
    }

    @Test
    void bizExceptionBecomesToolError() {
        when(taskService.update(eq(9L), any())).thenThrow(new BizException(HttpStatus.NOT_FOUND, "任务不存在"));

        CallToolResult result = call("update_task", Map.of("id", "9", "title", "改名"));

        assertTrue(result.isError());
        assertEquals("任务不存在", text(result));
    }

    @Test
    void searchOnlyReturnsWorkModules() {
        SearchResultVO found = new SearchResultVO();
        found.setPeople(List.of(new PersonVO()));
        when(searchService.search("周报")).thenReturn(found);

        CallToolResult result = call("search", Map.of("q", "周报"));

        assertFalse(result.isError());
        assertTrue(text(result).contains("\"tasks\""));
        assertFalse(text(result).contains("people"));
    }

    @Test
    void getDayAsksForTasksFromThatDayOn() {
        call("get_day", Map.of("date", "2026-10-14"));

        verify(taskService).listForDays(DAY, DAY.plusDays(1), DAY, DAY.atStartOfDay());
        verify(reportService).get("day", DAY);
    }
}
