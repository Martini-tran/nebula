package com.nebula.manager.config;

import com.nebula.common.ai.harness.realrun.DraftRealRunService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定期清理过期确认，并把失联的已认领真实试跑标记为 UNKNOWN。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HarnessRealRunScheduler {

    private final DraftRealRunService service;

    @Scheduled(fixedDelayString = "${nebula.ai.harness.real-run.cleanup-interval-ms:60000}")
    public void reconcile() {
        int expired = service.expireConfirmations();
        int unknown = service.markUnknownStaleOperations();
        int purged = service.purgeExpiredConfirmations();
        int resultDetailsExpired = service.expireOperationResultDetails();
        if (expired > 0 || unknown > 0 || purged > 0 || resultDetailsExpired > 0) {
            log.info("Harness 真实试跑清理完成: expiredConfirmations={}, purgedConfirmations={}, "
                            + "unknownOperations={}, expiredResultDetails={}",
                    expired, purged, unknown, resultDetailsExpired);
        }
    }
}
