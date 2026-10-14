package com.nebula.space.mcp;

import com.nebula.common.core.context.UserContext;
import com.nebula.common.web.config.JacksonConfig;
import com.nebula.space.service.SpaceFocusService;
import com.nebula.space.service.SpaceMeetingService;
import com.nebula.space.service.SpaceNoteService;
import com.nebula.space.service.SpaceOverviewService;
import com.nebula.space.service.SpaceReportService;
import com.nebula.space.service.SpaceSearchService;
import com.nebula.space.service.SpaceTaskListService;
import com.nebula.space.service.SpaceTaskService;
import com.nebula.space.vo.me.ReportVO;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 走 SDK 的完整链路：JSON-RPC 报文进，JSON-RPC 报文出
 */
class SpaceMcpServerTest {

    private static final JsonMapper JSON = JsonMapper.shared();

    private SpaceReportService reportService;
    private SpaceMcpTransport transport;
    private McpStatelessSyncServer server;

    @BeforeEach
    void setUp() {
        reportService = mock(SpaceReportService.class);
        SpaceMcpScopeChecker scopeChecker = mock(SpaceMcpScopeChecker.class);
        when(scopeChecker.granted(anyString())).thenReturn(true);
        SpaceMcpTools tools = new SpaceMcpTools(mock(SpaceOverviewService.class), mock(SpaceTaskService.class),
                mock(SpaceTaskListService.class), mock(SpaceNoteService.class), mock(SpaceMeetingService.class),
                mock(SpaceFocusService.class), reportService, mock(SpaceSearchService.class), scopeChecker,
                JsonMapper.builder().addModule(new JacksonConfig().nebulaJacksonModule()).build(),
                Validation.buildDefaultValidatorFactory().getValidator());
        SpaceMcpConfig config = new SpaceMcpConfig();
        transport = config.spaceMcpTransport();
        server = config.spaceMcpServer(transport, tools);
        UserContext.set(42L);
    }

    @AfterEach
    void tearDown() {
        server.close();
        UserContext.clear();
    }

    private JsonNode post(String body) {
        ResponseEntity<String> response = transport.handle(body);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return JSON.readTree(response.getBody());
    }

    private static String callTool(String name, String arguments) {
        return """
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"%s","arguments":%s}}
                """.formatted(name, arguments);
    }

    @Test
    void initializeReturnsServerInfoAndInstructions() {
        JsonNode result = post("""
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",
                 "capabilities":{},"clientInfo":{"name":"test","version":"1"}}}
                """).get("result");

        assertEquals(SpaceMcpConfig.SERVER_NAME, result.get("serverInfo").get("name").asString());
        assertEquals("2025-06-18", result.get("protocolVersion").asString());
        assertTrue(result.get("instructions").asString().contains("get_day"));
        assertTrue(result.get("capabilities").has("tools"));
    }

    @Test
    void notificationIsAccepted() {
        ResponseEntity<String> response = transport.handle("""
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                """);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
    }

    @Test
    void listsAllTools() {
        JsonNode tools = post("""
                {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                """).get("result").get("tools");

        assertEquals(15, tools.size());
        JsonNode getDay = tools.get(0);
        assertEquals("get_day", getDay.get("name").asString());
        assertTrue(getDay.get("annotations").get("readOnlyHint").asBoolean());
    }

    @Test
    void toolRunsOnRequestThreadWithCurrentUser() {
        AtomicReference<Long> seenUser = new AtomicReference<>();
        when(reportService.get("day", LocalDate.of(2026, 10, 14))).thenAnswer(invocation -> {
            seenUser.set(UserContext.getUserId());
            return null;
        });

        JsonNode result = post(callTool("get_report", """
                {"type":"day","date":"2026-10-14"}
                """)).get("result");

        assertEquals(42L, seenUser.get());
        assertFalse(result.get("isError").asBoolean());
        assertTrue(result.get("content").get(0).get("text").asString().contains("还没写过"));
    }

    @Test
    void toolReturnsReportAsJson() {
        ReportVO report = new ReportVO();
        report.setId(123L);
        report.setType("day");
        report.setDate(LocalDate.of(2026, 10, 14));
        report.setContent("### 完成");
        when(reportService.get("day", LocalDate.of(2026, 10, 14))).thenReturn(report);

        String text = post(callTool("get_report", """
                {"type":"day","date":"2026-10-14"}
                """)).get("result").get("content").get(0).get("text").asString();

        assertTrue(text.contains("\"id\":\"123\""));
        assertTrue(text.contains("### 完成"));
    }

    @Test
    void argumentsAreCheckedAgainstSchema() {
        JsonNode result = post(callTool("get_report", """
                {"type":"day","date":"10/14"}
                """)).get("result");

        assertTrue(result.get("isError").asBoolean());
    }

    @Test
    void unknownMethodIsJsonRpcError() {
        JsonNode error = post("""
                {"jsonrpc":"2.0","id":4,"method":"resources/list","params":{}}
                """).get("error");

        assertEquals(-32601, error.get("code").asInt());
    }

    @Test
    void malformedBodyIsParseError() {
        ResponseEntity<String> response = transport.handle("{not json");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("-32700"));
    }
}
