-- ----------------------------
-- AI 智能体（状态机）：Agent 定义 + 状态机实例两表（对应设计文档第七章 7.1 / 7.2 / 7.3）
-- 对应 nebula-sdk-ai 的 AgentDefinition / AgentInstanceSnapshot，由 nebula-sdk-ai-flow 的
-- DatabaseAgentDefinitionRepository / DatabaseAgentInstanceStore 读写。
--
-- 阶段 2 范围：Agent 层落库 + 1:N 复用 + Memory 钩子；挂起/递归列（awaiting_events / parent_* ，阶段 3）
-- 与补偿列（is_compensation / last_checkpoint_state，阶段 2.5）建列但运行时不写。
-- ----------------------------

-- ----------------------------
-- 7.2 Flow 三表补状态机字段（阶段 1 已加 SDK 模型字段，此处补 DDL 列；引擎按 engine_type 分流）
-- ----------------------------
ALTER TABLE `ai_flow` ADD COLUMN `engine_type` varchar(16) NOT NULL DEFAULT 'DAG'
  COMMENT '执行内核：DAG | STATE_MACHINE';
ALTER TABLE `ai_flow` ADD COLUMN `max_transitions` int NOT NULL DEFAULT 100
  COMMENT '状态机全局转移次数上限，防死循环；per-Flow 可配';
ALTER TABLE `ai_flow` ADD COLUMN `max_agent_depth` int NOT NULL DEFAULT 8
  COMMENT '递归子 Agent 最大深度，防无限递归（阶段 3 用）';
ALTER TABLE `ai_flow_node` ADD COLUMN `state_type` varchar(16) NULL DEFAULT NULL
  COMMENT '状态机语义：ENTRY | NORMAL | TERMINAL（DAG 忽略）';
ALTER TABLE `ai_flow_edge` ADD COLUMN `event_name` varchar(64) NULL DEFAULT NULL
  COMMENT '转移触发事件名（状态机用，DAG 忽略）';

-- ----------------------------
-- 7.1 Table structure for ai_agent（Agent 定义）
-- agent_code 跨版本稳定（= 记忆隔离键），(agent_code, version) 联合唯一；一个 Flow 可被多个 Agent 复用。
-- ----------------------------
DROP TABLE IF EXISTS `ai_agent`;
CREATE TABLE `ai_agent`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `agent_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Agent编码，跨版本稳定的逻辑标识（非全局唯一）；= 记忆隔离键，同 agent_code 各版本共享一份长期记忆',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '名称',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '描述',
  `flow_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '引用的编排图编码',
  `flow_version` int NOT NULL DEFAULT 1 COMMENT '引用的编排图版本（可换绑做灰度）',
  `input_schema` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '输入契约 JSON Schema',
  `output_schema` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '输出契约 JSON Schema',
  `memory_config` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '记忆配置：enabled/import 键/export 策略(Replace/Append/Summary)',
  `default_profile_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '默认模型档案编码',
  `version` int NOT NULL DEFAULT 1 COMMENT 'Agent 定义版本，发布后不可变',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '0=停用 1=启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_agent_code_version`(`agent_code` ASC, `version` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI 智能体定义表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 7.3 Table structure for ai_agent_instance（状态机实例）
-- graph_snapshot 版本锁定：续跑/回放只从此加载图，不回查 node/edge 表。
-- context_snapshot 分级落盘：仅挂起/越检查点/终态时全量刷新，配 context_snapshot_seq 增量重放。
-- ----------------------------
DROP TABLE IF EXISTS `ai_agent_instance`;
CREATE TABLE `ai_agent_instance`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '实例唯一标识，业务键，续跑/唤醒入口',
  `agent_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '归属 Agent 编码（= 记忆隔离键）',
  `agent_version` int NOT NULL COMMENT '审计标记：从哪个 Agent 版本创建',
  `flow_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '审计标记：引用的编排图编码',
  `flow_version` int NOT NULL COMMENT '审计标记：从哪个 Flow 版本创建；运行时不据此查表（改用 graph_snapshot）',
  `graph_snapshot` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '★版本锁定核心：创建时编译的完整源图定义(节点+边+stateConfig+guard)JSON。续跑/回放只从此加载，不回查 node/edge 表',
  `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '归属用户ID',
  `conversation_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '关联会话ID',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING | SUSPENDED | SUCCESS | FAILED',
  `current_state` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '状态机当前状态（节点编码）',
  `last_checkpoint_state` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '最近越过的检查点状态（补偿回滚边界；阶段 2.5 启用）',
  `awaiting_events` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'SUSPENDED 时等待的事件名集合(JSON数组)，signal 命中其一即唤醒（阶段 3 启用）',
  `inputs` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '初始输入快照(JSON)',
  `context_snapshot` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '分区上下文快照(JSON)，分级落盘：仅挂起/越检查点/终态时全量刷新',
  `context_snapshot_seq` int NOT NULL DEFAULT -1 COMMENT '快照对应的 transition seq，恢复时从此 seq 之后重放增量',
  `outputs` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'Outputs 快照(JSON)',
  `transition_count` int NOT NULL DEFAULT 0 COMMENT '已转移次数，配合 max_transitions 防死循环',
  `parent_instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '父实例（递归子 Agent 用，顶层为空，阶段 3 用）',
  `parent_node_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '父实例中触发本子实例的 AgentNode 编码（回调唤醒用，阶段 3）',
  `lock_version` int NOT NULL DEFAULT 0 COMMENT '乐观锁：signal/resume 用 CAS 抢占，防并发重复推进',
  `error_msg` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '失败原因',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_instance_id`(`instance_id` ASC) USING BTREE,
  INDEX `idx_parent`(`parent_instance_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI 智能体执行实例（状态机）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 7.3 Table structure for ai_agent_instance_transition（状态转移历史，回放/审计）
-- 每次 attempt（含重试失败）落一行：outcome=RETRY 标记中间失败；恢复时只重放 outcome=SUCCESS 行。
-- node_result 存该步对 context 的变更集(delta)，重放时 apply 它重建 context。
-- ----------------------------
DROP TABLE IF EXISTS `ai_agent_instance_transition`;
CREATE TABLE `ai_agent_instance_transition`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '归属实例标识',
  `seq` int NOT NULL COMMENT '转移序号，从 0 递增（重试也占序号）',
  `from_state` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '源状态',
  `to_state` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '目标状态',
  `event_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '触发本次转移的事件（如有）',
  `attempt` int NOT NULL DEFAULT 0 COMMENT '进入 to_state 的重试次数（0=首次）',
  `outcome` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'SUCCESS' COMMENT '轨迹类型：SUCCESS | RETRY | FAILED | COMPENSATED，回放时可折叠 RETRY 行',
  `is_compensation` tinyint(1) NOT NULL DEFAULT 0 COMMENT '预留：1=补偿轨迹（Saga 回滚，阶段 2.5 启用）',
  `node_result` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '进入 to_state 后该节点产物(delta)；失败/重试时记错误摘要（恢复时只重放 outcome=SUCCESS 行）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_instance_seq`(`instance_id` ASC, `seq` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI 智能体状态转移历史（回放/审计），每次 attempt 落一行' ROW_FORMAT = Dynamic;
