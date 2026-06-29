-- ----------------------------
-- AI MCP 服务器配置表（ai_mcp_server）
-- 集中维护一组 MCP（Model Context Protocol）服务器接入点，对外暴露工具/资源供 AI 推理时调用。
-- 与 ai_model_profile 平行：profile 描述「连哪个大模型」，本表描述「大模型能用哪些外部工具源」。
-- 由 server_code 全局唯一标识，被 ai_flow / ai_flow_node 引用复用。
--
-- 传输形态由 transport 区分（核心分叉字段）：
--   stdio           —— 本地拉起子进程，使用 command + args + env 通信；
--   sse             —— 远程 HTTP（Server-Sent Events）端点，使用 url + headers + auth_token；
--   streamable-http —— 远程 HTTP（Streamable HTTP）端点，同上。
-- stdio 专属列与远程专属列均可空，按 transport 取用；必填校验在 Service 层处理（不在 DB 层强约束）。
--
-- 注意：auth_token 以密文存储（AES 加密，落库前加密 / 读取后解密，加解密在 Service 层处理），
-- 复用 ai_model_profile 同一密钥配置 nebula.ai.profile.secret，故列宽放宽含密文膨胀冗余。
-- headers / env 为非敏感配置，以明文 JSON 存储（敏感凭证请放 auth_token）。
-- ----------------------------

-- ----------------------------
-- Table structure for ai_mcp_server
-- ----------------------------
DROP TABLE IF EXISTS `ai_mcp_server`;
CREATE TABLE `ai_mcp_server`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '服务器ID',
  `server_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '服务器编码，全局唯一，被流程/节点引用',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '服务器名称',
  `transport` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '传输类型：stdio/sse/streamable-http',
  `command` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '【stdio】启动命令（npx/uvx/python 等）',
  `args` json NULL COMMENT '【stdio】命令参数数组（JSON数组，如 ["-y","@scope/server","/data"]）',
  `env` json NULL COMMENT '【stdio】子进程环境变量（JSON对象，明文，敏感凭证请放 auth_token）',
  `url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '【远程】SSE/HTTP 端点地址',
  `headers` json NULL COMMENT '【远程】自定义请求头（JSON对象，明文非敏感部分）',
  `auth_token` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '【远程】鉴权凭证 Bearer/apiKey（AES加密密文存储，列宽含密文膨胀冗余）',
  `timeout_ms` int NULL DEFAULT NULL COMMENT '连接/请求超时（毫秒）',
  `options` json NULL COMMENT '扩展参数，透传未来新增的 MCP 配置项（JSON对象）',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态：0=停用 1=启用',
  `remark` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_server_code`(`server_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI MCP服务器配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 菜单：MCP服务器（挂在 AI模型目录 70 下，子菜单 72 + 按钮权限 7201~7204）
-- 依赖：目录 70（'AI模型'）由 ai_model_profile.sql 创建，请先执行该脚本再执行本段，否则子菜单挂载失败。
-- accessMode=backend，菜单由 /menu/routes 下发；超管登录返回全部菜单，普通角色需另绑 sys_role_menu。
-- 组件路径对应 ui/.../views/ai-mcp-server/index.vue；权限标识与 McpServerAdminController 的 @SaCheckPermission 对齐。
-- ----------------------------
INSERT INTO `sys_menu` VALUES (72, 70, 2, 'MCP服务器', 'AiMcpServer', '/ai-model/mcp-servers', 'ai-mcp-server/index', 'manager:ai-mcp-server:list', 'lucide:plug', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-06-29 00:00:00', '2026-06-29 00:00:00');
INSERT INTO `sys_menu` VALUES (7201, 72, 3, '查询服务器', NULL, NULL, NULL, 'manager:ai-mcp-server:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-06-29 00:00:00', '2026-06-29 00:00:00');
INSERT INTO `sys_menu` VALUES (7202, 72, 3, '新增服务器', NULL, NULL, NULL, 'manager:ai-mcp-server:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-06-29 00:00:00', '2026-06-29 00:00:00');
INSERT INTO `sys_menu` VALUES (7203, 72, 3, '编辑服务器', NULL, NULL, NULL, 'manager:ai-mcp-server:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-06-29 00:00:00', '2026-06-29 00:00:00');
INSERT INTO `sys_menu` VALUES (7204, 72, 3, '删除服务器', NULL, NULL, NULL, 'manager:ai-mcp-server:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-06-29 00:00:00', '2026-06-29 00:00:00');
