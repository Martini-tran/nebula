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
-- Table structure for ai_prompt
-- ----------------------------
DROP TABLE IF EXISTS `ai_prompt`;
CREATE TABLE `ai_prompt`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '提示词ID',
  `prompt_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '提示词编码，全局唯一，被流程/节点引用',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '提示词名称',
  `role` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'system' COMMENT '消息角色：system=系统设定 user=用户输入 assistant=助手示例',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '提示词正文，支持 {{变量名}} 占位符，渲染时由 variables 声明的变量替换',
  `variables` json NULL COMMENT '变量声明数组（JSON数组，如 [{"name":"topic","type":"string","required":true,"description":"主题"}]），编辑器据此渲染填充表单',
  `remark` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_prompt_code`(`prompt_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI提示词表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_prompt
-- ----------------------------
INSERT INTO `ai_prompt` (`prompt_code`, `name`, `role`, `content`, `variables`, `remark`) VALUES ('BLOG_SERIES_OUTLINE', '博客系列大纲生成', 'user', '请围绕主题「{{topic}}」规划一个 {{count}} 篇的系列文章大纲，写作风格为 {{style}}。\n每篇给出标题与一句话简介，输出 JSON 数组。', '[{\"name\": \"topic\", \"type\": \"string\", \"required\": true, \"description\": \"系列主题\"}, {\"name\": \"count\", \"type\": \"integer\", \"required\": true, \"description\": \"文章篇数\"}, {\"name\": \"style\", \"type\": \"string\", \"required\": false, \"description\": \"写作风格，如实战驱动\"}]', '博客系列生成流程的大纲节点提示词');
INSERT INTO `ai_prompt` (`prompt_code`, `name`, `role`, `content`, `variables`, `remark`) VALUES ('TECH_WRITER_PERSONA', '技术写作者人设', 'system', '你是一位资深 Java 工程师兼技术博主，擅长用可运行的代码示例讲解原理。行文简洁、避免空话，每个概念都配一段完整可编译的示例代码。', NULL, '无变量的静态人设提示词，可被多个流程节点复用');

-- ----------------------------
-- 菜单与权限（增量，与 sys_menu.sql 中同 id 的记录一致；可重复执行）
-- 菜单 91 挂在「智能体」目录（id=80）下，按钮 9101~9104
-- ----------------------------
DELETE FROM `sys_role_menu` WHERE `menu_id` IN (91, 9101, 9102, 9103, 9104);
DELETE FROM `sys_menu` WHERE `id` IN (91, 9101, 9102, 9103, 9104);

INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_type`, `menu_name`, `route_name`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`) VALUES (91, 80, 2, '提示词', 'AiPrompt', '/ai-model/prompts', 'ai-prompt/index', 'manager:ai-prompt:list', 'lucide:message-square-text', 4, 1, 1, '2026-07-10 00:00:00', '2026-07-10 00:00:00');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_type`, `menu_name`, `perms`, `sort`, `visible`, `status`, `create_time`, `update_time`) VALUES (9101, 91, 3, '查询提示词', 'manager:ai-prompt:query', 1, 1, 1, '2026-07-10 00:00:00', '2026-07-10 00:00:00');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_type`, `menu_name`, `perms`, `sort`, `visible`, `status`, `create_time`, `update_time`) VALUES (9102, 91, 3, '新增提示词', 'manager:ai-prompt:add', 2, 1, 1, '2026-07-10 00:00:00', '2026-07-10 00:00:00');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_type`, `menu_name`, `perms`, `sort`, `visible`, `status`, `create_time`, `update_time`) VALUES (9103, 91, 3, '编辑提示词', 'manager:ai-prompt:edit', 3, 1, 1, '2026-07-10 00:00:00', '2026-07-10 00:00:00');
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_type`, `menu_name`, `perms`, `sort`, `visible`, `status`, `create_time`, `update_time`) VALUES (9104, 91, 3, '删除提示词', 'manager:ai-prompt:delete', 4, 1, 1, '2026-07-10 00:00:00', '2026-07-10 00:00:00');

-- 授权给 admin 管理员角色（与其余 AI 菜单一致）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (2052706759021424642, 91);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (2052706759021424642, 9101);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (2052706759021424642, 9102);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (2052706759021424642, 9103);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (2052706759021424642, 9104);

SET FOREIGN_KEY_CHECKS = 1;
