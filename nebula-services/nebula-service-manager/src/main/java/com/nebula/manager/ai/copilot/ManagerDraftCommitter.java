package com.nebula.manager.ai.copilot;

import com.nebula.common.ai.flow.store.AiFlowDraftMapper;
import com.nebula.common.ai.harness.draft.DraftCommitRequest;
import com.nebula.common.ai.harness.draft.DraftCommitResult;
import com.nebula.common.ai.harness.draft.DraftCommitter;
import com.nebula.manager.service.FlowAdminService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Manager 对 Harness 草稿提交 SPI 的事务适配器。
 *
 * <p>正式流程三表 CREATE_ONLY 与草稿 COMMITTED CAS 位于同一事务。若 flowCode 冲突或 revision 在
 * 校验后变化，整个正式流程写入都会回滚。
 *
 * @author nebula
 */
@Slf4j
@Component
public class ManagerDraftCommitter implements DraftCommitter {

    private final FlowAdminService flowAdminService;
    private final AiFlowDraftMapper draftMapper;
    private final TransactionTemplate transactionTemplate;

    public ManagerDraftCommitter(FlowAdminService flowAdminService,
                                 AiFlowDraftMapper draftMapper,
                                 PlatformTransactionManager transactionManager) {
        this.flowAdminService = flowAdminService;
        this.draftMapper = draftMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public DraftCommitResult commit(DraftCommitRequest request) {
        try {
            DraftCommitResult result = transactionTemplate.execute(status -> {
                String flowCode = flowAdminService.createOnly(request.definition());
                int updated = draftMapper.markCommitted(
                        request.draftId(), request.userId(), request.revision(),
                        request.requireSimulation(), flowCode);
                if (updated != 1) {
                    throw new DraftCommitConflictException();
                }
                int version = request.definition().getVersion() <= 0 ? 1 : request.definition().getVersion();
                return DraftCommitResult.success(flowCode, version);
            });
            if (result == null) {
                return DraftCommitResult.failure("DRAFT_COMMIT_FAILED", "提交事务没有返回结果", "稍后重试提交");
            }
            return result;
        } catch (DuplicateKeyException e) {
            log.info("流程草稿 CREATE_ONLY 冲突: draftId={}, revision={}, flowCode={}",
                    request.draftId(), request.revision(), request.definition().getFlowCode());
            return DraftCommitResult.failure("FLOW_CODE_CONFLICT",
                    "flowCode 已存在，CREATE_ONLY 提交不会覆盖已有流程",
                    "调用 update_draft_metadata 更换 flowCode 后重新校验并提交");
        } catch (DraftCommitConflictException e) {
            log.info("流程草稿提交 CAS 冲突: draftId={}, userId={}, revision={}",
                    request.draftId(), request.userId(), request.revision());
            return DraftCommitResult.failure("DRAFT_CONFLICT",
                    "草稿 revision 或状态已变化，提交已整体回滚",
                    "调用 read_draft 获取最新 revision 后重试");
        } catch (RuntimeException e) {
            log.error("流程草稿提交失败: draftId={}, userId={}, revision={}, error={}",
                    request.draftId(), request.userId(), request.revision(), e.getMessage(), e);
            return DraftCommitResult.failure("DRAFT_COMMIT_FAILED",
                    "草稿提交失败: " + safeMessage(e), "检查正式流程存储后重试");
        }
    }

    private String safeMessage(RuntimeException error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static final class DraftCommitConflictException extends RuntimeException {
    }
}
