/*
 Navicat Premium Dump SQL

 Source Server         : jiaqi-mysql
 Source Server Type    : MySQL
 Source Server Version : 80046 (8.0.46)
 Source Host           : 140.143.222.164:3307
 Source Schema         : flux_note

 Target Server Type    : MySQL
 Target Server Version : 80046 (8.0.46)
 File Encoding         : 65001

 Date: 14/05/2026 16:43:49
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

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
) ENGINE = InnoDB AUTO_INCREMENT = 40 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客分类表' ROW_FORMAT = Dynamic;

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
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客内容版本表（完整快照）' ROW_FORMAT = Dynamic;

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
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '文本文件内容（Markdown等小型文本资源）',
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
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客文件资源表' ROW_FORMAT = Dynamic;

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
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文章标题',
  `slug` varchar(220) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'URL唯一标识',
  `summary` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '摘要',
  `content_file_id` bigint NOT NULL COMMENT 'Markdown正文文件ID（关联blog_file_asset）',
  `cover_file_id` bigint NULL DEFAULT NULL COMMENT '封面文件ID（关联blog_file_asset）',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'draft' COMMENT '状态：draft(草稿)/published(已发布)/archived(已归档)',
  `visibility` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'public' COMMENT '可见性：public(公开)/private(私有)',
  `source_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'manual' COMMENT '来源：manual(手动)/ai(AI生成)/import(导入)',
  `is_original` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否原创：1-是，0-否',
  `view_count` int unsigned NOT NULL DEFAULT 0 COMMENT '浏览次数',
  `like_count` int unsigned NOT NULL DEFAULT 0 COMMENT '点赞次数',
  `published_at` datetime NULL DEFAULT NULL COMMENT '发布时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_blog_post_slug`(`slug` ASC) USING BTREE,
  INDEX `idx_blog_post_author_id`(`author_id` ASC) USING BTREE,
  INDEX `idx_blog_post_status_published_at`(`status` ASC, `published_at` ASC) USING BTREE,
  INDEX `idx_blog_post_create_time`(`create_time` ASC) USING BTREE,
  INDEX `idx_blog_post_content_file_id`(`content_file_id` ASC) USING BTREE,
  INDEX `idx_blog_post_cover_file_id`(`cover_file_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客文章主表' ROW_FORMAT = Dynamic;

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
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客文章分类关系表（多对多）' ROW_FORMAT = Dynamic;

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
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客文章标签关系表（多对多）' ROW_FORMAT = Dynamic;

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
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客搜索索引同步任务表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_search_index_task
-- ----------------------------

-- ----------------------------
-- Table structure for blog_tag
-- ----------------------------
DROP TABLE IF EXISTS `blog_tag`;
CREATE TABLE `blog_tag`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '标签名称',
  `slug` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '标签URL标识',
  `use_count` int unsigned NOT NULL DEFAULT 0 COMMENT '使用次数（文章数）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_blog_tag_slug`(`slug` ASC) USING BTREE,
  UNIQUE INDEX `uk_blog_tag_name`(`name` ASC) USING BTREE,
  INDEX `idx_blog_tag_use_count`(`use_count` DESC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客标签表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_tag
-- ----------------------------

SET FOREIGN_KEY_CHECKS = 1;
