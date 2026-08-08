package com.nebula.manager.ai.copilot;

import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.flow.FlowEdgeDefinition;
import com.nebula.common.ai.flow.FlowNodeDefinition;
import com.nebula.common.ai.flow.store.AiFlowDraftMapper;
import com.nebula.common.ai.harness.draft.DraftCommitRequest;
import com.nebula.common.ai.harness.draft.DraftCommitResult;
import com.nebula.manager.service.FlowAdminService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 使用真实 MySQL 验证 CREATE_ONLY 并发裁决和跨表事务回滚。
 *
 * <p>仅在提供 MYSQL_HOST/MYSQL_PORT/MYSQL_DATABASE/MYSQL_USERNAME/MYSQL_PASSWORD 时执行。
 * 所有数据都使用随机业务编码，并在测试结束时精确清理。
 *
 * @author nebula
 */
class ManagerDraftCommitterMySqlTest {

    private JdbcTemplate jdbc;
    private ManagerDraftCommitter committer;
    private String prefix;

    @BeforeEach
    void setUp() {
        String host = requiredEnvironment("MYSQL_HOST");
        String port = requiredEnvironment("MYSQL_PORT");
        String database = requiredEnvironment("MYSQL_DATABASE");
        String username = requiredEnvironment("MYSQL_USERNAME");
        String password = requiredEnvironment("MYSQL_PASSWORD");

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=utf8&useSSL=false"
                + "&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true");
        dataSource.setUsername(username);
        dataSource.setPassword(password);

