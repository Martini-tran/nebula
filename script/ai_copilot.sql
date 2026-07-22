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
-- 流程设计助手（Copilot）权限（增量，可重复执行）
-- 端点 POST /admin/ai-flow/copilot/stream，权限点 manager:ai-copilot:stream
-- 前端入口在流程编辑器第四栏（模式开关），不建独立菜单页，仅加一个按钮权限，挂在「流程列表」(id=84) 下
-- 无需新建业务表：生成的流程落 ai_flow 三表（复用 FlowAdminService.save），派生的 Agent 落 ai_agent
-- ----------------------------
DELETE FROM `sys_role_menu` WHERE `menu_id` IN (8405);
DELETE FROM `sys_menu` WHERE `id` IN (8405);

INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_type`, `menu_name`, `perms`, `sort`, `visible`, `status`, `create_time`, `update_time`) VALUES (8405, 84, 3, 'AI生成流程', 'manager:ai-copilot:stream', 5, 1, 1, '2026-07-22 00:00:00', '2026-07-22 00:00:00');

-- 授权给 admin 管理员角色（与其余 AI 菜单一致）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (2052706759021424642, 8405);

SET FOREIGN_KEY_CHECKS = 1;
