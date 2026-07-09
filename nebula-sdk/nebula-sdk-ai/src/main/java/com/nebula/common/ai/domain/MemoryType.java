package com.nebula.common.ai.domain;

/**
 * 长期记忆类型
 *
 * @author nebula
 */
public enum MemoryType {

    /**
     * 情景记忆：具体发生过的事件/交互（通常关联某次会话）
     */
    EPISODIC,

    /**
     * 语义记忆：抽象的事实与知识（如用户画像、偏好）
     */
    SEMANTIC,

    /**
     * 程序记忆：完成某事的技能/规则/流程
     */
    PROCEDURAL,

    /**
     * 实体记忆：人、物、概念及其属性
     */
    ENTITY
}
