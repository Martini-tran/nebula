package com.nebula.common.ai.harness.realrun;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.harness.config.HarnessRealRunProperties;
import com.nebula.common.ai.harness.draft.DraftAccess;
import com.nebula.common.ai.harness.draft.DraftIssue;
import com.nebula.common.ai.harness.draft.DraftStatus;
import com.nebula.common.ai.harness.draft.DraftStore;
import com.nebula.common.ai.harness.draft.FlowDefinitionCodec;
import com.nebula.common.ai.harness.draft.FlowDraft;
import com.nebula.common.ai.harness.simulate.SimulationInputValidator;
import com.nebula.common.ai.harness.validate.DraftValidator;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 草稿真实试跑应用服务：统一处理访问控制、模拟门禁、双阶段确认、operation 幂等和异步执行。
 */
@Slf4j
public class DraftRealRunService {

    public static final String ACTION = "REAL_RUN";

    private final DraftStore draftStore;
    private final DraftConfirmationStore confirmationStore;
    private final HarnessOperationStore operationStore;
    private final DraftRunner runner;
    private final DraftValidator validator;
    private final SimulationInputValidator inputValidator;
    private final FlowDefinitionCodec codec;
    private final HarnessRealRunProperties properties;
    private final ExecutorService operationExecutor;
    private final ScheduledExecutorService heartbeatExecutor;
    private final RealRunSecurity security;
    private final SafeOperationSummary safeSummary;

    public DraftRealRunService(DraftStore draftStore,
                               DraftConfirmationStore confirmationStore,
                               HarnessOperationStore operationStore,
                               DraftRunner runner,
                               DraftValidator validator,
                               SimulationInputValidator inputValidator,
                               FlowDefinitionCodec codec,
                               ObjectMapper objectMapper,
                               HarnessRealRunProperties properties,
                               ExecutorService operationExecutor,
                               ScheduledExecutorService heartbeatExecutor) {
        this.draftStore = draftStore;
        this.confirmationStore = confirmationStore;
        this.operationStore = operationStore;
        this.runner = runner;
        this.validator = validator;
        this.inputValidator = inputValidator;
        this.codec = codec;
        this.properties = properties;
        this.operationExecutor = operationExecutor;
        this.heartbeatExecutor = heartbeatExecutor;
        this.security = new RealRunSecurity(objectMapper);
        this.safeSummary = new SafeOperationSummary(objectMapper, properties);
    }

    public RealRunResult realRun(DraftAccess access,
                                 String draftId,
                                 long expectedRevision,
                                 Map<String, Object> initialInput,
                                 String confirmationToken) {
        List<DraftIssue> inputIssues = inputValidator.validateForRealRun(initialInput);
        if (!inputIssues.isEmpty()) {
            return RealRunResult.failure(inputIssues);
        }
        FlowDraft draft = loadAuthorized(access, draftId);
        RealRunResult precondition = checkPreconditions(draft, draftId, expectedRevision);
        if (precondition != null) {
            return precondition;
        }
        List<DraftIssue> issues = validator.validate(draft);
        if (issues.stream().anyMatch(issue -> "ERROR".equals(issue.level()))) {
            return RealRunResult.failure(issues);
        }

        String inputDigest = security.inputDigest(initialInput);
        HarnessOperation existing = operationStore.findByKey(ACTION, draftId, expectedRevision, access.userId());
        if (existing != null) {
            if (existing.status() == HarnessOperationStatus.PENDING
                    && !Objects.equals(existing.inputDigest(), inputDigest)) {
                return RealRunResult.failure("OPERATION_INPUT_MISMATCH",
                        "已有待执行操作绑定了不同的 initialInput",
                        "使用原确认输入恢复，或查询已有 operation 状态");
            }
            submitIfPending(existing, draft, initialInput);
            return operationResult(current(existing));
        }
        if (confirmationToken == null || confirmationToken.isBlank()) {
            return confirmationRequired(access, draft, inputDigest, issues);
        }

        HarnessOperationRequest request = new HarnessOperationRequest(
                "op_" + randomId(), ACTION, draftId, expectedRevision,
                access.userId(), access.sessionId(), inputDigest);
        OperationAuthorization authorization = operationStore.authorizeAndCreate(
                security.tokenHash(confirmationToken), request);
        if (!authorization.authorized()) {
            return RealRunResult.failure("CONFIRMATION_INVALID",
                    "确认授权无效、已过期、已消费或与当前输入不匹配",
                    "重新调用 real_run_draft 获取新的确认请求");
        }

        HarnessOperation operation = authorization.operation();
        submitIfPending(operation, draft, initialInput);
        return operationResult(waitForResult(operation));
    }

