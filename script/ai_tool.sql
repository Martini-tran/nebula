-- ----------------------------
-- AI 工具定义表（ai_tool）
-- 流程节点（nodeType=TOOL）可调用的工具的「只读目录镜像」。
-- 工具的实现（invoke 逻辑）定义在代码中（每个工具实现 ToolDefinition 接口并注册为 Bean）；本表只承载
-- 工具的自描述元数据（编码/名称/描述/入参schema），供前端流程编辑器渲染「可选工具列表 + 参数表单」，
-- 并由节点的 nodeConfig.toolCode 引用。
--
-- 数据来源：代码 = 唯一真相源。应用启动时由同步器（ToolRegistrySynchronizer）扫描所有 ToolDefinition Bean，
-- 按 tool_code upsert 进本表；代码中已移除的工具置 enabled=0（软下线，不物理删，避免历史流程引用悬空）。
-- 故本表对管理端只读，无人工增删改。
--
-- 注意：本表不含任何密钥/凭证 —— 工具若需调用外部服务，其凭证走所引用的 ai_model_profile / ai_mcp_server
-- 或全局配置，工具元数据本身不落密钥，故本表无加密需求。
-- ----------------------------

-- ----------------------------
-- Table structure for ai_tool
-- ----------------------------
DROP TABLE IF EXISTS `ai_tool`;
CREATE TABLE `ai_tool`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '工具ID',
  `tool_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '工具编码，全局唯一，= 代码 ToolDefinition.code()，被流程节点引用',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '工具显示名',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用途描述（兼作 LLM function description）',
  `category` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '分类（http/data/search 等），编辑器分组用',
  `params_schema` json NULL COMMENT '入参 JSON Schema，声明工具所需参数/类型/必填项，编辑器据此渲染参数表单',
  `result_schema` json NULL COMMENT '出参结构描述（可空），给下游节点提示产物形状',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用：0=已下线(代码中已移除) 1=启用',
  `builtin` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否内置工具：1=代码内置(由同步器维护) 0=外部登记(预留扩展位)',
  `sort_no` int NOT NULL DEFAULT 0 COMMENT '排序号',
  `remark` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_tool_code`(`tool_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI工具定义表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 菜单：AI工具（挂在 AI模型目录 70 下，子菜单 73 + 只读查询按钮 7301）
-- 依赖：目录 70（'AI模型'）由 ai_model_profile.sql 创建，请先执行该脚本再执行本段，否则子菜单挂载失败。
-- 本表只读：仅一个查询按钮(7301)，无新增/编辑/删除按钮。
-- accessMode=backend，菜单由 /menu/routes 下发；超管登录返回全部菜单，普通角色需另绑 sys_role_menu。
-- 组件路径对应 ui/.../views/ai-tool/index.vue；权限标识与 ToolAdminController 的 @SaCheckPermission 对齐。
-- ----------------------------
INSERT INTO `sys_menu` VALUES (73, 70, 2, 'AI工具', 'AiTool', '/ai-model/tools', 'ai-tool/index', 'manager:ai-tool:list', 'lucide:wrench', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-06-29 00:00:00', '2026-06-29 00:00:00');
INSERT INTO `sys_menu` VALUES (7301, 73, 3, '查询工具', NULL, NULL, NULL, 'manager:ai-tool:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-06-29 00:00:00', '2026-06-29 00:00:00');
