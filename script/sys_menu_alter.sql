-- ----------------------------
-- sys_menu 扩展字段（支持前端 web-ele/src/views/system/menu 的完整 CRUD）
-- 与 nebula.sql 中的 sys_menu 基础结构一起使用，可独立执行
--
-- 类型扩展：
--   menu_type 1=目录 catalog / 2=菜单 menu / 3=按钮 button / 4=内嵌 embedded / 5=外链 link
-- 新增列：
--   route_name              路由名（vue-router name），跨菜单唯一
--   active_icon             激活态图标
--   active_path             高亮指定路径（type=embedded/menu）
--   link_url                外链/内嵌地址（type=link 走 link，type=embedded 走 iframeSrc）
--   keep_alive              是否缓存（0/1）
--   affix_tab               是否固定 tab（0/1）
--   hide_in_menu            是否在菜单隐藏（0/1）
--   hide_children_in_menu   是否隐藏子菜单（0/1）
--   hide_in_breadcrumb      是否在面包屑隐藏（0/1）
--   hide_in_tab             是否在多页签隐藏（0/1）
--   badge_type              徽章类型 dot/normal
--   badge                   徽章文本（badge_type=normal 时生效）
--   badge_variants          徽章样式 default/destructive/primary/success/warning
-- ----------------------------

ALTER TABLE `sys_menu`
  ADD COLUMN `route_name`            varchar(100) NULL DEFAULT NULL COMMENT '路由名（vue-router name），跨菜单唯一'      AFTER `menu_name`,
  ADD COLUMN `active_icon`           varchar(100) NULL DEFAULT NULL COMMENT '激活态图标'                                    AFTER `icon`,
  ADD COLUMN `active_path`           varchar(200) NULL DEFAULT NULL COMMENT '高亮指定路径'                                  AFTER `active_icon`,
  ADD COLUMN `link_url`              varchar(500) NULL DEFAULT NULL COMMENT '外链/内嵌地址（type=link 用 link，type=embedded 用 iframeSrc）' AFTER `active_path`,
  ADD COLUMN `keep_alive`            tinyint      NOT NULL DEFAULT 0 COMMENT '是否缓存：1是 0否'                            AFTER `link_url`,
  ADD COLUMN `affix_tab`             tinyint      NOT NULL DEFAULT 0 COMMENT '是否固定 tab：1是 0否'                       AFTER `keep_alive`,
  ADD COLUMN `hide_in_menu`          tinyint      NOT NULL DEFAULT 0 COMMENT '是否在菜单隐藏：1是 0否'                     AFTER `affix_tab`,
  ADD COLUMN `hide_children_in_menu` tinyint      NOT NULL DEFAULT 0 COMMENT '是否隐藏子菜单：1是 0否'                     AFTER `hide_in_menu`,
  ADD COLUMN `hide_in_breadcrumb`    tinyint      NOT NULL DEFAULT 0 COMMENT '是否在面包屑隐藏：1是 0否'                   AFTER `hide_children_in_menu`,
  ADD COLUMN `hide_in_tab`           tinyint      NOT NULL DEFAULT 0 COMMENT '是否在多页签隐藏：1是 0否'                   AFTER `hide_in_breadcrumb`,
  ADD COLUMN `badge_type`            varchar(20)  NULL DEFAULT NULL COMMENT '徽章类型：dot/normal'                          AFTER `hide_in_tab`,
  ADD COLUMN `badge`                 varchar(50)  NULL DEFAULT NULL COMMENT '徽章文本'                                      AFTER `badge_type`,
  ADD COLUMN `badge_variants`        varchar(20)  NULL DEFAULT NULL COMMENT '徽章样式 default/destructive/primary/success/warning' AFTER `badge`,
  ADD UNIQUE INDEX `uk_route_name`   (`route_name`) USING BTREE,
  ADD INDEX        `idx_path`        (`path`)       USING BTREE;

-- 修改 menu_type 注释，登记新增的两个枚举值
ALTER TABLE `sys_menu`
  MODIFY COLUMN `menu_type` tinyint NOT NULL COMMENT '类型：1目录 2菜单 3按钮 4内嵌 5外链';
