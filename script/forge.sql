-- =============================================================
-- Forge 插件商城建表脚本
-- 说明：
--   1. 默认字符集 utf8mb4 / 排序规则 utf8mb4_0900_ai_ci（需 MySQL 8.0+）。
--   2. 机器标识符列（plugin_key、code、version、sha256 等）统一用
--      CHARACTER SET ascii COLLATE ascii_bin，区分大小写、避免 Foo/foo 误判。
--   3. 关系/用户行为表（user_plugin、favorite、review、category_rel）改为物理删除，
--      避免「软删除 + 唯一索引」导致无法二次安装/收藏/评价。
--   4. 软删除表的列表索引把等值过滤列（deleted/status）前置，排序列放末尾。
-- =============================================================

CREATE TABLE `forge_plugin` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '插件ID',
  `plugin_key` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '插件唯一标识，对应 plugin.json 的 id',
  `name` varchar(100) NOT NULL COMMENT '插件名称',
  `type` varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'inline' COMMENT '插件类型：inline/view',
  `summary` varchar(255) DEFAULT NULL COMMENT '一句话简介',
  `description` text COMMENT '插件详情，Markdown 或 HTML',
  `keywords` varchar(500) DEFAULT NULL COMMENT '搜索关键词，多个用逗号分隔',
  `icon_file_id` bigint DEFAULT NULL COMMENT '图标文件ID，关联 sys_file',
  `cover_file_id` bigint DEFAULT NULL COMMENT '封面文件ID，关联 sys_file',
  `author_user_id` bigint DEFAULT NULL COMMENT '作者用户ID',
  `author_name` varchar(100) DEFAULT NULL COMMENT '作者展示名',
  `homepage_url` varchar(500) DEFAULT NULL COMMENT '主页地址',
  `repo_url` varchar(500) DEFAULT NULL COMMENT '源码仓库地址',
  `license` varchar(100) DEFAULT NULL COMMENT '许可证',
  `pricing_type` tinyint NOT NULL DEFAULT '1' COMMENT '定价类型：1免费 2付费 3订阅 4外部购买',
  `price` decimal(10,2) DEFAULT NULL COMMENT '展示价格，免费可为空',
  `original_price` decimal(10,2) DEFAULT NULL COMMENT '原价/划线价',
  `currency` varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'CNY' COMMENT '币种',
  `price_text` varchar(100) DEFAULT NULL COMMENT '价格展示文案，如 ¥9/月、联系作者、Pro 版可用',
  `purchase_url` varchar(500) DEFAULT NULL COMMENT '外部购买地址',
  `latest_version_id` bigint DEFAULT NULL COMMENT '最新版本ID',
  `latest_version` varchar(50) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '最新版本号',
  `download_count` bigint NOT NULL DEFAULT '0' COMMENT '下载次数',
  `install_count` bigint NOT NULL DEFAULT '0' COMMENT '安装次数',
  `favorite_count` bigint NOT NULL DEFAULT '0' COMMENT '收藏次数',
  `rating_score` decimal(3,2) NOT NULL DEFAULT '0.00' COMMENT '评分',
  `rating_count` int NOT NULL DEFAULT '0' COMMENT '评分人数',
  `is_featured` tinyint NOT NULL DEFAULT '0' COMMENT '是否推荐：1是 0否',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0草稿 1上架 2下架 3封禁',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `delete_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_forge_plugin_key` (`plugin_key`),
  KEY `idx_forge_plugin_list` (`deleted`, `status`, `is_featured`, `sort_order`),
  KEY `idx_forge_plugin_author` (`deleted`, `author_user_id`, `status`),
  KEY `idx_forge_plugin_pricing` (`deleted`, `pricing_type`, `status`),
  FULLTEXT KEY `ft_forge_plugin_search` (`name`, `summary`, `keywords`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='插件主表';

CREATE TABLE `forge_plugin_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '插件版本ID',
  `plugin_id` bigint NOT NULL COMMENT '插件ID',
  `version` varchar(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '版本号，semver',
  `channel` varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'stable' COMMENT '发布通道：stable/beta/dev',
  `manifest_json` json NOT NULL COMMENT 'plugin.json 快照',
  `package_file_id` bigint DEFAULT NULL COMMENT '插件包文件ID，关联 sys_file',
  `package_url` varchar(1000) DEFAULT NULL COMMENT '插件包外链，可选',
  `package_sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '插件包 SHA256',
  `package_size` bigint NOT NULL DEFAULT '0' COMMENT '包大小，字节',
  `signature` text COMMENT '包签名，可选',
  `min_app_version` varchar(50) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '最低宿主版本',
  `max_app_version` varchar(50) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '最高宿主版本',
  `changelog` text COMMENT '更新日志',
  `download_count` bigint NOT NULL DEFAULT '0' COMMENT '当前版本下载次数',
  `review_status` tinyint NOT NULL DEFAULT '0' COMMENT '审核状态：0待审 1通过 2拒绝',
  `review_remark` varchar(1000) DEFAULT NULL COMMENT '审核备注',
  `published_time` datetime DEFAULT NULL COMMENT '发布时间',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0草稿 1已发布 2已下架 3废弃',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `delete_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_forge_plugin_version` (`plugin_id`, `version`),
  KEY `idx_forge_plugin_version_status` (`plugin_id`, `deleted`, `status`, `review_status`),
  KEY `idx_forge_plugin_version_publish` (`deleted`, `status`, `published_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='插件版本表';

CREATE TABLE `forge_plugin_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '插件权限ID',
  `plugin_id` bigint NOT NULL COMMENT '插件ID',
  `version_id` bigint NOT NULL COMMENT '插件版本ID',
  `permission_code` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '权限编码，如 clipboard.read/network.request',
  `permission_name` varchar(100) NOT NULL COMMENT '权限名称',
  `description` varchar(500) DEFAULT NULL COMMENT '权限用途说明',
  `risk_level` tinyint NOT NULL DEFAULT '1' COMMENT '风险等级：1低 2中 3高',
  `required` tinyint NOT NULL DEFAULT '1' COMMENT '是否必需：1必需 0可选',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_forge_plugin_permission` (`version_id`, `permission_code`),
  KEY `idx_forge_plugin_permission_plugin` (`plugin_id`, `version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='插件版本权限声明表';

CREATE TABLE `forge_plugin_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '插件分类ID',
  `code` varchar(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '分类编码',
  `name` varchar(100) NOT NULL COMMENT '分类名称',
  `description` varchar(500) DEFAULT NULL COMMENT '分类说明',
  `icon_file_id` bigint DEFAULT NULL COMMENT '分类图标文件ID，关联 sys_file',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1启用 0停用',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `delete_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_forge_plugin_category_code` (`code`),
  KEY `idx_forge_plugin_category_sort` (`deleted`, `status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='插件分类表';

-- 关系表：物理删除，避免软删除后无法重新关联
CREATE TABLE `forge_plugin_category_rel` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '插件分类关联ID',
  `plugin_id` bigint NOT NULL COMMENT '插件ID',
  `category_id` bigint NOT NULL COMMENT '分类ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_forge_plugin_category_rel` (`plugin_id`, `category_id`),
  KEY `idx_forge_plugin_category_rel_category` (`category_id`, `plugin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='插件分类关联表';

-- 用户安装表：物理删除（卸载即删行），唯一键安全，可重复安装
CREATE TABLE `forge_user_plugin` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户插件ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `plugin_id` bigint NOT NULL COMMENT '插件ID',
  `version_id` bigint DEFAULT NULL COMMENT '当前安装版本ID',
  `installed_version` varchar(50) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '当前安装版本号',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用：1启用 0禁用',
  `auto_update` tinyint NOT NULL DEFAULT '1' COMMENT '是否自动更新：1是 0否',
  `config_json` json DEFAULT NULL COMMENT '用户配置，可选同步',
  `window_state_json` json DEFAULT NULL COMMENT '窗口状态，可选同步',
  `install_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '安装时间',
  `last_used_time` datetime DEFAULT NULL COMMENT '最近使用时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_forge_user_plugin` (`user_id`, `plugin_id`),
  KEY `idx_forge_user_plugin_user` (`user_id`, `enabled`),
  KEY `idx_forge_user_plugin_plugin` (`plugin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户插件安装表';

-- 评价表：物理删除（删评即删行），status 仅用于审核展示控制，唯一键安全可重新评价
CREATE TABLE `forge_plugin_review` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '插件评价ID',
  `plugin_id` bigint NOT NULL COMMENT '插件ID',
  `version_id` bigint DEFAULT NULL COMMENT '评价时安装的版本ID',
  `user_id` bigint NOT NULL COMMENT '评价用户ID',
  `rating` tinyint NOT NULL COMMENT '评分：1-5',
  `content` varchar(2000) DEFAULT NULL COMMENT '评价内容',
  `reply_content` varchar(2000) DEFAULT NULL COMMENT '作者/管理员回复',
  `reply_by` bigint DEFAULT NULL COMMENT '回复人ID',
  `reply_time` datetime DEFAULT NULL COMMENT '回复时间',
  `like_count` int NOT NULL DEFAULT '0' COMMENT '点赞数',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0待审 1展示 2隐藏 3拒绝',
  `audit_remark` varchar(1000) DEFAULT NULL COMMENT '审核备注',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_forge_plugin_review_user` (`plugin_id`, `user_id`),
  KEY `idx_forge_plugin_review_plugin` (`plugin_id`, `status`, `create_time`),
  KEY `idx_forge_plugin_review_rating` (`plugin_id`, `status`, `rating`),
  KEY `idx_forge_plugin_review_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='插件评价表';

-- 收藏表：物理删除（取消收藏即删行），去掉冗余的 status，唯一键安全可重复收藏
CREATE TABLE `forge_plugin_favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
  `plugin_id` bigint NOT NULL COMMENT '插件ID',
  `user_id` bigint NOT NULL COMMENT '收藏用户ID',
  `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_forge_plugin_favorite` (`plugin_id`, `user_id`),
  KEY `idx_forge_plugin_favorite_user` (`user_id`, `create_time`),
  KEY `idx_forge_plugin_favorite_plugin` (`plugin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='插件收藏表';

-- 下载流水表：按月 RANGE 分区，便于归档/清理。主键含分区列 create_time。
-- 注意：需定期补充新月份分区（如用事件调度或运维脚本维护 pMax 之前的分区）。
CREATE TABLE `forge_plugin_download_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '插件下载日志ID',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID，匿名下载可为空',
  `plugin_id` bigint NOT NULL COMMENT '插件ID',
  `version_id` bigint DEFAULT NULL COMMENT '插件版本ID',
  `client_version` varchar(50) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '客户端版本',
  `client_os` varchar(100) DEFAULT NULL COMMENT '客户端系统',
  `ip` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '客户端IP',
  `user_agent` varchar(1000) DEFAULT NULL COMMENT 'User-Agent',
  `result` tinyint NOT NULL DEFAULT '1' COMMENT '结果：1成功 0失败',
  `error_msg` varchar(2000) DEFAULT NULL COMMENT '失败原因',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`, `create_time`),
  KEY `idx_forge_plugin_download_plugin` (`plugin_id`, `version_id`, `create_time`),
  KEY `idx_forge_plugin_download_user` (`user_id`, `create_time`),
  KEY `idx_forge_plugin_download_result` (`result`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='插件下载日志表'
PARTITION BY RANGE (TO_DAYS(`create_time`)) (
  PARTITION p202606 VALUES LESS THAN (TO_DAYS('2026-07-01')),
  PARTITION p202607 VALUES LESS THAN (TO_DAYS('2026-08-01')),
  PARTITION p202608 VALUES LESS THAN (TO_DAYS('2026-09-01')),
  PARTITION p202609 VALUES LESS THAN (TO_DAYS('2026-10-01')),
  PARTITION p202610 VALUES LESS THAN (TO_DAYS('2026-11-01')),
  PARTITION p202611 VALUES LESS THAN (TO_DAYS('2026-12-01')),
  PARTITION p202612 VALUES LESS THAN (TO_DAYS('2027-01-01')),
  PARTITION pmax VALUES LESS THAN MAXVALUE
);
