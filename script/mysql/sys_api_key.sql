/*
 API Key（SK）管理：建表 + 菜单权限。增量脚本，可重复执行。
*/

SET NAMES utf8mb4;

-- ----------------------------
-- Table structure for sys_api_key
-- 库里是 key 的真相源；运行时校验读的是 Sa-Token 写在 Redis 里的那份，由管理端在增删改时同步。
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_api_key`  (
  `id` bigint(20) NOT NULL COMMENT '主键（雪花算法）',
  `user_id` bigint(20) NOT NULL COMMENT '所属用户ID：凭此 key 发起的请求以该用户身份执行',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '名称（说明这把 key 给谁/做什么用）',
  `api_key` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'key 密文（AES 加密，可逆：Redis 丢数据后靠它把 key 重新灌回去）',
  `key_mask` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'key 掩码（只露首尾），列表展示用',
  `scopes` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '授权范围（取值为权限码，对应接口上 @SaCheckApiKey 的 scope），英文逗号分隔',
  `expire_time` datetime NULL DEFAULT NULL COMMENT '过期时间，NULL=永不过期',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'API Key（SK）表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 菜单与权限（增量，可重复执行）
-- 菜单 15 挂在「系统管理」目录（id=10）下，按钮 1501~1503；
-- 按钮 2305 挂在「文章管理」（id=23）下，是发布接口 POST /blog/admin/articles/publish 的权限码，
-- 同时也是签发 key 时可选的授权范围。
--
-- 这里不给任何角色授权：超级管理员靠通配符 * 天然拥有；其余角色按需在「角色管理」里勾选。
-- 能管理 key 的人可以把自己拥有的权限签进 key，所以 system:api-key:* 只应给可信的角色。
-- 注意：重跑本脚本会清掉下面这些菜单已有的角色授权（先删后插），重跑后需重新勾选。
-- ----------------------------
DELETE FROM `sys_role_menu` WHERE `menu_id` IN (15, 1501, 1502, 1503, 2305, 2204);
DELETE FROM `sys_menu` WHERE `id` IN (15, 1501, 1502, 1503, 2305, 2204);

INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_type`, `menu_name`, `route_name`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`) VALUES (15, 10, 2, 'API 密钥', 'SystemApiKey', '/system/api-key', 'system/api-key/index', 'system:api-key:list', 'lucide:key-round', 5, 1, 1);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_type`, `menu_name`, `perms`, `sort`, `visible`, `status`) VALUES (1501, 15, 3, '新增密钥', 'system:api-key:add', 1, 1, 1);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_type`, `menu_name`, `perms`, `sort`, `visible`, `status`) VALUES (1502, 15, 3, '修改密钥', 'system:api-key:edit', 2, 1, 1);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_type`, `menu_name`, `perms`, `sort`, `visible`, `status`) VALUES (1503, 15, 3, '删除密钥', 'system:api-key:delete', 3, 1, 1);
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_type`, `menu_name`, `perms`, `sort`, `visible`, `status`) VALUES (2305, 23, 3, '接口发布文章', 'blog:article:publish', 5, 1, 1);

-- ----------------------------
-- 补缺：blog:tag:query（标签详情接口 GET /blog/admin/tags/{id} 的权限码）此前在 sys_menu 里没有对应行。
-- blog 服务这次注册了 Sa-Token 拦截器，权限注解开始真正生效；不补这一行，非超级管理员角色永远拿不到该权限。
-- 按钮 2204 挂在「标签管理」（id=22）下，并授给所有已能看到标签管理的角色——保持他们原有的可用范围不变。
-- ----------------------------
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_type`, `menu_name`, `perms`, `sort`, `visible`, `status`) VALUES (2204, 22, 3, '查询标签', 'blog:tag:query', 4, 1, 1);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) SELECT `role_id`, 2204 FROM `sys_role_menu` WHERE `menu_id` = 22;

-- ----------------------------
-- 历史数据修正：迭代链回调（series-append）早先给系列建的「正文」节点是目录类型（node_type=0），
-- 而前台目录树只展示「文章集合」类型（node_type=1）节点下的文章，导致这些文章在目录树里看不到。
-- 代码已改为建 node_type=1；这里把已有的节点原地改过来，文章关联不用动。
-- 只动迭代链产出的系列（chain_id 非空）里、没有子节点的「正文」节点，不碰手工建的目录。
-- ----------------------------
UPDATE `blog_series_catalog` c
  JOIN `blog_series` s ON s.`id` = c.`series_id`
   SET c.`node_type` = 1
 WHERE s.`chain_id` IS NOT NULL
   AND c.`node_type` = 0
   AND c.`title` = '正文'
   AND c.`children_count` = 0;
