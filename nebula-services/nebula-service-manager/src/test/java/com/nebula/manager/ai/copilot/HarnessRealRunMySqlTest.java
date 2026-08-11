package com.nebula.manager.ai.copilot;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.flow.ConditionCompiler;
import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.ai.flow.FlowEngine;
import com.nebula.common.ai.flow.FlowGraphFactory;
import com.nebula.common.ai.flow.FlowNodeDefinition;
import com.nebula.common.ai.flow.NoOpNodeExecutor;
import com.nebula.common.ai.flow.store.AiHarnessConfirmationMapper;
import com.nebula.common.ai.flow.store.AiHarnessOperation;
import com.nebula.common.ai.flow.store.AiHarnessOperationMapper;
import com.nebula.common.ai.harness.realrun.DatabaseDraftConfirmationStore;
import com.nebula.common.ai.harness.realrun.DatabaseHarnessOperationStore;
import com.nebula.common.ai.harness.realrun.DraftConfirmationRequest;
import com.nebula.common.ai.harness.realrun.DraftRunRequest;
import com.nebula.common.ai.harness.realrun.HarnessOperationRequest;
import com.nebula.common.ai.harness.realrun.OperationAuthorization;
import com.nebula.common.ai.orchestration.DagOrchestrator;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用真实 MySQL 验证真实试跑授权、幂等和隔离执行的不变量。 */
class HarnessRealRunMySqlTest {

    private static final Long USER_ID = 91001L;
    private static final String ACTION = "REAL_RUN";

    private JdbcTemplate jdbc;
    private DatabaseDraftConfirmationStore confirmationStore;
    private DatabaseHarnessOperationStore operationStore;
    private AiHarnessOperationMapper operationMapper;
    private TransactionTemplate transaction;
    private String prefix;

