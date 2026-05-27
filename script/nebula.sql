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

 Date: 27/05/2026 16:32:59
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
) ENGINE = InnoDB AUTO_INCREMENT = 24 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客文件资源表' ROW_FORMAT = DYNAMIC;

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
INSERT INTO `blog_post` VALUES (2, 2052290101098295297, 'article', '完整的流程图生成工作流（含节点自动生成）', 'post-mpkxbqf9', NULL, 22, NULL, 'published', 'public', 'manual', 1, 18, 0, '2026-05-25 08:36:23', '2026-05-25 16:07:49', '2026-05-27 06:25:07');
INSERT INTO `blog_post` VALUES (3, 2052290101098295297, 'essay', '夜雨孤灯', 'essay-mpmdfybc', NULL, 23, NULL, 'published', 'public', 'manual', 1, 13, 0, '2026-05-26 08:26:39', '2026-05-26 16:26:44', '2026-05-27 06:25:10');

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
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客系列表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_series
-- ----------------------------
INSERT INTO `blog_series` VALUES (1, 'Redis', 'redis', NULL, NULL, 'published', 'public', 0, 0, 2052290101098295297, '2026-05-27 11:31:55', '2026-05-27 11:31:55');

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
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系列目录节点表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_series_catalog
-- ----------------------------
INSERT INTO `blog_series_catalog` VALUES (1, 1, NULL, 'Redis', 0, NULL, '_blank', '/1/', 0, 0, 0, '2026-05-27 13:35:52', '2026-05-27 13:35:52');

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
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '目录文章关联表' ROW_FORMAT = Dynamic;

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
INSERT INTO `sys_menu` VALUES (20, 0, 1, '博客管理', 'Blog', '/blog', 'BasicLayout', NULL, 'lucide:notebook-pen', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 200, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (21, 20, 2, '分类管理', 'BlogCategory', '/blog/category', 'blog/category/index', 'blog:category:list', 'lucide:folder-tree', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (22, 20, 2, '标签管理', 'BlogTag', '/blog/tag', 'blog/tag/index', 'blog:tag:list', 'lucide:tag', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (23, 20, 2, '文章管理', 'BlogArticle', '/blog/article', 'blog/article/index', 'blog:article:list', 'lucide:file-text', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-15 00:00:00', '2026-05-15 00:00:00');
INSERT INTO `sys_menu` VALUES (24, 20, 2, '随笔管理', 'BlogEssay', '/blog/essay', 'blog/article/index', 'blog:article:list', 'lucide:pen-line', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-26 00:00:00', '2026-05-26 00:00:00');
INSERT INTO `sys_menu` VALUES (25, 20, 2, '系列管理', 'BlogSeries', '/blog/series', 'blog/series/index', 'blog:series:list', 'lucide:layers', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (26, 20, 2, '系列目录', 'BlogSeriesCatalog', '/blog/series/:id/catalog', 'blog/series/catalog', 'blog:series:query', 'lucide:list-tree', NULL, '/blog/series', NULL, 0, 0, 1, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-27 00:00:00', '2026-05-27 00:00:00');
INSERT INTO `sys_menu` VALUES (1101, 11, 3, '新增用户', NULL, NULL, NULL, 'system:user:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-09 15:26:33', '2026-05-09 15:26:33');
INSERT INTO `sys_menu` VALUES (1102, 11, 3, '修改用户', NULL, NULL, NULL, 'system:user:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-09 15:26:33', '2026-05-09 15:26:33');
INSERT INTO `sys_menu` VALUES (1103, 11, 3, '删除用户', NULL, NULL, NULL, 'system:user:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-09 15:26:33', '2026-05-09 15:26:33');
INSERT INTO `sys_menu` VALUES (1201, 12, 3, '新增菜单', NULL, NULL, NULL, 'system:menu:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-09 15:26:33', '2026-05-09 15:26:33');
INSERT INTO `sys_menu` VALUES (1202, 12, 3, '修改菜单', NULL, NULL, NULL, 'system:menu:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-09 15:26:33', '2026-05-09 15:26:33');
INSERT INTO `sys_menu` VALUES (1203, 12, 3, '删除菜单', NULL, NULL, NULL, 'system:menu:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-09 15:26:33', '2026-05-09 15:26:33');
INSERT INTO `sys_menu` VALUES (1301, 13, 3, '新增角色', NULL, NULL, NULL, 'system:role:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-09 15:26:33', '2026-05-09 15:26:33');
INSERT INTO `sys_menu` VALUES (1302, 13, 3, '修改角色', NULL, NULL, NULL, 'system:role:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-09 15:26:33', '2026-05-09 15:26:33');
INSERT INTO `sys_menu` VALUES (1303, 13, 3, '删除角色', NULL, NULL, NULL, 'system:role:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-09 15:26:33', '2026-05-09 15:26:33');
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
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '行程打卡点' ROW_FORMAT = Dynamic;

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
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '旅游目的地表' ROW_FORMAT = Dynamic;

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
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '旅行游记主表' ROW_FORMAT = Dynamic;

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
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '行程日表' ROW_FORMAT = Dynamic;

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
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'UID Generator workerId 分配表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of worker_node
-- ----------------------------

SET FOREIGN_KEY_CHECKS = 1;
