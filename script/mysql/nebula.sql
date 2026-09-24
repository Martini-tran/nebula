/*
 Navicat Premium Dump SQL

 Source Server         : localhost
 Source Server Type    : MySQL
 Source Server Version : 80012 (8.0.12)
 Source Host           : localhost:3306
 Source Schema         : nebula

 Target Server Type    : MySQL
 Target Server Version : 80012 (8.0.12)
 File Encoding         : 65001

 Date: 18/09/2026 19:52:34
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for ai_agent
-- ----------------------------
DROP TABLE IF EXISTS `ai_agent`;
CREATE TABLE `ai_agent`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `agent_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Agent编码，跨版本稳定的逻辑标识（非全局唯一）；= 记忆隔离键，同 agent_code 各版本共享一份长期记忆',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '名称',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '描述',
  `flow_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '引用的编排图编码',
  `flow_version` int(11) NOT NULL DEFAULT 1 COMMENT '引用的编排图版本（可换绑做灰度）',
  `input_schema` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '输入契约 JSON Schema',
  `output_schema` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '输出契约 JSON Schema',
  `memory_config` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '记忆配置：enabled/import 键/export 策略(Replace/Append/Summary)',
  `default_profile_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '默认模型档案编码',
  `skill_codes` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '技能编码集合',
  `version` int(11) NOT NULL DEFAULT 1 COMMENT 'Agent 定义版本，发布后不可变',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '0=停用 1=启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_agent_code_version`(`agent_code` ASC, `version` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI 智能体定义表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_agent
-- ----------------------------

-- ----------------------------
-- Table structure for ai_agent_instance
-- ----------------------------
DROP TABLE IF EXISTS `ai_agent_instance`;
CREATE TABLE `ai_agent_instance`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '实例唯一标识，业务键，续跑/唤醒入口',
  `agent_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '归属 Agent 编码（= 记忆隔离键）',
  `agent_version` int(11) NOT NULL COMMENT '审计标记：从哪个 Agent 版本创建',
  `flow_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '审计标记：引用的编排图编码',
  `flow_version` int(11) NOT NULL COMMENT '审计标记：从哪个 Flow 版本创建；运行时不据此查表（改用 graph_snapshot）',
  `graph_snapshot` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '★版本锁定核心：创建时编译的完整源图定义(节点+边+stateConfig+guard)JSON。续跑/回放只从此加载，不回查 node/edge 表',
  `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '归属用户ID',
  `conversation_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '关联会话ID',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING | SUSPENDED | SUCCESS | FAILED',
  `current_state` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '状态机当前状态（节点编码）',
  `last_checkpoint_state` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '最近越过的检查点状态（补偿回滚边界；阶段 2.5 启用）',
  `awaiting_events` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'SUSPENDED 时等待的事件名集合(JSON数组)，signal 命中其一即唤醒（阶段 3 启用）',
  `inputs` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '初始输入快照(JSON)',
  `context_snapshot` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '分区上下文快照(JSON)，分级落盘：仅挂起/越检查点/终态时全量刷新',
  `context_snapshot_seq` int(11) NOT NULL DEFAULT -1 COMMENT '快照对应的 transition seq，恢复时从此 seq 之后重放增量',
  `outputs` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'Outputs 快照(JSON)',
  `transition_count` int(11) NOT NULL DEFAULT 0 COMMENT '已转移次数，配合 max_transitions 防死循环',
  `parent_instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '父实例（递归子 Agent 用，顶层为空，阶段 3 用）',
  `parent_node_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '父实例中触发本子实例的 AgentNode 编码（回调唤醒用，阶段 3）',
  `lock_version` int(11) NOT NULL DEFAULT 0 COMMENT '乐观锁：signal/resume 用 CAS 抢占，防并发重复推进',
  `error_msg` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '失败原因',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_instance_id`(`instance_id` ASC) USING BTREE,
  INDEX `idx_parent`(`parent_instance_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 31 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI 智能体执行实例（状态机）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_agent_instance
-- ----------------------------

-- ----------------------------
-- Table structure for ai_agent_instance_transition
-- ----------------------------
DROP TABLE IF EXISTS `ai_agent_instance_transition`;
CREATE TABLE `ai_agent_instance_transition`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '归属实例标识',
  `seq` int(11) NOT NULL COMMENT '转移序号，从 0 递增（重试也占序号）',
  `from_state` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '源状态',
  `to_state` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '目标状态',
  `event_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '触发本次转移的事件（如有）',
  `attempt` int(11) NOT NULL DEFAULT 0 COMMENT '进入 to_state 的重试次数（0=首次）',
  `outcome` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'SUCCESS' COMMENT '轨迹类型：SUCCESS | RETRY | FAILED | COMPENSATED，回放时可折叠 RETRY 行',
  `is_compensation` tinyint(1) NOT NULL DEFAULT 0 COMMENT '预留：1=补偿轨迹（Saga 回滚，阶段 2.5 启用）',
  `node_result` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '进入 to_state 后该节点产物(delta)；失败/重试时记错误摘要（恢复时只重放 outcome=SUCCESS 行）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_instance_seq`(`instance_id` ASC, `seq` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 79 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI 智能体状态转移历史（回放/审计），每次 attempt 落一行' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_agent_instance_transition
-- ----------------------------

-- ----------------------------
-- Table structure for ai_agent_iteration
-- ----------------------------
DROP TABLE IF EXISTS `ai_agent_iteration`;
CREATE TABLE `ai_agent_iteration`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `chain_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '迭代链唯一标识，业务键（= 一个\"系列\"实例）',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '链名称，如\"30天Java进阶\"',
  `agent_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '每一轮用哪个 Agent 跑（跨版本稳定，运行时取该 agent_code 最新启用版本）',
  `cron` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '推进节律，如 \"0 0 9 * * ?\"（每天9点）',
  `next_run_at` datetime NOT NULL COMMENT '下一轮应触发时间（Driver 扫这个字段决定谁到点）',
  `seq` int(11) NOT NULL DEFAULT 0 COMMENT '已完成轮次（= 已写到第几篇）',
  `max_iterations` int(11) NULL DEFAULT NULL COMMENT '轮次上限（可空，防无限连载；到顶置 COMPLETED）',
  `until_expr` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '出链条件 SpEL（复用 ConditionCompiler），如 \"getString(\'outlineDone\') == \'true\'\"；对上一轮产物求值',
  `carry_over` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '★carry-over 映射(JSON)：上一轮产物键 → 下一轮 inputs 键',
  `seed_inputs` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '首轮种子入参(JSON)：第 0 轮没有上一轮，用它启动（如系列主题）',
  `last_instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '上一轮实例 id（carry-over 数据源指针；首轮为空）',
  `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '归属用户ID',
  `conversation_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '关联会话ID',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE | PAUSED | COMPLETED | FAILED',
  `consecutive_fails` int(11) NOT NULL DEFAULT 0 COMMENT '连续失败次数，达阈值自动 PAUSED（防定时打空转）',
  `error_msg` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '最近一次推进失败原因',
  `lock_version` int(11) NOT NULL DEFAULT 0 COMMENT '乐观锁：advance 用 CAS 防同一轮重复推进（配合 Redis 锁双保险）',
  `webhook_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '每轮 advance 成功后回调的 URL（如 blog 落库接口），空则不回调；见 编排回调Webhook设计.md',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_chain_id`(`chain_id` ASC) USING BTREE,
  INDEX `idx_due`(`status` ASC, `next_run_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI 智能体跨实例迭代链（系列递推）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_agent_iteration
-- ----------------------------

-- ----------------------------
-- Table structure for ai_flow
-- ----------------------------
DROP TABLE IF EXISTS `ai_flow`;
CREATE TABLE `ai_flow`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '流程ID',
  `flow_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '流程编码，全局唯一',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '流程名称',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '流程描述',
  `version` int(11) NOT NULL DEFAULT 1 COMMENT '版本号，用于缓存键与灰度',
  `default_profile_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '默认模型档案编码',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态：0=停用 1=启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `engine_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'DAG' COMMENT '执行内核：DAG | STATE_MACHINE',
  `max_transitions` int(11) NOT NULL DEFAULT 100 COMMENT '状态机全局转移次数上限，防死循环；per-Flow 可配',
  `max_agent_depth` int(11) NOT NULL DEFAULT 8 COMMENT '递归子 Agent 最大深度，防无限递归（阶段 3 用）',
  `webhook_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '流程级回调URL：实例终态后POST产物(INSTANCE_SUCCESS/FAILED)，空则不回调',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_flow_code`(`flow_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 19 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI流程定义表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_flow
-- ----------------------------

-- ----------------------------
-- Table structure for ai_flow_draft
-- ----------------------------
DROP TABLE IF EXISTS `ai_flow_draft`;
CREATE TABLE `ai_flow_draft`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `draft_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '草稿业务ID（工具入参用）',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '归属会话（Copilot conversationId）',
  `user_id` bigint(20) NOT NULL COMMENT '归属用户（所有访问均须匹配）',
  `flow_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '目标流程编码（提交时用）',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '流程名称',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '流程描述',
  `engine_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'DAG' COMMENT '执行内核 DAG|STATE_MACHINE',
  `graph_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '草稿图 JSON（FlowDefinition 序列化）',
  `revision` bigint(20) NOT NULL DEFAULT 0 COMMENT '乐观锁版本，每次变更递增',
  `last_validated_revision` bigint(20) NULL DEFAULT NULL COMMENT '最近一次无 ERROR 校验的草稿版本',
  `last_simulated_revision` bigint(20) NULL DEFAULT NULL COMMENT '最近一次完成模拟的草稿版本',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'BUILDING' COMMENT 'BUILDING|COMMITTED|ABANDONED',
  `committed_flow_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '提交后的流程编码',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_draft_id`(`draft_id` ASC) USING BTREE,
  INDEX `idx_user_session`(`user_id` ASC, `session_id` ASC) USING BTREE,
  INDEX `idx_update_time`(`update_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 19 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI流程生成草稿' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of ai_flow_draft
-- ----------------------------

-- ----------------------------
-- Table structure for ai_flow_edge
-- ----------------------------
DROP TABLE IF EXISTS `ai_flow_edge`;
CREATE TABLE `ai_flow_edge`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '边ID',
  `flow_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '归属流程编码',
  `from_node` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '起点节点编码',
  `to_node` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '终点节点编码',
  `condition_expr` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '条件表达式(SpEL)，空表示无条件直达',
  `sort_no` int(11) NOT NULL DEFAULT 0 COMMENT '排序号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `event_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '转移触发事件名（状态机用，DAG 忽略）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_flow_code`(`flow_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 84 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI流程边表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_flow_edge
-- ----------------------------

-- ----------------------------
-- Table structure for ai_flow_node
-- ----------------------------
DROP TABLE IF EXISTS `ai_flow_node`;
CREATE TABLE `ai_flow_node`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '节点ID',
  `flow_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '归属流程编码',
  `node_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '节点编码，流程内唯一',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '节点名称',
  `node_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PROMPT' COMMENT '节点类型，决定执行器',
  `system_prompt` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '系统提示词模板',
  `prompt_template` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '用户提示词模板',
  `profile_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '引用的模型档案编码',
  `provider` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '服务提供商（覆盖档案）',
  `model` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '模型名称（覆盖档案）',
  `base_url` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'API基础地址（覆盖档案）',
  `api_key` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'API密钥（覆盖档案）',
  `temperature` double NULL DEFAULT NULL COMMENT '采样温度（覆盖档案）',
  `max_tokens` int(11) NULL DEFAULT NULL COMMENT '最大输出token数（覆盖档案）',
  `top_p` double NULL DEFAULT NULL COMMENT 'Top P采样参数（覆盖档案）',
  `timeout_ms` int(11) NULL DEFAULT NULL COMMENT '请求超时毫秒（覆盖档案）',
  `stop` json NULL COMMENT '停止词（JSON数组）',
  `options` json NULL COMMENT '扩展参数（JSON对象）',
  `input_mapping` json NULL COMMENT '输入映射：模板变量名->上下文键（JSON对象）',
  `output_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '产物写回上下文的键',
  `output_mode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'TEXT' COMMENT '产物模式：TEXT/JSON',
  `node_config` json NULL COMMENT '非PROMPT类型的扩展配置（JSON对象）',
  `remember_trace` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否记录该节点轨迹',
  `sort_no` int(11) NOT NULL DEFAULT 0 COMMENT '排序号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `state_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '状态机语义：ENTRY | NORMAL | TERMINAL（DAG 忽略）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_flow_node`(`flow_code` ASC, `node_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 172 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI流程节点表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_flow_node
-- ----------------------------

-- ----------------------------
-- Table structure for ai_flow_run
-- ----------------------------
DROP TABLE IF EXISTS `ai_flow_run`;
CREATE TABLE `ai_flow_run`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `run_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '执行实例唯一标识，业务键，续跑入口',
  `flow_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '流程编码',
  `version` int(11) NOT NULL DEFAULT 1 COMMENT '流程版本',
  `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '归属用户ID',
  `conversation_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '关联会话ID',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'RUNNING' COMMENT '状态：RUNNING/SUCCESS/FAILED',
  `input` json NULL COMMENT '初始输入快照（JSON对象）',
  `attributes` json NULL COMMENT '全量产物快照（JSON对象），每节点完成后刷新；续跑时灌回上下文',
  `executed_nodes` json NULL COMMENT '已执行完成的节点编码集合（JSON数组）',
  `failed_node` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '失败节点编码',
  `error_msg` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '失败原因',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_run_id`(`run_id` ASC) USING BTREE,
  INDEX `idx_flow_code`(`flow_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI流程编排执行实例表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_flow_run
-- ----------------------------

-- ----------------------------
-- Table structure for ai_flow_run_node
-- ----------------------------
DROP TABLE IF EXISTS `ai_flow_run_node`;
CREATE TABLE `ai_flow_run_node`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `run_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '归属执行实例标识',
  `node_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '节点编码',
  `seq` int(11) NOT NULL DEFAULT 0 COMMENT '执行序（本次执行内自增）',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'SUCCESS' COMMENT '节点状态：SUCCESS',
  `output` json NULL COMMENT '该节点完成时的产物快照（JSON对象，可空）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_run_id`(`run_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 18 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI流程编排执行节点轨迹表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_flow_run_node
-- ----------------------------

-- ----------------------------
-- Table structure for ai_harness_confirmation
-- ----------------------------
DROP TABLE IF EXISTS `ai_harness_confirmation`;
CREATE TABLE `ai_harness_confirmation`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `confirmation_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '确认业务ID',
  `action` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '高风险动作编码',
  `draft_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '草稿业务ID',
  `draft_revision` bigint(20) NOT NULL COMMENT '确认绑定的草稿版本',
  `user_id` bigint(20) NOT NULL COMMENT '确认用户强边界',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '发起会话，仅审计',
  `input_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'canonical initialInput SHA-256',
  `token_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '一次性令牌 SHA-256，不存明文',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'PENDING|CONFIRMED|CONSUMED|EXPIRED|CANCELLED',
  `expires_at` datetime NOT NULL COMMENT '确认过期时间',
  `confirmed_at` datetime NULL DEFAULT NULL,
  `consumed_at` datetime NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_harness_confirmation_id`(`confirmation_id` ASC) USING BTREE,
  UNIQUE INDEX `uk_harness_confirmation_action_revision`(`action` ASC, `draft_id` ASC, `draft_revision` ASC) USING BTREE,
  INDEX `idx_harness_confirmation_token`(`token_hash` ASC) USING BTREE,
  INDEX `idx_harness_confirmation_expiry`(`status` ASC, `expires_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Harness高风险动作确认' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_harness_confirmation
-- ----------------------------

-- ----------------------------
-- Table structure for ai_harness_operation
-- ----------------------------
DROP TABLE IF EXISTS `ai_harness_operation`;
CREATE TABLE `ai_harness_operation`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `operation_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '操作业务ID',
  `action` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '高风险动作编码',
  `draft_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '草稿业务ID',
  `draft_revision` bigint(20) NOT NULL COMMENT '操作绑定的草稿版本',
  `user_id` bigint(20) NOT NULL COMMENT '操作用户强边界',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '发起会话，仅审计',
  `input_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'canonical initialInput SHA-256',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'PENDING|RUNNING|SUCCEEDED|FAILED|UNKNOWN',
  `result_summary_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '脱敏、截断后的安全结果摘要',
  `error_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '稳定错误编码',
  `error_message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '脱敏错误摘要',
  `started_at` datetime NULL DEFAULT NULL,
  `heartbeat_at` datetime NULL DEFAULT NULL,
  `finished_at` datetime NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_harness_operation_id`(`operation_id` ASC) USING BTREE,
  UNIQUE INDEX `uk_harness_operation_action_revision`(`action` ASC, `draft_id` ASC, `draft_revision` ASC) USING BTREE,
  INDEX `idx_harness_operation_owner`(`user_id` ASC, `operation_id` ASC) USING BTREE,
  INDEX `idx_harness_operation_status`(`status` ASC, `heartbeat_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Harness高风险幂等操作' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_harness_operation
-- ----------------------------

-- ----------------------------
-- Table structure for ai_knowledge_base
-- ----------------------------
DROP TABLE IF EXISTS `ai_knowledge_base`;
CREATE TABLE `ai_knowledge_base`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '知识库ID',
  `kb_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '知识库编码，全局唯一，被 knowledge_search 工具与导入引用',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '知识库名称',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '描述',
  `embedding_provider` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'embedding 提供者编码（对应 EmbeddingProvider.code()）',
  `embedding_model` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'embedding 模型名称',
  `dimension` int(11) NULL DEFAULT NULL COMMENT '向量维度（须与 embedding 模型一致，读写前校验）',
  `metric` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'COSINE' COMMENT '相似度度量（COSINE/L2/IP）',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态：0=停用 1=启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_kb_code`(`kb_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI知识库表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of ai_knowledge_base
-- ----------------------------

-- ----------------------------
-- Table structure for ai_knowledge_chunk
-- ----------------------------
DROP TABLE IF EXISTS `ai_knowledge_chunk`;
CREATE TABLE `ai_knowledge_chunk`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '切片ID',
  `kb_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属知识库编码',
  `doc_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '来源文档标识',
  `chunk_index` int(11) NOT NULL DEFAULT 0 COMMENT '切片在文档内的序号',
  `content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '切片正文（真相源；向量存 Milvus）',
  `token_count` int(11) NULL DEFAULT NULL COMMENT '粗略 token 数（按字符估算）',
  `metadata` json NULL COMMENT '附加元数据（来源/标题/页码等，JSON对象）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_kb_doc`(`kb_code` ASC, `doc_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI知识库切片表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of ai_knowledge_chunk
-- ----------------------------

-- ----------------------------
-- Table structure for ai_knowledge_document
-- ----------------------------
DROP TABLE IF EXISTS `ai_knowledge_document`;
CREATE TABLE `ai_knowledge_document`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '文档ID',
  `kb_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属知识库编码',
  `doc_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文档标识，库内唯一',
  `title` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '标题',
  `source_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '来源类型（text/markdown/url 等）',
  `source_uri` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '来源地址（URL 或原始路径，可空）',
  `char_count` int(11) NULL DEFAULT 0 COMMENT '正文字符数',
  `chunk_count` int(11) NULL DEFAULT 0 COMMENT '切块数',
  `status` tinyint(1) NOT NULL DEFAULT 0 COMMENT '状态：0=索引中 1=完成 2=失败',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_kb_doc`(`kb_code` ASC, `doc_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI知识库文档表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of ai_knowledge_document
-- ----------------------------

-- ----------------------------
-- Table structure for ai_mcp_server
-- ----------------------------
DROP TABLE IF EXISTS `ai_mcp_server`;
CREATE TABLE `ai_mcp_server`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '服务器ID',
  `server_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '服务器编码，全局唯一，被流程/节点引用',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '服务器名称',
  `transport` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '传输类型：stdio/sse/streamable-http',
  `command` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '【stdio】启动命令（npx/uvx/python 等）',
  `args` json NULL COMMENT '【stdio】命令参数数组（JSON数组，如 [\"-y\",\"@scope/server\",\"/data\"]）',
  `env` json NULL COMMENT '【stdio】子进程环境变量（JSON对象，明文，敏感凭证请放 auth_token）',
  `url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '【远程】SSE/HTTP 端点地址',
  `headers` json NULL COMMENT '【远程】自定义请求头（JSON对象，明文非敏感部分）',
  `auth_token` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '【远程】鉴权凭证 Bearer/apiKey（AES加密密文存储，列宽含密文膨胀冗余）',
  `timeout_ms` int(11) NULL DEFAULT NULL COMMENT '连接/请求超时（毫秒）',
  `options` json NULL COMMENT '扩展参数，透传未来新增的 MCP 配置项（JSON对象）',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态：0=停用 1=启用',
  `remark` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_server_code`(`server_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI MCP服务器配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_mcp_server
-- ----------------------------

-- ----------------------------
-- Table structure for ai_memory
-- ----------------------------
DROP TABLE IF EXISTS `ai_memory`;
CREATE TABLE `ai_memory`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '记忆ID（作为 Milvus nebula_memory 的业务主键 pk）',
  `agent_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '归属Agent功能编码（记忆按Agent隔离，Milvus partitionKey）',
  `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '归属用户ID（记忆按用户隔离）',
  `conversation_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '关联会话ID（可空=跨会话）',
  `mem_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '记忆类型：EPISODIC/SEMANTIC/PROCEDURAL/ENTITY',
  `content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '记忆内容（真相源；mode=vector 时向量副本存 Milvus）',
  `metadata` json NULL COMMENT '扩展元数据（JSON对象）',
  `need_reindex` tinyint(1) NOT NULL DEFAULT 0 COMMENT '向量待重索引：0=向量已同步，1=向量写/删失败待对账补偿（批次4 need_reindex 对账）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_agent_user`(`agent_code` ASC, `user_id` ASC) USING BTREE,
  INDEX `idx_need_reindex`(`need_reindex` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI长期记忆表（db=LIKE关键词/vector=Milvus语义召回）' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of ai_memory
-- ----------------------------

-- ----------------------------
-- Table structure for ai_model_profile
-- ----------------------------
DROP TABLE IF EXISTS `ai_model_profile`;
CREATE TABLE `ai_model_profile`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '档案ID',
  `profile_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '档案编码，全局唯一，被流程/节点引用',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '档案名称',
  `provider` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '服务提供商标识（openai/deepseek 等）',
  `base_url` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'API基础地址',
  `api_key` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'API密钥（AES加密密文存储，列宽含密文膨胀冗余）',
  `model` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '模型名称',
  `temperature` double NULL DEFAULT NULL COMMENT '默认采样温度',
  `max_tokens` int(11) NULL DEFAULT NULL COMMENT '默认最大输出token数',
  `top_p` double NULL DEFAULT NULL COMMENT '默认Top P采样参数',
  `timeout_ms` int(11) NULL DEFAULT NULL COMMENT '请求超时（毫秒）',
  `options` json NULL COMMENT '扩展参数，透传厂商私有参数（response_format / frequency_penalty 等，JSON对象）',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态：0=停用 1=启用',
  `remark` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_profile_code`(`profile_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI模型档案表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_model_profile
-- ----------------------------

-- ----------------------------
-- Table structure for ai_prompt
-- ----------------------------
DROP TABLE IF EXISTS `ai_prompt`;
CREATE TABLE `ai_prompt`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '提示词ID',
  `prompt_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '提示词编码，全局唯一，被流程/节点引用',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '提示词名称',
  `role` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'system' COMMENT '消息角色：system=系统设定 user=用户输入 assistant=助手示例',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '提示词正文，支持 {{变量名}} 占位符，渲染时由 variables 声明的变量替换',
  `variables` json NULL COMMENT '变量声明数组（JSON数组，如 [{\"name\":\"topic\",\"type\":\"string\",\"required\":true,\"description\":\"主题\"}]），编辑器据此渲染填充表单',
  `remark` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_prompt_code`(`prompt_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI提示词表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of ai_prompt
-- ----------------------------

-- ----------------------------
-- Table structure for ai_relay_model
-- ----------------------------
DROP TABLE IF EXISTS `ai_relay_model`;
CREATE TABLE `ai_relay_model`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '模型ID',
  `code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模型编码（如 gpt-4o-mini / claude-3-5-sonnet）',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模型名称',
  `model_vendor` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '模型厂商（OpenAI/Anthropic/Google等）',
  `model_type` tinyint(4) NOT NULL DEFAULT 1 COMMENT '模型类型（1文本 2图像 3音频 4多模态 5Embedding）',
  `description` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '模型说明',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '展示排序',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态（1正常 0停用）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_code`(`code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 24 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI模型配置' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_model
-- ----------------------------

-- ----------------------------
-- Table structure for ai_relay_package_model
-- ----------------------------
DROP TABLE IF EXISTS `ai_relay_package_model`;
CREATE TABLE `ai_relay_package_model`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '套餐模型ID',
  `package_id` bigint(20) NOT NULL COMMENT '套餐ID（ai_relay_provider_package.id）',
  `model_id` bigint(20) NOT NULL COMMENT '模型ID（ai_relay_model.id）',
  `provider_model_code` varchar(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '服务商侧模型编码，不填则默认使用模型编码',
  `consume_multiplier` decimal(8, 4) NOT NULL DEFAULT 1.0000 COMMENT '消耗倍率，如1.5表示消耗额度*1.5',
  `min_charge_amount` decimal(18, 6) NULL DEFAULT NULL COMMENT '最低扣费额度',
  `max_context_tokens` int(11) NULL DEFAULT NULL COMMENT '最大上下文Token数',
  `input_price_per_million_tokens` decimal(12, 4) NULL DEFAULT NULL COMMENT '输入Token单价（每百万Token），币种沿用套餐currency，跨套餐比价用',
  `output_price_per_million_tokens` decimal(12, 4) NULL DEFAULT NULL COMMENT '输出Token单价（每百万Token），币种沿用套餐currency，跨套餐比价用',
  `is_default` tinyint(4) NOT NULL DEFAULT 0 COMMENT '是否默认模型（1是 0否）',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '展示排序',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态（1正常 0停用）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_package_model`(`package_id` ASC, `model_id` ASC) USING BTREE,
  INDEX `idx_package_id`(`package_id` ASC) USING BTREE,
  INDEX `idx_model_id`(`model_id` ASC) USING BTREE,
  INDEX `idx_package_status_sort`(`package_id` ASC, `status` ASC, `sort_order` ASC) USING BTREE,
  INDEX `idx_model_input_price`(`model_id` ASC, `status` ASC, `input_price_per_million_tokens` ASC) USING BTREE,
  INDEX `idx_model_output_price`(`model_id` ASC, `status` ASC, `output_price_per_million_tokens` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转套餐支持模型及消耗倍率' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_package_model
-- ----------------------------

-- ----------------------------
-- Table structure for ai_relay_package_type
-- ----------------------------
DROP TABLE IF EXISTS `ai_relay_package_type`;
CREATE TABLE `ai_relay_package_type`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '套餐类型ID',
  `code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '套餐类型编码（day/week/month/usage等）',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '套餐类型名称（天卡/周卡/月卡/按量）',
  `billing_mode` tinyint(4) NOT NULL COMMENT '计费模式（1固定周期 2按量计费）',
  `duration_value` int(11) NULL DEFAULT NULL COMMENT '套餐周期数值，如1、7、30，按量计费可为空',
  `duration_unit` tinyint(4) NULL DEFAULT NULL COMMENT '周期单位（1天 2周 3月 4年），按量计费可为空',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '类型说明',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '展示排序',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态（1正常 0停用）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_code`(`code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转套餐类型配置' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_package_type
-- ----------------------------

-- ----------------------------
-- Table structure for ai_relay_payment_method
-- ----------------------------
DROP TABLE IF EXISTS `ai_relay_payment_method`;
CREATE TABLE `ai_relay_payment_method`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '支付方式ID',
  `code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '支付方式编码（alipay/wechat/paypal/usdt等）',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '支付方式名称（支付宝/微信/PayPal/USDT等）',
  `icon_file_id` bigint(20) NULL DEFAULT NULL COMMENT '支付方式图标文件ID（sys_file）',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '支付方式说明',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '展示排序',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态（1正常 0停用）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_code`(`code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转支付方式配置' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_payment_method
-- ----------------------------

-- ----------------------------
-- Table structure for ai_relay_provider
-- ----------------------------
DROP TABLE IF EXISTS `ai_relay_provider`;
CREATE TABLE `ai_relay_provider`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '服务商ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '服务商名称（如 OpenRouter）',
  `website_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '官网地址',
  `logo_file_id` bigint(20) NULL DEFAULT NULL COMMENT 'Logo文件ID（sys_file）',
  `description` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '服务商简介',
  `recommend_score` decimal(5, 2) NOT NULL DEFAULT 0.00 COMMENT '综合推荐分（核心排序依据）',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '展示排序',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态（1正常 0下线）',
  `last_sync_time` datetime NULL DEFAULT NULL COMMENT '最近一次同步时间（运营手动同步价格/模型时刷新）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（即收录时间）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_name`(`name` ASC) USING BTREE,
  INDEX `idx_last_sync_time`(`last_sync_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转服务商' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_provider
-- ----------------------------

-- ----------------------------
-- Table structure for ai_relay_provider_advantage
-- ----------------------------
DROP TABLE IF EXISTS `ai_relay_provider_advantage`;
CREATE TABLE `ai_relay_provider_advantage`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '优势ID',
  `provider_id` bigint(20) NOT NULL COMMENT '服务商ID（ai_relay_provider.id）',
  `title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '优势标题',
  `content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '优势说明',
  `advantage_type` tinyint(4) NOT NULL DEFAULT 1 COMMENT '优势类型（1普通优势 2核心优势 3风险提示）',
  `icon_file_id` bigint(20) NULL DEFAULT NULL COMMENT '优势图标文件ID（sys_file）',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '展示排序',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态（1正常 0停用）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_provider_id`(`provider_id` ASC) USING BTREE,
  INDEX `idx_provider_status_sort`(`provider_id` ASC, `status` ASC, `sort_order` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转服务商优势' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_provider_advantage
-- ----------------------------

-- ----------------------------
-- Table structure for ai_relay_provider_package
-- ----------------------------
DROP TABLE IF EXISTS `ai_relay_provider_package`;
CREATE TABLE `ai_relay_provider_package`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '套餐ID',
  `provider_id` bigint(20) NOT NULL COMMENT '服务商ID（ai_relay_provider.id）',
  `package_type_id` bigint(20) NOT NULL COMMENT '套餐类型ID（ai_relay_package_type.id）',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '套餐名称',
  `price` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '套餐价格',
  `original_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '原价/划线价',
  `currency` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'CNY' COMMENT '币种（CNY/USD等）',
  `is_recommended` tinyint(4) NOT NULL DEFAULT 0 COMMENT '是否推荐（1是 0否）',
  `recommend_score` decimal(5, 2) NOT NULL DEFAULT 0.00 COMMENT '套餐推荐分',
  `description` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '套餐说明',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '展示排序',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态（1正常 0下线）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_provider_id`(`provider_id` ASC) USING BTREE,
  INDEX `idx_package_type_id`(`package_type_id` ASC) USING BTREE,
  INDEX `idx_provider_status_sort`(`provider_id` ASC, `status` ASC, `sort_order` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 25 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转服务商套餐' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_provider_package
-- ----------------------------

-- ----------------------------
-- Table structure for ai_relay_provider_package_limit
-- ----------------------------
DROP TABLE IF EXISTS `ai_relay_provider_package_limit`;
CREATE TABLE `ai_relay_provider_package_limit`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '套餐限制ID',
  `package_id` bigint(20) NOT NULL COMMENT '套餐ID（ai_relay_provider_package.id）',
  `limit_type` tinyint(4) NOT NULL COMMENT '限制类型（1总额度 2每日额度 3每周额度 4每月额度 5单次额度）',
  `quota_amount` decimal(18, 6) NOT NULL COMMENT '额度数量',
  `quota_unit` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '额度单位（token/request/credit等）',
  `reset_cycle` tinyint(4) NOT NULL DEFAULT 0 COMMENT '重置周期（0不重置 1每日 2每周 3每月 4套餐周期）',
  `over_limit_strategy` tinyint(4) NOT NULL DEFAULT 1 COMMENT '超限策略（1禁止使用 2按量计费 3限速）',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '限制说明',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态（1正常 0停用）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_package_id`(`package_id` ASC) USING BTREE,
  INDEX `idx_package_limit_type`(`package_id` ASC, `limit_type` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转服务商套餐额度限制' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_provider_package_limit
-- ----------------------------

-- ----------------------------
-- Table structure for ai_relay_provider_payment_method
-- ----------------------------
DROP TABLE IF EXISTS `ai_relay_provider_payment_method`;
CREATE TABLE `ai_relay_provider_payment_method`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `provider_id` bigint(20) NOT NULL COMMENT '服务商ID（ai_relay_provider.id）',
  `payment_method_id` bigint(20) NOT NULL COMMENT '支付方式ID（ai_relay_payment_method.id）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注说明',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '展示排序',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态（1正常 0停用）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_provider_payment`(`provider_id` ASC, `payment_method_id` ASC) USING BTREE,
  INDEX `idx_provider_id`(`provider_id` ASC) USING BTREE,
  INDEX `idx_payment_method_id`(`payment_method_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转服务商支持支付方式' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_provider_payment_method
-- ----------------------------

-- ----------------------------
-- Table structure for ai_relay_provider_recharge
-- ----------------------------
DROP TABLE IF EXISTS `ai_relay_provider_recharge`;
CREATE TABLE `ai_relay_provider_recharge`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '充值记录ID',
  `provider_id` bigint(20) NOT NULL COMMENT '服务商ID（ai_relay_provider.id）',
  `package_id` bigint(20) NULL DEFAULT NULL COMMENT '套餐ID（ai_relay_provider_package.id），按量计费/直充可空',
  `amount` decimal(10, 2) NOT NULL COMMENT '充值金额',
  `currency` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'CNY' COMMENT '币种（CNY/USD等）',
  `exchange_rate` decimal(10, 4) NULL DEFAULT NULL COMMENT '汇率（非CNY时折算汇率）',
  `cny_amount` decimal(10, 2) NULL DEFAULT NULL COMMENT '折合人民币金额',
  `payment_method_id` bigint(20) NULL DEFAULT NULL COMMENT '支付方式ID（ai_relay_payment_method.id）',
  `recharge_time` datetime NOT NULL COMMENT '充值时间',
  `order_no` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '订单号/交易流水号',
  `voucher_file_id` bigint(20) NULL DEFAULT NULL COMMENT '充值凭证文件ID（sys_file）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注说明',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态（1正常 0作废）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_provider_id`(`provider_id` ASC) USING BTREE,
  INDEX `idx_package_id`(`package_id` ASC) USING BTREE,
  INDEX `idx_recharge_time`(`recharge_time` ASC) USING BTREE,
  INDEX `idx_provider_recharge_time`(`provider_id` ASC, `recharge_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转服务商个人充值记录（用于佐证推荐真实性）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_provider_recharge
-- ----------------------------

-- ----------------------------
-- Table structure for ai_relay_provider_recommend
-- ----------------------------
DROP TABLE IF EXISTS `ai_relay_provider_recommend`;
CREATE TABLE `ai_relay_provider_recommend`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '推荐ID',
  `provider_id` bigint(20) NOT NULL COMMENT '服务商ID（ai_relay_provider.id）',
  `recommend_reason` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '推荐原因（精简一句话/摘要，用于列表展示）',
  `review_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '完整测评内容（支持Markdown）',
  `review_score` decimal(3, 1) NULL DEFAULT NULL COMMENT '个人测评评分（0-10分）',
  `pros` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '优点（多个用换行/分号分隔）',
  `cons` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '缺点（多个用换行/分号分隔）',
  `use_scenario` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '推荐使用场景',
  `first_use_time` datetime NULL DEFAULT NULL COMMENT '首次使用时间',
  `review_time` datetime NULL DEFAULT NULL COMMENT '测评时间',
  `recommend_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '推荐时间',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '展示排序',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态（1正常 0下线）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_provider_id`(`provider_id` ASC) USING BTREE,
  INDEX `idx_status_sort`(`status` ASC, `sort_order` ASC) USING BTREE,
  INDEX `idx_recommend_time`(`recommend_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转服务商个人推荐及测评（推荐均为本人实际使用并充值）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_provider_recommend
-- ----------------------------

-- ----------------------------
-- Table structure for ai_tool
-- ----------------------------
DROP TABLE IF EXISTS `ai_tool`;
CREATE TABLE `ai_tool`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '工具ID',
  `tool_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '工具编码，全局唯一，= 代码 ToolDefinition.code()，被流程节点引用',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '工具显示名',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用途描述（兼作 LLM function description）',
  `category` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '分类（http/data/search 等），编辑器分组用',
  `params_schema` json NULL COMMENT '入参 JSON Schema，声明工具所需参数/类型/必填项，编辑器据此渲染参数表单',
  `result_schema` json NULL COMMENT '出参结构描述（可空），给下游节点提示产物形状',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用：0=已下线(代码中已移除) 1=启用',
  `builtin` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否内置工具：1=代码内置(由同步器维护) 0=外部登记(预留扩展位)',
  `sort_no` int(11) NOT NULL DEFAULT 0 COMMENT '排序号',
  `remark` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_tool_code`(`tool_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 22 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI工具定义表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_tool
-- ----------------------------

-- ----------------------------
-- Table structure for ai_webhook_delivery
-- ----------------------------
DROP TABLE IF EXISTS `ai_webhook_delivery`;
CREATE TABLE `ai_webhook_delivery`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `delivery_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '投递唯一标识（业务键，重发幂等键之一）',
  `instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '来源实例',
  `chain_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '来源迭代链（若来自链）',
  `seq` int(11) NULL DEFAULT NULL COMMENT '迭代链第几轮（幂等：chainId+seq 唯一定位一篇产物）',
  `agent_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `node_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '节点级回调才有',
  `event` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'ITERATION_ADVANCED | INSTANCE_SUCCESS | INSTANCE_FAILED | NODE_SUCCESS | NODE_FAILED',
  `url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '发去哪',
  `mode` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'INLINE' COMMENT 'INLINE(当场发) | DEFER(线程池发)；仅发送时机，与可靠性无关',
  `payload` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '发了什么(JSON，重发直接读它，不重跑 Agent)；不含鉴权 headers',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING | SUCCESS | FAILED | DEAD',
  `attempts` int(11) NOT NULL DEFAULT 0 COMMENT '已尝试次数',
  `max_attempts` int(11) NOT NULL DEFAULT 8 COMMENT '重发上限，达到后置 DEAD 不再自动重发',
  `response_code` int(11) NULL DEFAULT NULL COMMENT '最后一次 HTTP 响应码',
  `last_error` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '最后一次失败原因',
  `next_retry_at` datetime NULL DEFAULT NULL COMMENT '下次重发时间（FAILED 且未达上限时，按指数退避）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_delivery_id`(`delivery_id` ASC) USING BTREE,
  INDEX `idx_chain_seq`(`chain_id` ASC, `seq` ASC) USING BTREE,
  INDEX `idx_retry`(`status` ASC, `next_retry_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 14 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '编排回调投递记录（日志 + 断点续发）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_webhook_delivery
-- ----------------------------

-- ----------------------------
-- Table structure for blog_category
-- ----------------------------
DROP TABLE IF EXISTS `blog_category`;
CREATE TABLE `blog_category`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '分类名称',
  `slug` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '分类URL标识',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '分类描述',
  `parent_id` bigint(20) NULL DEFAULT NULL COMMENT '父分类ID（用于层级分类）',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序序号（升序）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_blog_category_slug`(`slug` ASC) USING BTREE,
  UNIQUE INDEX `uk_blog_category_name`(`name` ASC) USING BTREE,
  INDEX `idx_blog_category_parent_id`(`parent_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 41 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客分类表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_category
-- ----------------------------

-- ----------------------------
-- Table structure for blog_content_version
-- ----------------------------
DROP TABLE IF EXISTS `blog_content_version`;
CREATE TABLE `blog_content_version`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '版本ID',
  `post_id` bigint(20) NOT NULL COMMENT '文章ID',
  `version_no` int(11) NOT NULL COMMENT '版本号',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '版本标题',
  `summary` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '版本摘要',
  `content_file_id` bigint(20) NOT NULL COMMENT '版本正文文件ID',
  `cover_file_id` bigint(20) NULL DEFAULT NULL COMMENT '版本封面文件ID',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '该版本的状态快照',
  `visibility` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '该版本的可见性快照',
  `change_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'manual' COMMENT '变更类型：manual(手动)/auto(自动)',
  `change_note` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '变更备注',
  `creator_id` bigint(20) NULL DEFAULT NULL COMMENT '创建者用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_blog_version_post_version`(`post_id` ASC, `version_no` ASC) USING BTREE,
  INDEX `idx_blog_version_post_id`(`post_id` ASC) USING BTREE,
  INDEX `idx_blog_version_file_id`(`content_file_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 41 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客内容版本表（完整快照）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_content_version
-- ----------------------------

-- ----------------------------
-- Table structure for blog_file_asset
-- ----------------------------
DROP TABLE IF EXISTS `blog_file_asset`;
CREATE TABLE `blog_file_asset`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '文件ID',
  `storage_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '存储类型：local(本地)/oss(对象存储)',
  `bucket` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'OSS bucket名称（本地存储时可为空）',
  `object_key` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '存储路径，如 posts/2026/04/xxx.md',
  `url` varchar(750) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '访问URL',
  `filename` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '原始文件名',
  `extension` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件扩展名（如 .md, .jpg）',
  `mime_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'MIME类型（如 text/markdown, image/jpeg）',
  `size_bytes` bigint(20) NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
  `hash_sha256` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件SHA256哈希值（用于去重/校验）',
  `file_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'other' COMMENT '文件用途：markdown/image/attachment/cover/other',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_blog_file_storage_key`(`storage_type` ASC, `object_key`(100) ASC) USING BTREE,
  INDEX `idx_blog_file_hash`(`hash_sha256` ASC) USING BTREE,
  INDEX `idx_blog_file_type`(`file_type` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 63 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客文件资源表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_file_asset
-- ----------------------------

-- ----------------------------
-- Table structure for blog_post
-- ----------------------------
DROP TABLE IF EXISTS `blog_post`;
CREATE TABLE `blog_post`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '文章ID',
  `author_id` bigint(20) NOT NULL COMMENT '作者用户ID',
  `post_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'article' COMMENT '内容类型：article(文章)/essay(随笔)',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文章标题',
  `slug` varchar(220) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'URL唯一标识',
  `summary` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '摘要',
  `content_file_id` bigint(20) NOT NULL COMMENT 'Markdown正文文件ID（关联blog_file_asset）',
  `cover_file_id` bigint(20) NULL DEFAULT NULL COMMENT '封面文件ID（关联blog_file_asset）',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'draft' COMMENT '状态：draft(草稿)/published(已发布)/archived(已归档)',
  `visibility` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'public' COMMENT '可见性：public(公开)/private(私有)',
  `source_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'manual' COMMENT '来源：manual(手动)/ai(AI生成)/import(导入)',
  `is_original` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否原创：1-是，0-否',
  `view_count` int(10) UNSIGNED NOT NULL DEFAULT 0 COMMENT '浏览次数',
  `like_count` int(10) UNSIGNED NOT NULL DEFAULT 0 COMMENT '点赞次数',
  `published_at` datetime NULL DEFAULT NULL COMMENT '发布时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_blog_post_slug`(`slug` ASC) USING BTREE,
  INDEX `idx_blog_post_author_id`(`author_id` ASC) USING BTREE,
  INDEX `idx_blog_post_status_published_at`(`status` ASC, `published_at` ASC) USING BTREE,
  INDEX `idx_blog_post_create_time`(`create_time` ASC) USING BTREE,
  INDEX `idx_blog_post_content_file_id`(`content_file_id` ASC) USING BTREE,
  INDEX `idx_blog_post_cover_file_id`(`cover_file_id` ASC) USING BTREE,
  INDEX `idx_blog_post_type_status_published_at`(`post_type` ASC, `status` ASC, `visibility` ASC, `published_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 14 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客文章主表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_post
-- ----------------------------

-- ----------------------------
-- Table structure for blog_post_category
-- ----------------------------
DROP TABLE IF EXISTS `blog_post_category`;
CREATE TABLE `blog_post_category`  (
  `post_id` bigint(20) NOT NULL COMMENT '文章ID',
  `category_id` bigint(20) NOT NULL COMMENT '分类ID',
  PRIMARY KEY (`post_id`, `category_id`) USING BTREE,
  INDEX `idx_blog_post_category_category_id`(`category_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客文章分类关系表（多对多）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_post_category
-- ----------------------------

-- ----------------------------
-- Table structure for blog_post_import_item
-- ----------------------------
DROP TABLE IF EXISTS `blog_post_import_item`;
CREATE TABLE `blog_post_import_item`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `task_id` bigint(20) NOT NULL COMMENT '所属任务ID',
  `filename` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `success` tinyint(1) NOT NULL DEFAULT 0,
  `article_id` bigint(20) NULL DEFAULT NULL COMMENT '成功时的文章ID',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `slug` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `error` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_blog_post_import_item_task`(`task_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 9 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客文章导入明细表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_post_import_item
-- ----------------------------

-- ----------------------------
-- Table structure for blog_post_import_task
-- ----------------------------
DROP TABLE IF EXISTS `blog_post_import_task`;
CREATE TABLE `blog_post_import_task`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '导入任务ID',
  `user_id` bigint(20) NOT NULL COMMENT '发起导入的用户ID',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'pending' COMMENT 'pending/running/success/failed',
  `total_count` int(11) NOT NULL DEFAULT 0 COMMENT '文件总数',
  `processed_count` int(11) NOT NULL DEFAULT 0 COMMENT '已处理数（进度）',
  `success_count` int(11) NOT NULL DEFAULT 0 COMMENT '成功数',
  `fail_count` int(11) NOT NULL DEFAULT 0 COMMENT '失败数',
  `post_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '统一文章状态',
  `visibility` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `post_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `rehost_images` tinyint(1) NOT NULL DEFAULT 1,
  `error_message` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '任务级错误（整体失败时）',
  `started_at` datetime NULL DEFAULT NULL,
  `finished_at` datetime NULL DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_blog_post_import_task_user`(`user_id` ASC, `status` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客文章导入任务表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_post_import_task
-- ----------------------------

-- ----------------------------
-- Table structure for blog_post_tag
-- ----------------------------
DROP TABLE IF EXISTS `blog_post_tag`;
CREATE TABLE `blog_post_tag`  (
  `post_id` bigint(20) NOT NULL COMMENT '文章ID',
  `tag_id` bigint(20) NOT NULL COMMENT '标签ID',
  PRIMARY KEY (`post_id`, `tag_id`) USING BTREE,
  INDEX `idx_blog_post_tag_tag_id`(`tag_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客文章标签关系表（多对多）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_post_tag
-- ----------------------------

-- ----------------------------
-- Table structure for blog_search_index_task
-- ----------------------------
DROP TABLE IF EXISTS `blog_search_index_task`;
CREATE TABLE `blog_search_index_task`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `post_id` bigint(20) NOT NULL COMMENT '关联的文章ID',
  `index_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'blog_posts' COMMENT '索引名称',
  `action` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '操作类型：upsert(插入/更新)/delete(删除)',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'pending' COMMENT '状态：pending(等待)/running(执行中)/success(成功)/failed(失败)',
  `retry_count` int(11) NOT NULL DEFAULT 0 COMMENT '重试次数',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '错误信息（失败时记录）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `finished_at` datetime NULL DEFAULT NULL COMMENT '完成时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_blog_search_task_status`(`status` ASC) USING BTREE,
  INDEX `idx_blog_search_task_post_id`(`post_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客搜索索引同步任务表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_search_index_task
-- ----------------------------

-- ----------------------------
-- Table structure for blog_series
-- ----------------------------
DROP TABLE IF EXISTS `blog_series`;
CREATE TABLE `blog_series`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '系列ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '系列名称',
  `slug` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '系列URL标识（唯一）',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '系列简介',
  `cover_file_id` bigint(20) NULL DEFAULT NULL COMMENT '封面文件ID（关联blog_file_asset）',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'draft' COMMENT 'draft/published/archived',
  `visibility` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'public' COMMENT 'public/private',
  `is_finished` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否完结',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '系列列表排序',
  `create_by` bigint(20) NOT NULL COMMENT '创建者ID（关联sys_user）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `chain_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '产出本系列的迭代链 chainId（webhook 落库用；手工建的系列为空）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_slug`(`slug` ASC) USING BTREE,
  UNIQUE INDEX `uk_chain_id`(`chain_id` ASC) USING BTREE,
  INDEX `idx_status_visibility`(`status` ASC, `visibility` ASC) USING BTREE,
  INDEX `idx_cover_file_id`(`cover_file_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 9 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客系列表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_series
-- ----------------------------

-- ----------------------------
-- Table structure for blog_series_catalog
-- ----------------------------
DROP TABLE IF EXISTS `blog_series_catalog`;
CREATE TABLE `blog_series_catalog`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `series_id` bigint(20) NOT NULL,
  `parent_id` bigint(20) NULL DEFAULT NULL,
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `node_type` tinyint(4) NOT NULL DEFAULT 0 COMMENT '0目录 1文章集合 2链接',
  `link_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'node_type=2时有效',
  `link_target` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '_blank' COMMENT '_blank/_self',
  `path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '树路径，如 /1/5/12/',
  `level` int(11) NOT NULL DEFAULT 0,
  `sort_order` int(11) NOT NULL DEFAULT 0,
  `children_count` int(11) NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_series_id`(`series_id` ASC) USING BTREE,
  INDEX `idx_parent_id`(`parent_id` ASC) USING BTREE,
  INDEX `idx_series_parent_sort`(`series_id` ASC, `parent_id` ASC, `sort_order` ASC) USING BTREE,
  INDEX `idx_path`(`path`(191) ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 23 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系列目录节点表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_series_catalog
-- ----------------------------

-- ----------------------------
-- Table structure for blog_series_catalog_post
-- ----------------------------
DROP TABLE IF EXISTS `blog_series_catalog_post`;
CREATE TABLE `blog_series_catalog_post`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `catalog_id` bigint(20) NOT NULL,
  `post_id` bigint(20) NOT NULL,
  `sort_order` int(11) NOT NULL DEFAULT 0,
  `is_primary` tinyint(1) NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_catalog_post`(`catalog_id` ASC, `post_id` ASC) USING BTREE,
  INDEX `idx_post_id`(`post_id` ASC) USING BTREE,
  INDEX `idx_catalog_sort`(`catalog_id` ASC, `sort_order` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 74 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '目录文章关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_series_catalog_post
-- ----------------------------

-- ----------------------------
-- Table structure for blog_tag
-- ----------------------------
DROP TABLE IF EXISTS `blog_tag`;
CREATE TABLE `blog_tag`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '标签名称',
  `slug` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '标签URL标识',
  `use_count` int(10) UNSIGNED NOT NULL DEFAULT 0 COMMENT '使用次数（文章数）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_blog_tag_slug`(`slug` ASC) USING BTREE,
  UNIQUE INDEX `uk_blog_tag_name`(`name` ASC) USING BTREE,
  INDEX `idx_blog_tag_use_count`(`use_count` DESC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 31 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客标签表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_tag
-- ----------------------------

-- ----------------------------
-- Table structure for forge_plugin
-- ----------------------------
DROP TABLE IF EXISTS `forge_plugin`;
CREATE TABLE `forge_plugin`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '插件ID',
  `plugin_key` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '插件唯一标识，对应 plugin.json 的 id',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '插件名称',
  `type` varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'inline' COMMENT '插件类型：inline/view',
  `summary` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '一句话简介',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '插件详情，Markdown 或 HTML',
  `keywords` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '搜索关键词，多个用逗号分隔',
  `icon_file_id` bigint(20) NULL DEFAULT NULL COMMENT '图标文件ID，关联 sys_file',
  `cover_file_id` bigint(20) NULL DEFAULT NULL COMMENT '封面文件ID，关联 sys_file',
  `author_user_id` bigint(20) NULL DEFAULT NULL COMMENT '作者用户ID',
  `author_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '作者展示名',
  `homepage_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '主页地址',
  `repo_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '源码仓库地址',
  `license` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '许可证',
  `pricing_type` tinyint(4) NOT NULL DEFAULT 1 COMMENT '定价类型：1免费 2付费 3订阅 4外部购买',
  `price` decimal(10, 2) NULL DEFAULT NULL COMMENT '展示价格，免费可为空',
  `original_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '原价/划线价',
  `currency` varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'CNY' COMMENT '币种',
  `price_text` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '价格展示文案，如 ¥9/月、联系作者、Pro 版可用',
  `purchase_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '外部购买地址',
  `latest_version_id` bigint(20) NULL DEFAULT NULL COMMENT '最新版本ID',
  `latest_version` varchar(50) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '最新版本号',
  `download_count` bigint(20) NOT NULL DEFAULT 0 COMMENT '下载次数',
  `install_count` bigint(20) NOT NULL DEFAULT 0 COMMENT '安装次数',
  `favorite_count` bigint(20) NOT NULL DEFAULT 0 COMMENT '收藏次数',
  `rating_score` decimal(3, 2) NOT NULL DEFAULT 0.00 COMMENT '评分',
  `rating_count` int(11) NOT NULL DEFAULT 0 COMMENT '评分人数',
  `is_featured` tinyint(4) NOT NULL DEFAULT 0 COMMENT '是否推荐：1是 0否',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序',
  `status` tinyint(4) NOT NULL DEFAULT 0 COMMENT '状态：0草稿 1上架 2下架 3封禁',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT 0,
  `delete_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_forge_plugin_key`(`plugin_key` ASC) USING BTREE,
  INDEX `idx_forge_plugin_list`(`deleted` ASC, `status` ASC, `is_featured` ASC, `sort_order` ASC) USING BTREE,
  INDEX `idx_forge_plugin_author`(`deleted` ASC, `author_user_id` ASC, `status` ASC) USING BTREE,
  INDEX `idx_forge_plugin_pricing`(`deleted` ASC, `pricing_type` ASC, `status` ASC) USING BTREE,
  FULLTEXT INDEX `ft_forge_plugin_search`(`name`, `summary`, `keywords`) WITH PARSER `ngram`
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '插件主表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of forge_plugin
-- ----------------------------

-- ----------------------------
-- Table structure for forge_plugin_category
-- ----------------------------
DROP TABLE IF EXISTS `forge_plugin_category`;
CREATE TABLE `forge_plugin_category`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '插件分类ID',
  `code` varchar(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '分类编码',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '分类名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '分类说明',
  `icon_file_id` bigint(20) NULL DEFAULT NULL COMMENT '分类图标文件ID，关联 sys_file',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT 0,
  `delete_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_forge_plugin_category_code`(`code` ASC) USING BTREE,
  INDEX `idx_forge_plugin_category_sort`(`deleted` ASC, `status` ASC, `sort_order` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '插件分类表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of forge_plugin_category
-- ----------------------------

-- ----------------------------
-- Table structure for forge_plugin_category_rel
-- ----------------------------
DROP TABLE IF EXISTS `forge_plugin_category_rel`;
CREATE TABLE `forge_plugin_category_rel`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '插件分类关联ID',
  `plugin_id` bigint(20) NOT NULL COMMENT '插件ID',
  `category_id` bigint(20) NOT NULL COMMENT '分类ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_forge_plugin_category_rel`(`plugin_id` ASC, `category_id` ASC) USING BTREE,
  INDEX `idx_forge_plugin_category_rel_category`(`category_id` ASC, `plugin_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '插件分类关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of forge_plugin_category_rel
-- ----------------------------

-- ----------------------------
-- Table structure for forge_plugin_download_log
-- ----------------------------
DROP TABLE IF EXISTS `forge_plugin_download_log`;
CREATE TABLE `forge_plugin_download_log`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '插件下载日志ID',
  `user_id` bigint(20) NULL DEFAULT NULL COMMENT '用户ID，匿名下载可为空',
  `plugin_id` bigint(20) NOT NULL COMMENT '插件ID',
  `version_id` bigint(20) NULL DEFAULT NULL COMMENT '插件版本ID',
  `client_version` varchar(50) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '客户端版本',
  `client_os` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户端系统',
  `ip` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '客户端IP',
  `user_agent` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'User-Agent',
  `result` tinyint(4) NOT NULL DEFAULT 1 COMMENT '结果：1成功 0失败',
  `error_msg` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '失败原因',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`, `create_time`) USING BTREE,
  INDEX `idx_forge_plugin_download_plugin`(`plugin_id` ASC, `version_id` ASC, `create_time` ASC) USING BTREE,
  INDEX `idx_forge_plugin_download_user`(`user_id` ASC, `create_time` ASC) USING BTREE,
  INDEX `idx_forge_plugin_download_result`(`result` ASC, `create_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '插件下载日志表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of forge_plugin_download_log
-- ----------------------------

-- ----------------------------
-- Table structure for forge_plugin_favorite
-- ----------------------------
DROP TABLE IF EXISTS `forge_plugin_favorite`;
CREATE TABLE `forge_plugin_favorite`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
  `plugin_id` bigint(20) NOT NULL COMMENT '插件ID',
  `user_id` bigint(20) NOT NULL COMMENT '收藏用户ID',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_forge_plugin_favorite`(`plugin_id` ASC, `user_id` ASC) USING BTREE,
  INDEX `idx_forge_plugin_favorite_user`(`user_id` ASC, `create_time` ASC) USING BTREE,
  INDEX `idx_forge_plugin_favorite_plugin`(`plugin_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '插件收藏表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of forge_plugin_favorite
-- ----------------------------

-- ----------------------------
-- Table structure for forge_plugin_permission
-- ----------------------------
DROP TABLE IF EXISTS `forge_plugin_permission`;
CREATE TABLE `forge_plugin_permission`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '插件权限ID',
  `plugin_id` bigint(20) NOT NULL COMMENT '插件ID',
  `version_id` bigint(20) NOT NULL COMMENT '插件版本ID',
  `permission_code` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '权限编码，如 clipboard.read/network.request',
  `permission_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '权限名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '权限用途说明',
  `risk_level` tinyint(4) NOT NULL DEFAULT 1 COMMENT '风险等级：1低 2中 3高',
  `required` tinyint(4) NOT NULL DEFAULT 1 COMMENT '是否必需：1必需 0可选',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_forge_plugin_permission`(`version_id` ASC, `permission_code` ASC) USING BTREE,
  INDEX `idx_forge_plugin_permission_plugin`(`plugin_id` ASC, `version_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '插件版本权限声明表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of forge_plugin_permission
-- ----------------------------

-- ----------------------------
-- Table structure for forge_plugin_review
-- ----------------------------
DROP TABLE IF EXISTS `forge_plugin_review`;
CREATE TABLE `forge_plugin_review`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '插件评价ID',
  `plugin_id` bigint(20) NOT NULL COMMENT '插件ID',
  `version_id` bigint(20) NULL DEFAULT NULL COMMENT '评价时安装的版本ID',
  `user_id` bigint(20) NOT NULL COMMENT '评价用户ID',
  `rating` tinyint(4) NOT NULL COMMENT '评分：1-5',
  `content` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '评价内容',
  `reply_content` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '作者/管理员回复',
  `reply_by` bigint(20) NULL DEFAULT NULL COMMENT '回复人ID',
  `reply_time` datetime NULL DEFAULT NULL COMMENT '回复时间',
  `like_count` int(11) NOT NULL DEFAULT 0 COMMENT '点赞数',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：0待审 1展示 2隐藏 3拒绝',
  `audit_remark` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '审核备注',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_forge_plugin_review_user`(`plugin_id` ASC, `user_id` ASC) USING BTREE,
  INDEX `idx_forge_plugin_review_plugin`(`plugin_id` ASC, `status` ASC, `create_time` ASC) USING BTREE,
  INDEX `idx_forge_plugin_review_rating`(`plugin_id` ASC, `status` ASC, `rating` ASC) USING BTREE,
  INDEX `idx_forge_plugin_review_user`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '插件评价表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of forge_plugin_review
-- ----------------------------

-- ----------------------------
-- Table structure for forge_plugin_version
-- ----------------------------
DROP TABLE IF EXISTS `forge_plugin_version`;
CREATE TABLE `forge_plugin_version`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '插件版本ID',
  `plugin_id` bigint(20) NOT NULL COMMENT '插件ID',
  `version` varchar(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '版本号，semver',
  `channel` varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'stable' COMMENT '发布通道：stable/beta/dev',
  `manifest_json` json NOT NULL COMMENT 'plugin.json 快照',
  `package_file_id` bigint(20) NULL DEFAULT NULL COMMENT '插件包文件ID，关联 sys_file',
  `package_url` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '插件包外链，可选',
  `package_sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '插件包 SHA256',
  `package_size` bigint(20) NOT NULL DEFAULT 0 COMMENT '包大小，字节',
  `signature` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '包签名，可选',
  `min_app_version` varchar(50) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '最低宿主版本',
  `max_app_version` varchar(50) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '最高宿主版本',
  `changelog` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '更新日志',
  `download_count` bigint(20) NOT NULL DEFAULT 0 COMMENT '当前版本下载次数',
  `review_status` tinyint(4) NOT NULL DEFAULT 0 COMMENT '审核状态：0待审 1通过 2拒绝',
  `review_remark` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '审核备注',
  `published_time` datetime NULL DEFAULT NULL COMMENT '发布时间',
  `status` tinyint(4) NOT NULL DEFAULT 0 COMMENT '状态：0草稿 1已发布 2已下架 3废弃',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT 0,
  `delete_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_forge_plugin_version`(`plugin_id` ASC, `version` ASC) USING BTREE,
  INDEX `idx_forge_plugin_version_status`(`plugin_id` ASC, `deleted` ASC, `status` ASC, `review_status` ASC) USING BTREE,
  INDEX `idx_forge_plugin_version_publish`(`deleted` ASC, `status` ASC, `published_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '插件版本表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of forge_plugin_version
-- ----------------------------

-- ----------------------------
-- Table structure for forge_user_plugin
-- ----------------------------
DROP TABLE IF EXISTS `forge_user_plugin`;
CREATE TABLE `forge_user_plugin`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '用户插件ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `plugin_id` bigint(20) NOT NULL COMMENT '插件ID',
  `version_id` bigint(20) NULL DEFAULT NULL COMMENT '当前安装版本ID',
  `installed_version` varchar(50) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '当前安装版本号',
  `enabled` tinyint(4) NOT NULL DEFAULT 1 COMMENT '是否启用：1启用 0禁用',
  `auto_update` tinyint(4) NOT NULL DEFAULT 1 COMMENT '是否自动更新：1是 0否',
  `config_json` json NULL COMMENT '用户配置，可选同步',
  `window_state_json` json NULL COMMENT '窗口状态，可选同步',
  `install_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '安装时间',
  `last_used_time` datetime NULL DEFAULT NULL COMMENT '最近使用时间',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_forge_user_plugin`(`user_id` ASC, `plugin_id` ASC) USING BTREE,
  INDEX `idx_forge_user_plugin_user`(`user_id` ASC, `enabled` ASC) USING BTREE,
  INDEX `idx_forge_user_plugin_plugin`(`plugin_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户插件安装表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of forge_user_plugin
-- ----------------------------

-- ----------------------------
-- Table structure for scribe_work
-- ----------------------------
DROP TABLE IF EXISTS `scribe_work`;
CREATE TABLE `scribe_work`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '作品ID',
  `user_id` bigint(20) NOT NULL COMMENT '所属作者ID（关联sys_user，所有访问均须匹配）',
  `title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '作品标题（作者自用，平台书名见 scribe_work_platform）',
  `summary` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '一句话简介（列表卡片展示）',
  `logline` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '一句话立意/核心冲突（AI上下文用，不对外）',
  `intro` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '作品简介，纯文本（新建平台档案时作为默认简介）',
  `audience` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '目标读者：male男频/female女频/general不限',
  `genre` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '题材（作者自定义，如 古代悬疑）',
  `tags` json NULL COMMENT '标签数组，如 ["悬疑","慢热"]',
  `protagonists` json NULL COMMENT '主角名数组，如 ["沈砚"]',
  `cover_file_id` bigint(20) NULL DEFAULT NULL COMMENT '封面文件ID（关联sys_file）',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'draft' COMMENT 'draft构思中/serializing连载中/paused暂停/finished已完结',
  `target_word_count` int(11) NULL DEFAULT NULL COMMENT '目标总字数（进度条用）',
  `word_count` int(11) NOT NULL DEFAULT 0 COMMENT '累计字数（章节保存时冗余维护）',
  `chapter_count` int(11) NOT NULL DEFAULT 0 COMMENT '章节数（冗余维护）',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT 0 COMMENT '软删除（回收站）；账号注销时硬删除',
  `delete_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_scribe_work_user_status`(`user_id` ASC, `deleted` ASC, `status` ASC) USING BTREE,
  INDEX `idx_scribe_work_user_update`(`user_id` ASC, `deleted` ASC, `update_time` DESC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '写作台作品表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of scribe_work
-- ----------------------------

-- ----------------------------
-- Table structure for space_bookmark
-- ----------------------------
DROP TABLE IF EXISTS `space_bookmark`;
CREATE TABLE `space_bookmark`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '书签ID',
  `user_id` bigint(20) NOT NULL COMMENT '所属用户ID',
  `folder_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '所属目录ID，0表示未分类',
  `title` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '书签标题',
  `url` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '原始URL',
  `normalized_url` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '规范化URL，用于去重',
  `url_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'URL哈希，用于索引和去重',
  `domain` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '域名',
  `description` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '描述',
  `favicon_url` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '站点图标URL',
  `favicon_file_id` bigint(20) NULL DEFAULT NULL COMMENT '站点图标文件ID，关联sys_file',
  `source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'manual' COMMENT '来源：manual/chrome/import',
  `source_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '外部来源标识',
  `status` tinyint(4) NOT NULL DEFAULT 0 COMMENT '状态：0正常 1归档 2失效',
  `visit_count` int(11) NOT NULL DEFAULT 0 COMMENT '访问次数',
  `last_visit_time` datetime NULL DEFAULT NULL COMMENT '最后访问时间',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '目录内排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT 0,
  `delete_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_space_bookmark_user_folder`(`user_id` ASC, `folder_id` ASC, `deleted` ASC) USING BTREE,
  INDEX `idx_space_bookmark_user_hash`(`user_id` ASC, `url_hash` ASC, `deleted` ASC) USING BTREE,
  INDEX `idx_space_bookmark_domain`(`domain` ASC) USING BTREE,
  INDEX `idx_space_bookmark_status`(`user_id` ASC, `status` ASC, `deleted` ASC) USING BTREE,
  INDEX `idx_space_bookmark_sort`(`folder_id` ASC, `sort_order` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '书签表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of space_bookmark
-- ----------------------------

-- ----------------------------
-- Table structure for space_bookmark_export_task
-- ----------------------------
DROP TABLE IF EXISTS `space_bookmark_export_task`;
CREATE TABLE `space_bookmark_export_task`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '导出任务ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `file_id` bigint(20) NULL DEFAULT NULL COMMENT '导出文件ID，关联sys_file',
  `export_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'chrome_html' COMMENT '导出类型：chrome_html/json',
  `scope_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'all' COMMENT '范围：all/folder/tag',
  `scope_id` bigint(20) NULL DEFAULT NULL COMMENT '范围ID',
  `status` tinyint(4) NOT NULL DEFAULT 0 COMMENT '状态：0待处理 1处理中 2成功 3失败',
  `total_count` int(11) NOT NULL DEFAULT 0 COMMENT '导出数量',
  `error_msg` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '错误信息',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT 0,
  `delete_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_space_bookmark_export_task_user`(`user_id` ASC, `status` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '书签导出任务表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of space_bookmark_export_task
-- ----------------------------

-- ----------------------------
-- Table structure for space_bookmark_folder
-- ----------------------------
DROP TABLE IF EXISTS `space_bookmark_folder`;
CREATE TABLE `space_bookmark_folder`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '目录ID',
  `user_id` bigint(20) NOT NULL COMMENT '所属用户ID',
  `parent_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '父目录ID，0表示根目录',
  `ancestors` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '祖级列表，如0,1,2',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '目录名称',
  `level` int(11) NOT NULL DEFAULT 1 COMMENT '目录层级',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序',
  `source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'manual' COMMENT '来源：manual/chrome/import',
  `source_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '外部来源标识',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT 0,
  `delete_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_space_bookmark_folder_user_parent`(`user_id` ASC, `parent_id` ASC, `deleted` ASC) USING BTREE,
  INDEX `idx_space_bookmark_folder_user_sort`(`user_id` ASC, `sort_order` ASC) USING BTREE,
  INDEX `idx_space_bookmark_folder_ancestors`(`ancestors`(191) ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '书签目录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of space_bookmark_folder
-- ----------------------------

-- ----------------------------
-- Table structure for space_bookmark_import_task
-- ----------------------------
DROP TABLE IF EXISTS `space_bookmark_import_task`;
CREATE TABLE `space_bookmark_import_task`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '导入任务ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `file_id` bigint(20) NULL DEFAULT NULL COMMENT '导入文件ID，关联sys_file',
  `source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'chrome' COMMENT '来源：chrome/html/json',
  `status` tinyint(4) NOT NULL DEFAULT 0 COMMENT '状态：0待处理 1处理中 2成功 3失败',
  `total_count` int(11) NOT NULL DEFAULT 0 COMMENT '总数量',
  `success_count` int(11) NOT NULL DEFAULT 0 COMMENT '成功数量',
  `duplicate_count` int(11) NOT NULL DEFAULT 0 COMMENT '重复数量',
  `fail_count` int(11) NOT NULL DEFAULT 0 COMMENT '失败数量',
  `error_msg` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '错误信息',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT 0,
  `delete_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_space_bookmark_import_task_user`(`user_id` ASC, `status` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '书签导入任务表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of space_bookmark_import_task
-- ----------------------------

-- ----------------------------
-- Table structure for space_bookmark_tag
-- ----------------------------
DROP TABLE IF EXISTS `space_bookmark_tag`;
CREATE TABLE `space_bookmark_tag`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `bookmark_id` bigint(20) NOT NULL COMMENT '书签ID',
  `tag_id` bigint(20) NOT NULL COMMENT '标签ID',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT 0,
  `delete_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_space_bookmark_tag_bookmark`(`bookmark_id` ASC, `deleted` ASC) USING BTREE,
  INDEX `idx_space_bookmark_tag_tag`(`tag_id` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '书签标签关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of space_bookmark_tag
-- ----------------------------

-- ----------------------------
-- Table structure for space_tag
-- ----------------------------
DROP TABLE IF EXISTS `space_tag`;
CREATE TABLE `space_tag`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  `user_id` bigint(20) NOT NULL COMMENT '所属用户ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '标签名称',
  `color` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '标签颜色',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT 0,
  `delete_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_space_tag_user_name`(`user_id` ASC, `name` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '空间标签表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of space_tag
-- ----------------------------

-- ----------------------------
-- Table structure for sys_config
-- ----------------------------
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config`  (
  `id` bigint(20) NOT NULL COMMENT '配置ID',
  `config_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '配置键（唯一，如 site.title）',
  `config_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '配置值（支持长文本、JSON等）',
  `config_type` tinyint(4) NOT NULL DEFAULT 1 COMMENT '类型：1=文本 2=数字 3=布尔 4=JSON',
  `config_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '配置名称（如 网站标题）',
  `group_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'default' COMMENT '配置分组（如 system, email, security）',
  `is_frontend` tinyint(4) NOT NULL DEFAULT 0 COMMENT '是否前端可见：1=是（如主题色） 0=否（如数据库密码）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_config_key`(`config_key` ASC) USING BTREE,
  INDEX `idx_group`(`group_name` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_config
-- ----------------------------
INSERT INTO `sys_config` VALUES (1001, 'captcha.expire.seconds', '120', 2, '验证码过期时间(秒)', 'captcha', 0, '验证码生成后多久过期', NULL, '2026-05-06 07:35:48', NULL, '2026-05-06 07:35:48');
INSERT INTO `sys_config` VALUES (1002, 'captcha.verify.timeout.seconds', '300', 2, '验证通过有效期(秒)', 'captcha', 0, 'verifyToken 过期后需要重新验证', NULL, '2026-05-06 07:35:48', NULL, '2026-05-06 07:35:48');
INSERT INTO `sys_config` VALUES (1003, 'captcha.repeat.interval.seconds', '5', 2, '重复生成最小间隔(秒)', 'captcha', 0, '同 IP 两次 /captcha/get 之间的最小间隔', NULL, '2026-05-06 07:35:48', NULL, '2026-05-06 07:35:48');
INSERT INTO `sys_config` VALUES (1004, 'captcha.type.default', 'blockPuzzle', 1, '默认验证码类型', 'captcha', 1, 'blockPuzzle=滑块, clickWord=文字点选', NULL, '2026-05-06 07:35:48', NULL, '2026-05-06 07:35:48');
INSERT INTO `sys_config` VALUES (1005, 'captcha.type.slider.enabled', '1', 3, '是否启用滑块', 'captcha', 1, '', NULL, '2026-05-06 07:35:48', NULL, '2026-05-06 07:35:48');
INSERT INTO `sys_config` VALUES (1006, 'captcha.type.click.enabled', '1', 3, '是否启用文字点选', 'captcha', 1, '', NULL, '2026-05-06 07:35:48', NULL, '2026-05-06 07:35:48');
INSERT INTO `sys_config` VALUES (1007, 'captcha.image.water.mark', 'Nebula', 1, '验证码图片水印', 'captcha', 1, '', NULL, '2026-05-06 07:35:48', NULL, '2026-05-06 07:35:48');
INSERT INTO `sys_config` VALUES (1008, 'captcha.image.font.type', '宋体', 1, '验证码字体', 'captcha', 0, '', NULL, '2026-05-06 07:35:48', NULL, '2026-05-06 07:35:48');
INSERT INTO `sys_config` VALUES (1009, 'captcha.image.aj.tolerant', '10', 2, '滑块校验容忍像素', 'captcha', 0, '', NULL, '2026-05-06 07:35:48', NULL, '2026-05-07 03:22:17');
INSERT INTO `sys_config` VALUES (1101, 'auth.register.enabled', '1', 3, '是否开启注册', 'auth', 1, '关闭后 /auth/register 直接拒绝', NULL, '2026-05-06 08:03:34', NULL, '2026-05-07 07:22:06');
INSERT INTO `sys_config` VALUES (1102, 'auth.username.min.length', '4', 2, '用户名最小长度', 'auth', 0, '', NULL, '2026-05-06 08:03:34', NULL, '2026-05-06 08:03:34');
INSERT INTO `sys_config` VALUES (1103, 'auth.username.max.length', '32', 2, '用户名最大长度', 'auth', 0, '', NULL, '2026-05-06 08:03:34', NULL, '2026-05-06 08:03:34');
INSERT INTO `sys_config` VALUES (1104, 'auth.password.min.length', '6', 2, '密码最小长度', 'auth', 0, '', NULL, '2026-05-06 08:03:34', NULL, '2026-05-06 08:03:34');
INSERT INTO `sys_config` VALUES (1105, 'auth.password.max.length', '64', 2, '密码最大长度', 'auth', 0, '', NULL, '2026-05-06 08:03:34', NULL, '2026-05-06 08:03:34');

-- ----------------------------
-- Table structure for sys_dict_data
-- ----------------------------
DROP TABLE IF EXISTS `sys_dict_data`;
CREATE TABLE `sys_dict_data`  (
  `id` bigint(20) NOT NULL COMMENT '字典数据ID',
  `dict_type_id` bigint(20) NOT NULL COMMENT '关联的字典类型ID',
  `dict_label` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '字典标签（展示值，如 \"启用\"）',
  `dict_value` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '字典值（实际存储值，如 \"1\"）',
  `css_class` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'CSS类名（前端样式，如 text-success）',
  `list_class` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '列表样式（如 primary, danger）',
  `is_default` tinyint(4) NOT NULL DEFAULT 0 COMMENT '是否默认：1=是',
  `sort` int(11) NOT NULL DEFAULT 0 COMMENT '排序（越小越靠前）',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：1=正常 0=停用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_dict_type_id`(`dict_type_id` ASC) USING BTREE,
  INDEX `idx_sort`(`sort` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '字典数据表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_dict_data
-- ----------------------------

-- ----------------------------
-- Table structure for sys_dict_type
-- ----------------------------
DROP TABLE IF EXISTS `sys_dict_type`;
CREATE TABLE `sys_dict_type`  (
  `id` bigint(20) NOT NULL COMMENT '字典类型ID',
  `type_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '字典类型编码（唯一，如 user_status）',
  `type_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '字典类型名称（如 用户状态）',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：1=正常 0=停用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT 0,
  `delete_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_type_code`(`type_code` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '字典类型表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_dict_type
-- ----------------------------

-- ----------------------------
-- Table structure for sys_file
-- ----------------------------
DROP TABLE IF EXISTS `sys_file`;
CREATE TABLE `sys_file`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '文件ID',
  `target_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '关联业务类型（如：blog_post、user_avatar、travel_note）',
  `target_id` bigint(20) NOT NULL COMMENT '关联业务实体ID',
  `file_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'other' COMMENT '文件用途类型（logo、cover、avatar、attachment、image、video、other）',
  `storage_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '存储类型（local、minio、oss、cos）',
  `storage_platform` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '存储平台标识（minio-prod、aliyun-oss、tencent-cos等）',
  `bucket` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '存储桶名称',
  `object_key` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '对象存储Key/文件路径',
  `url` varchar(750) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件访问URL',
  `original_filename` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '原始文件名',
  `stored_filename` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '存储文件名（重命名后的文件名）',
  `extension` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件扩展名',
  `mime_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'MIME类型',
  `size_bytes` bigint(20) NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
  `width` int(11) NULL DEFAULT NULL COMMENT '图片宽度（像素）',
  `height` int(11) NULL DEFAULT NULL COMMENT '图片高度（像素）',
  `duration` int(11) NULL DEFAULT NULL COMMENT '音视频时长（秒）',
  `hash_sha256` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件SHA256哈希值，用于去重和秒传',
  `is_public` tinyint(4) NOT NULL DEFAULT 1 COMMENT '是否公开（1公开 0私有）',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '文件状态（1正常 0删除 2上传中 3上传失败 4禁用）',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序值',
  `metadata` json NULL COMMENT '扩展元数据（JSON格式）',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '上传人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_target`(`target_type` ASC, `target_id` ASC, `sort_order` ASC) USING BTREE COMMENT '业务关联查询索引',
  INDEX `idx_target_status`(`target_type` ASC, `target_id` ASC, `status` ASC) USING BTREE COMMENT '业务状态查询索引',
  INDEX `idx_hash`(`hash_sha256` ASC) USING BTREE COMMENT '文件哈希索引',
  INDEX `idx_create_by`(`create_by` ASC) USING BTREE COMMENT '上传人索引'
) ENGINE = InnoDB AUTO_INCREMENT = 2064904828278157314 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '统一文件资源表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_file
-- ----------------------------
INSERT INTO `sys_file` VALUES (2064888359892967426, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/0c0cd5f48d52451fa2e0c4901ee22bc5.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/0c0cd5f48d52451fa2e0c4901ee22bc5.png', '1757242319667-0.png', '0c0cd5f48d52451fa2e0c4901ee22bc5.png', 'png', 'image/png', 1154233, NULL, NULL, NULL, 'c8b60b0981113e35555c425f37e0a1d7cf7f99069c30e5c0a0fd2d85d9ddaad5', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 09:52:03', '2026-06-11 09:52:03');
INSERT INTO `sys_file` VALUES (2064888371049816065, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/7738068c8203450a95caf52cf97c62cd.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/7738068c8203450a95caf52cf97c62cd.png', '1757242319667-1.png', '7738068c8203450a95caf52cf97c62cd.png', 'png', 'image/png', 72584, NULL, NULL, NULL, '950801be772f7999ef983be483e075e15825dd5bc743dc092bd7c7e31285f8cd', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 09:52:06', '2026-06-11 09:52:06');
INSERT INTO `sys_file` VALUES (2064888386585518081, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/71b7cbe205d648e8affc5caf372c1ade.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/71b7cbe205d648e8affc5caf372c1ade.png', '1757242319667-2.png', '71b7cbe205d648e8affc5caf372c1ade.png', 'png', 'image/png', 132922, NULL, NULL, NULL, '3b44f2ad25bb2e3f903766e45461e05de75f6bf1a5e2c80f8d80cb9036b546c0', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 09:52:10', '2026-06-11 09:52:10');
INSERT INTO `sys_file` VALUES (2064888403249487874, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/a483385b0c9549c08522daa15ce96900.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/a483385b0c9549c08522daa15ce96900.png', '1757242319667-3.png', 'a483385b0c9549c08522daa15ce96900.png', 'png', 'image/png', 315564, NULL, NULL, NULL, 'acf74fc6bf1ef3fb51cc7b32a75a5548626d587b9c6bb9251613a789dd25a568', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 09:52:14', '2026-06-11 09:52:14');
INSERT INTO `sys_file` VALUES (2064888461420290049, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/6d0e90bb011941019d724a91322e541b.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/6d0e90bb011941019d724a91322e541b.png', '1757242319667-4.png', '6d0e90bb011941019d724a91322e541b.png', 'png', 'image/png', 906970, NULL, NULL, NULL, 'e8009637318ab4de263f9adaaa0becb1b0ebdc033b2b124d53d83620e5c78170', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 09:52:28', '2026-06-11 09:52:28');
INSERT INTO `sys_file` VALUES (2064888481544560642, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/8324a174c82c44b783a9bbd5aa650abc.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/8324a174c82c44b783a9bbd5aa650abc.png', '1757242319667-6.png', '8324a174c82c44b783a9bbd5aa650abc.png', 'png', 'image/png', 164039, NULL, NULL, NULL, '6c1f7d84672f594f4f12f1339e9f7bd5cd9e0444d602f2456a353f827152888c', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 09:52:32', '2026-06-11 09:52:32');
INSERT INTO `sys_file` VALUES (2064888497369669634, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/cacd9a256bfc42bdb3f5344613c6fad0.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/cacd9a256bfc42bdb3f5344613c6fad0.png', '1757242319667-5.png', 'cacd9a256bfc42bdb3f5344613c6fad0.png', 'png', 'image/png', 74079, NULL, NULL, NULL, 'af01a3725baa6da04713c4c31b3aa21e24dd304c365304a3f9d3bb94624a8597', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 09:52:36', '2026-06-11 09:52:36');
INSERT INTO `sys_file` VALUES (2064888508815925250, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/b7cedf103d6841578ba37e74172ae38c.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/b7cedf103d6841578ba37e74172ae38c.png', '1757242319667-18.png', 'b7cedf103d6841578ba37e74172ae38c.png', 'png', 'image/png', 89771, NULL, NULL, NULL, '4ad9a8bd60b23be30a84df4f1d1a688571c93d6a0a0e7b7c1b11b6cc51803da4', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 09:52:39', '2026-06-11 09:52:39');
INSERT INTO `sys_file` VALUES (2064903745719906305, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/4a747d89cb6f4154a0f1327efff009a5.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/4a747d89cb6f4154a0f1327efff009a5.png', '1757246501849-00.png', '4a747d89cb6f4154a0f1327efff009a5.png', 'png', 'image/png', 108477, NULL, NULL, NULL, 'b1f34ec65e266a8949696fb440c4dda0aaea5650f9125e4e6dcab3365ea594c1', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:12', '2026-06-11 10:53:12');
INSERT INTO `sys_file` VALUES (2064903751910699010, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/ebe2a225a8c843cdb461bb2914c56941.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/ebe2a225a8c843cdb461bb2914c56941.png', '1757246501849-0.png', 'ebe2a225a8c843cdb461bb2914c56941.png', 'png', 'image/png', 63720, NULL, NULL, NULL, 'b48d18732b1780e57fdca8d6c5b0ee60e9797fc47373679641dd59ff2fe4c78a', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:13', '2026-06-11 10:53:13');
INSERT INTO `sys_file` VALUES (2064903759405920258, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/888c8ba5e01c432cb5cdccb974b3b185.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/888c8ba5e01c432cb5cdccb974b3b185.png', '1757246501849-1.png', '888c8ba5e01c432cb5cdccb974b3b185.png', 'png', 'image/png', 128840, NULL, NULL, NULL, 'adfd9609b5df9a60342d14793f6fcc7d6b4c1452b1f7527563818e9a2f7b4179', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:15', '2026-06-11 10:53:15');
INSERT INTO `sys_file` VALUES (2064903768746635265, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/c3dc9a0ee3da4c498f3b0fd751ff2e5d.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/c3dc9a0ee3da4c498f3b0fd751ff2e5d.png', '1757246501849-2.png', 'c3dc9a0ee3da4c498f3b0fd751ff2e5d.png', 'png', 'image/png', 70630, NULL, NULL, NULL, '8fa4f753959e4ac713ec565e8b5370d71a5e8221ecfb0442d8902bb8f77336e4', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:17', '2026-06-11 10:53:17');
INSERT INTO `sys_file` VALUES (2064903783141486593, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/779e4800fb03499e84169985c61c045b.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/779e4800fb03499e84169985c61c045b.png', '1757246501849-3.png', '779e4800fb03499e84169985c61c045b.png', 'png', 'image/png', 1371298, NULL, NULL, NULL, '9aba24d9bef4235899fd684846bb53e24f18a7833b7a44d44f2aefbbae9164aa', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:21', '2026-06-11 10:53:21');
INSERT INTO `sys_file` VALUES (2064903789290336257, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/bff40f0fad8d466fa09d0387cfc1d0db.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/bff40f0fad8d466fa09d0387cfc1d0db.png', '1757246501849-4.png', 'bff40f0fad8d466fa09d0387cfc1d0db.png', 'png', 'image/png', 55091, NULL, NULL, NULL, 'ae083b1d11f5c5cc65116cdd45e3ea2459b5f8dca5a47bf6e4c70fc2639de81b', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:22', '2026-06-11 10:53:22');
INSERT INTO `sys_file` VALUES (2064903800359104513, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/8ab85a9f6f474d1ca192aa4005c5e1c1.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/8ab85a9f6f474d1ca192aa4005c5e1c1.png', '1757246501849-5.png', '8ab85a9f6f474d1ca192aa4005c5e1c1.png', 'png', 'image/png', 1219243, NULL, NULL, NULL, '9fc2115476c09ecc90f563eccdf150b02838ca15f2b9cd741ac36a5d846fbc3d', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:25', '2026-06-11 10:53:25');
INSERT INTO `sys_file` VALUES (2064903809175531522, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/4c6c0e12fa80400694ac08aea9841051.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/4c6c0e12fa80400694ac08aea9841051.png', '1757246501849-6.png', '4c6c0e12fa80400694ac08aea9841051.png', 'png', 'image/png', 104677, NULL, NULL, NULL, '8e7e8879df8bd42b8229669f96f0a1e24b370a0e2f06a9123887a1f05220b3da', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:27', '2026-06-11 10:53:27');
INSERT INTO `sys_file` VALUES (2064903816947576834, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/971b330abf394c3aac1817c38e761014.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/971b330abf394c3aac1817c38e761014.png', '1757246501849-7.png', '971b330abf394c3aac1817c38e761014.png', 'png', 'image/png', 142695, NULL, NULL, NULL, '885b2b02b5728e9837b24e6e0c8a9b55699cdcd21f38eb466bfb049f3d66db0b', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:29', '2026-06-11 10:53:29');
INSERT INTO `sys_file` VALUES (2064903827433336833, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/df97d5cdb2434fb9a9c6c951d04b1604.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/df97d5cdb2434fb9a9c6c951d04b1604.png', '1757246501849-8.png', 'df97d5cdb2434fb9a9c6c951d04b1604.png', 'png', 'image/png', 306253, NULL, NULL, NULL, '79a45b16440afef8cebd98d6ebd53176378acc55c6f2357e71760cde640710e7', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:31', '2026-06-11 10:53:31');
INSERT INTO `sys_file` VALUES (2064903836329455617, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/824d06ddf8094557855ec64120bb724e.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/824d06ddf8094557855ec64120bb724e.png', '1757246501849-9.png', '824d06ddf8094557855ec64120bb724e.png', 'png', 'image/png', 643767, NULL, NULL, NULL, 'ec4f828e5f325e120536623238c4cafb3701accc31ef1ad897213ec8a028477c', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:33', '2026-06-11 10:53:33');
INSERT INTO `sys_file` VALUES (2064903856269176833, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/cd83d80f9a0b4219975ac4c2e4537b9f.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/cd83d80f9a0b4219975ac4c2e4537b9f.png', '1757246501849-10.png', 'cd83d80f9a0b4219975ac4c2e4537b9f.png', 'png', 'image/png', 4275627, NULL, NULL, NULL, '740689b843b434e56cf22f4f4ec604dcbaa52511af65daf47821621fd4a7faf5', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:38', '2026-06-11 10:53:38');
INSERT INTO `sys_file` VALUES (2064903878272495618, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/a97fe5bd07cc40b48db9f60da03b8e82.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/a97fe5bd07cc40b48db9f60da03b8e82.png', '1757249275674-0.png', 'a97fe5bd07cc40b48db9f60da03b8e82.png', 'png', 'image/png', 37817, NULL, NULL, NULL, '9179d764594d8f48bae641cbbacd023c7a15c41cc5e9aa7e7acf433e259f0f1d', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:43', '2026-06-11 10:53:43');
INSERT INTO `sys_file` VALUES (2064903904746942465, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/1803a4d42749442794a3d433963d8a2d.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/1803a4d42749442794a3d433963d8a2d.png', '1757249275674-1.png', '1803a4d42749442794a3d433963d8a2d.png', 'png', 'image/png', 238696, NULL, NULL, NULL, 'd652cb4c22828061e2bcaadd56e168d0815e2efc4cf8585c5fbec4653d2da610', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:50', '2026-06-11 10:53:50');
INSERT INTO `sys_file` VALUES (2064903910899986434, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/a207e4e441554a5aaa2f9ef2b720f4cd.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/a207e4e441554a5aaa2f9ef2b720f4cd.png', '1757249275674-2.png', 'a207e4e441554a5aaa2f9ef2b720f4cd.png', 'png', 'image/png', 52838, NULL, NULL, NULL, '46356991019082bf5a80f7f317f4923bca2e95c0615c3334bd2698f889ed9999', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:51', '2026-06-11 10:53:51');
INSERT INTO `sys_file` VALUES (2064903918336487425, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/78dbbcb0ac3f497aab4d25c14ff8d0f8.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/78dbbcb0ac3f497aab4d25c14ff8d0f8.png', '1757249275674-3.png', '78dbbcb0ac3f497aab4d25c14ff8d0f8.png', 'png', 'image/png', 214390, NULL, NULL, NULL, '93e4d33d0e9fa59a475e405da641d93ca2456c6c82c1df78e76bf4b46d71a2e8', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:53', '2026-06-11 10:53:53');
INSERT INTO `sys_file` VALUES (2064903925382918146, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/1d0acce9d8ba43fd92dcb252a9c7aef4.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/1d0acce9d8ba43fd92dcb252a9c7aef4.png', '1757249275674-4.png', '1d0acce9d8ba43fd92dcb252a9c7aef4.png', 'png', 'image/png', 162804, NULL, NULL, NULL, 'cca7732908d425db8f3f83c40c145c683e32cde4271d5be3d29f3eca7bda051b', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:55', '2026-06-11 10:53:55');
INSERT INTO `sys_file` VALUES (2064903931133308930, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/acad4252e9174fbd9c529c0903273e29.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/acad4252e9174fbd9c529c0903273e29.png', '1757249275674-5.png', 'acad4252e9174fbd9c529c0903273e29.png', 'png', 'image/png', 114681, NULL, NULL, NULL, '34917fb5f0330f703140c6ca0a735728ef6ef76abaf10b01ede3f6f6803ce961', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:56', '2026-06-11 10:53:56');
INSERT INTO `sys_file` VALUES (2064903945951784962, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/2d9d3ef850cd480cbac0033d6b1a5162.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/2d9d3ef850cd480cbac0033d6b1a5162.png', '4-1.png', '2d9d3ef850cd480cbac0033d6b1a5162.png', 'png', 'image/png', 42472, NULL, NULL, NULL, 'c997de462c19c6ba9c5738bedf89a1ab3c67f93fffb75355705814936365601f', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:59', '2026-06-11 10:53:59');
INSERT INTO `sys_file` VALUES (2064903951932862465, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/51ecc6c9a493476a985819da3a0c9c35.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/51ecc6c9a493476a985819da3a0c9c35.png', '4-2.png', '51ecc6c9a493476a985819da3a0c9c35.png', 'png', 'image/png', 94507, NULL, NULL, NULL, 'ae484853fddbf066be140cacf3a4d1e1c0aa0a8f352655a15e75fcfdfded8ee4', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:01', '2026-06-11 10:54:01');
INSERT INTO `sys_file` VALUES (2064903958836686849, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/2c5bdbced70949319f303e0cd2dfd07d.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/2c5bdbced70949319f303e0cd2dfd07d.png', '4-3.png', '2c5bdbced70949319f303e0cd2dfd07d.png', 'png', 'image/png', 114243, NULL, NULL, NULL, '8b9aeb1565c9c26bd32d99903f80f7c8359004843c37d3aa3b69b49b126da1c6', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:03', '2026-06-11 10:54:03');
INSERT INTO `sys_file` VALUES (2064903965761482753, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/44af2672a8e443cea318063651099827.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/44af2672a8e443cea318063651099827.png', '4-4.png', '44af2672a8e443cea318063651099827.png', 'png', 'image/png', 154150, NULL, NULL, NULL, '9bc1f3895bff3c592901f06078e3d869ada7b85ce4807479afc8050d9e992bd0', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:04', '2026-06-11 10:54:04');
INSERT INTO `sys_file` VALUES (2064903991350931457, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/8cb98c8c625b4780b29145ff7790ad8d.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/8cb98c8c625b4780b29145ff7790ad8d.png', 'coze-01.png', '8cb98c8c625b4780b29145ff7790ad8d.png', 'png', 'image/png', 3813198, NULL, NULL, NULL, '68ab64cb8ddbc10cc63bb1d4e27ed238a43055044fc95dfaafef3661f065c9ef', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:10', '2026-06-11 10:54:10');
INSERT INTO `sys_file` VALUES (2064903998426722306, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/b5402d17bb5c4ee8afb8c03ba47bae23.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/b5402d17bb5c4ee8afb8c03ba47bae23.png', 'coze-02.png', 'b5402d17bb5c4ee8afb8c03ba47bae23.png', 'png', 'image/png', 192506, NULL, NULL, NULL, '388bcac25f88c4c33183c34b463769a32bbbbda763839a273d2d68073e3857e0', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:12', '2026-06-11 10:54:12');
INSERT INTO `sys_file` VALUES (2064904009738760194, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/e0f4a7f22d8243c0a7631f3d1d728fdd.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/e0f4a7f22d8243c0a7631f3d1d728fdd.png', 'coze-03.png', 'e0f4a7f22d8243c0a7631f3d1d728fdd.png', 'png', 'image/png', 654909, NULL, NULL, NULL, '92f0d2539e40d100c9f0790d4263efd0e7cabf3803443e2fe90e53423fdd790c', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:15', '2026-06-11 10:54:15');
INSERT INTO `sys_file` VALUES (2064904021210181634, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/76349e51d3d6483a84ee0fd324b1f617.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/76349e51d3d6483a84ee0fd324b1f617.png', 'coze-04.png', '76349e51d3d6483a84ee0fd324b1f617.png', 'png', 'image/png', 336180, NULL, NULL, NULL, 'a7ebfb1733cc78b7d6c866241d722f46809d835b0045c14c951a3d0191104895', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:17', '2026-06-11 10:54:17');
INSERT INTO `sys_file` VALUES (2064904029368102913, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/dd39bdb5c30a4aaa960e38085f3b93be.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/dd39bdb5c30a4aaa960e38085f3b93be.png', 'coze-05.png', 'dd39bdb5c30a4aaa960e38085f3b93be.png', 'png', 'image/png', 373621, NULL, NULL, NULL, '49023cb75545486172f469ef479389de40e874db782848a5e593045ea2ca6a52', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:19', '2026-06-11 10:54:19');
INSERT INTO `sys_file` VALUES (2064904040537534466, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/7c5b0a0e9ebb4aa09be6af9261096392.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/7c5b0a0e9ebb4aa09be6af9261096392.png', 'coze-06.png', '7c5b0a0e9ebb4aa09be6af9261096392.png', 'png', 'image/png', 302861, NULL, NULL, NULL, 'b26c5e7f3327654bdd62787ffecfd2b0c7536016372cfa9e6b2a8d7f6ca7d827', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:22', '2026-06-11 10:54:22');
INSERT INTO `sys_file` VALUES (2064904049202966530, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/b8e009de10874fb7bcf2b33391a83168.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/b8e009de10874fb7bcf2b33391a83168.png', 'coze-07.png', 'b8e009de10874fb7bcf2b33391a83168.png', 'png', 'image/png', 110281, NULL, NULL, NULL, 'd0c3e2bc7d209ac1096de717b085f8ad5998368a5d269f728e469e1711132594', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:24', '2026-06-11 10:54:24');
INSERT INTO `sys_file` VALUES (2064904055720914946, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/7fb0878cf95d4bd9baa77e2f168601f4.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/7fb0878cf95d4bd9baa77e2f168601f4.png', 'coze-08.png', '7fb0878cf95d4bd9baa77e2f168601f4.png', 'png', 'image/png', 118459, NULL, NULL, NULL, '85197a4b0e62dc2cdd277c3bff914fa90de933502105d5da304b3ff6b98467a8', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:26', '2026-06-11 10:54:26');
INSERT INTO `sys_file` VALUES (2064904068656148482, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/151767b00802432489d4affb8299a6e3.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/151767b00802432489d4affb8299a6e3.png', 'coze-09.png', '151767b00802432489d4affb8299a6e3.png', 'png', 'image/png', 616541, NULL, NULL, NULL, 'aa6bf2faff834e62a699d8c2a61865b177d76d7862b226342033604437f5df83', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:29', '2026-06-11 10:54:29');
INSERT INTO `sys_file` VALUES (2064904077296414722, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/62f82901206b413494638114238e5113.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/62f82901206b413494638114238e5113.png', 'coze-10.png', '62f82901206b413494638114238e5113.png', 'png', 'image/png', 186777, NULL, NULL, NULL, '30154c3dbf04c3fa6c438d97869074d19f983339e3562fc5c8404afd41c37c2a', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:31', '2026-06-11 10:54:31');
INSERT INTO `sys_file` VALUES (2064904089262764034, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/3af246a015414b9597c802e067d0630f.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/3af246a015414b9597c802e067d0630f.png', 'coze-11.png', '3af246a015414b9597c802e067d0630f.png', 'png', 'image/png', 324153, NULL, NULL, NULL, '4b0315524d5a63b7a6b91588ba31f4d44b9c3bee6533e76e175c621d8b617611', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:34', '2026-06-11 10:54:34');
INSERT INTO `sys_file` VALUES (2064904102785200130, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/47a7228503864b41ac8b9396714513b0.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/47a7228503864b41ac8b9396714513b0.png', 'coze-12.png', '47a7228503864b41ac8b9396714513b0.png', 'png', 'image/png', 589670, NULL, NULL, NULL, 'a79cce0e37c14cafbb7da4ecc0ad5abc3cbb6992c94275d37fc444c613fb344d', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:37', '2026-06-11 10:54:37');
INSERT INTO `sys_file` VALUES (2064904109739356162, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/f3675746a10546179147448cadc9737b.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/f3675746a10546179147448cadc9737b.png', 'coze-13.png', 'f3675746a10546179147448cadc9737b.png', 'png', 'image/png', 66887, NULL, NULL, NULL, '107b23c3b46123bb9fe8ba7ab0a846493c8d24bceaa00de45bb0d6976023581c', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:38', '2026-06-11 10:54:38');
INSERT INTO `sys_file` VALUES (2064904116873867266, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/66011b70e61c4e159f18b561326fbec2.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/66011b70e61c4e159f18b561326fbec2.png', 'coze-14.png', '66011b70e61c4e159f18b561326fbec2.png', 'png', 'image/png', 84189, NULL, NULL, NULL, 'f33051f820ce1529ee1cb737d292056b072567e6e917230588a4bc3a71dbcc02', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:40', '2026-06-11 10:54:40');
INSERT INTO `sys_file` VALUES (2064904125472190466, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/79318d445de94fd396d16fe0b14f5ce6.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/79318d445de94fd396d16fe0b14f5ce6.png', 'dify-01.png', '79318d445de94fd396d16fe0b14f5ce6.png', 'png', 'image/png', 191972, NULL, NULL, NULL, 'b445ce4edc67ae43a61c0c24abb622ecc764deb69ea5165d0600c6bb04a318e7', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:42', '2026-06-11 10:54:42');
INSERT INTO `sys_file` VALUES (2064904138378063873, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/f597de5e0a5a45128ad3d9419c6e18e4.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/f597de5e0a5a45128ad3d9419c6e18e4.png', 'dify-02.png', 'f597de5e0a5a45128ad3d9419c6e18e4.png', 'png', 'image/png', 315319, NULL, NULL, NULL, '2cfc4b3e3033393305f55c81502bd64d10bec6eaf0db2078b3445f6ac0ea1bdc', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:45', '2026-06-11 10:54:45');
INSERT INTO `sys_file` VALUES (2064904149228728322, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/780b2af3abf14116a6a8ba093172dbd9.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/780b2af3abf14116a6a8ba093172dbd9.png', 'dify-14.png', '780b2af3abf14116a6a8ba093172dbd9.png', 'png', 'image/png', 196443, NULL, NULL, NULL, 'a2e93743862cdeba3e62ae587731824eaac24b060402e2d6cfadfc07e9c4547f', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:48', '2026-06-11 10:54:48');
INSERT INTO `sys_file` VALUES (2064904162050715649, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/319f84b7ae64416183da78e39642c119.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/319f84b7ae64416183da78e39642c119.png', 'dify-18.png', '319f84b7ae64416183da78e39642c119.png', 'png', 'image/png', 373834, NULL, NULL, NULL, '4ba1d7ee0bbf573a1f051b4d0f385a170d1546097864d0524e02e3de3e392a18', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:51', '2026-06-11 10:54:51');
INSERT INTO `sys_file` VALUES (2064904175682203650, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/9a0a6fb38e944c19a5d9e8df0fb694f3.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/9a0a6fb38e944c19a5d9e8df0fb694f3.png', 'dify-15.png', '9a0a6fb38e944c19a5d9e8df0fb694f3.png', 'png', 'image/png', 379467, NULL, NULL, NULL, '79886b03923330e31787915d89e7c2fda84a008cf92a7a1dcb9f7f2c8de2118e', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:54', '2026-06-11 10:54:54');
INSERT INTO `sys_file` VALUES (2064904188072177666, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/787d5887d98b4435b4c9a1bc0debf693.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/787d5887d98b4435b4c9a1bc0debf693.png', 'dify-16.png', '787d5887d98b4435b4c9a1bc0debf693.png', 'png', 'image/png', 376657, NULL, NULL, NULL, '8654e3a74008ec1e932696aece60e4b71a67f3a2cc0d026ef1488b276968172a', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:57', '2026-06-11 10:54:57');
INSERT INTO `sys_file` VALUES (2064904197576470529, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/a7fc33e3ef484102b9b50974457e73f9.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/a7fc33e3ef484102b9b50974457e73f9.png', 'dify-17.png', 'a7fc33e3ef484102b9b50974457e73f9.png', 'png', 'image/png', 207539, NULL, NULL, NULL, 'f4bc344f42d6b52751e537c4c2faf103866b3f65c5f25be9d731a35a5f309616', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:59', '2026-06-11 10:54:59');
INSERT INTO `sys_file` VALUES (2064904211405090818, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/cf6240ce65dd4d39b5583001a940b049.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/cf6240ce65dd4d39b5583001a940b049.png', 'dify-19.png', 'cf6240ce65dd4d39b5583001a940b049.png', 'png', 'image/png', 503860, NULL, NULL, NULL, 'dbb5c041fa4093bfd166efb265881c7daa2e8d6d767f788d9a0740675dfdd49e', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:03', '2026-06-11 10:55:03');
INSERT INTO `sys_file` VALUES (2064904229323157506, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/f6967c59b88a4a91912e65454bdfef63.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/f6967c59b88a4a91912e65454bdfef63.png', 'dify-20.png', 'f6967c59b88a4a91912e65454bdfef63.png', 'png', 'image/png', 2014265, NULL, NULL, NULL, '669953d9828ab396b9c631e8388b9082a39b16c5ccb28abfd93cd9dafabc1f6e', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:07', '2026-06-11 10:55:07');
INSERT INTO `sys_file` VALUES (2064904243587985410, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/ef60afbe9514480bacc0a9049bc3cd04.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/ef60afbe9514480bacc0a9049bc3cd04.png', 'dify-21.png', 'ef60afbe9514480bacc0a9049bc3cd04.png', 'png', 'image/png', 1822376, NULL, NULL, NULL, '575e91864cc8f31ca227c3be6e946afe69a14b6920b63423177b9c931596356e', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:10', '2026-06-11 10:55:10');
INSERT INTO `sys_file` VALUES (2064904255617249282, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/45b8a8c2fcb84225afd83d2cc3f0fc8e.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/45b8a8c2fcb84225afd83d2cc3f0fc8e.png', 'dify-12.png', '45b8a8c2fcb84225afd83d2cc3f0fc8e.png', 'png', 'image/png', 1816760, NULL, NULL, NULL, 'b2e326a50f71d8b6750108301653cf481b67ceeffe7f428b1488b55f174a8145', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:13', '2026-06-11 10:55:13');
INSERT INTO `sys_file` VALUES (2064904265985568769, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/f1d1489bf911406197e7aa60675c29e2.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/f1d1489bf911406197e7aa60675c29e2.png', 'dify-03.png', 'f1d1489bf911406197e7aa60675c29e2.png', 'png', 'image/png', 177566, NULL, NULL, NULL, '1cc570c2afa84b9db8fcc0cd0d0d786ca8b677fb745b751172985edaa76fbf13', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:16', '2026-06-11 10:55:16');
INSERT INTO `sys_file` VALUES (2064904276144173057, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/6f7190f12f9449ba8a5a8ded9e03a480.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/6f7190f12f9449ba8a5a8ded9e03a480.png', 'dify-04.png', '6f7190f12f9449ba8a5a8ded9e03a480.png', 'png', 'image/png', 355773, NULL, NULL, NULL, '313589e3101b6411c74cf400e37be6cce2684b67f5f6e29a9a3bcef503ca9290', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:18', '2026-06-11 10:55:18');
INSERT INTO `sys_file` VALUES (2064904285363253250, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/e87847523875417f8f1d2af1cec0ccf8.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/e87847523875417f8f1d2af1cec0ccf8.png', 'dify-13.png', 'e87847523875417f8f1d2af1cec0ccf8.png', 'png', 'image/png', 170711, NULL, NULL, NULL, '549b4e1f46d345b2be7c2b19e4b171c6993d1ac4074bce621cd162a777fdb868', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:20', '2026-06-11 10:55:20');
INSERT INTO `sys_file` VALUES (2064904293554728961, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/a7e73800967d412191445c6f45fba6fb.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/a7e73800967d412191445c6f45fba6fb.png', 'dify-05.png', 'a7e73800967d412191445c6f45fba6fb.png', 'png', 'image/png', 437192, NULL, NULL, NULL, '96d08e9dd995866b93ec495bcf90fa376ca2a95f5adf391568fc19d5386507c0', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:22', '2026-06-11 10:55:22');
INSERT INTO `sys_file` VALUES (2064904300294975490, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/66c7566b61c541d4ac2581d01729ae4d.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/66c7566b61c541d4ac2581d01729ae4d.png', 'dify-06.png', '66c7566b61c541d4ac2581d01729ae4d.png', 'png', 'image/png', 113032, NULL, NULL, NULL, '8132307610de839d29cc0f6069a48b188224159d9bc756965c1c743e4cb52556', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:24', '2026-06-11 10:55:24');
INSERT INTO `sys_file` VALUES (2064904307572092929, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/ea62e4344c244512bb8bc6ca06b85632.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/ea62e4344c244512bb8bc6ca06b85632.png', 'dify-22.png', 'ea62e4344c244512bb8bc6ca06b85632.png', 'png', 'image/png', 221300, NULL, NULL, NULL, 'ddc1e3b86246930cd043f99e8584cfbdc7267f55bec7a8569b7262cf50b4eb13', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:26', '2026-06-11 10:55:26');
INSERT INTO `sys_file` VALUES (2064904315302195202, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/58d9e8a945594a3ea96eda89b197de13.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/58d9e8a945594a3ea96eda89b197de13.png', 'dify-07.png', '58d9e8a945594a3ea96eda89b197de13.png', 'png', 'image/png', 109395, NULL, NULL, NULL, 'cda20825f4f7610a59ab3577f4c0fffae029f3b6787eadea648c8fcf4011a8a3', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:27', '2026-06-11 10:55:27');
INSERT INTO `sys_file` VALUES (2064904322638032898, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/13a6f89e30f642399b8d3f8be1e403dc.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/13a6f89e30f642399b8d3f8be1e403dc.png', 'dify-08.png', '13a6f89e30f642399b8d3f8be1e403dc.png', 'png', 'image/png', 208304, NULL, NULL, NULL, 'c27951fa4d698e2f30d248d1e42e91dad6c4dde0f979248314cdaa0d60deab2e', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:29', '2026-06-11 10:55:29');
INSERT INTO `sys_file` VALUES (2064904332767277057, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/f92a1b75001d4bec99831ac4f21e6a1d.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/f92a1b75001d4bec99831ac4f21e6a1d.png', 'dify-23.png', 'f92a1b75001d4bec99831ac4f21e6a1d.png', 'png', 'image/png', 80972, NULL, NULL, NULL, '82abb9f34bf70aeedac6ca026d31872e0b4d7e1897ab3d010f39627dcbe66df0', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:32', '2026-06-11 10:55:32');
INSERT INTO `sys_file` VALUES (2064904340082143233, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/e13316a5b55840a5a7fd27ff0b035aaf.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/e13316a5b55840a5a7fd27ff0b035aaf.png', 'dify-09.png', 'e13316a5b55840a5a7fd27ff0b035aaf.png', 'png', 'image/png', 178776, NULL, NULL, NULL, 'b5487539a9c4bc7a349073d38a387335b5736b2e663512553df0cbaf3bcda18d', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:33', '2026-06-11 10:55:33');
INSERT INTO `sys_file` VALUES (2064904348462362625, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/4d83f03824fd4434a70a49f59196d4db.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/4d83f03824fd4434a70a49f59196d4db.png', 'dify-10.png', '4d83f03824fd4434a70a49f59196d4db.png', 'png', 'image/png', 217348, NULL, NULL, NULL, 'f574b5f8f33a5940e49c577737f2e4d3061ee7852faf026d88ca1b6ffbee794c', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:35', '2026-06-11 10:55:35');
INSERT INTO `sys_file` VALUES (2064904355630428161, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/bc78023eac964fa494770162e8be2f0b.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/bc78023eac964fa494770162e8be2f0b.png', 'dify-11.png', 'bc78023eac964fa494770162e8be2f0b.png', 'png', 'image/png', 144783, NULL, NULL, NULL, 'f5666da008b13324dc2f07305a530a461688df250ebb1decb6897fabb5257d62', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:37', '2026-06-11 10:55:37');
INSERT INTO `sys_file` VALUES (2064904362911739906, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/dc8cf53590a44118bb17b592bda58e71.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/dc8cf53590a44118bb17b592bda58e71.png', 'n8n-01.png', 'dc8cf53590a44118bb17b592bda58e71.png', 'png', 'image/png', 174064, NULL, NULL, NULL, '6be74e75b84134c502387b33f81a61351e3e6f758168b490b290258c830cec3e', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:39', '2026-06-11 10:55:39');
INSERT INTO `sys_file` VALUES (2064904370042056706, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/a9a2a492fa8a44b8ae608de46f77f8c2.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/a9a2a492fa8a44b8ae608de46f77f8c2.png', 'n8n-02.png', 'a9a2a492fa8a44b8ae608de46f77f8c2.png', 'png', 'image/png', 183483, NULL, NULL, NULL, '74af58bc8e2b400dc66fb08f42ed1606078dfc06280ab4fdb224c335d585b49d', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:41', '2026-06-11 10:55:41');
INSERT INTO `sys_file` VALUES (2064904377256259586, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/97eea5d9fd9740389e6252c1c22aeeac.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/97eea5d9fd9740389e6252c1c22aeeac.png', 'n8n-03.png', '97eea5d9fd9740389e6252c1c22aeeac.png', 'png', 'image/png', 166133, NULL, NULL, NULL, 'ee2c2d7e07562b433ef0bfa3f913e8ae3a45c469b1fadd65a9e47603be7493d1', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:42', '2026-06-11 10:55:42');
INSERT INTO `sys_file` VALUES (2064904384378187777, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/1628e692a090466ead4f7952a4ea98f8.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/1628e692a090466ead4f7952a4ea98f8.png', 'n8n-04.png', '1628e692a090466ead4f7952a4ea98f8.png', 'png', 'image/png', 178269, NULL, NULL, NULL, 'c07fb77a89e2656a77e46b6346318d3b75e06ad9f3a60238c89702b5772c87b3', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:44', '2026-06-11 10:55:44');
INSERT INTO `sys_file` VALUES (2064904391558836225, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/1a393d5936364715847d93f43a9a1780.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/1a393d5936364715847d93f43a9a1780.png', 'n8n-05.png', '1a393d5936364715847d93f43a9a1780.png', 'png', 'image/png', 102379, NULL, NULL, NULL, '7940ce71c09729618ffe593a8aba46fd0321935de4be101270f6dc5a19575320', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:46', '2026-06-11 10:55:46');
INSERT INTO `sys_file` VALUES (2064904397984509953, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/f7fe2182283a4cbb9602bb5ebc6fef48.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/f7fe2182283a4cbb9602bb5ebc6fef48.png', 'n8n-06.png', 'f7fe2182283a4cbb9602bb5ebc6fef48.png', 'png', 'image/png', 79096, NULL, NULL, NULL, '5655082069e6773be4ba9d51652d3f59c9b510fd094301e932bc20942f18c5a4', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:47', '2026-06-11 10:55:47');
INSERT INTO `sys_file` VALUES (2064904404447932417, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/20f4b89a729d47ef80e8497d26855a9b.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/20f4b89a729d47ef80e8497d26855a9b.png', 'n8n-07.png', '20f4b89a729d47ef80e8497d26855a9b.png', 'png', 'image/png', 83351, NULL, NULL, NULL, '3f71fb2f839fe29d10dbfcd924ebf764a8d34c8c80503ee5a0f66cc3cc2153c6', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:49', '2026-06-11 10:55:49');
INSERT INTO `sys_file` VALUES (2064904411590832130, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/213d9fe2d2174cc3b698192b8912ebf4.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/213d9fe2d2174cc3b698192b8912ebf4.png', 'n8n-08.png', '213d9fe2d2174cc3b698192b8912ebf4.png', 'png', 'image/png', 181541, NULL, NULL, NULL, 'a5a099f27cc7caa79fb455347769eafcc5cab6acd714e31d171b8902bf0af17e', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:50', '2026-06-11 10:55:50');
INSERT INTO `sys_file` VALUES (2064904418830200834, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/b8c50ef807e143899836dadc9887b441.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/b8c50ef807e143899836dadc9887b441.png', 'n8n-09.png', 'b8c50ef807e143899836dadc9887b441.png', 'png', 'image/png', 154952, NULL, NULL, NULL, '77a9ca4034648e0fbe1a5526116034529d4f103e16ea64aac64ff619c0169ef6', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:52', '2026-06-11 10:55:52');
INSERT INTO `sys_file` VALUES (2064904426430279681, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/80814c16d877401fa1bbed9df50266f6.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/80814c16d877401fa1bbed9df50266f6.png', 'n8n-10.png', '80814c16d877401fa1bbed9df50266f6.png', 'png', 'image/png', 247590, NULL, NULL, NULL, '5e02327b80d26fd814c2ac66e4d9f7bd72c7be69ac839fdf30c77ebe695ab65f', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:54', '2026-06-11 10:55:54');
INSERT INTO `sys_file` VALUES (2064904433594150914, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/4af1832f7fb641008326deac057e6b91.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/4af1832f7fb641008326deac057e6b91.png', 'n8n-11.png', '4af1832f7fb641008326deac057e6b91.png', 'png', 'image/png', 172794, NULL, NULL, NULL, 'e53c1319513563c3d8577217192f6b558363baac85c07c7678c145a801161371', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:56', '2026-06-11 10:55:56');
INSERT INTO `sys_file` VALUES (2064904441194229762, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/6bc1f3a385654d15a3277cd628edb043.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/6bc1f3a385654d15a3277cd628edb043.png', 'n8n-12.png', '6bc1f3a385654d15a3277cd628edb043.png', 'png', 'image/png', 99348, NULL, NULL, NULL, 'c96425cfb3f95be82c955bc11526f3cca75a021122bba93b3629f8aca3534a58', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:58', '2026-06-11 10:55:58');
INSERT INTO `sys_file` VALUES (2064904447934476290, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/1d699036f60b4f38bd759bdb4d931ec9.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/1d699036f60b4f38bd759bdb4d931ec9.png', 'n8n-13.png', '1d699036f60b4f38bd759bdb4d931ec9.png', 'png', 'image/png', 106291, NULL, NULL, NULL, '3319d9ef6eb68195f571342b96e5ae6ecae6188df91b29c712e75a7671f7be73', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:59', '2026-06-11 10:55:59');
INSERT INTO `sys_file` VALUES (2064904455056404482, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/1c142c3dd93d4c6387480e89b0fdc806.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/1c142c3dd93d4c6387480e89b0fdc806.png', 'n8n-14.png', '1c142c3dd93d4c6387480e89b0fdc806.png', 'png', 'image/png', 165662, NULL, NULL, NULL, 'cce3157cde3c7ff19022b6d1681f11809b4b4bdab21d2ad5db5eb624df92eafc', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:01', '2026-06-11 10:56:01');
INSERT INTO `sys_file` VALUES (2064904476803870721, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/7ecf71e995dc4a3cbba0366dd63656bb.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/7ecf71e995dc4a3cbba0366dd63656bb.png', '01.png', '7ecf71e995dc4a3cbba0366dd63656bb.png', 'png', 'image/png', 226970, NULL, NULL, NULL, '8d4f1e7c8d4ffc7308105b84ba1d45d6df78088ffd8ceec3bbf6f85127126d57', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:06', '2026-06-11 10:56:06');
INSERT INTO `sys_file` VALUES (2064904483250515969, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/6972a14f730046d9aa411a8697f32ffa.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/6972a14f730046d9aa411a8697f32ffa.png', '02.png', '6972a14f730046d9aa411a8697f32ffa.png', 'png', 'image/png', 281903, NULL, NULL, NULL, '972a05dd901d02e0bc62131ed02bfdbbde51cf1db9b46735381d12ea2214cc7d', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:08', '2026-06-11 10:56:08');
INSERT INTO `sys_file` VALUES (2064904492654145538, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/b7d3b79936014cd48079284fe436043a.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/b7d3b79936014cd48079284fe436043a.png', '03.png', 'b7d3b79936014cd48079284fe436043a.png', 'png', 'image/png', 955244, NULL, NULL, NULL, '107d4419c97e046838ab6f1159bd70807f3a453b674570e0203dbf97cff2bf7e', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:10', '2026-06-11 10:56:10');
INSERT INTO `sys_file` VALUES (2064904500447162369, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/9ff2c7d4f539419ba496f907d4946493.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/9ff2c7d4f539419ba496f907d4946493.png', '04.png', '9ff2c7d4f539419ba496f907d4946493.png', 'png', 'image/png', 366360, NULL, NULL, NULL, '40741f2994dd26ce98aaac87cdf765dba9e56952de8aa30a97daa3aa75f2daf3', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:12', '2026-06-11 10:56:12');
INSERT INTO `sys_file` VALUES (2064904517849329666, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/d2878312442049b6b0f0362300aab706.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/d2878312442049b6b0f0362300aab706.png', 'table-01.png', 'd2878312442049b6b0f0362300aab706.png', 'png', 'image/png', 167336, NULL, NULL, NULL, 'a4e1fb8852b191e5eeb5bc5bde801a302fc63301f8d7023a04995a66099aea8a', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:16', '2026-06-11 10:56:16');
INSERT INTO `sys_file` VALUES (2064904525239693314, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/d9240f0dd95f4256a91f33e15a1a6e59.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/d9240f0dd95f4256a91f33e15a1a6e59.png', 'table-02.png', 'd9240f0dd95f4256a91f33e15a1a6e59.png', 'png', 'image/png', 234130, NULL, NULL, NULL, '21438bff460bba08789561571ff219da572cb4634b59247f7d7a900dc98336b1', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:18', '2026-06-11 10:56:18');
INSERT INTO `sys_file` VALUES (2064904534718820354, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/8a9c133d83474f10a5d34aa4810b6af6.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/8a9c133d83474f10a5d34aa4810b6af6.png', '01.png', '8a9c133d83474f10a5d34aa4810b6af6.png', 'png', 'image/png', 1126211, NULL, NULL, NULL, '13f290c6f2dd802a86599e778eeb87a5688faf8def7e084e88beba55d7de14aa', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:20', '2026-06-11 10:56:20');
INSERT INTO `sys_file` VALUES (2064904549415661570, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/2886fd90656f4a46b19d10585d9147c1.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/2886fd90656f4a46b19d10585d9147c1.png', '8-1.png', '2886fd90656f4a46b19d10585d9147c1.png', 'png', 'image/png', 140133, NULL, NULL, NULL, 'd5fde21ad58653e1af819184d14e05b8fb44342f25acea0d3f9fb11fcd18b3f2', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:23', '2026-06-11 10:56:23');
INSERT INTO `sys_file` VALUES (2064904557284175874, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/0acb3d08000243d48943924cd80fab7e.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/0acb3d08000243d48943924cd80fab7e.png', '8-2.png', '0acb3d08000243d48943924cd80fab7e.png', 'png', 'image/png', 361499, NULL, NULL, NULL, '1b9eb648b4ee0d5aac31f813264b9b25554691e0ee722d3c93b99b60c326317c', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:25', '2026-06-11 10:56:25');
INSERT INTO `sys_file` VALUES (2064904563705655297, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/de0c835c7d894c29b87fc42a1be3da65.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/de0c835c7d894c29b87fc42a1be3da65.png', '8-3.png', 'de0c835c7d894c29b87fc42a1be3da65.png', 'png', 'image/png', 83756, NULL, NULL, NULL, '909a00e6fb4cd0411330fe0c9d018072846381b52b46a379a6ac47de409462d3', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:27', '2026-06-11 10:56:27');
INSERT INTO `sys_file` VALUES (2064904578096312322, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/52e26a1cfbb447959c8035130c9cebf8.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/52e26a1cfbb447959c8035130c9cebf8.png', '8-4.png', '52e26a1cfbb447959c8035130c9cebf8.png', 'png', 'image/png', 379260, NULL, NULL, NULL, '69d63d6b346c6e4a18b67df8c73d8c49c70e7d8cb3c0786cabd5dd4241223b3c', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:30', '2026-06-11 10:56:30');
INSERT INTO `sys_file` VALUES (2064904587420250114, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/7f9e6ec3694b4e188d692d6aba3b0942.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/7f9e6ec3694b4e188d692d6aba3b0942.png', '8-5.png', '7f9e6ec3694b4e188d692d6aba3b0942.png', 'png', 'image/png', 393216, NULL, NULL, NULL, 'c38e8321e8955de1e7b275f5595a7010c57ac0bcab8196363d2e7a33b5fca470', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:32', '2026-06-11 10:56:32');
INSERT INTO `sys_file` VALUES (2064904598010867714, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/f74e472644994fdc9f92cf45f207cafd.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/f74e472644994fdc9f92cf45f207cafd.png', '8-6.png', 'f74e472644994fdc9f92cf45f207cafd.png', 'png', 'image/png', 63391, NULL, NULL, NULL, '61155f4d14553665b468c081943ae9470a6e52f038e273c8c5abf33135cb09d8', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:35', '2026-06-11 10:56:35');
INSERT INTO `sys_file` VALUES (2064904604419764226, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/d52b20d9c2cb4bf08e231b75e8173dfe.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/d52b20d9c2cb4bf08e231b75e8173dfe.png', '8-7.png', 'd52b20d9c2cb4bf08e231b75e8173dfe.png', 'png', 'image/png', 80390, NULL, NULL, NULL, '129d205a2ab5c360c1c6707660b0137890bcec64ca081345d0959799ad499e60', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:36', '2026-06-11 10:56:36');
INSERT INTO `sys_file` VALUES (2064904611763990530, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/d65d2ee72da64ffd90d4c0230c0fed6a.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/d65d2ee72da64ffd90d4c0230c0fed6a.png', '8-8.png', 'd65d2ee72da64ffd90d4c0230c0fed6a.png', 'png', 'image/png', 216470, NULL, NULL, NULL, 'beeab6c99dff34a160f010ef74a61a03c9ccfb30284f8a38501ace250e73d0a7', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:38', '2026-06-11 10:56:38');
INSERT INTO `sys_file` VALUES (2064904618831392770, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/bf2d61dc418948fd805cf1cce750f7dc.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/bf2d61dc418948fd805cf1cce750f7dc.png', '8-9.png', 'bf2d61dc418948fd805cf1cce750f7dc.png', 'png', 'image/png', 74728, NULL, NULL, NULL, 'ff5850387bcd5902f62575c089d7c4810be348f571afd940363454351504bc68', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:40', '2026-06-11 10:56:40');
INSERT INTO `sys_file` VALUES (2064904625366118402, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/18ccb43aca1148689f5e557494bb88b5.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/18ccb43aca1148689f5e557494bb88b5.png', '8-10.png', '18ccb43aca1148689f5e557494bb88b5.png', 'png', 'image/png', 86037, NULL, NULL, NULL, 'b309d55d9651f6be0f17df6def019aa8a5cdaa1089195919f563133eb13894ee', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:41', '2026-06-11 10:56:41');
INSERT INTO `sys_file` VALUES (2064904634245459970, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/11136fcaa21f4b19b57e8fc94170ce15.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/11136fcaa21f4b19b57e8fc94170ce15.png', '8-11.png', '11136fcaa21f4b19b57e8fc94170ce15.png', 'png', 'image/png', 808923, NULL, NULL, NULL, 'ee94426313f79a0ecbfe965c8ea2a6ad16b96b23c3ac95e308ae85eb928d4550', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:44', '2026-06-11 10:56:44');
INSERT INTO `sys_file` VALUES (2064904650783600642, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/ee719377fe354487a9a923026f9e1a3d.webp', 'http://oss.orccode.com/public/blog/images/2026/06/11/ee719377fe354487a9a923026f9e1a3d.webp', '9-1.webp', 'ee719377fe354487a9a923026f9e1a3d.webp', 'webp', 'image/webp', 68676, NULL, NULL, NULL, '2c80407789814d462d3e75d2f530b82a8461ef52c7d5fa7bd3207e57f957f6fe', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:47', '2026-06-11 10:56:47');
INSERT INTO `sys_file` VALUES (2064904657481904130, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/6d9ccb7b597c48fb9fcba622eed14af6.webp', 'http://oss.orccode.com/public/blog/images/2026/06/11/6d9ccb7b597c48fb9fcba622eed14af6.webp', '9-2.webp', '6d9ccb7b597c48fb9fcba622eed14af6.webp', 'webp', 'image/webp', 136390, NULL, NULL, NULL, '9afca72306eb25f348bcd2c924f19e4f679fa877242e2c4f6eb8fa1136a0d2a7', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:49', '2026-06-11 10:56:49');
INSERT INTO `sys_file` VALUES (2064904665220395009, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/f009299ce4644b97bffa5e99abc438cc.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/f009299ce4644b97bffa5e99abc438cc.png', '9-3.png', 'f009299ce4644b97bffa5e99abc438cc.png', 'png', 'image/png', 325206, NULL, NULL, NULL, 'a6cd8da1a7e49e1243fb8b79ec7b79947584b342b6e8aed5df2efb8ecf05458a', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:51', '2026-06-11 10:56:51');
INSERT INTO `sys_file` VALUES (2064904681930502146, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/2165403995294a94b17b8487fc683a0e.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/2165403995294a94b17b8487fc683a0e.png', '10-1.png', '2165403995294a94b17b8487fc683a0e.png', 'png', 'image/png', 224681, NULL, NULL, NULL, '659f3fa6941d9303d0a72c559b76c41b60a57a17ceea4445c31d1d36536069f6', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:55', '2026-06-11 10:56:55');
INSERT INTO `sys_file` VALUES (2064904690130366465, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/c063db26442d4b4e866b94a4b7f058e4.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/c063db26442d4b4e866b94a4b7f058e4.png', '10-2.png', 'c063db26442d4b4e866b94a4b7f058e4.png', 'png', 'image/png', 293945, NULL, NULL, NULL, 'fc2b1e3393cc211a0ba23326b0e6ad1e29412812ab1932a7d3d06b704cb1012e', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:57', '2026-06-11 10:56:57');
INSERT INTO `sys_file` VALUES (2064904698141487105, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/a07c03328a8043f78939644c701f0331.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/a07c03328a8043f78939644c701f0331.png', '10-3.png', 'a07c03328a8043f78939644c701f0331.png', 'png', 'image/png', 278386, NULL, NULL, NULL, '6ba525f633c8be9b25b158722844fd257fe4d30d4c4c1ee20a8297c52105179d', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:59', '2026-06-11 10:56:59');
INSERT INTO `sys_file` VALUES (2064904705531850753, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/a8b0cc643f0f47ff8c88dd283ccd74d2.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/a8b0cc643f0f47ff8c88dd283ccd74d2.png', '10-table-1.png', 'a8b0cc643f0f47ff8c88dd283ccd74d2.png', 'png', 'image/png', 239680, NULL, NULL, NULL, '52b03b6c1fc87d0b0c5165717c2f216b9aa560b69cd428877bdd15e6a7874018', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:01', '2026-06-11 10:57:01');
INSERT INTO `sys_file` VALUES (2064904713182261249, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/38b4714c06f04fdc8239074a87efd81e.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/38b4714c06f04fdc8239074a87efd81e.png', '10-4.png', '38b4714c06f04fdc8239074a87efd81e.png', 'png', 'image/png', 312996, NULL, NULL, NULL, 'f02046f5709c83a845f531b64514011d9ea2649bfb25be0ea08ea11128894d93', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:02', '2026-06-11 10:57:02');
INSERT INTO `sys_file` VALUES (2064904721596035073, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/0e9f2cd051e94da383d79be0a947569d.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/0e9f2cd051e94da383d79be0a947569d.png', '10-5.png', '0e9f2cd051e94da383d79be0a947569d.png', 'png', 'image/png', 269969, NULL, NULL, NULL, '5e51e7c2d9b0e5b6ee0bfc3c00c1a265bf8d7706809ac79aa4718bf95fa24ab1', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:04', '2026-06-11 10:57:04');
INSERT INTO `sys_file` VALUES (2064904728235618306, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/17877542c9fd4e2aa59a54185ce525e5.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/17877542c9fd4e2aa59a54185ce525e5.png', '10-table-2.png', '17877542c9fd4e2aa59a54185ce525e5.png', 'png', 'image/png', 131323, NULL, NULL, NULL, '1bd13e85fd22d50f050d189357cebdb8454e8aa9ba240d5da932019b1648e911', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:06', '2026-06-11 10:57:06');
INSERT INTO `sys_file` VALUES (2064904736452259841, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/6e7149480fd845d488d90e76a9c61375.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/6e7149480fd845d488d90e76a9c61375.png', '10-6.png', '6e7149480fd845d488d90e76a9c61375.png', 'png', 'image/png', 422368, NULL, NULL, NULL, '6b9aff6a4a53974d395ea888284566f63664a24feaf5960c038a28c79c6c5653', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:08', '2026-06-11 10:57:08');
INSERT INTO `sys_file` VALUES (2064904743431581697, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/bc7cb28e105240baafebc5e779c996e7.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/bc7cb28e105240baafebc5e779c996e7.png', '10-table-3.png', 'bc7cb28e105240baafebc5e779c996e7.png', 'png', 'image/png', 196087, NULL, NULL, NULL, '67aaf3d529858b6d32e287a7f98611af097339a705faf56187656a44721d520d', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:10', '2026-06-11 10:57:10');
INSERT INTO `sys_file` VALUES (2064904750951968770, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/402452cdb5e248a29e7ffe320557192f.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/402452cdb5e248a29e7ffe320557192f.png', '10-table-4.png', '402452cdb5e248a29e7ffe320557192f.png', 'png', 'image/png', 207528, NULL, NULL, NULL, '00c677ee4faa04a424a92ff6f0a04205bcd76f2f75acb61e34f906642cdc7087', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:11', '2026-06-11 10:57:11');
INSERT INTO `sys_file` VALUES (2064904758619156481, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/9f55c578c9524011ab1d681110da8ff2.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/9f55c578c9524011ab1d681110da8ff2.png', '10-table-5.png', '9f55c578c9524011ab1d681110da8ff2.png', 'png', 'image/png', 253553, NULL, NULL, NULL, '821e393436c626592b1614d09b65ca195653c828907bf10e94bf67ac8f965cd5', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:13', '2026-06-11 10:57:13');
INSERT INTO `sys_file` VALUES (2064904773630570497, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/4b3ae378ec674fd29f5124bc2850701d.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/4b3ae378ec674fd29f5124bc2850701d.png', '10-table-6.png', '4b3ae378ec674fd29f5124bc2850701d.png', 'png', 'image/png', 367610, NULL, NULL, NULL, '3789070977809d4bbc9170e8559f0994e668831d1df7c5ae1d9501f0c6fb9b5d', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:17', '2026-06-11 10:57:17');
INSERT INTO `sys_file` VALUES (2064904781092237314, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/37620f78ca9040ecbee4bce2b5acd5b0.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/37620f78ca9040ecbee4bce2b5acd5b0.png', '10-table-7.png', '37620f78ca9040ecbee4bce2b5acd5b0.png', 'png', 'image/png', 203744, NULL, NULL, NULL, '6cb86b64f503603cab5424aea220003900e4b7e6ca862de7074da1e75342552c', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:19', '2026-06-11 10:57:19');
INSERT INTO `sys_file` VALUES (2064904788214165505, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/5e1b7f7e1f7c4c899f9f3febab9d5dd2.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/5e1b7f7e1f7c4c899f9f3febab9d5dd2.png', '10-7.png', '5e1b7f7e1f7c4c899f9f3febab9d5dd2.png', 'png', 'image/png', 131749, NULL, NULL, NULL, 'fadc8b034b9f5f100589c11d12ef791a430ac49b385953236d8f07cd1b380646', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:20', '2026-06-11 10:57:20');
INSERT INTO `sys_file` VALUES (2064904796242063361, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/13e3e7a8f284421193d6ef7ccbbf985f.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/13e3e7a8f284421193d6ef7ccbbf985f.png', '10-8.png', '13e3e7a8f284421193d6ef7ccbbf985f.png', 'png', 'image/png', 361775, NULL, NULL, NULL, 'a3fd0bb7bd060f5c8214e1f083e134d99de8b247a236901f715e6d9270d24744', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:22', '2026-06-11 10:57:22');
INSERT INTO `sys_file` VALUES (2064904803712118786, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/02397c8e36fa4fafa93680789ea37be9.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/02397c8e36fa4fafa93680789ea37be9.png', '10-table-8.png', '02397c8e36fa4fafa93680789ea37be9.png', 'png', 'image/png', 175253, NULL, NULL, NULL, '0cbe7612b794716c71974ec2cb50ce9698152ffb1511cc4f7d259690e32a6ff2', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:24', '2026-06-11 10:57:24');
INSERT INTO `sys_file` VALUES (2064904812381745153, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/fbe3fa17691e4fe39b32fbb14a0f7640.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/fbe3fa17691e4fe39b32fbb14a0f7640.png', '10-9.png', 'fbe3fa17691e4fe39b32fbb14a0f7640.png', 'png', 'image/png', 393253, NULL, NULL, NULL, '970d363039cfcf252f2976b88c7b30fa6d77fb60c7cabcc04836d3fd85636509', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:26', '2026-06-11 10:57:26');
INSERT INTO `sys_file` VALUES (2064904821030400002, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/4cd61c5f489049a29f8e20b37373ae7d.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/4cd61c5f489049a29f8e20b37373ae7d.png', '10-10.png', '4cd61c5f489049a29f8e20b37373ae7d.png', 'png', 'image/png', 79783, NULL, NULL, NULL, '72c279fc51fe94fc1c4d4a83b3c97171c15ad96364a14c19b1c616f3400360d3', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:28', '2026-06-11 10:57:28');
INSERT INTO `sys_file` VALUES (2064904828278157313, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/0ed1d504064f4b9ba07248381707da55.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/0ed1d504064f4b9ba07248381707da55.png', '10-11.png', '0ed1d504064f4b9ba07248381707da55.png', 'png', 'image/png', 147223, NULL, NULL, NULL, 'd4fa0763165ce2029b14b46ddd1ffdc2d552959024b48768d17f6b175e37bece', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:30', '2026-06-11 10:57:30');

-- ----------------------------
-- Table structure for sys_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu`  (
  `id` bigint(20) NOT NULL COMMENT '菜单ID',
  `parent_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '父菜单ID，0表示根节点',
  `menu_type` tinyint(4) NOT NULL COMMENT '类型：1目录 2菜单 3按钮 4内嵌 5外链',
  `menu_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '名称',
  `route_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '路由名（vue-router name），跨菜单唯一',
  `path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '前端路由路径（如 /user）',
  `component` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '前端组件路径（如 system/user/index）',
  `perms` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '权限标识（如 user:list, user:delete）',
  `icon` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '图标',
  `active_icon` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '激活态图标',
  `active_path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '高亮指定路径',
  `link_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '外链/内嵌地址（type=link 用 link，type=embedded 用 iframeSrc）',
  `keep_alive` tinyint(4) NOT NULL DEFAULT 0 COMMENT '是否缓存：1是 0否',
  `affix_tab` tinyint(4) NOT NULL DEFAULT 0 COMMENT '是否固定 tab：1是 0否',
  `hide_in_menu` tinyint(4) NOT NULL DEFAULT 0 COMMENT '是否在菜单隐藏：1是 0否',
  `hide_children_in_menu` tinyint(4) NOT NULL DEFAULT 0 COMMENT '是否隐藏子菜单：1是 0否',
  `hide_in_breadcrumb` tinyint(4) NOT NULL DEFAULT 0 COMMENT '是否在面包屑隐藏：1是 0否',
  `hide_in_tab` tinyint(4) NOT NULL DEFAULT 0 COMMENT '是否在多页签隐藏：1是 0否',
  `badge_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '徽章类型：dot/normal',
  `badge` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '徽章文本',
  `badge_variants` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '徽章样式 default/destructive/primary/success/warning',
  `sort` int(11) NOT NULL DEFAULT 0 COMMENT '排序（越小越靠前）',
  `visible` tinyint(4) NOT NULL DEFAULT 1 COMMENT '是否显示：1是 0否',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：1正常 0禁用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_route_name`(`route_name` ASC) USING BTREE,
  INDEX `idx_parent_id`(`parent_id` ASC) USING BTREE,
  INDEX `idx_sort`(`sort` ASC) USING BTREE,
  INDEX `idx_path`(`path` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '菜单与权限表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_menu
-- ----------------------------
INSERT INTO `sys_menu` VALUES (1, 0, 1, '仪表盘', NULL, '/dashboard', 'BasicLayout', NULL, 'lucide:layout-dashboard', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, -1, 1, 1, NULL, '2026-05-07 09:04:28', '2026-05-07 09:04:28');
INSERT INTO `sys_menu` VALUES (2, 1, 2, '分析页', NULL, '/analytics', 'dashboard/analytics/index', NULL, 'lucide:area-chart', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-07 09:04:28', '2026-05-07 09:04:28');
INSERT INTO `sys_menu` VALUES (10, 0, 1, '系统管理', NULL, '/system', 'BasicLayout', NULL, 'lucide:settings', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 100, 1, 1, NULL, '2026-05-07 09:04:28', '2026-05-07 09:04:28');
INSERT INTO `sys_menu` VALUES (11, 10, 2, '用户管理', 'SystemUser', '/system/user', 'system/user/index', 'system:user:list', 'lucide:users', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-07 09:04:28', '2026-05-07 09:04:28');
INSERT INTO `sys_menu` VALUES (12, 10, 2, '菜单管理', 'SystemMenu', '/system/menu', 'system/menu/index', 'system:menu:list', 'lucide:menu', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, '菜单管理', '2026-05-07 11:47:51', '2026-05-13 06:49:49');
INSERT INTO `sys_menu` VALUES (13, 10, 2, '角色管理', 'SystemRole', '/system/role', 'system/role/index', 'system:role:list', 'lucide:user-cog', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, '角色管理', '2026-05-07 11:47:51', '2026-05-13 06:49:49');
INSERT INTO `sys_menu` VALUES (14, 10, 2, '文件管理', 'SystemFile', '/system/file', 'system/file/index', 'system:file:list', 'lucide:files', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, '文件管理', '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (20, 0, 1, '博客管理', 'Blog', '/blog', 'BasicLayout', NULL, 'lucide:notebook-pen', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 200, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (21, 20, 2, '分类管理', 'BlogCategory', '/blog/category', 'blog/category/index', 'blog:category:list', 'lucide:folder-tree', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (22, 20, 2, '标签管理', 'BlogTag', '/blog/tag', 'blog/tag/index', 'blog:tag:list', 'lucide:tag', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (23, 20, 2, '文章管理', 'BlogArticle', '/blog/article', 'blog/article/index', 'blog:article:list', 'lucide:file-text', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (24, 20, 2, '随笔管理', 'BlogEssay', '/blog/essay', 'blog/article/index', 'blog:article:list', 'lucide:pen-line', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-26 00:00:00', '2026-05-26 00:00:00');
INSERT INTO `sys_menu` VALUES (25, 20, 2, '系列管理', 'BlogSeries', '/blog/series', 'blog/series/index', 'blog:series:list', 'lucide:layers', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (26, 20, 2, '系列目录', 'BlogSeriesCatalog', '/blog/series/:id/catalog', 'blog/series/catalog', 'blog:series:query', 'lucide:list-tree', NULL, '/blog/series', NULL, 0, 0, 1, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (27, 20, 2, '目的地管理', 'BlogTravelDestination', '/blog/travel/destination', 'blog/travel/destination/index', 'blog:travel:list', 'lucide:map-pin', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 5, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (28, 20, 2, '游记管理', 'BlogTravelTrip', '/blog/travel/trip', 'blog/travel/trip/index', 'blog:travel:list', 'lucide:map', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 6, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (29, 20, 2, '游记详情', 'BlogTravelTripDetail', '/blog/travel/trip/:id/detail', 'blog/travel/trip/detail', 'blog:travel:query', 'lucide:list-tree', NULL, '/blog/travel/trip', NULL, 0, 0, 1, 0, 0, 0, NULL, NULL, NULL, 7, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (30, 0, 1, 'AI中转管理', NULL, '/ai-relay', 'BasicLayout', NULL, 'lucide:plug-zap', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 300, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (31, 30, 2, '服务商管理', 'AiRelayProvider', '/ai-relay/provider', 'ai-relay/provider/index', 'blog:ai-relay:provider:list', 'lucide:server', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (32, 30, 2, '套餐管理', 'AiRelayPackage', '/ai-relay/package', 'ai-relay/package/index', 'blog:ai-relay:package:list', 'lucide:package', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (33, 30, 2, '套餐类型', 'AiRelayPackageType', '/ai-relay/package-type', 'ai-relay/package-type/index', 'blog:ai-relay:package-type:list', 'lucide:list-checks', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (34, 30, 2, 'AI模型', 'AiRelayModel', '/ai-relay/model', 'ai-relay/model/index', 'blog:ai-relay:model:list', 'lucide:bot', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (35, 30, 2, '支付方式', 'AiRelayPaymentMethod', '/ai-relay/payment-method', 'ai-relay/payment-method/index', 'blog:ai-relay:payment-method:list', 'lucide:credit-card', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 5, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (36, 30, 2, '推荐与测评', 'AiRelayRecommend', '/ai-relay/recommend', 'ai-relay/recommend/index', 'blog:ai-relay:recommend:list', 'lucide:star', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 6, 1, 1, NULL, '2026-05-29 00:00:00', '2026-05-29 00:00:00');
INSERT INTO `sys_menu` VALUES (37, 30, 2, '充值记录', 'AiRelayRecharge', '/ai-relay/recharge', 'ai-relay/recharge/index', 'blog:ai-relay:recharge:list', 'lucide:wallet', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 7, 1, 1, NULL, '2026-05-29 00:00:00', '2026-05-29 00:00:00');
INSERT INTO `sys_menu` VALUES (40, 0, 1, '个人空间', 'Space', '/space', 'BasicLayout', NULL, 'lucide:bookmark', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 250, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (41, 40, 2, '书签管理', 'SpaceBookmark', '/space/bookmark', 'space/bookmark/index', 'space:bookmark:list', 'lucide:bookmark-plus', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (42, 40, 2, '目录管理', 'SpaceFolder', '/space/folder', 'space/folder/index', 'space:folder:list', 'lucide:folder-tree', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (43, 40, 2, '标签管理', 'SpaceTag', '/space/tag', 'space/tag/index', 'space:tag:list', 'lucide:tags', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (44, 40, 2, '导入任务', 'SpaceImportTask', '/space/import-task', 'space/import-task/index', 'space:bookmark-import:list', 'lucide:download', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (45, 40, 2, '导出任务', 'SpaceExportTask', '/space/export-task', 'space/export-task/index', 'space:bookmark-export:list', 'lucide:upload', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 5, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (50, 0, 1, '插件商城', 'Forge', '/forge', 'BasicLayout', NULL, 'lucide:blocks', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 400, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (51, 50, 2, '插件分类', 'ForgeCategory', '/forge/category', 'forge/category/index', 'forge:category:list', 'lucide:folder-tree', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (52, 50, 2, '插件管理', 'ForgePlugin', '/forge/plugin', 'forge/plugin/index', 'forge:plugin:list', 'lucide:package', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (53, 50, 2, '插件版本', 'ForgePluginVersion', '/forge/plugin/:id/versions', 'forge/plugin/versions', 'forge:version:list', 'lucide:git-branch', NULL, '/forge/plugin', NULL, 0, 0, 1, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, '插件版本与权限声明管理（详情页）', '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (54, 50, 2, '评价管理', 'ForgeReview', '/forge/review', 'forge/review/index', 'forge:review:list', 'lucide:star', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (55, 50, 2, '下载日志', 'ForgeDownloadLog', '/forge/download-log', 'forge/download-log/index', 'forge:download-log:list', 'lucide:download', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 5, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (80, 0, 1, '智能体', 'AiAgent', '/ai-agent', 'BasicLayout', NULL, 'lucide:bot', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 315, 1, 1, NULL, '2026-07-06 00:00:00', '2026-07-06 00:00:00');
INSERT INTO `sys_menu` VALUES (81, 80, 2, '智能体定义', 'AiAgentDefinition', '/ai-agent/agents', 'ai-agent/agents', 'manager:ai-agent:list', 'lucide:bot', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-07-06 00:00:00', '2026-07-06 00:00:00');
INSERT INTO `sys_menu` VALUES (82, 80, 2, '智能体实例', 'AiAgentInstance', '/ai-agent/instances', 'ai-agent/index', 'manager:ai-agent:list', 'lucide:activity', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-07-06 00:00:00', '2026-07-06 00:00:00');
INSERT INTO `sys_menu` VALUES (83, 80, 2, '实例回放', 'AiAgentReplay', '/ai-agent/replay', 'ai-agent/replay/index', 'manager:ai-agent:query', 'lucide:play-circle', NULL, '/ai-agent/instances', NULL, 0, 0, 1, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-07-06 00:00:00', '2026-07-06 00:00:00');
INSERT INTO `sys_menu` VALUES (84, 80, 2, '流程列表', 'AiFlowList', '/ai-flow/list', 'ai-flow/index', 'manager:ai-flow:list', 'lucide:list-tree', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-06-27 00:00:00', '2026-07-07 10:05:46');
INSERT INTO `sys_menu` VALUES (85, 80, 2, '流程编辑器', 'AiFlowEditor', '/ai-flow/editor', 'ai-flow/editor/index', 'manager:ai-flow:query', 'lucide:pencil-ruler', NULL, '/ai-flow/list', NULL, 0, 0, 1, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-06-27 00:00:00', '2026-07-07 10:05:46');
INSERT INTO `sys_menu` VALUES (86, 80, 2, '新建流程', 'AiFlowCreate', '/ai-flow/create', 'ai-flow/create/index', 'manager:ai-flow:save', 'lucide:file-plus', NULL, '/ai-flow/list', NULL, 0, 0, 1, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-06-27 00:00:00', '2026-07-07 10:05:46');
INSERT INTO `sys_menu` VALUES (87, 80, 2, '模型档案', 'AiModelProfile', '/ai-model/profiles', 'ai-model-profile/index', 'manager:ai-model-profile:list', 'lucide:server-cog', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-06-29 00:00:00', '2026-07-07 10:07:29');
INSERT INTO `sys_menu` VALUES (88, 80, 2, 'MCP服务器', 'AiMcpServer', '/ai-model/mcp-servers', 'ai-mcp-server/index', 'manager:ai-mcp-server:list', 'lucide:plug', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-06-29 00:00:00', '2026-07-07 10:07:32');
INSERT INTO `sys_menu` VALUES (89, 80, 2, 'AI工具', 'AiTool', '/ai-model/tools', 'ai-tool/index', 'manager:ai-tool:list', 'lucide:wrench', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-06-29 00:00:00', '2026-07-07 10:07:35');
INSERT INTO `sys_menu` VALUES (90, 80, 2, '迭代链(系列)', 'AiIterationChain', '/ai-agent/iterations', 'ai-agent/iterations', 'manager:ai-iteration:list', 'lucide:repeat', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 10, 1, 1, NULL, '2026-07-08 00:00:00', '2026-07-08 00:00:00');
INSERT INTO `sys_menu` VALUES (91, 80, 2, '提示词', 'AiPrompt', '/ai-model/prompts', 'ai-prompt/index', 'manager:ai-prompt:list', 'lucide:message-square-text', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-07-10 00:00:00', '2026-07-10 00:00:00');
INSERT INTO `sys_menu` VALUES (230, 20, 2, '文章导入任务', 'BlogImportTask', '/blog/import-task', 'blog/import-task/index', 'blog:article:list', 'lucide:download', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 8, 1, 1, '文章 Markdown 批量导入任务进度与明细', '2026-06-11 00:00:00', '2026-06-11 00:00:00');
INSERT INTO `sys_menu` VALUES (1101, 11, 3, '新增用户', NULL, NULL, NULL, 'system:user:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-09 15:26:33', '2026-05-09 15:26:33');
INSERT INTO `sys_menu` VALUES (1102, 11, 3, '修改用户', NULL, NULL, NULL, 'system:user:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-09 15:26:33', '2026-05-09 15:26:33');
INSERT INTO `sys_menu` VALUES (1103, 11, 3, '删除用户', NULL, NULL, NULL, 'system:user:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-09 15:26:33', '2026-05-09 15:26:33');
INSERT INTO `sys_menu` VALUES (1201, 12, 3, '新增菜单', NULL, NULL, NULL, 'system:menu:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-09 15:26:33', '2026-05-09 15:26:33');
INSERT INTO `sys_menu` VALUES (1202, 12, 3, '修改菜单', NULL, NULL, NULL, 'system:menu:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-09 15:26:33', '2026-05-09 15:26:33');
INSERT INTO `sys_menu` VALUES (1203, 12, 3, '删除菜单', NULL, NULL, NULL, 'system:menu:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-09 15:26:33', '2026-05-09 15:26:33');
INSERT INTO `sys_menu` VALUES (1301, 13, 3, '新增角色', NULL, NULL, NULL, 'system:role:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-09 15:26:33', '2026-05-09 15:26:33');
INSERT INTO `sys_menu` VALUES (1302, 13, 3, '修改角色', NULL, NULL, NULL, 'system:role:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-09 15:26:33', '2026-05-09 15:26:33');
INSERT INTO `sys_menu` VALUES (1303, 13, 3, '删除角色', NULL, NULL, NULL, 'system:role:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-09 15:26:33', '2026-05-09 15:26:33');
INSERT INTO `sys_menu` VALUES (1401, 14, 3, '上传文件', NULL, NULL, NULL, 'system:file:upload', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (1402, 14, 3, '查询文件', NULL, NULL, NULL, 'system:file:list', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (1403, 14, 3, '编辑文件', NULL, NULL, NULL, 'system:file:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (1404, 14, 3, '删除文件', NULL, NULL, NULL, 'system:file:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (2101, 21, 3, '新增分类', NULL, NULL, NULL, 'blog:category:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (2102, 21, 3, '修改分类', NULL, NULL, NULL, 'blog:category:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (2103, 21, 3, '删除分类', NULL, NULL, NULL, 'blog:category:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (2201, 22, 3, '新增标签', NULL, NULL, NULL, 'blog:tag:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (2202, 22, 3, '修改标签', NULL, NULL, NULL, 'blog:tag:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (2203, 22, 3, '删除标签', NULL, NULL, NULL, 'blog:tag:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (2301, 23, 3, '新增文章', NULL, NULL, NULL, 'blog:article:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (2302, 23, 3, '修改文章', NULL, NULL, NULL, 'blog:article:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (2303, 23, 3, '删除文章', NULL, NULL, NULL, 'blog:article:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (2304, 23, 3, '查询文章', NULL, NULL, NULL, 'blog:article:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (2501, 25, 3, '新增系列', NULL, NULL, NULL, 'blog:series:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (2502, 25, 3, '修改系列', NULL, NULL, NULL, 'blog:series:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (2503, 25, 3, '删除系列', NULL, NULL, NULL, 'blog:series:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (2504, 25, 3, '查询系列', NULL, NULL, NULL, 'blog:series:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (2701, 27, 3, '新增目的地', NULL, NULL, NULL, 'blog:travel:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (2702, 27, 3, '修改目的地', NULL, NULL, NULL, 'blog:travel:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (2703, 27, 3, '删除目的地', NULL, NULL, NULL, 'blog:travel:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (2704, 27, 3, '查询目的地', NULL, NULL, NULL, 'blog:travel:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (2801, 28, 3, '新增游记', NULL, NULL, NULL, 'blog:travel:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (2802, 28, 3, '修改游记', NULL, NULL, NULL, 'blog:travel:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (2803, 28, 3, '删除游记', NULL, NULL, NULL, 'blog:travel:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (2804, 28, 3, '查询游记', NULL, NULL, NULL, 'blog:travel:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (3101, 31, 3, '查询服务商', NULL, NULL, NULL, 'blog:ai-relay:provider:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3102, 31, 3, '新增服务商', NULL, NULL, NULL, 'blog:ai-relay:provider:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3103, 31, 3, '修改服务商', NULL, NULL, NULL, 'blog:ai-relay:provider:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3104, 31, 3, '删除服务商', NULL, NULL, NULL, 'blog:ai-relay:provider:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3201, 32, 3, '查询套餐', NULL, NULL, NULL, 'blog:ai-relay:package:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3202, 32, 3, '新增套餐', NULL, NULL, NULL, 'blog:ai-relay:package:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3203, 32, 3, '修改套餐', NULL, NULL, NULL, 'blog:ai-relay:package:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3204, 32, 3, '删除套餐', NULL, NULL, NULL, 'blog:ai-relay:package:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3301, 33, 3, '查询套餐类型', NULL, NULL, NULL, 'blog:ai-relay:package-type:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3302, 33, 3, '新增套餐类型', NULL, NULL, NULL, 'blog:ai-relay:package-type:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3303, 33, 3, '修改套餐类型', NULL, NULL, NULL, 'blog:ai-relay:package-type:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3304, 33, 3, '删除套餐类型', NULL, NULL, NULL, 'blog:ai-relay:package-type:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3401, 34, 3, '查询模型', NULL, NULL, NULL, 'blog:ai-relay:model:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3402, 34, 3, '新增模型', NULL, NULL, NULL, 'blog:ai-relay:model:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3403, 34, 3, '修改模型', NULL, NULL, NULL, 'blog:ai-relay:model:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3404, 34, 3, '删除模型', NULL, NULL, NULL, 'blog:ai-relay:model:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3501, 35, 3, '查询支付方式', NULL, NULL, NULL, 'blog:ai-relay:payment-method:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3502, 35, 3, '新增支付方式', NULL, NULL, NULL, 'blog:ai-relay:payment-method:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3503, 35, 3, '修改支付方式', NULL, NULL, NULL, 'blog:ai-relay:payment-method:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3504, 35, 3, '删除支付方式', NULL, NULL, NULL, 'blog:ai-relay:payment-method:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3601, 36, 3, '查询推荐', NULL, NULL, NULL, 'blog:ai-relay:recommend:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-29 00:00:00', '2026-05-29 00:00:00');
INSERT INTO `sys_menu` VALUES (3602, 36, 3, '新增推荐', NULL, NULL, NULL, 'blog:ai-relay:recommend:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-29 00:00:00', '2026-05-29 00:00:00');
INSERT INTO `sys_menu` VALUES (3603, 36, 3, '修改推荐', NULL, NULL, NULL, 'blog:ai-relay:recommend:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-29 00:00:00', '2026-05-29 00:00:00');
INSERT INTO `sys_menu` VALUES (3604, 36, 3, '删除推荐', NULL, NULL, NULL, 'blog:ai-relay:recommend:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-29 00:00:00', '2026-05-29 00:00:00');
INSERT INTO `sys_menu` VALUES (3701, 37, 3, '查询充值记录', NULL, NULL, NULL, 'blog:ai-relay:recharge:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-29 00:00:00', '2026-05-29 00:00:00');
INSERT INTO `sys_menu` VALUES (3702, 37, 3, '新增充值记录', NULL, NULL, NULL, 'blog:ai-relay:recharge:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-29 00:00:00', '2026-05-29 00:00:00');
INSERT INTO `sys_menu` VALUES (3703, 37, 3, '修改充值记录', NULL, NULL, NULL, 'blog:ai-relay:recharge:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-29 00:00:00', '2026-05-29 00:00:00');
INSERT INTO `sys_menu` VALUES (3704, 37, 3, '删除充值记录', NULL, NULL, NULL, 'blog:ai-relay:recharge:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-29 00:00:00', '2026-05-29 00:00:00');
INSERT INTO `sys_menu` VALUES (4101, 41, 3, '查询书签', NULL, NULL, NULL, 'space:bookmark:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4102, 41, 3, '新增书签', NULL, NULL, NULL, 'space:bookmark:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4103, 41, 3, '修改书签', NULL, NULL, NULL, 'space:bookmark:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4104, 41, 3, '删除书签', NULL, NULL, NULL, 'space:bookmark:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4201, 42, 3, '查询目录', NULL, NULL, NULL, 'space:folder:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4202, 42, 3, '新增目录', NULL, NULL, NULL, 'space:folder:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4203, 42, 3, '修改目录', NULL, NULL, NULL, 'space:folder:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4204, 42, 3, '删除目录', NULL, NULL, NULL, 'space:folder:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4301, 43, 3, '查询标签', NULL, NULL, NULL, 'space:tag:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4302, 43, 3, '新增标签', NULL, NULL, NULL, 'space:tag:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4303, 43, 3, '修改标签', NULL, NULL, NULL, 'space:tag:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4304, 43, 3, '删除标签', NULL, NULL, NULL, 'space:tag:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4401, 44, 3, '查询导入任务', NULL, NULL, NULL, 'space:bookmark-import:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4402, 44, 3, '取消导入任务', NULL, NULL, NULL, 'space:bookmark-import:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4501, 45, 3, '查询导出任务', NULL, NULL, NULL, 'space:bookmark-export:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4502, 45, 3, '取消导出任务', NULL, NULL, NULL, 'space:bookmark-export:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (5101, 51, 3, '查询分类', NULL, NULL, NULL, 'forge:category:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (5102, 51, 3, '新增分类', NULL, NULL, NULL, 'forge:category:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (5103, 51, 3, '修改分类', NULL, NULL, NULL, 'forge:category:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (5104, 51, 3, '删除分类', NULL, NULL, NULL, 'forge:category:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (5201, 52, 3, '查询插件', NULL, NULL, NULL, 'forge:plugin:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (5202, 52, 3, '新增插件', NULL, NULL, NULL, 'forge:plugin:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (5203, 52, 3, '修改插件', NULL, NULL, NULL, 'forge:plugin:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (5204, 52, 3, '删除插件', NULL, NULL, NULL, 'forge:plugin:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (5301, 53, 3, '查询版本', NULL, NULL, NULL, 'forge:version:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (5302, 53, 3, '新增版本', NULL, NULL, NULL, 'forge:version:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (5303, 53, 3, '修改版本', NULL, NULL, NULL, 'forge:version:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (5304, 53, 3, '删除版本', NULL, NULL, NULL, 'forge:version:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (5305, 53, 3, '审核版本', NULL, NULL, NULL, 'forge:version:review', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 5, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (5401, 54, 3, '查询评价', NULL, NULL, NULL, 'forge:review:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (5402, 54, 3, '审核/回复评价', NULL, NULL, NULL, 'forge:review:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (5403, 54, 3, '删除评价', NULL, NULL, NULL, 'forge:review:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (8101, 81, 3, '查询Agent', NULL, NULL, NULL, 'manager:ai-agent:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-07-06 00:00:00', '2026-07-06 00:00:00');
INSERT INTO `sys_menu` VALUES (8102, 81, 3, '新增Agent', NULL, NULL, NULL, 'manager:ai-agent:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-07-06 00:00:00', '2026-07-06 00:00:00');
INSERT INTO `sys_menu` VALUES (8103, 81, 3, '编辑Agent', NULL, NULL, NULL, 'manager:ai-agent:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-07-06 00:00:00', '2026-07-06 00:00:00');
INSERT INTO `sys_menu` VALUES (8104, 81, 3, '删除Agent', NULL, NULL, NULL, 'manager:ai-agent:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-07-06 00:00:00', '2026-07-06 00:00:00');
INSERT INTO `sys_menu` VALUES (8105, 81, 3, '运行Agent', NULL, NULL, NULL, 'manager:ai-agent:run', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 5, 1, 1, NULL, '2026-07-06 00:00:00', '2026-07-06 00:00:00');
INSERT INTO `sys_menu` VALUES (8106, 81, 3, '版本管理', NULL, NULL, NULL, 'manager:ai-agent:version', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 6, 1, 1, NULL, '2026-07-06 00:00:00', '2026-07-06 00:00:00');
INSERT INTO `sys_menu` VALUES (8201, 82, 3, '查询实例', NULL, NULL, NULL, 'manager:ai-agent:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-07-06 00:00:00', '2026-07-06 00:00:00');
INSERT INTO `sys_menu` VALUES (8202, 82, 3, '运行/唤醒/续跑', NULL, NULL, NULL, 'manager:ai-agent:run', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-07-06 00:00:00', '2026-07-06 00:00:00');
INSERT INTO `sys_menu` VALUES (8401, 84, 3, '查询流程', NULL, NULL, NULL, 'manager:ai-flow:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-06-27 00:00:00', '2026-07-07 11:55:33');
INSERT INTO `sys_menu` VALUES (8402, 84, 3, '保存流程', NULL, NULL, NULL, 'manager:ai-flow:save', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-06-27 00:00:00', '2026-07-07 11:55:36');
INSERT INTO `sys_menu` VALUES (8403, 84, 3, '删除流程', NULL, NULL, NULL, 'manager:ai-flow:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-06-27 00:00:00', '2026-07-07 11:55:39');
INSERT INTO `sys_menu` VALUES (8404, 84, 3, '运行流程', NULL, NULL, NULL, 'manager:ai-flow:run', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-06-27 00:00:00', '2026-07-07 11:55:44');
INSERT INTO `sys_menu` VALUES (8405, 84, 3, 'AI生成流程', NULL, NULL, NULL, 'manager:ai-copilot:stream', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 5, 1, 1, NULL, '2026-07-22 00:00:00', '2026-07-22 00:00:00');
INSERT INTO `sys_menu` VALUES (8701, 87, 3, '查询档案', NULL, NULL, NULL, 'manager:ai-model-profile:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-06-29 00:00:00', '2026-07-07 11:55:49');
INSERT INTO `sys_menu` VALUES (8702, 87, 3, '新增档案', NULL, NULL, NULL, 'manager:ai-model-profile:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-06-29 00:00:00', '2026-07-07 11:55:51');
INSERT INTO `sys_menu` VALUES (8703, 87, 3, '编辑档案', NULL, NULL, NULL, 'manager:ai-model-profile:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-06-29 00:00:00', '2026-07-07 11:55:54');
INSERT INTO `sys_menu` VALUES (8704, 87, 3, '删除档案', NULL, NULL, NULL, 'manager:ai-model-profile:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-06-29 00:00:00', '2026-07-07 11:55:59');
INSERT INTO `sys_menu` VALUES (8801, 88, 3, '查询服务器', NULL, NULL, NULL, 'manager:ai-mcp-server:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-06-29 00:00:00', '2026-07-07 11:56:04');
INSERT INTO `sys_menu` VALUES (8802, 88, 3, '新增服务器', NULL, NULL, NULL, 'manager:ai-mcp-server:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-06-29 00:00:00', '2026-07-07 11:56:08');
INSERT INTO `sys_menu` VALUES (8803, 88, 3, '编辑服务器', NULL, NULL, NULL, 'manager:ai-mcp-server:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-06-29 00:00:00', '2026-07-07 11:56:11');
INSERT INTO `sys_menu` VALUES (8804, 88, 3, '删除服务器', NULL, NULL, NULL, 'manager:ai-mcp-server:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-06-29 00:00:00', '2026-07-07 11:56:15');
INSERT INTO `sys_menu` VALUES (8901, 89, 3, '查询工具', NULL, NULL, NULL, 'manager:ai-tool:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-06-29 00:00:00', '2026-07-07 11:56:56');
INSERT INTO `sys_menu` VALUES (9001, 90, 3, '查询迭代链', NULL, NULL, NULL, 'manager:ai-iteration:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-07-08 00:00:00', '2026-07-08 00:00:00');
INSERT INTO `sys_menu` VALUES (9002, 90, 3, '新增迭代链', NULL, NULL, NULL, 'manager:ai-iteration:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-07-08 00:00:00', '2026-07-08 00:00:00');
INSERT INTO `sys_menu` VALUES (9003, 90, 3, '编辑迭代链', NULL, NULL, NULL, 'manager:ai-iteration:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-07-08 00:00:00', '2026-07-08 00:00:00');
INSERT INTO `sys_menu` VALUES (9004, 90, 3, '删除迭代链', NULL, NULL, NULL, 'manager:ai-iteration:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-07-08 00:00:00', '2026-07-08 00:00:00');
INSERT INTO `sys_menu` VALUES (9005, 90, 3, '立即推进', NULL, NULL, NULL, 'manager:ai-iteration:run', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 5, 1, 1, NULL, '2026-07-08 00:00:00', '2026-07-08 00:00:00');
INSERT INTO `sys_menu` VALUES (9006, 90, 3, '查回调投递', NULL, NULL, NULL, 'manager:ai-webhook:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 6, 1, 1, NULL, '2026-07-08 00:00:00', '2026-07-08 00:00:00');
INSERT INTO `sys_menu` VALUES (9007, 90, 3, '重发回调', NULL, NULL, NULL, 'manager:ai-webhook:retry', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 7, 1, 1, NULL, '2026-07-08 00:00:00', '2026-07-08 00:00:00');
INSERT INTO `sys_menu` VALUES (9101, 91, 3, '查询提示词', NULL, NULL, NULL, 'manager:ai-prompt:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-07-10 00:00:00', '2026-07-10 00:00:00');
INSERT INTO `sys_menu` VALUES (9102, 91, 3, '新增提示词', NULL, NULL, NULL, 'manager:ai-prompt:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-07-10 00:00:00', '2026-07-10 00:00:00');
INSERT INTO `sys_menu` VALUES (9103, 91, 3, '编辑提示词', NULL, NULL, NULL, 'manager:ai-prompt:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-07-10 00:00:00', '2026-07-10 00:00:00');
INSERT INTO `sys_menu` VALUES (9104, 91, 3, '删除提示词', NULL, NULL, NULL, 'manager:ai-prompt:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-07-10 00:00:00', '2026-07-10 00:00:00');
INSERT INTO `sys_menu` VALUES (2080137523908308993, 84, 3, 'Ai流程生成', 'managerchat', NULL, NULL, 'manager:ai-chat:stream', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 0, 1, 1, NULL, '2026-07-23 11:46:47', '2026-07-23 11:46:47');

-- ----------------------------
-- Table structure for sys_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role`  (
  `id` bigint(20) NOT NULL COMMENT '角色ID（雪花算法）',
  `role_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色编码（唯一，用于程序判断，如 admin, user）',
  `role_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色名称（展示用）',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：1正常 0禁用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
  `delete_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_role_code`(`role_code` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统角色表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_role
-- ----------------------------
INSERT INTO `sys_role` VALUES (1, 'SUPER_ADMIN', '超级管理员', 1, '系统内置超级管理员（拥有所有权限）', NULL, '2026-05-09 03:10:30', NULL, '2026-05-09 15:39:04', 0, NULL);
INSERT INTO `sys_role` VALUES (2052706759021424642, 'admin', '管理员', 1, NULL, NULL, '2026-05-08 19:06:43', NULL, '2026-05-08 19:06:43', 0, NULL);

-- ----------------------------
-- Table structure for sys_role_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu`  (
  `role_id` bigint(20) NOT NULL,
  `menu_id` bigint(20) NOT NULL,
  PRIMARY KEY (`role_id`, `menu_id`) USING BTREE,
  INDEX `idx_menu_id`(`menu_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色菜单关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_role_menu
-- ----------------------------
INSERT INTO `sys_role_menu` VALUES (1, 1);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 1);
INSERT INTO `sys_role_menu` VALUES (1, 2);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2);
INSERT INTO `sys_role_menu` VALUES (1, 10);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 10);
INSERT INTO `sys_role_menu` VALUES (1, 11);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 11);
INSERT INTO `sys_role_menu` VALUES (1, 12);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 12);
INSERT INTO `sys_role_menu` VALUES (1, 13);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 13);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 14);
INSERT INTO `sys_role_menu` VALUES (1, 20);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 20);
INSERT INTO `sys_role_menu` VALUES (1, 21);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 21);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 22);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 23);
INSERT INTO `sys_role_menu` VALUES (1, 24);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 24);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 25);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 26);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 27);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 28);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 29);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 40);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 41);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 42);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 43);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 44);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 45);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 50);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 51);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 52);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 53);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 54);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 55);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 80);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 81);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 82);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 83);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 84);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 85);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 86);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 87);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 88);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 89);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 90);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 91);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 230);
INSERT INTO `sys_role_menu` VALUES (1, 1101);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 1101);
INSERT INTO `sys_role_menu` VALUES (1, 1102);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 1102);
INSERT INTO `sys_role_menu` VALUES (1, 1103);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 1103);
INSERT INTO `sys_role_menu` VALUES (1, 1201);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 1201);
INSERT INTO `sys_role_menu` VALUES (1, 1202);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 1202);
INSERT INTO `sys_role_menu` VALUES (1, 1203);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 1203);
INSERT INTO `sys_role_menu` VALUES (1, 1301);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 1301);
INSERT INTO `sys_role_menu` VALUES (1, 1302);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 1302);
INSERT INTO `sys_role_menu` VALUES (1, 1303);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 1303);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 1401);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 1402);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 1403);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 1404);
INSERT INTO `sys_role_menu` VALUES (1, 2101);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2101);
INSERT INTO `sys_role_menu` VALUES (1, 2102);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2102);
INSERT INTO `sys_role_menu` VALUES (1, 2103);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2103);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2201);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2202);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2203);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2301);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2302);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2303);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2304);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2501);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2502);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2503);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2504);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2701);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2702);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2703);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2704);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2801);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2802);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2803);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2804);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4101);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4102);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4103);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4104);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4201);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4202);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4203);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4204);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4301);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4302);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4303);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4304);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4401);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4402);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4501);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4502);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5101);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5102);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5103);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5104);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5201);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5202);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5203);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5204);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5301);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5302);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5303);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5304);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5305);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5401);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5402);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5403);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8101);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8102);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8103);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8104);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8105);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8106);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8201);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8202);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8401);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8402);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8403);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8404);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8405);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8701);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8702);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8703);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8704);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8801);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8802);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8803);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8804);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8901);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 9001);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 9002);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 9003);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 9004);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 9005);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 9006);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 9007);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 9101);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 9102);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 9103);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 9104);
INSERT INTO `sys_role_menu` VALUES (1, 2080137523908308993);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2080137523908308993);

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`  (
  `id` bigint(20) NOT NULL COMMENT '用户ID（雪花算法）',
  `username` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '登录账号',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '登录密码（加密存储）',
  `nickname` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用户昵称',
  `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '头像地址',
  `mobile` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '手机号',
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '邮箱',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：1正常 0禁用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT 0,
  `delete_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_username`(`username` ASC) USING BTREE,
  UNIQUE INDEX `uk_mobile`(`mobile` ASC) USING BTREE,
  UNIQUE INDEX `uk_email`(`email` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_create_time`(`create_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统用户表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_user
-- ----------------------------
INSERT INTO `sys_user` VALUES (1, 'nebula', '$2a$10$a/c6OV1BuVLclL4HDUOMCuouKwoa7Das6eUHlW2XrXJkURdKKtOJW', '超级管理员', NULL, NULL, NULL, 1, '系统初始化超级管理员', 1, '2026-05-08 11:19:31', NULL, '2026-05-09 15:30:59', 0, NULL);
INSERT INTO `sys_user` VALUES (2052290101098295297, 'admin', '$2a$10$a/c6OV1BuVLclL4HDUOMCuouKwoa7Das6eUHlW2XrXJkURdKKtOJW', 'admin', NULL, NULL, NULL, 1, NULL, NULL, '2026-05-07 15:31:04', NULL, '2026-05-13 15:56:42', 0, NULL);

-- ----------------------------
-- Table structure for sys_user_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role`  (
  `user_id` bigint(20) NOT NULL,
  `role_id` bigint(20) NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`, `role_id`) USING BTREE,
  INDEX `idx_role_id`(`role_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户角色关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_user_role
-- ----------------------------
INSERT INTO `sys_user_role` VALUES (1, 1, '2026-05-09 11:12:12');
INSERT INTO `sys_user_role` VALUES (2052290101098295297, 2052706759021424642, '2026-05-08 19:06:54');

-- ----------------------------
-- Table structure for travel_checkin
-- ----------------------------
DROP TABLE IF EXISTS `travel_checkin`;
CREATE TABLE `travel_checkin`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `trip_day_id` bigint(20) NOT NULL COMMENT '所属行程日ID',
  `destination_id` bigint(20) NULL DEFAULT NULL COMMENT '关联的目的地ID（可选）',
  `custom_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '自定义地点名称',
  `custom_longitude` decimal(10, 7) NULL DEFAULT NULL COMMENT '自定义经度',
  `custom_latitude` decimal(10, 7) NULL DEFAULT NULL COMMENT '自定义纬度',
  `arrival_time` datetime NULL DEFAULT NULL COMMENT '到达时间',
  `departure_time` datetime NULL DEFAULT NULL COMMENT '离开时间',
  `notes` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '游玩笔记',
  `rating` decimal(2, 1) NULL DEFAULT NULL COMMENT '个人评分',
  `photos` json NULL COMMENT '照片ID数组（blog_file_asset的id）',
  `sort_order` int(11) NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_trip_day_id`(`trip_day_id` ASC) USING BTREE,
  INDEX `idx_destination_id`(`destination_id` ASC) USING BTREE,
  INDEX `idx_custom_coord`(`custom_longitude` ASC, `custom_latitude` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '行程打卡点' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of travel_checkin
-- ----------------------------

-- ----------------------------
-- Table structure for travel_destination
-- ----------------------------
DROP TABLE IF EXISTS `travel_destination`;
CREATE TABLE `travel_destination`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '目的地ID',
  `parent_id` bigint(20) NULL DEFAULT NULL COMMENT '父级目的地ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '名称',
  `slug` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'URL标识',
  `type` tinyint(4) NOT NULL DEFAULT 0 COMMENT '类型：0国家 1省份/州 2城市 3景点/POI',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '描述',
  `cover_file_id` bigint(20) NULL DEFAULT NULL COMMENT '封面图（关联blog_file_asset）',
  `longitude` decimal(10, 7) NULL DEFAULT NULL COMMENT '经度',
  `latitude` decimal(10, 7) NULL DEFAULT NULL COMMENT '纬度',
  `address` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '详细地址',
  `visit_count` int(11) NOT NULL DEFAULT 0 COMMENT '访问/打卡次数',
  `rating` decimal(2, 1) NULL DEFAULT NULL COMMENT '评分（0.0-5.0）',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1启用',
  `sort_order` int(11) NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_slug`(`slug` ASC) USING BTREE,
  INDEX `idx_parent_id`(`parent_id` ASC) USING BTREE,
  INDEX `idx_type`(`type` ASC) USING BTREE,
  INDEX `idx_coord`(`longitude` ASC, `latitude` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '旅游目的地表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of travel_destination
-- ----------------------------

-- ----------------------------
-- Table structure for travel_trip
-- ----------------------------
DROP TABLE IF EXISTS `travel_trip`;
CREATE TABLE `travel_trip`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '游记ID',
  `user_id` bigint(20) NOT NULL COMMENT '作者用户ID（关联sys_user）',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '标题',
  `slug` varchar(220) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'URL标识',
  `summary` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '摘要',
  `cover_file_id` bigint(20) NULL DEFAULT NULL COMMENT '封面图（关联blog_file_asset）',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'draft' COMMENT 'draft/published/archived',
  `visibility` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'public' COMMENT 'public/private',
  `start_date` date NULL DEFAULT NULL COMMENT '开始日期',
  `end_date` date NULL DEFAULT NULL COMMENT '结束日期',
  `days_count` int(11) NULL DEFAULT NULL COMMENT '总天数',
  `persons` int(11) NULL DEFAULT NULL COMMENT '人数',
  `cost_total` decimal(10, 2) NULL DEFAULT NULL COMMENT '总花费',
  `cost_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'CNY' COMMENT '货币',
  `view_count` int(11) NOT NULL DEFAULT 0,
  `like_count` int(11) NOT NULL DEFAULT 0,
  `published_at` datetime NULL DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_slug`(`slug` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_status_visibility_published`(`status` ASC, `visibility` ASC, `published_at` ASC) USING BTREE,
  INDEX `idx_dates`(`start_date` ASC, `end_date` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '旅行游记主表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of travel_trip
-- ----------------------------

-- ----------------------------
-- Table structure for travel_trip_blog_post
-- ----------------------------
DROP TABLE IF EXISTS `travel_trip_blog_post`;
CREATE TABLE `travel_trip_blog_post`  (
  `trip_id` bigint(20) NOT NULL,
  `post_id` bigint(20) NOT NULL,
  `post_type` tinyint(4) NOT NULL DEFAULT 0 COMMENT '关联类型：0主要文章 1相关推荐',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`trip_id`, `post_id`) USING BTREE,
  INDEX `idx_post_id`(`post_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '游记与博客文章关联' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of travel_trip_blog_post
-- ----------------------------

-- ----------------------------
-- Table structure for travel_trip_day
-- ----------------------------
DROP TABLE IF EXISTS `travel_trip_day`;
CREATE TABLE `travel_trip_day`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `trip_id` bigint(20) NOT NULL COMMENT '所属游记ID',
  `day_number` int(11) NOT NULL COMMENT '第几天',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '当日标题',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '当日描述',
  `accommodation` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '住宿地点',
  `meal_cost` decimal(10, 2) NULL DEFAULT NULL COMMENT '餐饮花费',
  `transport_cost` decimal(10, 2) NULL DEFAULT NULL COMMENT '交通花费',
  `other_cost` decimal(10, 2) NULL DEFAULT NULL COMMENT '其他花费',
  `sort_order` int(11) NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_trip_day`(`trip_id` ASC, `day_number` ASC) USING BTREE,
  INDEX `idx_trip_id`(`trip_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '行程日表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of travel_trip_day
-- ----------------------------

-- ----------------------------
-- Table structure for worker_node
-- ----------------------------
DROP TABLE IF EXISTS `worker_node`;
CREATE TABLE `worker_node`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '自增主键，作为 workerId',
  `host_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'host：ACTUAL=IP，CONTAINER=hostname',
  `port` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'port：ACTUAL=时间戳-随机数，CONTAINER=容器端口',
  `type` int(11) NOT NULL COMMENT '节点类型：1=CONTAINER 2=ACTUAL',
  `launch_date` date NOT NULL COMMENT '启动日期',
  `modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `created` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'UID Generator workerId 分配表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of worker_node
-- ----------------------------

SET FOREIGN_KEY_CHECKS = 1;
