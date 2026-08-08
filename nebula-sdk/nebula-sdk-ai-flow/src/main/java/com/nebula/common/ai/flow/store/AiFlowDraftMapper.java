package com.nebula.common.ai.flow.store;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 流程草稿 Mapper。
 *
 * <p>{@link #compareAndSet(AiFlowDraft, long)} 是草稿 mutation 的唯一写路径，SQL 条件同时约束所有者、
 * revision 和 BUILDING 状态，不能拆成先查后写。
 *
 * @author nebula
 */
@Mapper
public interface AiFlowDraftMapper extends BaseMapper<AiFlowDraft> {

    @Insert("""
            INSERT INTO ai_flow_draft
              (draft_id, session_id, user_id, flow_code, name, description, engine_type,
               graph_json, revision, status, create_time, update_time)
            VALUES
              (#{draftId}, #{sessionId}, #{userId}, #{flowCode}, #{name}, #{description}, #{engineType},
               #{graphJson}, #{revision}, #{status}, NOW(), NOW())
            """)
    int insertDraft(AiFlowDraft draft);

    @Select("""
            SELECT id, draft_id, session_id, user_id, flow_code, name, description, engine_type,
                   graph_json, revision, last_validated_revision, last_simulated_revision, status,
                   committed_flow_code, create_time, update_time
              FROM ai_flow_draft
             WHERE draft_id = #{draftId} AND user_id = #{userId}
             LIMIT 1
            """)
    AiFlowDraft selectOwned(@Param("draftId") String draftId, @Param("userId") Long userId);

    @Update("""
            UPDATE ai_flow_draft
               SET flow_code = #{draft.flowCode},
                   name = #{draft.name},
                   description = #{draft.description},
                   graph_json = #{draft.graphJson},
                   revision = revision + 1,
                   last_validated_revision = NULL,
                   last_simulated_revision = NULL,
                   update_time = NOW()
             WHERE draft_id = #{draft.draftId}
               AND user_id = #{draft.userId}
               AND revision = #{expectedRevision}
               AND status = 'BUILDING'
            """)
    int compareAndSet(@Param("draft") AiFlowDraft draft,
                      @Param("expectedRevision") long expectedRevision);

    @Update("""
            UPDATE ai_flow_draft
               SET last_validated_revision = revision,
                   update_time = NOW()
             WHERE draft_id = #{draftId}
               AND user_id = #{userId}
               AND revision = #{expectedRevision}
               AND status = 'BUILDING'
            """)
    int markValidated(@Param("draftId") String draftId,
                      @Param("userId") Long userId,
                      @Param("expectedRevision") long expectedRevision);

    @Update("""
            UPDATE ai_flow_draft
               SET last_validated_revision = revision,
                   last_simulated_revision = revision,
                   update_time = NOW()
             WHERE draft_id = #{draftId}
               AND user_id = #{userId}
               AND revision = #{expectedRevision}
               AND status = 'BUILDING'
            """)
    int markSimulated(@Param("draftId") String draftId,
                      @Param("userId") Long userId,
                      @Param("expectedRevision") long expectedRevision);

    @Update("""
            UPDATE ai_flow_draft
               SET status = 'COMMITTED',
                   committed_flow_code = #{flowCode},
                   update_time = NOW()
             WHERE draft_id = #{draftId}
               AND user_id = #{userId}
               AND revision = #{expectedRevision}
               AND last_validated_revision = revision
               AND (#{requireSimulation} = FALSE OR last_simulated_revision = revision)
               AND status = 'BUILDING'
            """)
    int markCommitted(@Param("draftId") String draftId,
                      @Param("userId") Long userId,
                      @Param("expectedRevision") long expectedRevision,
                      @Param("requireSimulation") boolean requireSimulation,
                      @Param("flowCode") String flowCode);
}