    public RealRunResult confirm(DraftAccess access, String confirmationId) {
        DraftConfirmation confirmation = confirmationStore.findOwned(confirmationId, access.userId());
        if (confirmation == null) {
            return RealRunResult.failure("CONFIRMATION_NOT_FOUND", "确认请求不存在", "重新发起真实试跑");
        }
        FlowDraft draft = loadAuthorized(access, confirmation.draftId());
        if (draft == null || draft.getStatus() != DraftStatus.BUILDING
                || draft.getRevision() != confirmation.draftRevision()) {
            return RealRunResult.failure("CONFIRMATION_STALE",
                    "草稿已变化或不可访问，原确认请求已失效", "重新校验、模拟并发起真实试跑");
        }
        String token = security.newToken();
        if (!confirmationStore.confirm(confirmationId, access.userId(), security.tokenHash(token))) {
            return RealRunResult.failure("CONFIRMATION_INVALID",
                    "确认请求已过期或不处于待确认状态", "重新发起真实试跑");
        }
        log.info("用户已确认流程草稿真实试跑: confirmationId={}, draftId={}, userId={}, revision={}",
                confirmationId, confirmation.draftId(), access.userId(), confirmation.draftRevision());
        return RealRunResult.success(Map.of(
                "confirmationId", confirmationId,
                "confirmationToken", token,
                "draftId", confirmation.draftId(),
                "revision", confirmation.draftRevision(),
                "expiresAt", confirmation.expiresAt()));
    }

    public RealRunResult getOperation(DraftAccess access, String operationId) {
        HarnessOperation operation = operationStore.findOperationOwned(operationId, access.userId());
        return operation == null
                ? RealRunResult.failure("OPERATION_NOT_FOUND", "操作不存在", "核对 operationId")
                : operationResult(operation);
    }

    public int expireConfirmations() {
        return confirmationStore.expireDue();
    }

    public int markUnknownStaleOperations() {
        return operationStore.markUnknownStale(LocalDateTime.now().minus(properties.getStaleAfter()));
    }

    public int purgeExpiredConfirmations() {
        return confirmationStore.purgeTerminalBefore(
                LocalDateTime.now().minus(properties.getConfirmationRetention()));
    }

    public int expireOperationResultDetails() {
        return operationStore.expireResultDetails(
                LocalDateTime.now().minus(properties.getOperationResultRetention()));
    }

    private RealRunResult confirmationRequired(DraftAccess access,
                                               FlowDraft draft,
                                               String inputDigest,
                                               List<DraftIssue> issues) {
        String confirmationId = "cfm_" + randomId();
        DraftConfirmation confirmation = confirmationStore.createOrRefresh(new DraftConfirmationRequest(
                confirmationId,
                ACTION,
                draft.getDraftId(),
                draft.getRevision(),
                access.userId(),
                access.sessionId(),
                inputDigest,
                LocalDateTime.now().plus(properties.getConfirmationTtl())));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("confirmationRequired", true);
        payload.put("confirmationId", confirmation.confirmationId());
        payload.put("draftId", draft.getDraftId());
        payload.put("revision", draft.getRevision());
        payload.put("expiresAt", confirmation.expiresAt());
        payload.put("warning", "真实试跑会调用真实模型、工具和子 Agent，可能产生外部副作用");
        payload.put("warnings", issues.stream().filter(issue -> "WARN".equals(issue.level())).toList());
        log.info("创建流程草稿真实试跑确认: confirmationId={}, draftId={}, userId={}, revision={}",
                confirmation.confirmationId(), draft.getDraftId(), access.userId(), draft.getRevision());
        return new RealRunResult(false, "CONFIRM_REQUIRED", "真实试跑需要用户确认",
                "等待用户确认后携带服务端授权重新发起请求", List.of(), payload);
    }

    private void submitIfPending(HarnessOperation operation,
                                 FlowDraft draft,
                                 Map<String, Object> initialInput) {
        if (operation.status() != HarnessOperationStatus.PENDING) {
            return;
        }
        operationExecutor.execute(() -> runOperation(
                operation.operationId(), codec.copy(draft.getGraph()), initialInput,
                String.valueOf(draft.getUserId()), draft.getSessionId()));
    }

