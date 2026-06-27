-- ----------------------------
-- AI 流程编排：流程定义三表（ai_flow / ai_flow_node / ai_flow_edge）
-- 对应 nebula-sdk-ai 的 FlowDefinition / FlowNodeDefinition / FlowEdgeDefinition
-- 由 DatabaseFlowDefinitionRepository 读取组装为 FlowDefinition 供 FlowEngine 运行
-- ----------------------------

-- ----------------------------
-- Table structure for ai_flow
-- ----------------------------
DROP TABLE IF EXISTS `ai_flow`;
CREATE TABLE `ai_flow`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '流程ID',
  `flow_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '流程编码，全局唯一',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '流程名称',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '流程描述',
  `version` int NOT NULL DEFAULT 1 COMMENT '版本号，用于缓存键与灰度',
  `default_profile_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '默认模型档案编码',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态：0=停用 1=启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_flow_code`(`flow_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI流程定义表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ai_flow_node
-- ----------------------------
DROP TABLE IF EXISTS `ai_flow_node`;
CREATE TABLE `ai_flow_node`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '节点ID',
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
  `max_tokens` int NULL DEFAULT NULL COMMENT '最大输出token数（覆盖档案）',
  `top_p` double NULL DEFAULT NULL COMMENT 'Top P采样参数（覆盖档案）',
  `timeout_ms` int NULL DEFAULT NULL COMMENT '请求超时毫秒（覆盖档案）',
  `stop` json NULL COMMENT '停止词（JSON数组）',
  `options` json NULL COMMENT '扩展参数（JSON对象）',
  `input_mapping` json NULL COMMENT '输入映射：模板变量名->上下文键（JSON对象）',
  `output_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '产物写回上下文的键',
  `output_mode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'TEXT' COMMENT '产物模式：TEXT/JSON',
  `node_config` json NULL COMMENT '非PROMPT类型的扩展配置（JSON对象）',
  `remember_trace` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否记录该节点轨迹',
  `sort_no` int NOT NULL DEFAULT 0 COMMENT '排序号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_flow_node`(`flow_code` ASC, `node_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI流程节点表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ai_flow_edge
-- ----------------------------
DROP TABLE IF EXISTS `ai_flow_edge`;
CREATE TABLE `ai_flow_edge`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '边ID',
  `flow_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '归属流程编码',
  `from_node` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '起点节点编码',
  `to_node` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '终点节点编码',
  `condition_expr` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '条件表达式(SpEL)，空表示无条件直达',
  `sort_no` int NOT NULL DEFAULT 0 COMMENT '排序号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_flow_code`(`flow_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI流程边表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 菜单：AI编排（顶级目录 60 + 流程列表 61 + 隐藏的流程编辑器 62 + 按钮权限）
-- accessMode=backend，菜单由 /menu/routes 下发；超管登录返回全部菜单，普通角色需另绑 sys_role_menu。
-- 组件路径对应 ui/.../views/ai-flow/index.vue、ai-flow/editor/index.vue
-- ----------------------------
INSERT INTO `sys_menu` VALUES (60, 0, 1, 'AI编排', 'AiFlow', '/ai-flow', 'BasicLayout', NULL, 'lucide:workflow', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 310, 1, 1, NULL, '2026-06-27 00:00:00', '2026-06-27 00:00:00');
INSERT INTO `sys_menu` VALUES (61, 60, 2, '流程列表', 'AiFlowList', '/ai-flow/list', 'ai-flow/index', 'blog:ai-flow:list', 'lucide:list-tree', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-06-27 00:00:00', '2026-06-27 00:00:00');
INSERT INTO `sys_menu` VALUES (62, 60, 2, '流程编辑器', 'AiFlowEditor', '/ai-flow/editor', 'ai-flow/editor/index', 'blog:ai-flow:query', 'lucide:pencil-ruler', NULL, '/ai-flow/list', NULL, 0, 0, 1, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-06-27 00:00:00', '2026-06-27 00:00:00');
INSERT INTO `sys_menu` VALUES (6101, 61, 3, '查询流程', NULL, NULL, NULL, 'blog:ai-flow:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-06-27 00:00:00', '2026-06-27 00:00:00');
INSERT INTO `sys_menu` VALUES (6102, 61, 3, '保存流程', NULL, NULL, NULL, 'blog:ai-flow:save', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-06-27 00:00:00', '2026-06-27 00:00:00');
INSERT INTO `sys_menu` VALUES (6103, 61, 3, '删除流程', NULL, NULL, NULL, 'blog:ai-flow:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-06-27 00:00:00', '2026-06-27 00:00:00');
INSERT INTO `sys_menu` VALUES (6104, 61, 3, '运行流程', NULL, NULL, NULL, 'blog:ai-flow:run', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-06-27 00:00:00', '2026-06-27 00:00:00');
