/*
 Navicat Premium Dump SQL

 Source Server Type    : MySQL
 Source Schema         : nebula

 Target Server Type    : MySQL
 File Encoding         : 65001
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for ai_skill
-- ----------------------------
DROP TABLE IF EXISTS `ai_skill`;
CREATE TABLE `ai_skill`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '技能ID',
  `skill_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '技能编码，全局唯一，被 Agent(ai_agent.skill_codes) / 节点(node_config.skillCodes) 引用',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '技能名称',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '技能描述，用于人工挑选与后续语义匹配',
  `instructions` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '指令正文（Markdown），命中后作为 system 消息注入；支持 {{变量名}} 占位符，由编排上下文变量池渲染',
  `trigger_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'MANUAL' COMMENT '触发方式：AUTO=范围内始终装载 MANUAL=被显式引用才装载',
  `tool_codes` json NULL COMMENT '绑定的工具编码数组（JSON数组，如 ["knowledge_search"]），命中后并入该次调用的工具白名单',
  `mcp_server_codes` json NULL COMMENT '绑定的 MCP 服务器编码数组（JSON数组），预留：MCP 工具接入闭环后按此拉起',
  `sort_no` int(11) NOT NULL DEFAULT 0 COMMENT '排序号，决定多技能注入时的 system 消息先后顺序',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态：0=停用 1=启用。停用即对运行时不可见，是关掉一个技能的唯一开关',
  `remark` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_skill_code`(`skill_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI技能表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_skill
-- ----------------------------
INSERT INTO `ai_skill` (`skill_code`, `name`, `description`, `instructions`, `trigger_type`, `tool_codes`, `mcp_server_codes`, `sort_no`, `status`, `remark`) VALUES ('TECH_BLOG_WRITING', '技术博客写作规范', '撰写技术博客时的结构、代码示例与行文要求', '## 技术博客写作规范\n\n撰写技术文章时遵循以下要求：\n\n1. **开篇直入主题**：首段说明这篇文章解决什么问题，不写「随着互联网的发展」类空话。\n2. **代码优先**：每个概念配一段完整可编译的示例代码，不写伪代码片段。\n3. **由浅入深**：先给最小可运行示例，再逐步引入复杂场景。\n4. **说明取舍**：涉及技术选型时，写清备选方案与不选的理由。\n5. **结尾给出可验证的结论**，不写「希望本文对你有帮助」类套话。', 'MANUAL', NULL, NULL, 1, 1, '博客系列生成流程可挂载此技能');
INSERT INTO `ai_skill` (`skill_code`, `name`, `description`, `instructions`, `trigger_type`, `tool_codes`, `mcp_server_codes`, `sort_no`, `status`, `remark`) VALUES ('KNOWLEDGE_GROUNDED_QA', '知识库检索问答', '回答前先检索知识库，基于检索结果作答并标注来源', '## 知识库检索问答\n\n回答用户问题前，先调用 knowledge_search 工具检索相关资料，然后：\n\n1. **基于检索结果作答**，不要凭记忆编造。\n2. **标注来源**：每个关键结论标注其来自哪条检索结果。\n3. **检索不到就直说**：明确告知「知识库中未找到相关内容」，不要用通用知识填充后假装来自知识库。\n4. 检索结果与问题无关时，重新组织关键词再检索一次。', 'MANUAL', '[\"knowledge_search\"]', NULL, 2, 1, '演示「技能绑定工具」：挂载后模型自动获得 knowledge_search 工具');

-- ----------------------------
-- ai_agent 增加 Agent 级技能绑定列（增量，可重复执行）
-- ----------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_agent' AND COLUMN_NAME = 'skill_codes');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE `ai_agent` ADD COLUMN `skill_codes` json NULL COMMENT ''Agent级技能编码数组（JSON数组），该Agent全部节点共享，与节点级 nodeConfig.skillCodes 取并集'' AFTER `default_profile_code`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------
-- 菜单与权限（增量，与 sys_menu.sql 中同 id 的记录一致；可重复执行）
-- 菜单 92 挂在「智能体」目录（id=80）下，按钮 9201~9204
-- ----------------------------
DELETE FROM `sys_role_menu` WHERE `menu_id` IN (92, 9201, 9202, 9203, 9204);
DELETE FROM `sys_menu` WHERE `id` IN (92, 9201, 9202, 9203, 9204);

INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_type`, `menu_name`, `route_name`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`) VALUES (92, 80, 2, '技能', 'AiSkill', '/ai-model/skills', 'ai-skill/index', 'manager:ai-skill:list', 'lucide:sparkles', 5, 1, 1, '2026-08-14 00:00:00', '2026-08-14 00:00:00');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_type`, `menu_name`, `perms`, `sort`, `visible`, `status`, `create_time`, `update_time`) VALUES (9201, 92, 3, '查询技能', 'manager:ai-skill:query', 1, 1, 1, '2026-08-14 00:00:00', '2026-08-14 00:00:00');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_type`, `menu_name`, `perms`, `sort`, `visible`, `status`, `create_time`, `update_time`) VALUES (9202, 92, 3, '新增技能', 'manager:ai-skill:add', 2, 1, 1, '2026-08-14 00:00:00', '2026-08-14 00:00:00');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_type`, `menu_name`, `perms`, `sort`, `visible`, `status`, `create_time`, `update_time`) VALUES (9203, 92, 3, '编辑技能', 'manager:ai-skill:edit', 3, 1, 1, '2026-08-14 00:00:00', '2026-08-14 00:00:00');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_type`, `menu_name`, `perms`, `sort`, `visible`, `status`, `create_time`, `update_time`) VALUES (9204, 92, 3, '删除技能', 'manager:ai-skill:delete', 4, 1, 1, '2026-08-14 00:00:00', '2026-08-14 00:00:00');

-- 授权给 admin 管理员角色（与其余 AI 菜单一致）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (2052706759021424642, 92);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (2052706759021424642, 9201);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (2052706759021424642, 9202);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (2052706759021424642, 9203);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (2052706759021424642, 9204);

SET FOREIGN_KEY_CHECKS = 1;