    private void runOperation(String operationId,
                              com.nebula.common.ai.flow.FlowDefinition definition,
                              Map<String, Object> initialInput,
                              String userId,
                              String sessionId) {
        if (!operationStore.claim(operationId)) {
            return;
        }
        long heartbeatMs = Math.max(1000L, properties.getHeartbeatInterval().toMillis());
        ScheduledFuture<?> heartbeat = heartbeatExecutor.scheduleAtFixedRate(
                () -> heartbeatSafely(operationId), heartbeatMs, heartbeatMs, TimeUnit.MILLISECONDS);
        try {
            DraftRunResult result = runner.run(new DraftRunRequest(
                    operationId, definition, initialInput, userId, sessionId));
            operationStore.markSucceeded(operationId, safeSummary.summarize(result.output()));
            log.info("流程草稿真实试跑成功: operationId={}", operationId);
        } catch (Throwable e) {
            String message = safeError(e);
            operationStore.markFailed(operationId, "REAL_RUN_FAILED", message);
            log.warn("流程草稿真实试跑失败: operationId={}, error={}", operationId, message);
        } finally {
            heartbeat.cancel(false);
        }
    }

    private void heartbeatSafely(String operationId) {
        try {
            operationStore.heartbeat(operationId);
        } catch (RuntimeException e) {
            log.warn("更新真实试跑心跳失败: operationId={}, error={}", operationId, e.getMessage());
        }
    }

    private HarnessOperation waitForResult(HarnessOperation operation) {
        long deadline = System.nanoTime() + properties.getWaitTimeout().toNanos();
        HarnessOperation current = current(operation);
        while (!current.status().terminal() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            current = current(current);
        }
        return current;
    }

    private HarnessOperation current(HarnessOperation fallback) {
        HarnessOperation value = operationStore.findOperationOwned(fallback.operationId(), fallback.userId());
        return value == null ? fallback : value;
    }

    private RealRunResult operationResult(HarnessOperation operation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operationId", operation.operationId());
        payload.put("action", operation.action());
        payload.put("draftId", operation.draftId());
        payload.put("revision", operation.draftRevision());
        payload.put("status", operation.status().name());
        if (!operation.resultSummary().isEmpty()) {
            payload.put("result", operation.resultSummary());
        }
        if (operation.errorCode() != null) {
            payload.put("errorCode", operation.errorCode());
            payload.put("errorMessage", operation.errorMessage());
        }
        return RealRunResult.success(payload);
    }

    private RealRunResult checkPreconditions(FlowDraft draft, String draftId, long expectedRevision) {
        if (draft == null) {
            return RealRunResult.failure("DRAFT_NOT_FOUND", "草稿不存在或不可访问", "核对 draftId");
        }
        if (draft.getStatus() != DraftStatus.BUILDING) {
            return RealRunResult.failure("DRAFT_IMMUTABLE", "草稿已进入终态 " + draft.getStatus(), "新建草稿后再操作");
        }
        if (draft.getRevision() != expectedRevision) {
            return RealRunResult.failure("DRAFT_CONFLICT",
                    "草稿 revision 已变化，当前为 " + draft.getRevision(), "读取最新草稿后重试");
        }
        if (!Long.valueOf(expectedRevision).equals(draft.getLastValidatedRevision())
                || !Long.valueOf(expectedRevision).equals(draft.getLastSimulatedRevision())) {
            return RealRunResult.failure("DRAFT_NOT_SIMULATED",
                    "当前 revision 尚未完成校验和模拟", "先调用 simulate_draft，再真实试跑相同 revision");
        }
        return null;
    }

    private FlowDraft loadAuthorized(DraftAccess access, String draftId) {
        FlowDraft draft = draftStore.findOwned(draftId, access.userId());
        if (draft == null) {
            return null;
        }
        if (draft.getSessionId() != null && !Objects.equals(draft.getSessionId(), access.sessionId())) {
            log.warn("拒绝跨会话真实试跑访问: draftId={}, userId={}", draftId, access.userId());
            return null;
        }
        return draft;
    }

    private String safeError(Throwable error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        message = message.replaceAll("(?i)(api[-_]?key|token|password|authorization|secret)\\s*[=:]\\s*[^,;\\s]+",
                "$1=[REDACTED]");
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
