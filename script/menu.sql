DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu`  (
                             `id` bigint NOT NULL COMMENT '菜单ID',
                             `parent_id` bigint NOT NULL DEFAULT 0 COMMENT '父菜单ID，0表示根节点',
                             `menu_type` tinyint NOT NULL COMMENT '类型：1目录 2菜单 3按钮 4内嵌 5外链',
                             `menu_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '名称',
                             `route_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '路由名（vue-router name），跨菜单唯一',
                             `path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '前端路由路径（如 /user）',
                             `component` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '前端组件路径（如 system/user/index）',
                             `perms` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '权限标识（如 user:list, user:delete）',
                             `icon` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '图标',
                             `active_icon` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '激活态图标',
                             `active_path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '高亮指定路径',
                             `link_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '外链/内嵌地址（type=link 用 link，type=embedded 用 iframeSrc）',
                             `keep_alive` tinyint NOT NULL DEFAULT 0 COMMENT '是否缓存：1是 0否',
                             `affix_tab` tinyint NOT NULL DEFAULT 0 COMMENT '是否固定 tab：1是 0否',
                             `hide_in_menu` tinyint NOT NULL DEFAULT 0 COMMENT '是否在菜单隐藏：1是 0否',
                             `hide_children_in_menu` tinyint NOT NULL DEFAULT 0 COMMENT '是否隐藏子菜单：1是 0否',
                             `hide_in_breadcrumb` tinyint NOT NULL DEFAULT 0 COMMENT '是否在面包屑隐藏：1是 0否',
                             `hide_in_tab` tinyint NOT NULL DEFAULT 0 COMMENT '是否在多页签隐藏：1是 0否',
                             `badge_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '徽章类型：dot/normal',
                             `badge` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '徽章文本',
                             `badge_variants` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '徽章样式 default/destructive/primary/success/warning',
                             `sort` int NOT NULL DEFAULT 0 COMMENT '排序（越小越靠前）',
                             `visible` tinyint NOT NULL DEFAULT 1 COMMENT '是否显示：1是 0否',
                             `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1正常 0禁用',
                             `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
                             `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                             PRIMARY KEY (`id`) USING BTREE,
                             UNIQUE INDEX `uk_route_name`(`route_name` ASC) USING BTREE,
                             INDEX `idx_parent_id`(`parent_id` ASC) USING BTREE,
                             INDEX `idx_sort`(`sort` ASC) USING BTREE,
                             INDEX `idx_path`(`path` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '菜单与权限表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_menu
-- ----------------------------
INSERT INTO `sys_menu` VALUES (1, 0, 1, '仪表盘', NULL, '/dashboard', 'BasicLayout', NULL, 'lucide:layout-dashboard', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, -1, 1, 1, NULL, '2026-05-07 09:04:28', '2026-05-07 09:04:28');
INSERT INTO `sys_menu` VALUES (2, 1, 2, '分析页', NULL, '/analytics', 'dashboard/analytics/index', NULL, 'lucide:area-chart', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-07 09:04:28', '2026-05-07 09:04:28');
INSERT INTO `sys_menu` VALUES (10, 0, 1, '系统管理', NULL, '/system', 'BasicLayout', NULL, 'lucide:settings', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 100, 1, 1, NULL, '2026-05-07 09:04:28', '2026-05-07 09:04:28');
INSERT INTO `sys_menu` VALUES (11, 10, 2, '用户管理', 'SystemUser', '/system/user', 'system/user/index', 'system:user:list', 'lucide:users', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-07 09:04:28', '2026-05-07 09:04:28');
INSERT INTO `sys_menu` VALUES (12, 10, 2, '菜单管理', 'SystemMenu', '/system/menu', 'system/menu/index', 'system:menu:list', 'lucide:menu', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, '菜单管理', '2026-05-07 11:47:51', '2026-05-13 06:49:49');
INSERT INTO `sys_menu` VALUES (13, 10, 2, '角色管理', 'SystemRole', '/system/role', 'system/role/index', 'system:role:list', 'lucide:user-cog', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, '角色管理', '2026-05-07 11:47:51', '2026-05-13 06:49:49');
INSERT INTO `sys_menu` VALUES (14, 10, 2, '文件管理', 'SystemFile', '/system/file', 'system/file/index', 'system:file:list', 'lucide:files', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, '文件管理', '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (20, 0, 1, '博客管理', 'Blog', '/blog', 'BasicLayout', NULL, 'lucide:notebook-pen', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 200, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (21, 20, 2, '分类管理', 'BlogCategory', '/blog/category', 'blog/category/index', 'blog:category:list', 'lucide:folder-tree', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (22, 20, 2, '标签管理', 'BlogTag', '/blog/tag', 'blog/tag/index', 'blog:tag:list', 'lucide:tag', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (23, 20, 2, '文章管理', 'BlogArticle', '/blog/article', 'blog/article/index', 'blog:article:list', 'lucide:file-text', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (24, 20, 2, '随笔管理', 'BlogEssay', '/blog/essay', 'blog/article/index', 'blog:article:list', 'lucide:pen-line', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-26 00:00:00', '2026-05-26 00:00:00');
INSERT INTO `sys_menu` VALUES (25, 20, 2, '系列管理', 'BlogSeries', '/blog/series', 'blog/series/index', 'blog:series:list', 'lucide:layers', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (26, 20, 2, '系列目录', 'BlogSeriesCatalog', '/blog/series/:id/catalog', 'blog/series/catalog', 'blog:series:query', 'lucide:list-tree', NULL, '/blog/series', NULL, 0, 0, 1, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (27, 20, 2, '目的地管理', 'BlogTravelDestination', '/blog/travel/destination', 'blog/travel/destination/index', 'blog:travel:list', 'lucide:map-pin', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 5, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (28, 20, 2, '游记管理', 'BlogTravelTrip', '/blog/travel/trip', 'blog/travel/trip/index', 'blog:travel:list', 'lucide:map', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 6, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (29, 20, 2, '游记详情', 'BlogTravelTripDetail', '/blog/travel/trip/:id/detail', 'blog/travel/trip/detail', 'blog:travel:query', 'lucide:list-tree', NULL, '/blog/travel/trip', NULL, 0, 0, 1, 0, 0, 0, NULL, NULL, NULL, 7, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
-- 文章导入任务（异步进度查看，复用 blog:article:list / blog:article:query 权限，无需新增按钮权限）
INSERT INTO `sys_menu` VALUES (230, 20, 2, '文章导入任务', 'BlogImportTask', '/blog/import-task', 'blog/import-task/index', 'blog:article:list', 'lucide:download', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 8, 1, 1, '文章 Markdown 批量导入任务进度与明细', '2026-06-11 00:00:00', '2026-06-11 00:00:00');
INSERT INTO `sys_menu` VALUES (30, 0, 1, 'AI中转管理', NULL, '/ai-relay', 'BasicLayout', NULL, 'lucide:plug-zap', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 300, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (31, 30, 2, '服务商管理', 'AiRelayProvider', '/ai-relay/provider', 'ai-relay/provider/index', 'blog:ai-relay:provider:list', 'lucide:server', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (32, 30, 2, '套餐管理', 'AiRelayPackage', '/ai-relay/package', 'ai-relay/package/index', 'blog:ai-relay:package:list', 'lucide:package', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (33, 30, 2, '套餐类型', 'AiRelayPackageType', '/ai-relay/package-type', 'ai-relay/package-type/index', 'blog:ai-relay:package-type:list', 'lucide:list-checks', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (34, 30, 2, 'AI模型', 'AiRelayModel', '/ai-relay/model', 'ai-relay/model/index', 'blog:ai-relay:model:list', 'lucide:bot', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (35, 30, 2, '支付方式', 'AiRelayPaymentMethod', '/ai-relay/payment-method', 'ai-relay/payment-method/index', 'blog:ai-relay:payment-method:list', 'lucide:credit-card', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 5, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (36, 30, 2, '推荐与测评', 'AiRelayRecommend', '/ai-relay/recommend', 'ai-relay/recommend/index', 'blog:ai-relay:recommend:list', 'lucide:star', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 6, 1, 1, NULL, '2026-05-29 00:00:00', '2026-05-29 00:00:00');
INSERT INTO `sys_menu` VALUES (37, 30, 2, '充值记录', 'AiRelayRecharge', '/ai-relay/recharge', 'ai-relay/recharge/index', 'blog:ai-relay:recharge:list', 'lucide:wallet', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 7, 1, 1, NULL, '2026-05-29 00:00:00', '2026-05-29 00:00:00');
INSERT INTO `sys_menu` VALUES (1101, 11, 3, '新增用户', NULL, NULL, NULL, 'system:user:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-09 15:26:33', '2026-05-09 15:26:33');
INSERT INTO `sys_menu` VALUES (1102, 11, 3, '修改用户', NULL, NULL, NULL, 'system:user:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-09 15:26:33', '2026-05-09 15:26:33');
INSERT INTO `sys_menu` VALUES (1103, 11, 3, '删除用户', NULL, NULL, NULL, 'system:user:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-09 15:26:33', '2026-05-09 15:26:33');
INSERT INTO `sys_menu` VALUES (1201, 12, 3, '新增菜单', NULL, NULL, NULL, 'system:menu:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-09 15:26:33', '2026-05-09 15:26:33');
INSERT INTO `sys_menu` VALUES (1202, 12, 3, '修改菜单', NULL, NULL, NULL, 'system:menu:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-09 15:26:33', '2026-05-09 15:26:33');
INSERT INTO `sys_menu` VALUES (1203, 12, 3, '删除菜单', NULL, NULL, NULL, 'system:menu:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-09 15:26:33', '2026-05-09 15:26:33');
INSERT INTO `sys_menu` VALUES (1301, 13, 3, '新增角色', NULL, NULL, NULL, 'system:role:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-09 15:26:33', '2026-05-09 15:26:33');
INSERT INTO `sys_menu` VALUES (1302, 13, 3, '修改角色', NULL, NULL, NULL, 'system:role:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-09 15:26:33', '2026-05-09 15:26:33');
INSERT INTO `sys_menu` VALUES (1303, 13, 3, '删除角色', NULL, NULL, NULL, 'system:role:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-09 15:26:33', '2026-05-09 15:26:33');
INSERT INTO `sys_menu` VALUES (1401, 14, 3, '上传文件', NULL, NULL, NULL, 'system:file:upload', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (1402, 14, 3, '查询文件', NULL, NULL, NULL, 'system:file:list', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (1403, 14, 3, '编辑文件', NULL, NULL, NULL, 'system:file:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (1404, 14, 3, '删除文件', NULL, NULL, NULL, 'system:file:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (2101, 21, 3, '新增分类', NULL, NULL, NULL, 'blog:category:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (2102, 21, 3, '修改分类', NULL, NULL, NULL, 'blog:category:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (2103, 21, 3, '删除分类', NULL, NULL, NULL, 'blog:category:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (2201, 22, 3, '新增标签', NULL, NULL, NULL, 'blog:tag:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (2202, 22, 3, '修改标签', NULL, NULL, NULL, 'blog:tag:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (2203, 22, 3, '删除标签', NULL, NULL, NULL, 'blog:tag:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (2301, 23, 3, '新增文章', NULL, NULL, NULL, 'blog:article:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (2302, 23, 3, '修改文章', NULL, NULL, NULL, 'blog:article:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (2303, 23, 3, '删除文章', NULL, NULL, NULL, 'blog:article:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (2304, 23, 3, '查询文章', NULL, NULL, NULL, 'blog:article:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (2501, 25, 3, '新增系列', NULL, NULL, NULL, 'blog:series:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (2502, 25, 3, '修改系列', NULL, NULL, NULL, 'blog:series:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (2503, 25, 3, '删除系列', NULL, NULL, NULL, 'blog:series:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (2504, 25, 3, '查询系列', NULL, NULL, NULL, 'blog:series:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (2701, 27, 3, '新增目的地', NULL, NULL, NULL, 'blog:travel:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (2702, 27, 3, '修改目的地', NULL, NULL, NULL, 'blog:travel:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (2703, 27, 3, '删除目的地', NULL, NULL, NULL, 'blog:travel:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (2704, 27, 3, '查询目的地', NULL, NULL, NULL, 'blog:travel:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (2801, 28, 3, '新增游记', NULL, NULL, NULL, 'blog:travel:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (2802, 28, 3, '修改游记', NULL, NULL, NULL, 'blog:travel:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (2803, 28, 3, '删除游记', NULL, NULL, NULL, 'blog:travel:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (2804, 28, 3, '查询游记', NULL, NULL, NULL, 'blog:travel:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (3101, 31, 3, '查询服务商', NULL, NULL, NULL, 'blog:ai-relay:provider:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3102, 31, 3, '新增服务商', NULL, NULL, NULL, 'blog:ai-relay:provider:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3103, 31, 3, '修改服务商', NULL, NULL, NULL, 'blog:ai-relay:provider:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3104, 31, 3, '删除服务商', NULL, NULL, NULL, 'blog:ai-relay:provider:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3201, 32, 3, '查询套餐', NULL, NULL, NULL, 'blog:ai-relay:package:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3202, 32, 3, '新增套餐', NULL, NULL, NULL, 'blog:ai-relay:package:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3203, 32, 3, '修改套餐', NULL, NULL, NULL, 'blog:ai-relay:package:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3204, 32, 3, '删除套餐', NULL, NULL, NULL, 'blog:ai-relay:package:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3301, 33, 3, '查询套餐类型', NULL, NULL, NULL, 'blog:ai-relay:package-type:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3302, 33, 3, '新增套餐类型', NULL, NULL, NULL, 'blog:ai-relay:package-type:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3303, 33, 3, '修改套餐类型', NULL, NULL, NULL, 'blog:ai-relay:package-type:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3304, 33, 3, '删除套餐类型', NULL, NULL, NULL, 'blog:ai-relay:package-type:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3401, 34, 3, '查询模型', NULL, NULL, NULL, 'blog:ai-relay:model:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3402, 34, 3, '新增模型', NULL, NULL, NULL, 'blog:ai-relay:model:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3403, 34, 3, '修改模型', NULL, NULL, NULL, 'blog:ai-relay:model:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3404, 34, 3, '删除模型', NULL, NULL, NULL, 'blog:ai-relay:model:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3501, 35, 3, '查询支付方式', NULL, NULL, NULL, 'blog:ai-relay:payment-method:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3502, 35, 3, '新增支付方式', NULL, NULL, NULL, 'blog:ai-relay:payment-method:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3503, 35, 3, '修改支付方式', NULL, NULL, NULL, 'blog:ai-relay:payment-method:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3504, 35, 3, '删除支付方式', NULL, NULL, NULL, 'blog:ai-relay:payment-method:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (3601, 36, 3, '查询推荐', NULL, NULL, NULL, 'blog:ai-relay:recommend:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-29 00:00:00', '2026-05-29 00:00:00');
INSERT INTO `sys_menu` VALUES (3602, 36, 3, '新增推荐', NULL, NULL, NULL, 'blog:ai-relay:recommend:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-29 00:00:00', '2026-05-29 00:00:00');
INSERT INTO `sys_menu` VALUES (3603, 36, 3, '修改推荐', NULL, NULL, NULL, 'blog:ai-relay:recommend:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-29 00:00:00', '2026-05-29 00:00:00');
INSERT INTO `sys_menu` VALUES (3604, 36, 3, '删除推荐', NULL, NULL, NULL, 'blog:ai-relay:recommend:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-29 00:00:00', '2026-05-29 00:00:00');
INSERT INTO `sys_menu` VALUES (3701, 37, 3, '查询充值记录', NULL, NULL, NULL, 'blog:ai-relay:recharge:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-29 00:00:00', '2026-05-29 00:00:00');
INSERT INTO `sys_menu` VALUES (3702, 37, 3, '新增充值记录', NULL, NULL, NULL, 'blog:ai-relay:recharge:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-29 00:00:00', '2026-05-29 00:00:00');
INSERT INTO `sys_menu` VALUES (3703, 37, 3, '修改充值记录', NULL, NULL, NULL, 'blog:ai-relay:recharge:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-29 00:00:00', '2026-05-29 00:00:00');
INSERT INTO `sys_menu` VALUES (3704, 37, 3, '删除充值记录', NULL, NULL, NULL, 'blog:ai-relay:recharge:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-29 00:00:00', '2026-05-29 00:00:00');

-- ----------------------------
-- 个人空间（书签）模块
-- ----------------------------
INSERT INTO `sys_menu` VALUES (40, 0, 1, '个人空间', 'Space', '/space', 'BasicLayout', NULL, 'lucide:bookmark', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 250, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (41, 40, 2, '书签管理', 'SpaceBookmark', '/space/bookmark', 'space/bookmark/index', 'space:bookmark:list', 'lucide:bookmark-plus', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (42, 40, 2, '目录管理', 'SpaceFolder', '/space/folder', 'space/folder/index', 'space:folder:list', 'lucide:folder-tree', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (43, 40, 2, '标签管理', 'SpaceTag', '/space/tag', 'space/tag/index', 'space:tag:list', 'lucide:tags', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (44, 40, 2, '导入任务', 'SpaceImportTask', '/space/import-task', 'space/import-task/index', 'space:bookmark-import:list', 'lucide:download', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (45, 40, 2, '导出任务', 'SpaceExportTask', '/space/export-task', 'space/export-task/index', 'space:bookmark-export:list', 'lucide:upload', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 5, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');

-- 书签管理按钮
INSERT INTO `sys_menu` VALUES (4101, 41, 3, '查询书签', NULL, NULL, NULL, 'space:bookmark:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4102, 41, 3, '新增书签', NULL, NULL, NULL, 'space:bookmark:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4103, 41, 3, '修改书签', NULL, NULL, NULL, 'space:bookmark:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4104, 41, 3, '删除书签', NULL, NULL, NULL, 'space:bookmark:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');

-- 目录管理按钮
INSERT INTO `sys_menu` VALUES (4201, 42, 3, '查询目录', NULL, NULL, NULL, 'space:folder:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4202, 42, 3, '新增目录', NULL, NULL, NULL, 'space:folder:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4203, 42, 3, '修改目录', NULL, NULL, NULL, 'space:folder:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4204, 42, 3, '删除目录', NULL, NULL, NULL, 'space:folder:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');

-- 标签管理按钮
INSERT INTO `sys_menu` VALUES (4301, 43, 3, '查询标签', NULL, NULL, NULL, 'space:tag:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4302, 43, 3, '新增标签', NULL, NULL, NULL, 'space:tag:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4303, 43, 3, '修改标签', NULL, NULL, NULL, 'space:tag:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4304, 43, 3, '删除标签', NULL, NULL, NULL, 'space:tag:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');

-- 导入任务按钮（仅查询和取消）
INSERT INTO `sys_menu` VALUES (4401, 44, 3, '查询导入任务', NULL, NULL, NULL, 'space:bookmark-import:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4402, 44, 3, '取消导入任务', NULL, NULL, NULL, 'space:bookmark-import:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');

-- 导出任务按钮（仅查询和取消）
INSERT INTO `sys_menu` VALUES (4501, 45, 3, '查询导出任务', NULL, NULL, NULL, 'space:bookmark-export:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4502, 45, 3, '取消导出任务', NULL, NULL, NULL, 'space:bookmark-export:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
