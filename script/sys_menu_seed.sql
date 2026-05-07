-- ----------------------------
-- 系统菜单初始化数据（前端 backend 模式 / 路由由后端下发）
-- 字段约定：
--   menu_type: 1=目录 2=菜单 3=按钮（按钮不参与路由生成）
--   path:      绝对路径，前端 vue-router 直接吃
--   component: 顶级目录填 "BasicLayout"（前端 layoutMap 解析）
--              二级菜单填 "目录/页面/index" 风格（前端用 import.meta.glob 匹配 /views/<key>.vue）
--   sort:      越小越靠前
--   visible/status: 1=显示/启用 0=隐藏/禁用
--
-- 多次执行安全：先 DELETE 再 INSERT；如果不希望抹掉自己后续加的菜单，请只跑头一次
-- ----------------------------

DELETE FROM sys_menu WHERE id IN (1, 2, 10, 11);

INSERT INTO sys_menu (id, parent_id, menu_type, menu_name, path, component, perms, icon, sort, visible, status, remark)
VALUES
  -- 仪表盘
  (1,  0,  1, '仪表盘',     '/dashboard',     'BasicLayout',                NULL, 'lucide:layout-dashboard', -1, 1, 1, NULL),
  (2,  1,  2, '分析页',     '/analytics',     'dashboard/analytics/index',  NULL, 'lucide:area-chart',        1, 1, 1, NULL),

  -- 系统管理
  (10, 0,  1, '系统管理',   '/system',        'BasicLayout',                NULL, 'lucide:settings',        100, 1, 1, NULL),
  (11, 10, 2, '用户管理',   '/system/user',   'system/user/index',          NULL, 'lucide:users',             1, 1, 1, NULL);
