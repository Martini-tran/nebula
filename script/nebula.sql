/*
 Navicat Premium Dump SQL

 Source Server         : xiangqian-mysql
 Source Server Type    : MySQL
 Source Server Version : 80046 (8.0.46)
 Source Host           : 140.143.222.164:3307
 Source Schema         : nebula

 Target Server Type    : MySQL
 Target Server Version : 80046 (8.0.46)
 File Encoding         : 65001

 Date: 08/06/2026 15:14:44
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for ai_relay_model
-- ----------------------------
DROP TABLE IF EXISTS `ai_relay_model`;
CREATE TABLE `ai_relay_model`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '模型ID',
  `code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模型编码（如 gpt-4o-mini / claude-3-5-sonnet）',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模型名称',
  `model_vendor` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '模型厂商（OpenAI/Anthropic/Google等）',
  `model_type` tinyint NOT NULL DEFAULT 1 COMMENT '模型类型（1文本 2图像 3音频 4多模态 5Embedding）',
  `description` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '模型说明',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '展示排序',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态（1正常 0停用）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_code`(`code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 18 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI模型配置' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_model
-- ----------------------------

-- ----------------------------
-- Table structure for ai_relay_package_model
-- ----------------------------
DROP TABLE IF EXISTS `ai_relay_package_model`;
CREATE TABLE `ai_relay_package_model`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '套餐模型ID',
  `package_id` bigint NOT NULL COMMENT '套餐ID（ai_relay_provider_package.id）',
  `model_id` bigint NOT NULL COMMENT '模型ID（ai_relay_model.id）',
  `provider_model_code` varchar(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '服务商侧模型编码，不填则默认使用模型编码',
  `consume_multiplier` decimal(8, 4) NOT NULL DEFAULT 1.0000 COMMENT '消耗倍率，如1.5表示消耗额度*1.5',
  `min_charge_amount` decimal(18, 6) NULL DEFAULT NULL COMMENT '最低扣费额度',
  `max_context_tokens` int NULL DEFAULT NULL COMMENT '最大上下文Token数',
  `input_price_per_million_tokens` decimal(12, 4) NULL DEFAULT NULL COMMENT '输入Token单价（每百万Token），币种沿用套餐currency，跨套餐比价用',
  `output_price_per_million_tokens` decimal(12, 4) NULL DEFAULT NULL COMMENT '输出Token单价（每百万Token），币种沿用套餐currency，跨套餐比价用',
  `is_default` tinyint NOT NULL DEFAULT 0 COMMENT '是否默认模型（1是 0否）',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '展示排序',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态（1正常 0停用）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_package_model`(`package_id` ASC, `model_id` ASC) USING BTREE,
  INDEX `idx_package_id`(`package_id` ASC) USING BTREE,
  INDEX `idx_model_id`(`model_id` ASC) USING BTREE,
  INDEX `idx_package_status_sort`(`package_id` ASC, `status` ASC, `sort_order` ASC) USING BTREE,
  INDEX `idx_model_input_price`(`model_id` ASC, `status` ASC, `input_price_per_million_tokens` ASC) USING BTREE,
  INDEX `idx_model_output_price`(`model_id` ASC, `status` ASC, `output_price_per_million_tokens` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转套餐支持模型及消耗倍率' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_package_model
-- ----------------------------

-- ----------------------------
-- Table structure for ai_relay_package_type
-- ----------------------------
DROP TABLE IF EXISTS `ai_relay_package_type`;
CREATE TABLE `ai_relay_package_type`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '套餐类型ID',
  `code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '套餐类型编码（day/week/month/usage等）',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '套餐类型名称（天卡/周卡/月卡/按量）',
  `billing_mode` tinyint NOT NULL COMMENT '计费模式（1固定周期 2按量计费）',
  `duration_value` int NULL DEFAULT NULL COMMENT '套餐周期数值，如1、7、30，按量计费可为空',
  `duration_unit` tinyint NULL DEFAULT NULL COMMENT '周期单位（1天 2周 3月 4年），按量计费可为空',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '类型说明',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '展示排序',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态（1正常 0停用）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_code`(`code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转套餐类型配置' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_package_type
-- ----------------------------

-- ----------------------------
-- Table structure for ai_relay_payment_method
-- ----------------------------
DROP TABLE IF EXISTS `ai_relay_payment_method`;
CREATE TABLE `ai_relay_payment_method`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '支付方式ID',
  `code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '支付方式编码（alipay/wechat/paypal/usdt等）',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '支付方式名称（支付宝/微信/PayPal/USDT等）',
  `icon_file_id` bigint NULL DEFAULT NULL COMMENT '支付方式图标文件ID（sys_file）',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '支付方式说明',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '展示排序',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态（1正常 0停用）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_code`(`code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转支付方式配置' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_payment_method
-- ----------------------------

-- ----------------------------
-- Table structure for ai_relay_provider
-- ----------------------------
DROP TABLE IF EXISTS `ai_relay_provider`;
CREATE TABLE `ai_relay_provider`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '服务商ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '服务商名称（如 OpenRouter）',
  `website_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '官网地址',
  `logo_file_id` bigint NULL DEFAULT NULL COMMENT 'Logo文件ID（sys_file）',
  `description` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '服务商简介',
  `recommend_score` decimal(5, 2) NOT NULL DEFAULT 0.00 COMMENT '综合推荐分（核心排序依据）',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '展示排序',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态（1正常 0下线）',
  `last_sync_time` datetime NULL DEFAULT NULL COMMENT '最近一次同步时间（运营手动同步价格/模型时刷新）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（即收录时间）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_name`(`name` ASC) USING BTREE,
  INDEX `idx_last_sync_time`(`last_sync_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转服务商' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_provider
-- ----------------------------

-- ----------------------------
-- Table structure for ai_relay_provider_advantage
-- ----------------------------
DROP TABLE IF EXISTS `ai_relay_provider_advantage`;
CREATE TABLE `ai_relay_provider_advantage`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '优势ID',
  `provider_id` bigint NOT NULL COMMENT '服务商ID（ai_relay_provider.id）',
  `title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '优势标题',
  `content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '优势说明',
  `advantage_type` tinyint NOT NULL DEFAULT 1 COMMENT '优势类型（1普通优势 2核心优势 3风险提示）',
  `icon_file_id` bigint NULL DEFAULT NULL COMMENT '优势图标文件ID（sys_file）',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '展示排序',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态（1正常 0停用）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_provider_id`(`provider_id` ASC) USING BTREE,
  INDEX `idx_provider_status_sort`(`provider_id` ASC, `status` ASC, `sort_order` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 25 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转服务商优势' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_provider_advantage
-- ----------------------------

-- ----------------------------
-- Table structure for ai_relay_provider_package
-- ----------------------------
DROP TABLE IF EXISTS `ai_relay_provider_package`;
CREATE TABLE `ai_relay_provider_package`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '套餐ID',
  `provider_id` bigint NOT NULL COMMENT '服务商ID（ai_relay_provider.id）',
  `package_type_id` bigint NOT NULL COMMENT '套餐类型ID（ai_relay_package_type.id）',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '套餐名称',
  `price` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '套餐价格',
  `original_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '原价/划线价',
  `currency` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'CNY' COMMENT '币种（CNY/USD等）',
  `is_recommended` tinyint NOT NULL DEFAULT 0 COMMENT '是否推荐（1是 0否）',
  `recommend_score` decimal(5, 2) NOT NULL DEFAULT 0.00 COMMENT '套餐推荐分',
  `description` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '套餐说明',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '展示排序',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态（1正常 0下线）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_provider_id`(`provider_id` ASC) USING BTREE,
  INDEX `idx_package_type_id`(`package_type_id` ASC) USING BTREE,
  INDEX `idx_provider_status_sort`(`provider_id` ASC, `status` ASC, `sort_order` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 25 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转服务商套餐' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_provider_package
-- ----------------------------

-- ----------------------------
-- Table structure for ai_relay_provider_package_limit
-- ----------------------------
DROP TABLE IF EXISTS `ai_relay_provider_package_limit`;
CREATE TABLE `ai_relay_provider_package_limit`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '套餐限制ID',
  `package_id` bigint NOT NULL COMMENT '套餐ID（ai_relay_provider_package.id）',
  `limit_type` tinyint NOT NULL COMMENT '限制类型（1总额度 2每日额度 3每周额度 4每月额度 5单次额度）',
  `quota_amount` decimal(18, 6) NOT NULL COMMENT '额度数量',
  `quota_unit` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '额度单位（token/request/credit等）',
  `reset_cycle` tinyint NOT NULL DEFAULT 0 COMMENT '重置周期（0不重置 1每日 2每周 3每月 4套餐周期）',
  `over_limit_strategy` tinyint NOT NULL DEFAULT 1 COMMENT '超限策略（1禁止使用 2按量计费 3限速）',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '限制说明',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态（1正常 0停用）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_package_id`(`package_id` ASC) USING BTREE,
  INDEX `idx_package_limit_type`(`package_id` ASC, `limit_type` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转服务商套餐额度限制' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_provider_package_limit
-- ----------------------------

-- ----------------------------
-- Table structure for ai_relay_provider_payment_method
-- ----------------------------
DROP TABLE IF EXISTS `ai_relay_provider_payment_method`;
CREATE TABLE `ai_relay_provider_payment_method`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `provider_id` bigint NOT NULL COMMENT '服务商ID（ai_relay_provider.id）',
  `payment_method_id` bigint NOT NULL COMMENT '支付方式ID（ai_relay_payment_method.id）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注说明',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '展示排序',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态（1正常 0停用）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_provider_payment`(`provider_id` ASC, `payment_method_id` ASC) USING BTREE,
  INDEX `idx_provider_id`(`provider_id` ASC) USING BTREE,
  INDEX `idx_payment_method_id`(`payment_method_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 12 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转服务商支持支付方式' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_provider_payment_method
-- ----------------------------

-- ----------------------------
-- Table structure for ai_relay_provider_recharge
-- ----------------------------
DROP TABLE IF EXISTS `ai_relay_provider_recharge`;
CREATE TABLE `ai_relay_provider_recharge`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '充值记录ID',
  `provider_id` bigint NOT NULL COMMENT '服务商ID（ai_relay_provider.id）',
  `package_id` bigint NULL DEFAULT NULL COMMENT '套餐ID（ai_relay_provider_package.id），按量计费/直充可空',
  `amount` decimal(10, 2) NOT NULL COMMENT '充值金额',
  `currency` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'CNY' COMMENT '币种（CNY/USD等）',
  `exchange_rate` decimal(10, 4) NULL DEFAULT NULL COMMENT '汇率（非CNY时折算汇率）',
  `cny_amount` decimal(10, 2) NULL DEFAULT NULL COMMENT '折合人民币金额',
  `payment_method_id` bigint NULL DEFAULT NULL COMMENT '支付方式ID（ai_relay_payment_method.id）',
  `recharge_time` datetime NOT NULL COMMENT '充值时间',
  `order_no` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '订单号/交易流水号',
  `voucher_file_id` bigint NULL DEFAULT NULL COMMENT '充值凭证文件ID（sys_file）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注说明',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态（1正常 0作废）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_provider_id`(`provider_id` ASC) USING BTREE,
  INDEX `idx_package_id`(`package_id` ASC) USING BTREE,
  INDEX `idx_recharge_time`(`recharge_time` ASC) USING BTREE,
  INDEX `idx_provider_recharge_time`(`provider_id` ASC, `recharge_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转服务商个人充值记录（用于佐证推荐真实性）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_provider_recharge
-- ----------------------------

-- ----------------------------
-- Table structure for ai_relay_provider_recommend
-- ----------------------------
DROP TABLE IF EXISTS `ai_relay_provider_recommend`;
CREATE TABLE `ai_relay_provider_recommend`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '推荐ID',
  `provider_id` bigint NOT NULL COMMENT '服务商ID（ai_relay_provider.id）',
  `recommend_reason` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '推荐原因（精简一句话/摘要，用于列表展示）',
  `review_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '完整测评内容（支持Markdown）',
  `review_score` decimal(3, 1) NULL DEFAULT NULL COMMENT '个人测评评分（0-10分）',
  `pros` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '优点（多个用换行/分号分隔）',
  `cons` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '缺点（多个用换行/分号分隔）',
  `use_scenario` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '推荐使用场景',
  `first_use_time` datetime NULL DEFAULT NULL COMMENT '首次使用时间',
  `review_time` datetime NULL DEFAULT NULL COMMENT '测评时间',
  `recommend_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '推荐时间',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '展示排序',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态（1正常 0下线）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_provider_id`(`provider_id` ASC) USING BTREE,
  INDEX `idx_status_sort`(`status` ASC, `sort_order` ASC) USING BTREE,
  INDEX `idx_recommend_time`(`recommend_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转服务商个人推荐及测评（推荐均为本人实际使用并充值）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_provider_recommend
-- ----------------------------

-- ----------------------------
-- Table structure for blog_category
-- ----------------------------
DROP TABLE IF EXISTS `blog_category`;
CREATE TABLE `blog_category`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '分类名称',
  `slug` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '分类URL标识',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '分类描述',
  `parent_id` bigint NULL DEFAULT NULL COMMENT '父分类ID（用于层级分类）',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序序号（升序）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_blog_category_slug`(`slug` ASC) USING BTREE,
  UNIQUE INDEX `uk_blog_category_name`(`name` ASC) USING BTREE,
  INDEX `idx_blog_category_parent_id`(`parent_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 39 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客分类表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of blog_category
-- ----------------------------

-- ----------------------------
-- Table structure for blog_content_version
-- ----------------------------
DROP TABLE IF EXISTS `blog_content_version`;
CREATE TABLE `blog_content_version`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '版本ID',
  `post_id` bigint NOT NULL COMMENT '文章ID',
  `version_no` int NOT NULL COMMENT '版本号',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '版本标题',
  `summary` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '版本摘要',
  `content_file_id` bigint NOT NULL COMMENT '版本正文文件ID',
  `cover_file_id` bigint NULL DEFAULT NULL COMMENT '版本封面文件ID',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '该版本的状态快照',
  `visibility` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '该版本的可见性快照',
  `change_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'manual' COMMENT '变更类型：manual(手动)/auto(自动)',
  `change_note` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '变更备注',
  `creator_id` bigint NULL DEFAULT NULL COMMENT '创建者用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_blog_version_post_version`(`post_id` ASC, `version_no` ASC) USING BTREE,
  INDEX `idx_blog_version_post_id`(`post_id` ASC) USING BTREE,
  INDEX `idx_blog_version_file_id`(`content_file_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 22 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客内容版本表（完整快照）' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of blog_content_version
-- ----------------------------

-- ----------------------------
-- Table structure for blog_file_asset
-- ----------------------------
DROP TABLE IF EXISTS `blog_file_asset`;
CREATE TABLE `blog_file_asset`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '文件ID',
  `storage_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '存储类型：local(本地)/oss(对象存储)',
  `bucket` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'OSS bucket名称（本地存储时可为空）',
  `object_key` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '存储路径，如 posts/2026/04/xxx.md',
  `url` varchar(750) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '访问URL',
  `filename` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '原始文件名',
  `extension` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件扩展名（如 .md, .jpg）',
  `mime_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'MIME类型（如 text/markdown, image/jpeg）',
  `size_bytes` bigint NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
  `hash_sha256` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件SHA256哈希值（用于去重/校验）',
  `file_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'other' COMMENT '文件用途：markdown/image/attachment/cover/other',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_blog_file_storage_key`(`storage_type` ASC, `object_key`(100) ASC) USING BTREE,
  INDEX `idx_blog_file_hash`(`hash_sha256` ASC) USING BTREE,
  INDEX `idx_blog_file_type`(`file_type` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 31 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客文件资源表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of blog_file_asset
-- ----------------------------

-- ----------------------------
-- Table structure for blog_post
-- ----------------------------
DROP TABLE IF EXISTS `blog_post`;
CREATE TABLE `blog_post`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '文章ID',
  `author_id` bigint NOT NULL COMMENT '作者用户ID',
  `post_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'article' COMMENT '内容类型：article(文章)/essay(随笔)',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文章标题',
  `slug` varchar(220) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'URL唯一标识',
  `summary` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '摘要',
  `content_file_id` bigint NOT NULL COMMENT 'Markdown正文文件ID（关联blog_file_asset）',
  `cover_file_id` bigint NULL DEFAULT NULL COMMENT '封面文件ID（关联blog_file_asset）',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'draft' COMMENT '状态：draft(草稿)/published(已发布)/archived(已归档)',
  `visibility` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'public' COMMENT '可见性：public(公开)/private(私有)',
  `source_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'manual' COMMENT '来源：manual(手动)/ai(AI生成)/import(导入)',
  `is_original` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否原创：1-是，0-否',
  `view_count` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '浏览次数',
  `like_count` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '点赞次数',
  `published_at` datetime NULL DEFAULT NULL COMMENT '发布时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_blog_post_slug`(`slug` ASC) USING BTREE,
  INDEX `idx_blog_post_author_id`(`author_id` ASC) USING BTREE,
  INDEX `idx_blog_post_status_published_at`(`status` ASC, `published_at` ASC) USING BTREE,
  INDEX `idx_blog_post_create_time`(`create_time` ASC) USING BTREE,
  INDEX `idx_blog_post_content_file_id`(`content_file_id` ASC) USING BTREE,
  INDEX `idx_blog_post_cover_file_id`(`cover_file_id` ASC) USING BTREE,
  INDEX `idx_blog_post_type_status_published_at`(`post_type` ASC, `status` ASC, `visibility` ASC, `published_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客文章主表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of blog_post
-- ----------------------------

-- ----------------------------
-- Table structure for blog_post_category
-- ----------------------------
DROP TABLE IF EXISTS `blog_post_category`;
CREATE TABLE `blog_post_category`  (
  `post_id` bigint NOT NULL COMMENT '文章ID',
  `category_id` bigint NOT NULL COMMENT '分类ID',
  PRIMARY KEY (`post_id`, `category_id`) USING BTREE,
  INDEX `idx_blog_post_category_category_id`(`category_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客文章分类关系表（多对多）' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of blog_post_category
-- ----------------------------

-- ----------------------------
-- Table structure for blog_post_tag
-- ----------------------------
DROP TABLE IF EXISTS `blog_post_tag`;
CREATE TABLE `blog_post_tag`  (
  `post_id` bigint NOT NULL COMMENT '文章ID',
  `tag_id` bigint NOT NULL COMMENT '标签ID',
  PRIMARY KEY (`post_id`, `tag_id`) USING BTREE,
  INDEX `idx_blog_post_tag_tag_id`(`tag_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客文章标签关系表（多对多）' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of blog_post_tag
-- ----------------------------

-- ----------------------------
-- Table structure for blog_search_index_task
-- ----------------------------
DROP TABLE IF EXISTS `blog_search_index_task`;
CREATE TABLE `blog_search_index_task`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `post_id` bigint NOT NULL COMMENT '关联的文章ID',
  `index_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'blog_posts' COMMENT '索引名称',
  `action` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '操作类型：upsert(插入/更新)/delete(删除)',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'pending' COMMENT '状态：pending(等待)/running(执行中)/success(成功)/failed(失败)',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '错误信息（失败时记录）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `finished_at` datetime NULL DEFAULT NULL COMMENT '完成时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_blog_search_task_status`(`status` ASC) USING BTREE,
  INDEX `idx_blog_search_task_post_id`(`post_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 10 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客搜索索引同步任务表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of blog_search_index_task
-- ----------------------------

-- ----------------------------
-- Table structure for blog_series
-- ----------------------------
DROP TABLE IF EXISTS `blog_series`;
CREATE TABLE `blog_series`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '系列ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '系列名称',
  `slug` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '系列URL标识（唯一）',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '系列简介',
  `cover_file_id` bigint NULL DEFAULT NULL COMMENT '封面文件ID（关联blog_file_asset）',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'draft' COMMENT 'draft/published/archived',
  `visibility` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'public' COMMENT 'public/private',
  `is_finished` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否完结',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '系列列表排序',
  `create_by` bigint NOT NULL COMMENT '创建者ID（关联sys_user）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_slug`(`slug` ASC) USING BTREE,
  INDEX `idx_status_visibility`(`status` ASC, `visibility` ASC) USING BTREE,
  INDEX `idx_cover_file_id`(`cover_file_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客系列表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_series
-- ----------------------------

-- ----------------------------
-- Table structure for blog_series_catalog
-- ----------------------------
DROP TABLE IF EXISTS `blog_series_catalog`;
CREATE TABLE `blog_series_catalog`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `series_id` bigint NOT NULL,
  `parent_id` bigint NULL DEFAULT NULL,
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `node_type` tinyint NOT NULL DEFAULT 0 COMMENT '0目录 1文章集合 2链接',
  `link_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'node_type=2时有效',
  `link_target` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '_blank' COMMENT '_blank/_self',
  `path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '树路径，如 /1/5/12/',
  `level` int NOT NULL DEFAULT 0,
  `sort_order` int NOT NULL DEFAULT 0,
  `children_count` int NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_series_id`(`series_id` ASC) USING BTREE,
  INDEX `idx_parent_id`(`parent_id` ASC) USING BTREE,
  INDEX `idx_series_parent_sort`(`series_id` ASC, `parent_id` ASC, `sort_order` ASC) USING BTREE,
  INDEX `idx_path`(`path`(191) ASC) USING BTREE,
  CONSTRAINT `fk_catalog_parent` FOREIGN KEY (`parent_id`) REFERENCES `blog_series_catalog` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_catalog_series` FOREIGN KEY (`series_id`) REFERENCES `blog_series` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系列目录节点表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_series_catalog
-- ----------------------------

-- ----------------------------
-- Table structure for blog_series_catalog_post
-- ----------------------------
DROP TABLE IF EXISTS `blog_series_catalog_post`;
CREATE TABLE `blog_series_catalog_post`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `catalog_id` bigint NOT NULL,
  `post_id` bigint NOT NULL,
  `sort_order` int NOT NULL DEFAULT 0,
  `is_primary` tinyint(1) NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_catalog_post`(`catalog_id` ASC, `post_id` ASC) USING BTREE,
  INDEX `idx_post_id`(`post_id` ASC) USING BTREE,
  INDEX `idx_catalog_sort`(`catalog_id` ASC, `sort_order` ASC) USING BTREE,
  CONSTRAINT `fk_cp_catalog` FOREIGN KEY (`catalog_id`) REFERENCES `blog_series_catalog` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_cp_post` FOREIGN KEY (`post_id`) REFERENCES `blog_post` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '目录文章关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_series_catalog_post
-- ----------------------------

-- ----------------------------
-- Table structure for blog_tag
-- ----------------------------
DROP TABLE IF EXISTS `blog_tag`;
CREATE TABLE `blog_tag`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '标签名称',
  `slug` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '标签URL标识',
  `use_count` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '使用次数（文章数）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_blog_tag_slug`(`slug` ASC) USING BTREE,
  UNIQUE INDEX `uk_blog_tag_name`(`name` ASC) USING BTREE,
  INDEX `idx_blog_tag_use_count`(`use_count` DESC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 23 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客标签表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of blog_tag
-- ----------------------------

-- ----------------------------
-- Table structure for space_bookmark
-- ----------------------------
DROP TABLE IF EXISTS `space_bookmark`;
CREATE TABLE `space_bookmark`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '书签ID',
  `user_id` bigint NOT NULL COMMENT '所属用户ID',
  `folder_id` bigint NOT NULL DEFAULT 0 COMMENT '所属目录ID，0表示未分类',
  `title` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '书签标题',
  `url` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '原始URL',
  `normalized_url` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '规范化URL，用于去重',
  `url_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'URL哈希，用于索引和去重',
  `domain` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '域名',
  `description` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '描述',
  `favicon_url` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '站点图标URL',
  `favicon_file_id` bigint NULL DEFAULT NULL COMMENT '站点图标文件ID，关联sys_file',
  `source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'manual' COMMENT '来源：manual/chrome/import',
  `source_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '外部来源标识',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0正常 1归档 2失效',
  `visit_count` int NOT NULL DEFAULT 0 COMMENT '访问次数',
  `last_visit_time` datetime NULL DEFAULT NULL COMMENT '最后访问时间',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '目录内排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` bigint NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  `delete_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_space_bookmark_user_folder`(`user_id` ASC, `folder_id` ASC, `deleted` ASC) USING BTREE,
  INDEX `idx_space_bookmark_user_hash`(`user_id` ASC, `url_hash` ASC, `deleted` ASC) USING BTREE,
  INDEX `idx_space_bookmark_domain`(`domain` ASC) USING BTREE,
  INDEX `idx_space_bookmark_status`(`user_id` ASC, `status` ASC, `deleted` ASC) USING BTREE,
  INDEX `idx_space_bookmark_sort`(`folder_id` ASC, `sort_order` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 325 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '书签表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of space_bookmark
-- ----------------------------

-- ----------------------------
-- Table structure for space_bookmark_export_task
-- ----------------------------
DROP TABLE IF EXISTS `space_bookmark_export_task`;
CREATE TABLE `space_bookmark_export_task`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '导出任务ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `file_id` bigint NULL DEFAULT NULL COMMENT '导出文件ID，关联sys_file',
  `export_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'chrome_html' COMMENT '导出类型：chrome_html/json',
  `scope_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'all' COMMENT '范围：all/folder/tag',
  `scope_id` bigint NULL DEFAULT NULL COMMENT '范围ID',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0待处理 1处理中 2成功 3失败',
  `total_count` int NOT NULL DEFAULT 0 COMMENT '导出数量',
  `error_msg` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '错误信息',
  `create_by` bigint NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  `delete_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_space_bookmark_export_task_user`(`user_id` ASC, `status` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '书签导出任务表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of space_bookmark_export_task
-- ----------------------------

-- ----------------------------
-- Table structure for space_bookmark_folder
-- ----------------------------
DROP TABLE IF EXISTS `space_bookmark_folder`;
CREATE TABLE `space_bookmark_folder`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '目录ID',
  `user_id` bigint NOT NULL COMMENT '所属用户ID',
  `parent_id` bigint NOT NULL DEFAULT 0 COMMENT '父目录ID，0表示根目录',
  `ancestors` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '祖级列表，如0,1,2',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '目录名称',
  `level` int NOT NULL DEFAULT 1 COMMENT '目录层级',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
  `source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'manual' COMMENT '来源：manual/chrome/import',
  `source_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '外部来源标识',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` bigint NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  `delete_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_space_bookmark_folder_user_parent`(`user_id` ASC, `parent_id` ASC, `deleted` ASC) USING BTREE,
  INDEX `idx_space_bookmark_folder_user_sort`(`user_id` ASC, `sort_order` ASC) USING BTREE,
  INDEX `idx_space_bookmark_folder_ancestors`(`ancestors`(191) ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 56 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '书签目录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of space_bookmark_folder
-- ----------------------------

-- ----------------------------
-- Table structure for space_bookmark_import_task
-- ----------------------------
DROP TABLE IF EXISTS `space_bookmark_import_task`;
CREATE TABLE `space_bookmark_import_task`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '导入任务ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `file_id` bigint NULL DEFAULT NULL COMMENT '导入文件ID，关联sys_file',
  `source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'chrome' COMMENT '来源：chrome/html/json',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0待处理 1处理中 2成功 3失败',
  `total_count` int NOT NULL DEFAULT 0 COMMENT '总数量',
  `success_count` int NOT NULL DEFAULT 0 COMMENT '成功数量',
  `duplicate_count` int NOT NULL DEFAULT 0 COMMENT '重复数量',
  `fail_count` int NOT NULL DEFAULT 0 COMMENT '失败数量',
  `error_msg` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '错误信息',
  `create_by` bigint NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  `delete_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_space_bookmark_import_task_user`(`user_id` ASC, `status` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '书签导入任务表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of space_bookmark_import_task
-- ----------------------------

-- ----------------------------
-- Table structure for space_bookmark_tag
-- ----------------------------
DROP TABLE IF EXISTS `space_bookmark_tag`;
CREATE TABLE `space_bookmark_tag`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `bookmark_id` bigint NOT NULL COMMENT '书签ID',
  `tag_id` bigint NOT NULL COMMENT '标签ID',
  `create_by` bigint NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  `delete_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_space_bookmark_tag_bookmark`(`bookmark_id` ASC, `deleted` ASC) USING BTREE,
  INDEX `idx_space_bookmark_tag_tag`(`tag_id` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '书签标签关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of space_bookmark_tag
-- ----------------------------

-- ----------------------------
-- Table structure for space_tag
-- ----------------------------
DROP TABLE IF EXISTS `space_tag`;
CREATE TABLE `space_tag`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  `user_id` bigint NOT NULL COMMENT '所属用户ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '标签名称',
  `color` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '标签颜色',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` bigint NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  `delete_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_space_tag_user_name`(`user_id` ASC, `name` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '空间标签表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of space_tag
-- ----------------------------

-- ----------------------------
-- Table structure for sys_config
-- ----------------------------
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config`  (
  `id` bigint NOT NULL COMMENT '配置ID',
  `config_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '配置键（唯一，如 site.title）',
  `config_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '配置值（支持长文本、JSON等）',
  `config_type` tinyint NOT NULL DEFAULT 1 COMMENT '类型：1=文本 2=数字 3=布尔 4=JSON',
  `config_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '配置名称（如 网站标题）',
  `group_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'default' COMMENT '配置分组（如 system, email, security）',
  `is_frontend` tinyint NOT NULL DEFAULT 0 COMMENT '是否前端可见：1=是（如主题色） 0=否（如数据库密码）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `create_by` bigint NULL DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_config_key`(`config_key` ASC) USING BTREE,
  INDEX `idx_group`(`group_name` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_config
-- ----------------------------
INSERT INTO `sys_config` VALUES (1001, 'captcha.expire.seconds', '120', 2, '验证码过期时间(秒)', 'captcha', 0, '验证码生成后多久过期', NULL, '2026-05-06 07:35:48', NULL, '2026-05-06 07:35:48');
INSERT INTO `sys_config` VALUES (1002, 'captcha.verify.timeout.seconds', '300', 2, '验证通过有效期(秒)', 'captcha', 0, 'verifyToken 过期后需要重新验证', NULL, '2026-05-06 07:35:48', NULL, '2026-05-06 07:35:48');
INSERT INTO `sys_config` VALUES (1003, 'captcha.repeat.interval.seconds', '5', 2, '重复生成最小间隔(秒)', 'captcha', 0, '同 IP 两次 /captcha/get 之间的最小间隔', NULL, '2026-05-06 07:35:48', NULL, '2026-05-06 07:35:48');
INSERT INTO `sys_config` VALUES (1004, 'captcha.type.default', 'blockPuzzle', 1, '默认验证码类型', 'captcha', 1, 'blockPuzzle=滑块, clickWord=文字点选', NULL, '2026-05-06 07:35:48', NULL, '2026-05-06 07:35:48');
INSERT INTO `sys_config` VALUES (1005, 'captcha.type.slider.enabled', '1', 3, '是否启用滑块', 'captcha', 1, '', NULL, '2026-05-06 07:35:48', NULL, '2026-05-06 07:35:48');
INSERT INTO `sys_config` VALUES (1006, 'captcha.type.click.enabled', '1', 3, '是否启用文字点选', 'captcha', 1, '', NULL, '2026-05-06 07:35:48', NULL, '2026-05-06 07:35:48');
INSERT INTO `sys_config` VALUES (1007, 'captcha.image.water.mark', 'Nebula', 1, '验证码图片水印', 'captcha', 1, '', NULL, '2026-05-06 07:35:48', NULL, '2026-05-06 07:35:48');
INSERT INTO `sys_config` VALUES (1008, 'captcha.image.font.type', '宋体', 1, '验证码字体', 'captcha', 0, '', NULL, '2026-05-06 07:35:48', NULL, '2026-05-06 07:35:48');
INSERT INTO `sys_config` VALUES (1009, 'captcha.image.aj.tolerant', '10', 2, '滑块校验容忍像素', 'captcha', 0, '', NULL, '2026-05-06 07:35:48', NULL, '2026-05-07 03:22:17');
INSERT INTO `sys_config` VALUES (1101, 'auth.register.enabled', '1', 3, '是否开启注册', 'auth', 1, '关闭后 /auth/register 直接拒绝', NULL, '2026-05-06 08:03:34', NULL, '2026-05-07 07:22:06');
INSERT INTO `sys_config` VALUES (1102, 'auth.username.min.length', '4', 2, '用户名最小长度', 'auth', 0, '', NULL, '2026-05-06 08:03:34', NULL, '2026-05-06 08:03:34');
INSERT INTO `sys_config` VALUES (1103, 'auth.username.max.length', '32', 2, '用户名最大长度', 'auth', 0, '', NULL, '2026-05-06 08:03:34', NULL, '2026-05-06 08:03:34');
INSERT INTO `sys_config` VALUES (1104, 'auth.password.min.length', '6', 2, '密码最小长度', 'auth', 0, '', NULL, '2026-05-06 08:03:34', NULL, '2026-05-06 08:03:34');
INSERT INTO `sys_config` VALUES (1105, 'auth.password.max.length', '64', 2, '密码最大长度', 'auth', 0, '', NULL, '2026-05-06 08:03:34', NULL, '2026-05-06 08:03:34');

-- ----------------------------
-- Table structure for sys_dict_data
-- ----------------------------
DROP TABLE IF EXISTS `sys_dict_data`;
CREATE TABLE `sys_dict_data`  (
  `id` bigint NOT NULL COMMENT '字典数据ID',
  `dict_type_id` bigint NOT NULL COMMENT '关联的字典类型ID',
  `dict_label` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '字典标签（展示值，如 \"启用\"）',
  `dict_value` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '字典值（实际存储值，如 \"1\"）',
  `css_class` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'CSS类名（前端样式，如 text-success）',
  `list_class` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '列表样式（如 primary, danger）',
  `is_default` tinyint NOT NULL DEFAULT 0 COMMENT '是否默认：1=是',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序（越小越靠前）',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1=正常 0=停用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_dict_type_id`(`dict_type_id` ASC) USING BTREE,
  INDEX `idx_sort`(`sort` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '字典数据表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_dict_data
-- ----------------------------

-- ----------------------------
-- Table structure for sys_dict_type
-- ----------------------------
DROP TABLE IF EXISTS `sys_dict_type`;
CREATE TABLE `sys_dict_type`  (
  `id` bigint NOT NULL COMMENT '字典类型ID',
  `type_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '字典类型编码（唯一，如 user_status）',
  `type_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '字典类型名称（如 用户状态）',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1=正常 0=停用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `create_by` bigint NULL DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  `delete_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_type_code`(`type_code` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '字典类型表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_dict_type
-- ----------------------------

-- ----------------------------
-- Table structure for sys_file
-- ----------------------------
DROP TABLE IF EXISTS `sys_file`;
CREATE TABLE `sys_file`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '文件ID',
  `target_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '关联业务类型（如：blog_post、user_avatar、travel_note）',
  `target_id` bigint NOT NULL COMMENT '关联业务实体ID',
  `file_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'other' COMMENT '文件用途类型（logo、cover、avatar、attachment、image、video、other）',
  `storage_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '存储类型（local、minio、oss、cos）',
  `storage_platform` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '存储平台标识（minio-prod、aliyun-oss、tencent-cos等）',
  `bucket` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '存储桶名称',
  `object_key` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '对象存储Key/文件路径',
  `url` varchar(750) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件访问URL',
  `original_filename` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '原始文件名',
  `stored_filename` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '存储文件名（重命名后的文件名）',
  `extension` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件扩展名',
  `mime_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'MIME类型',
  `size_bytes` bigint NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
  `width` int NULL DEFAULT NULL COMMENT '图片宽度（像素）',
  `height` int NULL DEFAULT NULL COMMENT '图片高度（像素）',
  `duration` int NULL DEFAULT NULL COMMENT '音视频时长（秒）',
  `hash_sha256` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件SHA256哈希值，用于去重和秒传',
  `is_public` tinyint NOT NULL DEFAULT 1 COMMENT '是否公开（1公开 0私有）',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '文件状态（1正常 0删除 2上传中 3上传失败 4禁用）',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序值',
  `metadata` json NULL COMMENT '扩展元数据（JSON格式）',
  `create_by` bigint NULL DEFAULT NULL COMMENT '上传人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_target`(`target_type` ASC, `target_id` ASC, `sort_order` ASC) USING BTREE COMMENT '业务关联查询索引',
  INDEX `idx_target_status`(`target_type` ASC, `target_id` ASC, `status` ASC) USING BTREE COMMENT '业务状态查询索引',
  INDEX `idx_hash`(`hash_sha256` ASC) USING BTREE COMMENT '文件哈希索引',
  INDEX `idx_create_by`(`create_by` ASC) USING BTREE COMMENT '上传人索引'
) ENGINE = InnoDB AUTO_INCREMENT = 2059841760028413955 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '统一文件资源表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_file
-- ----------------------------

-- ----------------------------
-- Table structure for sys_menu
-- ----------------------------
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
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '菜单与权限表' ROW_FORMAT = DYNAMIC;

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
INSERT INTO `sys_menu` VALUES (30, 0, 1, 'AI中转管理', NULL, '/ai-relay', 'BasicLayout', NULL, 'lucide:plug-zap', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 300, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (31, 30, 2, '服务商管理', 'AiRelayProvider', '/ai-relay/provider', 'ai-relay/provider/index', 'blog:ai-relay:provider:list', 'lucide:server', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (32, 30, 2, '套餐管理', 'AiRelayPackage', '/ai-relay/package', 'ai-relay/package/index', 'blog:ai-relay:package:list', 'lucide:package', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (33, 30, 2, '套餐类型', 'AiRelayPackageType', '/ai-relay/package-type', 'ai-relay/package-type/index', 'blog:ai-relay:package-type:list', 'lucide:list-checks', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (34, 30, 2, 'AI模型', 'AiRelayModel', '/ai-relay/model', 'ai-relay/model/index', 'blog:ai-relay:model:list', 'lucide:bot', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (35, 30, 2, '支付方式', 'AiRelayPaymentMethod', '/ai-relay/payment-method', 'ai-relay/payment-method/index', 'blog:ai-relay:payment-method:list', 'lucide:credit-card', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 5, 1, 1, NULL, '2026-05-28 00:00:00', '2026-05-28 00:00:00');
INSERT INTO `sys_menu` VALUES (36, 30, 2, '推荐与测评', 'AiRelayRecommend', '/ai-relay/recommend', 'ai-relay/recommend/index', 'blog:ai-relay:recommend:list', 'lucide:star', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 6, 1, 1, NULL, '2026-05-29 00:00:00', '2026-05-29 00:00:00');
INSERT INTO `sys_menu` VALUES (37, 30, 2, '充值记录', 'AiRelayRecharge', '/ai-relay/recharge', 'ai-relay/recharge/index', 'blog:ai-relay:recharge:list', 'lucide:wallet', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 7, 1, 1, NULL, '2026-05-29 00:00:00', '2026-05-29 00:00:00');
INSERT INTO `sys_menu` VALUES (40, 0, 1, '个人空间', 'Space', '/space', 'BasicLayout', NULL, 'lucide:bookmark', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 250, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (41, 40, 2, '书签管理', 'SpaceBookmark', '/space/bookmark', 'space/bookmark/index', 'space:bookmark:list', 'lucide:bookmark-plus', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (42, 40, 2, '目录管理', 'SpaceFolder', '/space/folder', 'space/folder/index', 'space:folder:list', 'lucide:folder-tree', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (43, 40, 2, '标签管理', 'SpaceTag', '/space/tag', 'space/tag/index', 'space:tag:list', 'lucide:tags', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (44, 40, 2, '导入任务', 'SpaceImportTask', '/space/import-task', 'space/import-task/index', 'space:bookmark-import:list', 'lucide:download', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (45, 40, 2, '导出任务', 'SpaceExportTask', '/space/export-task', 'space/export-task/index', 'space:bookmark-export:list', 'lucide:upload', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 5, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
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
INSERT INTO `sys_menu` VALUES (4101, 41, 3, '查询书签', NULL, NULL, NULL, 'space:bookmark:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4102, 41, 3, '新增书签', NULL, NULL, NULL, 'space:bookmark:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4103, 41, 3, '修改书签', NULL, NULL, NULL, 'space:bookmark:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4104, 41, 3, '删除书签', NULL, NULL, NULL, 'space:bookmark:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4201, 42, 3, '查询目录', NULL, NULL, NULL, 'space:folder:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4202, 42, 3, '新增目录', NULL, NULL, NULL, 'space:folder:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4203, 42, 3, '修改目录', NULL, NULL, NULL, 'space:folder:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4204, 42, 3, '删除目录', NULL, NULL, NULL, 'space:folder:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4301, 43, 3, '查询标签', NULL, NULL, NULL, 'space:tag:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4302, 43, 3, '新增标签', NULL, NULL, NULL, 'space:tag:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4303, 43, 3, '修改标签', NULL, NULL, NULL, 'space:tag:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4304, 43, 3, '删除标签', NULL, NULL, NULL, 'space:tag:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4401, 44, 3, '查询导入任务', NULL, NULL, NULL, 'space:bookmark-import:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4402, 44, 3, '取消导入任务', NULL, NULL, NULL, 'space:bookmark-import:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4501, 45, 3, '查询导出任务', NULL, NULL, NULL, 'space:bookmark-export:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (4502, 45, 3, '取消导出任务', NULL, NULL, NULL, 'space:bookmark-export:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');

-- ----------------------------
-- Table structure for sys_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role`  (
  `id` bigint NOT NULL COMMENT '角色ID（雪花算法）',
  `role_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色编码（唯一，用于程序判断，如 admin, user）',
  `role_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色名称（展示用）',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1正常 0禁用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` bigint NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
  `delete_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_role_code`(`role_code` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统角色表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_role
-- ----------------------------
INSERT INTO `sys_role` VALUES (1, 'SUPER_ADMIN', '超级管理员', 1, '系统内置超级管理员（拥有所有权限）', NULL, '2026-05-09 03:10:30', NULL, '2026-05-09 15:39:04', 0, NULL);
INSERT INTO `sys_role` VALUES (2052706759021424642, 'admin', '管理员', 1, NULL, NULL, '2026-05-08 19:06:43', NULL, '2026-05-08 19:06:43', 0, NULL);

-- ----------------------------
-- Table structure for sys_role_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu`  (
  `role_id` bigint NOT NULL,
  `menu_id` bigint NOT NULL,
  PRIMARY KEY (`role_id`, `menu_id`) USING BTREE,
  INDEX `idx_menu_id`(`menu_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色菜单关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_role_menu
-- ----------------------------
INSERT INTO `sys_role_menu` VALUES (1, 1);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 1);
INSERT INTO `sys_role_menu` VALUES (1, 2);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2);
INSERT INTO `sys_role_menu` VALUES (1, 10);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 10);
INSERT INTO `sys_role_menu` VALUES (1, 11);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 11);
INSERT INTO `sys_role_menu` VALUES (1, 12);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 12);
INSERT INTO `sys_role_menu` VALUES (1, 13);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 13);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 14);
INSERT INTO `sys_role_menu` VALUES (1, 20);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 20);
INSERT INTO `sys_role_menu` VALUES (1, 21);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 21);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 22);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 23);
INSERT INTO `sys_role_menu` VALUES (1, 24);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 24);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 25);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 26);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 27);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 28);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 29);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 30);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 31);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 32);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 33);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 34);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 35);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 36);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 37);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 40);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 41);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 42);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 43);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 44);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 45);
INSERT INTO `sys_role_menu` VALUES (1, 1101);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 1101);
INSERT INTO `sys_role_menu` VALUES (1, 1102);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 1102);
INSERT INTO `sys_role_menu` VALUES (1, 1103);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 1103);
INSERT INTO `sys_role_menu` VALUES (1, 1201);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 1201);
INSERT INTO `sys_role_menu` VALUES (1, 1202);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 1202);
INSERT INTO `sys_role_menu` VALUES (1, 1203);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 1203);
INSERT INTO `sys_role_menu` VALUES (1, 1301);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 1301);
INSERT INTO `sys_role_menu` VALUES (1, 1302);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 1302);
INSERT INTO `sys_role_menu` VALUES (1, 1303);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 1303);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 1401);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 1402);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 1403);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 1404);
INSERT INTO `sys_role_menu` VALUES (1, 2101);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2101);
INSERT INTO `sys_role_menu` VALUES (1, 2102);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2102);
INSERT INTO `sys_role_menu` VALUES (1, 2103);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2103);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2201);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2202);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2203);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2301);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2302);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2303);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2304);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2501);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2502);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2503);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2504);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2701);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2702);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2703);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2704);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2801);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2802);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2803);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2804);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 3101);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 3102);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 3103);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 3104);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 3201);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 3202);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 3203);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 3204);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 3301);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 3302);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 3303);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 3304);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 3401);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 3402);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 3403);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 3404);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 3501);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 3502);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 3503);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 3504);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 3601);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 3602);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 3603);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 3604);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 3701);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 3702);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 3703);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 3704);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4101);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4102);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4103);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4104);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4201);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4202);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4203);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4204);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4301);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4302);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4303);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4304);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4401);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4402);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4501);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 4502);

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`  (
  `id` bigint NOT NULL COMMENT '用户ID（雪花算法）',
  `username` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '登录账号',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '登录密码（加密存储）',
  `nickname` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用户昵称',
  `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '头像地址',
  `mobile` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '手机号',
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '邮箱',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1正常 0禁用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` bigint NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  `delete_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_username`(`username` ASC) USING BTREE,
  UNIQUE INDEX `uk_mobile`(`mobile` ASC) USING BTREE,
  UNIQUE INDEX `uk_email`(`email` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_create_time`(`create_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统用户表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_user
-- ----------------------------
INSERT INTO `sys_user` VALUES (1, 'nebula', '$2a$10$a/c6OV1BuVLclL4HDUOMCuouKwoa7Das6eUHlW2XrXJkURdKKtOJW', '超级管理员', NULL, NULL, NULL, 1, '系统初始化超级管理员', 1, '2026-05-08 11:19:31', NULL, '2026-05-09 15:30:59', 0, NULL);
INSERT INTO `sys_user` VALUES (2052290101098295297, 'admin', '$2a$10$a/c6OV1BuVLclL4HDUOMCuouKwoa7Das6eUHlW2XrXJkURdKKtOJW', 'admin', NULL, NULL, NULL, 1, NULL, NULL, '2026-05-07 15:31:04', NULL, '2026-05-13 15:56:42', 0, NULL);

-- ----------------------------
-- Table structure for sys_user_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role`  (
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`, `role_id`) USING BTREE,
  INDEX `idx_role_id`(`role_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户角色关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_user_role
-- ----------------------------
INSERT INTO `sys_user_role` VALUES (1, 1, '2026-05-09 11:12:12');
INSERT INTO `sys_user_role` VALUES (2052290101098295297, 2052706759021424642, '2026-05-08 19:06:54');

-- ----------------------------
-- Table structure for travel_checkin
-- ----------------------------
DROP TABLE IF EXISTS `travel_checkin`;
CREATE TABLE `travel_checkin`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `trip_day_id` bigint NOT NULL COMMENT '所属行程日ID',
  `destination_id` bigint NULL DEFAULT NULL COMMENT '关联的目的地ID（可选）',
  `custom_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '自定义地点名称',
  `custom_longitude` decimal(10, 7) NULL DEFAULT NULL COMMENT '自定义经度',
  `custom_latitude` decimal(10, 7) NULL DEFAULT NULL COMMENT '自定义纬度',
  `arrival_time` datetime NULL DEFAULT NULL COMMENT '到达时间',
  `departure_time` datetime NULL DEFAULT NULL COMMENT '离开时间',
  `notes` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '游玩笔记',
  `rating` decimal(2, 1) NULL DEFAULT NULL COMMENT '个人评分',
  `photos` json NULL COMMENT '照片ID数组（blog_file_asset的id）',
  `sort_order` int NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_trip_day_id`(`trip_day_id` ASC) USING BTREE,
  INDEX `idx_destination_id`(`destination_id` ASC) USING BTREE,
  INDEX `idx_custom_coord`(`custom_longitude` ASC, `custom_latitude` ASC) USING BTREE,
  CONSTRAINT `fk_checkin_day` FOREIGN KEY (`trip_day_id`) REFERENCES `travel_trip_day` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_checkin_destination` FOREIGN KEY (`destination_id`) REFERENCES `travel_destination` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '行程打卡点' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of travel_checkin
-- ----------------------------

-- ----------------------------
-- Table structure for travel_destination
-- ----------------------------
DROP TABLE IF EXISTS `travel_destination`;
CREATE TABLE `travel_destination`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '目的地ID',
  `parent_id` bigint NULL DEFAULT NULL COMMENT '父级目的地ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '名称',
  `slug` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'URL标识',
  `type` tinyint NOT NULL DEFAULT 0 COMMENT '类型：0国家 1省份/州 2城市 3景点/POI',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '描述',
  `cover_file_id` bigint NULL DEFAULT NULL COMMENT '封面图（关联blog_file_asset）',
  `longitude` decimal(10, 7) NULL DEFAULT NULL COMMENT '经度',
  `latitude` decimal(10, 7) NULL DEFAULT NULL COMMENT '纬度',
  `address` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '详细地址',
  `visit_count` int NOT NULL DEFAULT 0 COMMENT '访问/打卡次数',
  `rating` decimal(2, 1) NULL DEFAULT NULL COMMENT '评分（0.0-5.0）',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1启用',
  `sort_order` int NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_slug`(`slug` ASC) USING BTREE,
  INDEX `idx_parent_id`(`parent_id` ASC) USING BTREE,
  INDEX `idx_type`(`type` ASC) USING BTREE,
  INDEX `idx_coord`(`longitude` ASC, `latitude` ASC) USING BTREE,
  CONSTRAINT `fk_destination_parent` FOREIGN KEY (`parent_id`) REFERENCES `travel_destination` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '旅游目的地表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of travel_destination
-- ----------------------------

-- ----------------------------
-- Table structure for travel_trip
-- ----------------------------
DROP TABLE IF EXISTS `travel_trip`;
CREATE TABLE `travel_trip`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '游记ID',
  `user_id` bigint NOT NULL COMMENT '作者用户ID（关联sys_user）',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '标题',
  `slug` varchar(220) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'URL标识',
  `summary` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '摘要',
  `cover_file_id` bigint NULL DEFAULT NULL COMMENT '封面图（关联blog_file_asset）',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'draft' COMMENT 'draft/published/archived',
  `visibility` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'public' COMMENT 'public/private',
  `start_date` date NULL DEFAULT NULL COMMENT '开始日期',
  `end_date` date NULL DEFAULT NULL COMMENT '结束日期',
  `days_count` int NULL DEFAULT NULL COMMENT '总天数',
  `persons` int NULL DEFAULT NULL COMMENT '人数',
  `cost_total` decimal(10, 2) NULL DEFAULT NULL COMMENT '总花费',
  `cost_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'CNY' COMMENT '货币',
  `view_count` int NOT NULL DEFAULT 0,
  `like_count` int NOT NULL DEFAULT 0,
  `published_at` datetime NULL DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_slug`(`slug` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_status_visibility_published`(`status` ASC, `visibility` ASC, `published_at` ASC) USING BTREE,
  INDEX `idx_dates`(`start_date` ASC, `end_date` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '旅行游记主表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of travel_trip
-- ----------------------------

-- ----------------------------
-- Table structure for travel_trip_blog_post
-- ----------------------------
DROP TABLE IF EXISTS `travel_trip_blog_post`;
CREATE TABLE `travel_trip_blog_post`  (
  `trip_id` bigint NOT NULL,
  `post_id` bigint NOT NULL,
  `post_type` tinyint NOT NULL DEFAULT 0 COMMENT '关联类型：0主要文章 1相关推荐',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`trip_id`, `post_id`) USING BTREE,
  INDEX `idx_post_id`(`post_id` ASC) USING BTREE,
  CONSTRAINT `fk_trip_blog_post` FOREIGN KEY (`post_id`) REFERENCES `blog_post` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_trip_blog_trip` FOREIGN KEY (`trip_id`) REFERENCES `travel_trip` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '游记与博客文章关联' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of travel_trip_blog_post
-- ----------------------------

-- ----------------------------
-- Table structure for travel_trip_day
-- ----------------------------
DROP TABLE IF EXISTS `travel_trip_day`;
CREATE TABLE `travel_trip_day`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `trip_id` bigint NOT NULL COMMENT '所属游记ID',
  `day_number` int NOT NULL COMMENT '第几天',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '当日标题',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '当日描述',
  `accommodation` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '住宿地点',
  `meal_cost` decimal(10, 2) NULL DEFAULT NULL COMMENT '餐饮花费',
  `transport_cost` decimal(10, 2) NULL DEFAULT NULL COMMENT '交通花费',
  `other_cost` decimal(10, 2) NULL DEFAULT NULL COMMENT '其他花费',
  `sort_order` int NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_trip_day`(`trip_id` ASC, `day_number` ASC) USING BTREE,
  INDEX `idx_trip_id`(`trip_id` ASC) USING BTREE,
  CONSTRAINT `fk_day_trip` FOREIGN KEY (`trip_id`) REFERENCES `travel_trip` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '行程日表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of travel_trip_day
-- ----------------------------

-- ----------------------------
-- Table structure for worker_node
-- ----------------------------
DROP TABLE IF EXISTS `worker_node`;
CREATE TABLE `worker_node`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键，作为 workerId',
  `host_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'host：ACTUAL=IP，CONTAINER=hostname',
  `port` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'port：ACTUAL=时间戳-随机数，CONTAINER=容器端口',
  `type` int NOT NULL COMMENT '节点类型：1=CONTAINER 2=ACTUAL',
  `launch_date` date NOT NULL COMMENT '启动日期',
  `modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `created` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'UID Generator workerId 分配表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of worker_node
-- ----------------------------

-- ----------------------------
-- Table structure for blog_post_import_task
-- ----------------------------
DROP TABLE IF EXISTS `blog_post_import_task`;
CREATE TABLE `blog_post_import_task`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '导入任务ID',
  `user_id` bigint NOT NULL COMMENT '发起导入的用户ID',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'pending' COMMENT '状态：pending(等待)/running(执行中)/success(完成)/failed(失败)',
  `total_count` int NOT NULL DEFAULT 0 COMMENT '文件总数',
  `processed_count` int NOT NULL DEFAULT 0 COMMENT '已处理数（进度）',
  `success_count` int NOT NULL DEFAULT 0 COMMENT '成功数',
  `fail_count` int NOT NULL DEFAULT 0 COMMENT '失败数',
  `post_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '统一应用的文章状态',
  `visibility` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '统一应用的可见性',
  `post_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '统一应用的内容类型',
  `rehost_images` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否转存外链图片',
  `error_message` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '任务级错误信息（整体失败时记录）',
  `started_at` datetime NULL DEFAULT NULL COMMENT '开始处理时间',
  `finished_at` datetime NULL DEFAULT NULL COMMENT '完成时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_blog_post_import_task_user`(`user_id` ASC, `status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客文章导入任务表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for blog_post_import_item
-- ----------------------------
DROP TABLE IF EXISTS `blog_post_import_item`;
CREATE TABLE `blog_post_import_item`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `task_id` bigint NOT NULL COMMENT '所属导入任务ID',
  `filename` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '原始文件名',
  `success` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否导入成功',
  `article_id` bigint NULL DEFAULT NULL COMMENT '成功时的文章ID',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '派生标题',
  `slug` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '最终 slug',
  `error` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '失败原因',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_blog_post_import_item_task`(`task_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客文章导入明细表' ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
