-- AI 流程编排执行实例：断点续跑的状态持久化（ai_flow_run / ai_flow_run_node 两表）
-- 一次编排执行 = 一条 ai_flow_run；执行过程中每完成一个节点写一条 ai_flow_run_node 并刷新 run 的全量产物快照。
-- 节点失败时 run 标记 FAILED 并保留进度，可经 run_id 续跑（跳过已完成节点）。

-- 表1：执行实例
CREATE TABLE `ai_flow_run`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `run_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '执行实例唯一标识，业务键，续跑入口',
  `flow_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '流程编码',
  `version` int NOT NULL DEFAULT 1 COMMENT '流程版本',
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
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI流程编排执行实例表' ROW_FORMAT = Dynamic;

-- 表2：执行节点轨迹
CREATE TABLE `ai_flow_run_node`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `run_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '归属执行实例标识',
  `node_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '节点编码',
  `seq` int NOT NULL DEFAULT 0 COMMENT '执行序（本次执行内自增）',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'SUCCESS' COMMENT '节点状态：SUCCESS',
  `output` json NULL COMMENT '该节点完成时的产物快照（JSON对象，可空）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_run_id`(`run_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI流程编排执行节点轨迹表' ROW_FORMAT = Dynamic;
