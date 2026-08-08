package com.nebula.manager.service;

import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.core.domain.PageResult;
import com.nebula.manager.dto.FlowPageQuery;
import com.nebula.manager.dto.FlowRunRequest;
import com.nebula.manager.vo.FlowRunResultVO;
import com.nebula.manager.vo.FlowSummaryVO;

/**
 * AI流程编排管理服务接口（管理员端）
 * 负责流程定义的列表/详情/保存整图/删除，以及一键运行。
 *
 * @author nebula
 */
public interface FlowAdminService {

    /**
     * 分页查询流程
     *
     * @param query 查询参数
     * @return 分页结果
     */
    PageResult<FlowSummaryVO> page(FlowPageQuery query);

    /**
     * 获取流程完整定义（含节点与边），供前端画布回显
     *
     * @param flowCode 流程编码
     * @return 流程定义
     */
    FlowDefinition getDefinition(String flowCode);

    /**
     * 保存整图（按流程编码 upsert 流程头，节点/边全删再插），并失效引擎缓存
     *
     * @param definition 流程定义
     * @return 流程编码
     */
    String save(FlowDefinition definition);

    /**
     * 仅当 flowCode 尚不存在时创建完整流程，不允许覆盖已有流程。
     *
     * <p>该方法供 Harness 草稿提交使用，数据库唯一键是并发 CREATE_ONLY 的最终裁决者。
     *
     * @param definition 已完整校验的流程定义
     * @return 流程编码
     */
    String createOnly(FlowDefinition definition);

    /**
     * 删除流程（连同节点与边），并失效引擎缓存
     *
     * @param flowCode 流程编码
     */
    void delete(String flowCode);

    /**
     * 一键运行流程
     *
     * @param flowCode 流程编码
     * @param request  运行请求（初始输入、会话标识）
     * @return 运行结果（产物与节点轨迹，含执行实例 runId）
     */
    FlowRunResultVO run(String flowCode, FlowRunRequest request);

    /**
     * 续跑一个已落库的执行实例（从断点恢复，跳过已完成节点）。
     * 需启用状态持久化（装配了 RunStateStore）；实例不存在或已成功时报错。
     *
     * @param runId 执行实例标识
     * @return 续跑结果（产物与节点轨迹）
     */
    FlowRunResultVO resume(String runId);
}
