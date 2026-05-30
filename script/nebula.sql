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

 Date: 30/05/2026 11:19:46
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
) ENGINE = InnoDB AUTO_INCREMENT = 19 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI模型配置' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_model
-- ----------------------------
INSERT INTO `ai_relay_model` VALUES (1, 'gpt-4o', 'GPT-4o', 'OpenAI', 4, 'OpenAI最新多模态旗舰模型，支持文本、图像、音频', 100, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_model` VALUES (2, 'gpt-4-turbo', 'GPT-4 Turbo', 'OpenAI', 1, 'GPT-4增强版，128K上下文', 90, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_model` VALUES (3, 'gpt-3.5-turbo', 'GPT-3.5 Turbo', 'OpenAI', 1, '性价比最高的GPT模型', 80, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_model` VALUES (4, 'claude-3-opus', 'Claude 3 Opus', 'Anthropic', 1, 'Claude最强推理模型', 95, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_model` VALUES (5, 'claude-3-sonnet', 'Claude 3 Sonnet', 'Anthropic', 1, 'Claude平衡型模型', 85, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_model` VALUES (6, 'claude-3-haiku', 'Claude 3 Haiku', 'Anthropic', 1, 'Claude最快响应模型', 75, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_model` VALUES (7, 'gemini-1.5-pro', 'Gemini 1.5 Pro', 'Google', 4, '200万超长上下文多模态模型', 92, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_model` VALUES (8, 'gemini-1.5-flash', 'Gemini 1.5 Flash', 'Google', 4, '轻量快速版Gemini', 82, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_model` VALUES (9, 'deepseek-v3', 'DeepSeek-V3', 'DeepSeek', 1, 'DeepSeek最新大模型，性价比极高', 88, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_model` VALUES (10, 'deepseek-r1', 'DeepSeek-R1', 'DeepSeek', 1, '深度推理模型，擅长数学逻辑', 86, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_model` VALUES (11, 'qwen-max', '通义千问Max', '阿里', 4, '阿里云旗舰模型，中文优化', 84, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_model` VALUES (12, 'qwen-plus', '通义千问Plus', '阿里', 1, '高性价比中文模型', 78, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_model` VALUES (13, 'glm-4-plus', 'GLM-4-Plus', '智谱AI', 1, '智谱最新GLM模型', 83, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_model` VALUES (14, 'glm-4-air', 'GLM-4-Air', '智谱AI', 1, '轻量版GLM', 76, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_model` VALUES (15, 'ernie-4.0', '文心一言4.0', '百度', 4, '文心旗舰版', 81, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_model` VALUES (16, 'ernie-3.5', '文心一言3.5', '百度', 1, '文心高性价比版', 72, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_model` VALUES (17, 'moonshot-v1', 'Moonshot Kimi', '月之暗面', 1, 'Kimi同款长上下文模型', 79, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_model` VALUES (18, 'yi-large', 'Yi-Large', '01.AI', 1, '零一万物旗舰模型', 77, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');

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
) ENGINE = InnoDB AUTO_INCREMENT = 26 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转套餐支持模型及消耗倍率' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_package_model
-- ----------------------------
INSERT INTO `ai_relay_package_model` VALUES (1, 2, 1, 'openai/gpt-4o', 1.0000, 0.000003, 128000, 100.0000, 100.0000, 1, 100, 1, '2026-05-29 05:49:11', '2026-05-29 11:50:42');
INSERT INTO `ai_relay_package_model` VALUES (2, 2, 2, 'openai/gpt-4-turbo', 1.2000, 0.000010, 128000, 100.0000, 90.0000, 0, 90, 1, '2026-05-29 05:49:11', '2026-05-29 11:50:47');
INSERT INTO `ai_relay_package_model` VALUES (3, 2, 3, 'openai/gpt-3.5-turbo', 0.2000, 0.000001, 16384, 200.0000, 80.0000, 0, 80, 1, '2026-05-29 05:49:11', '2026-05-29 11:50:47');
INSERT INTO `ai_relay_package_model` VALUES (4, 2, 4, 'anthropic/claude-3-opus', 2.5000, 0.000015, 200000, 300.0000, 70.0000, 0, 70, 1, '2026-05-29 05:49:11', '2026-05-29 11:50:47');
INSERT INTO `ai_relay_package_model` VALUES (5, 2, 5, 'anthropic/claude-3-sonnet', 1.4000, 0.000003, 200000, 400.0000, 75.0000, 0, 75, 1, '2026-05-29 05:49:11', '2026-05-29 11:50:47');
INSERT INTO `ai_relay_package_model` VALUES (6, 2, 6, 'anthropic/claude-3-haiku', 0.8000, 0.000001, 200000, 22.0000, 65.0000, 0, 65, 1, '2026-05-29 05:49:11', '2026-05-29 11:50:47');
INSERT INTO `ai_relay_package_model` VALUES (7, 2, 7, 'google/gemini-1.5-pro', 1.3000, 0.000004, 2000000, 22.0000, 85.0000, 0, 85, 1, '2026-05-29 05:49:11', '2026-05-29 11:50:47');
INSERT INTO `ai_relay_package_model` VALUES (8, 2, 8, 'google/gemini-1.5-flash', 0.6000, 0.000001, 1000000, 11.0000, 60.0000, 0, 60, 1, '2026-05-29 05:49:11', '2026-05-29 11:50:47');
INSERT INTO `ai_relay_package_model` VALUES (9, 2, 9, 'deepseek/deepseek-v3', 0.5000, 0.000000, 64000, 11.0000, 95.0000, 0, 95, 1, '2026-05-29 05:49:11', '2026-05-29 11:50:47');
INSERT INTO `ai_relay_package_model` VALUES (10, 2, 10, 'deepseek/deepseek-r1', 0.6000, 0.000000, 64000, 11.0000, 88.0000, 0, 88, 1, '2026-05-29 05:49:11', '2026-05-29 11:50:47');
INSERT INTO `ai_relay_package_model` VALUES (11, 2, 11, 'qwen/qwen-max', 0.7000, 0.000001, 32000, 11.0000, 78.0000, 0, 78, 1, '2026-05-29 05:49:11', '2026-05-29 11:50:47');
INSERT INTO `ai_relay_package_model` VALUES (12, 11, 1, 'gpt-4o', 1.0000, NULL, 128000, 11.0000, 100.0000, 1, 100, 1, '2026-05-29 05:49:11', '2026-05-29 11:50:47');
INSERT INTO `ai_relay_package_model` VALUES (13, 11, 2, 'gpt-4-turbo', 1.0000, NULL, 128000, 11.0000, 90.0000, 0, 90, 1, '2026-05-29 05:49:11', '2026-05-29 11:50:47');
INSERT INTO `ai_relay_package_model` VALUES (14, 11, 4, 'claude-3-opus', 1.8000, NULL, 200000, 11.0000, 80.0000, 0, 80, 1, '2026-05-29 05:49:11', '2026-05-29 11:50:47');
INSERT INTO `ai_relay_package_model` VALUES (15, 11, 5, 'claude-3-sonnet', 1.2000, NULL, 200000, 11.0000, 85.0000, 0, 85, 1, '2026-05-29 05:49:11', '2026-05-29 11:50:47');
INSERT INTO `ai_relay_package_model` VALUES (16, 11, 9, 'deepseek-v3', 0.4000, NULL, 64000, 11.0000, 95.0000, 0, 95, 1, '2026-05-29 05:49:11', '2026-05-29 11:50:47');
INSERT INTO `ai_relay_package_model` VALUES (17, 16, 1, 'gpt-4o', 0.9000, NULL, 128000, 11.0000, 100.0000, 1, 100, 1, '2026-05-29 05:49:11', '2026-05-29 11:50:47');
INSERT INTO `ai_relay_package_model` VALUES (18, 16, 3, 'gpt-3.5-turbo', 0.1500, NULL, 16384, 11.0000, 90.0000, 0, 90, 1, '2026-05-29 05:49:11', '2026-05-29 11:50:47');
INSERT INTO `ai_relay_package_model` VALUES (19, 16, 5, 'claude-3-sonnet', 1.1000, NULL, 200000, 11.0000, 80.0000, 0, 80, 1, '2026-05-29 05:49:11', '2026-05-29 11:50:47');
INSERT INTO `ai_relay_package_model` VALUES (20, 16, 9, 'deepseek-v3', 0.3500, NULL, 64000, 11.0000, 95.0000, 0, 95, 1, '2026-05-29 05:49:11', '2026-05-29 11:50:47');
INSERT INTO `ai_relay_package_model` VALUES (21, 16, 11, 'qwen-max', 0.6000, NULL, 32000, 11.0000, 70.0000, 0, 70, 1, '2026-05-29 05:49:11', '2026-05-29 11:50:47');
INSERT INTO `ai_relay_package_model` VALUES (22, 19, 1, 'gpt-4o', 1.0000, NULL, 128000, 11.0000, 100.0000, 1, 100, 1, '2026-05-29 05:49:11', '2026-05-29 11:50:47');
INSERT INTO `ai_relay_package_model` VALUES (23, 19, 3, 'gpt-3.5-turbo', 0.2000, NULL, 16384, 11.0000, 90.0000, 0, 90, 1, '2026-05-29 05:49:11', '2026-05-29 11:50:47');
INSERT INTO `ai_relay_package_model` VALUES (24, 19, 5, 'claude-3-sonnet', 1.5000, NULL, 200000, 11.0000, 80.0000, 0, 80, 1, '2026-05-29 05:49:11', '2026-05-29 11:50:47');
INSERT INTO `ai_relay_package_model` VALUES (25, 19, 9, 'deepseek-v3', 0.5000, NULL, 64000, 11.0000, 95.0000, 0, 95, 1, '2026-05-29 05:49:11', '2026-05-29 11:50:47');

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
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转套餐类型配置' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_package_type
-- ----------------------------
INSERT INTO `ai_relay_package_type` VALUES (1, 'day', '天卡', 1, 1, 1, '按天购买的固定周期套餐', 10, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_package_type` VALUES (2, 'week', '周卡', 1, 1, 2, '按周购买的固定周期套餐', 20, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_package_type` VALUES (3, 'month', '月卡', 1, 1, 3, '按月购买的固定周期套餐', 30, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_package_type` VALUES (4, 'quarter', '季卡', 1, 3, 3, '按季度购买的固定周期套餐', 35, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_package_type` VALUES (5, 'year', '年卡', 1, 1, 4, '按年购买的固定周期套餐', 40, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_package_type` VALUES (6, 'usage', '按量计费', 2, NULL, NULL, '按照实际用量计费，预充值模式', 50, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');

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
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转支付方式配置' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_payment_method
-- ----------------------------
INSERT INTO `ai_relay_payment_method` VALUES (1, 'alipay', '支付宝', NULL, '支付宝扫码支付', 10, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_payment_method` VALUES (2, 'wechat', '微信支付', NULL, '微信扫码支付', 20, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_payment_method` VALUES (3, 'bank_card', '银行卡', NULL, '银联/信用卡支付', 30, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_payment_method` VALUES (4, 'paypal', 'PayPal', NULL, '国际版PayPal', 40, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_payment_method` VALUES (5, 'usdt', 'USDT (TRC20)', NULL, '数字货币USDT支付', 50, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_payment_method` VALUES (6, 'crypto', '加密货币', NULL, '支持BTC/ETH等', 60, 0, '2026-05-29 05:49:11', '2026-05-29 05:49:11');

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
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转服务商' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_provider
-- ----------------------------
INSERT INTO `ai_relay_provider` VALUES (1, 'OpenRouter', 'https://openrouter.ai', NULL, '全球最大AI模型聚合平台，提供200+模型的统一API接口，按量付费，开发者首选。', 9.20, 1, 1, '2026-05-28 10:00:00', '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider` VALUES (2, 'AIProxy', 'https://aiproxy.io', NULL, '国内领先的AI中转服务商，提供OpenAI/Claude等海外模型的国内直连代理，企业级SLA保障。', 8.95, 2, 1, '2026-05-28 09:30:00', '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider` VALUES (3, 'NexusAI', 'https://nexusai.com', NULL, '新兴AI聚合平台，支持主流模型且价格优惠，提供月付套餐。', 8.80, 3, 1, '2026-05-27 16:00:00', '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider` VALUES (4, 'API2D', 'https://api2d.com', NULL, '个人开发者友好，最低充值1元，支持支付宝/微信，快速接入。', 8.50, 4, 1, '2026-05-26 11:20:00', '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider` VALUES (5, 'OneAPI', 'https://github.com/songquanpeng/one-api', NULL, '开源方案，可私有化部署，统一接口管理与负载均衡。', 8.40, 5, 1, '2026-05-25 14:30:00', '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider` VALUES (6, 'GPTPandora', 'https://github.com/pandora-next/deploy', NULL, '公益开源项目，社区维护，已停止官方服务，仅供自部署参考。', 7.20, 6, 0, NULL, '2026-05-29 05:49:11', '2026-05-29 05:49:11');

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
) ENGINE = InnoDB AUTO_INCREMENT = 26 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转服务商优势' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_provider_advantage
-- ----------------------------
INSERT INTO `ai_relay_provider_advantage` VALUES (1, 1, '模型数量第一', '聚合超过200种模型，覆盖全球所有主流大模型', 2, NULL, 10, 1, '2026-05-29 09:08:58', '2026-05-29 09:08:58');
INSERT INTO `ai_relay_provider_advantage` VALUES (2, 1, '统一API接口', 'OpenAI兼容，切换模型零代码改动', 1, NULL, 20, 1, '2026-05-29 09:08:58', '2026-05-29 09:08:58');
INSERT INTO `ai_relay_provider_advantage` VALUES (3, 1, '按Token计费', '用多少付多少，无订阅压力，适合弹性需求', 1, NULL, 30, 1, '2026-05-29 09:08:58', '2026-05-29 09:08:58');
INSERT INTO `ai_relay_provider_advantage` VALUES (4, 1, '开发者社区活跃', '官方Discord 2万+成员，问题响应快', 1, NULL, 40, 1, '2026-05-29 09:08:58', '2026-05-29 09:08:58');
INSERT INTO `ai_relay_provider_advantage` VALUES (5, 1, '⚠️ 国内访问需代理', '部分网络环境直连不稳定，建议配合代理使用', 3, NULL, 5, 1, '2026-05-29 09:08:58', '2026-05-29 09:08:58');
INSERT INTO `ai_relay_provider_advantage` VALUES (6, 2, '国内直连免代理', '国内服务器中转，延迟低至50ms，无需科学上网', 2, NULL, 10, 1, '2026-05-29 09:08:58', '2026-05-29 09:08:58');
INSERT INTO `ai_relay_provider_advantage` VALUES (7, 2, '企业级SLA保障', '99.9%可用性承诺，7x24小时技术支持', 2, NULL, 20, 1, '2026-05-29 09:08:58', '2026-05-29 09:08:58');
INSERT INTO `ai_relay_provider_advantage` VALUES (8, 2, '价格优惠30%~50%', '相比官方渠道，相同模型价格更低', 1, NULL, 30, 1, '2026-05-29 09:08:58', '2026-05-29 09:08:58');
INSERT INTO `ai_relay_provider_advantage` VALUES (9, 2, '合规发票支持', '可开具增值税专用发票，企业采购无忧', 1, NULL, 40, 1, '2026-05-29 09:08:58', '2026-05-29 09:08:58');
INSERT INTO `ai_relay_provider_advantage` VALUES (10, 2, '多套餐灵活选择', '从天卡到年卡，从按量到包月，覆盖各种场景', 1, NULL, 35, 1, '2026-05-29 09:08:58', '2026-05-29 09:08:58');
INSERT INTO `ai_relay_provider_advantage` VALUES (11, 3, '极致性价比', '月卡29元/300万Token，业内低价', 2, NULL, 10, 1, '2026-05-29 09:08:58', '2026-05-29 09:08:58');
INSERT INTO `ai_relay_provider_advantage` VALUES (12, 3, '新用户送10美元', '注册即赠体验金，零成本测试', 1, NULL, 20, 1, '2026-05-29 09:08:58', '2026-05-29 09:08:58');
INSERT INTO `ai_relay_provider_advantage` VALUES (13, 3, '模型上新快', 'GPT-4o、Claude 3.5等新模型一周内上线', 1, NULL, 30, 1, '2026-05-29 09:08:58', '2026-05-29 09:08:58');
INSERT INTO `ai_relay_provider_advantage` VALUES (14, 3, '中文文档友好', '全中文操作界面和API文档，降低使用门槛', 1, NULL, 40, 1, '2026-05-29 09:08:58', '2026-05-29 09:08:58');
INSERT INTO `ai_relay_provider_advantage` VALUES (15, 3, '⚠️ 非工作时间客服响应慢', '周末及节假日技术支持响应延迟', 3, NULL, 5, 1, '2026-05-29 09:08:58', '2026-05-29 09:08:58');
INSERT INTO `ai_relay_provider_advantage` VALUES (16, 4, '个人开发者首选', '最低1元起充，学生/个人无负担', 2, NULL, 10, 1, '2026-05-29 09:08:58', '2026-05-29 09:08:58');
INSERT INTO `ai_relay_provider_advantage` VALUES (17, 4, '支付宝微信直接付', '无需外币信用卡，国内支付便捷', 1, NULL, 20, 1, '2026-05-29 09:08:58', '2026-05-29 09:08:58');
INSERT INTO `ai_relay_provider_advantage` VALUES (18, 4, '教程与样例丰富', '官方提供多语言SDK和详细接入指南', 1, NULL, 30, 1, '2026-05-29 09:08:58', '2026-05-29 09:08:58');
INSERT INTO `ai_relay_provider_advantage` VALUES (19, 4, '轻度使用成本极低', '仅偶尔调用API的场景，按量套餐非常划算', 1, NULL, 40, 1, '2026-05-29 09:08:58', '2026-05-29 09:08:58');
INSERT INTO `ai_relay_provider_advantage` VALUES (20, 4, '⚠️ 企业级功能缺失', '无团队管理、无高级监控告警', 3, NULL, 5, 1, '2026-05-29 09:08:58', '2026-05-29 09:08:58');
INSERT INTO `ai_relay_provider_advantage` VALUES (21, 5, '完全开源免费', '代码在GitHub公开，可任意修改和部署', 2, NULL, 10, 1, '2026-05-29 09:08:58', '2026-05-29 09:08:58');
INSERT INTO `ai_relay_provider_advantage` VALUES (22, 5, '私有化部署', '数据不出公司网络，满足数据安全合规', 2, NULL, 20, 1, '2026-05-29 09:08:58', '2026-05-29 09:08:58');
INSERT INTO `ai_relay_provider_advantage` VALUES (23, 5, '负载均衡与容灾', '支持多API Key轮询，自动切换故障渠道', 1, NULL, 30, 1, '2026-05-29 09:08:58', '2026-05-29 09:08:58');
INSERT INTO `ai_relay_provider_advantage` VALUES (24, 5, '对接任意供应商', '可通过配置接入任意OpenAI兼容的代理', 1, NULL, 40, 1, '2026-05-29 09:08:58', '2026-05-29 09:08:58');
INSERT INTO `ai_relay_provider_advantage` VALUES (25, 5, '⚠️ 需要技术团队维护', '自部署需要服务器、数据库和运维能力', 3, NULL, 5, 1, '2026-05-29 09:08:58', '2026-05-29 09:08:58');

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
) ENGINE = InnoDB AUTO_INCREMENT = 26 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转服务商套餐' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_provider_package
-- ----------------------------
INSERT INTO `ai_relay_provider_package` VALUES (1, 1, 6, '按量-入门包', 10.00, NULL, 'USD', 0, 7.50, '预充值10美元，按模型实际费率扣费', 40, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_package` VALUES (2, 1, 6, '按量-标准包', 50.00, NULL, 'USD', 1, 8.80, '预充值50美元，适合个人开发者', 30, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_package` VALUES (3, 1, 6, '按量-专业包', 200.00, 250.00, 'USD', 0, 9.00, '预充值200美元，赠送50美元额度', 20, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_package` VALUES (4, 1, 3, '月卡-基础', 19.99, NULL, 'USD', 0, 7.90, '每月100万Token额度', 35, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_package` VALUES (5, 1, 3, '月卡-进阶', 49.99, 79.99, 'USD', 1, 9.10, '每月400万Token额度，性价比最高', 10, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_package` VALUES (6, 1, 3, '月卡-企业', 199.99, NULL, 'USD', 0, 8.60, '每月2000万Token额度，支持团队协作', 5, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_package` VALUES (7, 2, 1, '天卡-体验', 9.90, 19.90, 'CNY', 0, 6.80, '24小时有效期，含50万Token', 60, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_package` VALUES (8, 2, 2, '周卡-标准', 49.00, 99.00, 'CNY', 0, 7.90, '7天有效期，每日限200次调用', 50, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_package` VALUES (9, 2, 3, '月卡-标准', 199.00, 399.00, 'CNY', 1, 9.20, '30天有效期，无限调用，最热门套餐', 15, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_package` VALUES (10, 2, 3, '季卡-企业', 499.00, 1197.00, 'CNY', 0, 9.00, '90天有效期，无限调用+专属支持', 5, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_package` VALUES (11, 2, 6, '按量-弹性包', 100.00, NULL, 'CNY', 0, 8.50, '预充值100元，按实际用量扣费', 45, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_package` VALUES (12, 2, 6, '按量-企业包', 500.00, 550.00, 'CNY', 1, 9.10, '预充值500元，赠送100元额度', 25, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_package` VALUES (13, 2, 6, '按量-旗舰包', 2000.00, NULL, 'CNY', 0, 8.90, '预充值2000元，专属客户经理', 8, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_package` VALUES (14, 3, 3, '月卡-轻量', 29.00, 59.00, 'CNY', 0, 7.80, '每月300万Token', 55, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_package` VALUES (15, 3, 3, '月卡-标准', 79.00, 159.00, 'CNY', 1, 9.30, '每月1000万Token，赠200万', 12, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_package` VALUES (16, 3, 3, '年卡-专业', 799.00, 1908.00, 'CNY', 0, 9.15, '全年1.5亿Token，最省心', 3, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_package` VALUES (17, 3, 6, '按量-体验', 15.00, NULL, 'CNY', 0, 7.20, '预充值15元，新用户专享', 65, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_package` VALUES (18, 3, 6, '按量-标准', 60.00, NULL, 'CNY', 0, 8.40, '预充值60元，送10元', 42, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_package` VALUES (19, 4, 3, '月卡-个人', 39.00, 79.00, 'CNY', 1, 8.70, '每月200万Token', 48, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_package` VALUES (20, 4, 3, '月卡-专业', 129.00, 299.00, 'CNY', 0, 8.50, '每月800万Token', 32, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_package` VALUES (21, 4, 6, '按量-入门', 10.00, NULL, 'CNY', 0, 7.50, '预充值10元，无有效期', 70, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_package` VALUES (22, 4, 6, '按量-进阶', 50.00, NULL, 'CNY', 0, 8.20, '预充值50元，送5元', 52, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_package` VALUES (23, 5, 6, '开源版-捐赠支持', 0.00, NULL, 'USD', 0, 5.00, '完全免费开源，可自行部署', 80, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_package` VALUES (24, 5, 6, '捐赠-个人', 10.00, NULL, 'USD', 0, 6.50, '支持项目发展，获得优先技术答疑', 75, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_package` VALUES (25, 5, 6, '捐赠-企业', 100.00, NULL, 'USD', 0, 7.00, '企业级部署咨询及定制支持', 68, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');

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
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转服务商套餐额度限制' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_provider_package_limit
-- ----------------------------
INSERT INTO `ai_relay_provider_package_limit` VALUES (1, 2, 1, 50.000000, 'USD', 0, 2, '账户总余额，耗尽后需充值', 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_package_limit` VALUES (2, 11, 1, 999999.000000, 'request', 0, 1, '无限调用，但防止恶意刷量设置极大值', 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_package_limit` VALUES (3, 11, 2, 5000.000000, 'request', 1, 1, '每日上限5000次调用', 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_package_limit` VALUES (4, 16, 1, 10000000.000000, 'token', 3, 1, '每月1000万Token额度', 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_package_limit` VALUES (5, 16, 2, 500000.000000, 'token', 1, 1, '每日上限50万Token', 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_package_limit` VALUES (6, 21, 1, 10.000000, 'CNY', 0, 2, '预充值10元，按量扣费', 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');

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
) ENGINE = InnoDB AUTO_INCREMENT = 13 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转服务商支持支付方式' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_provider_payment_method
-- ----------------------------
INSERT INTO `ai_relay_provider_payment_method` VALUES (1, 1, 4, 'PayPal主流', 10, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_payment_method` VALUES (2, 1, 5, 'USDT (TRC20/ERC20)', 20, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_payment_method` VALUES (3, 1, 6, 'BTC/ETH', 30, 0, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_payment_method` VALUES (4, 2, 1, '支付宝扫码', 10, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_payment_method` VALUES (5, 2, 2, '微信支付', 20, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_payment_method` VALUES (6, 2, 3, '银联卡/信用卡', 30, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_payment_method` VALUES (7, 3, 1, '支付宝', 10, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_payment_method` VALUES (8, 3, 2, '微信支付', 20, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_payment_method` VALUES (9, 4, 1, '支付宝', 10, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_payment_method` VALUES (10, 4, 2, '微信支付', 20, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_payment_method` VALUES (11, 5, 4, 'PayPal捐赠', 10, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');
INSERT INTO `ai_relay_provider_payment_method` VALUES (12, 5, 5, 'USDT捐赠', 20, 1, '2026-05-29 05:49:11', '2026-05-29 05:49:11');

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
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转服务商个人充值记录（用于佐证推荐真实性）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_provider_recharge
-- ----------------------------
INSERT INTO `ai_relay_provider_recharge` VALUES (1, 1, NULL, 100.00, 'CNY', NULL, 100.00, NULL, '2026-05-29 17:10:28', NULL, NULL, NULL, 1, '2026-05-29 17:10:36', '2026-05-29 17:10:36');

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
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转服务商个人推荐及测评（推荐均为本人实际使用并充值）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_provider_recommend
-- ----------------------------
INSERT INTO `ai_relay_provider_recommend` VALUES (1, 1, '全球最全模型聚合，开发者出海首选', '## 深度测评 OpenRouter\n\n### 使用背景\n本人从2025年3月开始使用OpenRouter，累计充值$200，调用超500万Token。\n\n### 优点详情\n- **模型数量**：确实是最多的，连一些小众模型（如Cohere、AI21）都有。\n- **API稳定性**：高峰期偶尔会有延迟，但总体可用性99.5%以上。\n- **价格透明**：每个模型的计费标准明确，支持按Token或按请求计费。\n\n### 缺点/坑点\n- **国内访问困难**：不挂代理经常超时，建议配合Cloudflare Workers代理。\n- **客服响应慢**：免费用户工单回复需2-3天。\n\n### 最佳场景\n- 需要同时对比多个模型效果的研究人员\n- 面向海外用户的AI应用开发者\n- 希望避免供应商锁定，随时切换模型的团队\n\n### 充值建议\n首次可先充$10测试，稳定后充$50或$200（$200档有赠送）。', 9.2, '模型极全；文档规范；按量付费灵活；支持充值赠送', '国内需代理；客服响应慢；部分模型不支持流式输出', '多模型对比测试；海外应用集成；研究和学术用途', '2025-03-10 10:00:00', '2026-05-20 14:30:00', '2026-05-20 14:30:00', 10, 1, '2026-05-29 09:08:58', '2026-05-29 09:08:58');
INSERT INTO `ai_relay_provider_recommend` VALUES (2, 2, '国内直连体验极佳，企业级稳定首选', '## AIProxy 充值使用测评\n\n### 使用时长\n2025年8月至今，累计充值￥800，企业级账号。\n\n### 实测表现\n- **网络延迟**：从北京服务器调用，平均延迟85ms，无丢包。\n- **并发能力**：支持100 QPS稳定，更高需单独申请。\n- **模型可用性**：GPT-4o、Claude 3.5 Sonnet均有，更新快。\n\n### 企业功能\n- 提供用量报表和API Key权限分级。\n- 支持设置预算告警和自动充值。\n- 可签署NDA和数据保护协议。\n\n### 不足\n- 价格比纯按量略高（毕竟有国内加速成本）。\n- 不提供免费额度，最低充值￥50。\n\n### 适用对象\n推荐给国内中小企业和需要稳定直连的开发者。', 9.5, '国内直连超快；企业级SLA；发票齐全；技术支持响应及时', '价格略高于海外直充；无免费试用额度', '国内企业AI应用；需要合规发票的项目；对延迟敏感的场景', '2025-08-01 09:00:00', '2026-05-25 11:20:00', '2026-05-25 11:20:00', 9, 1, '2026-05-29 09:08:58', '2026-05-29 09:08:58');
INSERT INTO `ai_relay_provider_recommend` VALUES (3, 3, '性价比之王，个人开发者和小团队闭眼入', '## NexusAI 性价比分析\n\n### 使用背景\n个人项目使用，已购买3次月卡标准版。\n\n### 价格对比\n- 月卡79元含1000万Token，赠送200万，实际1200万Token。\n- 对比OpenRouter相同模型（GPT-4o）便宜约40%。\n\n### 模型质量\nGPT-4o和Claude 3.5 Sonnet效果与官方一致，未发现降智。\n\n### 需要改进的地方\n- 控制台偶尔卡顿，刷新可解决。\n- 文档更新滞后于新模型上线。\n\n### 适合人群\n- 预算有限的个人开发者\n- 每天调用量10万Token以内的项目\n- 对模型速度要求不苛刻的ChatBot应用', 8.8, '价格极低；套餐灵活；新用户送额度；中文支持好', '控制台体验一般；文档更新慢；不提供企业发票', '个人学习实验；创业公司MVP产品；内容生成批处理', '2026-01-15 10:00:00', '2026-05-26 16:00:00', '2026-05-26 16:00:00', 8, 1, '2026-05-29 09:08:58', '2026-05-29 09:08:58');
INSERT INTO `ai_relay_provider_recommend` VALUES (4, 4, '轻度使用者的福音，10元就能玩转GPT-4', '## API2D 真实体验\n\n### 使用概况\n我属于偶尔调用API（每月不超过50万Token），API2D的按量包最适合我。\n\n### 支付体验\n支付宝扫码后秒到账，不需要绑卡或上传证件，隐私友好。\n\n### 接入便捷度\n提供了Python/JS/Java等SDK，5分钟接入成功。\n\n### 模型支持\n主流的GPT-4o、Claude 3、DeepSeek都有，但缺少部分最新模型（如GPT-4o mini当时晚了2周）。\n\n### 缺点\n- 没有套餐的用量统计图表，只有总余额。\n- 客服仅限邮件，响应较慢。\n\n### 推荐场景\n- 学生做作业/毕业设计\n- 自媒体偶尔生成文案\n- 技术爱好者测试prompt', 8.5, '充值门槛极低；支付便捷；接入简单', '缺乏企业级功能；新模型上线慢；客服响应慢', '个人学习；原型验证；低频调用场景', '2025-12-01 14:00:00', '2026-05-24 09:30:00', '2026-05-24 09:30:00', 7, 1, '2026-05-29 09:08:58', '2026-05-29 09:08:58');
INSERT INTO `ai_relay_provider_recommend` VALUES (5, 5, '私有化部署必备，统一管理所有AI Key的最佳选择', '## OneAPI 自部署深度评测\n\n### 我的部署环境\n腾讯云轻量服务器（2C4G），Docker一键部署，已稳定运行3个月。\n\n### 功能亮点\n- **渠道管理**：可以添加多个API服务商（OpenAI、Azure、OpenRouter等）作为上游。\n- **令牌管理**：为不同项目生成独立的API Key，可设置额度、过期时间。\n- **日志统计**：详细记录每次调用的Token和费用，支持导出。\n\n### 稳定性表现\n单节点每日处理约2万次请求，从未崩溃。如需高可用可加负载均衡。\n\n### 缺点\n- 没有官方托管版，必须自己运维。\n- 初次配置稍微复杂（需要修改配置文件）。\n- 不支持按Token计费的细粒度控费（只能设置总次数或总金额）。\n\n### 适合用户\n- 有自建机房或云服务器的企业\n- 希望统一管控多供应商Key的团队\n- 对数据隐私要求极高的项目', 9.0, '开源免费；功能强大；支持多供应商；社区活跃', '需要自建和维护；高级功能需二次开发', '企业内部AI网关；多供应商统一接入；数据合规场景', '2026-02-10 15:00:00', '2026-05-28 10:00:00', '2026-05-28 10:00:00', 6, 1, '2026-05-29 09:08:58', '2026-05-29 09:08:58');

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
) ENGINE = InnoDB AUTO_INCREMENT = 41 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客分类表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of blog_category
-- ----------------------------
INSERT INTO `blog_category` VALUES (1, '前端开发', 'frontend', '前端技术相关，包括HTML/CSS/JavaScript等', NULL, 10, '2026-04-29 09:31:39', '2026-04-29 09:31:39');
INSERT INTO `blog_category` VALUES (2, '后端开发', 'backend', '后端技术，包括Python/Java/Go等', NULL, 20, '2026-04-29 09:31:39', '2026-04-29 09:31:39');
INSERT INTO `blog_category` VALUES (3, '数据库', 'database', '数据库技术，MySQL/Redis/MongoDB等', NULL, 30, '2026-04-29 09:31:39', '2026-04-29 09:31:39');
INSERT INTO `blog_category` VALUES (4, 'DevOps', 'devops', '开发运维，Docker/K8s/CI-CD等', NULL, 40, '2026-04-29 09:31:39', '2026-04-29 09:31:39');
INSERT INTO `blog_category` VALUES (5, 'AI与机器学习', 'ai-ml', '人工智能、机器学习、深度学习', NULL, 50, '2026-04-29 09:31:39', '2026-04-29 09:31:39');
INSERT INTO `blog_category` VALUES (6, '移动开发', 'mobile', 'iOS/Android/跨平台开发', NULL, 60, '2026-04-29 09:31:39', '2026-04-29 09:31:39');
INSERT INTO `blog_category` VALUES (7, '云计算', 'cloud', '云服务/AWS/阿里云/Azure', NULL, 70, '2026-04-29 09:31:39', '2026-04-29 09:31:39');
INSERT INTO `blog_category` VALUES (8, '安全', 'security', '网络安全、渗透测试、加密技术', NULL, 80, '2026-04-29 09:31:39', '2026-04-29 09:31:39');
INSERT INTO `blog_category` VALUES (9, 'Vue.js', 'vuejs', 'Vue.js 框架相关', 1, 10, '2026-04-29 09:31:39', '2026-04-29 09:31:39');
INSERT INTO `blog_category` VALUES (10, 'React', 'react', 'React 框架相关', 1, 20, '2026-04-29 09:31:39', '2026-04-29 09:31:39');
INSERT INTO `blog_category` VALUES (11, 'Angular', 'angular', 'Angular 框架相关', 1, 30, '2026-04-29 09:31:39', '2026-04-29 09:31:39');
INSERT INTO `blog_category` VALUES (12, 'JavaScript', 'javascript', '原生JavaScript教程', 1, 40, '2026-04-29 09:31:39', '2026-04-29 09:31:39');
INSERT INTO `blog_category` VALUES (13, 'TypeScript', 'typescript', 'TypeScript语言', 1, 50, '2026-04-29 09:31:39', '2026-04-29 09:31:39');
INSERT INTO `blog_category` VALUES (14, 'CSS/Tailwind', 'css', 'CSS样式和Tailwind框架', 1, 60, '2026-04-29 09:31:39', '2026-04-29 09:31:39');
INSERT INTO `blog_category` VALUES (15, 'Python', 'python', 'Python后端开发', 2, 10, '2026-04-29 09:31:39', '2026-04-29 09:31:39');
INSERT INTO `blog_category` VALUES (16, 'Java', 'java', 'Java/Spring Boot', 2, 20, '2026-04-29 09:31:39', '2026-04-29 09:31:39');
INSERT INTO `blog_category` VALUES (17, 'Go', 'golang', 'Go语言开发', 2, 30, '2026-04-29 09:31:39', '2026-04-29 09:31:39');
INSERT INTO `blog_category` VALUES (18, 'Node.js', 'nodejs', 'Node.js后端开发', 2, 40, '2026-04-29 09:31:39', '2026-04-29 09:31:39');
INSERT INTO `blog_category` VALUES (19, 'PHP', 'php', 'PHP开发', 2, 50, '2026-04-29 09:31:39', '2026-04-29 09:31:39');
INSERT INTO `blog_category` VALUES (20, 'Rust', 'rust', 'Rust语言', 2, 60, '2026-04-29 09:31:39', '2026-04-29 09:31:39');
INSERT INTO `blog_category` VALUES (21, 'MySQL', 'mysql', 'MySQL关系型数据库', 3, 10, '2026-04-29 09:31:40', '2026-04-29 09:31:40');
INSERT INTO `blog_category` VALUES (22, 'PostgreSQL', 'postgresql', 'PostgreSQL教程', 3, 20, '2026-04-29 09:31:40', '2026-04-29 09:31:40');
INSERT INTO `blog_category` VALUES (23, 'Redis', 'redis', 'Redis缓存数据库', 3, 30, '2026-04-29 09:31:40', '2026-04-29 09:31:40');
INSERT INTO `blog_category` VALUES (24, 'MongoDB', 'mongodb', 'MongoDB NoSQL数据库', 3, 40, '2026-04-29 09:31:40', '2026-04-29 09:31:40');
INSERT INTO `blog_category` VALUES (25, 'ClickHouse', 'clickhouse', '列式存储数据库', 3, 50, '2026-04-29 09:31:40', '2026-04-29 09:31:40');
INSERT INTO `blog_category` VALUES (26, 'Elasticsearch', 'elasticsearch', '搜索引擎', 3, 60, '2026-04-29 09:31:40', '2026-04-29 09:31:40');
INSERT INTO `blog_category` VALUES (27, 'Docker', 'docker', '容器化技术', 4, 10, '2026-04-29 09:31:40', '2026-04-29 09:31:40');
INSERT INTO `blog_category` VALUES (28, 'Kubernetes', 'kubernetes', 'K8s容器编排', 4, 20, '2026-04-29 09:31:40', '2026-04-29 09:31:40');
INSERT INTO `blog_category` VALUES (29, 'Jenkins', 'jenkins', 'CI/CD持续集成', 4, 30, '2026-04-29 09:31:40', '2026-04-29 09:31:40');
INSERT INTO `blog_category` VALUES (30, 'Git/GitHub', 'git', '版本控制', 4, 40, '2026-04-29 09:31:40', '2026-04-29 09:31:40');
INSERT INTO `blog_category` VALUES (31, 'Ansible', 'ansible', '自动化运维', 4, 50, '2026-04-29 09:31:40', '2026-04-29 09:31:40');
INSERT INTO `blog_category` VALUES (32, 'Terraform', 'terraform', '基础设施即代码', 4, 60, '2026-04-29 09:31:40', '2026-04-29 09:31:40');
INSERT INTO `blog_category` VALUES (33, '机器学习', 'machine-learning', '机器学习算法', 5, 10, '2026-04-29 09:31:40', '2026-04-29 09:31:40');
INSERT INTO `blog_category` VALUES (34, '深度学习', 'deep-learning', '神经网络与深度学习', 5, 20, '2026-04-29 09:31:40', '2026-04-29 09:31:40');
INSERT INTO `blog_category` VALUES (35, 'NLP自然语言', 'nlp', '自然语言处理', 5, 30, '2026-04-29 09:31:40', '2026-04-29 09:31:40');
INSERT INTO `blog_category` VALUES (36, '计算机视觉', 'computer-vision', '图像识别与处理', 5, 40, '2026-04-29 09:31:40', '2026-04-29 09:31:40');
INSERT INTO `blog_category` VALUES (37, 'PyTorch', 'pytorch', 'PyTorch框架', 5, 50, '2026-04-29 09:31:40', '2026-04-29 09:31:40');
INSERT INTO `blog_category` VALUES (38, 'TensorFlow', 'tensorflow', 'TensorFlow框架', 5, 60, '2026-04-29 09:31:40', '2026-04-29 09:31:40');
INSERT INTO `blog_category` VALUES (39, 'LLM大模型', 'llm', '大语言模型应用', 5, 70, '2026-04-29 09:31:40', '2026-04-29 09:31:40');

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
) ENGINE = InnoDB AUTO_INCREMENT = 23 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客内容版本表（完整快照）' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of blog_content_version
-- ----------------------------
INSERT INTO `blog_content_version` VALUES (13, 2, 13, '完整的流程图生成工作流（含节点自动生成）', NULL, 16, 11, 'published', 'public', 'manual', NULL, 2052290101098295297, '2026-05-25 17:13:01');
INSERT INTO `blog_content_version` VALUES (14, 2, 14, '完整的流程图生成工作流（含节点自动生成）', NULL, 17, 11, 'published', 'public', 'manual', NULL, 2052290101098295297, '2026-05-25 17:13:17');
INSERT INTO `blog_content_version` VALUES (15, 2, 15, '完整的流程图生成工作流（含节点自动生成）', NULL, 17, 11, 'published', 'public', 'manual', NULL, 2052290101098295297, '2026-05-25 17:13:40');
INSERT INTO `blog_content_version` VALUES (16, 2, 16, '完整的流程图生成工作流（含节点自动生成）', NULL, 17, 11, 'published', 'public', 'manual', NULL, 2052290101098295297, '2026-05-25 17:16:30');
INSERT INTO `blog_content_version` VALUES (17, 2, 17, '完整的流程图生成工作流（含节点自动生成）', NULL, 17, 11, 'published', 'public', 'manual', NULL, 2052290101098295297, '2026-05-25 17:16:34');
INSERT INTO `blog_content_version` VALUES (18, 2, 18, '完整的流程图生成工作流（含节点自动生成）', NULL, 18, 11, 'published', 'public', 'manual', NULL, 2052290101098295297, '2026-05-25 17:16:51');
INSERT INTO `blog_content_version` VALUES (19, 2, 19, '完整的流程图生成工作流（含节点自动生成）', NULL, 18, 11, 'published', 'public', 'manual', NULL, 2052290101098295297, '2026-05-25 17:20:05');
INSERT INTO `blog_content_version` VALUES (20, 2, 20, '完整的流程图生成工作流（含节点自动生成）', NULL, 19, 11, 'published', 'public', 'manual', NULL, 2052290101098295297, '2026-05-25 17:20:55');
INSERT INTO `blog_content_version` VALUES (21, 2, 21, '完整的流程图生成工作流（含节点自动生成）', NULL, 20, 11, 'published', 'public', 'manual', NULL, 2052290101098295297, '2026-05-25 17:45:00');
INSERT INTO `blog_content_version` VALUES (22, 2, 22, '完整的流程图生成工作流（含节点自动生成）', NULL, 21, 11, 'published', 'public', 'manual', NULL, 2052290101098295297, '2026-05-25 17:47:58');

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
) ENGINE = InnoDB AUTO_INCREMENT = 32 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客文件资源表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of blog_file_asset
-- ----------------------------
INSERT INTO `blog_file_asset` VALUES (1, 'oss', 'flux-note', 'posts/2026/05/25/c2565c14b7c549cb94224ce73e1650a7.md', 'http://140.143.222.164/flux-note/posts/2026/05/25/c2565c14b7c549cb94224ce73e1650a7.md', '111.md', '.md', 'text/markdown', 7, 'cc7435135e6b6be6bc04d48051bed83fb9014525c1d13ff9149c28118cedccbe', 'markdown', '2026-05-25 15:23:23', '2026-05-25 15:23:23');
INSERT INTO `blog_file_asset` VALUES (2, 'oss', 'flux-note', 'covers/2026/05/25/a5c932b9820140eaa47911b6075a383f.jpg', 'http://140.143.222.164/flux-note/covers/2026/05/25/a5c932b9820140eaa47911b6075a383f.jpg', '0bf36cb28ce57256fae7f6f73f67bf5c.jpg', '.jpg', 'image/jpeg', 504465, 'a0e7ae88b7094c314ec24e402e3d2baff062ed24291b191c3e17164b5d240752', 'cover', '2026-05-25 15:46:36', '2026-05-25 15:46:36');
INSERT INTO `blog_file_asset` VALUES (3, 'oss', 'flux-note', 'covers/2026/05/25/d96e6a7b1ac24d14acc1d5b9c8b1937a.jpg', 'http://140.143.222.164/flux-note/covers/2026/05/25/d96e6a7b1ac24d14acc1d5b9c8b1937a.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=admin%2F20260525%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20260525T080713Z&X-Amz-Expires=3600&X-Amz-SignedHeaders=host&X-Amz-Signature=581f51c4f02a4a1cb27b2e3b0edaa106abe79d93a21a7c6c8082e7d2f1b83159', '0bf36cb28ce57256fae7f6f73f67bf5c.jpg', '.jpg', 'image/jpeg', 504465, 'a0e7ae88b7094c314ec24e402e3d2baff062ed24291b191c3e17164b5d240752', 'cover', '2026-05-25 16:07:14', '2026-05-25 16:07:14');
INSERT INTO `blog_file_asset` VALUES (11, 'oss', 'flux-note', 'covers/2026/05/25/8698e33eb3ae412db9ad623a999d8371.jpg', 'http://140.143.222.164/flux-note/covers/2026/05/25/8698e33eb3ae412db9ad623a999d8371.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=admin%2F20260525%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20260525T085616Z&X-Amz-Expires=3600&X-Amz-SignedHeaders=host&X-Amz-Signature=e3a9d001231c2bd79b1075b1f0815f2e941fb58d3373b309c84e3a99a1485f06', '0bf36cb28ce57256fae7f6f73f67bf5c.jpg', '.jpg', 'image/jpeg', 504465, 'a0e7ae88b7094c314ec24e402e3d2baff062ed24291b191c3e17164b5d240752', 'cover', '2026-05-25 16:56:16', '2026-05-25 16:56:16');
INSERT INTO `blog_file_asset` VALUES (17, 'oss', 'flux-note', 'posts/2026/05/25/315991fbe6c7470aa389e634b0ff0d8b.md', 'http://140.143.222.164/flux-note/posts/2026/05/25/315991fbe6c7470aa389e634b0ff0d8b.md', 'post-mpkxbqf9.md', '.md', 'text/markdown', 23447, 'eb8f5e47614fc564ed4aba6a2e2922c14e1e728e260325c0ec0b80af6e46498d', 'markdown', '2026-05-25 17:13:01', '2026-05-25 17:13:01');
INSERT INTO `blog_file_asset` VALUES (18, 'oss', 'flux-note', 'posts/2026/05/25/f268b781634e4b708d4de61b355a511c.md', 'http://140.143.222.164/flux-note/posts/2026/05/25/f268b781634e4b708d4de61b355a511c.md', 'post-mpkxbqf9.md', '.md', 'text/markdown', 23447, 'eb8f5e47614fc564ed4aba6a2e2922c14e1e728e260325c0ec0b80af6e46498d', 'markdown', '2026-05-25 17:16:34', '2026-05-25 17:16:34');
INSERT INTO `blog_file_asset` VALUES (19, 'oss', 'flux-note', 'posts/2026/05/25/509736ee16ae419c9c11a82bf28d2de4.md', 'http://140.143.222.164/flux-note/posts/2026/05/25/509736ee16ae419c9c11a82bf28d2de4.md', 'post-mpkxbqf9.md', '.md', 'text/markdown', 23447, 'eb8f5e47614fc564ed4aba6a2e2922c14e1e728e260325c0ec0b80af6e46498d', 'markdown', '2026-05-25 17:20:05', '2026-05-25 17:20:05');
INSERT INTO `blog_file_asset` VALUES (20, 'oss', 'flux-note', 'posts/2026/05/25/5c119f372acb47e8be19b433310b5e3f.md', 'http://140.143.222.164/flux-note/posts/2026/05/25/5c119f372acb47e8be19b433310b5e3f.md', 'post-mpkxbqf9.md', '.md', 'text/markdown', 23447, 'eb8f5e47614fc564ed4aba6a2e2922c14e1e728e260325c0ec0b80af6e46498d', 'markdown', '2026-05-25 17:20:56', '2026-05-25 17:20:56');
INSERT INTO `blog_file_asset` VALUES (21, 'oss', 'flux-note', 'posts/2026/05/25/6d3ede62cd7f4a47a16a5febec733aa2.md', 'http://140.143.222.164/flux-note/posts/2026/05/25/6d3ede62cd7f4a47a16a5febec733aa2.md', 'post-mpkxbqf9.md', '.md', 'text/markdown', 23447, 'eb8f5e47614fc564ed4aba6a2e2922c14e1e728e260325c0ec0b80af6e46498d', 'markdown', '2026-05-25 17:45:01', '2026-05-25 17:45:01');
INSERT INTO `blog_file_asset` VALUES (22, 'oss', 'flux-note', 'posts/2026/05/25/bbaf264dde7f4326bcf3ae5e3379a78b.md', 'http://140.143.222.164/flux-note/posts/2026/05/25/bbaf264dde7f4326bcf3ae5e3379a78b.md', 'post-mpkxbqf9.md', '.md', 'text/markdown', 23447, 'eb8f5e47614fc564ed4aba6a2e2922c14e1e728e260325c0ec0b80af6e46498d', 'markdown', '2026-05-25 17:47:59', '2026-05-25 17:47:59');
INSERT INTO `blog_file_asset` VALUES (23, 'oss', 'flux-note', 'posts/2026/05/26/8282142d87d445a29d6ecfcc3765a37d.md', 'http://140.143.222.164/flux-note/posts/2026/05/26/8282142d87d445a29d6ecfcc3765a37d.md', 'essay-mpmdfybc.md', '.md', 'text/markdown', 655, '61bd63abf6736de087e95d6d2245136e573e77e2cfa3f4079635479b53a8c308', 'markdown', '2026-05-26 16:26:44', '2026-05-26 16:26:44');
INSERT INTO `blog_file_asset` VALUES (24, 'oss', 'flux-note', 'covers/2026/05/27/1a4d16e9119948bd960adb0cfdcfdee5.jpg', 'http://140.143.222.164/flux-note/covers/2026/05/27/1a4d16e9119948bd960adb0cfdcfdee5.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=admin%2F20260527%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20260527T113508Z&X-Amz-Expires=3600&X-Amz-SignedHeaders=host&X-Amz-Signature=1e0a054b63aef33ee3ada1b44f81ec1fe6b9329581a5f2f28561b929dde5da0c', '0bf36cb28ce57256fae7f6f73f67bf5c.jpg', '.jpg', 'image/jpeg', 504465, 'a0e7ae88b7094c314ec24e402e3d2baff062ed24291b191c3e17164b5d240752', 'cover', '2026-05-27 19:35:09', '2026-05-27 19:35:09');
INSERT INTO `blog_file_asset` VALUES (25, 'oss', 'flux-note', 'covers/2026/05/27/408fb6db59f341229f4c288a8d7ff4a1.jpg', 'http://140.143.222.164/flux-note/covers/2026/05/27/408fb6db59f341229f4c288a8d7ff4a1.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=admin%2F20260527%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20260527T113748Z&X-Amz-Expires=3600&X-Amz-SignedHeaders=host&X-Amz-Signature=5ecdf25d03c00568bce46a8ecde0484ea0deee337f8921740731fd2e57c779f1', '0bf36cb28ce57256fae7f6f73f67bf5c.jpg', '.jpg', 'image/jpeg', 504465, 'a0e7ae88b7094c314ec24e402e3d2baff062ed24291b191c3e17164b5d240752', 'cover', '2026-05-27 19:37:48', '2026-05-27 19:37:48');
INSERT INTO `blog_file_asset` VALUES (26, 'oss', 'flux-note', 'covers/2026/05/27/ee0c225639e9468b96d9061bae8d40f9.jpg', 'http://140.143.222.164/flux-note/covers/2026/05/27/ee0c225639e9468b96d9061bae8d40f9.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=admin%2F20260527%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20260527T113847Z&X-Amz-Expires=3600&X-Amz-SignedHeaders=host&X-Amz-Signature=5093a785e1423baac921fa1a4765acab4e4c7ad3516d3465a200bd61e67b8b1e', 'images.jpg', '.jpg', 'image/jpeg', 14888, '4f78055f18d7dd4a70d8a8c8f86437e9190cf02e2420dadf8218efb956a2c00a', 'cover', '2026-05-27 19:38:48', '2026-05-27 19:38:48');
INSERT INTO `blog_file_asset` VALUES (27, 'oss', 'flux-note', 'images/2026/05/27/7aa418a8824e4173983c7ffcffb2450e.jpg', 'http://140.143.222.164/flux-note/images/2026/05/27/7aa418a8824e4173983c7ffcffb2450e.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=admin%2F20260527%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20260527T121426Z&X-Amz-Expires=3600&X-Amz-SignedHeaders=host&X-Amz-Signature=521d95c9520c7262f38048eadbeebd53cdda9e1fe4cab94bc1ea72f29ad21785', 'images.jpg', '.jpg', 'image/jpeg', 14888, '4f78055f18d7dd4a70d8a8c8f86437e9190cf02e2420dadf8218efb956a2c00a', 'image', '2026-05-27 20:14:26', '2026-05-27 20:14:26');
INSERT INTO `blog_file_asset` VALUES (28, 'oss', 'flux-note', 'images/2026/05/27/6abd7373abf04df6b613a453bea4583e.jpg', 'http://140.143.222.164/flux-note/images/2026/05/27/6abd7373abf04df6b613a453bea4583e.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=admin%2F20260527%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20260527T121429Z&X-Amz-Expires=3600&X-Amz-SignedHeaders=host&X-Amz-Signature=6358ed8a693474da021dff4fa53d4a5c102144a2bb2ce44724d2486ec9c5f600', 'images.jpg', '.jpg', 'image/jpeg', 14888, '4f78055f18d7dd4a70d8a8c8f86437e9190cf02e2420dadf8218efb956a2c00a', 'image', '2026-05-27 20:14:29', '2026-05-27 20:14:29');
INSERT INTO `blog_file_asset` VALUES (29, 'oss', 'flux-note', 'images/2026/05/27/2125cc3d5fd24e12add3d96865e2a2f5.jpg', 'http://140.143.222.164/flux-note/images/2026/05/27/2125cc3d5fd24e12add3d96865e2a2f5.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=admin%2F20260527%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20260527T121520Z&X-Amz-Expires=3600&X-Amz-SignedHeaders=host&X-Amz-Signature=dcb773ff81817b8b7965abf766b0f710bb008739aabc0409d9ffcee53f6dc491', 'images.jpg', '.jpg', 'image/jpeg', 14888, '4f78055f18d7dd4a70d8a8c8f86437e9190cf02e2420dadf8218efb956a2c00a', 'image', '2026-05-27 20:15:21', '2026-05-27 20:15:21');
INSERT INTO `blog_file_asset` VALUES (30, 'oss', 'flux-note', 'images/2026/05/27/1b357c2949d04bdf82697057c7a09265.jpg', 'http://140.143.222.164/flux-note/images/2026/05/27/1b357c2949d04bdf82697057c7a09265.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=admin%2F20260527%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20260527T122034Z&X-Amz-Expires=3600&X-Amz-SignedHeaders=host&X-Amz-Signature=b331f3181340e50f6d6e6cd3928e2060a737012db0f9d8a1211d853ed9020c7b', 'images.jpg', '.jpg', 'image/jpeg', 14888, '4f78055f18d7dd4a70d8a8c8f86437e9190cf02e2420dadf8218efb956a2c00a', 'image', '2026-05-27 20:20:34', '2026-05-27 20:20:34');
INSERT INTO `blog_file_asset` VALUES (31, 'oss', 'flux-note', 'covers/2026/05/28/4ac465db24d844c4985a8ba769f58fdb.jpg', 'http://140.143.222.164/flux-note/covers/2026/05/28/4ac465db24d844c4985a8ba769f58fdb.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=admin%2F20260528%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20260528T031831Z&X-Amz-Expires=3600&X-Amz-SignedHeaders=host&X-Amz-Signature=1b2588b1b322089250ad7541de2c95749eda5cfeaa408ae40ef5abba41d00450', 'images.jpg', '.jpg', 'image/jpeg', 14888, '4f78055f18d7dd4a70d8a8c8f86437e9190cf02e2420dadf8218efb956a2c00a', 'cover', '2026-05-28 11:18:32', '2026-05-28 11:18:32');

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
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客文章主表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of blog_post
-- ----------------------------
INSERT INTO `blog_post` VALUES (2, 2052290101098295297, 'article', '完整的流程图生成工作流（含节点自动生成）', 'post-mpkxbqf9', NULL, 22, NULL, 'published', 'public', 'manual', 1, 33, 0, '2026-05-25 08:36:23', '2026-05-25 16:07:49', '2026-05-28 05:46:05');
INSERT INTO `blog_post` VALUES (3, 2052290101098295297, 'essay', '夜雨孤灯', 'essay-mpmdfybc', NULL, 23, NULL, 'published', 'public', 'manual', 1, 27, 0, '2026-05-26 08:26:39', '2026-05-26 16:26:44', '2026-05-28 05:51:04');

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
INSERT INTO `blog_post_category` VALUES (2, 1);

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
) ENGINE = InnoDB AUTO_INCREMENT = 11 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客搜索索引同步任务表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of blog_search_index_task
-- ----------------------------
INSERT INTO `blog_search_index_task` VALUES (1, 2, 'nebula_blog_posts', 'upsert', 'failed', 0, 'Failed making field \'java.time.LocalDateTime#date\' accessible; either increase its visibility or write a custom TypeAdapter for its declaring type.\nSee https://github.com/google/gson/blob/main/Troubleshooting.md#reflection-inaccessible', '2026-05-25 16:36:27', '2026-05-25 16:36:27', NULL);
INSERT INTO `blog_search_index_task` VALUES (2, 2, 'nebula_blog_posts', 'upsert', 'failed', 0, 'Failed making field \'java.time.LocalDateTime#date\' accessible; either increase its visibility or write a custom TypeAdapter for its declaring type.\nSee https://github.com/google/gson/blob/main/Troubleshooting.md#reflection-inaccessible', '2026-05-25 16:38:36', '2026-05-25 16:38:36', NULL);
INSERT INTO `blog_search_index_task` VALUES (3, 2, 'nebula_blog_posts', 'upsert', 'failed', 0, 'Failed making field \'java.time.LocalDateTime#date\' accessible; either increase its visibility or write a custom TypeAdapter for its declaring type.\nSee https://github.com/google/gson/blob/main/Troubleshooting.md#reflection-inaccessible', '2026-05-25 16:56:22', '2026-05-25 16:56:22', NULL);
INSERT INTO `blog_search_index_task` VALUES (4, 2, 'nebula_blog_posts', 'upsert', 'failed', 0, 'Failed making field \'java.time.LocalDateTime#date\' accessible; either increase its visibility or write a custom TypeAdapter for its declaring type.\nSee https://github.com/google/gson/blob/main/Troubleshooting.md#reflection-inaccessible', '2026-05-25 17:02:39', '2026-05-25 17:02:39', NULL);
INSERT INTO `blog_search_index_task` VALUES (5, 2, 'nebula_blog_posts', 'upsert', 'failed', 0, 'Failed making field \'java.time.LocalDateTime#date\' accessible; either increase its visibility or write a custom TypeAdapter for its declaring type.\nSee https://github.com/google/gson/blob/main/Troubleshooting.md#reflection-inaccessible', '2026-05-25 17:08:19', '2026-05-25 17:08:19', NULL);
INSERT INTO `blog_search_index_task` VALUES (6, 2, 'nebula_blog_posts', 'upsert', 'failed', 0, 'Failed making field \'java.time.LocalDateTime#date\' accessible; either increase its visibility or write a custom TypeAdapter for its declaring type.\nSee https://github.com/google/gson/blob/main/Troubleshooting.md#reflection-inaccessible', '2026-05-25 17:08:29', '2026-05-25 17:08:29', NULL);
INSERT INTO `blog_search_index_task` VALUES (7, 2, 'nebula_blog_posts', 'upsert', 'failed', 0, 'Failed making field \'java.time.LocalDateTime#date\' accessible; either increase its visibility or write a custom TypeAdapter for its declaring type.\nSee https://github.com/google/gson/blob/main/Troubleshooting.md#reflection-inaccessible', '2026-05-25 17:12:59', '2026-05-25 17:12:59', NULL);
INSERT INTO `blog_search_index_task` VALUES (8, 2, 'nebula_blog_posts', 'upsert', 'failed', 0, 'Failed making field \'java.time.LocalDateTime#date\' accessible; either increase its visibility or write a custom TypeAdapter for its declaring type.\nSee https://github.com/google/gson/blob/main/Troubleshooting.md#reflection-inaccessible', '2026-05-25 17:13:01', '2026-05-25 17:13:01', NULL);
INSERT INTO `blog_search_index_task` VALUES (9, 2, 'nebula_blog_posts', 'upsert', 'failed', 0, 'Failed making field \'java.time.LocalDateTime#date\' accessible; either increase its visibility or write a custom TypeAdapter for its declaring type.\nSee https://github.com/google/gson/blob/main/Troubleshooting.md#reflection-inaccessible', '2026-05-25 17:13:18', '2026-05-25 17:13:18', NULL);
INSERT INTO `blog_search_index_task` VALUES (10, 2, 'nebula_blog_posts', 'upsert', 'failed', 0, 'Failed making field \'java.time.LocalDateTime#date\' accessible; either increase its visibility or write a custom TypeAdapter for its declaring type.\nSee https://github.com/google/gson/blob/main/Troubleshooting.md#reflection-inaccessible', '2026-05-25 17:13:40', '2026-05-25 17:13:40', NULL);

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
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客系列表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_series
-- ----------------------------
INSERT INTO `blog_series` VALUES (1, 'Redis', 'redis', NULL, NULL, 'published', 'public', 0, 0, 2052290101098295297, '2026-05-27 11:31:55', '2026-05-27 11:31:55');
INSERT INTO `blog_series` VALUES (2, 'Mysql', 'mysql', NULL, 31, 'published', 'public', 0, 0, 2052290101098295297, '2026-05-28 11:18:33', '2026-05-28 11:18:33');

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
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系列目录节点表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_series_catalog
-- ----------------------------
INSERT INTO `blog_series_catalog` VALUES (1, 1, NULL, 'Redis', 0, NULL, '_blank', '/1/', 0, 0, 1, '2026-05-27 13:35:52', '2026-05-27 13:35:52');
INSERT INTO `blog_series_catalog` VALUES (4, 1, 1, '初始redis', 1, NULL, '_blank', '/1/4/', 1, 0, 0, '2026-05-28 11:16:20', '2026-05-28 11:16:20');
INSERT INTO `blog_series_catalog` VALUES (5, 1, NULL, 'Mysql', 0, NULL, '_blank', '/5/', 0, 0, 1, '2026-05-28 11:17:09', '2026-05-28 11:17:09');
INSERT INTO `blog_series_catalog` VALUES (6, 1, 5, '初始Mysql', 1, NULL, '_blank', '/5/6/', 1, 0, 0, '2026-05-28 11:17:30', '2026-05-28 11:17:30');

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
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '目录文章关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_series_catalog_post
-- ----------------------------
INSERT INTO `blog_series_catalog_post` VALUES (3, 6, 2, 0, 0, '2026-05-28 11:17:39');
INSERT INTO `blog_series_catalog_post` VALUES (5, 4, 3, 0, 0, '2026-05-28 13:43:44');

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
) ENGINE = InnoDB AUTO_INCREMENT = 24 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客标签表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of blog_tag
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
INSERT INTO `sys_file` VALUES (2059841760028413954, '', 0, 'image', 'minio', NULL, 'nebula-system', 'image/2026/05/28/a1990375100b4e4aad23e07d99734ddb.jpg', 'http://140.143.222.164/nebula-system/image/2026/05/28/a1990375100b4e4aad23e07d99734ddb.jpg', 'images.jpg', 'a1990375100b4e4aad23e07d99734ddb.jpg', 'jpg', 'image/jpeg', 14888, NULL, NULL, NULL, '4f78055f18d7dd4a70d8a8c8f86437e9190cf02e2420dadf8218efb956a2c00a', 1, 1, 0, NULL, 2052290101098295297, '2026-05-28 11:38:40', '2026-05-28 11:38:40');

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
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '行程打卡点' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of travel_checkin
-- ----------------------------
INSERT INTO `travel_checkin` VALUES (1, 1, 2, NULL, 180.0000000, 90.0000000, NULL, NULL, '大理古城位于云南省大理市，东临洱海、西倚苍山，海拔约2090米。其历史可追溯至唐天宝年间南诏王阁罗凤修建的羊苴咩城，后为南诏国、大理国的国都，在唐、宋五百多年间一直是云南的政治、经济和文化中心。今日的古城始建于明洪武十五年（1382年），1982年经重修后入选全国首批24个历史文化名城，现为国家4A级旅游景区。\n\n古城占地约3平方公里，呈典型的棋盘式布局，有“九街十八巷”之称。南北城门对称，东西城门相错，体现了白族建筑“东西南北不取中正”的独特原则。复兴路为中轴主干道，贯穿南北；五华楼位于古城中心，是古城的制高点。南城门为古城正门，城楼上的“大理”二字为郭沫若先生题写。\n\n城内清泉环绕，白族民居青瓦白墙、照壁彩绘，形成“家家流水，户户养花”的独特景致。古城融合了历史与现代，兼具白族传统风情与文艺气息，是感受大理风光与多元文化的理想目的地。', 5.0, '[30]', 0, '2026-05-27 19:44:26', '2026-05-27 19:44:26');
INSERT INTO `travel_checkin` VALUES (2, 2, 2, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '[29]', 0, '2026-05-27 20:15:16', '2026-05-27 20:15:16');

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
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '旅游目的地表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of travel_destination
-- ----------------------------
INSERT INTO `travel_destination` VALUES (1, NULL, '中国', 'dest-mpnzluhv', 0, NULL, NULL, NULL, NULL, NULL, 0, NULL, 1, 0, '2026-05-27 19:35:31', '2026-05-27 19:35:31');
INSERT INTO `travel_destination` VALUES (2, 1, '大理古城', 'dest-mpnzmxje', 3, NULL, NULL, NULL, NULL, NULL, 0, NULL, 1, 0, '2026-05-27 19:35:53', '2026-05-27 19:35:53');

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
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '旅行游记主表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of travel_trip
-- ----------------------------
INSERT INTO `travel_trip` VALUES (1, 2052290101098295297, '大理古城', 'trip-mpnzng23', '大理古城位于云南省大理市，东临洱海、西倚苍山，海拔约2090米。其历史可追溯至唐天宝年间南诏王阁罗凤修建的羊苴咩城，后为南诏国、大理国的国都，在唐、宋五百多年间一直是云南的政治、经济和文化中心。今日的古城始建于明洪武十五年（1382年），1982年经重修后入选全国首批24个历史文化名城，现为国家4A级旅游景区。\n\n古城占地约3平方公里，呈典型的棋盘式布局，有“九街十八巷”之称。南北城门对称，东西城门相错，体现了白族建筑“东西南北不取中正”的独特原则。复兴路为中轴主干道，贯穿南北；五华楼位于古城中心，是古城的制高点。南城门为古城正门，城楼上的“大理”二字为郭沫若先生题写。\n\n城内清泉环绕，白族民居青瓦白墙、照壁彩绘，形成“家家流水，户户养花”的独特景致。古城融合了历史与现代，兼具白族传统风情与文艺气息，是感受大理风光与多元文化的理想目的地。', 26, 'published', 'public', '2026-04-30', '2026-05-30', 31, 1, 100.00, 'CNY', 43, 0, '2026-05-27 19:36:23', '2026-05-27 19:36:19', '2026-05-29 02:23:43');

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
INSERT INTO `travel_trip_blog_post` VALUES (1, 3, 1, '2026-05-27 19:47:57');

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
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '行程日表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of travel_trip_day
-- ----------------------------
INSERT INTO `travel_trip_day` VALUES (1, 1, 1, '抵达大理，漫步古城', '晨光漫过苍山，照亮古城南门。复兴路上，石板被脚步声唤醒。街巷里，乳扇在炭火上微微卷曲，鲜花饼飘散甜香。午后躲进洋人街的小院，看三角梅探出白族照壁。黄昏时登上五华楼，风从洱海来，吹动满城灯火。\n\n', '洱海客栈', 200.00, 600.00, 400.00, 0, '2026-05-27 19:44:03', '2026-05-27 19:44:03');
INSERT INTO `travel_trip_day` VALUES (2, 1, 2, '大理第二日', NULL, NULL, NULL, NULL, NULL, 0, '2026-05-27 20:15:02', '2026-05-27 20:15:02');

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
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'UID Generator workerId 分配表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of worker_node
-- ----------------------------

SET FOREIGN_KEY_CHECKS = 1;
