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

 Date: 27/05/2026 17:35:26
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

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
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '旅游目的地表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of travel_destination
-- ----------------------------

SET FOREIGN_KEY_CHECKS = 1;