        this.jdbc = new JdbcTemplate(dataSource);
        this.prefix = "it_harness_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        this.committer = new ManagerDraftCommitter(
                flowAdminService(dataSource), draftMapper(dataSource), new DataSourceTransactionManager(dataSource));
    }

    @AfterEach
    void cleanUp() {
        if (jdbc == null || prefix == null) {
            return;
        }
        jdbc.update("DELETE FROM ai_flow_edge WHERE flow_code LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM ai_flow_node WHERE flow_code LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM ai_flow WHERE flow_code LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM ai_flow_draft WHERE draft_id LIKE ?", prefix + "%");
    }

    @Test
    void concurrentCreateOnlyAllowsExactlyOneCommit() throws Exception {
        String flowCode = prefix + "_flow";
        String draftA = prefix + "_draft_a";
        String draftB = prefix + "_draft_b";
        insertValidatedDraft(draftA, flowCode, 0);
        insertValidatedDraft(draftB, flowCode, 0);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<DraftCommitResult> first = executor.submit(() -> commitAfterBarrier(draftA, flowCode, ready, start));
            Future<DraftCommitResult> second = executor.submit(() -> commitAfterBarrier(draftB, flowCode, ready, start));
            ready.await();
            start.countDown();

            List<DraftCommitResult> results = List.of(first.get(), second.get());
            assertEquals(1, results.stream().filter(DraftCommitResult::committed).count());
            assertEquals(1, results.stream()
                    .filter(result -> "FLOW_CODE_CONFLICT".equals(result.errorCode())).count());
        }

        assertEquals(1, count("SELECT COUNT(*) FROM ai_flow WHERE flow_code = ?", flowCode));
        assertEquals(2, count("SELECT COUNT(*) FROM ai_flow_node WHERE flow_code = ?", flowCode));
        assertEquals(1, count("SELECT COUNT(*) FROM ai_flow_edge WHERE flow_code = ?", flowCode));
        assertEquals(1, count("SELECT COUNT(*) FROM ai_flow_draft WHERE draft_id IN (?, ?) AND status = 'COMMITTED'",
                draftA, draftB));
        assertEquals(1, count("SELECT COUNT(*) FROM ai_flow_draft WHERE draft_id IN (?, ?) AND status = 'BUILDING'",
                draftA, draftB));
    }

    @Test
    void failedDraftCasRollsBackAllOfficialFlowRows() {
        String flowCode = prefix + "_rollback_flow";
        String draftId = prefix + "_rollback_draft";
        insertValidatedDraft(draftId, flowCode, 0);

        DraftCommitResult result = committer.commit(new DraftCommitRequest(
                draftId, 90001L, 1, true, definition(flowCode)));

        assertEquals("DRAFT_CONFLICT", result.errorCode());
        assertEquals(0, count("SELECT COUNT(*) FROM ai_flow WHERE flow_code = ?", flowCode));
        assertEquals(0, count("SELECT COUNT(*) FROM ai_flow_node WHERE flow_code = ?", flowCode));
        assertEquals(0, count("SELECT COUNT(*) FROM ai_flow_edge WHERE flow_code = ?", flowCode));
        assertEquals(1, count("SELECT COUNT(*) FROM ai_flow_draft WHERE draft_id = ? AND status = 'BUILDING'", draftId));
    }

    @Test
    void missingSimulationMarkerRollsBackAllOfficialFlowRows() {
        String flowCode = prefix + "_simulation_gate_flow";
        String draftId = prefix + "_simulation_gate_draft";
        insertValidatedDraft(draftId, flowCode, 0);
        jdbc.update("UPDATE ai_flow_draft SET last_simulated_revision = NULL WHERE draft_id = ?", draftId);

        DraftCommitResult result = committer.commit(new DraftCommitRequest(
                draftId, 90001L, 0, true, definition(flowCode)));

        assertEquals("DRAFT_CONFLICT", result.errorCode());
        assertEquals(0, count("SELECT COUNT(*) FROM ai_flow WHERE flow_code = ?", flowCode));
        assertEquals(0, count("SELECT COUNT(*) FROM ai_flow_node WHERE flow_code = ?", flowCode));
        assertEquals(0, count("SELECT COUNT(*) FROM ai_flow_edge WHERE flow_code = ?", flowCode));
        assertEquals(1, count("SELECT COUNT(*) FROM ai_flow_draft WHERE draft_id = ? AND status = 'BUILDING'", draftId));
    }

    private DraftCommitResult commitAfterBarrier(String draftId,
                                                 String flowCode,
                                                 CountDownLatch ready,
                                                 CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return committer.commit(new DraftCommitRequest(draftId, 90001L, 0, true, definition(flowCode)));
    }

    private void insertValidatedDraft(String draftId, String flowCode, long revision) {
        jdbc.update("""
                INSERT INTO ai_flow_draft
                  (draft_id, user_id, flow_code, engine_type, graph_json, revision,
                   last_validated_revision, last_simulated_revision, status, create_time, update_time)
                VALUES (?, ?, ?, 'DAG', '{}', ?, ?, ?, 'BUILDING', NOW(), NOW())
                """, draftId, 90001L, flowCode, revision, revision, revision);
    }

    private FlowDefinition definition(String flowCode) {
        FlowDefinition definition = new FlowDefinition()
                .setFlowCode(flowCode)
                .setName("Harness MySQL integration")
                .setEngineType("DAG");
        definition.getNodes().add(new FlowNodeDefinition()
                .setNodeCode("start").setNodeType("START").setSortNo(0));
        definition.getNodes().add(new FlowNodeDefinition()
                .setNodeCode("end").setNodeType("END").setSortNo(1));
        definition.getEdges().add(new FlowEdgeDefinition()
                .setFromNode("start").setToNode("end").setSortNo(0));
        return definition;
    }

    private FlowAdminService flowAdminService(DataSource dataSource) {
        JdbcTemplate transactionalJdbc = new JdbcTemplate(dataSource);
        return (FlowAdminService) Proxy.newProxyInstance(
                FlowAdminService.class.getClassLoader(),
                new Class<?>[]{FlowAdminService.class},
                (proxy, method, args) -> {
                    if (!"createOnly".equals(method.getName())) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                    FlowDefinition definition = (FlowDefinition) args[0];
                    String flowCode = definition.getFlowCode();
                    transactionalJdbc.update("""
                            INSERT INTO ai_flow
                              (flow_code, name, version, status, engine_type, max_transitions, max_agent_depth,
                               create_time, update_time)
                            VALUES (?, ?, 1, 1, ?, 100, 8, NOW(), NOW())
                            """, flowCode, definition.getName(), definition.getEngineType());
                    for (FlowNodeDefinition node : definition.getNodes()) {
                        transactionalJdbc.update("""
                                INSERT INTO ai_flow_node
                                  (flow_code, node_code, node_type, output_mode, node_config,
                                   remember_trace, sort_no, create_time, update_time)
                                VALUES (?, ?, ?, 'TEXT', '{}', 0, ?, NOW(), NOW())
                                """, flowCode, node.getNodeCode(), node.getNodeType(), node.getSortNo());
                    }
                    for (FlowEdgeDefinition edge : definition.getEdges()) {
                        transactionalJdbc.update("""
                                INSERT INTO ai_flow_edge
                                  (flow_code, from_node, to_node, sort_no, create_time, update_time)
                                VALUES (?, ?, ?, ?, NOW(), NOW())
                                """, flowCode, edge.getFromNode(), edge.getToNode(), edge.getSortNo());
                    }
                    return flowCode;
                });
    }

    private AiFlowDraftMapper draftMapper(DataSource dataSource) {
        JdbcTemplate transactionalJdbc = new JdbcTemplate(dataSource);
        return (AiFlowDraftMapper) Proxy.newProxyInstance(
                AiFlowDraftMapper.class.getClassLoader(),
                new Class<?>[]{AiFlowDraftMapper.class},
                (proxy, method, args) -> {
                    if (!"markCommitted".equals(method.getName())) {
                        return defaultValue(method.getReturnType());
                    }
                    return transactionalJdbc.update("""
                            UPDATE ai_flow_draft
                               SET status = 'COMMITTED', committed_flow_code = ?, update_time = NOW()
                             WHERE draft_id = ? AND user_id = ? AND revision = ?
                               AND last_validated_revision = revision
                               AND (? = FALSE OR last_simulated_revision = revision)
                               AND status = 'BUILDING'
                            """, args[4], args[0], args[1], args[2], args[3]);
                });
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        return 0;
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        assertNotNull(value);
        return value;
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        Assumptions.assumeTrue(value != null && !value.isBlank(), name + " 未配置，跳过 MySQL 集成测试");
        return value;
    }
}