    @BeforeEach
    void setUp() {
        DataSource dataSource = dataSourceFromEnvironment();
        applyMigration(dataSource);
        this.jdbc = new JdbcTemplate(dataSource);
        this.prefix = "it_real_run_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment(
                "harness-real-run-test", new SpringManagedTransactionFactory(), dataSource));
        configuration.addMapper(AiHarnessConfirmationMapper.class);
        configuration.addMapper(AiHarnessOperationMapper.class);
        SqlSessionFactory sessionFactory = new MybatisSqlSessionFactoryBuilder().build(configuration);
        SqlSessionTemplate sessionTemplate = new SqlSessionTemplate(sessionFactory);
        AiHarnessConfirmationMapper confirmationMapper =
                sessionTemplate.getMapper(AiHarnessConfirmationMapper.class);
        this.operationMapper = sessionTemplate.getMapper(AiHarnessOperationMapper.class);
        this.confirmationStore = new DatabaseDraftConfirmationStore(confirmationMapper);
        this.operationStore = new DatabaseHarnessOperationStore(
                confirmationMapper, operationMapper, new ObjectMapper());
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @AfterEach
    void cleanUp() {
        if (jdbc == null || prefix == null) return;
        jdbc.update("DELETE FROM ai_harness_operation WHERE draft_id LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM ai_harness_confirmation WHERE draft_id LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM ai_flow_edge WHERE flow_code LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM ai_flow_node WHERE flow_code LIKE ?", prefix + "%");
        jdbc.update("DELETE FROM ai_flow WHERE flow_code LIKE ?", prefix + "%");
    }

    @Test
    void token明文不落库且输入摘要不匹配时零写入() {
        String token = "token-" + UUID.randomUUID();
        String draftId = prefix + "_digest";
        String digest = sha256("canonical-input-a");
        String tokenHash = createConfirmedAuthorization(draftId, 4, digest, token);

        String storedHash = jdbc.queryForObject(
                "SELECT token_hash FROM ai_harness_confirmation WHERE draft_id = ?", String.class, draftId);
        assertNotNull(storedHash);
        assertEquals(tokenHash, storedHash);
        assertNotEquals(token, storedHash);
        assertEquals(0, count("SELECT COUNT(*) FROM ai_harness_confirmation WHERE token_hash = ?", token));

        OperationAuthorization authorization = authorize(new HarnessOperationRequest(
                prefix + "_op_digest", ACTION, draftId, 4, USER_ID, "session-a",
                sha256("canonical-input-b")), tokenHash);
        assertFalse(authorization.authorized());
        assertEquals(0, count("SELECT COUNT(*) FROM ai_harness_operation WHERE draft_id = ?", draftId));
    }

    @Test
    void 并发授权和并发认领都只能成功一次且失败后保留幂等键() throws Exception {
        String draftId = prefix + "_concurrent";
        String digest = sha256("same-input");
        String tokenHash = createConfirmedAuthorization(draftId, 7, digest, "token-" + UUID.randomUUID());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<OperationAuthorization> authorizations;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<OperationAuthorization> first = executor.submit(() -> authorizeAfterBarrier(
                    prefix + "_op_a", draftId, digest, tokenHash, ready, start));
            Future<OperationAuthorization> second = executor.submit(() -> authorizeAfterBarrier(
                    prefix + "_op_b", draftId, digest, tokenHash, ready, start));
            ready.await();
            start.countDown();
            authorizations = List.of(first.get(), second.get());
        }

        assertEquals(1, authorizations.stream().filter(OperationAuthorization::authorized).count());
        assertEquals(1, count("SELECT COUNT(*) FROM ai_harness_operation WHERE draft_id = ?", draftId));
        String operationId = authorizations.stream()
                .filter(OperationAuthorization::authorized)
                .map(value -> value.operation().operationId())
                .findFirst()
                .orElseThrow();

        CountDownLatch claimStart = new CountDownLatch(1);
        List<Boolean> claims;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> claimAfterBarrier(operationId, claimStart));
            Future<Boolean> second = executor.submit(() -> claimAfterBarrier(operationId, claimStart));
            claimStart.countDown();
            claims = List.of(first.get(), second.get());
        }
        assertEquals(1, claims.stream().filter(Boolean::booleanValue).count());
        assertTrue(operationStore.markFailed(operationId, "EXPECTED_FAILURE", "integration failure"));

        AiHarnessOperation duplicate = new AiHarnessOperation();
        duplicate.setOperationId(prefix + "_op_duplicate");
        duplicate.setAction(ACTION);
        duplicate.setDraftId(draftId);
        duplicate.setDraftRevision(7L);
        duplicate.setUserId(USER_ID);
        duplicate.setInputDigest(digest);
        assertEquals(0, operationMapper.insertOperation(duplicate));
        assertEquals(1, count("SELECT COUNT(*) FROM ai_harness_operation WHERE draft_id = ?", draftId));
        assertEquals("FAILED", jdbc.queryForObject(
                "SELECT status FROM ai_harness_operation WHERE operation_id = ?", String.class, operationId));
    }

    @Test
    void 失败和未知状态可在同一版本反复授权且复用幂等行() {
        String draftId = prefix + "_retry";
        String firstDigest = sha256("first-input");
        String firstTokenHash = createConfirmedAuthorization(
                draftId, 8, firstDigest, "token-" + UUID.randomUUID());
        OperationAuthorization first = authorize(new HarnessOperationRequest(
                prefix + "_op_retry", ACTION, draftId, 8, USER_ID, "session-a", firstDigest),
                firstTokenHash);
        assertTrue(first.authorized());
        String operationId = first.operation().operationId();
        assertTrue(operationStore.claim(operationId));
        assertTrue(operationStore.markFailed(operationId, "EXPECTED_FAILURE", "first failure"));

        String retryDigest = sha256("retry-input");
        String retryTokenHash = createConfirmedAuthorization(
                draftId, 8, retryDigest, "token-" + UUID.randomUUID());
        OperationAuthorization retry = authorize(new HarnessOperationRequest(
                prefix + "_ignored_new_id", ACTION, draftId, 8, USER_ID, "session-b", retryDigest),
                retryTokenHash);

        assertTrue(retry.authorized());
        assertEquals(operationId, retry.operation().operationId());
        assertEquals("PENDING", retry.operation().status().name());
        assertEquals("session-b", retry.operation().sessionId());
        assertEquals(retryDigest, retry.operation().inputDigest());
        assertNull(retry.operation().errorCode());
        assertNull(retry.operation().startedAt());
        assertEquals(1, count("SELECT COUNT(*) FROM ai_harness_operation WHERE draft_id = ?", draftId));

        assertTrue(operationStore.claim(operationId));
        jdbc.update("UPDATE ai_harness_operation SET heartbeat_at = DATE_SUB(NOW(), INTERVAL 1 HOUR) "
                + "WHERE operation_id = ?", operationId);
        assertEquals(1, operationStore.markUnknownStale(LocalDateTime.now().minusMinutes(5)));

        String thirdDigest = sha256("third-input");
        String thirdTokenHash = createConfirmedAuthorization(
                draftId, 8, thirdDigest, "token-" + UUID.randomUUID());
        OperationAuthorization third = authorize(new HarnessOperationRequest(
                prefix + "_another_ignored_id", ACTION, draftId, 8, USER_ID, "session-c", thirdDigest),
                thirdTokenHash);

        assertTrue(third.authorized());
        assertEquals(operationId, third.operation().operationId());
        assertEquals("PENDING", third.operation().status().name());
        assertEquals(thirdDigest, third.operation().inputDigest());
        assertEquals(1, count("SELECT COUNT(*) FROM ai_harness_operation WHERE draft_id = ?", draftId));
    }

    @Test
    void 失败详情为空也能过期且幂等行永久保留() {
        String operationId = prefix + "_op_expire";
        jdbc.update("""
                INSERT INTO ai_harness_operation
                  (operation_id, action, draft_id, draft_revision, user_id, input_digest,
                   status, finished_at, create_time, update_time)
                VALUES (?, 'REAL_RUN', ?, 9, ?, ?, 'FAILED', DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), NOW())
                """, operationId, prefix + "_expire", USER_ID, sha256("expire-input"));

        assertEquals(1, operationStore.expireResultDetails(LocalDateTime.now().minusDays(1)));
        assertEquals("操作详情已过保留期", jdbc.queryForObject(
                "SELECT error_message FROM ai_harness_operation WHERE operation_id = ?", String.class, operationId));
        assertEquals(1, count("SELECT COUNT(*) FROM ai_harness_operation WHERE operation_id = ?", operationId));
    }

    @Test
    void 草稿隔离执行不写正式流程三表() {
        String flowCode = prefix + "_uncommitted";
        FlowDefinition definition = new FlowDefinition().setFlowCode(flowCode).setEngineType("DAG");
        definition.getNodes().add(new FlowNodeDefinition()
                .setNodeCode("start").setNodeType(NoOpNodeExecutor.TYPE_START).setSortNo(0));
        definition.getNodes().add(new FlowNodeDefinition()
                .setNodeCode("end").setNodeType(NoOpNodeExecutor.TYPE_END).setSortNo(1));
        definition.getEdges().add(new com.nebula.common.ai.flow.FlowEdgeDefinition()
                .setFromNode("start").setToNode("end").setSortNo(0));
        FlowGraphFactory graphFactory = new FlowGraphFactory(List.of(
                new NoOpNodeExecutor(NoOpNodeExecutor.TYPE_START),
                new NoOpNodeExecutor(NoOpNodeExecutor.TYPE_END)), new ConditionCompiler());
        FlowEngine flowEngine = new FlowEngine(
                code -> { throw new AssertionError("草稿试跑不应查询正式流程仓储"); },
                graphFactory,
                new DagOrchestrator());
        ManagerDraftRunner runner = new ManagerDraftRunner(flowEngine);

        assertEquals(0, officialRows(flowCode));
        runner.run(new DraftRunRequest(
                prefix + "_operation", definition, Map.of("question", "允许提供"),
                String.valueOf(USER_ID), "session-a"));
        assertEquals(0, officialRows(flowCode));
        assertEquals(flowCode, definition.getFlowCode());
    }

    private OperationAuthorization authorizeAfterBarrier(String operationId,
                                                          String draftId,
                                                          String digest,
                                                          String tokenHash,
                                                          CountDownLatch ready,
                                                          CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return authorize(new HarnessOperationRequest(
                operationId, ACTION, draftId, 7, USER_ID, "session-a", digest), tokenHash);
    }

    private boolean claimAfterBarrier(String operationId, CountDownLatch start) throws InterruptedException {
        start.await();
        return operationStore.claim(operationId);
    }

    private OperationAuthorization authorize(HarnessOperationRequest request, String tokenHash) {
        return transaction.execute(status -> operationStore.authorizeAndCreateOrRetry(tokenHash, request));
    }

    private String createConfirmedAuthorization(String draftId, long revision, String digest, String token) {
        String confirmationId = prefix + "_cfm_" + revision;
        confirmationStore.createOrRefresh(new DraftConfirmationRequest(
                confirmationId, ACTION, draftId, revision, USER_ID, "session-a", digest,
                LocalDateTime.now().plusMinutes(5)));
        String tokenHash = sha256(token);
        assertTrue(confirmationStore.confirm(confirmationId, USER_ID, tokenHash));
        return tokenHash;
    }

    private int officialRows(String flowCode) {
        return count("SELECT COUNT(*) FROM ai_flow WHERE flow_code = ?", flowCode)
                + count("SELECT COUNT(*) FROM ai_flow_node WHERE flow_code = ?", flowCode)
                + count("SELECT COUNT(*) FROM ai_flow_edge WHERE flow_code = ?", flowCode);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        assertNotNull(value);
        return value;
    }

    private DataSource dataSourceFromEnvironment() {
        String host = requiredEnvironment("MYSQL_HOST");
        String port = requiredEnvironment("MYSQL_PORT");
        String database = requiredEnvironment("MYSQL_DATABASE");
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=utf8&useSSL=false"
                + "&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true");
        dataSource.setUsername(requiredEnvironment("MYSQL_USERNAME"));
        dataSource.setPassword(requiredEnvironment("MYSQL_PASSWORD"));
        return dataSource;
    }

    private void applyMigration(DataSource dataSource) {
        Path migration = findRepositoryRoot().resolve("script/V20260808__create_ai_harness_real_run.sql");
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new FileSystemResource(migration));
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.execute(dataSource);
    }

    private Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("script/V20260808__create_ai_harness_real_run.sql"))) {
            current = current.getParent();
        }
        if (current == null) throw new IllegalStateException("无法定位仓库根目录");
        return current;
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        Assumptions.assumeTrue(value != null && !value.isBlank(), name + " 未配置，跳过 MySQL 集成测试");
        return value;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
