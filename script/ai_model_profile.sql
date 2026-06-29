-- ----------------------------
-- AI 模型档案表（ai_model_profile）
-- 对应 nebula-sdk-ai 的 com.nebula.common.ai.flow.ModelProfile
-- 集中维护一组「模型连接 + 默认调用参数」，由 profile_code 被 ai_flow / ai_flow_node 引用复用；
-- 参数合并优先级：节点 > 流程默认 > 模型档案(本表) > 全局配置(nebula.ai.*)。
-- 由（待实现的）DatabaseModelProfileRepository 按 profile_code 读取，覆盖 SDK 默认内存实现。
-- 注意：api_key 以密文存储（落库前加密 / 读取后解密，加解密在 Repository 层处理），故列宽放宽。
-- ----------------------------

-- ----------------------------
-- Table structure for ai_model_profile
-- ----------------------------
DROP TABLE IF EXISTS `ai_model_profile`;
CREATE TABLE `ai_model_profile`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '档案ID',
  `profile_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '档案编码，全局唯一，被流程/节点引用',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '档案名称',
  `provider` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '服务提供商标识（openai/deepseek 等）',
  `base_url` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'API基础地址',
  `api_key` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'API密钥（AES加密密文存储，列宽含密文膨胀冗余）',
  `model` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '模型名称',
  `temperature` double NULL DEFAULT NULL COMMENT '默认采样温度',
  `max_tokens` int NULL DEFAULT NULL COMMENT '默认最大输出token数',
  `top_p` double NULL DEFAULT NULL COMMENT '默认Top P采样参数',
  `timeout_ms` int NULL DEFAULT NULL COMMENT '请求超时（毫秒）',
  `options` json NULL COMMENT '扩展参数，透传厂商私有参数（response_format / frequency_penalty 等，JSON对象）',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态：0=停用 1=启用',
  `remark` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_profile_code`(`profile_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI模型档案表' ROW_FORMAT = Dynamic;
