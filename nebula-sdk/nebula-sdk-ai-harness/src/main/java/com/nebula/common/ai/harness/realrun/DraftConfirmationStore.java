package com.nebula.common.ai.harness.realrun;

/** 一次性服务端确认存储 SPI。实现不得返回或记录令牌明文。 */
public interface DraftConfirmationStore {

    DraftConfirmation createOrRefresh(DraftConfirmationRequest request);

    DraftConfirmation findOwned(String confirmationId, Long userId);

    boolean confirm(String confirmationId, Long userId, String tokenHash);

    int expireDue();

    int purgeTerminalBefore(java.time.LocalDateTime before);
}
