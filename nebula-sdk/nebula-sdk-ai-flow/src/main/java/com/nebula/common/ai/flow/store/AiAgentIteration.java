package com.nebula.common.ai.flow.store;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 智能体跨实例迭代链表（系列递推）
 * "执行与执行之间"的长期状态载体：由 {@code IterationDriver} 定时到点推进，每轮创建一个 Agent 实例，
 * 上一轮实例产物按 {@code carryOver} 映射成下一轮入参。对应 SDK 的 {@code com.nebula.common.ai.iteration.IterationChain}。
 * JSON 列（carryOver/seedInputs）以字符串存储，经 {@link FlowJsonCodec} 序列化。
 *
 * @author nebula
 */
@Data
@TableName("ai_agent_iteration")
public class AiAgentIteration implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 迭代链唯一标识，业务键（= 一个"系列"实例）
     */
    private String chainId;

    /**
     * 链名称，如"30天Java进阶"
     */
    private String name;

    /**
     * 每一轮用哪个 Agent 跑
     */
    private String agentCode;

    /**
     * 推进节律，如 "0 0 9 * * ?"（每天9点）
     */
    private String cron;

    /**
     * 下一轮应触发时间（Driver 扫这个字段决定谁到点）
     */
    private LocalDateTime nextRunAt;

    /**
     * 已完成轮次（= 已写到第几篇）
     */
    private Integer seq;

    /**
     * 轮次上限（可空，防无限连载；到顶置 COMPLETED）
     */
    private Integer maxIterations;

    /**
     * 出链条件 SpEL（对上一轮产物求值）
     */
    private String untilExpr;

    /**
     * carry-over 映射(JSON)：上一轮产物键 → 下一轮 inputs 键
     */
    private String carryOver;

    /**
     * 首轮种子入参(JSON)：第 0 轮没有上一轮，用它启动（如系列主题）
     */
    private String seedInputs;

    /**
     * 上一轮实例 id（carry-over 数据源指针；首轮为空）
     */
    private String lastInstanceId;

    /**
     * 归属用户ID
     */
    private String userId;

    /**
     * 关联会话ID
     */
    private String conversationId;

    /**
     * 状态：ACTIVE | PAUSED | COMPLETED | FAILED
     */
    private String status;

    /**
     * 连续失败次数，达阈值自动 PAUSED（防定时打空转）
     */
    private Integer consecutiveFails;

    /**
     * 最近一次推进失败原因
     */
    private String errorMsg;

    /**
     * 乐观锁：advance 用 CAS 防同一轮重复推进（配合 Redis 锁双保险）
     */
    private Integer lockVersion;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
