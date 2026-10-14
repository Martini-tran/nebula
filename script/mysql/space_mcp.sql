/*
 个人空间 MCP：给 API Key 用的授权范围（权限码）。增量脚本，可重复执行。

 MCP 端点 POST /space/me/mcp 只对 key 开放这一个地址，每个工具要求下面其中一个范围
 （代码见 nebula-service-space 的 com.nebula.space.mcp.SpaceMcpScopes）：
   space:me:read       读任务、清单、会议、笔记、专注、日报周报、搜索
   space:report:write  写日报 / 周报
   space:me:write      写任务、笔记（新建、修改、完成，不含删除）

 /me/** 接口本身只认登录、不看权限码，这几行只是为了让后台签发 key 时能选到这些范围
 （签发时要求操作人自己拥有所选的每一个权限）。
*/

SET NAMES utf8mb4;

-- ----------------------------
-- 按钮 4001~4003 挂在「个人空间」目录（id=40）下。
-- 这里不给任何角色授权：超级管理员靠通配符 * 天然拥有；其余角色按需在「角色管理」里勾选。
-- 注意：重跑本脚本会清掉这些按钮已有的角色授权（先删后插），重跑后需重新勾选。
-- ----------------------------
DELETE FROM `sys_role_menu` WHERE `menu_id` IN (4001, 4002, 4003);
DELETE FROM `sys_menu` WHERE `id` IN (4001, 4002, 4003);

INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_type`, `menu_name`, `perms`, `sort`, `visible`, `status`) VALUES (4001, 40, 3, 'MCP 读取个人空间', 'space:me:read', 101, 1, 1);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_type`, `menu_name`, `perms`, `sort`, `visible`, `status`) VALUES (4002, 40, 3, 'MCP 写日报周报', 'space:report:write', 102, 1, 1);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_type`, `menu_name`, `perms`, `sort`, `visible`, `status`) VALUES (4003, 40, 3, 'MCP 写任务与笔记', 'space:me:write', 103, 1, 1);
