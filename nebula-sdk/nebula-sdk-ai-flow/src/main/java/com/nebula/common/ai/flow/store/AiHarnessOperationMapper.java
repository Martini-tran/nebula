package com.nebula.common.ai.flow.store;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** Harness 幂等操作 Mapper。 */
@Mapper
public interface AiHarnessOperationMapper extends BaseMapper<AiHarnessOperation> {

    @Insert("""
            INSERT IGNORE INTO ai_harness_operation
              (operation_id, action, draft_id, draft_revision, user_id, session_id,
               input_digest, status, create_time, update_time)
            VALUES
              (#{operationId}, #{action}, #{draftId}, #{draftRevision}, #{userId}, #{sessionId},
               #{inputDigest}, 'PENDING', NOW(), NOW())
            """)
    int insertOperation(AiHarnessOperation operation);

    @Select("""
            SELECT id, operation_id, action, draft_id, draft_revision, user_id, session_id,
                   input_digest, status, result_summary_json, error_code, error_message,
                   started_at, heartbeat_at, finished_at, create_time, update_time
              FROM ai_harness_operation
             WHERE action = #{action} AND draft_id = #{draftId} AND draft_revision = #{draftRevision}
               AND user_id = #{userId}
             LIMIT 1
            """)
    AiHarnessOperation selectByKey(@Param("action") String action,
                                   @Param("draftId") String draftId,
                                   @Param("draftRevision") long draftRevision,
                                   @Param("userId") Long userId);

    @Select("""
            SELECT id, operation_id, action, draft_id, draft_revision, user_id, session_id,
                   input_digest, status, result_summary_json, error_code, error_message,
                   started_at, heartbeat_at, finished_at, create_time, update_time
              FROM ai_harness_operation
             WHERE operation_id = #{operationId} AND user_id = #{userId}
             LIMIT 1
            """)
    AiHarnessOperation selectOwned(@Param("operationId") String operationId,
                                   @Param("userId") Long userId);

    @Update("""
            UPDATE ai_harness_operation
               SET status = 'RUNNING', started_at = NOW(), heartbeat_at = NOW(), update_time = NOW()
             WHERE operation_id = #{operationId} AND status = 'PENDING'
            """)
    int claim(@Param("operationId") String operationId);

    @Update("""
            UPDATE ai_harness_operation
               SET heartbeat_at = NOW(), update_time = NOW()
             WHERE operation_id = #{operationId} AND status = 'RUNNING'
            """)
    int heartbeat(@Param("operationId") String operationId);

    @Update("""
            UPDATE ai_harness_operation
               SET status = 'SUCCEEDED', result_summary_json = #{resultSummaryJson},
                   finished_at = NOW(), heartbeat_at = NOW(), update_time = NOW()
             WHERE operation_id = #{operationId} AND status = 'RUNNING'
            """)
    int markSucceeded(@Param("operationId") String operationId,
                      @Param("resultSummaryJson") String resultSummaryJson);

    @Update("""
            UPDATE ai_harness_operation
               SET status = 'FAILED', error_code = #{errorCode}, error_message = #{errorMessage},
                   finished_at = NOW(), heartbeat_at = NOW(), update_time = NOW()
             WHERE operation_id = #{operationId} AND status = 'RUNNING'
            """)
    int markFailed(@Param("operationId") String operationId,
                   @Param("errorCode") String errorCode,
                   @Param("errorMessage") String errorMessage);

    @Update("""
            UPDATE ai_harness_operation
               SET status = 'UNKNOWN', error_code = 'OPERATION_HEARTBEAT_LOST',
                   error_message = '执行进程中断，无法判断外部副作用是否已发生',
                   finished_at = NOW(), update_time = NOW()
             WHERE status = 'RUNNING' AND heartbeat_at < #{staleBefore}
            """)
    int markUnknownStale(@Param("staleBefore") LocalDateTime staleBefore);

    @Update("""
            UPDATE ai_harness_operation
               SET result_summary_json = CASE WHEN status = 'SUCCEEDED' THEN '{\"expired\":true}'
                                              ELSE result_summary_json END,
                   error_message = CASE WHEN status IN ('FAILED', 'UNKNOWN') THEN '操作详情已过保留期'
                                        ELSE error_message END,
                   update_time = NOW()
             WHERE status IN ('SUCCEEDED', 'FAILED', 'UNKNOWN')
               AND finished_at < #{before}
               AND ((status = 'SUCCEEDED' AND (result_summary_json IS NULL OR result_summary_json <> '{\"expired\":true}'))
                    OR (status IN ('FAILED', 'UNKNOWN')
                        AND COALESCE(error_message, '') <> '操作详情已过保留期'))
            """)
    int expireResultDetails(@Param("before") LocalDateTime before);
}
