-- ----------------------------
-- 后台管理（菜单 / 角色）路由初始化数据
-- 依赖：先执行 sys_menu_alter.sql，使 sys_menu 含有 route_name / hide_* 等列
-- 与 sys_menu_seed.sql 中的 /system 父节点（id=10）配套
--
-- 多次执行安全：先 DELETE 再 INSERT
-- ----------------------------

DELETE FROM sys_menu WHERE id IN (12, 13);

INSERT INTO sys_menu (
  id, parent_id, menu_type, menu_name, route_name, path, component,
  perms, icon, sort, visible, status, remark
) VALUES
  -- 菜单管理
  (12, 10, 2, 'system.menu.name',  'SystemMenu', '/system/menu', 'system/menu/list',
   'system:menu:list', 'lucide:menu',     2, 1, 1, '菜单管理'),
  -- 角色管理
  (13, 10, 2, 'system.role.name',  'SystemRole', '/system/role', 'system/role/list',
   'system:role:list', 'lucide:user-cog', 3, 1, 1, '角色管理');
