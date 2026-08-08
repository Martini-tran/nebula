package com.nebula.common.ai.flow.store;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Harness 服务端确认 Mapper，所有状态迁移均使用条件更新。 */
@Mapper
public interface AiHarnessConfirmationMapper extends BaseMapper<AiHarnessConfirmation> {

    @Insert("""
            INSERT INTO ai_harness_confirmation
              (confirmation_id, action, draft_id, draft_revision, user_id, session_id,
               input_digest, status, expires_at, create_time, update_time)
            VALUES
              (#{confirmationId}, #{action}, #{draftId}, #{draftRevision}, #{userId}, #{sessionId},
               #{inputDigest}, 'PENDING', #{expiresAt}, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
              confirmation_id = IF(status = 'CONSUMED', confirmation_id, VALUES(confirmation_id)),
              session_id = IF(status = 'CONSUMED', session_id, VALUES(session_id)),
              input_digest = IF(status = 'CONSUMED', input_digest, VALUES(input_digest)),
              token_hash = IF(status = 'CONSUMED', token_hash, NULL),
              expires_at = IF(status = 'CONSUMED', expires_at, VALUES(expires_at)),
              confirmed_at = IF(status = 'CONSUMED', confirmed_at, NULL),
              consumed_at = IF(status = 'CONSUMED', consumed_at, NULL),
              status = IF(status = 'CONSUMED', 'CONSUMED', 'PENDING'),
              update_time = NOW()
            """)
    int upsertPending(AiHarnessConfirmation confirmation);

    @Select("""
            SELECT id, confirmation_id, action, draft_id, draft_revision, user_id, session_id,
                   input_digest, token_hash, status, expires_at, confirmed_at, consumed_at,
                   create_time, update_time
              FROM ai_harness_confirmation
             WHERE confirmation_id = #{confirmationId} AND user_id = #{userId}
             LIMIT 1
            """)
    AiHarnessConfirmation selectOwned(@Param("confirmationId") String confirmationId,
                                      @Param("userId") Long userId);

    @Update("""
            UPDATE ai_harness_confirmation
               SET token_hash = #{tokenHash}, status = 'CONFIRMED', confirmed_at = NOW(), update_time = NOW()
             WHERE confirmation_id = #{confirmationId}
               AND user_id = #{userId}
               AND status = 'PENDING'
               AND expires_at > NOW()
            """)
    int confirm(@Param("confirmationId") String confirmationId,
                @Param("userId") Long userId,
                @Param("tokenHash") String tokenHash);

    @Update("""
            UPDATE ai_harness_confirmation
               SET status = 'CONSUMED', consumed_at = NOW(), update_time = NOW()
             WHERE token_hash = #{tokenHash}
               AND action = #{action}
               AND draft_id = #{draftId}
               AND draft_revision = #{draftRevision}
               AND user_id = #{userId}
               AND input_digest = #{inputDigest}
               AND status = 'CONFIRMED'
               AND expires_at > NOW()
            """)
    int consume(@Param("tokenHash") String tokenHash,
                @Param("action") String action,
                @Param("draftId") String draftId,
                @Param("draftRevision") long draftRevision,
                @Param("userId") Long userId,
                @Param("inputDigest") String inputDigest);

    @Update("""
            UPDATE ai_harness_confirmation
               SET status = 'EXPIRED', token_hash = NULL, update_time = NOW()
             WHERE status IN ('PENDING', 'CONFIRMED') AND expires_at <= NOW()
            """)
    int expireDue();

    @Delete("""
            DELETE FROM ai_harness_confirmation
             WHERE status IN ('CONSUMED', 'EXPIRED', 'CANCELLED') AND update_time < #{before}
            """)
    int purgeTerminalBefore(@Param("before") java.time.LocalDateTime before);
}
