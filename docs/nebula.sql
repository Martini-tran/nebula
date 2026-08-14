/*
 Navicat Premium Dump SQL

 Source Server         : localhost
 Source Server Type    : MySQL
 Source Server Version : 80012 (8.0.12)
 Source Host           : localhost:3306
 Source Schema         : nebula

 Target Server Type    : MySQL
 Target Server Version : 80012 (8.0.12)
 File Encoding         : 65001

 Date: 14/08/2026 20:24:29
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for ai_agent
-- ----------------------------
DROP TABLE IF EXISTS `ai_agent`;
CREATE TABLE `ai_agent`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `agent_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Agent编码，跨版本稳定的逻辑标识（非全局唯一）；= 记忆隔离键，同 agent_code 各版本共享一份长期记忆',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '名称',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '描述',
  `flow_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '引用的编排图编码',
  `flow_version` int(11) NOT NULL DEFAULT 1 COMMENT '引用的编排图版本（可换绑做灰度）',
  `input_schema` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '输入契约 JSON Schema',
  `output_schema` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '输出契约 JSON Schema',
  `memory_config` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '记忆配置：enabled/import 键/export 策略(Replace/Append/Summary)',
  `default_profile_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '默认模型档案编码',
  `version` int(11) NOT NULL DEFAULT 1 COMMENT 'Agent 定义版本，发布后不可变',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '0=停用 1=启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_agent_code_version`(`agent_code` ASC, `version` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 9 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI 智能体定义表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_agent
-- ----------------------------
INSERT INTO `ai_agent` VALUES (2, 'blog_series_writer', '博客系列作者', '每轮写一篇并更新大纲，供迭代链逐日推进', 'blog_series', 1, '{\"type\":\"object\",\"properties\":{\"topic\":{\"type\":\"string\"}},\"required\":[\"topic\"]}', '{\"type\":\"object\",\"properties\":{\"seriesState\":{\"type\":\"object\"}}}', '{\"enabled\":false}', 'DS-V3-001', 1, 1, '2026-07-08 17:09:27', '2026-07-08 17:09:27');
INSERT INTO `ai_agent` VALUES (7, 'blog_series', '博客系列生成', '按系列大纲逐篇生成博客，边写边规划', 'blog_series', 1, NULL, NULL, NULL, 'DS-V3-001', 1, 1, '2026-07-09 16:08:51', '2026-07-09 16:08:51');
INSERT INTO `ai_agent` VALUES (8, 'blog_outline_agent', '博客目录生成 Agent', '根据用户提供的主题，自动生成带目录结构的完整博客文章', 'blog_outline_agent', 1, '{\"type\":\"object\",\"required\":[\"topic\"],\"properties\":{\"topic\":{\"type\":\"string\",\"description\":\"博客主题\"}}}', '{\"type\":\"object\",\"properties\":{\"outline\":{\"type\":\"string\",\"description\":\"目录大纲\"},\"blogContent\":{\"type\":\"string\",\"description\":\"完整博客正文\"}}}', NULL, NULL, 1, 1, '2026-07-23 20:24:01', '2026-07-23 20:24:01');

-- ----------------------------
-- Table structure for ai_agent_instance
-- ----------------------------
DROP TABLE IF EXISTS `ai_agent_instance`;
CREATE TABLE `ai_agent_instance`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '实例唯一标识，业务键，续跑/唤醒入口',
  `agent_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '归属 Agent 编码（= 记忆隔离键）',
  `agent_version` int(11) NOT NULL COMMENT '审计标记：从哪个 Agent 版本创建',
  `flow_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '审计标记：引用的编排图编码',
  `flow_version` int(11) NOT NULL COMMENT '审计标记：从哪个 Flow 版本创建；运行时不据此查表（改用 graph_snapshot）',
  `graph_snapshot` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '★版本锁定核心：创建时编译的完整源图定义(节点+边+stateConfig+guard)JSON。续跑/回放只从此加载，不回查 node/edge 表',
  `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '归属用户ID',
  `conversation_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '关联会话ID',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING | SUSPENDED | SUCCESS | FAILED',
  `current_state` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '状态机当前状态（节点编码）',
  `last_checkpoint_state` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '最近越过的检查点状态（补偿回滚边界；阶段 2.5 启用）',
  `awaiting_events` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'SUSPENDED 时等待的事件名集合(JSON数组)，signal 命中其一即唤醒（阶段 3 启用）',
  `inputs` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '初始输入快照(JSON)',
  `context_snapshot` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '分区上下文快照(JSON)，分级落盘：仅挂起/越检查点/终态时全量刷新',
  `context_snapshot_seq` int(11) NOT NULL DEFAULT -1 COMMENT '快照对应的 transition seq，恢复时从此 seq 之后重放增量',
  `outputs` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'Outputs 快照(JSON)',
  `transition_count` int(11) NOT NULL DEFAULT 0 COMMENT '已转移次数，配合 max_transitions 防死循环',
  `parent_instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '父实例（递归子 Agent 用，顶层为空，阶段 3 用）',
  `parent_node_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '父实例中触发本子实例的 AgentNode 编码（回调唤醒用，阶段 3）',
  `lock_version` int(11) NOT NULL DEFAULT 0 COMMENT '乐观锁：signal/resume 用 CAS 抢占，防并发重复推进',
  `error_msg` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '失败原因',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_instance_id`(`instance_id` ASC) USING BTREE,
  INDEX `idx_parent`(`parent_instance_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 32 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI 智能体执行实例（状态机）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_agent_instance
-- ----------------------------
INSERT INTO `ai_agent_instance` VALUES (20, 'blog_series_writer-aefcca8e3c684218911af30ed196531c', 'blog_series_writer', 1, 'blog_series', 1, '{\"flowCode\":\"blog_series\",\"name\":\"博客系列生成\",\"description\":\"按系列大纲逐篇生成博客，边写边规划\",\"version\":1,\"defaultProfileCode\":\"DS-V3-001\",\"engineType\":\"STATE_MACHINE\",\"maxTransitions\":100,\"maxAgentDepth\":8,\"webhookUrl\":null,\"nodes\":[{\"nodeCode\":\"node_1\",\"name\":\"开始\",\"nodeType\":\"START\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"__x6\":{\"x\":120,\"y\":200},\"inputs\":{\"topic\":\"系列主题\",\"history\":\"已写文章索引\",\"outline\":\"上一轮大纲\",\"prevSeq\":\"上一篇序号\",\"accumulated\":\"累积状态\"}},\"rememberTrace\":false,\"stateType\":\"ENTRY\",\"sortNo\":0},{\"nodeCode\":\"node_2\",\"name\":\"写一篇并更新大纲\",\"nodeType\":\"PROMPT\",\"systemPrompt\":\"你是一个技术博客连载作者。围绕系列主题《{{topic}}》持续创作。\\n\\n已有整体大纲：{{outline}}\\n累积写作状态（已用例子/已讲知识点/风格）：{{accumulated}}\\n已写文章索引：{{history}}\\n这是第 {{prevSeq}} 篇之后的下一篇。\\n\\n请：\\n1. 基于大纲与累积状态，决定并撰写下一篇文章（承接前文、不重复已讲内容）。\\n2. 如有需要，微调整体大纲（边写边规划）。\\n3. 更新累积写作状态。\\n4. 判断系列是否已写完。\\n\\n只输出 JSON，无其它文字：\",\"promptTemplate\":\"请生成下一篇。\",\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":0.8,\"maxTokens\":4096,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"seriesState\",\"outputMode\":\"JSON\",\"nodeConfig\":{\"llm\":{\"output\":{\"type\":\"JSON\",\"jsonSchema\":\"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"articleTitle\\\":{\\\"type\\\":\\\"string\\\"},\\\"articleBody\\\":{\\\"type\\\":\\\"string\\\"},\\\"outline\\\":{\\\"type\\\":\\\"string\\\"},\\\"accumulated\\\":{\\\"type\\\":\\\"string\\\"},\\\"outlineDone\\\":{\\\"type\\\":\\\"string\\\",\\\"description\\\":\\\"全系列写完置 true，否则 false\\\"}},\\\"required\\\":[\\\"articleTitle\\\",\\\"articleBody\\\",\\\"outline\\\",\\\"accumulated\\\",\\\"outlineDone\\\"]}\"},\"prompt\":{\"systemPrompt\":\"你是一个技术博客连载作者。围绕系列主题《{{topic}}》持续创作。\\n\\n已有整体大纲：{{outline}}\\n累积写作状态（已用例子/已讲知识点/风格）：{{accumulated}}\\n已写文章索引：{{history}}\\n这是第 {{prevSeq}} 篇之后的下一篇。\\n\\n请：\\n1. 基于大纲与累积状态，决定并撰写下一篇文章（承接前文、不重复已讲内容）。\\n2. 如有需要，微调整体大纲（边写边规划）。\\n3. 更新累积写作状态。\\n4. 判断系列是否已写完。\\n\\n只输出 JSON，无其它文字：\",\"userPromptTemplate\":\"请生成下一篇。\"},\"parameters\":{\"mode\":\"basic\",\"basic\":{\"maxTokens\":4096,\"temperature\":0.8}}},\"__x6\":{\"x\":420,\"y\":200}},\"rememberTrace\":true,\"stateType\":\"NORMAL\",\"sortNo\":1},{\"nodeCode\":\"node_3\",\"name\":\"结束\",\"nodeType\":\"END\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"__x6\":{\"x\":760,\"y\":200}},\"rememberTrace\":false,\"stateType\":\"TERMINAL\",\"sortNo\":2}],\"edges\":[{\"fromNode\":\"node_1\",\"toNode\":\"node_2\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":0},{\"fromNode\":\"node_2\",\"toNode\":\"node_3\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":1}]}', NULL, NULL, 'SUCCESS', 'node_3', NULL, NULL, '{\"topic\":\"AI原生应用工程化落地\",\"prevSeq\":6}', '{\"outline_update\":{\"changes\":\"无重大调整，保持原有大纲顺序。\",\"new_outline\":[\"1. AI原生应用概述与工程化挑战\",\"2. 数据管道设计与治理\",\"3. 模型训练与实验管理\",\"4. 模型部署与推理优化\",\"5. 监控与可观测性体系\",\"6. 持续交付与CI/CD流水线\",\"7. 安全与合规实践\",\"8. 团队协作与组织架构\",\"9. 案例研究：端到端AI原生应用\",\"10. 未来趋势与总结\"]},\"prevSeq\":6,\"__currentInstanceId\":\"blog_series_writer-aefcca8e3c684218911af30ed196531c\",\"__maxAgentDepth\":8,\"series_complete\":false,\"topic\":\"AI原生应用工程化落地\",\"accumulated_update\":{\"used_examples\":[\"数据管道ETL\",\"MLflow实验跟踪\",\"Kubernetes部署\",\"Prometheus监控\",\"GitLab CI流水线\",\"金丝雀部署\"],\"covered_topics\":[\"数据治理\",\"模型训练\",\"部署策略\",\"监控\",\"CI/CD\"],\"writing_style\":\"技术深度适中，结合案例与工具选型，注重实践指导。\"},\"article\":{\"title\":\"AI原生应用的持续交付：CI/CD流水线设计与实践\",\"content\":\"在之前的文章中，我们探讨了AI原生应用的数据管道、模型训练、部署与监控等关键环节。然而，这些环节的自动化与协同是保障应用持续演进的核心。本文聚焦于AI原生应用的CI/CD流水线设计，阐述如何构建一套高效、可靠的持续交付体系，以应对模型迭代、数据漂移与业务需求变化的挑战。\\n\\n## 1. 传统CI/CD与AI CI/CD的差异\\n传统软件CI/CD主要关注代码变更的构建、测试与部署。AI原生应用则增加了模型、数据与实验的复杂性。例如，模型训练是一个计算密集型过程，且模型性能依赖于数据质量。因此，AI CI/CD需要纳入数据验证、模型评估、版本管理以及A/B测试等环节。\\n\\n## 2. 流水线核心阶段\\n### 2.1 数据准备与验证\\n- 自动触发数据管道，拉取最新训练数据。\\n- 运行数据质量检查（如缺失值、分布偏移、特征完整性）。\\n- 若数据质量不合格，阻断后续流程并告警。\\n\\n### 2.2 模型训练与实验跟踪\\n- 基于最新数据触发训练任务，使用MLflow、Weights & Biases等工具记录超参数、指标与模型产物。\\n- 支持并行实验（如超参数搜索、架构变体）。\\n- 训练完成后，自动将模型注册到模型仓库（如MLflow Model Registry）。\\n\\n### 2.3 模型评估与验证\\n- 在hold-out测试集上计算性能指标（如准确率、召回率、AUC）。\\n- 与生产中的当前模型进行对比，设置性能门限（如提升>1%才通过）。\\n- 运行公平性、鲁棒性测试（如对抗样本测试）。\\n\\n### 2.4 部署与发布\\n- 采用蓝绿部署或金丝雀发布策略，将新模型逐步上线。\\n- 自动配置推理服务（如使用Kubernetes + Istio进行流量管理）。\\n- 部署后执行冒烟测试，验证推理接口可用性。\\n\\n### 2.5 监控与回滚\\n- 实时监控模型性能（如延迟、吞吐、预测分布）。\\n- 若检测到性能退化（如数据漂移、准确率下降），自动触发回滚至上一个稳定版本。\\n- 记录所有发布事件，便于审计。\\n\\n## 3. 工具链选型\\n- **CI/CD平台**：GitLab CI、Jenkins、Argo Workflows（适合Kubernetes原生）。\\n- **实验跟踪**：MLflow、Kubeflow Pipelines。\\n- **模型仓库**：MLflow Model Registry、DVC。\\n- **部署与编排**：Kubernetes、Helm、Seldon Core（用于模型serving）。\\n- **监控**：Prometheus + Grafana，结合Evidently AI检测数据漂移。\\n\\n## 4. 实践案例：基于GitLab CI的端到端流水线\\n假设一个文本分类应用，代码库包含训练脚本、Dockerfile与Kubernetes部署清单。流水线流程如下：\\n1. 开发者提交代码（含模型架构修改）。\\n2. GitLab CI触发：\\n   - 阶段1：运行单元测试与lint。\\n   - 阶段2：执行数据验证（使用Great Expectations）。\\n   - 阶段3：启动训练任务（在GPU节点上），记录指标到MLflow。\\n   - 阶段4：评估模型，若指标不达标则失败。\\n   - 阶段5：构建推理镜像，推送至容器注册表。\\n   - 阶段6：部署至staging环境，运行集成测试。\\n   - 阶段7：手动审批后，金丝雀发布至生产（5%流量），监控1小时后全量。\\n\\n## 5. 挑战与应对\\n- **数据版本控制**：使用DVC或LakeFS管理数据快照，确保可复现。\\n- **模型漂移检测**：集成在线监控，当分布偏移超过阈值时自动触发重新训练。\\n- **资源成本**：训练任务使用spot实例，流水线设置超时与自动缩放。\\n- **安全合规**：扫描模型文件中的恶意代码，限制镜像来源。\\n\\n## 6. 总结\\nAI原生应用的CI/CD流水线需要融合数据工程、机器学习与DevOps的最佳实践。通过自动化数据验证、模型评估与渐进式部署，可以显著提升迭代速度与可靠性。下一篇文章将探讨如何构建AI应用的监控与可观测性体系，确保生产环境中的持续健康。\",\"key_points\":[\"AI CI/CD与传统CI/CD的差异\",\"流水线核心阶段：数据验证、训练、评估、部署、监控\",\"工具链选型建议\",\"基于GitLab CI的端到端案例\",\"挑战：数据版本、漂移检测、成本、安全\"],\"examples_used\":[\"GitLab CI流水线配置\",\"MLflow实验跟踪\",\"金丝雀部署策略\"]},\"seriesState\":\"{\\n  \\\"article\\\": {\\n    \\\"title\\\": \\\"AI原生应用的持续交付：CI/CD流水线设计与实践\\\",\\n    \\\"content\\\": \\\"在之前的文章中，我们探讨了AI原生应用的数据管道、模型训练、部署与监控等关键环节。然而，这些环节的自动化与协同是保障应用持续演进的核心。本文聚焦于AI原生应用的CI/CD流水线设计，阐述如何构建一套高效、可靠的持续交付体系，以应对模型迭代、数据漂移与业务需求变化的挑战。\\\\n\\\\n## 1. 传统CI/CD与AI CI/CD的差异\\\\n传统软件CI/CD主要关注代码变更的构建、测试与部署。AI原生应用则增加了模型、数据与实验的复杂性。例如，模型训练是一个计算密集型过程，且模型性能依赖于数据质量。因此，AI CI/CD需要纳入数据验证、模型评估、版本管理以及A/B测试等环节。\\\\n\\\\n## 2. 流水线核心阶段\\\\n### 2.1 数据准备与验证\\\\n- 自动触发数据管道，拉取最新训练数据。\\\\n- 运行数据质量检查（如缺失值、分布偏移、特征完整性）。\\\\n- 若数据质量不合格，阻断后续流程并告警。\\\\n\\\\n### 2.2 模型训练与实验跟踪\\\\n- 基于最新数据触发训练任务，使用MLflow、Weights & Biases等工具记录超参数、指标与模型产物。\\\\n- 支持并行实验（如超参数搜索、架构变体）。\\\\n- 训练完成后，自动将模型注册到模型仓库（如MLflow Model Registry）。\\\\n\\\\n### 2.3 模型评估与验证\\\\n- 在hold-out测试集上计算性能指标（如准确率、召回率、AUC）。\\\\n- 与生产中的当前模型进行对比，设置性能门限（如提升>1%才通过）。\\\\n- 运行公平性、鲁棒性测试（如对抗样本测试）。\\\\n\\\\n### 2.4 部署与发布\\\\n- 采用蓝绿部署或金丝雀发布策略，将新模型逐步上线。\\\\n- 自动配置推理服务（如使用Kubernetes + Istio进行流量管理）。\\\\n- 部署后执行冒烟测试，验证推理接口可用性。\\\\n\\\\n### 2.5 监控与回滚\\\\n- 实时监控模型性能（如延迟、吞吐、预测分布）。\\\\n- 若检测到性能退化（如数据漂移、准确率下降），自动触发回滚至上一个稳定版本。\\\\n- 记录所有发布事件，便于审计。\\\\n\\\\n## 3. 工具链选型\\\\n- **CI/CD平台**：GitLab CI、Jenkins、Argo Workflows（适合Kubernetes原生）。\\\\n- **实验跟踪**：MLflow、Kubeflow Pipelines。\\\\n- **模型仓库**：MLflow Model Registry、DVC。\\\\n- **部署与编排**：Kubernetes、Helm、Seldon Core（用于模型serving）。\\\\n- **监控**：Prometheus + Grafana，结合Evidently AI检测数据漂移。\\\\n\\\\n## 4. 实践案例：基于GitLab CI的端到端流水线\\\\n假设一个文本分类应用，代码库包含训练脚本、Dockerfile与Kubernetes部署清单。流水线流程如下：\\\\n1. 开发者提交代码（含模型架构修改）。\\\\n2. GitLab CI触发：\\\\n   - 阶段1：运行单元测试与lint。\\\\n   - 阶段2：执行数据验证（使用Great Expectations）。\\\\n   - 阶段3：启动训练任务（在GPU节点上），记录指标到MLflow。\\\\n   - 阶段4：评估模型，若指标不达标则失败。\\\\n   - 阶段5：构建推理镜像，推送至容器注册表。\\\\n   - 阶段6：部署至staging环境，运行集成测试。\\\\n   - 阶段7：手动审批后，金丝雀发布至生产（5%流量），监控1小时后全量。\\\\n\\\\n## 5. 挑战与应对\\\\n- **数据版本控制**：使用DVC或LakeFS管理数据快照，确保可复现。\\\\n- **模型漂移检测**：集成在线监控，当分布偏移超过阈值时自动触发重新训练。\\\\n- **资源成本**：训练任务使用spot实例，流水线设置超时与自动缩放。\\\\n- **安全合规**：扫描模型文件中的恶意代码，限制镜像来源。\\\\n\\\\n## 6. 总结\\\\nAI原生应用的CI/CD流水线需要融合数据工程、机器学习与DevOps的最佳实践。通过自动化数据验证、模型评估与渐进式部署，可以显著提升迭代速度与可靠性。下一篇文章将探讨如何构建AI应用的监控与可观测性体系，确保生产环境中的持续健康。\\\",\\n    \\\"key_points\\\": [\\n      \\\"AI CI/CD与传统CI/CD的差异\\\",\\n      \\\"流水线核心阶段：数据验证、训练、评估、部署、监控\\\",\\n      \\\"工具链选型建议\\\",\\n      \\\"基于GitLab CI的端到端案例\\\",\\n      \\\"挑战：数据版本、漂移检测、成本、安全\\\"\\n    ],\\n    \\\"examples_used\\\": [\\\"GitLab CI流水线配置\\\", \\\"MLflow实验跟踪\\\", \\\"金丝雀部署策略\\\"]\\n  },\\n  \\\"outline_update\\\": {\\n    \\\"changes\\\": \\\"无重大调整，保持原有大纲顺序。\\\",\\n    \\\"new_outline\\\": [\\n      \\\"1. AI原生应用概述与工程化挑战\\\",\\n      \\\"2. 数据管道设计与治理\\\",\\n      \\\"3. 模型训练与实验管理\\\",\\n      \\\"4. 模型部署与推理优化\\\",\\n      \\\"5. 监控与可观测性体系\\\",\\n      \\\"6. 持续交付与CI/CD流水线\\\",\\n      \\\"7. 安全与合规实践\\\",\\n      \\\"8. 团队协作与组织架构\\\",\\n      \\\"9. 案例研究：端到端AI原生应用\\\",\\n      \\\"10. 未来趋势与总结\\\"\\n    ]\\n  },\\n  \\\"accumulated_update\\\": {\\n    \\\"used_examples\\\": [\\\"数据管道ETL\\\", \\\"MLflow实验跟踪\\\", \\\"Kubernetes部署\\\", \\\"Prometheus监控\\\", \\\"GitLab CI流水线\\\", \\\"金丝雀部署\\\"],\\n    \\\"covered_topics\\\": [\\\"数据治理\\\", \\\"模型训练\\\", \\\"部署策略\\\", \\\"监控\\\", \\\"CI/CD\\\"],\\n    \\\"writing_style\\\": \\\"技术深度适中，结合案例与工具选型，注重实践指导。\\\"\\n  },\\n  \\\"series_complete\\\": false\\n}\"}', 2, NULL, 2, NULL, NULL, 0, NULL, '2026-07-09 15:28:28', '2026-07-09 15:28:28');
INSERT INTO `ai_agent_instance` VALUES (21, 'blog_series_writer-065131816cc647eca9ccc876f915861d', 'blog_series_writer', 1, 'blog_series', 1, '{\"flowCode\":\"blog_series\",\"name\":\"博客系列生成\",\"description\":\"按系列大纲逐篇生成博客，边写边规划\",\"version\":1,\"defaultProfileCode\":\"DS-V3-001\",\"engineType\":\"STATE_MACHINE\",\"maxTransitions\":100,\"maxAgentDepth\":8,\"webhookUrl\":null,\"nodes\":[{\"nodeCode\":\"node_1\",\"name\":\"开始\",\"nodeType\":\"START\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"__x6\":{\"x\":120,\"y\":200},\"inputs\":{\"topic\":\"系列主题\",\"history\":\"已写文章索引\",\"outline\":\"上一轮大纲\",\"prevSeq\":\"上一篇序号\",\"accumulated\":\"累积状态\"}},\"rememberTrace\":false,\"stateType\":\"ENTRY\",\"sortNo\":0},{\"nodeCode\":\"node_2\",\"name\":\"写一篇并更新大纲\",\"nodeType\":\"PROMPT\",\"systemPrompt\":\"你是一个技术博客连载作者。围绕系列主题《{{topic}}》持续创作。\\n\\n已有整体大纲：{{outline}}\\n累积写作状态（已用例子/已讲知识点/风格）：{{accumulated}}\\n已写文章索引：{{history}}\\n这是第 {{prevSeq}} 篇之后的下一篇。\\n\\n请：\\n1. 基于大纲与累积状态，决定并撰写下一篇文章（承接前文、不重复已讲内容）。\\n2. 如有需要，微调整体大纲（边写边规划）。\\n3. 更新累积写作状态。\\n4. 判断系列是否已写完。\\n\\n只输出 JSON，无其它文字：\",\"promptTemplate\":\"请生成下一篇。\",\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":0.8,\"maxTokens\":4096,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"seriesState\",\"outputMode\":\"JSON\",\"nodeConfig\":{\"llm\":{\"output\":{\"type\":\"JSON\",\"jsonSchema\":\"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"articleTitle\\\":{\\\"type\\\":\\\"string\\\"},\\\"articleBody\\\":{\\\"type\\\":\\\"string\\\"},\\\"outline\\\":{\\\"type\\\":\\\"string\\\"},\\\"accumulated\\\":{\\\"type\\\":\\\"string\\\"},\\\"outlineDone\\\":{\\\"type\\\":\\\"string\\\",\\\"description\\\":\\\"全系列写完置 true，否则 false\\\"}},\\\"required\\\":[\\\"articleTitle\\\",\\\"articleBody\\\",\\\"outline\\\",\\\"accumulated\\\",\\\"outlineDone\\\"]}\"},\"prompt\":{\"systemPrompt\":\"你是一个技术博客连载作者。围绕系列主题《{{topic}}》持续创作。\\n\\n已有整体大纲：{{outline}}\\n累积写作状态（已用例子/已讲知识点/风格）：{{accumulated}}\\n已写文章索引：{{history}}\\n这是第 {{prevSeq}} 篇之后的下一篇。\\n\\n请：\\n1. 基于大纲与累积状态，决定并撰写下一篇文章（承接前文、不重复已讲内容）。\\n2. 如有需要，微调整体大纲（边写边规划）。\\n3. 更新累积写作状态。\\n4. 判断系列是否已写完。\\n\\n只输出 JSON，无其它文字：\",\"userPromptTemplate\":\"请生成下一篇。\"},\"parameters\":{\"mode\":\"basic\",\"basic\":{\"maxTokens\":4096,\"temperature\":0.8}}},\"__x6\":{\"x\":420,\"y\":200}},\"rememberTrace\":true,\"stateType\":\"NORMAL\",\"sortNo\":1},{\"nodeCode\":\"node_3\",\"name\":\"结束\",\"nodeType\":\"END\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"__x6\":{\"x\":760,\"y\":200}},\"rememberTrace\":false,\"stateType\":\"TERMINAL\",\"sortNo\":2}],\"edges\":[{\"fromNode\":\"node_1\",\"toNode\":\"node_2\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":0},{\"fromNode\":\"node_2\",\"toNode\":\"node_3\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":1}]}', NULL, NULL, 'SUCCESS', 'node_3', NULL, NULL, '{\"topic\":\"AI原生应用工程化落地\",\"prevSeq\":6}', '{\"prevSeq\":6,\"__currentInstanceId\":\"blog_series_writer-065131816cc647eca9ccc876f915861d\",\"__maxAgentDepth\":8,\"topic\":\"AI原生应用工程化落地\",\"article\":{\"title\":\"AI原生应用的监控与可观测性：从黑盒到白盒的工程实践\",\"content\":\"在前几篇文章中，我们探讨了AI原生应用的架构设计、数据管道、模型部署与推理优化。然而，当应用上线后，如何确保其稳定运行、快速定位问题，成为工程化落地的关键一环。传统的监控体系往往只关注系统层面（如CPU、内存），但AI应用的特殊性——模型行为的非确定性、数据漂移、推理延迟波动——要求我们构建更精细的可观测性体系。本文将深入探讨如何从黑盒监控走向白盒可观测，覆盖指标、日志、链路追踪三大支柱，并结合AI场景的特殊挑战给出实践方案。\\n\\n## 1. AI原生应用的可观测性挑战\\nAI应用不同于传统软件：\\n- **模型行为不可预测**：即使输入相似，输出也可能因模型版本、数据分布变化而不同。\\n- **数据漂移**：生产数据与训练数据分布不一致，导致模型性能下降。\\n- **推理延迟波动**：GPU资源竞争、模型大小、批处理策略等影响响应时间。\\n- **多模态与复杂链路**：一个请求可能经过多个模型、数据预处理、后处理步骤。\\n\\n因此，我们需要监控三个层次：\\n- **基础设施层**：GPU利用率、显存、网络延迟等。\\n- **模型服务层**：推理延迟、吞吐量、错误率、模型版本。\\n- **业务指标层**：预测准确率、用户反馈、A/B测试结果。\\n\\n## 2. 构建可观测性体系：三大支柱\\n### 2.1 指标（Metrics）\\n指标是数字化的、可聚合的度量。对于AI应用，关键指标包括：\\n- **系统指标**：GPU利用率、显存占用、请求队列长度。\\n- **模型指标**：推理延迟（P50/P95/P99）、吞吐量（QPS）、错误率（如模型输出NaN）。\\n- **数据指标**：输入特征分布（如均值、标准差）、输出概率分布、数据漂移检测得分（如PSI）。\\n\\n**实践**：使用Prometheus采集指标，Grafana可视化。例如，对每个模型版本记录推理延迟直方图，并设置告警：当P95延迟超过500ms时触发。\\n\\n### 2.2 日志（Logs）\\n日志提供事件的详细记录。在AI场景中，需要结构化日志，包含：\\n- 请求ID、时间戳、模型版本、输入特征摘要（如哈希值，避免隐私泄露）。\\n- 推理结果、置信度、错误堆栈。\\n- 数据预处理参数（如归一化系数）。\\n\\n**实践**：使用ELK栈（Elasticsearch、Logstash、Kibana）或Loki。例如，当模型返回低置信度结果时，记录输入特征以便后续分析。\\n\\n### 2.3 链路追踪（Traces）\\n链路追踪揭示请求在分布式系统中的完整路径。对于AI应用，一个请求可能经历：\\n1. 网关接收 → 2. 数据预处理（特征工程）→ 3. 模型推理（可能多模型级联）→ 4. 后处理 → 5. 返回。\\n每个步骤的耗时、依赖关系需清晰可见。\\n\\n**实践**：使用OpenTelemetry注入追踪上下文，Jaeger或Zipkin可视化。例如，追踪发现数据预处理步骤耗时占比过高，则考虑优化特征计算。\\n\\n## 3. 针对AI场景的特殊监控\\n### 3.1 数据漂移检测\\n数据漂移是AI应用退化的主要原因。可在线计算：\\n- **特征分布漂移**：使用PSI（Population Stability Index）或KS检验。\\n- **预测分布漂移**：监控模型输出概率的分布变化。\\n- **概念漂移**：结合业务反馈（如用户点击率）间接检测。\\n\\n**实践**：在推理管道中集成漂移检测模块，定期（如每小时）计算PSI，超过阈值则告警并触发模型重训练。\\n\\n### 3.2 模型性能退化监控\\n除了延迟，还需关注模型准确性。但真实标签往往有延迟，因此采用：\\n- **代理指标**：如置信度、熵值，若平均置信度下降可能预示性能退化。\\n- **影子模型对比**：将生产请求同时发送到新模型和基线模型，比较输出差异。\\n- **A/B测试**：对部分流量部署新模型，对比业务指标。\\n\\n### 3.3 推理成本监控\\nGPU资源昂贵，需监控每个请求的推理成本：\\n- **每请求GPU时间**：通过CUDA事件或NVIDIA DCGM采集。\\n- **批处理效率**：监控批处理大小与延迟的权衡。\\n\\n## 4. 工程化落地案例\\n假设我们有一个文本分类服务，使用BERT模型。\\n- **指标**：Prometheus采集推理延迟、QPS、GPU利用率。Grafana仪表盘显示实时趋势。\\n- **日志**：每个请求记录输入文本长度、预测类别、置信度。当置信度<0.6时，日志级别提升为WARN。\\n- **追踪**：OpenTelemetry注入，Jaeger展示请求链路：预处理（分词）→模型推理（GPU）→后处理（softmax）。发现预处理耗时占40%，于是优化分词库。\\n- **漂移检测**：每小时计算输入文本长度分布与训练集PSI，某天PSI突增，告警发现新用户群体使用更长的文本，触发模型微调。\\n\\n## 5. 工具选型建议\\n- **指标**：Prometheus + Grafana（开源标准）。\\n- **日志**：ELK（功能全面）或 Loki（轻量级，与Prometheus集成好）。\\n- **追踪**：OpenTelemetry（统一标准）+ Jaeger（社区活跃）或 Tempo（Grafana生态）。\\n- **漂移检测**：WhyLabs（SaaS）或自建基于MLflow的监控。\\n\\n## 6. 总结与展望\\n可观测性是AI原生应用工程化的“眼睛”。从黑盒监控到白盒可观测，我们不仅能快速定位问题，还能提前预警性能退化。下一步，可以结合MLOps平台实现自动化回滚与重训练，形成闭环。在下一篇文章中，我们将探讨AI应用的持续交付与MLOps实践。\",\"next_outline\":{\"sections\":[\"1. 引言：AI原生应用工程化落地的全景\",\"2. 架构设计：微服务与事件驱动\",\"3. 数据工程：实时数据管道与特征存储\",\"4. 模型部署：容器化与无服务器推理\",\"5. 推理优化：缓存、批处理与模型压缩\",\"6. 监控与可观测性：从黑盒到白盒\",\"7. 持续交付与MLOps：自动化流水线\",\"8. 安全与合规：模型审计与隐私保护\",\"9. 成本优化：GPU资源管理与弹性伸缩\",\"10. 案例研究：从0到1构建AI原生应用\"]},\"accumulated\":{\"used_examples\":[\"文本分类服务（BERT）的监控与可观测性案例\"],\"covered_topics\":[\"监控指标（GPU、延迟、吞吐量）\",\"日志结构化（请求ID、特征摘要）\",\"链路追踪（预处理、推理、后处理）\",\"数据漂移检测（PSI、KS检验）\",\"模型性能退化监控（代理指标、影子模型）\",\"推理成本监控（GPU时间、批处理效率）\",\"工具选型（Prometheus、ELK、OpenTelemetry、Jaeger）\"],\"style\":\"技术深度与工程实践结合，提供具体指标、工具和案例\"},\"series_complete\":false},\"seriesState\":\"{\\n  \\\"article\\\": {\\n    \\\"title\\\": \\\"AI原生应用的监控与可观测性：从黑盒到白盒的工程实践\\\",\\n    \\\"content\\\": \\\"在前几篇文章中，我们探讨了AI原生应用的架构设计、数据管道、模型部署与推理优化。然而，当应用上线后，如何确保其稳定运行、快速定位问题，成为工程化落地的关键一环。传统的监控体系往往只关注系统层面（如CPU、内存），但AI应用的特殊性——模型行为的非确定性、数据漂移、推理延迟波动——要求我们构建更精细的可观测性体系。本文将深入探讨如何从黑盒监控走向白盒可观测，覆盖指标、日志、链路追踪三大支柱，并结合AI场景的特殊挑战给出实践方案。\\\\n\\\\n## 1. AI原生应用的可观测性挑战\\\\nAI应用不同于传统软件：\\\\n- **模型行为不可预测**：即使输入相似，输出也可能因模型版本、数据分布变化而不同。\\\\n- **数据漂移**：生产数据与训练数据分布不一致，导致模型性能下降。\\\\n- **推理延迟波动**：GPU资源竞争、模型大小、批处理策略等影响响应时间。\\\\n- **多模态与复杂链路**：一个请求可能经过多个模型、数据预处理、后处理步骤。\\\\n\\\\n因此，我们需要监控三个层次：\\\\n- **基础设施层**：GPU利用率、显存、网络延迟等。\\\\n- **模型服务层**：推理延迟、吞吐量、错误率、模型版本。\\\\n- **业务指标层**：预测准确率、用户反馈、A/B测试结果。\\\\n\\\\n## 2. 构建可观测性体系：三大支柱\\\\n### 2.1 指标（Metrics）\\\\n指标是数字化的、可聚合的度量。对于AI应用，关键指标包括：\\\\n- **系统指标**：GPU利用率、显存占用、请求队列长度。\\\\n- **模型指标**：推理延迟（P50/P95/P99）、吞吐量（QPS）、错误率（如模型输出NaN）。\\\\n- **数据指标**：输入特征分布（如均值、标准差）、输出概率分布、数据漂移检测得分（如PSI）。\\\\n\\\\n**实践**：使用Prometheus采集指标，Grafana可视化。例如，对每个模型版本记录推理延迟直方图，并设置告警：当P95延迟超过500ms时触发。\\\\n\\\\n### 2.2 日志（Logs）\\\\n日志提供事件的详细记录。在AI场景中，需要结构化日志，包含：\\\\n- 请求ID、时间戳、模型版本、输入特征摘要（如哈希值，避免隐私泄露）。\\\\n- 推理结果、置信度、错误堆栈。\\\\n- 数据预处理参数（如归一化系数）。\\\\n\\\\n**实践**：使用ELK栈（Elasticsearch、Logstash、Kibana）或Loki。例如，当模型返回低置信度结果时，记录输入特征以便后续分析。\\\\n\\\\n### 2.3 链路追踪（Traces）\\\\n链路追踪揭示请求在分布式系统中的完整路径。对于AI应用，一个请求可能经历：\\\\n1. 网关接收 → 2. 数据预处理（特征工程）→ 3. 模型推理（可能多模型级联）→ 4. 后处理 → 5. 返回。\\\\n每个步骤的耗时、依赖关系需清晰可见。\\\\n\\\\n**实践**：使用OpenTelemetry注入追踪上下文，Jaeger或Zipkin可视化。例如，追踪发现数据预处理步骤耗时占比过高，则考虑优化特征计算。\\\\n\\\\n## 3. 针对AI场景的特殊监控\\\\n### 3.1 数据漂移检测\\\\n数据漂移是AI应用退化的主要原因。可在线计算：\\\\n- **特征分布漂移**：使用PSI（Population Stability Index）或KS检验。\\\\n- **预测分布漂移**：监控模型输出概率的分布变化。\\\\n- **概念漂移**：结合业务反馈（如用户点击率）间接检测。\\\\n\\\\n**实践**：在推理管道中集成漂移检测模块，定期（如每小时）计算PSI，超过阈值则告警并触发模型重训练。\\\\n\\\\n### 3.2 模型性能退化监控\\\\n除了延迟，还需关注模型准确性。但真实标签往往有延迟，因此采用：\\\\n- **代理指标**：如置信度、熵值，若平均置信度下降可能预示性能退化。\\\\n- **影子模型对比**：将生产请求同时发送到新模型和基线模型，比较输出差异。\\\\n- **A/B测试**：对部分流量部署新模型，对比业务指标。\\\\n\\\\n### 3.3 推理成本监控\\\\nGPU资源昂贵，需监控每个请求的推理成本：\\\\n- **每请求GPU时间**：通过CUDA事件或NVIDIA DCGM采集。\\\\n- **批处理效率**：监控批处理大小与延迟的权衡。\\\\n\\\\n## 4. 工程化落地案例\\\\n假设我们有一个文本分类服务，使用BERT模型。\\\\n- **指标**：Prometheus采集推理延迟、QPS、GPU利用率。Grafana仪表盘显示实时趋势。\\\\n- **日志**：每个请求记录输入文本长度、预测类别、置信度。当置信度<0.6时，日志级别提升为WARN。\\\\n- **追踪**：OpenTelemetry注入，Jaeger展示请求链路：预处理（分词）→模型推理（GPU）→后处理（softmax）。发现预处理耗时占40%，于是优化分词库。\\\\n- **漂移检测**：每小时计算输入文本长度分布与训练集PSI，某天PSI突增，告警发现新用户群体使用更长的文本，触发模型微调。\\\\n\\\\n## 5. 工具选型建议\\\\n- **指标**：Prometheus + Grafana（开源标准）。\\\\n- **日志**：ELK（功能全面）或 Loki（轻量级，与Prometheus集成好）。\\\\n- **追踪**：OpenTelemetry（统一标准）+ Jaeger（社区活跃）或 Tempo（Grafana生态）。\\\\n- **漂移检测**：WhyLabs（SaaS）或自建基于MLflow的监控。\\\\n\\\\n## 6. 总结与展望\\\\n可观测性是AI原生应用工程化的“眼睛”。从黑盒监控到白盒可观测，我们不仅能快速定位问题，还能提前预警性能退化。下一步，可以结合MLOps平台实现自动化回滚与重训练，形成闭环。在下一篇文章中，我们将探讨AI应用的持续交付与MLOps实践。\\\",\\n    \\\"next_outline\\\": {\\n      \\\"sections\\\": [\\n        \\\"1. 引言：AI原生应用工程化落地的全景\\\",\\n        \\\"2. 架构设计：微服务与事件驱动\\\",\\n        \\\"3. 数据工程：实时数据管道与特征存储\\\",\\n        \\\"4. 模型部署：容器化与无服务器推理\\\",\\n        \\\"5. 推理优化：缓存、批处理与模型压缩\\\",\\n        \\\"6. 监控与可观测性：从黑盒到白盒\\\",\\n        \\\"7. 持续交付与MLOps：自动化流水线\\\",\\n        \\\"8. 安全与合规：模型审计与隐私保护\\\",\\n        \\\"9. 成本优化：GPU资源管理与弹性伸缩\\\",\\n        \\\"10. 案例研究：从0到1构建AI原生应用\\\"\\n      ]\\n    },\\n    \\\"accumulated\\\": {\\n      \\\"used_examples\\\": [\\n        \\\"文本分类服务（BERT）的监控与可观测性案例\\\"\\n      ],\\n      \\\"covered_topics\\\": [\\n        \\\"监控指标（GPU、延迟、吞吐量）\\\",\\n        \\\"日志结构化（请求ID、特征摘要）\\\",\\n        \\\"链路追踪（预处理、推理、后处理）\\\",\\n        \\\"数据漂移检测（PSI、KS检验）\\\",\\n        \\\"模型性能退化监控（代理指标、影子模型）\\\",\\n        \\\"推理成本监控（GPU时间、批处理效率）\\\",\\n        \\\"工具选型（Prometheus、ELK、OpenTelemetry、Jaeger）\\\"\\n      ],\\n      \\\"style\\\": \\\"技术深度与工程实践结合，提供具体指标、工具和案例\\\"\\n    },\\n    \\\"series_complete\\\": false\\n  }\\n}\"}', 2, NULL, 2, NULL, NULL, 0, NULL, '2026-07-09 15:34:45', '2026-07-09 15:34:45');
INSERT INTO `ai_agent_instance` VALUES (22, 'blog_series_writer-bf2e0e39ce8844f9abbca1cc1f9b942c', 'blog_series_writer', 1, 'blog_series', 1, '{\"flowCode\":\"blog_series\",\"name\":\"博客系列生成\",\"description\":\"按系列大纲逐篇生成博客，边写边规划\",\"version\":1,\"defaultProfileCode\":\"DS-V3-001\",\"engineType\":\"STATE_MACHINE\",\"maxTransitions\":100,\"maxAgentDepth\":8,\"webhookUrl\":null,\"nodes\":[{\"nodeCode\":\"node_1\",\"name\":\"开始\",\"nodeType\":\"START\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"__x6\":{\"x\":120,\"y\":200},\"inputs\":{\"topic\":\"系列主题\",\"history\":\"已写文章索引\",\"outline\":\"上一轮大纲\",\"prevSeq\":\"上一篇序号\",\"accumulated\":\"累积状态\"}},\"rememberTrace\":false,\"stateType\":\"ENTRY\",\"sortNo\":0},{\"nodeCode\":\"node_2\",\"name\":\"写一篇并更新大纲\",\"nodeType\":\"PROMPT\",\"systemPrompt\":\"你是一个技术博客连载作者。围绕系列主题《{{topic}}》持续创作。\\n\\n已有整体大纲：{{outline}}\\n累积写作状态（已用例子/已讲知识点/风格）：{{accumulated}}\\n已写文章索引：{{history}}\\n这是第 {{prevSeq}} 篇之后的下一篇。\\n\\n请：\\n1. 基于大纲与累积状态，决定并撰写下一篇文章（承接前文、不重复已讲内容）。\\n2. 如有需要，微调整体大纲（边写边规划）。\\n3. 更新累积写作状态。\\n4. 判断系列是否已写完。\\n\\n只输出 JSON，无其它文字：\",\"promptTemplate\":\"请生成下一篇。\",\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":0.8,\"maxTokens\":4096,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"seriesState\",\"outputMode\":\"JSON\",\"nodeConfig\":{\"llm\":{\"output\":{\"type\":\"JSON\",\"jsonSchema\":\"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"articleTitle\\\":{\\\"type\\\":\\\"string\\\"},\\\"articleBody\\\":{\\\"type\\\":\\\"string\\\"},\\\"outline\\\":{\\\"type\\\":\\\"string\\\"},\\\"accumulated\\\":{\\\"type\\\":\\\"string\\\"},\\\"outlineDone\\\":{\\\"type\\\":\\\"string\\\",\\\"description\\\":\\\"全系列写完置 true，否则 false\\\"}},\\\"required\\\":[\\\"articleTitle\\\",\\\"articleBody\\\",\\\"outline\\\",\\\"accumulated\\\",\\\"outlineDone\\\"]}\"},\"prompt\":{\"systemPrompt\":\"你是一个技术博客连载作者。围绕系列主题《{{topic}}》持续创作。\\n\\n已有整体大纲：{{outline}}\\n累积写作状态（已用例子/已讲知识点/风格）：{{accumulated}}\\n已写文章索引：{{history}}\\n这是第 {{prevSeq}} 篇之后的下一篇。\\n\\n请：\\n1. 基于大纲与累积状态，决定并撰写下一篇文章（承接前文、不重复已讲内容）。\\n2. 如有需要，微调整体大纲（边写边规划）。\\n3. 更新累积写作状态。\\n4. 判断系列是否已写完。\\n\\n只输出 JSON，无其它文字：\",\"userPromptTemplate\":\"请生成下一篇。\"},\"parameters\":{\"mode\":\"basic\",\"basic\":{\"maxTokens\":4096,\"temperature\":0.8}}},\"__x6\":{\"x\":420,\"y\":200}},\"rememberTrace\":true,\"stateType\":\"NORMAL\",\"sortNo\":1},{\"nodeCode\":\"node_3\",\"name\":\"结束\",\"nodeType\":\"END\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"__x6\":{\"x\":760,\"y\":200}},\"rememberTrace\":false,\"stateType\":\"TERMINAL\",\"sortNo\":2}],\"edges\":[{\"fromNode\":\"node_1\",\"toNode\":\"node_2\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":0},{\"fromNode\":\"node_2\",\"toNode\":\"node_3\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":1}]}', NULL, NULL, 'SUCCESS', 'node_3', NULL, NULL, '{\"topic\":\"AI原生应用工程化落地\",\"prevSeq\":0}', '{\"outline_update\":{\"changes\":\"无变化，大纲已涵盖本内容。\",\"updated_outline\":\"1. AI原生应用工程化落地：引言与核心挑战（已完成）\\n2. AI原生应用的架构设计：从单体到智能体编排（当前）\\n3. 数据管道设计：构建高效、可靠的AI数据流（计划中）\\n4. 模型部署与推理优化：从实验到生产（计划中）\\n5. 监控与可观测性：AI应用的生命线（计划中）\\n6. 安全与伦理：AI原生应用的边界（计划中）\\n7. 总结与未来展望（计划中）\"},\"prevSeq\":0,\"__currentInstanceId\":\"blog_series_writer-bf2e0e39ce8844f9abbca1cc1f9b942c\",\"__maxAgentDepth\":8,\"series_complete\":false,\"topic\":\"AI原生应用工程化落地\",\"accumulated_update\":{\"used_examples\":[\"聊天机器人单体架构问题\",\"智能客服智能体\",\"文档问答助手架构\"],\"covered_knowledge\":[\"微服务架构\",\"智能体编排\",\"异步消息驱动\",\"状态管理\",\"可观测性\"],\"writing_style\":\"技术务实，结合案例，逐步深入\"},\"next_article\":{\"title\":\"AI原生应用的架构设计：从单体到智能体编排\",\"content\":\"在前一篇文章中，我们探讨了AI原生应用与传统软件的根本差异，以及工程化落地的核心挑战。今天，我们将深入技术架构层面，讨论如何设计一个既能支撑AI能力（如大模型推理、数据流处理）又能保持工程可维护性的应用架构。\\n\\n### 从单体到微服务：AI场景的演进\\n\\n传统的单体架构在AI应用中往往显得力不从心。例如，一个简单的聊天机器人，如果将所有逻辑（前端、后端、模型推理、知识库检索）都放在一个进程中，当模型推理耗时较长时，整个应用会阻塞；当需要扩展推理能力时，只能整体复制，导致资源浪费。\\n\\n因此，AI原生应用通常采用微服务架构，将不同职责分离：\\n- **推理服务**：负责调用大模型API或本地模型，提供文本生成、图像识别等能力。\\n- **知识库服务**：管理向量数据库，处理文档嵌入、相似性检索。\\n- **编排服务**：协调多个AI组件，实现复杂工作流（如多轮对话、工具调用）。\\n- **网关服务**：负责认证、限流、请求路由。\\n\\n### 智能体编排：AI应用的核心模式\\n\\n更进一步，AI原生应用的核心不再是传统的请求-响应模式，而是**智能体编排（Agent Orchestration）**。智能体是一个拥有自主决策能力的模块，它能够根据用户输入，动态选择工具、调用外部API、组合多个模型输出。\\n\\n例如，一个“智能客服”智能体：\\n1. 接收用户问题“我的订单什么时候到？”\\n2. 调用意图识别模型，判断需要查询订单状态。\\n3. 调用订单API获取实时数据。\\n4. 调用大模型生成自然语言回复。\\n5. 返回给用户。\\n\\n这种编排模式要求架构支持：\\n- **异步消息驱动**：避免长任务阻塞。\\n- **状态管理**：智能体需要记住对话上下文和中间结果。\\n- **可观测性**：追踪每个决策路径，便于调试和优化。\\n\\n### 实战案例：一个简单的智能体架构\\n\\n假设我们构建一个“文档问答助手”，架构如下：\\n- **用户请求** → **网关** → **编排服务**（使用LangChain或自研框架）\\n- 编排服务：\\n  - 调用**意图识别模型**（轻量级BERT）判断用户意图（查询、总结、翻译）。\\n  - 根据意图，调用**知识库服务**检索相关文档片段。\\n  - 调用**大模型服务**（如GPT-4）生成答案。\\n  - 返回结果。\\n- 所有服务通过**消息队列**（如RabbitMQ）解耦，确保高可用。\\n\\n### 架构决策要点\\n\\n在设计AI原生应用架构时，需权衡以下几点：\\n1. **延迟 vs 准确性**：实时推理需要低延迟，但复杂模型可能更准。可考虑缓存或预计算。\\n2. **成本 vs 性能**：大模型API调用成本高，可引入本地小模型做预筛选。\\n3. **灵活性 vs 可维护性**：智能体编排增加灵活性，但需定义清晰的接口和错误处理机制。\\n\\n### 小结\\n\\nAI原生应用的架构核心是**微服务+智能体编排**，它让AI能力像乐高积木一样可组合、可扩展。下一篇文章，我们将深入讨论数据管道设计：如何构建高效、可靠的训练和推理数据流。\\n\\n（本文为系列第2篇，后续将涉及数据工程、模型部署、监控等主题。）\"},\"seriesState\":\"{\\n  \\\"next_article\\\": {\\n    \\\"title\\\": \\\"AI原生应用的架构设计：从单体到智能体编排\\\",\\n    \\\"content\\\": \\\"在前一篇文章中，我们探讨了AI原生应用与传统软件的根本差异，以及工程化落地的核心挑战。今天，我们将深入技术架构层面，讨论如何设计一个既能支撑AI能力（如大模型推理、数据流处理）又能保持工程可维护性的应用架构。\\\\n\\\\n### 从单体到微服务：AI场景的演进\\\\n\\\\n传统的单体架构在AI应用中往往显得力不从心。例如，一个简单的聊天机器人，如果将所有逻辑（前端、后端、模型推理、知识库检索）都放在一个进程中，当模型推理耗时较长时，整个应用会阻塞；当需要扩展推理能力时，只能整体复制，导致资源浪费。\\\\n\\\\n因此，AI原生应用通常采用微服务架构，将不同职责分离：\\\\n- **推理服务**：负责调用大模型API或本地模型，提供文本生成、图像识别等能力。\\\\n- **知识库服务**：管理向量数据库，处理文档嵌入、相似性检索。\\\\n- **编排服务**：协调多个AI组件，实现复杂工作流（如多轮对话、工具调用）。\\\\n- **网关服务**：负责认证、限流、请求路由。\\\\n\\\\n### 智能体编排：AI应用的核心模式\\\\n\\\\n更进一步，AI原生应用的核心不再是传统的请求-响应模式，而是**智能体编排（Agent Orchestration）**。智能体是一个拥有自主决策能力的模块，它能够根据用户输入，动态选择工具、调用外部API、组合多个模型输出。\\\\n\\\\n例如，一个“智能客服”智能体：\\\\n1. 接收用户问题“我的订单什么时候到？”\\\\n2. 调用意图识别模型，判断需要查询订单状态。\\\\n3. 调用订单API获取实时数据。\\\\n4. 调用大模型生成自然语言回复。\\\\n5. 返回给用户。\\\\n\\\\n这种编排模式要求架构支持：\\\\n- **异步消息驱动**：避免长任务阻塞。\\\\n- **状态管理**：智能体需要记住对话上下文和中间结果。\\\\n- **可观测性**：追踪每个决策路径，便于调试和优化。\\\\n\\\\n### 实战案例：一个简单的智能体架构\\\\n\\\\n假设我们构建一个“文档问答助手”，架构如下：\\\\n- **用户请求** → **网关** → **编排服务**（使用LangChain或自研框架）\\\\n- 编排服务：\\\\n  - 调用**意图识别模型**（轻量级BERT）判断用户意图（查询、总结、翻译）。\\\\n  - 根据意图，调用**知识库服务**检索相关文档片段。\\\\n  - 调用**大模型服务**（如GPT-4）生成答案。\\\\n  - 返回结果。\\\\n- 所有服务通过**消息队列**（如RabbitMQ）解耦，确保高可用。\\\\n\\\\n### 架构决策要点\\\\n\\\\n在设计AI原生应用架构时，需权衡以下几点：\\\\n1. **延迟 vs 准确性**：实时推理需要低延迟，但复杂模型可能更准。可考虑缓存或预计算。\\\\n2. **成本 vs 性能**：大模型API调用成本高，可引入本地小模型做预筛选。\\\\n3. **灵活性 vs 可维护性**：智能体编排增加灵活性，但需定义清晰的接口和错误处理机制。\\\\n\\\\n### 小结\\\\n\\\\nAI原生应用的架构核心是**微服务+智能体编排**，它让AI能力像乐高积木一样可组合、可扩展。下一篇文章，我们将深入讨论数据管道设计：如何构建高效、可靠的训练和推理数据流。\\\\n\\\\n（本文为系列第2篇，后续将涉及数据工程、模型部署、监控等主题。）\\\"\\n  },\\n  \\\"outline_update\\\": {\\n    \\\"changes\\\": \\\"无变化，大纲已涵盖本内容。\\\",\\n    \\\"updated_outline\\\": \\\"1. AI原生应用工程化落地：引言与核心挑战（已完成）\\\\n2. AI原生应用的架构设计：从单体到智能体编排（当前）\\\\n3. 数据管道设计：构建高效、可靠的AI数据流（计划中）\\\\n4. 模型部署与推理优化：从实验到生产（计划中）\\\\n5. 监控与可观测性：AI应用的生命线（计划中）\\\\n6. 安全与伦理：AI原生应用的边界（计划中）\\\\n7. 总结与未来展望（计划中）\\\"\\n  },\\n  \\\"accumulated_update\\\": {\\n    \\\"used_examples\\\": [\\\"聊天机器人单体架构问题\\\", \\\"智能客服智能体\\\", \\\"文档问答助手架构\\\"],\\n    \\\"covered_knowledge\\\": [\\\"微服务架构\\\", \\\"智能体编排\\\", \\\"异步消息驱动\\\", \\\"状态管理\\\", \\\"可观测性\\\"],\\n    \\\"writing_style\\\": \\\"技术务实，结合案例，逐步深入\\\"\\n  },\\n  \\\"series_complete\\\": false\\n}\"}', 2, NULL, 2, NULL, NULL, 0, NULL, '2026-07-09 15:37:04', '2026-07-09 15:37:04');
INSERT INTO `ai_agent_instance` VALUES (23, 'blog_series_writer-1e8144b4f8324e9f94f2747935c69eca', 'blog_series_writer', 1, 'blog_series', 1, '{\"flowCode\":\"blog_series\",\"name\":\"博客系列生成\",\"description\":\"按系列大纲逐篇生成博客，边写边规划\",\"version\":1,\"defaultProfileCode\":\"DS-V3-001\",\"engineType\":\"STATE_MACHINE\",\"maxTransitions\":100,\"maxAgentDepth\":8,\"webhookUrl\":null,\"nodes\":[{\"nodeCode\":\"node_1\",\"name\":\"开始\",\"nodeType\":\"START\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"__x6\":{\"x\":120,\"y\":200},\"inputs\":{\"topic\":\"系列主题\",\"history\":\"已写文章索引\",\"outline\":\"上一轮大纲\",\"prevSeq\":\"上一篇序号\",\"accumulated\":\"累积状态\"}},\"rememberTrace\":false,\"stateType\":\"ENTRY\",\"sortNo\":0},{\"nodeCode\":\"node_2\",\"name\":\"写一篇并更新大纲\",\"nodeType\":\"PROMPT\",\"systemPrompt\":\"你是一个技术博客连载作者。围绕系列主题《{{topic}}》持续创作。\\n\\n已有整体大纲：{{outline}}\\n累积写作状态（已用例子/已讲知识点/风格）：{{accumulated}}\\n已写文章索引：{{history}}\\n这是第 {{prevSeq}} 篇之后的下一篇。\\n\\n请：\\n1. 基于大纲与累积状态，决定并撰写下一篇文章（承接前文、不重复已讲内容）。\\n2. 如有需要，微调整体大纲（边写边规划）。\\n3. 更新累积写作状态。\\n4. 判断系列是否已写完。\\n\\n只输出 JSON，无其它文字：\",\"promptTemplate\":\"请生成下一篇。\",\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":0.8,\"maxTokens\":4096,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"seriesState\",\"outputMode\":\"JSON\",\"nodeConfig\":{\"llm\":{\"output\":{\"type\":\"JSON\",\"jsonSchema\":\"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"articleTitle\\\":{\\\"type\\\":\\\"string\\\"},\\\"articleBody\\\":{\\\"type\\\":\\\"string\\\"},\\\"outline\\\":{\\\"type\\\":\\\"string\\\"},\\\"accumulated\\\":{\\\"type\\\":\\\"string\\\"},\\\"outlineDone\\\":{\\\"type\\\":\\\"string\\\",\\\"description\\\":\\\"全系列写完置 true，否则 false\\\"}},\\\"required\\\":[\\\"articleTitle\\\",\\\"articleBody\\\",\\\"outline\\\",\\\"accumulated\\\",\\\"outlineDone\\\"]}\"},\"prompt\":{\"systemPrompt\":\"你是一个技术博客连载作者。围绕系列主题《{{topic}}》持续创作。\\n\\n已有整体大纲：{{outline}}\\n累积写作状态（已用例子/已讲知识点/风格）：{{accumulated}}\\n已写文章索引：{{history}}\\n这是第 {{prevSeq}} 篇之后的下一篇。\\n\\n请：\\n1. 基于大纲与累积状态，决定并撰写下一篇文章（承接前文、不重复已讲内容）。\\n2. 如有需要，微调整体大纲（边写边规划）。\\n3. 更新累积写作状态。\\n4. 判断系列是否已写完。\\n\\n只输出 JSON，无其它文字：\",\"userPromptTemplate\":\"请生成下一篇。\"},\"parameters\":{\"mode\":\"basic\",\"basic\":{\"maxTokens\":4096,\"temperature\":0.8}}},\"__x6\":{\"x\":420,\"y\":200}},\"rememberTrace\":true,\"stateType\":\"NORMAL\",\"sortNo\":1},{\"nodeCode\":\"node_3\",\"name\":\"结束\",\"nodeType\":\"END\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"__x6\":{\"x\":760,\"y\":200}},\"rememberTrace\":false,\"stateType\":\"TERMINAL\",\"sortNo\":2}],\"edges\":[{\"fromNode\":\"node_1\",\"toNode\":\"node_2\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":0},{\"fromNode\":\"node_2\",\"toNode\":\"node_3\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":1}]}', NULL, NULL, 'SUCCESS', 'node_3', NULL, NULL, '{\"topic\":\"AI原生应用工程化落地\",\"prevSeq\":1}', '{\"outline_update\":{\"changes\":\"无调整，当前大纲仍适用。\",\"updated_outline\":\"已有大纲：{{outline}}\"},\"prevSeq\":1,\"__currentInstanceId\":\"blog_series_writer-1e8144b4f8324e9f94f2747935c69eca\",\"series_status\":\"未完成，还有后续文章待撰写。\",\"__maxAgentDepth\":8,\"topic\":\"AI原生应用工程化落地\",\"accumulated_update\":{\"used_examples\":\"智能推荐系统迁移案例\",\"covered_topics\":\"单体架构局限、微服务优势、进化路径、实战案例\",\"writing_style\":\"技术分析为主，结合案例，保持清晰结构\"},\"article\":{\"title\":\"AI原生应用架构：从单体到微服务的进化路径\",\"content\":\"在上一篇文章中，我们探讨了AI原生应用的基础概念和核心原则，强调了数据驱动、模型即服务和持续迭代的重要性。今天，我们将深入架构层面，分析从传统单体应用到AI原生微服务架构的进化路径，这是实现工程化落地的关键一步。\\n\\n## 单体架构的局限\\n传统AI应用常采用单体架构：所有组件（数据预处理、模型推理、业务逻辑）耦合在一个进程中。这种模式在小规模场景下可行，但随着业务复杂度和数据量的增长，问题逐渐显现：\\n- **扩展性差**：无法独立扩展推理或数据组件，导致资源浪费。\\n- **部署困难**：任何修改都需要重新部署整个应用，影响迭代速度。\\n- **维护成本高**：团队协作时，代码冲突和依赖管理成为瓶颈。\\n\\n## 微服务架构的优势\\nAI原生应用推荐采用微服务架构，将系统拆分为独立服务，每个服务负责特定功能，如：\\n- **数据服务**：处理数据采集、清洗和特征工程。\\n- **模型服务**：封装模型推理逻辑，提供RESTful或gRPC接口。\\n- **业务服务**：协调流程，调用模型服务并返回结果。\\n- **监控服务**：跟踪性能、日志和模型漂移。\\n\\n这种架构带来灵活性：\\n- **独立扩展**：根据负载，仅扩展模型服务实例，节省成本。\\n- **技术异构**：不同服务可用不同语言或框架（如Python用于模型，Go用于业务）。\\n- **快速迭代**：单个服务可独立部署，不影响全局。\\n\\n## 进化路径：从单体到微服务\\n迁移不是一蹴而就，建议分步进行：\\n1. **识别边界**：分析现有单体，划分功能模块（如数据、模型、业务）。\\n2. **提取模型服务**：优先将模型推理抽离为独立服务，使用容器化部署（如Docker）。\\n3. **引入API网关**：统一入口，管理路由、限流和认证。\\n4. **逐步解耦**：依次提取数据服务和业务服务，确保接口稳定。\\n5. **完善监控**：集成日志聚合和指标收集（如Prometheus、ELK）。\\n\\n## 实战案例：智能推荐系统\\n假设我们有一个单体推荐系统，包含用户画像、模型推理和结果排序。迁移后：\\n- 用户画像服务：处理实时特征。\\n- 模型服务：调用深度学习模型，返回候选列表。\\n- 排序服务：基于规则重排。\\n- 网关：接收请求，分发到各服务。\\n\\n这样，当流量高峰时，我们只需扩展模型服务，而其他服务保持稳定。\\n\\n## 注意事项\\n- **服务间通信**：选择轻量协议（gRPC优于HTTP），避免过重。\\n- **数据一致性**：使用事件驱动或分布式事务，确保最终一致。\\n- **版本管理**：模型服务需支持多版本，便于回滚。\\n\\n下一篇文章，我们将讨论如何设计可复用的模型服务层，包括模型加载、推理优化和动态更新。\",\"summary\":\"本文介绍了AI原生应用从单体架构向微服务架构的进化路径，分析了单体架构的局限和微服务的优势，并给出了具体的迁移步骤和实战案例。\"},\"seriesState\":\"{\\n  \\\"article\\\": {\\n    \\\"title\\\": \\\"AI原生应用架构：从单体到微服务的进化路径\\\",\\n    \\\"content\\\": \\\"在上一篇文章中，我们探讨了AI原生应用的基础概念和核心原则，强调了数据驱动、模型即服务和持续迭代的重要性。今天，我们将深入架构层面，分析从传统单体应用到AI原生微服务架构的进化路径，这是实现工程化落地的关键一步。\\\\n\\\\n## 单体架构的局限\\\\n传统AI应用常采用单体架构：所有组件（数据预处理、模型推理、业务逻辑）耦合在一个进程中。这种模式在小规模场景下可行，但随着业务复杂度和数据量的增长，问题逐渐显现：\\\\n- **扩展性差**：无法独立扩展推理或数据组件，导致资源浪费。\\\\n- **部署困难**：任何修改都需要重新部署整个应用，影响迭代速度。\\\\n- **维护成本高**：团队协作时，代码冲突和依赖管理成为瓶颈。\\\\n\\\\n## 微服务架构的优势\\\\nAI原生应用推荐采用微服务架构，将系统拆分为独立服务，每个服务负责特定功能，如：\\\\n- **数据服务**：处理数据采集、清洗和特征工程。\\\\n- **模型服务**：封装模型推理逻辑，提供RESTful或gRPC接口。\\\\n- **业务服务**：协调流程，调用模型服务并返回结果。\\\\n- **监控服务**：跟踪性能、日志和模型漂移。\\\\n\\\\n这种架构带来灵活性：\\\\n- **独立扩展**：根据负载，仅扩展模型服务实例，节省成本。\\\\n- **技术异构**：不同服务可用不同语言或框架（如Python用于模型，Go用于业务）。\\\\n- **快速迭代**：单个服务可独立部署，不影响全局。\\\\n\\\\n## 进化路径：从单体到微服务\\\\n迁移不是一蹴而就，建议分步进行：\\\\n1. **识别边界**：分析现有单体，划分功能模块（如数据、模型、业务）。\\\\n2. **提取模型服务**：优先将模型推理抽离为独立服务，使用容器化部署（如Docker）。\\\\n3. **引入API网关**：统一入口，管理路由、限流和认证。\\\\n4. **逐步解耦**：依次提取数据服务和业务服务，确保接口稳定。\\\\n5. **完善监控**：集成日志聚合和指标收集（如Prometheus、ELK）。\\\\n\\\\n## 实战案例：智能推荐系统\\\\n假设我们有一个单体推荐系统，包含用户画像、模型推理和结果排序。迁移后：\\\\n- 用户画像服务：处理实时特征。\\\\n- 模型服务：调用深度学习模型，返回候选列表。\\\\n- 排序服务：基于规则重排。\\\\n- 网关：接收请求，分发到各服务。\\\\n\\\\n这样，当流量高峰时，我们只需扩展模型服务，而其他服务保持稳定。\\\\n\\\\n## 注意事项\\\\n- **服务间通信**：选择轻量协议（gRPC优于HTTP），避免过重。\\\\n- **数据一致性**：使用事件驱动或分布式事务，确保最终一致。\\\\n- **版本管理**：模型服务需支持多版本，便于回滚。\\\\n\\\\n下一篇文章，我们将讨论如何设计可复用的模型服务层，包括模型加载、推理优化和动态更新。\\\",\\n    \\\"summary\\\": \\\"本文介绍了AI原生应用从单体架构向微服务架构的进化路径，分析了单体架构的局限和微服务的优势，并给出了具体的迁移步骤和实战案例。\\\"\\n  },\\n  \\\"outline_update\\\": {\\n    \\\"changes\\\": \\\"无调整，当前大纲仍适用。\\\",\\n    \\\"updated_outline\\\": \\\"已有大纲：{{outline}}\\\"\\n  },\\n  \\\"accumulated_update\\\": {\\n    \\\"used_examples\\\": \\\"智能推荐系统迁移案例\\\",\\n    \\\"covered_topics\\\": \\\"单体架构局限、微服务优势、进化路径、实战案例\\\",\\n    \\\"writing_style\\\": \\\"技术分析为主，结合案例，保持清晰结构\\\"\\n  },\\n  \\\"series_status\\\": \\\"未完成，还有后续文章待撰写。\\\"\\n}\"}', 2, NULL, 2, NULL, NULL, 0, NULL, '2026-07-09 15:39:18', '2026-07-09 15:39:18');
INSERT INTO `ai_agent_instance` VALUES (24, 'blog_series-1cc9d41d02f746bfb442e7cc8f27cbf0', 'blog_series', 1, 'blog_series', 1, '{\"flowCode\":\"blog_series\",\"name\":\"博客系列生成\",\"description\":\"按系列大纲逐篇生成博客，边写边规划\",\"version\":1,\"defaultProfileCode\":\"DS-V3-001\",\"engineType\":\"STATE_MACHINE\",\"maxTransitions\":100,\"maxAgentDepth\":8,\"webhookUrl\":null,\"nodes\":[{\"nodeCode\":\"node_1\",\"name\":\"开始\",\"nodeType\":\"START\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"__x6\":{\"x\":120,\"y\":200},\"inputs\":{\"topic\":\"系列主题\",\"history\":\"已写文章索引\",\"outline\":\"上一轮大纲\",\"prevSeq\":\"上一篇序号\",\"accumulated\":\"累积状态\"}},\"rememberTrace\":false,\"stateType\":\"ENTRY\",\"sortNo\":0},{\"nodeCode\":\"node_2\",\"name\":\"写一篇并更新大纲\",\"nodeType\":\"PROMPT\",\"systemPrompt\":\"你是一个技术博客连载作者。围绕系列主题《{{topic}}》持续创作。\\n\\n已有整体大纲：{{outline}}\\n累积写作状态（已用例子/已讲知识点/风格）：{{accumulated}}\\n已写文章索引：{{history}}\\n这是第 {{prevSeq}} 篇之后的下一篇。\\n\\n请：\\n1. 基于大纲与累积状态，决定并撰写下一篇文章（承接前文、不重复已讲内容）。\\n2. 如有需要，微调整体大纲（边写边规划）。\\n3. 更新累积写作状态。\\n4. 判断系列是否已写完。\\n\\n只输出 JSON，无其它文字：\",\"promptTemplate\":\"请生成下一篇。\",\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":0.8,\"maxTokens\":4096,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"seriesState\",\"outputMode\":\"JSON\",\"nodeConfig\":{\"llm\":{\"output\":{\"type\":\"JSON\",\"jsonSchema\":\"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"articleTitle\\\":{\\\"type\\\":\\\"string\\\"},\\\"articleBody\\\":{\\\"type\\\":\\\"string\\\"},\\\"outline\\\":{\\\"type\\\":\\\"string\\\"},\\\"accumulated\\\":{\\\"type\\\":\\\"string\\\"},\\\"outlineDone\\\":{\\\"type\\\":\\\"string\\\",\\\"description\\\":\\\"全系列写完置 true，否则 false\\\"}},\\\"required\\\":[\\\"articleTitle\\\",\\\"articleBody\\\",\\\"outline\\\",\\\"accumulated\\\",\\\"outlineDone\\\"]}\"},\"prompt\":{\"systemPrompt\":\"你是一个技术博客连载作者。围绕系列主题《{{topic}}》持续创作。\\n\\n已有整体大纲：{{outline}}\\n累积写作状态（已用例子/已讲知识点/风格）：{{accumulated}}\\n已写文章索引：{{history}}\\n这是第 {{prevSeq}} 篇之后的下一篇。\\n\\n请：\\n1. 基于大纲与累积状态，决定并撰写下一篇文章（承接前文、不重复已讲内容）。\\n2. 如有需要，微调整体大纲（边写边规划）。\\n3. 更新累积写作状态。\\n4. 判断系列是否已写完。\\n\\n只输出 JSON，无其它文字：\",\"userPromptTemplate\":\"请生成下一篇。\"},\"parameters\":{\"mode\":\"basic\",\"basic\":{\"maxTokens\":4096,\"temperature\":0.8}}},\"__x6\":{\"x\":420,\"y\":200}},\"rememberTrace\":true,\"stateType\":\"NORMAL\",\"sortNo\":1},{\"nodeCode\":\"node_3\",\"name\":\"结束\",\"nodeType\":\"END\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"__x6\":{\"x\":760,\"y\":200}},\"rememberTrace\":false,\"stateType\":\"TERMINAL\",\"sortNo\":2}],\"edges\":[{\"fromNode\":\"node_1\",\"toNode\":\"node_2\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":0},{\"fromNode\":\"node_2\",\"toNode\":\"node_3\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":1}]}', NULL, NULL, 'SUCCESS', 'node_3', NULL, NULL, NULL, '{\"is_series_finished\":false,\"__currentInstanceId\":\"blog_series-1cc9d41d02f746bfb442e7cc8f27cbf0\",\"__maxAgentDepth\":8,\"next_seq\":2,\"updated_accumulated\":{\"used_examples\":[\"MNIST手写数字识别\"],\"covered_knowledge\":[\"数据预处理（归一化、one-hot）\",\"三层神经网络结构设计\",\"前向传播与反向传播\",\"ReLU和Softmax激活函数\",\"小批量梯度下降\",\"权重初始化（He初始化）\",\"模型评估与可视化\"],\"writing_style\":\"实践导向，包含完整代码示例和解释\"},\"title\":\"从零开始：用Python搭建你的第一个神经网络\",\"updated_outline\":[\"1. 神经网络基础概念（神经元、激活函数、层结构）\",\"2. 从零搭建神经网络（Python实现，MNIST示例）\",\"3. 优化网络性能（正则化、学习率调整、优化器）\",\"4. 卷积神经网络入门（CNN原理与实践）\",\"5. 循环神经网络入门（RNN与LSTM）\",\"6. 实战项目：图像分类与文本生成\"],\"content\":\"在前一篇文章中，我们了解了神经网络的基本概念，包括神经元、激活函数和层结构。现在，让我们动手实践，用Python从零搭建一个简单的神经网络，实现手写数字识别（MNIST数据集）。\\n\\n## 环境准备\\n首先，确保你安装了Python和必要的库。我们将使用numpy进行矩阵运算，matplotlib进行可视化。\\n```python\\nimport numpy as np\\nimport matplotlib.pyplot as plt\\nfrom sklearn.datasets import fetch_openml\\nfrom sklearn.model_selection import train_test_split\\nfrom sklearn.preprocessing import OneHotEncoder\\n```\\n\\n## 加载和预处理数据\\nMNIST数据集包含28x28像素的手写数字图片，共10个类别（0-9）。\\n```python\\n# 加载数据（首次运行会下载，约11MB）\\nX, y = fetch_openml(\'mnist_784\', version=1, return_X_y=True, as_frame=False)\\nX = X / 255.0  # 归一化到0-1\\n# 转换为one-hot编码\\nencoder = OneHotEncoder(sparse=False)\\ny_onehot = encoder.fit_transform(y.reshape(-1, 1))\\n# 划分训练集和测试集\\nX_train, X_test, y_train, y_test = train_test_split(X, y_onehot, test_size=0.2, random_state=42)\\n```\\n\\n## 定义神经网络结构\\n我们构建一个简单的三层网络：输入层（784个神经元，对应28x28像素）、隐藏层（128个神经元，使用ReLU激活函数）、输出层（10个神经元，使用Softmax激活函数）。\\n```python\\nclass NeuralNetwork:\\n    def __init__(self, input_size, hidden_size, output_size, learning_rate=0.01):\\n        self.lr = learning_rate\\n        # 初始化权重和偏置（使用He初始化）\\n        self.W1 = np.random.randn(input_size, hidden_size) * np.sqrt(2.0/input_size)\\n        self.b1 = np.zeros((1, hidden_size))\\n        self.W2 = np.random.randn(hidden_size, output_size) * np.sqrt(2.0/hidden_size)\\n        self.b2 = np.zeros((1, output_size))\\n    \\n    def relu(self, x):\\n        return np.maximum(0, x)\\n    \\n    def softmax(self, x):\\n        exp_x = np.exp(x - np.max(x, axis=1, keepdims=True))\\n        return exp_x / np.sum(exp_x, axis=1, keepdims=True)\\n    \\n    def forward(self, X):\\n        # 前向传播\\n        self.z1 = np.dot(X, self.W1) + self.b1\\n        self.a1 = self.relu(self.z1)\\n        self.z2 = np.dot(self.a1, self.W2) + self.b2\\n        self.a2 = self.softmax(self.z2)\\n        return self.a2\\n    \\n    def backward(self, X, y, output):\\n        # 反向传播（交叉熵损失）\\n        m = X.shape[0]\\n        dz2 = output - y  # 输出层梯度\\n        dW2 = np.dot(self.a1.T, dz2) / m\\n        db2 = np.sum(dz2, axis=0, keepdims=True) / m\\n        \\n        dz1 = np.dot(dz2, self.W2.T) * (self.z1 > 0)  # ReLU导数\\n        dW1 = np.dot(X.T, dz1) / m\\n        db1 = np.sum(dz1, axis=0, keepdims=True) / m\\n        \\n        # 更新参数\\n        self.W2 -= self.lr * dW2\\n        self.b2 -= self.lr * db2\\n        self.W1 -= self.lr * dW1\\n        self.b1 -= self.lr * db1\\n    \\n    def train(self, X, y, epochs=100, batch_size=32, verbose=True):\\n        n = X.shape[0]\\n        for epoch in range(epochs):\\n            # 随机打乱数据\\n            indices = np.random.permutation(n)\\n            X_shuffled = X[indices]\\n            y_shuffled = y[indices]\\n            \\n            for i in range(0, n, batch_size):\\n                X_batch = X_shuffled[i:i+batch_size]\\n                y_batch = y_shuffled[i:i+batch_size]\\n                output = self.forward(X_batch)\\n                self.backward(X_batch, y_batch, output)\\n            \\n            if verbose and (epoch+1) % 10 == 0:\\n                loss = -np.mean(np.sum(y * np.log(self.forward(X) + 1e-8), axis=1))\\n                acc = np.mean(np.argmax(self.forward(X), axis=1) == np.argmax(y, axis=1))\\n                print(f\\\"Epoch {epoch+1}/{epochs}, Loss: {loss:.4f}, Accuracy: {acc:.4f}\\\")\\n```\\n\\n## 训练模型\\n```python\\n# 实例化网络\\nnn = NeuralNetwork(input_size=784, hidden_size=128, output_size=10, learning_rate=0.01)\\n# 训练（由于计算量较大，这里只训练50个epoch作为演示）\\nnn.train(X_train, y_train, epochs=50, batch_size=64)\\n```\\n\\n## 评估模型\\n```python\\n# 测试集准确率\\ntest_output = nn.forward(X_test)\\npredictions = np.argmax(test_output, axis=1)\\ntrue_labels = np.argmax(y_test, axis=1)\\naccuracy = np.mean(predictions == true_labels)\\nprint(f\\\"Test Accuracy: {accuracy:.4f}\\\")\\n\\n# 可视化一些预测结果\\nfig, axes = plt.subplots(2, 5, figsize=(10, 4))\\nfor i, ax in enumerate(axes.flat):\\n    ax.imshow(X_test[i].reshape(28, 28), cmap=\'gray\')\\n    ax.set_title(f\\\"True: {true_labels[i]}, Pred: {predictions[i]}\\\")\\n    ax.axis(\'off\')\\nplt.tight_layout()\\nplt.show()\\n```\\n\\n## 关键点总结\\n- 数据预处理：归一化和one-hot编码对训练至关重要。\\n- 权重初始化：He初始化适用于ReLU激活函数，避免梯度消失/爆炸。\\n- 小批量梯度下降：平衡了训练速度和稳定性。\\n- 反向传播：通过链式法则计算梯度，更新参数。\\n\\n## 练习与思考\\n1. 尝试调整隐藏层神经元数量（如64、256），观察准确率变化。\\n2. 修改激活函数为sigmoid或tanh，比较效果。\\n3. 添加一个额外的隐藏层，实现更深的网络。\\n\\n下一篇文章中，我们将探讨如何优化网络性能，包括正则化、学习率调整和更高级的优化器。\",\"seriesState\":\"{\\n  \\\"next_seq\\\": 2,\\n  \\\"title\\\": \\\"从零开始：用Python搭建你的第一个神经网络\\\",\\n  \\\"content\\\": \\\"在前一篇文章中，我们了解了神经网络的基本概念，包括神经元、激活函数和层结构。现在，让我们动手实践，用Python从零搭建一个简单的神经网络，实现手写数字识别（MNIST数据集）。\\\\n\\\\n## 环境准备\\\\n首先，确保你安装了Python和必要的库。我们将使用numpy进行矩阵运算，matplotlib进行可视化。\\\\n```python\\\\nimport numpy as np\\\\nimport matplotlib.pyplot as plt\\\\nfrom sklearn.datasets import fetch_openml\\\\nfrom sklearn.model_selection import train_test_split\\\\nfrom sklearn.preprocessing import OneHotEncoder\\\\n```\\\\n\\\\n## 加载和预处理数据\\\\nMNIST数据集包含28x28像素的手写数字图片，共10个类别（0-9）。\\\\n```python\\\\n# 加载数据（首次运行会下载，约11MB）\\\\nX, y = fetch_openml(\'mnist_784\', version=1, return_X_y=True, as_frame=False)\\\\nX = X / 255.0  # 归一化到0-1\\\\n# 转换为one-hot编码\\\\nencoder = OneHotEncoder(sparse=False)\\\\ny_onehot = encoder.fit_transform(y.reshape(-1, 1))\\\\n# 划分训练集和测试集\\\\nX_train, X_test, y_train, y_test = train_test_split(X, y_onehot, test_size=0.2, random_state=42)\\\\n```\\\\n\\\\n## 定义神经网络结构\\\\n我们构建一个简单的三层网络：输入层（784个神经元，对应28x28像素）、隐藏层（128个神经元，使用ReLU激活函数）、输出层（10个神经元，使用Softmax激活函数）。\\\\n```python\\\\nclass NeuralNetwork:\\\\n    def __init__(self, input_size, hidden_size, output_size, learning_rate=0.01):\\\\n        self.lr = learning_rate\\\\n        # 初始化权重和偏置（使用He初始化）\\\\n        self.W1 = np.random.randn(input_size, hidden_size) * np.sqrt(2.0/input_size)\\\\n        self.b1 = np.zeros((1, hidden_size))\\\\n        self.W2 = np.random.randn(hidden_size, output_size) * np.sqrt(2.0/hidden_size)\\\\n        self.b2 = np.zeros((1, output_size))\\\\n    \\\\n    def relu(self, x):\\\\n        return np.maximum(0, x)\\\\n    \\\\n    def softmax(self, x):\\\\n        exp_x = np.exp(x - np.max(x, axis=1, keepdims=True))\\\\n        return exp_x / np.sum(exp_x, axis=1, keepdims=True)\\\\n    \\\\n    def forward(self, X):\\\\n        # 前向传播\\\\n        self.z1 = np.dot(X, self.W1) + self.b1\\\\n        self.a1 = self.relu(self.z1)\\\\n        self.z2 = np.dot(self.a1, self.W2) + self.b2\\\\n        self.a2 = self.softmax(self.z2)\\\\n        return self.a2\\\\n    \\\\n    def backward(self, X, y, output):\\\\n        # 反向传播（交叉熵损失）\\\\n        m = X.shape[0]\\\\n        dz2 = output - y  # 输出层梯度\\\\n        dW2 = np.dot(self.a1.T, dz2) / m\\\\n        db2 = np.sum(dz2, axis=0, keepdims=True) / m\\\\n        \\\\n        dz1 = np.dot(dz2, self.W2.T) * (self.z1 > 0)  # ReLU导数\\\\n        dW1 = np.dot(X.T, dz1) / m\\\\n        db1 = np.sum(dz1, axis=0, keepdims=True) / m\\\\n        \\\\n        # 更新参数\\\\n        self.W2 -= self.lr * dW2\\\\n        self.b2 -= self.lr * db2\\\\n        self.W1 -= self.lr * dW1\\\\n        self.b1 -= self.lr * db1\\\\n    \\\\n    def train(self, X, y, epochs=100, batch_size=32, verbose=True):\\\\n        n = X.shape[0]\\\\n        for epoch in range(epochs):\\\\n            # 随机打乱数据\\\\n            indices = np.random.permutation(n)\\\\n            X_shuffled = X[indices]\\\\n            y_shuffled = y[indices]\\\\n            \\\\n            for i in range(0, n, batch_size):\\\\n                X_batch = X_shuffled[i:i+batch_size]\\\\n                y_batch = y_shuffled[i:i+batch_size]\\\\n                output = self.forward(X_batch)\\\\n                self.backward(X_batch, y_batch, output)\\\\n            \\\\n            if verbose and (epoch+1) % 10 == 0:\\\\n                loss = -np.mean(np.sum(y * np.log(self.forward(X) + 1e-8), axis=1))\\\\n                acc = np.mean(np.argmax(self.forward(X), axis=1) == np.argmax(y, axis=1))\\\\n                print(f\\\\\\\"Epoch {epoch+1}/{epochs}, Loss: {loss:.4f}, Accuracy: {acc:.4f}\\\\\\\")\\\\n```\\\\n\\\\n## 训练模型\\\\n```python\\\\n# 实例化网络\\\\nnn = NeuralNetwork(input_size=784, hidden_size=128, output_size=10, learning_rate=0.01)\\\\n# 训练（由于计算量较大，这里只训练50个epoch作为演示）\\\\nnn.train(X_train, y_train, epochs=50, batch_size=64)\\\\n```\\\\n\\\\n## 评估模型\\\\n```python\\\\n# 测试集准确率\\\\ntest_output = nn.forward(X_test)\\\\npredictions = np.argmax(test_output, axis=1)\\\\ntrue_labels = np.argmax(y_test, axis=1)\\\\naccuracy = np.mean(predictions == true_labels)\\\\nprint(f\\\\\\\"Test Accuracy: {accuracy:.4f}\\\\\\\")\\\\n\\\\n# 可视化一些预测结果\\\\nfig, axes = plt.subplots(2, 5, figsize=(10, 4))\\\\nfor i, ax in enumerate(axes.flat):\\\\n    ax.imshow(X_test[i].reshape(28, 28), cmap=\'gray\')\\\\n    ax.set_title(f\\\\\\\"True: {true_labels[i]}, Pred: {predictions[i]}\\\\\\\")\\\\n    ax.axis(\'off\')\\\\nplt.tight_layout()\\\\nplt.show()\\\\n```\\\\n\\\\n## 关键点总结\\\\n- 数据预处理：归一化和one-hot编码对训练至关重要。\\\\n- 权重初始化：He初始化适用于ReLU激活函数，避免梯度消失/爆炸。\\\\n- 小批量梯度下降：平衡了训练速度和稳定性。\\\\n- 反向传播：通过链式法则计算梯度，更新参数。\\\\n\\\\n## 练习与思考\\\\n1. 尝试调整隐藏层神经元数量（如64、256），观察准确率变化。\\\\n2. 修改激活函数为sigmoid或tanh，比较效果。\\\\n3. 添加一个额外的隐藏层，实现更深的网络。\\\\n\\\\n下一篇文章中，我们将探讨如何优化网络性能，包括正则化、学习率调整和更高级的优化器。\\\",\\n  \\\"updated_outline\\\": [\\n    \\\"1. 神经网络基础概念（神经元、激活函数、层结构）\\\",\\n    \\\"2. 从零搭建神经网络（Python实现，MNIST示例）\\\",\\n    \\\"3. 优化网络性能（正则化、学习率调整、优化器）\\\",\\n    \\\"4. 卷积神经网络入门（CNN原理与实践）\\\",\\n    \\\"5. 循环神经网络入门（RNN与LSTM）\\\",\\n    \\\"6. 实战项目：图像分类与文本生成\\\"\\n  ],\\n  \\\"updated_accumulated\\\": {\\n    \\\"used_examples\\\": [\\\"MNIST手写数字识别\\\"],\\n    \\\"covered_knowledge\\\": [\\\"数据预处理（归一化、one-hot）\\\", \\\"三层神经网络结构设计\\\", \\\"前向传播与反向传播\\\", \\\"ReLU和Softmax激活函数\\\", \\\"小批量梯度下降\\\", \\\"权重初始化（He初始化）\\\", \\\"模型评估与可视化\\\"],\\n    \\\"writing_style\\\": \\\"实践导向，包含完整代码示例和解释\\\"\\n  },\\n  \\\"is_series_finished\\\": false\\n}\"}', 2, NULL, 2, NULL, NULL, 0, NULL, '2026-07-09 16:10:30', '2026-07-09 16:10:30');
INSERT INTO `ai_agent_instance` VALUES (25, 'blog_series-7341c7a6d5614cd5bb91d0573c95af05', 'blog_series', 1, 'blog_series', 1, '{\"flowCode\":\"blog_series\",\"name\":\"博客系列生成\",\"description\":\"按系列大纲逐篇生成博客，边写边规划\",\"version\":1,\"defaultProfileCode\":\"DS-V3-001\",\"engineType\":\"STATE_MACHINE\",\"maxTransitions\":100,\"maxAgentDepth\":8,\"webhookUrl\":null,\"nodes\":[{\"nodeCode\":\"node_1\",\"name\":\"开始\",\"nodeType\":\"START\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"__x6\":{\"x\":120,\"y\":200},\"inputs\":{\"topic\":\"系列主题\",\"history\":\"已写文章索引\",\"outline\":\"上一轮大纲\",\"prevSeq\":\"上一篇序号\",\"accumulated\":\"累积状态\"}},\"rememberTrace\":false,\"stateType\":\"ENTRY\",\"sortNo\":0},{\"nodeCode\":\"node_2\",\"name\":\"写一篇并更新大纲\",\"nodeType\":\"PROMPT\",\"systemPrompt\":\"你是一个技术博客连载作者。围绕系列主题《{{topic}}》持续创作。\\n\\n已有整体大纲：{{outline}}\\n累积写作状态（已用例子/已讲知识点/风格）：{{accumulated}}\\n已写文章索引：{{history}}\\n这是第 {{prevSeq}} 篇之后的下一篇。\\n\\n请：\\n1. 基于大纲与累积状态，决定并撰写下一篇文章（承接前文、不重复已讲内容）。\\n2. 如有需要，微调整体大纲（边写边规划）。\\n3. 更新累积写作状态。\\n4. 判断系列是否已写完。\\n\\n只输出 JSON，无其它文字：\",\"promptTemplate\":\"请生成下一篇。\",\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":0.8,\"maxTokens\":4096,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"seriesState\",\"outputMode\":\"JSON\",\"nodeConfig\":{\"llm\":{\"output\":{\"type\":\"JSON\",\"jsonSchema\":\"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"articleTitle\\\":{\\\"type\\\":\\\"string\\\"},\\\"articleBody\\\":{\\\"type\\\":\\\"string\\\"},\\\"outline\\\":{\\\"type\\\":\\\"string\\\"},\\\"accumulated\\\":{\\\"type\\\":\\\"string\\\"},\\\"outlineDone\\\":{\\\"type\\\":\\\"string\\\",\\\"description\\\":\\\"全系列写完置 true，否则 false\\\"}},\\\"required\\\":[\\\"articleTitle\\\",\\\"articleBody\\\",\\\"outline\\\",\\\"accumulated\\\",\\\"outlineDone\\\"]}\"},\"prompt\":{\"systemPrompt\":\"你是一个技术博客连载作者。围绕系列主题《{{topic}}》持续创作。\\n\\n已有整体大纲：{{outline}}\\n累积写作状态（已用例子/已讲知识点/风格）：{{accumulated}}\\n已写文章索引：{{history}}\\n这是第 {{prevSeq}} 篇之后的下一篇。\\n\\n请：\\n1. 基于大纲与累积状态，决定并撰写下一篇文章（承接前文、不重复已讲内容）。\\n2. 如有需要，微调整体大纲（边写边规划）。\\n3. 更新累积写作状态。\\n4. 判断系列是否已写完。\\n\\n只输出 JSON，无其它文字：\",\"userPromptTemplate\":\"请生成下一篇。\"},\"parameters\":{\"mode\":\"basic\",\"basic\":{\"maxTokens\":4096,\"temperature\":0.8}}},\"__x6\":{\"x\":420,\"y\":200}},\"rememberTrace\":true,\"stateType\":\"NORMAL\",\"sortNo\":1},{\"nodeCode\":\"node_3\",\"name\":\"结束\",\"nodeType\":\"END\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"__x6\":{\"x\":760,\"y\":200}},\"rememberTrace\":false,\"stateType\":\"TERMINAL\",\"sortNo\":2}],\"edges\":[{\"fromNode\":\"node_1\",\"toNode\":\"node_2\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":0},{\"fromNode\":\"node_2\",\"toNode\":\"node_3\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":1}]}', NULL, NULL, 'SUCCESS', 'node_3', NULL, NULL, '{\"topic\":\"AI原生应用工程化落地\",\"prevSeq\":1}', '{\"prevSeq\":1,\"__currentInstanceId\":\"blog_series-7341c7a6d5614cd5bb91d0573c95af05\",\"__maxAgentDepth\":8,\"topic\":\"AI原生应用工程化落地\",\"next_article\":{\"title\":\"AI原生应用的架构设计：从单体到智能体编排\",\"content\":\"## 前言\\n在上一篇文章中，我们探讨了AI原生应用与传统软件的本质区别，以及工程化落地的核心挑战。今天，我们将深入架构层面，解析AI原生应用如何从传统的单体架构演进为智能体（Agent）编排模式，并讨论这种转变背后的设计原则与实战经验。\\n\\n## 单体架构的局限\\n传统的单体应用将所有功能模块（包括AI模型调用）打包在一个进程中。虽然初期开发简单，但随着AI能力增强，单体架构暴露出几个问题：\\n- **模型耦合**：模型更新或替换导致整个应用重新部署。\\n- **资源不均**：AI推理消耗大量GPU/CPU资源，与业务逻辑争抢资源。\\n- **扩展困难**：无法独立扩展AI服务与业务服务。\\n\\n## 微服务化的第一步：解耦AI能力\\n将AI模型封装为独立的微服务（如推理服务、特征服务）是常见的演进路径。例如，使用gRPC或REST API将大语言模型（LLM）调用暴露为服务，业务层通过异步消息队列调用。这种模式带来了：\\n- **独立部署**：模型更新不影响业务代码。\\n- **弹性伸缩**：根据推理请求量动态调整AI服务实例数。\\n- **技术多样性**：不同模型（如GPT-4、Claude）可共存于同一架构。\\n\\n## 智能体编排：AI原生架构的核心模式\\n随着AI应用复杂化（如多步骤推理、工具调用、记忆管理），单纯的微服务已不够。智能体编排模式应运而生，它将应用拆分为多个智能体（Agent），每个智能体负责一个子任务，并通过编排引擎协同工作。\\n\\n### 智能体设计原则\\n- **单一职责**：每个智能体只做一件事（如“代码生成智能体”或“数据检索智能体”）。\\n- **上下文隔离**：智能体间通过结构化消息传递，避免状态污染。\\n- **可观测性**：每个智能体暴露日志、指标，便于调试与监控。\\n\\n### 编排引擎的职责\\n编排引擎（如LangGraph、CrewAI或自研框架）负责：\\n- **路由**：根据用户请求，决定调用哪个智能体。\\n- **调度**：管理智能体执行顺序与并行。\\n- **容错**：处理智能体失败或超时，重试或降级。\\n- **记忆**：维护对话历史或任务上下文。\\n\\n### 实战案例：智能客服系统\\n假设我们要构建一个智能客服系统，传统做法是让一个LLM直接回答所有问题。但采用智能体编排后：\\n1. **意图识别智能体**：判断用户问题类型（退款、咨询、投诉）。\\n2. **知识库检索智能体**：从向量数据库检索相关文档。\\n3. **答案生成智能体**：基于检索结果生成回答。\\n4. **情绪检测智能体**：分析用户情绪，决定是否转人工。\\n\\n编排引擎依次调用这些智能体，每个智能体返回结构化结果，最终合成回复。这种设计使得每个智能体可以独立优化（如知识库检索智能体可单独升级为混合检索），且整体系统更鲁棒。\\n\\n## 架构演进中的陷阱与应对\\n- **过度拆分**：智能体过多导致编排复杂度飙升。建议开始时只有3-5个智能体，按需拆分。\\n- **状态同步**：多个智能体共享状态时，使用外部存储（如Redis）而非内存传递。\\n- **延迟累积**：串行调用智能体可能增加响应时间。采用并行调用或流式输出优化。\\n\\n## 总结\\nAI原生应用的架构设计是一个从单体到微服务，再到智能体编排的演进过程。关键在于：\\n1. 解耦AI能力与业务逻辑。\\n2. 以智能体为单位组织功能。\\n3. 使用编排引擎管理协作。\\n\\n下一篇文章，我们将深入讨论智能体编排中的关键技术：提示工程（Prompt Engineering）与工具调用（Function Calling）的最佳实践。\",\"outline_update\":\"无调整，保持原大纲。\",\"accumulated_update\":{\"used_examples\":[\"智能客服系统（意图识别、知识库检索、答案生成、情绪检测智能体）\",\"单体架构与微服务对比\",\"智能体编排引擎职责\"],\"covered_knowledge\":[\"单体架构局限\",\"AI能力微服务化\",\"智能体设计原则\",\"编排引擎职责\",\"架构陷阱与应对\"],\"style\":\"技术性、实战导向，包含案例与设计原则\"},\"series_complete\":false},\"seriesState\":\"{\\n  \\\"next_article\\\": {\\n    \\\"title\\\": \\\"AI原生应用的架构设计：从单体到智能体编排\\\",\\n    \\\"content\\\": \\\"## 前言\\\\n在上一篇文章中，我们探讨了AI原生应用与传统软件的本质区别，以及工程化落地的核心挑战。今天，我们将深入架构层面，解析AI原生应用如何从传统的单体架构演进为智能体（Agent）编排模式，并讨论这种转变背后的设计原则与实战经验。\\\\n\\\\n## 单体架构的局限\\\\n传统的单体应用将所有功能模块（包括AI模型调用）打包在一个进程中。虽然初期开发简单，但随着AI能力增强，单体架构暴露出几个问题：\\\\n- **模型耦合**：模型更新或替换导致整个应用重新部署。\\\\n- **资源不均**：AI推理消耗大量GPU/CPU资源，与业务逻辑争抢资源。\\\\n- **扩展困难**：无法独立扩展AI服务与业务服务。\\\\n\\\\n## 微服务化的第一步：解耦AI能力\\\\n将AI模型封装为独立的微服务（如推理服务、特征服务）是常见的演进路径。例如，使用gRPC或REST API将大语言模型（LLM）调用暴露为服务，业务层通过异步消息队列调用。这种模式带来了：\\\\n- **独立部署**：模型更新不影响业务代码。\\\\n- **弹性伸缩**：根据推理请求量动态调整AI服务实例数。\\\\n- **技术多样性**：不同模型（如GPT-4、Claude）可共存于同一架构。\\\\n\\\\n## 智能体编排：AI原生架构的核心模式\\\\n随着AI应用复杂化（如多步骤推理、工具调用、记忆管理），单纯的微服务已不够。智能体编排模式应运而生，它将应用拆分为多个智能体（Agent），每个智能体负责一个子任务，并通过编排引擎协同工作。\\\\n\\\\n### 智能体设计原则\\\\n- **单一职责**：每个智能体只做一件事（如“代码生成智能体”或“数据检索智能体”）。\\\\n- **上下文隔离**：智能体间通过结构化消息传递，避免状态污染。\\\\n- **可观测性**：每个智能体暴露日志、指标，便于调试与监控。\\\\n\\\\n### 编排引擎的职责\\\\n编排引擎（如LangGraph、CrewAI或自研框架）负责：\\\\n- **路由**：根据用户请求，决定调用哪个智能体。\\\\n- **调度**：管理智能体执行顺序与并行。\\\\n- **容错**：处理智能体失败或超时，重试或降级。\\\\n- **记忆**：维护对话历史或任务上下文。\\\\n\\\\n### 实战案例：智能客服系统\\\\n假设我们要构建一个智能客服系统，传统做法是让一个LLM直接回答所有问题。但采用智能体编排后：\\\\n1. **意图识别智能体**：判断用户问题类型（退款、咨询、投诉）。\\\\n2. **知识库检索智能体**：从向量数据库检索相关文档。\\\\n3. **答案生成智能体**：基于检索结果生成回答。\\\\n4. **情绪检测智能体**：分析用户情绪，决定是否转人工。\\\\n\\\\n编排引擎依次调用这些智能体，每个智能体返回结构化结果，最终合成回复。这种设计使得每个智能体可以独立优化（如知识库检索智能体可单独升级为混合检索），且整体系统更鲁棒。\\\\n\\\\n## 架构演进中的陷阱与应对\\\\n- **过度拆分**：智能体过多导致编排复杂度飙升。建议开始时只有3-5个智能体，按需拆分。\\\\n- **状态同步**：多个智能体共享状态时，使用外部存储（如Redis）而非内存传递。\\\\n- **延迟累积**：串行调用智能体可能增加响应时间。采用并行调用或流式输出优化。\\\\n\\\\n## 总结\\\\nAI原生应用的架构设计是一个从单体到微服务，再到智能体编排的演进过程。关键在于：\\\\n1. 解耦AI能力与业务逻辑。\\\\n2. 以智能体为单位组织功能。\\\\n3. 使用编排引擎管理协作。\\\\n\\\\n下一篇文章，我们将深入讨论智能体编排中的关键技术：提示工程（Prompt Engineering）与工具调用（Function Calling）的最佳实践。\\\",\\n    \\\"outline_update\\\": \\\"无调整，保持原大纲。\\\",\\n    \\\"accumulated_update\\\": {\\n      \\\"used_examples\\\": [\\\"智能客服系统（意图识别、知识库检索、答案生成、情绪检测智能体）\\\", \\\"单体架构与微服务对比\\\", \\\"智能体编排引擎职责\\\"],\\n      \\\"covered_knowledge\\\": [\\\"单体架构局限\\\", \\\"AI能力微服务化\\\", \\\"智能体设计原则\\\", \\\"编排引擎职责\\\", \\\"架构陷阱与应对\\\"],\\n      \\\"style\\\": \\\"技术性、实战导向，包含案例与设计原则\\\"\\n    },\\n    \\\"series_complete\\\": false\\n  }\\n}\"}', 2, NULL, 2, NULL, NULL, 0, NULL, '2026-07-09 16:11:44', '2026-07-09 16:11:44');
INSERT INTO `ai_agent_instance` VALUES (26, 'blog_series_writer-d570f38cee474461b92823a9c1e785a0', 'blog_series_writer', 1, 'blog_series', 1, '{\"flowCode\":\"blog_series\",\"name\":\"博客系列生成\",\"description\":\"按系列大纲逐篇生成博客，边写边规划\",\"version\":1,\"defaultProfileCode\":\"DS-V3-001\",\"engineType\":\"STATE_MACHINE\",\"maxTransitions\":100,\"maxAgentDepth\":8,\"webhookUrl\":null,\"nodes\":[{\"nodeCode\":\"node_1\",\"name\":\"开始\",\"nodeType\":\"START\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"__x6\":{\"x\":120,\"y\":200},\"inputs\":{\"topic\":\"系列主题\",\"history\":\"已写文章索引\",\"outline\":\"上一轮大纲\",\"prevSeq\":\"上一篇序号\",\"accumulated\":\"累积状态\"}},\"rememberTrace\":false,\"stateType\":\"ENTRY\",\"sortNo\":0},{\"nodeCode\":\"node_2\",\"name\":\"写一篇并更新大纲\",\"nodeType\":\"PROMPT\",\"systemPrompt\":\"你是一个技术博客连载作者。围绕系列主题《{{topic}}》持续创作。\\n\\n已有整体大纲：{{outline}}\\n累积写作状态（已用例子/已讲知识点/风格）：{{accumulated}}\\n已写文章索引：{{history}}\\n这是第 {{prevSeq}} 篇之后的下一篇。\\n\\n请：\\n1. 基于大纲与累积状态，决定并撰写下一篇文章（承接前文、不重复已讲内容）。\\n2. 如有需要，微调整体大纲（边写边规划）。\\n3. 更新累积写作状态。\\n4. 判断系列是否已写完。\\n\\n只输出 JSON，无其它文字：\",\"promptTemplate\":\"请生成下一篇。\",\"profileCode\":\"DS-V3-001\",\"provider\":\"\",\"model\":\"\",\"baseUrl\":\"admin\",\"apiKey\":null,\"temperature\":0.8,\"maxTokens\":4096,\"topP\":1.0,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"seriesState\",\"outputMode\":\"JSON\",\"nodeConfig\":{\"llm\":{\"model\":{\"model\":\"\",\"baseUrl\":\"admin\",\"provider\":\"\",\"credential\":\"Hump@123!@#\",\"profileCode\":\"\"},\"output\":{\"type\":\"JSON\",\"mapping\":{},\"jsonSchema\":\"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"articleTitle\\\":{\\\"type\\\":\\\"string\\\"},\\\"articleBody\\\":{\\\"type\\\":\\\"string\\\"},\\\"outline\\\":{\\\"type\\\":\\\"string\\\"},\\\"accumulated\\\":{\\\"type\\\":\\\"string\\\"},\\\"outlineDone\\\":{\\\"type\\\":\\\"string\\\",\\\"description\\\":\\\"全系列写完置 true，否则 false\\\"}},\\\"required\\\":[\\\"articleTitle\\\",\\\"articleBody\\\",\\\"outline\\\",\\\"accumulated\\\",\\\"outlineDone\\\"]}\"},\"prompt\":{\"variables\":{},\"systemPrompt\":\"你是一个技术博客连载作者。围绕系列主题《{{topic}}》持续创作。\\n\\n已有整体大纲：{{outline}}\\n累积写作状态（已用例子/已讲知识点/风格）：{{accumulated}}\\n已写文章索引：{{history}}\\n这是第 {{prevSeq}} 篇之后的下一篇。\\n\\n请：\\n1. 基于大纲与累积状态，决定并撰写下一篇文章（承接前文、不重复已讲内容）。\\n2. 如有需要，微调整体大纲（边写边规划）。\\n3. 更新累积写作状态。\\n4. 判断系列是否已写完。\\n\\n只输出 JSON，无其它文字：\",\"userPromptTemplate\":\"请生成下一篇。{{outline}}\"},\"context\":{\"memory\":true,\"messages\":true,\"artifacts\":false,\"knowledge\":true,\"variables\":true},\"parameters\":{\"mode\":\"basic\",\"basic\":{\"seed\":null,\"topP\":1,\"stream\":true,\"maxTokens\":4096,\"temperature\":0.8},\"advanced\":\"\"}},\"__x6\":{\"x\":420,\"y\":200}},\"rememberTrace\":true,\"stateType\":\"NORMAL\",\"sortNo\":1},{\"nodeCode\":\"node_3\",\"name\":\"结束\",\"nodeType\":\"END\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"__x6\":{\"x\":760,\"y\":200}},\"rememberTrace\":false,\"stateType\":\"TERMINAL\",\"sortNo\":2}],\"edges\":[{\"fromNode\":\"node_1\",\"toNode\":\"node_2\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":0},{\"fromNode\":\"node_2\",\"toNode\":\"node_3\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":1}]}', NULL, NULL, 'FAILED', 'node_2', NULL, NULL, '{\"topic\":\"AI原生应用工程化落地\",\"prevSeq\":2}', '{\"prevSeq\":2,\"__currentInstanceId\":\"blog_series_writer-d570f38cee474461b92823a9c1e785a0\",\"__maxAgentDepth\":8,\"topic\":\"AI原生应用工程化落地\",\"__smError\":\"状态[node_2]执行失败: AI调用IO异常: Target host is not specified\"}', 0, NULL, 1, NULL, NULL, 0, '状态[node_2]执行失败: AI调用IO异常: Target host is not specified', '2026-07-10 09:51:46', '2026-07-10 09:51:46');
INSERT INTO `ai_agent_instance` VALUES (27, 'blog_series-55f2ee52c5d74099a6ba4b5b0dcf3137', 'blog_series', 1, 'blog_series', 1, '{\"flowCode\":\"blog_series\",\"name\":\"博客系列生成\",\"description\":\"按系列大纲逐篇生成博客，边写边规划\",\"version\":1,\"defaultProfileCode\":\"DS-V3-001\",\"engineType\":\"STATE_MACHINE\",\"maxTransitions\":100,\"maxAgentDepth\":8,\"webhookUrl\":null,\"nodes\":[{\"nodeCode\":\"node_1\",\"name\":\"开始\",\"nodeType\":\"START\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"__x6\":{\"x\":120,\"y\":200},\"inputs\":{\"topic\":\"系列主题\",\"history\":\"已写文章索引\",\"outline\":\"上一轮大纲\",\"prevSeq\":\"上一篇序号\",\"accumulated\":\"累积状态\"}},\"rememberTrace\":false,\"stateType\":\"ENTRY\",\"sortNo\":0},{\"nodeCode\":\"node_2\",\"name\":\"写一篇并更新大纲\",\"nodeType\":\"PROMPT\",\"systemPrompt\":\"你是一个技术博客连载作者。围绕系列主题《{{topic}}》持续创作。\\n\\n已有整体大纲：{{outline}}\\n累积写作状态（已用例子/已讲知识点/风格）：{{accumulated}}\\n已写文章索引：{{history}}\\n这是第 {{prevSeq}} 篇之后的下一篇。\\n\\n请：\\n1. 基于大纲与累积状态，决定并撰写下一篇文章（承接前文、不重复已讲内容）。\\n2. 如有需要，微调整体大纲（边写边规划）。\\n3. 更新累积写作状态。\\n4. 判断系列是否已写完。\\n\\n只输出 JSON，无其它文字：\",\"promptTemplate\":\"请生成下一篇。\",\"profileCode\":\"DS-V3-001\",\"provider\":\"\",\"model\":\"\",\"baseUrl\":\"admin\",\"apiKey\":null,\"temperature\":0.8,\"maxTokens\":4096,\"topP\":1.0,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"seriesState\",\"outputMode\":\"JSON\",\"nodeConfig\":{\"llm\":{\"model\":{\"model\":\"\",\"baseUrl\":\"admin\",\"provider\":\"\",\"credential\":\"Hump@123!@#\",\"profileCode\":\"\"},\"output\":{\"type\":\"JSON\",\"mapping\":{},\"jsonSchema\":\"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"articleTitle\\\":{\\\"type\\\":\\\"string\\\"},\\\"articleBody\\\":{\\\"type\\\":\\\"string\\\"},\\\"outline\\\":{\\\"type\\\":\\\"string\\\"},\\\"accumulated\\\":{\\\"type\\\":\\\"string\\\"},\\\"outlineDone\\\":{\\\"type\\\":\\\"string\\\",\\\"description\\\":\\\"全系列写完置 true，否则 false\\\"}},\\\"required\\\":[\\\"articleTitle\\\",\\\"articleBody\\\",\\\"outline\\\",\\\"accumulated\\\",\\\"outlineDone\\\"]}\"},\"prompt\":{\"variables\":{},\"systemPrompt\":\"你是一个技术博客连载作者。围绕系列主题《{{topic}}》持续创作。\\n\\n已有整体大纲：{{outline}}\\n累积写作状态（已用例子/已讲知识点/风格）：{{accumulated}}\\n已写文章索引：{{history}}\\n这是第 {{prevSeq}} 篇之后的下一篇。\\n\\n请：\\n1. 基于大纲与累积状态，决定并撰写下一篇文章（承接前文、不重复已讲内容）。\\n2. 如有需要，微调整体大纲（边写边规划）。\\n3. 更新累积写作状态。\\n4. 判断系列是否已写完。\\n\\n只输出 JSON，无其它文字：\",\"userPromptTemplate\":\"请生成下一篇。{{outline}}\"},\"context\":{\"memory\":true,\"messages\":true,\"artifacts\":false,\"knowledge\":true,\"variables\":true},\"parameters\":{\"mode\":\"basic\",\"basic\":{\"seed\":null,\"topP\":1,\"stream\":true,\"maxTokens\":4096,\"temperature\":0.8},\"advanced\":\"\"}},\"__x6\":{\"x\":420,\"y\":200}},\"rememberTrace\":true,\"stateType\":\"NORMAL\",\"sortNo\":1},{\"nodeCode\":\"node_3\",\"name\":\"结束\",\"nodeType\":\"END\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"__x6\":{\"x\":760,\"y\":200}},\"rememberTrace\":false,\"stateType\":\"TERMINAL\",\"sortNo\":2}],\"edges\":[{\"fromNode\":\"node_1\",\"toNode\":\"node_2\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":0},{\"fromNode\":\"node_2\",\"toNode\":\"node_3\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":1}]}', NULL, NULL, 'FAILED', 'node_2', NULL, NULL, '{\"topic\":\"AI原生应用工程化落地\",\"prevSeq\":2}', '{\"prevSeq\":2,\"__currentInstanceId\":\"blog_series-55f2ee52c5d74099a6ba4b5b0dcf3137\",\"__maxAgentDepth\":8,\"topic\":\"AI原生应用工程化落地\",\"__smError\":\"状态[node_2]执行失败: AI调用IO异常: Target host is not specified\"}', 0, NULL, 1, NULL, NULL, 0, '状态[node_2]执行失败: AI调用IO异常: Target host is not specified', '2026-07-10 09:51:47', '2026-07-10 09:51:47');
INSERT INTO `ai_agent_instance` VALUES (28, 'blog_series_writer-423f486d6dc2470c839d8939838423c6', 'blog_series_writer', 1, 'blog_series', 1, '{\"flowCode\":\"blog_series\",\"name\":\"博客系列生成\",\"description\":\"按系列大纲逐篇生成博客，边写边规划\",\"version\":1,\"defaultProfileCode\":\"DS-V3-001\",\"engineType\":\"STATE_MACHINE\",\"maxTransitions\":100,\"maxAgentDepth\":8,\"webhookUrl\":null,\"nodes\":[{\"nodeCode\":\"node_3\",\"name\":\"结束\",\"nodeType\":\"END\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"__x6\":{\"x\":740,\"y\":260}},\"rememberTrace\":false,\"stateType\":\"TERMINAL\",\"sortNo\":0},{\"nodeCode\":\"node_2\",\"name\":\"写一篇并更新大纲\",\"nodeType\":\"PROMPT\",\"systemPrompt\":\"你是一个技术博客连载作者。围绕系列主题《{{topic}}》持续创作。\\n\\n已有整体大纲：{{outline}}\\n累积写作状态（已用例子/已讲知识点/风格）：{{accumulated}}\\n已写文章索引：{{history}}\\n这是第 {{prevSeq}} 篇之后的下一篇。\\n\\n请：\\n1. 基于大纲与累积状态，决定并撰写下一篇文章（承接前文、不重复已讲内容）。\\n2. 如有需要，微调整体大纲（边写边规划）。\\n3. 更新累积写作状态。\\n4. 判断系列是否已写完。\\n\\n只输出 JSON，无其它文字：\",\"promptTemplate\":\"请生成下一篇。\",\"profileCode\":\"DS-V3-001\",\"provider\":\"\",\"model\":\"\",\"baseUrl\":\"admin\",\"apiKey\":null,\"temperature\":0.8,\"maxTokens\":4096,\"topP\":1.0,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"seriesState\",\"outputMode\":\"JSON\",\"nodeConfig\":{\"llm\":{\"model\":{\"model\":\"\",\"baseUrl\":\"admin\",\"provider\":\"\",\"credential\":\"Hump@123!@#\",\"profileCode\":\"\"},\"output\":{\"type\":\"JSON\",\"mapping\":{},\"jsonSchema\":\"{\\n  \\\"type\\\": \\\"object\\\",\\n  \\\"properties\\\": {\\n    \\\"articleTitle\\\": {\\n      \\\"type\\\": \\\"string\\\"\\n    },\\n    \\\"articleBody\\\": {\\n      \\\"type\\\": \\\"string\\\"\\n    },\\n    \\\"outline\\\": {\\n      \\\"type\\\": \\\"string\\\"\\n    },\\n    \\\"accumulated\\\": {\\n      \\\"type\\\": \\\"string\\\"\\n    },\\n    \\\"outlineDone\\\": {\\n      \\\"type\\\": \\\"string\\\",\\n      \\\"description\\\": \\\"全系列写完置 true，否则 false\\\"\\n    }\\n  },\\n  \\\"required\\\": [\\n    \\\"articleTitle\\\",\\n    \\\"articleBody\\\",\\n    \\\"outline\\\",\\n    \\\"accumulated\\\",\\n    \\\"outlineDone\\\"\\n  ]\\n}\"},\"prompt\":{\"variables\":{},\"systemPrompt\":\"你是一个技术博客连载作者。围绕系列主题《{{topic}}》持续创作。\\n\\n已有整体大纲：{{outline}}\\n累积写作状态（已用例子/已讲知识点/风格）：{{accumulated}}\\n已写文章索引：{{history}}\\n这是第 {{prevSeq}} 篇之后的下一篇。\\n\\n请：\\n1. 基于大纲与累积状态，决定并撰写下一篇文章（承接前文、不重复已讲内容）。\\n2. 如有需要，微调整体大纲（边写边规划）。\\n3. 更新累积写作状态。\\n4. 判断系列是否已写完。\\n\\n只输出 JSON，无其它文字：\",\"userPromptTemplate\":\"请生成下一篇。\"},\"context\":{\"memory\":true,\"messages\":true,\"artifacts\":false,\"knowledge\":true,\"variables\":true},\"parameters\":{\"mode\":\"basic\",\"basic\":{\"seed\":null,\"topP\":1,\"stream\":true,\"maxTokens\":4096,\"temperature\":0.8},\"advanced\":\"\"}},\"__x6\":{\"x\":380,\"y\":280}},\"rememberTrace\":true,\"stateType\":\"NORMAL\",\"sortNo\":1},{\"nodeCode\":\"node_1\",\"name\":\"开始\",\"nodeType\":\"START\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"__x6\":{\"x\":50,\"y\":306},\"inputs\":{\"topic\":\"系列主题\",\"history\":\"已写文章索引\",\"outline\":\"上一轮大纲\",\"prevSeq\":\"上一篇序号\",\"accumulated\":\"累积状态\"}},\"rememberTrace\":false,\"stateType\":\"ENTRY\",\"sortNo\":2}],\"edges\":[{\"fromNode\":\"node_1\",\"toNode\":\"node_2\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":0},{\"fromNode\":\"node_2\",\"toNode\":\"node_3\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":1}]}', NULL, NULL, 'FAILED', 'node_2', NULL, NULL, '{\"topic\":\"AI原生应用工程化落地\",\"prevSeq\":2}', '{\"prevSeq\":2,\"__currentInstanceId\":\"blog_series_writer-423f486d6dc2470c839d8939838423c6\",\"__maxAgentDepth\":8,\"topic\":\"AI原生应用工程化落地\",\"__smError\":\"状态[node_2]执行失败: AI调用IO异常: Target host is not specified\"}', 0, NULL, 1, NULL, NULL, 0, '状态[node_2]执行失败: AI调用IO异常: Target host is not specified', '2026-07-11 15:45:03', '2026-07-11 15:45:03');
INSERT INTO `ai_agent_instance` VALUES (29, 'blog_series-e84deb4842674fe99e0dc1fe1e966039', 'blog_series', 1, 'blog_series', 1, '{\"flowCode\":\"blog_series\",\"name\":\"博客系列生成\",\"description\":\"按系列大纲逐篇生成博客，边写边规划\",\"version\":1,\"defaultProfileCode\":\"DS-V3-001\",\"engineType\":\"STATE_MACHINE\",\"maxTransitions\":100,\"maxAgentDepth\":8,\"webhookUrl\":null,\"nodes\":[{\"nodeCode\":\"node_3\",\"name\":\"结束\",\"nodeType\":\"END\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"__x6\":{\"x\":740,\"y\":260}},\"rememberTrace\":false,\"stateType\":\"TERMINAL\",\"sortNo\":0},{\"nodeCode\":\"node_2\",\"name\":\"写一篇并更新大纲\",\"nodeType\":\"PROMPT\",\"systemPrompt\":\"你是一个技术博客连载作者。围绕系列主题《{{topic}}》持续创作。\\n\\n已有整体大纲：{{outline}}\\n累积写作状态（已用例子/已讲知识点/风格）：{{accumulated}}\\n已写文章索引：{{history}}\\n这是第 {{prevSeq}} 篇之后的下一篇。\\n\\n请：\\n1. 基于大纲与累积状态，决定并撰写下一篇文章（承接前文、不重复已讲内容）。\\n2. 如有需要，微调整体大纲（边写边规划）。\\n3. 更新累积写作状态。\\n4. 判断系列是否已写完。\\n\\n只输出 JSON，无其它文字：\",\"promptTemplate\":\"请生成下一篇。\",\"profileCode\":\"DS-V3-001\",\"provider\":\"\",\"model\":\"\",\"baseUrl\":\"admin\",\"apiKey\":null,\"temperature\":0.8,\"maxTokens\":4096,\"topP\":1.0,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"seriesState\",\"outputMode\":\"JSON\",\"nodeConfig\":{\"llm\":{\"model\":{\"model\":\"\",\"baseUrl\":\"admin\",\"provider\":\"\",\"credential\":\"Hump@123!@#\",\"profileCode\":\"\"},\"output\":{\"type\":\"JSON\",\"mapping\":{},\"jsonSchema\":\"{\\n  \\\"type\\\": \\\"object\\\",\\n  \\\"properties\\\": {\\n    \\\"articleTitle\\\": {\\n      \\\"type\\\": \\\"string\\\"\\n    },\\n    \\\"articleBody\\\": {\\n      \\\"type\\\": \\\"string\\\"\\n    },\\n    \\\"outline\\\": {\\n      \\\"type\\\": \\\"string\\\"\\n    },\\n    \\\"accumulated\\\": {\\n      \\\"type\\\": \\\"string\\\"\\n    },\\n    \\\"outlineDone\\\": {\\n      \\\"type\\\": \\\"string\\\",\\n      \\\"description\\\": \\\"全系列写完置 true，否则 false\\\"\\n    }\\n  },\\n  \\\"required\\\": [\\n    \\\"articleTitle\\\",\\n    \\\"articleBody\\\",\\n    \\\"outline\\\",\\n    \\\"accumulated\\\",\\n    \\\"outlineDone\\\"\\n  ]\\n}\"},\"prompt\":{\"variables\":{},\"systemPrompt\":\"你是一个技术博客连载作者。围绕系列主题《{{topic}}》持续创作。\\n\\n已有整体大纲：{{outline}}\\n累积写作状态（已用例子/已讲知识点/风格）：{{accumulated}}\\n已写文章索引：{{history}}\\n这是第 {{prevSeq}} 篇之后的下一篇。\\n\\n请：\\n1. 基于大纲与累积状态，决定并撰写下一篇文章（承接前文、不重复已讲内容）。\\n2. 如有需要，微调整体大纲（边写边规划）。\\n3. 更新累积写作状态。\\n4. 判断系列是否已写完。\\n\\n只输出 JSON，无其它文字：\",\"userPromptTemplate\":\"请生成下一篇。\"},\"context\":{\"memory\":true,\"messages\":true,\"artifacts\":false,\"knowledge\":true,\"variables\":true},\"parameters\":{\"mode\":\"basic\",\"basic\":{\"seed\":null,\"topP\":1,\"stream\":true,\"maxTokens\":4096,\"temperature\":0.8},\"advanced\":\"\"}},\"__x6\":{\"x\":380,\"y\":280}},\"rememberTrace\":true,\"stateType\":\"NORMAL\",\"sortNo\":1},{\"nodeCode\":\"node_1\",\"name\":\"开始\",\"nodeType\":\"START\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"__x6\":{\"x\":50,\"y\":306},\"inputs\":{\"topic\":\"系列主题\",\"history\":\"已写文章索引\",\"outline\":\"上一轮大纲\",\"prevSeq\":\"上一篇序号\",\"accumulated\":\"累积状态\"}},\"rememberTrace\":false,\"stateType\":\"ENTRY\",\"sortNo\":2}],\"edges\":[{\"fromNode\":\"node_1\",\"toNode\":\"node_2\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":0},{\"fromNode\":\"node_2\",\"toNode\":\"node_3\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":1}]}', NULL, NULL, 'FAILED', 'node_2', NULL, NULL, '{\"topic\":\"AI原生应用工程化落地\",\"prevSeq\":2}', '{\"prevSeq\":2,\"__currentInstanceId\":\"blog_series-e84deb4842674fe99e0dc1fe1e966039\",\"__maxAgentDepth\":8,\"topic\":\"AI原生应用工程化落地\",\"__smError\":\"状态[node_2]执行失败: AI调用IO异常: Target host is not specified\"}', 0, NULL, 1, NULL, NULL, 0, '状态[node_2]执行失败: AI调用IO异常: Target host is not specified', '2026-07-11 15:45:03', '2026-07-11 15:45:03');
INSERT INTO `ai_agent_instance` VALUES (30, 'blog_series_writer-34a06b385b064001b76f854d8b6dc610', 'blog_series_writer', 1, 'blog_series', 1, '{\"flowCode\":\"blog_series\",\"name\":\"博客系列生成\",\"description\":\"按系列大纲逐篇生成博客，边写边规划\",\"version\":1,\"defaultProfileCode\":\"DS-V3-001\",\"engineType\":\"STATE_MACHINE\",\"maxTransitions\":100,\"maxAgentDepth\":8,\"webhookUrl\":null,\"nodes\":[{\"nodeCode\":\"node_3\",\"name\":\"结束\",\"nodeType\":\"END\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"__x6\":{\"x\":740,\"y\":260}},\"rememberTrace\":false,\"stateType\":\"TERMINAL\",\"sortNo\":0},{\"nodeCode\":\"node_2\",\"name\":\"写一篇并更新大纲\",\"nodeType\":\"PROMPT\",\"systemPrompt\":\"你是一个技术博客连载作者。围绕系列主题《{{topic}}》持续创作。\\n\\n已有整体大纲：{{outline}}\\n累积写作状态（已用例子/已讲知识点/风格）：{{accumulated}}\\n已写文章索引：{{history}}\\n这是第 {{prevSeq}} 篇之后的下一篇。\\n\\n请：\\n1. 基于大纲与累积状态，决定并撰写下一篇文章（承接前文、不重复已讲内容）。\\n2. 如有需要，微调整体大纲（边写边规划）。\\n3. 更新累积写作状态。\\n4. 判断系列是否已写完。\\n\\n只输出 JSON，无其它文字：\",\"promptTemplate\":\"请生成下一篇。\",\"profileCode\":\"DS-V3-001\",\"provider\":\"\",\"model\":\"\",\"baseUrl\":\"admin\",\"apiKey\":null,\"temperature\":0.8,\"maxTokens\":4096,\"topP\":1.0,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"seriesState\",\"outputMode\":\"JSON\",\"nodeConfig\":{\"llm\":{\"model\":{\"model\":\"\",\"baseUrl\":\"admin\",\"provider\":\"\",\"credential\":\"Hump@123!@#\",\"profileCode\":\"\"},\"output\":{\"type\":\"JSON\",\"mapping\":{},\"jsonSchema\":\"{\\n  \\\"type\\\": \\\"object\\\",\\n  \\\"properties\\\": {\\n    \\\"articleTitle\\\": {\\n      \\\"type\\\": \\\"string\\\"\\n    },\\n    \\\"articleBody\\\": {\\n      \\\"type\\\": \\\"string\\\"\\n    },\\n    \\\"outline\\\": {\\n      \\\"type\\\": \\\"string\\\"\\n    },\\n    \\\"accumulated\\\": {\\n      \\\"type\\\": \\\"string\\\"\\n    },\\n    \\\"outlineDone\\\": {\\n      \\\"type\\\": \\\"string\\\",\\n      \\\"description\\\": \\\"全系列写完置 true，否则 false\\\"\\n    }\\n  },\\n  \\\"required\\\": [\\n    \\\"articleTitle\\\",\\n    \\\"articleBody\\\",\\n    \\\"outline\\\",\\n    \\\"accumulated\\\",\\n    \\\"outlineDone\\\"\\n  ]\\n}\"},\"prompt\":{\"variables\":{},\"systemPrompt\":\"你是一个技术博客连载作者。围绕系列主题《{{topic}}》持续创作。\\n\\n已有整体大纲：{{outline}}\\n累积写作状态（已用例子/已讲知识点/风格）：{{accumulated}}\\n已写文章索引：{{history}}\\n这是第 {{prevSeq}} 篇之后的下一篇。\\n\\n请：\\n1. 基于大纲与累积状态，决定并撰写下一篇文章（承接前文、不重复已讲内容）。\\n2. 如有需要，微调整体大纲（边写边规划）。\\n3. 更新累积写作状态。\\n4. 判断系列是否已写完。\\n\\n只输出 JSON，无其它文字：\",\"userPromptTemplate\":\"请生成下一篇。\"},\"context\":{\"memory\":true,\"messages\":true,\"artifacts\":false,\"knowledge\":true,\"variables\":true},\"parameters\":{\"mode\":\"basic\",\"basic\":{\"seed\":null,\"topP\":1,\"stream\":true,\"maxTokens\":4096,\"temperature\":0.8},\"advanced\":\"\"}},\"__x6\":{\"x\":380,\"y\":280}},\"rememberTrace\":true,\"stateType\":\"NORMAL\",\"sortNo\":1},{\"nodeCode\":\"node_1\",\"name\":\"开始\",\"nodeType\":\"START\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"__x6\":{\"x\":50,\"y\":306},\"inputs\":{\"topic\":\"系列主题\",\"history\":\"已写文章索引\",\"outline\":\"上一轮大纲\",\"prevSeq\":\"上一篇序号\",\"accumulated\":\"累积状态\"}},\"rememberTrace\":false,\"stateType\":\"ENTRY\",\"sortNo\":2}],\"edges\":[{\"fromNode\":\"node_1\",\"toNode\":\"node_2\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":0},{\"fromNode\":\"node_2\",\"toNode\":\"node_3\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":1}]}', NULL, NULL, 'FAILED', 'node_2', NULL, NULL, '{\"topic\":\"AI原生应用工程化落地\",\"prevSeq\":2}', '{\"prevSeq\":2,\"__currentInstanceId\":\"blog_series_writer-34a06b385b064001b76f854d8b6dc610\",\"__maxAgentDepth\":8,\"topic\":\"AI原生应用工程化落地\",\"__smError\":\"状态[node_2]执行失败: AI调用IO异常: Target host is not specified\"}', 0, NULL, 1, NULL, NULL, 0, '状态[node_2]执行失败: AI调用IO异常: Target host is not specified', '2026-07-13 15:22:00', '2026-07-13 15:22:00');
INSERT INTO `ai_agent_instance` VALUES (31, 'blog_series-f65005e14aad4cc5a6fd2cfa968e23c8', 'blog_series', 1, 'blog_series', 1, '{\"flowCode\":\"blog_series\",\"name\":\"博客系列生成\",\"description\":\"按系列大纲逐篇生成博客，边写边规划\",\"version\":1,\"defaultProfileCode\":\"DS-V3-001\",\"engineType\":\"STATE_MACHINE\",\"maxTransitions\":100,\"maxAgentDepth\":8,\"webhookUrl\":null,\"nodes\":[{\"nodeCode\":\"node_3\",\"name\":\"结束\",\"nodeType\":\"END\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"__x6\":{\"x\":740,\"y\":260}},\"rememberTrace\":false,\"stateType\":\"TERMINAL\",\"sortNo\":0},{\"nodeCode\":\"node_2\",\"name\":\"写一篇并更新大纲\",\"nodeType\":\"PROMPT\",\"systemPrompt\":\"你是一个技术博客连载作者。围绕系列主题《{{topic}}》持续创作。\\n\\n已有整体大纲：{{outline}}\\n累积写作状态（已用例子/已讲知识点/风格）：{{accumulated}}\\n已写文章索引：{{history}}\\n这是第 {{prevSeq}} 篇之后的下一篇。\\n\\n请：\\n1. 基于大纲与累积状态，决定并撰写下一篇文章（承接前文、不重复已讲内容）。\\n2. 如有需要，微调整体大纲（边写边规划）。\\n3. 更新累积写作状态。\\n4. 判断系列是否已写完。\\n\\n只输出 JSON，无其它文字：\",\"promptTemplate\":\"请生成下一篇。\",\"profileCode\":\"DS-V3-001\",\"provider\":\"\",\"model\":\"\",\"baseUrl\":\"admin\",\"apiKey\":null,\"temperature\":0.8,\"maxTokens\":4096,\"topP\":1.0,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"seriesState\",\"outputMode\":\"JSON\",\"nodeConfig\":{\"llm\":{\"model\":{\"model\":\"\",\"baseUrl\":\"admin\",\"provider\":\"\",\"credential\":\"Hump@123!@#\",\"profileCode\":\"\"},\"output\":{\"type\":\"JSON\",\"mapping\":{},\"jsonSchema\":\"{\\n  \\\"type\\\": \\\"object\\\",\\n  \\\"properties\\\": {\\n    \\\"articleTitle\\\": {\\n      \\\"type\\\": \\\"string\\\"\\n    },\\n    \\\"articleBody\\\": {\\n      \\\"type\\\": \\\"string\\\"\\n    },\\n    \\\"outline\\\": {\\n      \\\"type\\\": \\\"string\\\"\\n    },\\n    \\\"accumulated\\\": {\\n      \\\"type\\\": \\\"string\\\"\\n    },\\n    \\\"outlineDone\\\": {\\n      \\\"type\\\": \\\"string\\\",\\n      \\\"description\\\": \\\"全系列写完置 true，否则 false\\\"\\n    }\\n  },\\n  \\\"required\\\": [\\n    \\\"articleTitle\\\",\\n    \\\"articleBody\\\",\\n    \\\"outline\\\",\\n    \\\"accumulated\\\",\\n    \\\"outlineDone\\\"\\n  ]\\n}\"},\"prompt\":{\"variables\":{},\"systemPrompt\":\"你是一个技术博客连载作者。围绕系列主题《{{topic}}》持续创作。\\n\\n已有整体大纲：{{outline}}\\n累积写作状态（已用例子/已讲知识点/风格）：{{accumulated}}\\n已写文章索引：{{history}}\\n这是第 {{prevSeq}} 篇之后的下一篇。\\n\\n请：\\n1. 基于大纲与累积状态，决定并撰写下一篇文章（承接前文、不重复已讲内容）。\\n2. 如有需要，微调整体大纲（边写边规划）。\\n3. 更新累积写作状态。\\n4. 判断系列是否已写完。\\n\\n只输出 JSON，无其它文字：\",\"userPromptTemplate\":\"请生成下一篇。\"},\"context\":{\"memory\":true,\"messages\":true,\"artifacts\":false,\"knowledge\":true,\"variables\":true},\"parameters\":{\"mode\":\"basic\",\"basic\":{\"seed\":null,\"topP\":1,\"stream\":true,\"maxTokens\":4096,\"temperature\":0.8},\"advanced\":\"\"}},\"__x6\":{\"x\":380,\"y\":280}},\"rememberTrace\":true,\"stateType\":\"NORMAL\",\"sortNo\":1},{\"nodeCode\":\"node_1\",\"name\":\"开始\",\"nodeType\":\"START\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"__x6\":{\"x\":50,\"y\":306},\"inputs\":{\"topic\":\"系列主题\",\"history\":\"已写文章索引\",\"outline\":\"上一轮大纲\",\"prevSeq\":\"上一篇序号\",\"accumulated\":\"累积状态\"}},\"rememberTrace\":false,\"stateType\":\"ENTRY\",\"sortNo\":2}],\"edges\":[{\"fromNode\":\"node_1\",\"toNode\":\"node_2\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":0},{\"fromNode\":\"node_2\",\"toNode\":\"node_3\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":1}]}', NULL, NULL, 'FAILED', 'node_2', NULL, NULL, '{\"topic\":\"AI原生应用工程化落地\",\"prevSeq\":2}', '{\"prevSeq\":2,\"__currentInstanceId\":\"blog_series-f65005e14aad4cc5a6fd2cfa968e23c8\",\"__maxAgentDepth\":8,\"topic\":\"AI原生应用工程化落地\",\"__smError\":\"状态[node_2]执行失败: AI调用IO异常: Target host is not specified\"}', 0, NULL, 1, NULL, NULL, 0, '状态[node_2]执行失败: AI调用IO异常: Target host is not specified', '2026-07-13 15:22:00', '2026-07-13 15:22:00');

-- ----------------------------
-- Table structure for ai_agent_instance_transition
-- ----------------------------
DROP TABLE IF EXISTS `ai_agent_instance_transition`;
CREATE TABLE `ai_agent_instance_transition`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '归属实例标识',
  `seq` int(11) NOT NULL COMMENT '转移序号，从 0 递增（重试也占序号）',
  `from_state` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '源状态',
  `to_state` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '目标状态',
  `event_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '触发本次转移的事件（如有）',
  `attempt` int(11) NOT NULL DEFAULT 0 COMMENT '进入 to_state 的重试次数（0=首次）',
  `outcome` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'SUCCESS' COMMENT '轨迹类型：SUCCESS | RETRY | FAILED | COMPENSATED，回放时可折叠 RETRY 行',
  `is_compensation` tinyint(1) NOT NULL DEFAULT 0 COMMENT '预留：1=补偿轨迹（Saga 回滚，阶段 2.5 启用）',
  `node_result` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '进入 to_state 后该节点产物(delta)；失败/重试时记错误摘要（恢复时只重放 outcome=SUCCESS 行）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_instance_seq`(`instance_id` ASC, `seq` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 80 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI 智能体状态转移历史（回放/审计），每次 attempt 落一行' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_agent_instance_transition
-- ----------------------------
INSERT INTO `ai_agent_instance_transition` VALUES (59, 'blog_series_writer-065131816cc647eca9ccc876f915861d', 0, NULL, 'node_1', NULL, 0, 'SUCCESS', 0, NULL, '2026-07-09 15:34:45');
INSERT INTO `ai_agent_instance_transition` VALUES (60, 'blog_series_writer-065131816cc647eca9ccc876f915861d', 1, NULL, 'node_2', NULL, 0, 'SUCCESS', 0, '{\"article\":{\"title\":\"AI原生应用的监控与可观测性：从黑盒到白盒的工程实践\",\"content\":\"在前几篇文章中，我们探讨了AI原生应用的架构设计、数据管道、模型部署与推理优化。然而，当应用上线后，如何确保其稳定运行、快速定位问题，成为工程化落地的关键一环。传统的监控体系往往只关注系统层面（如CPU、内存），但AI应用的特殊性——模型行为的非确定性、数据漂移、推理延迟波动——要求我们构建更精细的可观测性体系。本文将深入探讨如何从黑盒监控走向白盒可观测，覆盖指标、日志、链路追踪三大支柱，并结合AI场景的特殊挑战给出实践方案。\\n\\n## 1. AI原生应用的可观测性挑战\\nAI应用不同于传统软件：\\n- **模型行为不可预测**：即使输入相似，输出也可能因模型版本、数据分布变化而不同。\\n- **数据漂移**：生产数据与训练数据分布不一致，导致模型性能下降。\\n- **推理延迟波动**：GPU资源竞争、模型大小、批处理策略等影响响应时间。\\n- **多模态与复杂链路**：一个请求可能经过多个模型、数据预处理、后处理步骤。\\n\\n因此，我们需要监控三个层次：\\n- **基础设施层**：GPU利用率、显存、网络延迟等。\\n- **模型服务层**：推理延迟、吞吐量、错误率、模型版本。\\n- **业务指标层**：预测准确率、用户反馈、A/B测试结果。\\n\\n## 2. 构建可观测性体系：三大支柱\\n### 2.1 指标（Metrics）\\n指标是数字化的、可聚合的度量。对于AI应用，关键指标包括：\\n- **系统指标**：GPU利用率、显存占用、请求队列长度。\\n- **模型指标**：推理延迟（P50/P95/P99）、吞吐量（QPS）、错误率（如模型输出NaN）。\\n- **数据指标**：输入特征分布（如均值、标准差）、输出概率分布、数据漂移检测得分（如PSI）。\\n\\n**实践**：使用Prometheus采集指标，Grafana可视化。例如，对每个模型版本记录推理延迟直方图，并设置告警：当P95延迟超过500ms时触发。\\n\\n### 2.2 日志（Logs）\\n日志提供事件的详细记录。在AI场景中，需要结构化日志，包含：\\n- 请求ID、时间戳、模型版本、输入特征摘要（如哈希值，避免隐私泄露）。\\n- 推理结果、置信度、错误堆栈。\\n- 数据预处理参数（如归一化系数）。\\n\\n**实践**：使用ELK栈（Elasticsearch、Logstash、Kibana）或Loki。例如，当模型返回低置信度结果时，记录输入特征以便后续分析。\\n\\n### 2.3 链路追踪（Traces）\\n链路追踪揭示请求在分布式系统中的完整路径。对于AI应用，一个请求可能经历：\\n1. 网关接收 → 2. 数据预处理（特征工程）→ 3. 模型推理（可能多模型级联）→ 4. 后处理 → 5. 返回。\\n每个步骤的耗时、依赖关系需清晰可见。\\n\\n**实践**：使用OpenTelemetry注入追踪上下文，Jaeger或Zipkin可视化。例如，追踪发现数据预处理步骤耗时占比过高，则考虑优化特征计算。\\n\\n## 3. 针对AI场景的特殊监控\\n### 3.1 数据漂移检测\\n数据漂移是AI应用退化的主要原因。可在线计算：\\n- **特征分布漂移**：使用PSI（Population Stability Index）或KS检验。\\n- **预测分布漂移**：监控模型输出概率的分布变化。\\n- **概念漂移**：结合业务反馈（如用户点击率）间接检测。\\n\\n**实践**：在推理管道中集成漂移检测模块，定期（如每小时）计算PSI，超过阈值则告警并触发模型重训练。\\n\\n### 3.2 模型性能退化监控\\n除了延迟，还需关注模型准确性。但真实标签往往有延迟，因此采用：\\n- **代理指标**：如置信度、熵值，若平均置信度下降可能预示性能退化。\\n- **影子模型对比**：将生产请求同时发送到新模型和基线模型，比较输出差异。\\n- **A/B测试**：对部分流量部署新模型，对比业务指标。\\n\\n### 3.3 推理成本监控\\nGPU资源昂贵，需监控每个请求的推理成本：\\n- **每请求GPU时间**：通过CUDA事件或NVIDIA DCGM采集。\\n- **批处理效率**：监控批处理大小与延迟的权衡。\\n\\n## 4. 工程化落地案例\\n假设我们有一个文本分类服务，使用BERT模型。\\n- **指标**：Prometheus采集推理延迟、QPS、GPU利用率。Grafana仪表盘显示实时趋势。\\n- **日志**：每个请求记录输入文本长度、预测类别、置信度。当置信度<0.6时，日志级别提升为WARN。\\n- **追踪**：OpenTelemetry注入，Jaeger展示请求链路：预处理（分词）→模型推理（GPU）→后处理（softmax）。发现预处理耗时占40%，于是优化分词库。\\n- **漂移检测**：每小时计算输入文本长度分布与训练集PSI，某天PSI突增，告警发现新用户群体使用更长的文本，触发模型微调。\\n\\n## 5. 工具选型建议\\n- **指标**：Prometheus + Grafana（开源标准）。\\n- **日志**：ELK（功能全面）或 Loki（轻量级，与Prometheus集成好）。\\n- **追踪**：OpenTelemetry（统一标准）+ Jaeger（社区活跃）或 Tempo（Grafana生态）。\\n- **漂移检测**：WhyLabs（SaaS）或自建基于MLflow的监控。\\n\\n## 6. 总结与展望\\n可观测性是AI原生应用工程化的“眼睛”。从黑盒监控到白盒可观测，我们不仅能快速定位问题，还能提前预警性能退化。下一步，可以结合MLOps平台实现自动化回滚与重训练，形成闭环。在下一篇文章中，我们将探讨AI应用的持续交付与MLOps实践。\",\"next_outline\":{\"sections\":[\"1. 引言：AI原生应用工程化落地的全景\",\"2. 架构设计：微服务与事件驱动\",\"3. 数据工程：实时数据管道与特征存储\",\"4. 模型部署：容器化与无服务器推理\",\"5. 推理优化：缓存、批处理与模型压缩\",\"6. 监控与可观测性：从黑盒到白盒\",\"7. 持续交付与MLOps：自动化流水线\",\"8. 安全与合规：模型审计与隐私保护\",\"9. 成本优化：GPU资源管理与弹性伸缩\",\"10. 案例研究：从0到1构建AI原生应用\"]},\"accumulated\":{\"used_examples\":[\"文本分类服务（BERT）的监控与可观测性案例\"],\"covered_topics\":[\"监控指标（GPU、延迟、吞吐量）\",\"日志结构化（请求ID、特征摘要）\",\"链路追踪（预处理、推理、后处理）\",\"数据漂移检测（PSI、KS检验）\",\"模型性能退化监控（代理指标、影子模型）\",\"推理成本监控（GPU时间、批处理效率）\",\"工具选型（Prometheus、ELK、OpenTelemetry、Jaeger）\"],\"style\":\"技术深度与工程实践结合，提供具体指标、工具和案例\"},\"series_complete\":false},\"seriesState\":\"{\\n  \\\"article\\\": {\\n    \\\"title\\\": \\\"AI原生应用的监控与可观测性：从黑盒到白盒的工程实践\\\",\\n    \\\"content\\\": \\\"在前几篇文章中，我们探讨了AI原生应用的架构设计、数据管道、模型部署与推理优化。然而，当应用上线后，如何确保其稳定运行、快速定位问题，成为工程化落地的关键一环。传统的监控体系往往只关注系统层面（如CPU、内存），但AI应用的特殊性——模型行为的非确定性、数据漂移、推理延迟波动——要求我们构建更精细的可观测性体系。本文将深入探讨如何从黑盒监控走向白盒可观测，覆盖指标、日志、链路追踪三大支柱，并结合AI场景的特殊挑战给出实践方案。\\\\n\\\\n## 1. AI原生应用的可观测性挑战\\\\nAI应用不同于传统软件：\\\\n- **模型行为不可预测**：即使输入相似，输出也可能因模型版本、数据分布变化而不同。\\\\n- **数据漂移**：生产数据与训练数据分布不一致，导致模型性能下降。\\\\n- **推理延迟波动**：GPU资源竞争、模型大小、批处理策略等影响响应时间。\\\\n- **多模态与复杂链路**：一个请求可能经过多个模型、数据预处理、后处理步骤。\\\\n\\\\n因此，我们需要监控三个层次：\\\\n- **基础设施层**：GPU利用率、显存、网络延迟等。\\\\n- **模型服务层**：推理延迟、吞吐量、错误率、模型版本。\\\\n- **业务指标层**：预测准确率、用户反馈、A/B测试结果。\\\\n\\\\n## 2. 构建可观测性体系：三大支柱\\\\n### 2.1 指标（Metrics）\\\\n指标是数字化的、可聚合的度量。对于AI应用，关键指标包括：\\\\n- **系统指标**：GPU利用率、显存占用、请求队列长度。\\\\n- **模型指标**：推理延迟（P50/P95/P99）、吞吐量（QPS）、错误率（如模型输出NaN）。\\\\n- **数据指标**：输入特征分布（如均值、标准差）、输出概率分布、数据漂移检测得分（如PSI）。\\\\n\\\\n**实践**：使用Prometheus采集指标，Grafana可视化。例如，对每个模型版本记录推理延迟直方图，并设置告警：当P95延迟超过500ms时触发。\\\\n\\\\n### 2.2 日志（Logs）\\\\n日志提供事件的详细记录。在AI场景中，需要结构化日志，包含：\\\\n- 请求ID、时间戳、模型版本、输入特征摘要（如哈希值，避免隐私泄露）。\\\\n- 推理结果、置信度、错误堆栈。\\\\n- 数据预处理参数（如归一化系数）。\\\\n\\\\n**实践**：使用ELK栈（Elasticsearch、Logstash、Kibana）或Loki。例如，当模型返回低置信度结果时，记录输入特征以便后续分析。\\\\n\\\\n### 2.3 链路追踪（Traces）\\\\n链路追踪揭示请求在分布式系统中的完整路径。对于AI应用，一个请求可能经历：\\\\n1. 网关接收 → 2. 数据预处理（特征工程）→ 3. 模型推理（可能多模型级联）→ 4. 后处理 → 5. 返回。\\\\n每个步骤的耗时、依赖关系需清晰可见。\\\\n\\\\n**实践**：使用OpenTelemetry注入追踪上下文，Jaeger或Zipkin可视化。例如，追踪发现数据预处理步骤耗时占比过高，则考虑优化特征计算。\\\\n\\\\n## 3. 针对AI场景的特殊监控\\\\n### 3.1 数据漂移检测\\\\n数据漂移是AI应用退化的主要原因。可在线计算：\\\\n- **特征分布漂移**：使用PSI（Population Stability Index）或KS检验。\\\\n- **预测分布漂移**：监控模型输出概率的分布变化。\\\\n- **概念漂移**：结合业务反馈（如用户点击率）间接检测。\\\\n\\\\n**实践**：在推理管道中集成漂移检测模块，定期（如每小时）计算PSI，超过阈值则告警并触发模型重训练。\\\\n\\\\n### 3.2 模型性能退化监控\\\\n除了延迟，还需关注模型准确性。但真实标签往往有延迟，因此采用：\\\\n- **代理指标**：如置信度、熵值，若平均置信度下降可能预示性能退化。\\\\n- **影子模型对比**：将生产请求同时发送到新模型和基线模型，比较输出差异。\\\\n- **A/B测试**：对部分流量部署新模型，对比业务指标。\\\\n\\\\n### 3.3 推理成本监控\\\\nGPU资源昂贵，需监控每个请求的推理成本：\\\\n- **每请求GPU时间**：通过CUDA事件或NVIDIA DCGM采集。\\\\n- **批处理效率**：监控批处理大小与延迟的权衡。\\\\n\\\\n## 4. 工程化落地案例\\\\n假设我们有一个文本分类服务，使用BERT模型。\\\\n- **指标**：Prometheus采集推理延迟、QPS、GPU利用率。Grafana仪表盘显示实时趋势。\\\\n- **日志**：每个请求记录输入文本长度、预测类别、置信度。当置信度<0.6时，日志级别提升为WARN。\\\\n- **追踪**：OpenTelemetry注入，Jaeger展示请求链路：预处理（分词）→模型推理（GPU）→后处理（softmax）。发现预处理耗时占40%，于是优化分词库。\\\\n- **漂移检测**：每小时计算输入文本长度分布与训练集PSI，某天PSI突增，告警发现新用户群体使用更长的文本，触发模型微调。\\\\n\\\\n## 5. 工具选型建议\\\\n- **指标**：Prometheus + Grafana（开源标准）。\\\\n- **日志**：ELK（功能全面）或 Loki（轻量级，与Prometheus集成好）。\\\\n- **追踪**：OpenTelemetry（统一标准）+ Jaeger（社区活跃）或 Tempo（Grafana生态）。\\\\n- **漂移检测**：WhyLabs（SaaS）或自建基于MLflow的监控。\\\\n\\\\n## 6. 总结与展望\\\\n可观测性是AI原生应用工程化的“眼睛”。从黑盒监控到白盒可观测，我们不仅能快速定位问题，还能提前预警性能退化。下一步，可以结合MLOps平台实现自动化回滚与重训练，形成闭环。在下一篇文章中，我们将探讨AI应用的持续交付与MLOps实践。\\\",\\n    \\\"next_outline\\\": {\\n      \\\"sections\\\": [\\n        \\\"1. 引言：AI原生应用工程化落地的全景\\\",\\n        \\\"2. 架构设计：微服务与事件驱动\\\",\\n        \\\"3. 数据工程：实时数据管道与特征存储\\\",\\n        \\\"4. 模型部署：容器化与无服务器推理\\\",\\n        \\\"5. 推理优化：缓存、批处理与模型压缩\\\",\\n        \\\"6. 监控与可观测性：从黑盒到白盒\\\",\\n        \\\"7. 持续交付与MLOps：自动化流水线\\\",\\n        \\\"8. 安全与合规：模型审计与隐私保护\\\",\\n        \\\"9. 成本优化：GPU资源管理与弹性伸缩\\\",\\n        \\\"10. 案例研究：从0到1构建AI原生应用\\\"\\n      ]\\n    },\\n    \\\"accumulated\\\": {\\n      \\\"used_examples\\\": [\\n        \\\"文本分类服务（BERT）的监控与可观测性案例\\\"\\n      ],\\n      \\\"covered_topics\\\": [\\n        \\\"监控指标（GPU、延迟、吞吐量）\\\",\\n        \\\"日志结构化（请求ID、特征摘要）\\\",\\n        \\\"链路追踪（预处理、推理、后处理）\\\",\\n        \\\"数据漂移检测（PSI、KS检验）\\\",\\n        \\\"模型性能退化监控（代理指标、影子模型）\\\",\\n        \\\"推理成本监控（GPU时间、批处理效率）\\\",\\n        \\\"工具选型（Prometheus、ELK、OpenTelemetry、Jaeger）\\\"\\n      ],\\n      \\\"style\\\": \\\"技术深度与工程实践结合，提供具体指标、工具和案例\\\"\\n    },\\n    \\\"series_complete\\\": false\\n  }\\n}\"}', '2026-07-09 15:35:04');
INSERT INTO `ai_agent_instance_transition` VALUES (61, 'blog_series_writer-065131816cc647eca9ccc876f915861d', 2, NULL, 'node_3', NULL, 0, 'SUCCESS', 0, NULL, '2026-07-09 15:35:04');
INSERT INTO `ai_agent_instance_transition` VALUES (62, 'blog_series_writer-bf2e0e39ce8844f9abbca1cc1f9b942c', 0, NULL, 'node_1', NULL, 0, 'SUCCESS', 0, NULL, '2026-07-09 15:37:04');
INSERT INTO `ai_agent_instance_transition` VALUES (63, 'blog_series_writer-bf2e0e39ce8844f9abbca1cc1f9b942c', 1, NULL, 'node_2', NULL, 0, 'SUCCESS', 0, '{\"outline_update\":{\"changes\":\"无变化，大纲已涵盖本内容。\",\"updated_outline\":\"1. AI原生应用工程化落地：引言与核心挑战（已完成）\\n2. AI原生应用的架构设计：从单体到智能体编排（当前）\\n3. 数据管道设计：构建高效、可靠的AI数据流（计划中）\\n4. 模型部署与推理优化：从实验到生产（计划中）\\n5. 监控与可观测性：AI应用的生命线（计划中）\\n6. 安全与伦理：AI原生应用的边界（计划中）\\n7. 总结与未来展望（计划中）\"},\"series_complete\":false,\"accumulated_update\":{\"used_examples\":[\"聊天机器人单体架构问题\",\"智能客服智能体\",\"文档问答助手架构\"],\"covered_knowledge\":[\"微服务架构\",\"智能体编排\",\"异步消息驱动\",\"状态管理\",\"可观测性\"],\"writing_style\":\"技术务实，结合案例，逐步深入\"},\"next_article\":{\"title\":\"AI原生应用的架构设计：从单体到智能体编排\",\"content\":\"在前一篇文章中，我们探讨了AI原生应用与传统软件的根本差异，以及工程化落地的核心挑战。今天，我们将深入技术架构层面，讨论如何设计一个既能支撑AI能力（如大模型推理、数据流处理）又能保持工程可维护性的应用架构。\\n\\n### 从单体到微服务：AI场景的演进\\n\\n传统的单体架构在AI应用中往往显得力不从心。例如，一个简单的聊天机器人，如果将所有逻辑（前端、后端、模型推理、知识库检索）都放在一个进程中，当模型推理耗时较长时，整个应用会阻塞；当需要扩展推理能力时，只能整体复制，导致资源浪费。\\n\\n因此，AI原生应用通常采用微服务架构，将不同职责分离：\\n- **推理服务**：负责调用大模型API或本地模型，提供文本生成、图像识别等能力。\\n- **知识库服务**：管理向量数据库，处理文档嵌入、相似性检索。\\n- **编排服务**：协调多个AI组件，实现复杂工作流（如多轮对话、工具调用）。\\n- **网关服务**：负责认证、限流、请求路由。\\n\\n### 智能体编排：AI应用的核心模式\\n\\n更进一步，AI原生应用的核心不再是传统的请求-响应模式，而是**智能体编排（Agent Orchestration）**。智能体是一个拥有自主决策能力的模块，它能够根据用户输入，动态选择工具、调用外部API、组合多个模型输出。\\n\\n例如，一个“智能客服”智能体：\\n1. 接收用户问题“我的订单什么时候到？”\\n2. 调用意图识别模型，判断需要查询订单状态。\\n3. 调用订单API获取实时数据。\\n4. 调用大模型生成自然语言回复。\\n5. 返回给用户。\\n\\n这种编排模式要求架构支持：\\n- **异步消息驱动**：避免长任务阻塞。\\n- **状态管理**：智能体需要记住对话上下文和中间结果。\\n- **可观测性**：追踪每个决策路径，便于调试和优化。\\n\\n### 实战案例：一个简单的智能体架构\\n\\n假设我们构建一个“文档问答助手”，架构如下：\\n- **用户请求** → **网关** → **编排服务**（使用LangChain或自研框架）\\n- 编排服务：\\n  - 调用**意图识别模型**（轻量级BERT）判断用户意图（查询、总结、翻译）。\\n  - 根据意图，调用**知识库服务**检索相关文档片段。\\n  - 调用**大模型服务**（如GPT-4）生成答案。\\n  - 返回结果。\\n- 所有服务通过**消息队列**（如RabbitMQ）解耦，确保高可用。\\n\\n### 架构决策要点\\n\\n在设计AI原生应用架构时，需权衡以下几点：\\n1. **延迟 vs 准确性**：实时推理需要低延迟，但复杂模型可能更准。可考虑缓存或预计算。\\n2. **成本 vs 性能**：大模型API调用成本高，可引入本地小模型做预筛选。\\n3. **灵活性 vs 可维护性**：智能体编排增加灵活性，但需定义清晰的接口和错误处理机制。\\n\\n### 小结\\n\\nAI原生应用的架构核心是**微服务+智能体编排**，它让AI能力像乐高积木一样可组合、可扩展。下一篇文章，我们将深入讨论数据管道设计：如何构建高效、可靠的训练和推理数据流。\\n\\n（本文为系列第2篇，后续将涉及数据工程、模型部署、监控等主题。）\"},\"seriesState\":\"{\\n  \\\"next_article\\\": {\\n    \\\"title\\\": \\\"AI原生应用的架构设计：从单体到智能体编排\\\",\\n    \\\"content\\\": \\\"在前一篇文章中，我们探讨了AI原生应用与传统软件的根本差异，以及工程化落地的核心挑战。今天，我们将深入技术架构层面，讨论如何设计一个既能支撑AI能力（如大模型推理、数据流处理）又能保持工程可维护性的应用架构。\\\\n\\\\n### 从单体到微服务：AI场景的演进\\\\n\\\\n传统的单体架构在AI应用中往往显得力不从心。例如，一个简单的聊天机器人，如果将所有逻辑（前端、后端、模型推理、知识库检索）都放在一个进程中，当模型推理耗时较长时，整个应用会阻塞；当需要扩展推理能力时，只能整体复制，导致资源浪费。\\\\n\\\\n因此，AI原生应用通常采用微服务架构，将不同职责分离：\\\\n- **推理服务**：负责调用大模型API或本地模型，提供文本生成、图像识别等能力。\\\\n- **知识库服务**：管理向量数据库，处理文档嵌入、相似性检索。\\\\n- **编排服务**：协调多个AI组件，实现复杂工作流（如多轮对话、工具调用）。\\\\n- **网关服务**：负责认证、限流、请求路由。\\\\n\\\\n### 智能体编排：AI应用的核心模式\\\\n\\\\n更进一步，AI原生应用的核心不再是传统的请求-响应模式，而是**智能体编排（Agent Orchestration）**。智能体是一个拥有自主决策能力的模块，它能够根据用户输入，动态选择工具、调用外部API、组合多个模型输出。\\\\n\\\\n例如，一个“智能客服”智能体：\\\\n1. 接收用户问题“我的订单什么时候到？”\\\\n2. 调用意图识别模型，判断需要查询订单状态。\\\\n3. 调用订单API获取实时数据。\\\\n4. 调用大模型生成自然语言回复。\\\\n5. 返回给用户。\\\\n\\\\n这种编排模式要求架构支持：\\\\n- **异步消息驱动**：避免长任务阻塞。\\\\n- **状态管理**：智能体需要记住对话上下文和中间结果。\\\\n- **可观测性**：追踪每个决策路径，便于调试和优化。\\\\n\\\\n### 实战案例：一个简单的智能体架构\\\\n\\\\n假设我们构建一个“文档问答助手”，架构如下：\\\\n- **用户请求** → **网关** → **编排服务**（使用LangChain或自研框架）\\\\n- 编排服务：\\\\n  - 调用**意图识别模型**（轻量级BERT）判断用户意图（查询、总结、翻译）。\\\\n  - 根据意图，调用**知识库服务**检索相关文档片段。\\\\n  - 调用**大模型服务**（如GPT-4）生成答案。\\\\n  - 返回结果。\\\\n- 所有服务通过**消息队列**（如RabbitMQ）解耦，确保高可用。\\\\n\\\\n### 架构决策要点\\\\n\\\\n在设计AI原生应用架构时，需权衡以下几点：\\\\n1. **延迟 vs 准确性**：实时推理需要低延迟，但复杂模型可能更准。可考虑缓存或预计算。\\\\n2. **成本 vs 性能**：大模型API调用成本高，可引入本地小模型做预筛选。\\\\n3. **灵活性 vs 可维护性**：智能体编排增加灵活性，但需定义清晰的接口和错误处理机制。\\\\n\\\\n### 小结\\\\n\\\\nAI原生应用的架构核心是**微服务+智能体编排**，它让AI能力像乐高积木一样可组合、可扩展。下一篇文章，我们将深入讨论数据管道设计：如何构建高效、可靠的训练和推理数据流。\\\\n\\\\n（本文为系列第2篇，后续将涉及数据工程、模型部署、监控等主题。）\\\"\\n  },\\n  \\\"outline_update\\\": {\\n    \\\"changes\\\": \\\"无变化，大纲已涵盖本内容。\\\",\\n    \\\"updated_outline\\\": \\\"1. AI原生应用工程化落地：引言与核心挑战（已完成）\\\\n2. AI原生应用的架构设计：从单体到智能体编排（当前）\\\\n3. 数据管道设计：构建高效、可靠的AI数据流（计划中）\\\\n4. 模型部署与推理优化：从实验到生产（计划中）\\\\n5. 监控与可观测性：AI应用的生命线（计划中）\\\\n6. 安全与伦理：AI原生应用的边界（计划中）\\\\n7. 总结与未来展望（计划中）\\\"\\n  },\\n  \\\"accumulated_update\\\": {\\n    \\\"used_examples\\\": [\\\"聊天机器人单体架构问题\\\", \\\"智能客服智能体\\\", \\\"文档问答助手架构\\\"],\\n    \\\"covered_knowledge\\\": [\\\"微服务架构\\\", \\\"智能体编排\\\", \\\"异步消息驱动\\\", \\\"状态管理\\\", \\\"可观测性\\\"],\\n    \\\"writing_style\\\": \\\"技术务实，结合案例，逐步深入\\\"\\n  },\\n  \\\"series_complete\\\": false\\n}\"}', '2026-07-09 15:37:18');
INSERT INTO `ai_agent_instance_transition` VALUES (64, 'blog_series_writer-bf2e0e39ce8844f9abbca1cc1f9b942c', 2, NULL, 'node_3', NULL, 0, 'SUCCESS', 0, NULL, '2026-07-09 15:37:18');
INSERT INTO `ai_agent_instance_transition` VALUES (65, 'blog_series_writer-1e8144b4f8324e9f94f2747935c69eca', 0, NULL, 'node_1', NULL, 0, 'SUCCESS', 0, NULL, '2026-07-09 15:39:19');
INSERT INTO `ai_agent_instance_transition` VALUES (66, 'blog_series_writer-1e8144b4f8324e9f94f2747935c69eca', 1, NULL, 'node_2', NULL, 0, 'SUCCESS', 0, '{\"outline_update\":{\"changes\":\"无调整，当前大纲仍适用。\",\"updated_outline\":\"已有大纲：{{outline}}\"},\"series_status\":\"未完成，还有后续文章待撰写。\",\"accumulated_update\":{\"used_examples\":\"智能推荐系统迁移案例\",\"covered_topics\":\"单体架构局限、微服务优势、进化路径、实战案例\",\"writing_style\":\"技术分析为主，结合案例，保持清晰结构\"},\"article\":{\"title\":\"AI原生应用架构：从单体到微服务的进化路径\",\"content\":\"在上一篇文章中，我们探讨了AI原生应用的基础概念和核心原则，强调了数据驱动、模型即服务和持续迭代的重要性。今天，我们将深入架构层面，分析从传统单体应用到AI原生微服务架构的进化路径，这是实现工程化落地的关键一步。\\n\\n## 单体架构的局限\\n传统AI应用常采用单体架构：所有组件（数据预处理、模型推理、业务逻辑）耦合在一个进程中。这种模式在小规模场景下可行，但随着业务复杂度和数据量的增长，问题逐渐显现：\\n- **扩展性差**：无法独立扩展推理或数据组件，导致资源浪费。\\n- **部署困难**：任何修改都需要重新部署整个应用，影响迭代速度。\\n- **维护成本高**：团队协作时，代码冲突和依赖管理成为瓶颈。\\n\\n## 微服务架构的优势\\nAI原生应用推荐采用微服务架构，将系统拆分为独立服务，每个服务负责特定功能，如：\\n- **数据服务**：处理数据采集、清洗和特征工程。\\n- **模型服务**：封装模型推理逻辑，提供RESTful或gRPC接口。\\n- **业务服务**：协调流程，调用模型服务并返回结果。\\n- **监控服务**：跟踪性能、日志和模型漂移。\\n\\n这种架构带来灵活性：\\n- **独立扩展**：根据负载，仅扩展模型服务实例，节省成本。\\n- **技术异构**：不同服务可用不同语言或框架（如Python用于模型，Go用于业务）。\\n- **快速迭代**：单个服务可独立部署，不影响全局。\\n\\n## 进化路径：从单体到微服务\\n迁移不是一蹴而就，建议分步进行：\\n1. **识别边界**：分析现有单体，划分功能模块（如数据、模型、业务）。\\n2. **提取模型服务**：优先将模型推理抽离为独立服务，使用容器化部署（如Docker）。\\n3. **引入API网关**：统一入口，管理路由、限流和认证。\\n4. **逐步解耦**：依次提取数据服务和业务服务，确保接口稳定。\\n5. **完善监控**：集成日志聚合和指标收集（如Prometheus、ELK）。\\n\\n## 实战案例：智能推荐系统\\n假设我们有一个单体推荐系统，包含用户画像、模型推理和结果排序。迁移后：\\n- 用户画像服务：处理实时特征。\\n- 模型服务：调用深度学习模型，返回候选列表。\\n- 排序服务：基于规则重排。\\n- 网关：接收请求，分发到各服务。\\n\\n这样，当流量高峰时，我们只需扩展模型服务，而其他服务保持稳定。\\n\\n## 注意事项\\n- **服务间通信**：选择轻量协议（gRPC优于HTTP），避免过重。\\n- **数据一致性**：使用事件驱动或分布式事务，确保最终一致。\\n- **版本管理**：模型服务需支持多版本，便于回滚。\\n\\n下一篇文章，我们将讨论如何设计可复用的模型服务层，包括模型加载、推理优化和动态更新。\",\"summary\":\"本文介绍了AI原生应用从单体架构向微服务架构的进化路径，分析了单体架构的局限和微服务的优势，并给出了具体的迁移步骤和实战案例。\"},\"seriesState\":\"{\\n  \\\"article\\\": {\\n    \\\"title\\\": \\\"AI原生应用架构：从单体到微服务的进化路径\\\",\\n    \\\"content\\\": \\\"在上一篇文章中，我们探讨了AI原生应用的基础概念和核心原则，强调了数据驱动、模型即服务和持续迭代的重要性。今天，我们将深入架构层面，分析从传统单体应用到AI原生微服务架构的进化路径，这是实现工程化落地的关键一步。\\\\n\\\\n## 单体架构的局限\\\\n传统AI应用常采用单体架构：所有组件（数据预处理、模型推理、业务逻辑）耦合在一个进程中。这种模式在小规模场景下可行，但随着业务复杂度和数据量的增长，问题逐渐显现：\\\\n- **扩展性差**：无法独立扩展推理或数据组件，导致资源浪费。\\\\n- **部署困难**：任何修改都需要重新部署整个应用，影响迭代速度。\\\\n- **维护成本高**：团队协作时，代码冲突和依赖管理成为瓶颈。\\\\n\\\\n## 微服务架构的优势\\\\nAI原生应用推荐采用微服务架构，将系统拆分为独立服务，每个服务负责特定功能，如：\\\\n- **数据服务**：处理数据采集、清洗和特征工程。\\\\n- **模型服务**：封装模型推理逻辑，提供RESTful或gRPC接口。\\\\n- **业务服务**：协调流程，调用模型服务并返回结果。\\\\n- **监控服务**：跟踪性能、日志和模型漂移。\\\\n\\\\n这种架构带来灵活性：\\\\n- **独立扩展**：根据负载，仅扩展模型服务实例，节省成本。\\\\n- **技术异构**：不同服务可用不同语言或框架（如Python用于模型，Go用于业务）。\\\\n- **快速迭代**：单个服务可独立部署，不影响全局。\\\\n\\\\n## 进化路径：从单体到微服务\\\\n迁移不是一蹴而就，建议分步进行：\\\\n1. **识别边界**：分析现有单体，划分功能模块（如数据、模型、业务）。\\\\n2. **提取模型服务**：优先将模型推理抽离为独立服务，使用容器化部署（如Docker）。\\\\n3. **引入API网关**：统一入口，管理路由、限流和认证。\\\\n4. **逐步解耦**：依次提取数据服务和业务服务，确保接口稳定。\\\\n5. **完善监控**：集成日志聚合和指标收集（如Prometheus、ELK）。\\\\n\\\\n## 实战案例：智能推荐系统\\\\n假设我们有一个单体推荐系统，包含用户画像、模型推理和结果排序。迁移后：\\\\n- 用户画像服务：处理实时特征。\\\\n- 模型服务：调用深度学习模型，返回候选列表。\\\\n- 排序服务：基于规则重排。\\\\n- 网关：接收请求，分发到各服务。\\\\n\\\\n这样，当流量高峰时，我们只需扩展模型服务，而其他服务保持稳定。\\\\n\\\\n## 注意事项\\\\n- **服务间通信**：选择轻量协议（gRPC优于HTTP），避免过重。\\\\n- **数据一致性**：使用事件驱动或分布式事务，确保最终一致。\\\\n- **版本管理**：模型服务需支持多版本，便于回滚。\\\\n\\\\n下一篇文章，我们将讨论如何设计可复用的模型服务层，包括模型加载、推理优化和动态更新。\\\",\\n    \\\"summary\\\": \\\"本文介绍了AI原生应用从单体架构向微服务架构的进化路径，分析了单体架构的局限和微服务的优势，并给出了具体的迁移步骤和实战案例。\\\"\\n  },\\n  \\\"outline_update\\\": {\\n    \\\"changes\\\": \\\"无调整，当前大纲仍适用。\\\",\\n    \\\"updated_outline\\\": \\\"已有大纲：{{outline}}\\\"\\n  },\\n  \\\"accumulated_update\\\": {\\n    \\\"used_examples\\\": \\\"智能推荐系统迁移案例\\\",\\n    \\\"covered_topics\\\": \\\"单体架构局限、微服务优势、进化路径、实战案例\\\",\\n    \\\"writing_style\\\": \\\"技术分析为主，结合案例，保持清晰结构\\\"\\n  },\\n  \\\"series_status\\\": \\\"未完成，还有后续文章待撰写。\\\"\\n}\"}', '2026-07-09 15:39:30');
INSERT INTO `ai_agent_instance_transition` VALUES (67, 'blog_series_writer-1e8144b4f8324e9f94f2747935c69eca', 2, NULL, 'node_3', NULL, 0, 'SUCCESS', 0, NULL, '2026-07-09 15:39:30');
INSERT INTO `ai_agent_instance_transition` VALUES (68, 'blog_series-1cc9d41d02f746bfb442e7cc8f27cbf0', 0, NULL, 'node_1', NULL, 0, 'SUCCESS', 0, NULL, '2026-07-09 16:10:30');
INSERT INTO `ai_agent_instance_transition` VALUES (69, 'blog_series-1cc9d41d02f746bfb442e7cc8f27cbf0', 1, NULL, 'node_2', NULL, 0, 'SUCCESS', 0, '{\"is_series_finished\":false,\"next_seq\":2,\"updated_accumulated\":{\"used_examples\":[\"MNIST手写数字识别\"],\"covered_knowledge\":[\"数据预处理（归一化、one-hot）\",\"三层神经网络结构设计\",\"前向传播与反向传播\",\"ReLU和Softmax激活函数\",\"小批量梯度下降\",\"权重初始化（He初始化）\",\"模型评估与可视化\"],\"writing_style\":\"实践导向，包含完整代码示例和解释\"},\"title\":\"从零开始：用Python搭建你的第一个神经网络\",\"updated_outline\":[\"1. 神经网络基础概念（神经元、激活函数、层结构）\",\"2. 从零搭建神经网络（Python实现，MNIST示例）\",\"3. 优化网络性能（正则化、学习率调整、优化器）\",\"4. 卷积神经网络入门（CNN原理与实践）\",\"5. 循环神经网络入门（RNN与LSTM）\",\"6. 实战项目：图像分类与文本生成\"],\"content\":\"在前一篇文章中，我们了解了神经网络的基本概念，包括神经元、激活函数和层结构。现在，让我们动手实践，用Python从零搭建一个简单的神经网络，实现手写数字识别（MNIST数据集）。\\n\\n## 环境准备\\n首先，确保你安装了Python和必要的库。我们将使用numpy进行矩阵运算，matplotlib进行可视化。\\n```python\\nimport numpy as np\\nimport matplotlib.pyplot as plt\\nfrom sklearn.datasets import fetch_openml\\nfrom sklearn.model_selection import train_test_split\\nfrom sklearn.preprocessing import OneHotEncoder\\n```\\n\\n## 加载和预处理数据\\nMNIST数据集包含28x28像素的手写数字图片，共10个类别（0-9）。\\n```python\\n# 加载数据（首次运行会下载，约11MB）\\nX, y = fetch_openml(\'mnist_784\', version=1, return_X_y=True, as_frame=False)\\nX = X / 255.0  # 归一化到0-1\\n# 转换为one-hot编码\\nencoder = OneHotEncoder(sparse=False)\\ny_onehot = encoder.fit_transform(y.reshape(-1, 1))\\n# 划分训练集和测试集\\nX_train, X_test, y_train, y_test = train_test_split(X, y_onehot, test_size=0.2, random_state=42)\\n```\\n\\n## 定义神经网络结构\\n我们构建一个简单的三层网络：输入层（784个神经元，对应28x28像素）、隐藏层（128个神经元，使用ReLU激活函数）、输出层（10个神经元，使用Softmax激活函数）。\\n```python\\nclass NeuralNetwork:\\n    def __init__(self, input_size, hidden_size, output_size, learning_rate=0.01):\\n        self.lr = learning_rate\\n        # 初始化权重和偏置（使用He初始化）\\n        self.W1 = np.random.randn(input_size, hidden_size) * np.sqrt(2.0/input_size)\\n        self.b1 = np.zeros((1, hidden_size))\\n        self.W2 = np.random.randn(hidden_size, output_size) * np.sqrt(2.0/hidden_size)\\n        self.b2 = np.zeros((1, output_size))\\n    \\n    def relu(self, x):\\n        return np.maximum(0, x)\\n    \\n    def softmax(self, x):\\n        exp_x = np.exp(x - np.max(x, axis=1, keepdims=True))\\n        return exp_x / np.sum(exp_x, axis=1, keepdims=True)\\n    \\n    def forward(self, X):\\n        # 前向传播\\n        self.z1 = np.dot(X, self.W1) + self.b1\\n        self.a1 = self.relu(self.z1)\\n        self.z2 = np.dot(self.a1, self.W2) + self.b2\\n        self.a2 = self.softmax(self.z2)\\n        return self.a2\\n    \\n    def backward(self, X, y, output):\\n        # 反向传播（交叉熵损失）\\n        m = X.shape[0]\\n        dz2 = output - y  # 输出层梯度\\n        dW2 = np.dot(self.a1.T, dz2) / m\\n        db2 = np.sum(dz2, axis=0, keepdims=True) / m\\n        \\n        dz1 = np.dot(dz2, self.W2.T) * (self.z1 > 0)  # ReLU导数\\n        dW1 = np.dot(X.T, dz1) / m\\n        db1 = np.sum(dz1, axis=0, keepdims=True) / m\\n        \\n        # 更新参数\\n        self.W2 -= self.lr * dW2\\n        self.b2 -= self.lr * db2\\n        self.W1 -= self.lr * dW1\\n        self.b1 -= self.lr * db1\\n    \\n    def train(self, X, y, epochs=100, batch_size=32, verbose=True):\\n        n = X.shape[0]\\n        for epoch in range(epochs):\\n            # 随机打乱数据\\n            indices = np.random.permutation(n)\\n            X_shuffled = X[indices]\\n            y_shuffled = y[indices]\\n            \\n            for i in range(0, n, batch_size):\\n                X_batch = X_shuffled[i:i+batch_size]\\n                y_batch = y_shuffled[i:i+batch_size]\\n                output = self.forward(X_batch)\\n                self.backward(X_batch, y_batch, output)\\n            \\n            if verbose and (epoch+1) % 10 == 0:\\n                loss = -np.mean(np.sum(y * np.log(self.forward(X) + 1e-8), axis=1))\\n                acc = np.mean(np.argmax(self.forward(X), axis=1) == np.argmax(y, axis=1))\\n                print(f\\\"Epoch {epoch+1}/{epochs}, Loss: {loss:.4f}, Accuracy: {acc:.4f}\\\")\\n```\\n\\n## 训练模型\\n```python\\n# 实例化网络\\nnn = NeuralNetwork(input_size=784, hidden_size=128, output_size=10, learning_rate=0.01)\\n# 训练（由于计算量较大，这里只训练50个epoch作为演示）\\nnn.train(X_train, y_train, epochs=50, batch_size=64)\\n```\\n\\n## 评估模型\\n```python\\n# 测试集准确率\\ntest_output = nn.forward(X_test)\\npredictions = np.argmax(test_output, axis=1)\\ntrue_labels = np.argmax(y_test, axis=1)\\naccuracy = np.mean(predictions == true_labels)\\nprint(f\\\"Test Accuracy: {accuracy:.4f}\\\")\\n\\n# 可视化一些预测结果\\nfig, axes = plt.subplots(2, 5, figsize=(10, 4))\\nfor i, ax in enumerate(axes.flat):\\n    ax.imshow(X_test[i].reshape(28, 28), cmap=\'gray\')\\n    ax.set_title(f\\\"True: {true_labels[i]}, Pred: {predictions[i]}\\\")\\n    ax.axis(\'off\')\\nplt.tight_layout()\\nplt.show()\\n```\\n\\n## 关键点总结\\n- 数据预处理：归一化和one-hot编码对训练至关重要。\\n- 权重初始化：He初始化适用于ReLU激活函数，避免梯度消失/爆炸。\\n- 小批量梯度下降：平衡了训练速度和稳定性。\\n- 反向传播：通过链式法则计算梯度，更新参数。\\n\\n## 练习与思考\\n1. 尝试调整隐藏层神经元数量（如64、256），观察准确率变化。\\n2. 修改激活函数为sigmoid或tanh，比较效果。\\n3. 添加一个额外的隐藏层，实现更深的网络。\\n\\n下一篇文章中，我们将探讨如何优化网络性能，包括正则化、学习率调整和更高级的优化器。\",\"seriesState\":\"{\\n  \\\"next_seq\\\": 2,\\n  \\\"title\\\": \\\"从零开始：用Python搭建你的第一个神经网络\\\",\\n  \\\"content\\\": \\\"在前一篇文章中，我们了解了神经网络的基本概念，包括神经元、激活函数和层结构。现在，让我们动手实践，用Python从零搭建一个简单的神经网络，实现手写数字识别（MNIST数据集）。\\\\n\\\\n## 环境准备\\\\n首先，确保你安装了Python和必要的库。我们将使用numpy进行矩阵运算，matplotlib进行可视化。\\\\n```python\\\\nimport numpy as np\\\\nimport matplotlib.pyplot as plt\\\\nfrom sklearn.datasets import fetch_openml\\\\nfrom sklearn.model_selection import train_test_split\\\\nfrom sklearn.preprocessing import OneHotEncoder\\\\n```\\\\n\\\\n## 加载和预处理数据\\\\nMNIST数据集包含28x28像素的手写数字图片，共10个类别（0-9）。\\\\n```python\\\\n# 加载数据（首次运行会下载，约11MB）\\\\nX, y = fetch_openml(\'mnist_784\', version=1, return_X_y=True, as_frame=False)\\\\nX = X / 255.0  # 归一化到0-1\\\\n# 转换为one-hot编码\\\\nencoder = OneHotEncoder(sparse=False)\\\\ny_onehot = encoder.fit_transform(y.reshape(-1, 1))\\\\n# 划分训练集和测试集\\\\nX_train, X_test, y_train, y_test = train_test_split(X, y_onehot, test_size=0.2, random_state=42)\\\\n```\\\\n\\\\n## 定义神经网络结构\\\\n我们构建一个简单的三层网络：输入层（784个神经元，对应28x28像素）、隐藏层（128个神经元，使用ReLU激活函数）、输出层（10个神经元，使用Softmax激活函数）。\\\\n```python\\\\nclass NeuralNetwork:\\\\n    def __init__(self, input_size, hidden_size, output_size, learning_rate=0.01):\\\\n        self.lr = learning_rate\\\\n        # 初始化权重和偏置（使用He初始化）\\\\n        self.W1 = np.random.randn(input_size, hidden_size) * np.sqrt(2.0/input_size)\\\\n        self.b1 = np.zeros((1, hidden_size))\\\\n        self.W2 = np.random.randn(hidden_size, output_size) * np.sqrt(2.0/hidden_size)\\\\n        self.b2 = np.zeros((1, output_size))\\\\n    \\\\n    def relu(self, x):\\\\n        return np.maximum(0, x)\\\\n    \\\\n    def softmax(self, x):\\\\n        exp_x = np.exp(x - np.max(x, axis=1, keepdims=True))\\\\n        return exp_x / np.sum(exp_x, axis=1, keepdims=True)\\\\n    \\\\n    def forward(self, X):\\\\n        # 前向传播\\\\n        self.z1 = np.dot(X, self.W1) + self.b1\\\\n        self.a1 = self.relu(self.z1)\\\\n        self.z2 = np.dot(self.a1, self.W2) + self.b2\\\\n        self.a2 = self.softmax(self.z2)\\\\n        return self.a2\\\\n    \\\\n    def backward(self, X, y, output):\\\\n        # 反向传播（交叉熵损失）\\\\n        m = X.shape[0]\\\\n        dz2 = output - y  # 输出层梯度\\\\n        dW2 = np.dot(self.a1.T, dz2) / m\\\\n        db2 = np.sum(dz2, axis=0, keepdims=True) / m\\\\n        \\\\n        dz1 = np.dot(dz2, self.W2.T) * (self.z1 > 0)  # ReLU导数\\\\n        dW1 = np.dot(X.T, dz1) / m\\\\n        db1 = np.sum(dz1, axis=0, keepdims=True) / m\\\\n        \\\\n        # 更新参数\\\\n        self.W2 -= self.lr * dW2\\\\n        self.b2 -= self.lr * db2\\\\n        self.W1 -= self.lr * dW1\\\\n        self.b1 -= self.lr * db1\\\\n    \\\\n    def train(self, X, y, epochs=100, batch_size=32, verbose=True):\\\\n        n = X.shape[0]\\\\n        for epoch in range(epochs):\\\\n            # 随机打乱数据\\\\n            indices = np.random.permutation(n)\\\\n            X_shuffled = X[indices]\\\\n            y_shuffled = y[indices]\\\\n            \\\\n            for i in range(0, n, batch_size):\\\\n                X_batch = X_shuffled[i:i+batch_size]\\\\n                y_batch = y_shuffled[i:i+batch_size]\\\\n                output = self.forward(X_batch)\\\\n                self.backward(X_batch, y_batch, output)\\\\n            \\\\n            if verbose and (epoch+1) % 10 == 0:\\\\n                loss = -np.mean(np.sum(y * np.log(self.forward(X) + 1e-8), axis=1))\\\\n                acc = np.mean(np.argmax(self.forward(X), axis=1) == np.argmax(y, axis=1))\\\\n                print(f\\\\\\\"Epoch {epoch+1}/{epochs}, Loss: {loss:.4f}, Accuracy: {acc:.4f}\\\\\\\")\\\\n```\\\\n\\\\n## 训练模型\\\\n```python\\\\n# 实例化网络\\\\nnn = NeuralNetwork(input_size=784, hidden_size=128, output_size=10, learning_rate=0.01)\\\\n# 训练（由于计算量较大，这里只训练50个epoch作为演示）\\\\nnn.train(X_train, y_train, epochs=50, batch_size=64)\\\\n```\\\\n\\\\n## 评估模型\\\\n```python\\\\n# 测试集准确率\\\\ntest_output = nn.forward(X_test)\\\\npredictions = np.argmax(test_output, axis=1)\\\\ntrue_labels = np.argmax(y_test, axis=1)\\\\naccuracy = np.mean(predictions == true_labels)\\\\nprint(f\\\\\\\"Test Accuracy: {accuracy:.4f}\\\\\\\")\\\\n\\\\n# 可视化一些预测结果\\\\nfig, axes = plt.subplots(2, 5, figsize=(10, 4))\\\\nfor i, ax in enumerate(axes.flat):\\\\n    ax.imshow(X_test[i].reshape(28, 28), cmap=\'gray\')\\\\n    ax.set_title(f\\\\\\\"True: {true_labels[i]}, Pred: {predictions[i]}\\\\\\\")\\\\n    ax.axis(\'off\')\\\\nplt.tight_layout()\\\\nplt.show()\\\\n```\\\\n\\\\n## 关键点总结\\\\n- 数据预处理：归一化和one-hot编码对训练至关重要。\\\\n- 权重初始化：He初始化适用于ReLU激活函数，避免梯度消失/爆炸。\\\\n- 小批量梯度下降：平衡了训练速度和稳定性。\\\\n- 反向传播：通过链式法则计算梯度，更新参数。\\\\n\\\\n## 练习与思考\\\\n1. 尝试调整隐藏层神经元数量（如64、256），观察准确率变化。\\\\n2. 修改激活函数为sigmoid或tanh，比较效果。\\\\n3. 添加一个额外的隐藏层，实现更深的网络。\\\\n\\\\n下一篇文章中，我们将探讨如何优化网络性能，包括正则化、学习率调整和更高级的优化器。\\\",\\n  \\\"updated_outline\\\": [\\n    \\\"1. 神经网络基础概念（神经元、激活函数、层结构）\\\",\\n    \\\"2. 从零搭建神经网络（Python实现，MNIST示例）\\\",\\n    \\\"3. 优化网络性能（正则化、学习率调整、优化器）\\\",\\n    \\\"4. 卷积神经网络入门（CNN原理与实践）\\\",\\n    \\\"5. 循环神经网络入门（RNN与LSTM）\\\",\\n    \\\"6. 实战项目：图像分类与文本生成\\\"\\n  ],\\n  \\\"updated_accumulated\\\": {\\n    \\\"used_examples\\\": [\\\"MNIST手写数字识别\\\"],\\n    \\\"covered_knowledge\\\": [\\\"数据预处理（归一化、one-hot）\\\", \\\"三层神经网络结构设计\\\", \\\"前向传播与反向传播\\\", \\\"ReLU和Softmax激活函数\\\", \\\"小批量梯度下降\\\", \\\"权重初始化（He初始化）\\\", \\\"模型评估与可视化\\\"],\\n    \\\"writing_style\\\": \\\"实践导向，包含完整代码示例和解释\\\"\\n  },\\n  \\\"is_series_finished\\\": false\\n}\"}', '2026-07-09 16:10:44');
INSERT INTO `ai_agent_instance_transition` VALUES (70, 'blog_series-1cc9d41d02f746bfb442e7cc8f27cbf0', 2, NULL, 'node_3', NULL, 0, 'SUCCESS', 0, NULL, '2026-07-09 16:10:44');
INSERT INTO `ai_agent_instance_transition` VALUES (71, 'blog_series-7341c7a6d5614cd5bb91d0573c95af05', 0, NULL, 'node_1', NULL, 0, 'SUCCESS', 0, NULL, '2026-07-09 16:11:44');
INSERT INTO `ai_agent_instance_transition` VALUES (72, 'blog_series-7341c7a6d5614cd5bb91d0573c95af05', 1, NULL, 'node_2', NULL, 0, 'SUCCESS', 0, '{\"next_article\":{\"title\":\"AI原生应用的架构设计：从单体到智能体编排\",\"content\":\"## 前言\\n在上一篇文章中，我们探讨了AI原生应用与传统软件的本质区别，以及工程化落地的核心挑战。今天，我们将深入架构层面，解析AI原生应用如何从传统的单体架构演进为智能体（Agent）编排模式，并讨论这种转变背后的设计原则与实战经验。\\n\\n## 单体架构的局限\\n传统的单体应用将所有功能模块（包括AI模型调用）打包在一个进程中。虽然初期开发简单，但随着AI能力增强，单体架构暴露出几个问题：\\n- **模型耦合**：模型更新或替换导致整个应用重新部署。\\n- **资源不均**：AI推理消耗大量GPU/CPU资源，与业务逻辑争抢资源。\\n- **扩展困难**：无法独立扩展AI服务与业务服务。\\n\\n## 微服务化的第一步：解耦AI能力\\n将AI模型封装为独立的微服务（如推理服务、特征服务）是常见的演进路径。例如，使用gRPC或REST API将大语言模型（LLM）调用暴露为服务，业务层通过异步消息队列调用。这种模式带来了：\\n- **独立部署**：模型更新不影响业务代码。\\n- **弹性伸缩**：根据推理请求量动态调整AI服务实例数。\\n- **技术多样性**：不同模型（如GPT-4、Claude）可共存于同一架构。\\n\\n## 智能体编排：AI原生架构的核心模式\\n随着AI应用复杂化（如多步骤推理、工具调用、记忆管理），单纯的微服务已不够。智能体编排模式应运而生，它将应用拆分为多个智能体（Agent），每个智能体负责一个子任务，并通过编排引擎协同工作。\\n\\n### 智能体设计原则\\n- **单一职责**：每个智能体只做一件事（如“代码生成智能体”或“数据检索智能体”）。\\n- **上下文隔离**：智能体间通过结构化消息传递，避免状态污染。\\n- **可观测性**：每个智能体暴露日志、指标，便于调试与监控。\\n\\n### 编排引擎的职责\\n编排引擎（如LangGraph、CrewAI或自研框架）负责：\\n- **路由**：根据用户请求，决定调用哪个智能体。\\n- **调度**：管理智能体执行顺序与并行。\\n- **容错**：处理智能体失败或超时，重试或降级。\\n- **记忆**：维护对话历史或任务上下文。\\n\\n### 实战案例：智能客服系统\\n假设我们要构建一个智能客服系统，传统做法是让一个LLM直接回答所有问题。但采用智能体编排后：\\n1. **意图识别智能体**：判断用户问题类型（退款、咨询、投诉）。\\n2. **知识库检索智能体**：从向量数据库检索相关文档。\\n3. **答案生成智能体**：基于检索结果生成回答。\\n4. **情绪检测智能体**：分析用户情绪，决定是否转人工。\\n\\n编排引擎依次调用这些智能体，每个智能体返回结构化结果，最终合成回复。这种设计使得每个智能体可以独立优化（如知识库检索智能体可单独升级为混合检索），且整体系统更鲁棒。\\n\\n## 架构演进中的陷阱与应对\\n- **过度拆分**：智能体过多导致编排复杂度飙升。建议开始时只有3-5个智能体，按需拆分。\\n- **状态同步**：多个智能体共享状态时，使用外部存储（如Redis）而非内存传递。\\n- **延迟累积**：串行调用智能体可能增加响应时间。采用并行调用或流式输出优化。\\n\\n## 总结\\nAI原生应用的架构设计是一个从单体到微服务，再到智能体编排的演进过程。关键在于：\\n1. 解耦AI能力与业务逻辑。\\n2. 以智能体为单位组织功能。\\n3. 使用编排引擎管理协作。\\n\\n下一篇文章，我们将深入讨论智能体编排中的关键技术：提示工程（Prompt Engineering）与工具调用（Function Calling）的最佳实践。\",\"outline_update\":\"无调整，保持原大纲。\",\"accumulated_update\":{\"used_examples\":[\"智能客服系统（意图识别、知识库检索、答案生成、情绪检测智能体）\",\"单体架构与微服务对比\",\"智能体编排引擎职责\"],\"covered_knowledge\":[\"单体架构局限\",\"AI能力微服务化\",\"智能体设计原则\",\"编排引擎职责\",\"架构陷阱与应对\"],\"style\":\"技术性、实战导向，包含案例与设计原则\"},\"series_complete\":false},\"seriesState\":\"{\\n  \\\"next_article\\\": {\\n    \\\"title\\\": \\\"AI原生应用的架构设计：从单体到智能体编排\\\",\\n    \\\"content\\\": \\\"## 前言\\\\n在上一篇文章中，我们探讨了AI原生应用与传统软件的本质区别，以及工程化落地的核心挑战。今天，我们将深入架构层面，解析AI原生应用如何从传统的单体架构演进为智能体（Agent）编排模式，并讨论这种转变背后的设计原则与实战经验。\\\\n\\\\n## 单体架构的局限\\\\n传统的单体应用将所有功能模块（包括AI模型调用）打包在一个进程中。虽然初期开发简单，但随着AI能力增强，单体架构暴露出几个问题：\\\\n- **模型耦合**：模型更新或替换导致整个应用重新部署。\\\\n- **资源不均**：AI推理消耗大量GPU/CPU资源，与业务逻辑争抢资源。\\\\n- **扩展困难**：无法独立扩展AI服务与业务服务。\\\\n\\\\n## 微服务化的第一步：解耦AI能力\\\\n将AI模型封装为独立的微服务（如推理服务、特征服务）是常见的演进路径。例如，使用gRPC或REST API将大语言模型（LLM）调用暴露为服务，业务层通过异步消息队列调用。这种模式带来了：\\\\n- **独立部署**：模型更新不影响业务代码。\\\\n- **弹性伸缩**：根据推理请求量动态调整AI服务实例数。\\\\n- **技术多样性**：不同模型（如GPT-4、Claude）可共存于同一架构。\\\\n\\\\n## 智能体编排：AI原生架构的核心模式\\\\n随着AI应用复杂化（如多步骤推理、工具调用、记忆管理），单纯的微服务已不够。智能体编排模式应运而生，它将应用拆分为多个智能体（Agent），每个智能体负责一个子任务，并通过编排引擎协同工作。\\\\n\\\\n### 智能体设计原则\\\\n- **单一职责**：每个智能体只做一件事（如“代码生成智能体”或“数据检索智能体”）。\\\\n- **上下文隔离**：智能体间通过结构化消息传递，避免状态污染。\\\\n- **可观测性**：每个智能体暴露日志、指标，便于调试与监控。\\\\n\\\\n### 编排引擎的职责\\\\n编排引擎（如LangGraph、CrewAI或自研框架）负责：\\\\n- **路由**：根据用户请求，决定调用哪个智能体。\\\\n- **调度**：管理智能体执行顺序与并行。\\\\n- **容错**：处理智能体失败或超时，重试或降级。\\\\n- **记忆**：维护对话历史或任务上下文。\\\\n\\\\n### 实战案例：智能客服系统\\\\n假设我们要构建一个智能客服系统，传统做法是让一个LLM直接回答所有问题。但采用智能体编排后：\\\\n1. **意图识别智能体**：判断用户问题类型（退款、咨询、投诉）。\\\\n2. **知识库检索智能体**：从向量数据库检索相关文档。\\\\n3. **答案生成智能体**：基于检索结果生成回答。\\\\n4. **情绪检测智能体**：分析用户情绪，决定是否转人工。\\\\n\\\\n编排引擎依次调用这些智能体，每个智能体返回结构化结果，最终合成回复。这种设计使得每个智能体可以独立优化（如知识库检索智能体可单独升级为混合检索），且整体系统更鲁棒。\\\\n\\\\n## 架构演进中的陷阱与应对\\\\n- **过度拆分**：智能体过多导致编排复杂度飙升。建议开始时只有3-5个智能体，按需拆分。\\\\n- **状态同步**：多个智能体共享状态时，使用外部存储（如Redis）而非内存传递。\\\\n- **延迟累积**：串行调用智能体可能增加响应时间。采用并行调用或流式输出优化。\\\\n\\\\n## 总结\\\\nAI原生应用的架构设计是一个从单体到微服务，再到智能体编排的演进过程。关键在于：\\\\n1. 解耦AI能力与业务逻辑。\\\\n2. 以智能体为单位组织功能。\\\\n3. 使用编排引擎管理协作。\\\\n\\\\n下一篇文章，我们将深入讨论智能体编排中的关键技术：提示工程（Prompt Engineering）与工具调用（Function Calling）的最佳实践。\\\",\\n    \\\"outline_update\\\": \\\"无调整，保持原大纲。\\\",\\n    \\\"accumulated_update\\\": {\\n      \\\"used_examples\\\": [\\\"智能客服系统（意图识别、知识库检索、答案生成、情绪检测智能体）\\\", \\\"单体架构与微服务对比\\\", \\\"智能体编排引擎职责\\\"],\\n      \\\"covered_knowledge\\\": [\\\"单体架构局限\\\", \\\"AI能力微服务化\\\", \\\"智能体设计原则\\\", \\\"编排引擎职责\\\", \\\"架构陷阱与应对\\\"],\\n      \\\"style\\\": \\\"技术性、实战导向，包含案例与设计原则\\\"\\n    },\\n    \\\"series_complete\\\": false\\n  }\\n}\"}', '2026-07-09 16:11:56');
INSERT INTO `ai_agent_instance_transition` VALUES (73, 'blog_series-7341c7a6d5614cd5bb91d0573c95af05', 2, NULL, 'node_3', NULL, 0, 'SUCCESS', 0, NULL, '2026-07-09 16:11:56');
INSERT INTO `ai_agent_instance_transition` VALUES (74, 'blog_series_writer-d570f38cee474461b92823a9c1e785a0', 0, NULL, 'node_1', NULL, 0, 'SUCCESS', 0, NULL, '2026-07-10 09:51:46');
INSERT INTO `ai_agent_instance_transition` VALUES (75, 'blog_series-55f2ee52c5d74099a6ba4b5b0dcf3137', 0, NULL, 'node_1', NULL, 0, 'SUCCESS', 0, NULL, '2026-07-10 09:51:47');
INSERT INTO `ai_agent_instance_transition` VALUES (76, 'blog_series_writer-423f486d6dc2470c839d8939838423c6', 0, NULL, 'node_1', NULL, 0, 'SUCCESS', 0, NULL, '2026-07-11 15:45:03');
INSERT INTO `ai_agent_instance_transition` VALUES (77, 'blog_series-e84deb4842674fe99e0dc1fe1e966039', 0, NULL, 'node_1', NULL, 0, 'SUCCESS', 0, NULL, '2026-07-11 15:45:03');
INSERT INTO `ai_agent_instance_transition` VALUES (78, 'blog_series_writer-34a06b385b064001b76f854d8b6dc610', 0, NULL, 'node_1', NULL, 0, 'SUCCESS', 0, NULL, '2026-07-13 15:22:00');
INSERT INTO `ai_agent_instance_transition` VALUES (79, 'blog_series-f65005e14aad4cc5a6fd2cfa968e23c8', 0, NULL, 'node_1', NULL, 0, 'SUCCESS', 0, NULL, '2026-07-13 15:22:00');

-- ----------------------------
-- Table structure for ai_agent_iteration
-- ----------------------------
DROP TABLE IF EXISTS `ai_agent_iteration`;
CREATE TABLE `ai_agent_iteration`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `chain_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '迭代链唯一标识，业务键（= 一个\"系列\"实例）',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '链名称，如\"30天Java进阶\"',
  `agent_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '每一轮用哪个 Agent 跑（跨版本稳定，运行时取该 agent_code 最新启用版本）',
  `cron` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '推进节律，如 \"0 0 9 * * ?\"（每天9点）',
  `next_run_at` datetime NOT NULL COMMENT '下一轮应触发时间（Driver 扫这个字段决定谁到点）',
  `seq` int(11) NOT NULL DEFAULT 0 COMMENT '已完成轮次（= 已写到第几篇）',
  `max_iterations` int(11) NULL DEFAULT NULL COMMENT '轮次上限（可空，防无限连载；到顶置 COMPLETED）',
  `until_expr` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '出链条件 SpEL（复用 ConditionCompiler），如 \"getString(\'outlineDone\') == \'true\'\"；对上一轮产物求值',
  `carry_over` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '★carry-over 映射(JSON)：上一轮产物键 → 下一轮 inputs 键',
  `seed_inputs` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '首轮种子入参(JSON)：第 0 轮没有上一轮，用它启动（如系列主题）',
  `last_instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '上一轮实例 id（carry-over 数据源指针；首轮为空）',
  `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '归属用户ID',
  `conversation_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '关联会话ID',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE | PAUSED | COMPLETED | FAILED',
  `consecutive_fails` int(11) NOT NULL DEFAULT 0 COMMENT '连续失败次数，达阈值自动 PAUSED（防定时打空转）',
  `error_msg` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '最近一次推进失败原因',
  `lock_version` int(11) NOT NULL DEFAULT 0 COMMENT '乐观锁：advance 用 CAS 防同一轮重复推进（配合 Redis 锁双保险）',
  `webhook_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '每轮 advance 成功后回调的 URL（如 blog 落库接口），空则不回调；见 编排回调Webhook设计.md',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_chain_id`(`chain_id` ASC) USING BTREE,
  INDEX `idx_due`(`status` ASC, `next_run_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI 智能体跨实例迭代链（系列递推）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_agent_iteration
-- ----------------------------
INSERT INTO `ai_agent_iteration` VALUES (1, 'blog_series_writer-chain-demo01', 'AI原生应用工程化落地', 'blog_series_writer', '0 0 9 * * ?', '2026-07-14 09:00:00', 2, 30, 'getString(\'outlineDone\') == \'true\'', '{\"outline\":\"outline\",\"accumulated\":\"accumulated\",\"articleTitle\":\"history\",\"prevSeq\":\"prevSeq\"}', '{\"topic\":\"AI原生应用工程化落地\"}', 'blog_series_writer-1e8144b4f8324e9f94f2747935c69eca', NULL, NULL, 'PAUSED', 3, '本轮实例失败: 状态[node_2]执行失败: AI调用IO异常: Target host is not specified', 3, 'http://127.0.0.1:8082/blog/admin/blog/webhook/series-append', '2026-07-08 19:21:03', '2026-07-13 15:21:59');
INSERT INTO `ai_agent_iteration` VALUES (3, 'blog_series-chain-55f07fe784644443bcb013b66c7993cc', 'AI原生应用工程化落地', 'blog_series', '0 0 9 * * ?', '2026-07-14 09:00:00', 2, 30, 'getString(\'outlineDone\') == \'true\'', '{\"outline\":\"outline\",\"accumulated\":\"accumulated\",\"articleTitle\":\"history\",\"prevSeq\":\"prevSeq\"}', '{\"topic\":\"AI原生应用工程化落地\"}', 'blog_series-7341c7a6d5614cd5bb91d0573c95af05', NULL, NULL, 'PAUSED', 3, '本轮实例失败: 状态[node_2]执行失败: AI调用IO异常: Target host is not specified', 2, 'http://127.0.0.1:8082/blog/admin/blog/webhook/series-append', '2026-07-09 16:09:59', '2026-07-13 15:21:59');

-- ----------------------------
-- Table structure for ai_flow
-- ----------------------------
DROP TABLE IF EXISTS `ai_flow`;
CREATE TABLE `ai_flow`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '流程ID',
  `flow_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '流程编码，全局唯一',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '流程名称',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '流程描述',
  `version` int(11) NOT NULL DEFAULT 1 COMMENT '版本号，用于缓存键与灰度',
  `default_profile_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '默认模型档案编码',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态：0=停用 1=启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `engine_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'DAG' COMMENT '执行内核：DAG | STATE_MACHINE',
  `max_transitions` int(11) NOT NULL DEFAULT 100 COMMENT '状态机全局转移次数上限，防死循环；per-Flow 可配',
  `max_agent_depth` int(11) NOT NULL DEFAULT 8 COMMENT '递归子 Agent 最大深度，防无限递归（阶段 3 用）',
  `webhook_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '流程级回调URL：实例终态后POST产物(INSTANCE_SUCCESS/FAILED)，空则不回调',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_flow_code`(`flow_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 20 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI流程定义表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_flow
-- ----------------------------
INSERT INTO `ai_flow` VALUES (5, 'blog_series', '博客系列生成', '按系列大纲逐篇生成博客，边写边规划', 1, 'DS-V3-001', 1, '2026-07-08 17:09:27', '2026-07-08 17:09:27', 'STATE_MACHINE', 100, 8, NULL);
INSERT INTO `ai_flow` VALUES (6, 'daily_blog_creator', '每日博客创作', '每天自动创建一个博客文章：获取今日信息→生成主题→撰写文章→润色优化→输出最终博客', 1, 'DS-V3-001', 1, '2026-07-23 20:16:11', '2026-07-23 20:16:11', 'DAG', 100, 8, NULL);
INSERT INTO `ai_flow` VALUES (7, 'blog_outline_agent', '博客目录生成 Agent', '根据用户提供的主题，自动生成带目录结构的完整博客文章', 1, 'DS-V3-001', 1, '2026-07-23 20:23:59', '2026-07-23 20:23:59', 'DAG', 100, 8, NULL);
INSERT INTO `ai_flow` VALUES (18, 'daily_blog_generator', '每日博客生成流程', '按天循环生成博客：每天先产出结构化大纲，再撰写完整正文，并经过内容检查确认，最终汇总为多篇可发布文章。', 1, 'DS-V3-001', 1, '2026-08-11 19:42:45', '2026-08-11 19:42:45', 'DAG', 100, 8, NULL);
INSERT INTO `ai_flow` VALUES (19, 'blog_generation', '博客生成流程', '根据用户提供的主题、风格和目标读者，自动生成完整博客文章：先规划提纲，再撰写初稿，最后润色优化。', 1, 'DS-V3-001', 1, '2026-08-11 20:31:33', '2026-08-11 20:31:33', 'DAG', 100, 8, NULL);

-- ----------------------------
-- Table structure for ai_flow_draft
-- ----------------------------
DROP TABLE IF EXISTS `ai_flow_draft`;
CREATE TABLE `ai_flow_draft`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `draft_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '草稿业务ID（工具入参用）',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '归属会话（Copilot conversationId）',
  `user_id` bigint(20) NOT NULL COMMENT '归属用户（所有访问均须匹配）',
  `flow_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '目标流程编码（提交时用）',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '流程名称',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '流程描述',
  `engine_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'DAG' COMMENT '执行内核 DAG|STATE_MACHINE',
  `graph_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '草稿图 JSON（FlowDefinition 序列化）',
  `revision` bigint(20) NOT NULL DEFAULT 0 COMMENT '乐观锁版本，每次变更递增',
  `last_validated_revision` bigint(20) NULL DEFAULT NULL COMMENT '最近一次无 ERROR 校验的草稿版本',
  `last_simulated_revision` bigint(20) NULL DEFAULT NULL COMMENT '最近一次完成模拟的草稿版本',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'BUILDING' COMMENT 'BUILDING|COMMITTED|ABANDONED',
  `committed_flow_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '提交后的流程编码',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_draft_id`(`draft_id` ASC) USING BTREE,
  INDEX `idx_user_session`(`user_id` ASC, `session_id` ASC) USING BTREE,
  INDEX `idx_update_time`(`update_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 20 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI流程生成草稿' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of ai_flow_draft
-- ----------------------------
INSERT INTO `ai_flow_draft` VALUES (11, 'd_6511d0afb1a3411091112aaa75f0e45d', '5385fd6e-03e4-4d99-8326-8be6ba89fd4d', 2052290101098295297, NULL, 'AI博客每日生成流程', '每日自动生成一篇AI主题博客：选题、写作、质量审核，审核不通过则重写，通过后输出最终博客。', 'STATE_MACHINE', '{\"flowCode\":null,\"name\":\"AI博客每日生成流程\",\"description\":\"每日自动生成一篇AI主题博客：选题、写作、质量审核，审核不通过则重写，通过后输出最终博客。\",\"version\":1,\"defaultProfileCode\":null,\"engineType\":\"STATE_MACHINE\",\"maxTransitions\":100,\"maxAgentDepth\":8,\"webhookUrl\":null,\"nodes\":[{\"nodeCode\":\"topic_selection\",\"name\":\"AI主题选题\",\"nodeType\":\"PROMPT\",\"systemPrompt\":null,\"promptTemplate\":\"请为今日的AI主题博客选择一个有趣且有价值的选题。\\n\\n要求：\\n- 主题围绕人工智能领域的最新趋势、技术或应用\\n- 选题要具体、有讨论价值\\n- 输出一个明确的博客标题和一句话选题说明\\n\\n请以JSON格式输出：\\n{\\\"title\\\": \\\"博客标题\\\", \\\"topic_summary\\\": \\\"选题说明\\\"}\",\"profileCode\":\"DS-V3-CREATIVE-003\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"topic\",\"outputMode\":\"JSON\",\"nodeConfig\":{},\"rememberTrace\":false,\"stateType\":\"ENTRY\",\"sortNo\":0},{\"nodeCode\":\"blog_writing\",\"name\":\"博客正文写作\",\"nodeType\":\"PROMPT\",\"systemPrompt\":null,\"promptTemplate\":\"请根据以下博客选题撰写一篇完整的博客文章。\\n\\n选题标题：#{topic.title}\\n选题说明：#{topic.topic_summary}\\n\\n写作要求：\\n- 结构清晰，包含引言、主体、结论\\n- 语言专业但通俗易懂\\n- 篇幅约1000-1500字\\n- 若存在上一版草稿（#{draft_content}），请基于审核反馈进行改进重写\\n\\n请以JSON格式输出：\\n{\\\"content\\\": \\\"博客正文全文\\\"}\",\"profileCode\":\"DS-V3-CREATIVE-003\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"draft_content\",\"outputMode\":\"JSON\",\"nodeConfig\":{},\"rememberTrace\":false,\"stateType\":\"NORMAL\",\"sortNo\":0},{\"nodeCode\":\"quality_review\",\"name\":\"质量审核\",\"nodeType\":\"PROMPT\",\"systemPrompt\":null,\"promptTemplate\":\"请审核以下博客文章的质量。\\n\\n博客标题：#{topic.title}\\n博客正文：#{draft_content.content}\\n\\n从以下维度审核：\\n1. 内容准确性（是否有事实错误）\\n2. 结构逻辑（是否条理清晰）\\n3. 语言表达（是否通顺专业）\\n4. 主题契合度（是否紧扣选题）\\n\\n请以JSON格式输出审核结论：\\n{\\\"pass\\\": true或false, \\\"review_feedback\\\": \\\"具体的改进建议，若通过则为空\\\"}\",\"profileCode\":\"DS-V3-JSON-004\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"review_result\",\"outputMode\":\"JSON\",\"nodeConfig\":{},\"rememberTrace\":false,\"stateType\":\"NORMAL\",\"sortNo\":0}],\"edges\":[]}', 3, NULL, NULL, 'BUILDING', NULL, '2026-08-11 17:02:12', '2026-08-11 17:02:20');
INSERT INTO `ai_flow_draft` VALUES (12, 'd_46233ac7a43c49e9acb38e24ceb06fbf', '490874ae-16fd-4f4d-b1eb-51737e4315c9', 2052290101098295297, NULL, 'AI Agent 博客每日生成', '根据输入的主题，自动生成博客大纲、正文草稿并润色优化，最终输出一篇完整博客文章', 'DAG', '{\"flowCode\":null,\"name\":\"AI Agent 博客每日生成\",\"description\":\"根据输入的主题，自动生成博客大纲、正文草稿并润色优化，最终输出一篇完整博客文章\",\"version\":1,\"defaultProfileCode\":null,\"engineType\":\"DAG\",\"maxTransitions\":100,\"maxAgentDepth\":8,\"webhookUrl\":null,\"nodes\":[{\"nodeCode\":\"start\",\"name\":\"流程入口\",\"nodeType\":\"START\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":null,\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"inputs\":{\"topic\":{\"type\":\"string\",\"description\":\"今日要生成的博客主题\"}}},\"rememberTrace\":false,\"stateType\":null,\"sortNo\":0},{\"nodeCode\":\"gen_outline\",\"name\":\"生成博客大纲\",\"nodeType\":\"PROMPT\",\"systemPrompt\":null,\"promptTemplate\":\"你是一位资深技术博客主编。请根据主题「{{topic}}」生成一篇结构清晰的博客文章大纲。\\n\\n要求：\\n1. 大纲需包含：标题 title、引言 intro、若干章节 sections（每节含小标题 heading 和要点 bullet_points）、结论 conclusion。\\n2. 内容要逻辑连贯、层次分明，适合作为后续正文生成的骨架。\\n3. 只输出 JSON，不要输出任何其他文字。\\n\\n输出格式：\\n{\\\"title\\\": \\\"...\\\", \\\"intro\\\": \\\"...\\\", \\\"sections\\\": [{\\\"heading\\\": \\\"...\\\", \\\"bullet_points\\\": [\\\"...\\\"]}], \\\"conclusion\\\": \\\"...\\\"}\",\"profileCode\":\"DS-V3-CREATIVE-003\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"outline\",\"outputMode\":\"JSON\",\"nodeConfig\":{},\"rememberTrace\":false,\"stateType\":null,\"sortNo\":0},{\"nodeCode\":\"gen_draft\",\"name\":\"生成正文草稿\",\"nodeType\":\"PROMPT\",\"systemPrompt\":null,\"promptTemplate\":\"请基于以下大纲，围绕主题「{{topic}}」撰写一篇完整的博客文章正文草稿。\\n\\n大纲：\\n{{outline}}\\n\\n要求：\\n1. 严格按照大纲的章节结构展开，每节内容充实、有实际信息量。\\n2. 语言自然流畅，符合中文技术博客的阅读习惯。\\n3. 输出为 Markdown 格式，包含标题（#）、小节标题（##）与正文段落。\\n\\n请直接输出完整的 Markdown 正文，不要输出大纲 JSON 本身。\",\"profileCode\":\"DS-V3-CREATIVE-003\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"draft\",\"outputMode\":\"TEXT\",\"nodeConfig\":{},\"rememberTrace\":false,\"stateType\":null,\"sortNo\":0}],\"edges\":[]}', 3, NULL, NULL, 'BUILDING', NULL, '2026-08-11 17:27:41', '2026-08-11 17:27:48');
INSERT INTO `ai_flow_draft` VALUES (13, 'd_f4233f2c480a483aa4a51215d422ad37', '2ff7b234-ea93-480d-9454-8da51d54fbff', 2052290101098295297, NULL, 'AI Agent 每日博客生成', '每日自动生成 AI 主题博客：从选题规划、正文撰写到润色校对，最终产出结构化博客文章。', 'DAG', '{\"flowCode\":null,\"name\":\"AI Agent 每日博客生成\",\"description\":\"每日自动生成 AI 主题博客：从选题规划、正文撰写到润色校对，最终产出结构化博客文章。\",\"version\":1,\"defaultProfileCode\":\"DS-V3-001\",\"engineType\":\"DAG\",\"maxTransitions\":100,\"maxAgentDepth\":8,\"webhookUrl\":null,\"nodes\":[{\"nodeCode\":\"start\",\"name\":\"流程入口\",\"nodeType\":\"START\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":null,\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"inputs\":{\"topic\":{\"type\":\"string\",\"description\":\"博客主题方向\"},\"audience\":{\"type\":\"string\",\"description\":\"目标读者\"},\"tone\":{\"type\":\"string\",\"description\":\"语气风格\"}}},\"rememberTrace\":false,\"stateType\":null,\"sortNo\":0},{\"nodeCode\":\"outline\",\"name\":\"选题规划\",\"nodeType\":\"PROMPT\",\"systemPrompt\":\"你是一名资深技术内容策划。你负责为博客文章制定清晰、有逻辑、可执行的大纲。你的输出必须是严格的 JSON，不要包含任何额外文字。\",\"promptTemplate\":\"请为以下主题规划一篇博客文章的大纲。\\n\\n主题方向：{{topic}}\\n目标读者：{{audience}}\\n语气风格：{{tone}}\\n\\n请输出严格 JSON，字段结构如下：\\n{\\n  \\\"title\\\": \\\"建议的文章标题\\\",\\n  \\\"intro\\\": \\\"开篇导语要点\\\",\\n  \\\"sections\\\": [\\n    {\\\"heading\\\": \\\"章节标题\\\", \\\"points\\\": [\\\"要点1\\\", \\\"要点2\\\"]}\\n  ],\\n  \\\"conclusion\\\": \\\"结尾要点\\\"\\n}\\n只输出 JSON。\",\"profileCode\":null,\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":0.7,\"maxTokens\":1200,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"outline\",\"outputMode\":\"JSON\",\"nodeConfig\":{},\"rememberTrace\":false,\"stateType\":null,\"sortNo\":0}],\"edges\":[]}', 3, NULL, NULL, 'BUILDING', NULL, '2026-08-11 17:46:57', '2026-08-11 17:47:05');
INSERT INTO `ai_flow_draft` VALUES (14, 'd_e91d7b1f3b714eb8a21ac79a68a5c36f', 'ae4158dc-96ae-48f9-bb9f-be93912e0371', 2052290101098295297, 'daily_blog_generator', 'AI 博客每日生成流程', '每天根据用户提供的主题、风格、受众和语言，自动完成博客选题、写作与润色，最终产出一篇可直接发布的博客文章。', 'DAG', '{\"flowCode\":\"daily_blog_generator\",\"name\":\"AI 博客每日生成流程\",\"description\":\"每天根据用户提供的主题、风格、受众和语言，自动完成博客选题、写作与润色，最终产出一篇可直接发布的博客文章。\",\"version\":1,\"defaultProfileCode\":\"DS-V3-001\",\"engineType\":\"DAG\",\"maxTransitions\":100,\"maxAgentDepth\":8,\"webhookUrl\":null,\"nodes\":[{\"nodeCode\":\"start\",\"name\":\"开始\",\"nodeType\":\"START\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":null,\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"inputs\":{\"topic\":{\"type\":\"string\",\"description\":\"博客主题或方向\"},\"style\":{\"type\":\"string\",\"description\":\"写作风格（如专业、轻松、幽默）\"},\"audience\":{\"type\":\"string\",\"description\":\"目标读者\"},\"language\":{\"type\":\"string\",\"description\":\"输出语言\"}}},\"rememberTrace\":false,\"stateType\":null,\"sortNo\":0},{\"nodeCode\":\"gen_outline\",\"name\":\"生成博客大纲\",\"nodeType\":\"PROMPT\",\"systemPrompt\":\"你是资深内容策划，擅长为博客文章设计清晰、有吸引力、结构完整的大纲。你只输出 JSON，不输出任何其他文字。\",\"promptTemplate\":\"请为以下主题设计一篇博客文章的大纲。\\n\\n主题：{{topic}}\\n目标读者：{{audience}}\\n写作风格：{{style}}\\n输出语言：{{language}}\\n\\n请只输出 JSON，结构如下：\\n{\\n  \\\"title\\\": \\\"文章标题\\\",\\n  \\\"intro\\\": \\\"引言要点\\\",\\n  \\\"sections\\\": [\\\"章节1标题\\\", \\\"章节2标题\\\", \\\"章节3标题\\\"],\\n  \\\"conclusion\\\": \\\"结论要点\\\"\\n}\",\"profileCode\":null,\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":0.7,\"maxTokens\":1000,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"outline\",\"outputMode\":\"JSON\",\"nodeConfig\":{},\"rememberTrace\":false,\"stateType\":null,\"sortNo\":0}],\"edges\":[]}', 3, NULL, NULL, 'BUILDING', NULL, '2026-08-11 19:04:47', '2026-08-11 19:04:53');
INSERT INTO `ai_flow_draft` VALUES (15, 'd_afc4edb5f7a84a09bacb1feaebf494d4', '8967d923-4ed2-49c2-ba94-031407f50772', 2052290101098295297, 'blog_generator_v1', '博客生成流程', '从主题出发，生成大纲、撰写初稿并润色，最终产出结构化博客文章', 'DAG', '{\"flowCode\":\"blog_generator_v1\",\"name\":\"博客生成流程\",\"description\":\"从主题出发，生成大纲、撰写初稿并润色，最终产出结构化博客文章\",\"version\":1,\"defaultProfileCode\":\"DS-V3-001\",\"engineType\":\"DAG\",\"maxTransitions\":100,\"maxAgentDepth\":8,\"webhookUrl\":null,\"nodes\":[{\"nodeCode\":\"start\",\"name\":\"流程入口\",\"nodeType\":\"START\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":null,\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"inputs\":{\"topic\":{\"type\":\"string\",\"description\":\"博客主题\"},\"style\":{\"type\":\"string\",\"description\":\"写作风格，如专业/轻松/教程式\"},\"targetAudience\":{\"type\":\"string\",\"description\":\"目标读者\"}}},\"rememberTrace\":false,\"stateType\":null,\"sortNo\":0},{\"nodeCode\":\"gen_outline\",\"name\":\"生成博客大纲\",\"nodeType\":\"PROMPT\",\"systemPrompt\":\"你是一位资深内容策划。你的职责是根据给定的博客主题、目标读者和写作风格，产出一份结构清晰、逻辑连贯、有吸引力的博客大纲。\",\"promptTemplate\":\"请为以下博客主题生成一份详细的大纲。\\n\\n主题：{{topic}}\\n目标读者：{{targetAudience}}\\n写作风格：{{style}}\\n\\n大纲要求：\\n1. 包含引人入胜的开头切入点\\n2. 主体部分分 3-5 个小节，每节给出要点\\n3. 结尾包含总结和行动号召（CTA）\\n\\n请只输出 JSON，格式如下：\\n{\\\"title\\\": \\\"博客标题\\\", \\\"sections\\\": [{\\\"heading\\\": \\\"小节标题\\\", \\\"points\\\": [\\\"要点1\\\", \\\"要点2\\\"]}], \\\"intro_angle\\\": \\\"开头切入点\\\", \\\"cta\\\": \\\"结尾行动号召\\\"}\",\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":0.7,\"maxTokens\":1200,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"outline\",\"outputMode\":\"JSON\",\"nodeConfig\":{},\"rememberTrace\":false,\"stateType\":null,\"sortNo\":0}],\"edges\":[]}', 3, NULL, NULL, 'BUILDING', NULL, '2026-08-11 19:26:25', '2026-08-11 19:26:32');
INSERT INTO `ai_flow_draft` VALUES (16, 'd_a50cd01915c74f68af31f209e1fc203b', '8967d923-4ed2-49c2-ba94-031407f50772', 2052290101098295297, NULL, '博客生成 Agent', '根据主题先生成博客大纲，再生成每日博客内容', 'DAG', '{\"flowCode\":null,\"name\":\"博客生成 Agent\",\"description\":\"根据主题先生成博客大纲，再生成每日博客内容\",\"version\":1,\"defaultProfileCode\":null,\"engineType\":\"DAG\",\"maxTransitions\":100,\"maxAgentDepth\":8,\"webhookUrl\":null,\"nodes\":[{\"nodeCode\":\"start\",\"name\":\"流程入口\",\"nodeType\":\"START\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":null,\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"inputs\":{\"topic\":{\"type\":\"string\",\"description\":\"博客主题\"},\"audience\":{\"type\":\"string\",\"description\":\"目标读者/受众\"},\"language\":{\"type\":\"string\",\"description\":\"输出语言，默认中文\"}}},\"rememberTrace\":false,\"stateType\":null,\"sortNo\":0},{\"nodeCode\":\"gen_outline\",\"name\":\"生成博客大纲\",\"nodeType\":\"PROMPT\",\"systemPrompt\":\"你是一位资深博客内容策划与编辑，擅长为博客文章搭建清晰、有逻辑、有吸引力的结构大纲。你输出的每一份大纲都层次分明、重点突出，能直接指导后续的正文写作。\",\"promptTemplate\":\"请围绕主题「{{topic}}」，面向目标读者「{{audience}}」，用「{{language}}」生成一份详细的博客文章大纲。\\n\\n大纲需要包含：\\n1. 一个能吸引点击的标题建议\\n2. 引言要点（如何开场抓住读者）\\n3. 3-5 个正文主章节，每个章节给出小节要点与建议示例/论据\\n4. 结尾要点（总结与行动号召）\\n\\n请严格只输出 JSON，字段结构如下：\\n{\\n  \\\"title\\\": \\\"建议的博客标题\\\",\\n  \\\"introduction\\\": \\\"引言写作要点\\\",\\n  \\\"sections\\\": [\\n    {\\\"heading\\\": \\\"章节标题\\\", \\\"points\\\": [\\\"要点1\\\", \\\"要点2\\\", \\\"要点3\\\"]}\\n  ],\\n  \\\"conclusion\\\": \\\"结尾与行动号召要点\\\"\\n}\",\"profileCode\":\"DS-V3-JSON-004\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":0.3,\"maxTokens\":1500,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"outline\",\"outputMode\":\"JSON\",\"nodeConfig\":{},\"rememberTrace\":false,\"stateType\":null,\"sortNo\":0},{\"nodeCode\":\"gen_content\",\"name\":\"生成每日博客内容\",\"nodeType\":\"PROMPT\",\"systemPrompt\":\"你是一位才华横溢的博客作者，擅长把大纲扩展成生动、流畅、有信息量、能引发读者共鸣的完整文章。你的文章通俗易懂、结构清晰、语言生动，符合目标读者的阅读习惯。\",\"promptTemplate\":\"请根据以下博客大纲，用「{{language}}」撰写一篇完整的博客文章正文。\\n\\n博客大纲：\\n{{outline}}\\n\\n写作要求：\\n1. 严格遵循大纲的标题、章节结构与要点，不要遗漏章节\\n2. 每个章节充分展开，补充具体例子、数据或故事，让内容充实丰满\\n3. 语言自然流畅，段落分明，符合目标读者「{{audience}}」的阅读水平\\n4. 开头吸引人，结尾有行动号召\\n5. 全文连贯，各章节之间自然过渡\\n\\n请直接输出文章正文（不含 JSON 包装），标题使用 Markdown 的 # 格式。\",\"profileCode\":\"DS-V3-CREATIVE-003\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":0.8,\"maxTokens\":3000,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"blogContent\",\"outputMode\":\"TEXT\",\"nodeConfig\":{},\"rememberTrace\":false,\"stateType\":null,\"sortNo\":0}],\"edges\":[]}', 3, NULL, NULL, 'BUILDING', NULL, '2026-08-11 19:27:15', '2026-08-11 19:27:25');
INSERT INTO `ai_flow_draft` VALUES (17, 'd_79cc877813dd407fadf9cbdd6514d0e7', 'dad1e279-614b-42ba-a7ea-988998674c5e', 2052290101098295297, 'daily_blog_generator', '每日博客生成流程', '按天循环生成博客：每天先产出结构化大纲，再撰写完整正文，并经过内容检查确认，最终汇总为多篇可发布文章。', 'DAG', '{\"flowCode\":\"daily_blog_generator\",\"name\":\"每日博客生成流程\",\"description\":\"按天循环生成博客：每天先产出结构化大纲，再撰写完整正文，并经过内容检查确认，最终汇总为多篇可发布文章。\",\"version\":1,\"defaultProfileCode\":\"DS-V3-001\",\"engineType\":\"DAG\",\"maxTransitions\":100,\"maxAgentDepth\":8,\"webhookUrl\":null,\"nodes\":[{\"nodeCode\":\"start\",\"name\":\"开始\",\"nodeType\":\"START\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":null,\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"inputs\":{\"topic\":{\"type\":\"string\",\"description\":\"博客主题\"},\"language\":{\"type\":\"string\",\"description\":\"目标语言，默认中文\"},\"audience\":{\"type\":\"string\",\"description\":\"目标受众\"},\"tone\":{\"type\":\"string\",\"description\":\"写作语气，默认专业\"},\"days\":{\"type\":\"integer\",\"description\":\"需要生成博客的天数，每天一篇\"}}},\"rememberTrace\":false,\"stateType\":null,\"sortNo\":0},{\"nodeCode\":\"gen_outline\",\"name\":\"生成博客大纲\",\"nodeType\":\"PROMPT\",\"systemPrompt\":\"你是一位资深内容策划与博客写作专家，擅长为主题搭建结构清晰、逻辑严谨的博客大纲。你输出的内容必须严格遵循指定的 JSON 格式，不输出任何多余文字。\",\"promptTemplate\":\"请为主题「{{topic}}」设计一篇博客文章的完整大纲。\\n\\n要求：\\n- 目标语言：{{language}}\\n- 目标受众：{{audience}}\\n- 写作语气：{{tone}}\\n\\n大纲需包含：引言要点、3-5 个主体章节（每章给出核心论点与要点）、结论要点。\\n\\n请只输出 JSON，格式如下：\\n{\\n  \\\"title\\\": \\\"建议的博客标题\\\",\\n  \\\"introduction_points\\\": [\\\"要点1\\\", \\\"要点2\\\"],\\n  \\\"sections\\\": [\\n    {\\\"heading\\\": \\\"章节标题\\\", \\\"key_points\\\": [\\\"要点1\\\", \\\"要点2\\\"]}\\n  ],\\n  \\\"conclusion_points\\\": [\\\"要点1\\\", \\\"要点2\\\"]\\n}\",\"profileCode\":\"DS-V3-JSON-004\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":0.3,\"maxTokens\":1500,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"outline\",\"outputMode\":\"JSON\",\"nodeConfig\":{},\"rememberTrace\":false,\"stateType\":null,\"sortNo\":0},{\"nodeCode\":\"write_article\",\"name\":\"撰写博客正文\",\"nodeType\":\"PROMPT\",\"systemPrompt\":\"你是一位专业的博客作者，擅长把结构化大纲扩展为流畅、有深度、可读性强的完整文章。请严格按照大纲组织内容，保持语气一致，输出自然连贯的正文。\",\"promptTemplate\":\"请根据以下大纲撰写一篇完整博客正文。\\n\\n【博客主题】{{topic}}\\n【目标语言】{{language}}\\n【目标受众】{{audience}}\\n【写作语气】{{tone}}\\n\\n【大纲内容】\\n{{outline}}\\n\\n要求：\\n1. 标题采用大纲中的建议标题。\\n2. 按大纲的章节顺序展开，每个章节围绕核心论点充分论述。\\n3. 正文需包含引言、主体和结论，整体篇幅完整充实。\\n4. 语气贴合目标受众，语言使用{{language}}。\\n5. 请只输出正文内容本身，不要输出 JSON 或额外说明。\",\"profileCode\":\"DS-V3-CREATIVE-003\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":0.7,\"maxTokens\":4000,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"article\",\"outputMode\":\"TEXT\",\"nodeConfig\":{},\"rememberTrace\":false,\"stateType\":null,\"sortNo\":0},{\"nodeCode\":\"end\",\"name\":\"结束\",\"nodeType\":\"END\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":null,\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"end\":{\"outputJson\":{\"topic\":\"{{topic}}\",\"days\":\"{{days}}\",\"articles\":\"{{daily_loop.outputs}}\"}}},\"rememberTrace\":false,\"stateType\":null,\"sortNo\":0},{\"nodeCode\":\"check_content\",\"name\":\"确认博客内容检查\",\"nodeType\":\"PROMPT\",\"systemPrompt\":\"你是一位严谨的博客内容审核专家，负责对生成的博客正文进行质量检查。你只输出符合指定 JSON 格式的检查结论，不输出任何多余文字。\",\"promptTemplate\":\"请对以下博客正文做一次内容质量检查，并给出确认结论。\\n\\n【博客主题】{{topic}}\\n【目标语言】{{language}}\\n【目标受众】{{audience}}\\n【写作语气】{{tone}}\\n\\n【博客大纲】\\n{{outline}}\\n\\n【博客正文】\\n{{article}}\\n\\n请从以下维度检查并只输出 JSON：\\n1. 完整性：正文是否包含引言、主体章节、结论，是否覆盖大纲所有要点；\\n2. 一致性：内容是否与主题、大纲一致，语气是否贴合受众；\\n3. 质量：是否存在明显语病、逻辑断裂或空泛表述；\\n4. 语言：是否使用目标语言{{language}}。\\n\\n输出格式：\\n{\\n  \\\"passed\\\": true 或 false,\\n  \\\"score\\\": 0-100 的整数评分,\\n  \\\"issues\\\": [\\\"发现的问题，若无则为空数组\\\"],\\n  \\\"suggestions\\\": [\\\"改进建议，若无则为空数组\\\"],\\n  \\\"confirmation\\\": \\\"确认通过\\\" 或 \\\"需要修订\\\"\\n}\",\"profileCode\":\"DS-V3-JSON-004\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":0.2,\"maxTokens\":1000,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"check_result\",\"outputMode\":\"JSON\",\"nodeConfig\":{},\"rememberTrace\":false,\"stateType\":null,\"sortNo\":0},{\"nodeCode\":\"daily_loop\",\"name\":\"按天循环生成\",\"nodeType\":\"LOOP\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":null,\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"members\":[\"gen_outline\",\"write_article\",\"check_content\"],\"loop\":{\"type\":\"COUNT\",\"count\":\"{{days}}\"}},\"rememberTrace\":false,\"stateType\":null,\"sortNo\":0}],\"edges\":[{\"fromNode\":\"gen_outline\",\"toNode\":\"write_article\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":1},{\"fromNode\":\"write_article\",\"toNode\":\"check_content\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":3},{\"fromNode\":\"daily_loop\",\"toNode\":\"end\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":6},{\"fromNode\":\"start\",\"toNode\":\"gen_outline\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":7},{\"fromNode\":\"check_content\",\"toNode\":\"daily_loop\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":8}]}', 32, 32, 32, 'BUILDING', NULL, '2026-08-11 19:41:12', '2026-08-11 19:48:09');
INSERT INTO `ai_flow_draft` VALUES (18, 'd_2adc44ac09cc48c3a53bdc14b4334a06', 'c288771f-30e2-47b8-9479-4d50b674e4f4', 2052290101098295297, 'daily_blog_generation', '每日博客生成流程', '每天生成一篇博客：选题→大纲→正文→润色，按天触发，每次执行产出一篇完整博客', 'DAG', '{\"flowCode\":\"daily_blog_generation\",\"name\":\"每日博客生成流程\",\"description\":\"每天生成一篇博客：选题→大纲→正文→润色，按天触发，每次执行产出一篇完整博客\",\"version\":1,\"defaultProfileCode\":\"DS-V3-001\",\"engineType\":\"DAG\",\"maxTransitions\":100,\"maxAgentDepth\":8,\"webhookUrl\":null,\"nodes\":[{\"nodeCode\":\"start\",\"name\":\"流程入口\",\"nodeType\":\"START\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":null,\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"inputs\":{\"date\":{\"type\":\"string\",\"description\":\"生成日期，如 2025-01-01\"},\"topic\":{\"type\":\"string\",\"description\":\"博客主题方向，可留空由 AI 自动选题\"}}},\"rememberTrace\":false,\"stateType\":null,\"sortNo\":0},{\"nodeCode\":\"topic_selector\",\"name\":\"选题\",\"nodeType\":\"PROMPT\",\"systemPrompt\":\"你是一位资深内容策划，擅长为技术博客挑选有吸引力、有深度且贴合时机的选题。你的输出必须简洁、聚焦，只给出一个明确的选题标题。\",\"promptTemplate\":\"今天是 {{date}}。请为今天的博客挑选一个具体、可写的选题。\\n\\n如果提供了主题方向 {{topic}}，请围绕它细化出一个具体选题标题；如果主题为空，请结合当下技术趋势自行策划一个。\\n\\n要求：\\n- 选题要有明确的读者价值和搜索价值\\n- 标题具体、不含糊，避免空泛\\n- 只输出一个选题标题，不要解释\",\"profileCode\":\"DS-V3-CREATIVE-003\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":0.9,\"maxTokens\":300,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"topicChoice\",\"outputMode\":\"TEXT\",\"nodeConfig\":{},\"rememberTrace\":false,\"stateType\":null,\"sortNo\":0},{\"nodeCode\":\"outline_writer\",\"name\":\"生成大纲\",\"nodeType\":\"PROMPT\",\"systemPrompt\":\"你是一位严谨的技术博客编辑，负责把选题拆解成结构清晰、逻辑递进的写作大纲。你只输出合法 JSON，不输出任何其他文字或解释。\",\"promptTemplate\":\"请为以下选题生成一篇技术博客的详细写作大纲：\\n\\n选题：{{topicChoice}}\\n\\n请输出 JSON，结构如下：\\n{\\n  \\\"title\\\": \\\"文章标题\\\",\\n  \\\"intro\\\": \\\"引言要点（1-2 句）\\\",\\n  \\\"sections\\\": [\\n    {\\\"heading\\\": \\\"小节标题\\\", \\\"points\\\": [\\\"要点1\\\", \\\"要点2\\\"]}\\n  ],\\n  \\\"conclusion\\\": \\\"结论要点\\\"\\n}\\n\\n要求：\\n- sections 至少 3 个小节，每个小节 2-4 个要点\\n- 结构要逻辑递进、覆盖完整\\n- 只输出 JSON 对象，不要有其他内容\",\"profileCode\":\"DS-V3-JSON-004\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":0.3,\"maxTokens\":800,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"outline\",\"outputMode\":\"JSON\",\"nodeConfig\":{},\"rememberTrace\":false,\"stateType\":null,\"sortNo\":0},{\"nodeCode\":\"article_writer\",\"name\":\"撰写正文\",\"nodeType\":\"PROMPT\",\"systemPrompt\":\"你是一位专业的技术博客作者，擅长把大纲展开为有深度、可读性强、结构清晰的完整文章。文章要使用 Markdown 格式，语言自然流畅，避免空话套话。\",\"promptTemplate\":\"请根据以下大纲撰写一篇完整的技术博客正文（Markdown 格式）。\\n\\n大纲：\\n{{outline}}\\n\\n要求：\\n- 严格按照大纲的标题和要点展开，每个小节都要有实质内容\\n- 使用 Markdown 标题（#、##、###）、列表、代码块等格式\\n- 引入具体示例、数据或代码片段增强说服力\\n- 全文 1500-2500 字\\n- 开头有引言，结尾有结论\\n- 只输出文章正文，不要输出额外说明\",\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":0.7,\"maxTokens\":3000,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"draft\",\"outputMode\":\"TEXT\",\"nodeConfig\":{},\"rememberTrace\":false,\"stateType\":null,\"sortNo\":0},{\"nodeCode\":\"polisher\",\"name\":\"润色成稿\",\"nodeType\":\"PROMPT\",\"systemPrompt\":\"你是一位资深文字编辑，负责对技术博文做最后的润色，使其表达更精准、结构更紧凑、语言更生动。保持 Markdown 格式，不改变原意与技术准确性。\",\"promptTemplate\":\"请对下面的博客初稿进行润色，输出最终成稿（Markdown 格式）。\\n\\n初稿：\\n{{draft}}\\n\\n润色要求：\\n- 修正语病、错别字和表达冗余\\n- 优化段落衔接，使逻辑更流畅\\n- 强化开头吸引力与结尾收束\\n- 保持技术内容准确，不删减关键信息\\n- 只输出润色后的完整文章，不要附加说明\",\"profileCode\":\"DS-V3-CREATIVE-003\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":0.7,\"maxTokens\":3500,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"finalArticle\",\"outputMode\":\"TEXT\",\"nodeConfig\":{},\"rememberTrace\":false,\"stateType\":null,\"sortNo\":0},{\"nodeCode\":\"end\",\"name\":\"流程出口\",\"nodeType\":\"END\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":null,\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"end\":{\"outputJson\":{\"date\":\"{{date}}\",\"title\":\"{{topicChoice}}\",\"outline\":\"{{outline}}\",\"article\":\"{{finalArticle}}\"}}},\"rememberTrace\":false,\"stateType\":null,\"sortNo\":0}],\"edges\":[{\"fromNode\":\"start\",\"toNode\":\"topic_selector\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":0},{\"fromNode\":\"topic_selector\",\"toNode\":\"outline_writer\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":1},{\"fromNode\":\"outline_writer\",\"toNode\":\"article_writer\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":2},{\"fromNode\":\"article_writer\",\"toNode\":\"polisher\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":3},{\"fromNode\":\"polisher\",\"toNode\":\"end\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":4}]}', 12, 12, 12, 'BUILDING', NULL, '2026-08-11 19:59:14', '2026-08-11 19:59:46');
INSERT INTO `ai_flow_draft` VALUES (19, 'd_7d9a475743364df8b955c1bf6125a1ce', '31b8aebb-401c-4703-95c5-f4e742d9b663', 2052290101098295297, 'blog_generation', '博客生成流程', '根据用户提供的主题、风格和目标读者，自动生成完整博客文章：先规划提纲，再撰写初稿，最后润色优化。', 'DAG', '{\"flowCode\":\"blog_generation\",\"name\":\"博客生成流程\",\"description\":\"根据用户提供的主题、风格和目标读者，自动生成完整博客文章：先规划提纲，再撰写初稿，最后润色优化。\",\"version\":1,\"defaultProfileCode\":\"DS-V3-001\",\"engineType\":\"DAG\",\"maxTransitions\":100,\"maxAgentDepth\":8,\"webhookUrl\":null,\"nodes\":[{\"nodeCode\":\"start\",\"name\":\"开始\",\"nodeType\":\"START\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":null,\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"inputs\":[{\"name\":\"topic\",\"type\":\"string\",\"required\":true,\"description\":\"博客文章的主题\",\"example\":\"如何高效使用 AI 辅助写作\"},{\"name\":\"style\",\"type\":\"string\",\"required\":false,\"description\":\"写作风格，如专业、轻松、幽默等\",\"example\":\"轻松易懂\"},{\"name\":\"targetAudience\",\"type\":\"string\",\"required\":false,\"description\":\"目标读者群体\",\"example\":\"希望提升写作效率的职场人士\"},{\"name\":\"wordCount\",\"type\":\"integer\",\"required\":false,\"description\":\"期望的文章字数\",\"example\":1500,\"defaultValue\":1500}]},\"rememberTrace\":false,\"stateType\":null,\"sortNo\":0},{\"nodeCode\":\"outline_gen\",\"name\":\"生成文章提纲\",\"nodeType\":\"PROMPT\",\"systemPrompt\":\"你是一位资深的博客内容策划专家，擅长为不同主题和读者规划清晰、有吸引力的文章结构。你输出的提纲逻辑严密、层次分明，能引导读者循序渐进地理解主题。\",\"promptTemplate\":\"请为主题「{{topic}}」规划一篇博客文章的详细提纲。\\n\\n写作风格：{{#style}}{{style}}{{/style}}{{^style}}专业清晰{{/style}}\\n目标读者：{{#targetAudience}}{{targetAudience}}{{/targetAudience}}{{^targetAudience}}普通读者{{/targetAudience}}\\n期望字数：约 {{wordCount}} 字\\n\\n请输出一个 JSON 对象，包含以下字段：\\n{\\n  \\\"title\\\": \\\"建议的文章标题（吸引眼球）\\\",\\n  \\\"sections\\\": [\\n    {\\\"heading\\\": \\\"章节标题\\\", \\\"points\\\": [\\\"要点1\\\", \\\"要点2\\\"]}\\n  ],\\n  \\\"keyTakeaways\\\": [\\\"读者读完应收获的核心要点\\\"]\\n}\\n\\n只输出 JSON，不要有任何额外文字。\",\"profileCode\":\"DS-V3-JSON-004\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":0.7,\"maxTokens\":1000,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"outline\",\"outputMode\":\"JSON\",\"nodeConfig\":{},\"rememberTrace\":false,\"stateType\":null,\"sortNo\":0},{\"nodeCode\":\"draft_write\",\"name\":\"撰写初稿\",\"nodeType\":\"PROMPT\",\"systemPrompt\":\"你是一位才华横溢的博客写手，文笔流畅、表达生动。你能按照提纲把主题展开成一篇完整、连贯、有吸引力的文章，让读者愿意读下去并有所收获。\",\"promptTemplate\":\"请根据以下提纲撰写一篇完整的博客文章初稿。\\n\\n文章主题：{{topic}}\\n写作风格：{{#style}}{{style}}{{/style}}{{^style}}专业清晰{{/style}}\\n目标读者：{{#targetAudience}}{{targetAudience}}{{/targetAudience}}{{^targetAudience}}普通读者{{/targetAudience}}\\n期望字数：约 {{wordCount}} 字\\n\\n提纲内容：\\n{{outline}}\\n\\n请根据提纲展开，写出完整的文章正文（包含标题、引言、各章节内容、结论）。语言要自然流畅，符合指定的写作风格和读者群体。请确保字数接近期望值。\\n\\n直接输出文章正文（Markdown 格式），不要输出 JSON。\",\"profileCode\":\"DS-V3-CREATIVE-003\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":0.8,\"maxTokens\":3000,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"draft\",\"outputMode\":\"TEXT\",\"nodeConfig\":{},\"rememberTrace\":false,\"stateType\":null,\"sortNo\":0},{\"nodeCode\":\"polish\",\"name\":\"润色优化\",\"nodeType\":\"PROMPT\",\"systemPrompt\":\"你是一位精益求精的文字编辑，注重语言的准确、流畅与感染力。你擅长打磨文章，使表达更精炼、结构更清晰、逻辑更严密，同时保持原文的风格与核心内容不变。\",\"promptTemplate\":\"请对下面的博客文章初稿进行润色优化，使其达到最佳质量。\\n\\n原始主题：{{topic}}\\n目标读者：{{targetAudience}}\\n期望风格：{{#style}}{{style}}{{/style}}{{^style}}专业清晰{{/style}}\\n\\n初稿内容：\\n{{draft}}\\n\\n润色要求：\\n1. 修正语法错误、错别字和不通顺的句子\\n2. 优化表达，使语言更精炼、生动\\n3. 增强段落间的衔接与过渡，保证行文流畅\\n4. 保持原文结构和核心内容不变\\n5. 确保风格符合目标读者和期望风格\\n\\n直接输出润色后的完整文章（Markdown 格式），不要输出任何额外说明。\",\"profileCode\":\"DS-V3-001\",\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":0.5,\"maxTokens\":3000,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":\"polished\",\"outputMode\":\"TEXT\",\"nodeConfig\":{},\"rememberTrace\":false,\"stateType\":null,\"sortNo\":0},{\"nodeCode\":\"end\",\"name\":\"结束\",\"nodeType\":\"END\",\"systemPrompt\":null,\"promptTemplate\":null,\"profileCode\":null,\"provider\":null,\"model\":null,\"baseUrl\":null,\"apiKey\":null,\"temperature\":null,\"maxTokens\":null,\"topP\":null,\"timeoutMs\":null,\"stop\":[],\"options\":{},\"inputMapping\":{},\"outputKey\":null,\"outputMode\":\"TEXT\",\"nodeConfig\":{\"end\":{\"outputJson\":{\"topic\":\"{{topic}}\",\"outline\":\"{{outline}}\",\"article\":\"{{polished}}\"}}},\"rememberTrace\":false,\"stateType\":null,\"sortNo\":0}],\"edges\":[{\"fromNode\":\"start\",\"toNode\":\"outline_gen\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":0},{\"fromNode\":\"outline_gen\",\"toNode\":\"draft_write\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":1},{\"fromNode\":\"draft_write\",\"toNode\":\"polish\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":2},{\"fromNode\":\"polish\",\"toNode\":\"end\",\"conditionExpr\":null,\"eventName\":null,\"sortNo\":3}]}', 10, 10, 10, 'BUILDING', NULL, '2026-08-11 20:24:18', '2026-08-11 20:24:48');

-- ----------------------------
-- Table structure for ai_flow_edge
-- ----------------------------
DROP TABLE IF EXISTS `ai_flow_edge`;
CREATE TABLE `ai_flow_edge`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '边ID',
  `flow_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '归属流程编码',
  `from_node` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '起点节点编码',
  `to_node` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '终点节点编码',
  `condition_expr` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '条件表达式(SpEL)，空表示无条件直达',
  `sort_no` int(11) NOT NULL DEFAULT 0 COMMENT '排序号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `event_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '转移触发事件名（状态机用，DAG 忽略）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_flow_code`(`flow_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 85 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI流程边表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_flow_edge
-- ----------------------------
INSERT INTO `ai_flow_edge` VALUES (51, 'blog_series', 'node_1', 'node_2', NULL, 0, '2026-07-21 16:23:17', '2026-07-21 16:23:17', NULL);
INSERT INTO `ai_flow_edge` VALUES (52, 'blog_series', 'node_2', 'node_3', NULL, 1, '2026-07-21 16:23:17', '2026-07-21 16:23:17', NULL);
INSERT INTO `ai_flow_edge` VALUES (53, 'daily_blog_creator', 'start', 'get_topic', NULL, 0, '2026-07-23 20:16:11', '2026-07-23 20:16:11', NULL);
INSERT INTO `ai_flow_edge` VALUES (54, 'daily_blog_creator', 'get_topic', 'write_blog', NULL, 0, '2026-07-23 20:16:11', '2026-07-23 20:16:11', NULL);
INSERT INTO `ai_flow_edge` VALUES (55, 'daily_blog_creator', 'write_blog', 'polish', NULL, 0, '2026-07-23 20:16:11', '2026-07-23 20:16:11', NULL);
INSERT INTO `ai_flow_edge` VALUES (56, 'daily_blog_creator', 'polish', 'end', NULL, 0, '2026-07-23 20:16:11', '2026-07-23 20:16:11', NULL);
INSERT INTO `ai_flow_edge` VALUES (60, 'blog_outline_agent', 'start', 'generate_outline', NULL, 0, '2026-07-23 20:24:25', '2026-07-23 20:24:25', NULL);
INSERT INTO `ai_flow_edge` VALUES (61, 'blog_outline_agent', 'generate_outline', 'generate_content', NULL, 1, '2026-07-23 20:24:25', '2026-07-23 20:24:25', NULL);
INSERT INTO `ai_flow_edge` VALUES (62, 'blog_outline_agent', 'generate_content', 'end', NULL, 2, '2026-07-23 20:24:25', '2026-07-23 20:24:25', NULL);
INSERT INTO `ai_flow_edge` VALUES (76, 'daily_blog_generator', 'gen_outline', 'write_article', NULL, 0, '2026-08-11 19:49:06', '2026-08-11 19:49:06', NULL);
INSERT INTO `ai_flow_edge` VALUES (77, 'daily_blog_generator', 'write_article', 'check_content', NULL, 1, '2026-08-11 19:49:06', '2026-08-11 19:49:06', NULL);
INSERT INTO `ai_flow_edge` VALUES (78, 'daily_blog_generator', 'daily_loop', 'end', NULL, 2, '2026-08-11 19:49:06', '2026-08-11 19:49:06', NULL);
INSERT INTO `ai_flow_edge` VALUES (79, 'daily_blog_generator', 'start', 'gen_outline', NULL, 3, '2026-08-11 19:49:06', '2026-08-11 19:49:06', NULL);
INSERT INTO `ai_flow_edge` VALUES (80, 'daily_blog_generator', 'check_content', 'daily_loop', NULL, 4, '2026-08-11 19:49:06', '2026-08-11 19:49:06', NULL);
INSERT INTO `ai_flow_edge` VALUES (81, 'blog_generation', 'start', 'outline_gen', NULL, 0, '2026-08-11 20:31:33', '2026-08-11 20:31:33', NULL);
INSERT INTO `ai_flow_edge` VALUES (82, 'blog_generation', 'outline_gen', 'draft_write', NULL, 1, '2026-08-11 20:31:33', '2026-08-11 20:31:33', NULL);
INSERT INTO `ai_flow_edge` VALUES (83, 'blog_generation', 'draft_write', 'polish', NULL, 2, '2026-08-11 20:31:33', '2026-08-11 20:31:33', NULL);
INSERT INTO `ai_flow_edge` VALUES (84, 'blog_generation', 'polish', 'end', NULL, 3, '2026-08-11 20:31:33', '2026-08-11 20:31:33', NULL);

-- ----------------------------
-- Table structure for ai_flow_node
-- ----------------------------
DROP TABLE IF EXISTS `ai_flow_node`;
CREATE TABLE `ai_flow_node`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '节点ID',
  `flow_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '归属流程编码',
  `node_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '节点编码，流程内唯一',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '节点名称',
  `node_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PROMPT' COMMENT '节点类型，决定执行器',
  `system_prompt` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '系统提示词模板',
  `prompt_template` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '用户提示词模板',
  `profile_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '引用的模型档案编码',
  `provider` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '服务提供商（覆盖档案）',
  `model` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '模型名称（覆盖档案）',
  `base_url` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'API基础地址（覆盖档案）',
  `api_key` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'API密钥（覆盖档案）',
  `temperature` double NULL DEFAULT NULL COMMENT '采样温度（覆盖档案）',
  `max_tokens` int(11) NULL DEFAULT NULL COMMENT '最大输出token数（覆盖档案）',
  `top_p` double NULL DEFAULT NULL COMMENT 'Top P采样参数（覆盖档案）',
  `timeout_ms` int(11) NULL DEFAULT NULL COMMENT '请求超时毫秒（覆盖档案）',
  `stop` json NULL COMMENT '停止词（JSON数组）',
  `options` json NULL COMMENT '扩展参数（JSON对象）',
  `input_mapping` json NULL COMMENT '输入映射：模板变量名->上下文键（JSON对象）',
  `output_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '产物写回上下文的键',
  `output_mode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'TEXT' COMMENT '产物模式：TEXT/JSON',
  `node_config` json NULL COMMENT '非PROMPT类型的扩展配置（JSON对象）',
  `remember_trace` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否记录该节点轨迹',
  `sort_no` int(11) NOT NULL DEFAULT 0 COMMENT '排序号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `state_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '状态机语义：ENTRY | NORMAL | TERMINAL（DAG 忽略）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_flow_node`(`flow_code` ASC, `node_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 173 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI流程节点表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_flow_node
-- ----------------------------
INSERT INTO `ai_flow_node` VALUES (118, 'blog_series', 'node_13', '', 'IF', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'TEXT', '{\"if\": {\"branches\": [{\"id\": \"br_5\", \"label\": \"分支1\", \"condition\": {\"logic\": \"AND\", \"clauses\": [{\"op\": \"eq\", \"left\": \"\", \"right\": \"\"}]}}, {\"id\": \"br_6\", \"label\": \"否则\", \"isElse\": true, \"condition\": {\"logic\": \"AND\", \"clauses\": [{\"op\": \"eq\", \"left\": \"\", \"right\": \"\"}]}}]}, \"__x6\": {\"x\": 199, \"y\": 25.109375}}', 0, 0, '2026-07-21 16:23:17', '2026-07-21 16:23:17', 'NORMAL');
INSERT INTO `ai_flow_node` VALUES (119, 'blog_series', 'node_4', '', 'AGENT_REACT', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'TEXT', '{\"__x6\": {\"x\": 580, \"y\": 50}}', 0, 1, '2026-07-21 16:23:17', '2026-07-21 16:23:17', 'NORMAL');
INSERT INTO `ai_flow_node` VALUES (120, 'blog_series', 'node_2', '写一篇并更新大纲', 'PROMPT', '你是一个技术博客连载作者。围绕系列主题《{{topic}}》持续创作。\n\n已有整体大纲：{{outline}}\n累积写作状态（已用例子/已讲知识点/风格）：{{accumulated}}\n已写文章索引：{{history}}\n这是第 {{prevSeq}} 篇之后的下一篇。\n\n请：\n1. 基于大纲与累积状态，决定并撰写下一篇文章（承接前文、不重复已讲内容）。\n2. 如有需要，微调整体大纲（边写边规划）。\n3. 更新累积写作状态。\n4. 判断系列是否已写完。\n\n只输出 JSON，无其它文字：', '请生成下一篇。', 'DS-V3-JSON-004', NULL, NULL, 'admin', NULL, 0.8, 4096, 1, NULL, NULL, NULL, NULL, 'seriesState', 'JSON', '{\"llm\": {\"model\": {\"profileCode\": \"DS-V3-JSON-004\"}, \"output\": {\"type\": \"JSON\"}, \"prompt\": {\"systemPrompt\": \"你是一位资深 Java 工程师兼技术博主，擅长用可运行的代码示例讲解原理。行文简洁、避免空话，每个概念都配一段完整可编译的示例代码。\", \"userPromptCode\": \"BLOG_SERIES_OUTLINE\", \"systemPromptCode\": \"TECH_WRITER_PERSONA\", \"userPromptTemplate\": \"请围绕主题「{{topic}}」规划一个 {{count}} 篇的系列文章大纲，写作风格为 {{style}}。\\n每篇给出标题与一句话简介，输出 JSON 数组。\"}, \"context\": {\"memory\": true, \"messages\": true, \"artifacts\": false, \"knowledge\": true, \"variables\": true}, \"parameters\": {\"seed\": null, \"topP\": 1, \"stream\": true, \"maxTokens\": 4096, \"temperature\": 0.7}}, \"__x6\": {\"x\": 390, \"y\": 230}}', 1, 2, '2026-07-21 16:23:17', '2026-07-21 16:23:17', 'NORMAL');
INSERT INTO `ai_flow_node` VALUES (121, 'blog_series', 'node_3', '结束', 'END', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'TEXT', '{\"__x6\": {\"x\": 740, \"y\": 260}}', 0, 3, '2026-07-21 16:23:17', '2026-07-21 16:23:17', 'TERMINAL');
INSERT INTO `ai_flow_node` VALUES (122, 'blog_series', 'node_1', '开始', 'START', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'TEXT', '{\"__x6\": {\"x\": 40, \"y\": 260}, \"inputs\": {\"topic\": \"系列主题\", \"history\": \"已写文章索引\", \"outline\": \"上一轮大纲\", \"prevSeq\": \"上一篇序号\", \"accumulated\": \"累积状态\"}}', 0, 4, '2026-07-21 16:23:17', '2026-07-21 16:23:17', 'ENTRY');
INSERT INTO `ai_flow_node` VALUES (123, 'blog_series', 'node_5', '博客系列生成', 'AGENT', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'TEXT', '{\"__x6\": {\"x\": 350, \"y\": 390}, \"inputMapping\": {}, \"refAgentCode\": \"blog_series\", \"outputMapping\": {}, \"__agentRefName\": \"博客系列生成\"}', 0, 5, '2026-07-21 16:23:17', '2026-07-21 16:23:17', 'NORMAL');
INSERT INTO `ai_flow_node` VALUES (124, 'blog_series', 'node_12', '', 'AGENT_REACT', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'TEXT', '{\"__x6\": {\"x\": 40, \"y\": 424.109375}}', 0, 6, '2026-07-21 16:23:17', '2026-07-21 16:23:17', 'NORMAL');
INSERT INTO `ai_flow_node` VALUES (125, 'blog_series', 'node_10', '', 'IF', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'TEXT', '{\"if\": {\"branches\": [{\"id\": \"br_1\", \"label\": \"分支1\", \"condition\": {\"logic\": \"AND\", \"clauses\": [{\"op\": \"eq\", \"left\": \"\", \"right\": \"\"}]}}, {\"id\": \"br_2\", \"label\": \"否则\", \"isElse\": true, \"condition\": {\"logic\": \"AND\", \"clauses\": [{\"op\": \"eq\", \"left\": \"\", \"right\": \"\"}]}}]}, \"__x6\": {\"x\": 640, \"y\": 520.109375}}', 0, 7, '2026-07-21 16:23:17', '2026-07-21 16:23:17', 'NORMAL');
INSERT INTO `ai_flow_node` VALUES (126, 'blog_series', 'node_9', '', 'JOIN', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'TEXT', '{\"__x6\": {\"x\": 199, \"y\": 530.109375}}', 0, 8, '2026-07-21 16:23:17', '2026-07-21 16:23:17', 'NORMAL');
INSERT INTO `ai_flow_node` VALUES (127, 'blog_series', 'node_11', '', 'TOOL', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'TEXT', '{\"__x6\": {\"x\": 358, \"y\": 702.109375}}', 0, 9, '2026-07-21 16:23:17', '2026-07-21 16:23:17', 'NORMAL');
INSERT INTO `ai_flow_node` VALUES (128, 'daily_blog_creator', 'start', '开始', 'START', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'TEXT', '{\"__x6\": {\"x\": 120.0, \"y\": 200.0}}', 0, 0, '2026-07-23 20:16:11', '2026-07-23 20:16:11', NULL);
INSERT INTO `ai_flow_node` VALUES (129, 'daily_blog_creator', 'get_topic', '获取主题灵感', 'PROMPT', NULL, '今天是 #{date}。请根据当前热门趋势和时事，构思一个有趣、有深度且适合博客发布的主题。\n要求：主题要新颖，有吸引力，适合大众阅读。\n请直接输出一个博客主题标题（20字以内）。', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'topic', 'TEXT', '{\"__x6\": {\"x\": 360.0, \"y\": 200.0}}', 0, 0, '2026-07-23 20:16:11', '2026-07-23 20:16:11', NULL);
INSERT INTO `ai_flow_node` VALUES (130, 'daily_blog_creator', 'write_blog', '撰写博客正文', 'PROMPT', NULL, '请根据以下博客主题，撰写一篇完整的博客文章。\n\n主题：#{topic}\n\n要求：\n1. 文章结构完整，包含引言、正文（2-3个小节）、结语\n2. 语言生动有趣，通俗易懂\n3. 字数在800-1200字之间\n4. 适当使用小标题、列表等格式\n5. 文章风格积极向上，有深度思考\n\n请直接输出完整的博客文章。', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'draft', 'TEXT', '{\"__x6\": {\"x\": 600.0, \"y\": 200.0}}', 0, 0, '2026-07-23 20:16:11', '2026-07-23 20:16:11', NULL);
INSERT INTO `ai_flow_node` VALUES (131, 'daily_blog_creator', 'polish', '润色优化', 'PROMPT', NULL, '请对以下博客文章进行润色优化，使其更具可读性和吸引力。\n\n原文：\n#{draft}\n\n润色要求：\n1. 修正语法和表达问题\n2. 优化段落衔接，使行文更流畅\n3. 增强标题的吸引力\n4. 保持原文的核心观点和风格\n5. 添加一个简短的摘要（50字以内）放在文章最前面\n\n请直接输出润色后的完整博客文章，摘要用【摘要】标记。', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'final_blog', 'TEXT', '{\"__x6\": {\"x\": 840.0, \"y\": 200.0}}', 0, 0, '2026-07-23 20:16:11', '2026-07-23 20:16:11', NULL);
INSERT INTO `ai_flow_node` VALUES (132, 'daily_blog_creator', 'end', '结束', 'END', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'TEXT', '{\"__x6\": {\"x\": 1080.0, \"y\": 200.0}}', 0, 0, '2026-07-23 20:16:11', '2026-07-23 20:16:11', NULL);
INSERT INTO `ai_flow_node` VALUES (137, 'blog_outline_agent', 'start', '开始', 'START', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'TEXT', '{\"__x6\": {\"x\": 120, \"y\": 200}}', 0, 0, '2026-07-23 20:24:25', '2026-07-23 20:24:25', 'ENTRY');
INSERT INTO `ai_flow_node` VALUES (138, 'blog_outline_agent', 'generate_outline', '生成目录', 'PROMPT', '你是一位专业的博客写手，擅长为各类主题规划清晰、有吸引力的文章结构。', '请根据以下博客主题，生成一篇结构清晰的目录大纲。\n\n主题：#{topic}\n\n要求：\n1. 目录层级不超过3级\n2. 包含引言、正文（2-4个章节）、结语\n3. 每个章节给出核心要点\n4. 用 Markdown 格式输出\n\n目录大纲：', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'outline', 'TEXT', '{\"__x6\": {\"x\": 360, \"y\": 200}}', 0, 1, '2026-07-23 20:24:25', '2026-07-23 20:24:25', 'NORMAL');
INSERT INTO `ai_flow_node` VALUES (139, 'blog_outline_agent', 'generate_content', '生成正文', 'PROMPT', '你是一位资深博客作者，文笔生动流畅，逻辑清晰，擅长将目录大纲扩展为完整的博客文章。', '请根据以下目录大纲，展开撰写一篇完整的博客文章。\n\n主题：#{topic}\n\n目录大纲：\n#{outline}\n\n要求：\n1. 严格按照目录结构展开，每个章节内容充实\n2. 语言生动自然，贴近读者\n3. 全文不少于800字\n4. 用 Markdown 格式输出，包含标题层级（#, ##, ###）\n\n完整博客文章：', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'blogContent', 'TEXT', '{\"__x6\": {\"x\": 600, \"y\": 200}}', 0, 2, '2026-07-23 20:24:25', '2026-07-23 20:24:25', 'NORMAL');
INSERT INTO `ai_flow_node` VALUES (140, 'blog_outline_agent', 'end', '结束', 'END', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'TEXT', '{\"__x6\": {\"x\": 840, \"y\": 200}}', 0, 3, '2026-07-23 20:24:25', '2026-07-23 20:24:25', 'TERMINAL');
INSERT INTO `ai_flow_node` VALUES (163, 'daily_blog_generator', 'start', '开始', 'START', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'TEXT', '{\"__x6\": {\"x\": 120, \"y\": 80}, \"inputs\": {\"days\": {\"type\": \"integer\", \"description\": \"需要生成博客的天数，每天一篇\"}, \"tone\": {\"type\": \"string\", \"description\": \"写作语气，默认专业\"}, \"topic\": {\"type\": \"string\", \"description\": \"博客主题\"}, \"audience\": {\"type\": \"string\", \"description\": \"目标受众\"}, \"language\": {\"type\": \"string\", \"description\": \"目标语言，默认中文\"}}}', 0, 0, '2026-08-11 19:49:06', '2026-08-11 19:49:06', 'ENTRY');
INSERT INTO `ai_flow_node` VALUES (164, 'daily_blog_generator', 'gen_outline', '生成博客大纲', 'PROMPT', '你是一位资深内容策划与博客写作专家，擅长为主题搭建结构清晰、逻辑严谨的博客大纲。你输出的内容必须严格遵循指定的 JSON 格式，不输出任何多余文字。', '请为主题「{{topic}}」设计一篇博客文章的完整大纲。\n\n要求：\n- 目标语言：{{language}}\n- 目标受众：{{audience}}\n- 写作语气：{{tone}}\n\n大纲需包含：引言要点、3-5 个主体章节（每章给出核心论点与要点）、结论要点。\n\n请只输出 JSON，格式如下：\n{\n  \"title\": \"建议的博客标题\",\n  \"introduction_points\": [\"要点1\", \"要点2\"],\n  \"sections\": [\n    {\"heading\": \"章节标题\", \"key_points\": [\"要点1\", \"要点2\"]}\n  ],\n  \"conclusion_points\": [\"要点1\", \"要点2\"]\n}', 'DS-V3-JSON-004', NULL, NULL, NULL, NULL, 0.3, 1500, 1, NULL, NULL, NULL, NULL, 'outline', 'JSON', '{\"llm\": {\"model\": {\"profileCode\": \"DS-V3-JSON-004\"}, \"output\": {\"type\": \"JSON\"}, \"prompt\": {\"systemPrompt\": \"你是一位资深内容策划与博客写作专家，擅长为主题搭建结构清晰、逻辑严谨的博客大纲。你输出的内容必须严格遵循指定的 JSON 格式，不输出任何多余文字。\", \"userPromptCode\": \"\", \"systemPromptCode\": \"\", \"userPromptTemplate\": \"请为主题「{{topic}}」设计一篇博客文章的完整大纲。\\n\\n要求：\\n- 目标语言：{{language}}\\n- 目标受众：{{audience}}\\n- 写作语气：{{tone}}\\n\\n大纲需包含：引言要点、3-5 个主体章节（每章给出核心论点与要点）、结论要点。\\n\\n请只输出 JSON，格式如下：\\n{\\n  \\\"title\\\": \\\"建议的博客标题\\\",\\n  \\\"introduction_points\\\": [\\\"要点1\\\", \\\"要点2\\\"],\\n  \\\"sections\\\": [\\n    {\\\"heading\\\": \\\"章节标题\\\", \\\"key_points\\\": [\\\"要点1\\\", \\\"要点2\\\"]}\\n  ],\\n  \\\"conclusion_points\\\": [\\\"要点1\\\", \\\"要点2\\\"]\\n}\"}, \"context\": {\"memory\": true, \"messages\": true, \"artifacts\": false, \"knowledge\": true, \"variables\": true}, \"parameters\": {\"seed\": null, \"topP\": 1, \"stream\": true, \"maxTokens\": 1500, \"temperature\": 0.3}}, \"__x6\": {\"x\": 360, \"y\": 80}}', 0, 1, '2026-08-11 19:49:06', '2026-08-11 19:49:06', 'NORMAL');
INSERT INTO `ai_flow_node` VALUES (165, 'daily_blog_generator', 'write_article', '撰写博客正文', 'PROMPT', '你是一位专业的博客作者，擅长把结构化大纲扩展为流畅、有深度、可读性强的完整文章。请严格按照大纲组织内容，保持语气一致，输出自然连贯的正文。', '请根据以下大纲撰写一篇完整博客正文。\n\n【博客主题】{{topic}}\n【目标语言】{{language}}\n【目标受众】{{audience}}\n【写作语气】{{tone}}\n\n【大纲内容】\n{{outline}}\n\n要求：\n1. 标题采用大纲中的建议标题。\n2. 按大纲的章节顺序展开，每个章节围绕核心论点充分论述。\n3. 正文需包含引言、主体和结论，整体篇幅完整充实。\n4. 语气贴合目标受众，语言使用{{language}}。\n5. 请只输出正文内容本身，不要输出 JSON 或额外说明。', 'DS-V3-CREATIVE-003', NULL, NULL, NULL, NULL, 0.7, 4000, 1, NULL, NULL, NULL, NULL, 'article', 'TEXT', '{\"llm\": {\"model\": {\"profileCode\": \"DS-V3-CREATIVE-003\"}, \"output\": {\"type\": \"TEXT\"}, \"prompt\": {\"systemPrompt\": \"你是一位专业的博客作者，擅长把结构化大纲扩展为流畅、有深度、可读性强的完整文章。请严格按照大纲组织内容，保持语气一致，输出自然连贯的正文。\", \"userPromptCode\": \"\", \"systemPromptCode\": \"\", \"userPromptTemplate\": \"请根据以下大纲撰写一篇完整博客正文。\\n\\n【博客主题】{{topic}}\\n【目标语言】{{language}}\\n【目标受众】{{audience}}\\n【写作语气】{{tone}}\\n\\n【大纲内容】\\n{{outline}}\\n\\n要求：\\n1. 标题采用大纲中的建议标题。\\n2. 按大纲的章节顺序展开，每个章节围绕核心论点充分论述。\\n3. 正文需包含引言、主体和结论，整体篇幅完整充实。\\n4. 语气贴合目标受众，语言使用{{language}}。\\n5. 请只输出正文内容本身，不要输出 JSON 或额外说明。\"}, \"context\": {\"memory\": true, \"messages\": true, \"artifacts\": false, \"knowledge\": true, \"variables\": true}, \"parameters\": {\"seed\": null, \"topP\": 1, \"stream\": true, \"maxTokens\": 4000, \"temperature\": 0.7}}, \"__x6\": {\"x\": 600, \"y\": 80}}', 0, 2, '2026-08-11 19:49:06', '2026-08-11 19:49:06', 'NORMAL');
INSERT INTO `ai_flow_node` VALUES (166, 'daily_blog_generator', 'end', '结束', 'END', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'TEXT', '{\"end\": {\"outputJson\": {\"days\": \"{{days}}\", \"topic\": \"{{topic}}\", \"articles\": \"{{daily_loop.outputs}}\"}}, \"__x6\": {\"x\": 120, \"y\": 220}}', 0, 3, '2026-08-11 19:49:06', '2026-08-11 19:49:06', 'TERMINAL');
INSERT INTO `ai_flow_node` VALUES (167, 'daily_blog_generator', 'check_content', '确认博客内容检查', 'PROMPT', '你是一位严谨的博客内容审核专家，负责对生成的博客正文进行质量检查。你只输出符合指定 JSON 格式的检查结论，不输出任何多余文字。', '请对以下博客正文做一次内容质量检查，并给出确认结论。\n\n【博客主题】{{topic}}\n【目标语言】{{language}}\n【目标受众】{{audience}}\n【写作语气】{{tone}}\n\n【博客大纲】\n{{outline}}\n\n【博客正文】\n{{article}}\n\n请从以下维度检查并只输出 JSON：\n1. 完整性：正文是否包含引言、主体章节、结论，是否覆盖大纲所有要点；\n2. 一致性：内容是否与主题、大纲一致，语气是否贴合受众；\n3. 质量：是否存在明显语病、逻辑断裂或空泛表述；\n4. 语言：是否使用目标语言{{language}}。\n\n输出格式：\n{\n  \"passed\": true 或 false,\n  \"score\": 0-100 的整数评分,\n  \"issues\": [\"发现的问题，若无则为空数组\"],\n  \"suggestions\": [\"改进建议，若无则为空数组\"],\n  \"confirmation\": \"确认通过\" 或 \"需要修订\"\n}', 'DS-V3-JSON-004', NULL, NULL, NULL, NULL, 0.2, 1000, 1, NULL, NULL, NULL, NULL, 'check_result', 'JSON', '{\"llm\": {\"model\": {\"profileCode\": \"DS-V3-JSON-004\"}, \"output\": {\"type\": \"JSON\"}, \"prompt\": {\"systemPrompt\": \"你是一位严谨的博客内容审核专家，负责对生成的博客正文进行质量检查。你只输出符合指定 JSON 格式的检查结论，不输出任何多余文字。\", \"userPromptCode\": \"\", \"systemPromptCode\": \"\", \"userPromptTemplate\": \"请对以下博客正文做一次内容质量检查，并给出确认结论。\\n\\n【博客主题】{{topic}}\\n【目标语言】{{language}}\\n【目标受众】{{audience}}\\n【写作语气】{{tone}}\\n\\n【博客大纲】\\n{{outline}}\\n\\n【博客正文】\\n{{article}}\\n\\n请从以下维度检查并只输出 JSON：\\n1. 完整性：正文是否包含引言、主体章节、结论，是否覆盖大纲所有要点；\\n2. 一致性：内容是否与主题、大纲一致，语气是否贴合受众；\\n3. 质量：是否存在明显语病、逻辑断裂或空泛表述；\\n4. 语言：是否使用目标语言{{language}}。\\n\\n输出格式：\\n{\\n  \\\"passed\\\": true 或 false,\\n  \\\"score\\\": 0-100 的整数评分,\\n  \\\"issues\\\": [\\\"发现的问题，若无则为空数组\\\"],\\n  \\\"suggestions\\\": [\\\"改进建议，若无则为空数组\\\"],\\n  \\\"confirmation\\\": \\\"确认通过\\\" 或 \\\"需要修订\\\"\\n}\"}, \"context\": {\"memory\": true, \"messages\": true, \"artifacts\": false, \"knowledge\": true, \"variables\": true}, \"parameters\": {\"seed\": null, \"topP\": 1, \"stream\": true, \"maxTokens\": 1000, \"temperature\": 0.2}}, \"__x6\": {\"x\": 360, \"y\": 220}}', 0, 4, '2026-08-11 19:49:06', '2026-08-11 19:49:06', 'NORMAL');
INSERT INTO `ai_flow_node` VALUES (168, 'blog_generation', 'start', '开始', 'START', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'TEXT', '{\"__x6\": {\"x\": 120, \"y\": 80}, \"inputs\": [{\"name\": \"topic\", \"type\": \"string\", \"example\": \"如何高效使用 AI 辅助写作\", \"required\": true, \"description\": \"博客文章的主题\"}, {\"name\": \"style\", \"type\": \"string\", \"example\": \"轻松易懂\", \"required\": false, \"description\": \"写作风格，如专业、轻松、幽默等\"}, {\"name\": \"targetAudience\", \"type\": \"string\", \"example\": \"希望提升写作效率的职场人士\", \"required\": false, \"description\": \"目标读者群体\"}, {\"name\": \"wordCount\", \"type\": \"integer\", \"example\": 1500, \"required\": false, \"description\": \"期望的文章字数\", \"defaultValue\": 1500}]}', 0, 0, '2026-08-11 20:31:33', '2026-08-11 20:31:33', 'ENTRY');
INSERT INTO `ai_flow_node` VALUES (169, 'blog_generation', 'outline_gen', '生成文章提纲', 'PROMPT', '你是一位资深的博客内容策划专家，擅长为不同主题和读者规划清晰、有吸引力的文章结构。你输出的提纲逻辑严密、层次分明，能引导读者循序渐进地理解主题。', '请为主题「{{topic}}」规划一篇博客文章的详细提纲。\n\n写作风格：{{#style}}{{style}}{{/style}}{{^style}}专业清晰{{/style}}\n目标读者：{{#targetAudience}}{{targetAudience}}{{/targetAudience}}{{^targetAudience}}普通读者{{/targetAudience}}\n期望字数：约 {{wordCount}} 字\n\n请输出一个 JSON 对象，包含以下字段：\n{\n  \"title\": \"建议的文章标题（吸引眼球）\",\n  \"sections\": [\n    {\"heading\": \"章节标题\", \"points\": [\"要点1\", \"要点2\"]}\n  ],\n  \"keyTakeaways\": [\"读者读完应收获的核心要点\"]\n}\n\n只输出 JSON，不要有任何额外文字。', 'DS-V3-JSON-004', NULL, NULL, NULL, NULL, 0.7, 1000, 1, NULL, NULL, NULL, NULL, 'outline', 'JSON', '{\"llm\": {\"model\": {\"profileCode\": \"DS-V3-JSON-004\"}, \"output\": {\"type\": \"JSON\"}, \"prompt\": {\"systemPrompt\": \"你是一位资深的博客内容策划专家，擅长为不同主题和读者规划清晰、有吸引力的文章结构。你输出的提纲逻辑严密、层次分明，能引导读者循序渐进地理解主题。\", \"userPromptCode\": \"\", \"systemPromptCode\": \"\", \"userPromptTemplate\": \"请为主题「{{topic}}」规划一篇博客文章的详细提纲。\\n\\n写作风格：{{#style}}{{style}}{{/style}}{{^style}}专业清晰{{/style}}\\n目标读者：{{#targetAudience}}{{targetAudience}}{{/targetAudience}}{{^targetAudience}}普通读者{{/targetAudience}}\\n期望字数：约 {{wordCount}} 字\\n\\n请输出一个 JSON 对象，包含以下字段：\\n{\\n  \\\"title\\\": \\\"建议的文章标题（吸引眼球）\\\",\\n  \\\"sections\\\": [\\n    {\\\"heading\\\": \\\"章节标题\\\", \\\"points\\\": [\\\"要点1\\\", \\\"要点2\\\"]}\\n  ],\\n  \\\"keyTakeaways\\\": [\\\"读者读完应收获的核心要点\\\"]\\n}\\n\\n只输出 JSON，不要有任何额外文字。\"}, \"context\": {\"memory\": true, \"messages\": true, \"artifacts\": false, \"knowledge\": true, \"variables\": true}, \"parameters\": {\"seed\": null, \"topP\": 1, \"stream\": true, \"maxTokens\": 1000, \"temperature\": 0.7}}, \"__x6\": {\"x\": 500, \"y\": 80}}', 0, 1, '2026-08-11 20:31:33', '2026-08-11 20:31:33', 'NORMAL');
INSERT INTO `ai_flow_node` VALUES (170, 'blog_generation', 'draft_write', '撰写初稿', 'PROMPT', '你是一位才华横溢的博客写手，文笔流畅、表达生动。你能按照提纲把主题展开成一篇完整、连贯、有吸引力的文章，让读者愿意读下去并有所收获。', '请根据以下提纲撰写一篇完整的博客文章初稿。\n\n文章主题：{{topic}}\n写作风格：{{#style}}{{style}}{{/style}}{{^style}}专业清晰{{/style}}\n目标读者：{{#targetAudience}}{{targetAudience}}{{/targetAudience}}{{^targetAudience}}普通读者{{/targetAudience}}\n期望字数：约 {{wordCount}} 字\n\n提纲内容：\n{{outline}}\n\n请根据提纲展开，写出完整的文章正文（包含标题、引言、各章节内容、结论）。语言要自然流畅，符合指定的写作风格和读者群体。请确保字数接近期望值。\n\n直接输出文章正文（Markdown 格式），不要输出 JSON。', 'DS-V3-CREATIVE-003', NULL, NULL, NULL, NULL, 0.8, 3000, 1, NULL, NULL, NULL, NULL, 'draft', 'TEXT', '{\"llm\": {\"model\": {\"profileCode\": \"DS-V3-CREATIVE-003\"}, \"output\": {\"type\": \"TEXT\"}, \"prompt\": {\"systemPrompt\": \"你是一位才华横溢的博客写手，文笔流畅、表达生动。你能按照提纲把主题展开成一篇完整、连贯、有吸引力的文章，让读者愿意读下去并有所收获。\", \"userPromptCode\": \"\", \"systemPromptCode\": \"\", \"userPromptTemplate\": \"请根据以下提纲撰写一篇完整的博客文章初稿。\\n\\n文章主题：{{topic}}\\n写作风格：{{#style}}{{style}}{{/style}}{{^style}}专业清晰{{/style}}\\n目标读者：{{#targetAudience}}{{targetAudience}}{{/targetAudience}}{{^targetAudience}}普通读者{{/targetAudience}}\\n期望字数：约 {{wordCount}} 字\\n\\n提纲内容：\\n{{outline}}\\n\\n请根据提纲展开，写出完整的文章正文（包含标题、引言、各章节内容、结论）。语言要自然流畅，符合指定的写作风格和读者群体。请确保字数接近期望值。\\n\\n直接输出文章正文（Markdown 格式），不要输出 JSON。\"}, \"context\": {\"memory\": true, \"messages\": true, \"artifacts\": false, \"knowledge\": true, \"variables\": true}, \"parameters\": {\"seed\": null, \"topP\": 1, \"stream\": true, \"maxTokens\": 3000, \"temperature\": 0.8}}, \"__x6\": {\"x\": 880, \"y\": 80}}', 0, 2, '2026-08-11 20:31:33', '2026-08-11 20:31:33', 'NORMAL');
INSERT INTO `ai_flow_node` VALUES (171, 'blog_generation', 'polish', '润色优化', 'PROMPT', '你是一位精益求精的文字编辑，注重语言的准确、流畅与感染力。你擅长打磨文章，使表达更精炼、结构更清晰、逻辑更严密，同时保持原文的风格与核心内容不变。', '请对下面的博客文章初稿进行润色优化，使其达到最佳质量。\n\n原始主题：{{topic}}\n目标读者：{{targetAudience}}\n期望风格：{{#style}}{{style}}{{/style}}{{^style}}专业清晰{{/style}}\n\n初稿内容：\n{{draft}}\n\n润色要求：\n1. 修正语法错误、错别字和不通顺的句子\n2. 优化表达，使语言更精炼、生动\n3. 增强段落间的衔接与过渡，保证行文流畅\n4. 保持原文结构和核心内容不变\n5. 确保风格符合目标读者和期望风格\n\n直接输出润色后的完整文章（Markdown 格式），不要输出任何额外说明。', 'DS-V3-001', NULL, NULL, NULL, NULL, 0.5, 3000, 1, NULL, NULL, NULL, NULL, 'polished', 'TEXT', '{\"llm\": {\"model\": {\"profileCode\": \"DS-V3-001\"}, \"output\": {\"type\": \"TEXT\"}, \"prompt\": {\"systemPrompt\": \"你是一位精益求精的文字编辑，注重语言的准确、流畅与感染力。你擅长打磨文章，使表达更精炼、结构更清晰、逻辑更严密，同时保持原文的风格与核心内容不变。\", \"userPromptCode\": \"\", \"systemPromptCode\": \"\", \"userPromptTemplate\": \"请对下面的博客文章初稿进行润色优化，使其达到最佳质量。\\n\\n原始主题：{{topic}}\\n目标读者：{{targetAudience}}\\n期望风格：{{#style}}{{style}}{{/style}}{{^style}}专业清晰{{/style}}\\n\\n初稿内容：\\n{{draft}}\\n\\n润色要求：\\n1. 修正语法错误、错别字和不通顺的句子\\n2. 优化表达，使语言更精炼、生动\\n3. 增强段落间的衔接与过渡，保证行文流畅\\n4. 保持原文结构和核心内容不变\\n5. 确保风格符合目标读者和期望风格\\n\\n直接输出润色后的完整文章（Markdown 格式），不要输出任何额外说明。\"}, \"context\": {\"memory\": true, \"messages\": true, \"artifacts\": false, \"knowledge\": true, \"variables\": true}, \"parameters\": {\"seed\": null, \"topP\": 1, \"stream\": true, \"maxTokens\": 3000, \"temperature\": 0.5}}, \"__x6\": {\"x\": 120, \"y\": 272}}', 0, 3, '2026-08-11 20:31:33', '2026-08-11 20:31:33', 'NORMAL');
INSERT INTO `ai_flow_node` VALUES (172, 'blog_generation', 'end', '结束', 'END', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'TEXT', '{\"end\": {\"outputJson\": {\"topic\": \"{{topic}}\", \"article\": \"{{polished}}\", \"outline\": \"{{outline}}\"}}, \"__x6\": {\"x\": 500, \"y\": 272}}', 0, 4, '2026-08-11 20:31:33', '2026-08-11 20:31:33', 'TERMINAL');

-- ----------------------------
-- Table structure for ai_flow_run
-- ----------------------------
DROP TABLE IF EXISTS `ai_flow_run`;
CREATE TABLE `ai_flow_run`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `run_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '执行实例唯一标识，业务键，续跑入口',
  `flow_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '流程编码',
  `version` int(11) NOT NULL DEFAULT 1 COMMENT '流程版本',
  `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '归属用户ID',
  `conversation_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '关联会话ID',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'RUNNING' COMMENT '状态：RUNNING/SUCCESS/FAILED',
  `input` json NULL COMMENT '初始输入快照（JSON对象）',
  `attributes` json NULL COMMENT '全量产物快照（JSON对象），每节点完成后刷新；续跑时灌回上下文',
  `executed_nodes` json NULL COMMENT '已执行完成的节点编码集合（JSON数组）',
  `failed_node` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '失败节点编码',
  `error_msg` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '失败原因',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_run_id`(`run_id` ASC) USING BTREE,
  INDEX `idx_flow_code`(`flow_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 8 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI流程编排执行实例表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_flow_run
-- ----------------------------
INSERT INTO `ai_flow_run` VALUES (7, 'daily_blog_generator-9b8c3420cfb240a19babe99083fd66b3', 'daily_blog_generator', 1, '2052290101098295297', NULL, 'SUCCESS', '{\"tone\": {\"type\": \"string\", \"description\": \"写作语气，默认专业\"}, \"topic\": {\"type\": \"string\", \"description\": \"博客主题\"}, \"audience\": {\"type\": \"string\", \"description\": \"目标受众\"}, \"language\": {\"type\": \"string\", \"description\": \"目标语言，默认中文\"}}', '{\"tone\": {\"type\": \"string\", \"description\": \"写作语气，默认专业\"}, \"title\": \"如何通过内容营销提升品牌影响力\", \"topic\": {\"type\": \"string\", \"description\": \"博客主题\"}, \"__runId\": \"daily_blog_generator-9b8c3420cfb240a19babe99083fd66b3\", \"article\": \"# 如何通过内容营销提升品牌影响力\\n\\n在信息过载的数字时代，消费者的注意力已成为最稀缺的资源。传统的硬性广告正逐渐失效，取而代之的是以价值为核心的内容营销。内容营销并非简单的“写文章”或“发视频”，而是一种以用户为中心、以价值为导向的长期战略。它通过持续输出有用、有趣、有深度的内容，与目标受众建立信任，进而潜移默化地提升品牌影响力。本文将为您提供一套系统化的内容营销策略，从理解受众到效果评估，帮助您的品牌在激烈的市场竞争中脱颖而出。\\n\\n## 理解目标受众\\n\\n任何成功的内容营销都始于对受众的深刻理解。如果不知道为谁创作，再精美的内容也如同对空谷喊话，难以产生回响。\\n\\n**创建详细的买家画像**是第一步。买家画像是基于数据与调研构建的半虚构人物形象，代表您的理想客户。除了基本的年龄、职业、收入等人口统计学特征外，更需关注他们的兴趣偏好、信息获取习惯以及价值观。例如，一个面向Z世代的美妆品牌，其买家画像可能是一位关注环保、热衷社交媒体、偏好短视频内容的大学生。\\n\\n**分析受众需求与痛点**是内容价值的核心来源。您的受众在生活或工作中遇到了什么难题？他们在寻求什么样的解决方案？通过深度访谈、客服反馈分析、社交媒体评论挖掘等方式，您可以精准捕捉这些信息。当您的内容能直接回应这些痛点时，它就不再是“推销”，而是“帮助”。\\n\\n**利用数据分析工具精准定位**能让您的努力事半功倍。Google Analytics、百度指数、社交媒体后台数据等工具，可以揭示受众的地理分布、活跃时段、兴趣关键词等。这些客观数据能修正您的主观猜测，确保您的策略始终走在正确的轨道上。\\n\\n## 制定内容策略\\n\\n理解了“对谁说”之后，下一步是解决“说什么”和“怎么说”的问题。没有策略的内容生产，就像没有航向的船只，随波逐流却难以抵达目的地。\\n\\n**设定清晰的营销目标**是策略的起点。您的内容营销是为了提升品牌知名度、增加网站流量、生成销售线索，还是促进用户转化？不同阶段的目标决定了内容的侧重点。例如，品牌初创期侧重于曝光，而成熟期则更关注用户留存与复购。\\n\\n**选择合适的内容类型与渠道**需因地制宜。图文、长视频、短视频、播客、信息图……每种形式都有其独特的传播逻辑。同样，抖音、小红书、LinkedIn、知乎等平台也拥有截然不同的用户生态。您需要根据目标受众的偏好和营销目标，决定将资源集中在哪些内容形式和渠道上。与其在五个平台上浅尝辄止，不如在一个平台上深耕细作。\\n\\n**规划内容日历与发布频率**是保持内容节奏的保障。内容日历不仅帮助您提前规划选题，还能确保内容发布的连贯性和周期性。保持稳定的更新频率有助于培养受众的阅读习惯，提升账号的活跃度与权重。但请注意，频率应服务于质量，切忌为了更新而牺牲内容的深度。\\n\\n## 创作高质量内容\\n\\n内容是营销的载体，其质量直接决定了品牌影响力的天花板。在信息泛滥的环境中，唯有高质量的内容才能穿透噪音，直击人心。\\n\\n**注重内容的原创性与价值**是底线。抄袭或洗稿不仅损害品牌声誉，也会被搜索引擎降权。真正的价值体现在独到的见解、深入的调研或实用的解决方案上。当用户觉得“看了这篇文章收获很大”时，他们就愿意为您的品牌“点赞”甚至“买单”。\\n\\n**采用故事化叙述增强吸引力**是内容创作的高级技巧。人类天生对故事敏感。将产品数据或品牌理念包装进一个生动的故事中，远比干巴巴的罗列更打动人。例如，与其说“我们的咖啡豆来自海拔1500米的高山”，不如讲述一位咖农如何世代守护这片土地的故事。故事能让品牌变得有温度、有血肉。\\n\\n**优化SEO提升搜索可见度**是内容被发现的入口。在创作时进行关键词研究，合理布局标题、副标题和正文，优化元描述和图片Alt标签。但这并不意味着堆砌关键词，而是让内容在“为读者写作”的同时，也兼顾搜索引擎的抓取规则，实现“人机共赢”。\\n\\n## 多渠道分发与推广\\n\\n“酒香也怕巷子深”。一份优质内容如果没有得到充分的传播，其价值将大打折扣。多渠道分发是放大内容影响力的关键一步。\\n\\n**利用社交媒体扩大传播**是最直接的方式。根据平台特性对内容进行二次加工，例如将长文的核心观点提炼为图文卡片或短视频脚本。在微博、微信、抖音等平台与用户积极互动，鼓励转发和讨论，利用社交裂变效应实现指数级传播。\\n\\n**合作KOL或行业意见领袖**是建立信任背书的高效路径。与品牌调性相符的KOL合作，可以借助他们的公信力和粉丝基础，快速触达更广泛的潜在受众。这种“借力”不仅能提升品牌曝光度，还能通过第三方的视角增强内容的可信度。\\n\\n**邮件营销与内容聚合平台**同样不可忽视。邮件营销是私域流量的核心阵地，能实现与订阅用户的一对一深度沟通。而今日头条、知乎等聚合平台，则能根据算法推荐将您的内容推送给精准的兴趣人群，实现内容的跨圈层流动。\\n\\n## 效果评估与优化\\n\\n内容营销不是“一锤子买卖”，而是一个动态循环的过程。没有评估，就无法知道投入产出比，更无法实现持续精进。\\n\\n**设定关键绩效指标(KPI)** 是评估的前提。您需要根据初期设定的营销目标，确定相应的衡量指标。例如，若目标是提升品牌知名度，可关注阅读量、转发量、粉丝增长率；若目标是获客，则需关注线索数、转化率、获客成本。\\n\\n**使用分析工具追踪表现**能提供客观的数据支撑。利用GA、新榜、各平台自带的专业后台，定期监测内容的打开率、完读率、互动率及转化路径。通过数据分析，您可以清晰地看到哪些选题、哪些标题、哪些渠道表现更优，从而找到成功内容的共性。\\n\\n**基于数据反馈持续优化策略**是内容营销的核心竞争力。如果数据显示短视频的完播率远高于图文，不妨将更多资源向视频倾斜；如果发现某类话题的互动率极高，则应深挖该方向，形成内容系列。内容营销的美妙之处在于，它永远有优化的空间，永远有上升的可能。\\n\\n## 结语\\n\\n内容营销是一场马拉松，而非百米冲刺。它需要耐心、洞察力以及持续迭代的勇气。在这个过程中，您可能会经历数据波动的焦虑，也可能会面临创意枯竭的瓶颈，但请相信——**内容营销是一个持续迭代的过程**，每一次的反馈都是下一次进步的阶梯。\\n\\n只要您始终坚持“以受众为中心”的初心，坚守“价值输出”的底线，并依据数据反馈灵活调整策略，您的品牌就一定能在用户心中占据不可替代的位置，实现品牌影响力的长期、稳健提升。从今天开始，迈出第一步，用内容与您的受众建立真诚的连接吧。\", \"outline\": \"{\\n  \\\"title\\\": \\\"如何通过内容营销提升品牌影响力\\\",\\n  \\\"introduction_points\\\": [\\n    \\\"内容营销在现代商业中的重要性\\\",\\n    \\\"本文旨在提供一套系统化的内容营销策略，帮助品牌提升影响力\\\"\\n  ],\\n  \\\"sections\\\": [\\n    {\\n      \\\"heading\\\": \\\"理解目标受众\\\",\\n      \\\"key_points\\\": [\\n        \\\"创建详细的买家画像\\\",\\n        \\\"分析受众需求与痛点\\\",\\n        \\\"利用数据分析工具精准定位\\\"\\n      ]\\n    },\\n    {\\n      \\\"heading\\\": \\\"制定内容策略\\\",\\n      \\\"key_points\\\": [\\n        \\\"设定清晰的营销目标\\\",\\n        \\\"选择合适的内容类型与渠道\\\",\\n        \\\"规划内容日历与发布频率\\\"\\n      ]\\n    },\\n    {\\n      \\\"heading\\\": \\\"创作高质量内容\\\",\\n      \\\"key_points\\\": [\\n        \\\"注重内容的原创性与价值\\\",\\n        \\\"采用故事化叙述增强吸引力\\\",\\n        \\\"优化SEO提升搜索可见度\\\"\\n      ]\\n    },\\n    {\\n      \\\"heading\\\": \\\"多渠道分发与推广\\\",\\n      \\\"key_points\\\": [\\n        \\\"利用社交媒体扩大传播\\\",\\n        \\\"合作KOL或行业意见领袖\\\",\\n        \\\"邮件营销与内容聚合平台\\\"\\n      ]\\n    },\\n    {\\n      \\\"heading\\\": \\\"效果评估与优化\\\",\\n      \\\"key_points\\\": [\\n        \\\"设定关键绩效指标(KPI)\\\",\\n        \\\"使用分析工具追踪表现\\\",\\n        \\\"基于数据反馈持续优化策略\\\"\\n      ]\\n    }\\n  ],\\n  \\\"conclusion_points\\\": [\\n    \\\"内容营销是一个持续迭代的过程\\\",\\n    \\\"坚持策略并灵活调整，方能实现品牌影响力的长期提升\\\"\\n  ]\\n}\", \"audience\": {\"type\": \"string\", \"description\": \"目标受众\"}, \"language\": {\"type\": \"string\", \"description\": \"目标语言，默认中文\"}, \"sections\": [{\"heading\": \"理解目标受众\", \"key_points\": [\"创建详细的买家画像\", \"分析受众需求与痛点\", \"利用数据分析工具精准定位\"]}, {\"heading\": \"制定内容策略\", \"key_points\": [\"设定清晰的营销目标\", \"选择合适的内容类型与渠道\", \"规划内容日历与发布频率\"]}, {\"heading\": \"创作高质量内容\", \"key_points\": [\"注重内容的原创性与价值\", \"采用故事化叙述增强吸引力\", \"优化SEO提升搜索可见度\"]}, {\"heading\": \"多渠道分发与推广\", \"key_points\": [\"利用社交媒体扩大传播\", \"合作KOL或行业意见领袖\", \"邮件营销与内容聚合平台\"]}, {\"heading\": \"效果评估与优化\", \"key_points\": [\"设定关键绩效指标(KPI)\", \"使用分析工具追踪表现\", \"基于数据反馈持续优化策略\"]}], \"__flowCode\": \"daily_blog_generator\", \"conclusion_points\": [\"内容营销是一个持续迭代的过程\", \"坚持策略并灵活调整，方能实现品牌影响力的长期提升\"], \"introduction_points\": [\"内容营销在现代商业中的重要性\", \"本文旨在提供一套系统化的内容营销策略，帮助品牌提升影响力\"]}', '[\"start\", \"gen_outline\", \"write_article\", \"end\"]', NULL, NULL, '2026-08-11 19:42:50', '2026-08-11 19:42:50');

-- ----------------------------
-- Table structure for ai_flow_run_node
-- ----------------------------
DROP TABLE IF EXISTS `ai_flow_run_node`;
CREATE TABLE `ai_flow_run_node`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `run_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '归属执行实例标识',
  `node_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '节点编码',
  `seq` int(11) NOT NULL DEFAULT 0 COMMENT '执行序（本次执行内自增）',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'SUCCESS' COMMENT '节点状态：SUCCESS',
  `output` json NULL COMMENT '该节点完成时的产物快照（JSON对象，可空）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_run_id`(`run_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 19 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI流程编排执行节点轨迹表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_flow_run_node
-- ----------------------------
INSERT INTO `ai_flow_run_node` VALUES (15, 'daily_blog_generator-9b8c3420cfb240a19babe99083fd66b3', 'start', 1, 'SUCCESS', '{\"tone\": {\"type\": \"string\", \"description\": \"写作语气，默认专业\"}, \"topic\": {\"type\": \"string\", \"description\": \"博客主题\"}, \"__runId\": \"daily_blog_generator-9b8c3420cfb240a19babe99083fd66b3\", \"audience\": {\"type\": \"string\", \"description\": \"目标受众\"}, \"language\": {\"type\": \"string\", \"description\": \"目标语言，默认中文\"}, \"__flowCode\": \"daily_blog_generator\"}', '2026-08-11 19:42:50');
INSERT INTO `ai_flow_run_node` VALUES (16, 'daily_blog_generator-9b8c3420cfb240a19babe99083fd66b3', 'gen_outline', 2, 'SUCCESS', '{\"tone\": {\"type\": \"string\", \"description\": \"写作语气，默认专业\"}, \"title\": \"如何通过内容营销提升品牌影响力\", \"topic\": {\"type\": \"string\", \"description\": \"博客主题\"}, \"__runId\": \"daily_blog_generator-9b8c3420cfb240a19babe99083fd66b3\", \"outline\": \"{\\n  \\\"title\\\": \\\"如何通过内容营销提升品牌影响力\\\",\\n  \\\"introduction_points\\\": [\\n    \\\"内容营销在现代商业中的重要性\\\",\\n    \\\"本文旨在提供一套系统化的内容营销策略，帮助品牌提升影响力\\\"\\n  ],\\n  \\\"sections\\\": [\\n    {\\n      \\\"heading\\\": \\\"理解目标受众\\\",\\n      \\\"key_points\\\": [\\n        \\\"创建详细的买家画像\\\",\\n        \\\"分析受众需求与痛点\\\",\\n        \\\"利用数据分析工具精准定位\\\"\\n      ]\\n    },\\n    {\\n      \\\"heading\\\": \\\"制定内容策略\\\",\\n      \\\"key_points\\\": [\\n        \\\"设定清晰的营销目标\\\",\\n        \\\"选择合适的内容类型与渠道\\\",\\n        \\\"规划内容日历与发布频率\\\"\\n      ]\\n    },\\n    {\\n      \\\"heading\\\": \\\"创作高质量内容\\\",\\n      \\\"key_points\\\": [\\n        \\\"注重内容的原创性与价值\\\",\\n        \\\"采用故事化叙述增强吸引力\\\",\\n        \\\"优化SEO提升搜索可见度\\\"\\n      ]\\n    },\\n    {\\n      \\\"heading\\\": \\\"多渠道分发与推广\\\",\\n      \\\"key_points\\\": [\\n        \\\"利用社交媒体扩大传播\\\",\\n        \\\"合作KOL或行业意见领袖\\\",\\n        \\\"邮件营销与内容聚合平台\\\"\\n      ]\\n    },\\n    {\\n      \\\"heading\\\": \\\"效果评估与优化\\\",\\n      \\\"key_points\\\": [\\n        \\\"设定关键绩效指标(KPI)\\\",\\n        \\\"使用分析工具追踪表现\\\",\\n        \\\"基于数据反馈持续优化策略\\\"\\n      ]\\n    }\\n  ],\\n  \\\"conclusion_points\\\": [\\n    \\\"内容营销是一个持续迭代的过程\\\",\\n    \\\"坚持策略并灵活调整，方能实现品牌影响力的长期提升\\\"\\n  ]\\n}\", \"audience\": {\"type\": \"string\", \"description\": \"目标受众\"}, \"language\": {\"type\": \"string\", \"description\": \"目标语言，默认中文\"}, \"sections\": [{\"heading\": \"理解目标受众\", \"key_points\": [\"创建详细的买家画像\", \"分析受众需求与痛点\", \"利用数据分析工具精准定位\"]}, {\"heading\": \"制定内容策略\", \"key_points\": [\"设定清晰的营销目标\", \"选择合适的内容类型与渠道\", \"规划内容日历与发布频率\"]}, {\"heading\": \"创作高质量内容\", \"key_points\": [\"注重内容的原创性与价值\", \"采用故事化叙述增强吸引力\", \"优化SEO提升搜索可见度\"]}, {\"heading\": \"多渠道分发与推广\", \"key_points\": [\"利用社交媒体扩大传播\", \"合作KOL或行业意见领袖\", \"邮件营销与内容聚合平台\"]}, {\"heading\": \"效果评估与优化\", \"key_points\": [\"设定关键绩效指标(KPI)\", \"使用分析工具追踪表现\", \"基于数据反馈持续优化策略\"]}], \"__flowCode\": \"daily_blog_generator\", \"conclusion_points\": [\"内容营销是一个持续迭代的过程\", \"坚持策略并灵活调整，方能实现品牌影响力的长期提升\"], \"introduction_points\": [\"内容营销在现代商业中的重要性\", \"本文旨在提供一套系统化的内容营销策略，帮助品牌提升影响力\"]}', '2026-08-11 19:42:54');
INSERT INTO `ai_flow_run_node` VALUES (17, 'daily_blog_generator-9b8c3420cfb240a19babe99083fd66b3', 'write_article', 3, 'SUCCESS', '{\"tone\": {\"type\": \"string\", \"description\": \"写作语气，默认专业\"}, \"title\": \"如何通过内容营销提升品牌影响力\", \"topic\": {\"type\": \"string\", \"description\": \"博客主题\"}, \"__runId\": \"daily_blog_generator-9b8c3420cfb240a19babe99083fd66b3\", \"article\": \"# 如何通过内容营销提升品牌影响力\\n\\n在信息过载的数字时代，消费者的注意力已成为最稀缺的资源。传统的硬性广告正逐渐失效，取而代之的是以价值为核心的内容营销。内容营销并非简单的“写文章”或“发视频”，而是一种以用户为中心、以价值为导向的长期战略。它通过持续输出有用、有趣、有深度的内容，与目标受众建立信任，进而潜移默化地提升品牌影响力。本文将为您提供一套系统化的内容营销策略，从理解受众到效果评估，帮助您的品牌在激烈的市场竞争中脱颖而出。\\n\\n## 理解目标受众\\n\\n任何成功的内容营销都始于对受众的深刻理解。如果不知道为谁创作，再精美的内容也如同对空谷喊话，难以产生回响。\\n\\n**创建详细的买家画像**是第一步。买家画像是基于数据与调研构建的半虚构人物形象，代表您的理想客户。除了基本的年龄、职业、收入等人口统计学特征外，更需关注他们的兴趣偏好、信息获取习惯以及价值观。例如，一个面向Z世代的美妆品牌，其买家画像可能是一位关注环保、热衷社交媒体、偏好短视频内容的大学生。\\n\\n**分析受众需求与痛点**是内容价值的核心来源。您的受众在生活或工作中遇到了什么难题？他们在寻求什么样的解决方案？通过深度访谈、客服反馈分析、社交媒体评论挖掘等方式，您可以精准捕捉这些信息。当您的内容能直接回应这些痛点时，它就不再是“推销”，而是“帮助”。\\n\\n**利用数据分析工具精准定位**能让您的努力事半功倍。Google Analytics、百度指数、社交媒体后台数据等工具，可以揭示受众的地理分布、活跃时段、兴趣关键词等。这些客观数据能修正您的主观猜测，确保您的策略始终走在正确的轨道上。\\n\\n## 制定内容策略\\n\\n理解了“对谁说”之后，下一步是解决“说什么”和“怎么说”的问题。没有策略的内容生产，就像没有航向的船只，随波逐流却难以抵达目的地。\\n\\n**设定清晰的营销目标**是策略的起点。您的内容营销是为了提升品牌知名度、增加网站流量、生成销售线索，还是促进用户转化？不同阶段的目标决定了内容的侧重点。例如，品牌初创期侧重于曝光，而成熟期则更关注用户留存与复购。\\n\\n**选择合适的内容类型与渠道**需因地制宜。图文、长视频、短视频、播客、信息图……每种形式都有其独特的传播逻辑。同样，抖音、小红书、LinkedIn、知乎等平台也拥有截然不同的用户生态。您需要根据目标受众的偏好和营销目标，决定将资源集中在哪些内容形式和渠道上。与其在五个平台上浅尝辄止，不如在一个平台上深耕细作。\\n\\n**规划内容日历与发布频率**是保持内容节奏的保障。内容日历不仅帮助您提前规划选题，还能确保内容发布的连贯性和周期性。保持稳定的更新频率有助于培养受众的阅读习惯，提升账号的活跃度与权重。但请注意，频率应服务于质量，切忌为了更新而牺牲内容的深度。\\n\\n## 创作高质量内容\\n\\n内容是营销的载体，其质量直接决定了品牌影响力的天花板。在信息泛滥的环境中，唯有高质量的内容才能穿透噪音，直击人心。\\n\\n**注重内容的原创性与价值**是底线。抄袭或洗稿不仅损害品牌声誉，也会被搜索引擎降权。真正的价值体现在独到的见解、深入的调研或实用的解决方案上。当用户觉得“看了这篇文章收获很大”时，他们就愿意为您的品牌“点赞”甚至“买单”。\\n\\n**采用故事化叙述增强吸引力**是内容创作的高级技巧。人类天生对故事敏感。将产品数据或品牌理念包装进一个生动的故事中，远比干巴巴的罗列更打动人。例如，与其说“我们的咖啡豆来自海拔1500米的高山”，不如讲述一位咖农如何世代守护这片土地的故事。故事能让品牌变得有温度、有血肉。\\n\\n**优化SEO提升搜索可见度**是内容被发现的入口。在创作时进行关键词研究，合理布局标题、副标题和正文，优化元描述和图片Alt标签。但这并不意味着堆砌关键词，而是让内容在“为读者写作”的同时，也兼顾搜索引擎的抓取规则，实现“人机共赢”。\\n\\n## 多渠道分发与推广\\n\\n“酒香也怕巷子深”。一份优质内容如果没有得到充分的传播，其价值将大打折扣。多渠道分发是放大内容影响力的关键一步。\\n\\n**利用社交媒体扩大传播**是最直接的方式。根据平台特性对内容进行二次加工，例如将长文的核心观点提炼为图文卡片或短视频脚本。在微博、微信、抖音等平台与用户积极互动，鼓励转发和讨论，利用社交裂变效应实现指数级传播。\\n\\n**合作KOL或行业意见领袖**是建立信任背书的高效路径。与品牌调性相符的KOL合作，可以借助他们的公信力和粉丝基础，快速触达更广泛的潜在受众。这种“借力”不仅能提升品牌曝光度，还能通过第三方的视角增强内容的可信度。\\n\\n**邮件营销与内容聚合平台**同样不可忽视。邮件营销是私域流量的核心阵地，能实现与订阅用户的一对一深度沟通。而今日头条、知乎等聚合平台，则能根据算法推荐将您的内容推送给精准的兴趣人群，实现内容的跨圈层流动。\\n\\n## 效果评估与优化\\n\\n内容营销不是“一锤子买卖”，而是一个动态循环的过程。没有评估，就无法知道投入产出比，更无法实现持续精进。\\n\\n**设定关键绩效指标(KPI)** 是评估的前提。您需要根据初期设定的营销目标，确定相应的衡量指标。例如，若目标是提升品牌知名度，可关注阅读量、转发量、粉丝增长率；若目标是获客，则需关注线索数、转化率、获客成本。\\n\\n**使用分析工具追踪表现**能提供客观的数据支撑。利用GA、新榜、各平台自带的专业后台，定期监测内容的打开率、完读率、互动率及转化路径。通过数据分析，您可以清晰地看到哪些选题、哪些标题、哪些渠道表现更优，从而找到成功内容的共性。\\n\\n**基于数据反馈持续优化策略**是内容营销的核心竞争力。如果数据显示短视频的完播率远高于图文，不妨将更多资源向视频倾斜；如果发现某类话题的互动率极高，则应深挖该方向，形成内容系列。内容营销的美妙之处在于，它永远有优化的空间，永远有上升的可能。\\n\\n## 结语\\n\\n内容营销是一场马拉松，而非百米冲刺。它需要耐心、洞察力以及持续迭代的勇气。在这个过程中，您可能会经历数据波动的焦虑，也可能会面临创意枯竭的瓶颈，但请相信——**内容营销是一个持续迭代的过程**，每一次的反馈都是下一次进步的阶梯。\\n\\n只要您始终坚持“以受众为中心”的初心，坚守“价值输出”的底线，并依据数据反馈灵活调整策略，您的品牌就一定能在用户心中占据不可替代的位置，实现品牌影响力的长期、稳健提升。从今天开始，迈出第一步，用内容与您的受众建立真诚的连接吧。\", \"outline\": \"{\\n  \\\"title\\\": \\\"如何通过内容营销提升品牌影响力\\\",\\n  \\\"introduction_points\\\": [\\n    \\\"内容营销在现代商业中的重要性\\\",\\n    \\\"本文旨在提供一套系统化的内容营销策略，帮助品牌提升影响力\\\"\\n  ],\\n  \\\"sections\\\": [\\n    {\\n      \\\"heading\\\": \\\"理解目标受众\\\",\\n      \\\"key_points\\\": [\\n        \\\"创建详细的买家画像\\\",\\n        \\\"分析受众需求与痛点\\\",\\n        \\\"利用数据分析工具精准定位\\\"\\n      ]\\n    },\\n    {\\n      \\\"heading\\\": \\\"制定内容策略\\\",\\n      \\\"key_points\\\": [\\n        \\\"设定清晰的营销目标\\\",\\n        \\\"选择合适的内容类型与渠道\\\",\\n        \\\"规划内容日历与发布频率\\\"\\n      ]\\n    },\\n    {\\n      \\\"heading\\\": \\\"创作高质量内容\\\",\\n      \\\"key_points\\\": [\\n        \\\"注重内容的原创性与价值\\\",\\n        \\\"采用故事化叙述增强吸引力\\\",\\n        \\\"优化SEO提升搜索可见度\\\"\\n      ]\\n    },\\n    {\\n      \\\"heading\\\": \\\"多渠道分发与推广\\\",\\n      \\\"key_points\\\": [\\n        \\\"利用社交媒体扩大传播\\\",\\n        \\\"合作KOL或行业意见领袖\\\",\\n        \\\"邮件营销与内容聚合平台\\\"\\n      ]\\n    },\\n    {\\n      \\\"heading\\\": \\\"效果评估与优化\\\",\\n      \\\"key_points\\\": [\\n        \\\"设定关键绩效指标(KPI)\\\",\\n        \\\"使用分析工具追踪表现\\\",\\n        \\\"基于数据反馈持续优化策略\\\"\\n      ]\\n    }\\n  ],\\n  \\\"conclusion_points\\\": [\\n    \\\"内容营销是一个持续迭代的过程\\\",\\n    \\\"坚持策略并灵活调整，方能实现品牌影响力的长期提升\\\"\\n  ]\\n}\", \"audience\": {\"type\": \"string\", \"description\": \"目标受众\"}, \"language\": {\"type\": \"string\", \"description\": \"目标语言，默认中文\"}, \"sections\": [{\"heading\": \"理解目标受众\", \"key_points\": [\"创建详细的买家画像\", \"分析受众需求与痛点\", \"利用数据分析工具精准定位\"]}, {\"heading\": \"制定内容策略\", \"key_points\": [\"设定清晰的营销目标\", \"选择合适的内容类型与渠道\", \"规划内容日历与发布频率\"]}, {\"heading\": \"创作高质量内容\", \"key_points\": [\"注重内容的原创性与价值\", \"采用故事化叙述增强吸引力\", \"优化SEO提升搜索可见度\"]}, {\"heading\": \"多渠道分发与推广\", \"key_points\": [\"利用社交媒体扩大传播\", \"合作KOL或行业意见领袖\", \"邮件营销与内容聚合平台\"]}, {\"heading\": \"效果评估与优化\", \"key_points\": [\"设定关键绩效指标(KPI)\", \"使用分析工具追踪表现\", \"基于数据反馈持续优化策略\"]}], \"__flowCode\": \"daily_blog_generator\", \"conclusion_points\": [\"内容营销是一个持续迭代的过程\", \"坚持策略并灵活调整，方能实现品牌影响力的长期提升\"], \"introduction_points\": [\"内容营销在现代商业中的重要性\", \"本文旨在提供一套系统化的内容营销策略，帮助品牌提升影响力\"]}', '2026-08-11 19:43:11');
INSERT INTO `ai_flow_run_node` VALUES (18, 'daily_blog_generator-9b8c3420cfb240a19babe99083fd66b3', 'end', 4, 'SUCCESS', '{\"tone\": {\"type\": \"string\", \"description\": \"写作语气，默认专业\"}, \"title\": \"如何通过内容营销提升品牌影响力\", \"topic\": {\"type\": \"string\", \"description\": \"博客主题\"}, \"__runId\": \"daily_blog_generator-9b8c3420cfb240a19babe99083fd66b3\", \"article\": \"# 如何通过内容营销提升品牌影响力\\n\\n在信息过载的数字时代，消费者的注意力已成为最稀缺的资源。传统的硬性广告正逐渐失效，取而代之的是以价值为核心的内容营销。内容营销并非简单的“写文章”或“发视频”，而是一种以用户为中心、以价值为导向的长期战略。它通过持续输出有用、有趣、有深度的内容，与目标受众建立信任，进而潜移默化地提升品牌影响力。本文将为您提供一套系统化的内容营销策略，从理解受众到效果评估，帮助您的品牌在激烈的市场竞争中脱颖而出。\\n\\n## 理解目标受众\\n\\n任何成功的内容营销都始于对受众的深刻理解。如果不知道为谁创作，再精美的内容也如同对空谷喊话，难以产生回响。\\n\\n**创建详细的买家画像**是第一步。买家画像是基于数据与调研构建的半虚构人物形象，代表您的理想客户。除了基本的年龄、职业、收入等人口统计学特征外，更需关注他们的兴趣偏好、信息获取习惯以及价值观。例如，一个面向Z世代的美妆品牌，其买家画像可能是一位关注环保、热衷社交媒体、偏好短视频内容的大学生。\\n\\n**分析受众需求与痛点**是内容价值的核心来源。您的受众在生活或工作中遇到了什么难题？他们在寻求什么样的解决方案？通过深度访谈、客服反馈分析、社交媒体评论挖掘等方式，您可以精准捕捉这些信息。当您的内容能直接回应这些痛点时，它就不再是“推销”，而是“帮助”。\\n\\n**利用数据分析工具精准定位**能让您的努力事半功倍。Google Analytics、百度指数、社交媒体后台数据等工具，可以揭示受众的地理分布、活跃时段、兴趣关键词等。这些客观数据能修正您的主观猜测，确保您的策略始终走在正确的轨道上。\\n\\n## 制定内容策略\\n\\n理解了“对谁说”之后，下一步是解决“说什么”和“怎么说”的问题。没有策略的内容生产，就像没有航向的船只，随波逐流却难以抵达目的地。\\n\\n**设定清晰的营销目标**是策略的起点。您的内容营销是为了提升品牌知名度、增加网站流量、生成销售线索，还是促进用户转化？不同阶段的目标决定了内容的侧重点。例如，品牌初创期侧重于曝光，而成熟期则更关注用户留存与复购。\\n\\n**选择合适的内容类型与渠道**需因地制宜。图文、长视频、短视频、播客、信息图……每种形式都有其独特的传播逻辑。同样，抖音、小红书、LinkedIn、知乎等平台也拥有截然不同的用户生态。您需要根据目标受众的偏好和营销目标，决定将资源集中在哪些内容形式和渠道上。与其在五个平台上浅尝辄止，不如在一个平台上深耕细作。\\n\\n**规划内容日历与发布频率**是保持内容节奏的保障。内容日历不仅帮助您提前规划选题，还能确保内容发布的连贯性和周期性。保持稳定的更新频率有助于培养受众的阅读习惯，提升账号的活跃度与权重。但请注意，频率应服务于质量，切忌为了更新而牺牲内容的深度。\\n\\n## 创作高质量内容\\n\\n内容是营销的载体，其质量直接决定了品牌影响力的天花板。在信息泛滥的环境中，唯有高质量的内容才能穿透噪音，直击人心。\\n\\n**注重内容的原创性与价值**是底线。抄袭或洗稿不仅损害品牌声誉，也会被搜索引擎降权。真正的价值体现在独到的见解、深入的调研或实用的解决方案上。当用户觉得“看了这篇文章收获很大”时，他们就愿意为您的品牌“点赞”甚至“买单”。\\n\\n**采用故事化叙述增强吸引力**是内容创作的高级技巧。人类天生对故事敏感。将产品数据或品牌理念包装进一个生动的故事中，远比干巴巴的罗列更打动人。例如，与其说“我们的咖啡豆来自海拔1500米的高山”，不如讲述一位咖农如何世代守护这片土地的故事。故事能让品牌变得有温度、有血肉。\\n\\n**优化SEO提升搜索可见度**是内容被发现的入口。在创作时进行关键词研究，合理布局标题、副标题和正文，优化元描述和图片Alt标签。但这并不意味着堆砌关键词，而是让内容在“为读者写作”的同时，也兼顾搜索引擎的抓取规则，实现“人机共赢”。\\n\\n## 多渠道分发与推广\\n\\n“酒香也怕巷子深”。一份优质内容如果没有得到充分的传播，其价值将大打折扣。多渠道分发是放大内容影响力的关键一步。\\n\\n**利用社交媒体扩大传播**是最直接的方式。根据平台特性对内容进行二次加工，例如将长文的核心观点提炼为图文卡片或短视频脚本。在微博、微信、抖音等平台与用户积极互动，鼓励转发和讨论，利用社交裂变效应实现指数级传播。\\n\\n**合作KOL或行业意见领袖**是建立信任背书的高效路径。与品牌调性相符的KOL合作，可以借助他们的公信力和粉丝基础，快速触达更广泛的潜在受众。这种“借力”不仅能提升品牌曝光度，还能通过第三方的视角增强内容的可信度。\\n\\n**邮件营销与内容聚合平台**同样不可忽视。邮件营销是私域流量的核心阵地，能实现与订阅用户的一对一深度沟通。而今日头条、知乎等聚合平台，则能根据算法推荐将您的内容推送给精准的兴趣人群，实现内容的跨圈层流动。\\n\\n## 效果评估与优化\\n\\n内容营销不是“一锤子买卖”，而是一个动态循环的过程。没有评估，就无法知道投入产出比，更无法实现持续精进。\\n\\n**设定关键绩效指标(KPI)** 是评估的前提。您需要根据初期设定的营销目标，确定相应的衡量指标。例如，若目标是提升品牌知名度，可关注阅读量、转发量、粉丝增长率；若目标是获客，则需关注线索数、转化率、获客成本。\\n\\n**使用分析工具追踪表现**能提供客观的数据支撑。利用GA、新榜、各平台自带的专业后台，定期监测内容的打开率、完读率、互动率及转化路径。通过数据分析，您可以清晰地看到哪些选题、哪些标题、哪些渠道表现更优，从而找到成功内容的共性。\\n\\n**基于数据反馈持续优化策略**是内容营销的核心竞争力。如果数据显示短视频的完播率远高于图文，不妨将更多资源向视频倾斜；如果发现某类话题的互动率极高，则应深挖该方向，形成内容系列。内容营销的美妙之处在于，它永远有优化的空间，永远有上升的可能。\\n\\n## 结语\\n\\n内容营销是一场马拉松，而非百米冲刺。它需要耐心、洞察力以及持续迭代的勇气。在这个过程中，您可能会经历数据波动的焦虑，也可能会面临创意枯竭的瓶颈，但请相信——**内容营销是一个持续迭代的过程**，每一次的反馈都是下一次进步的阶梯。\\n\\n只要您始终坚持“以受众为中心”的初心，坚守“价值输出”的底线，并依据数据反馈灵活调整策略，您的品牌就一定能在用户心中占据不可替代的位置，实现品牌影响力的长期、稳健提升。从今天开始，迈出第一步，用内容与您的受众建立真诚的连接吧。\", \"outline\": \"{\\n  \\\"title\\\": \\\"如何通过内容营销提升品牌影响力\\\",\\n  \\\"introduction_points\\\": [\\n    \\\"内容营销在现代商业中的重要性\\\",\\n    \\\"本文旨在提供一套系统化的内容营销策略，帮助品牌提升影响力\\\"\\n  ],\\n  \\\"sections\\\": [\\n    {\\n      \\\"heading\\\": \\\"理解目标受众\\\",\\n      \\\"key_points\\\": [\\n        \\\"创建详细的买家画像\\\",\\n        \\\"分析受众需求与痛点\\\",\\n        \\\"利用数据分析工具精准定位\\\"\\n      ]\\n    },\\n    {\\n      \\\"heading\\\": \\\"制定内容策略\\\",\\n      \\\"key_points\\\": [\\n        \\\"设定清晰的营销目标\\\",\\n        \\\"选择合适的内容类型与渠道\\\",\\n        \\\"规划内容日历与发布频率\\\"\\n      ]\\n    },\\n    {\\n      \\\"heading\\\": \\\"创作高质量内容\\\",\\n      \\\"key_points\\\": [\\n        \\\"注重内容的原创性与价值\\\",\\n        \\\"采用故事化叙述增强吸引力\\\",\\n        \\\"优化SEO提升搜索可见度\\\"\\n      ]\\n    },\\n    {\\n      \\\"heading\\\": \\\"多渠道分发与推广\\\",\\n      \\\"key_points\\\": [\\n        \\\"利用社交媒体扩大传播\\\",\\n        \\\"合作KOL或行业意见领袖\\\",\\n        \\\"邮件营销与内容聚合平台\\\"\\n      ]\\n    },\\n    {\\n      \\\"heading\\\": \\\"效果评估与优化\\\",\\n      \\\"key_points\\\": [\\n        \\\"设定关键绩效指标(KPI)\\\",\\n        \\\"使用分析工具追踪表现\\\",\\n        \\\"基于数据反馈持续优化策略\\\"\\n      ]\\n    }\\n  ],\\n  \\\"conclusion_points\\\": [\\n    \\\"内容营销是一个持续迭代的过程\\\",\\n    \\\"坚持策略并灵活调整，方能实现品牌影响力的长期提升\\\"\\n  ]\\n}\", \"audience\": {\"type\": \"string\", \"description\": \"目标受众\"}, \"language\": {\"type\": \"string\", \"description\": \"目标语言，默认中文\"}, \"sections\": [{\"heading\": \"理解目标受众\", \"key_points\": [\"创建详细的买家画像\", \"分析受众需求与痛点\", \"利用数据分析工具精准定位\"]}, {\"heading\": \"制定内容策略\", \"key_points\": [\"设定清晰的营销目标\", \"选择合适的内容类型与渠道\", \"规划内容日历与发布频率\"]}, {\"heading\": \"创作高质量内容\", \"key_points\": [\"注重内容的原创性与价值\", \"采用故事化叙述增强吸引力\", \"优化SEO提升搜索可见度\"]}, {\"heading\": \"多渠道分发与推广\", \"key_points\": [\"利用社交媒体扩大传播\", \"合作KOL或行业意见领袖\", \"邮件营销与内容聚合平台\"]}, {\"heading\": \"效果评估与优化\", \"key_points\": [\"设定关键绩效指标(KPI)\", \"使用分析工具追踪表现\", \"基于数据反馈持续优化策略\"]}], \"__flowCode\": \"daily_blog_generator\", \"conclusion_points\": [\"内容营销是一个持续迭代的过程\", \"坚持策略并灵活调整，方能实现品牌影响力的长期提升\"], \"introduction_points\": [\"内容营销在现代商业中的重要性\", \"本文旨在提供一套系统化的内容营销策略，帮助品牌提升影响力\"]}', '2026-08-11 19:43:11');

-- ----------------------------
-- Table structure for ai_harness_confirmation
-- ----------------------------
DROP TABLE IF EXISTS `ai_harness_confirmation`;
CREATE TABLE `ai_harness_confirmation`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `confirmation_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '确认业务ID',
  `action` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '高风险动作编码',
  `draft_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '草稿业务ID',
  `draft_revision` bigint(20) NOT NULL COMMENT '确认绑定的草稿版本',
  `user_id` bigint(20) NOT NULL COMMENT '确认用户强边界',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '发起会话，仅审计',
  `input_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'canonical initialInput SHA-256',
  `token_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '一次性令牌 SHA-256，不存明文',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'PENDING|CONFIRMED|CONSUMED|EXPIRED|CANCELLED',
  `expires_at` datetime NOT NULL COMMENT '确认过期时间',
  `confirmed_at` datetime NULL DEFAULT NULL,
  `consumed_at` datetime NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_harness_confirmation_id`(`confirmation_id` ASC) USING BTREE,
  UNIQUE INDEX `uk_harness_confirmation_action_revision`(`action` ASC, `draft_id` ASC, `draft_revision` ASC) USING BTREE,
  INDEX `idx_harness_confirmation_token`(`token_hash` ASC) USING BTREE,
  INDEX `idx_harness_confirmation_expiry`(`status` ASC, `expires_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Harness高风险动作确认' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_harness_confirmation
-- ----------------------------
INSERT INTO `ai_harness_confirmation` VALUES (1, 'cfm_5f1ff3da7efb411dba07bbc4c34f036f', 'REAL_RUN', 'd_79cc877813dd407fadf9cbdd6514d0e7', 8, 2052290101098295297, 'dad1e279-614b-42ba-a7ea-988998674c5e', 'c7a8e7dc43d8488b6a8feb21ec76ecfffbe6776b7a1904eb07146ef3359c27f0', '418eb946ead4a66ca4e5a022f54fc01bf7cb69abbd6f8656290c41701ad32d3b', 'CONSUMED', '2026-08-11 19:46:33', '2026-08-11 19:41:38', '2026-08-11 19:41:39', '2026-08-11 19:41:33', '2026-08-11 19:41:39');
INSERT INTO `ai_harness_confirmation` VALUES (2, 'cfm_35b644bf20c34c5e9e924d59697bff3e', 'REAL_RUN', 'd_79cc877813dd407fadf9cbdd6514d0e7', 24, 2052290101098295297, 'dad1e279-614b-42ba-a7ea-988998674c5e', '873eeadbf846e4d0875d790748b011c0f59deff1f1a77275213d89fff65ab3e1', '7167f2f806da8cd089d8cd3f9a8a2c7955cf4ec1ac30d9fd3209c46a3692e75a', 'CONSUMED', '2026-08-11 19:50:29', '2026-08-11 19:45:35', '2026-08-11 19:45:35', '2026-08-11 19:45:29', '2026-08-11 19:45:35');
INSERT INTO `ai_harness_confirmation` VALUES (3, 'cfm_e563c4cdea7641e8a0265082345bfffd', 'REAL_RUN', 'd_2adc44ac09cc48c3a53bdc14b4334a06', 12, 2052290101098295297, 'c288771f-30e2-47b8-9479-4d50b674e4f4', '6a2aa5357c3a4254cee441f1d85219221e84a794fbc36070238f396af42cd14f', '6dbc706a7ed596f0400c89ca8c4cecaeabd66cf47c722ad120799e86aa3a3e85', 'CONSUMED', '2026-08-11 20:05:25', '2026-08-11 20:00:31', '2026-08-11 20:00:31', '2026-08-11 20:00:25', '2026-08-11 20:00:31');
INSERT INTO `ai_harness_confirmation` VALUES (4, 'cfm_d2e4a82e86184145a857c281aa046dee', 'REAL_RUN', 'd_7d9a475743364df8b955c1bf6125a1ce', 10, 2052290101098295297, '31b8aebb-401c-4703-95c5-f4e742d9b663', 'f5a6bbf77305dda3d5b48f41454e5f860dd4330d3e5dc06975e89c76ddf8ca7b', 'c7f64b8589fd2a795940050546c727b8bf305cb20a9d2d64a08078baedb30975', 'CONSUMED', '2026-08-11 20:29:50', '2026-08-11 20:24:52', '2026-08-11 20:24:52', '2026-08-11 20:24:49', '2026-08-11 20:24:52');

-- ----------------------------
-- Table structure for ai_harness_operation
-- ----------------------------
DROP TABLE IF EXISTS `ai_harness_operation`;
CREATE TABLE `ai_harness_operation`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `operation_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '操作业务ID',
  `action` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '高风险动作编码',
  `draft_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '草稿业务ID',
  `draft_revision` bigint(20) NOT NULL COMMENT '操作绑定的草稿版本',
  `user_id` bigint(20) NOT NULL COMMENT '操作用户强边界',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '发起会话，仅审计',
  `input_digest` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'canonical initialInput SHA-256',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'PENDING|RUNNING|SUCCEEDED|FAILED|UNKNOWN',
  `result_summary_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '脱敏、截断后的安全结果摘要',
  `error_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '稳定错误编码',
  `error_message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '脱敏错误摘要',
  `started_at` datetime NULL DEFAULT NULL,
  `heartbeat_at` datetime NULL DEFAULT NULL,
  `finished_at` datetime NULL DEFAULT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_harness_operation_id`(`operation_id` ASC) USING BTREE,
  UNIQUE INDEX `uk_harness_operation_action_revision`(`action` ASC, `draft_id` ASC, `draft_revision` ASC) USING BTREE,
  INDEX `idx_harness_operation_owner`(`user_id` ASC, `operation_id` ASC) USING BTREE,
  INDEX `idx_harness_operation_status`(`status` ASC, `heartbeat_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Harness高风险幂等操作' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_harness_operation
-- ----------------------------
INSERT INTO `ai_harness_operation` VALUES (1, 'op_bda738d315a24ed1a0c354888c657813', 'REAL_RUN', 'd_79cc877813dd407fadf9cbdd6514d0e7', 8, 2052290101098295297, 'dad1e279-614b-42ba-a7ea-988998674c5e', 'c7a8e7dc43d8488b6a8feb21ec76ecfffbe6776b7a1904eb07146ef3359c27f0', 'FAILED', NULL, 'REAL_RUN_FAILED', '入参校验失败：「topic」类型应为 Object；「language」类型应为 Object；「audience」类型应为 Object；「tone」类型应为 Object', '2026-08-11 19:41:39', '2026-08-11 19:41:39', '2026-08-11 19:41:39', '2026-08-11 19:41:39', '2026-08-11 19:41:39');
INSERT INTO `ai_harness_operation` VALUES (2, 'op_06a6bf8241a24712af83fc1eb4424f08', 'REAL_RUN', 'd_79cc877813dd407fadf9cbdd6514d0e7', 24, 2052290101098295297, 'dad1e279-614b-42ba-a7ea-988998674c5e', '873eeadbf846e4d0875d790748b011c0f59deff1f1a77275213d89fff65ab3e1', 'FAILED', NULL, 'REAL_RUN_FAILED', '入参校验失败：「topic」类型应为 Object；「language」类型应为 Object；「audience」类型应为 Object；「tone」类型应为 Object；「days」类型应为 Object', '2026-08-11 19:45:35', '2026-08-11 19:45:35', '2026-08-11 19:45:35', '2026-08-11 19:45:35', '2026-08-11 19:45:35');
INSERT INTO `ai_harness_operation` VALUES (3, 'op_050ef0df6f0542b9b0f6203d59b988ee', 'REAL_RUN', 'd_2adc44ac09cc48c3a53bdc14b4334a06', 12, 2052290101098295297, 'c288771f-30e2-47b8-9479-4d50b674e4f4', '6a2aa5357c3a4254cee441f1d85219221e84a794fbc36070238f396af42cd14f', 'FAILED', NULL, 'REAL_RUN_FAILED', '入参校验失败：「date」类型应为 Object；「topic」类型应为 Object', '2026-08-11 20:00:31', '2026-08-11 20:00:31', '2026-08-11 20:00:31', '2026-08-11 20:00:31', '2026-08-11 20:00:31');
INSERT INTO `ai_harness_operation` VALUES (4, 'op_443110be48bc46f1a89d9c7638a0e0b0', 'REAL_RUN', 'd_7d9a475743364df8b955c1bf6125a1ce', 10, 2052290101098295297, '31b8aebb-401c-4703-95c5-f4e742d9b663', 'f5a6bbf77305dda3d5b48f41454e5f860dd4330d3e5dc06975e89c76ddf8ca7b', 'SUCCEEDED', '{\"nodeResults\":{\"start\":true,\"outline_gen\":true,\"draft_write\":true,\"polish\":true,\"end\":true},\"attributes\":{\"polished\":\"# AI写作助手：让你的效率翻倍的5个秘诀\\n\\n## 引言：写作的痛点与AI的机遇\\n\\n你有没有过这样的经历？打开一个空白文档，光标闪烁，大脑却一片空白。截止日期近在眼前，邮件还没回，报告还没写，方案还没憋出来——这是多少职场人的日常困境。时间紧张、思路枯竭、表达不畅，这三个词几乎概括了职场写作的全部痛点。\\n\\n但好消息是，AI写作工具正在改变这一切。它能在几秒钟内生成一段结构完整的文字，帮你把\\\"车轱辘话\\\"变得精炼有力，甚至在你毫无头绪时，给你一个全新的切入点。AI不是来取代你的，而是来帮你把省下来的时间，用在更有价值的事情上——比如思考、创意、决策。\\n\\n这篇文章不讲高深的理论，只分享五个实操性极强的秘诀，让你从今天开始，就能把AI变成自己的写作加速器。\\n\\n## 一、选择合适的AI写作工具\\n\\n市面上的AI写作工具五花八门，选对工具等于成功了一半。目前主流的选项包括：ChatGPT（通用对话能力强，适合各类写作场景）、文心一言（中文理解优秀，对本土语境把握更准）、Notion AI（集成在笔记软件中，适合知识管理和长文写作）、Claude（长文本处理能力突出）等。\\n\\n怎么选？关键看你的核心需求。如果你主要写长文报告或深度分析，ChatGPT或Claude会更合适；如果你经常写短文案、营销话术，文心一言或Copy.ai这类工具可能更对口；如果你需要边整理资料边写作，Notion AI的无缝集成体验会让人上瘾。\\n\\n免费和付费怎么权衡？免费版通常有次数限制或功能阉割，但对轻度使用者完全够用。如果你每周都要写大量内容，花几十块钱订阅付费版绝对物超所值——省下的时间成本远超订阅费用。\\n\\n## 二、明确目标，提供清晰指令\\n\\n很多人在用AI时觉得\\\"它不懂我\\\"，其实问题往往出在指令不够清晰。AI不是读心术师，你给它多模糊的指令，它就还你多模糊的答案。\\n\\n**第一步，设定写作目标。** 在提问前，先想清楚四个问题：读者是谁？写作目的是什么？期望的风格是什么？大概要多长？比如，与其说\\\"帮我写一封邮件\\\"，不如说\\\"帮我写一封给客户的道歉邮件，语气诚恳但不过度卑微，说明项目延期原因并提出补救方案，300字左右\\\"。\\n\\n**第二步，编写有效提示词。** 好的提示词要具体、详细、包含示例。你可以这样写：\\\"以产品经理的口吻，写一段200字的产品介绍，突出三个核心卖点：速度快、操作简单、性价比高。参考风格：Apple官网的产品文案，简洁有力。\\\"\\n\\n**第三步，迭代优化。** 第一次输出不满意？别急着换工具，试着追问\\\"能否更正式一些\\\"\\\"加一个数据支撑\\\"\\\"换个更有感染力的开头\\\"。AI的上下文记忆能力让你可以在对话中不断打磨，直到接近理想效果。\\n\\n## 三、利用AI进行内容生成与优化\\n\\nAI最强大的能力不是\\\"替你写\\\"，而是\\\"帮你写得更快更好\\\"。具体来说，有三个层面的应用：\\n\\n**快速生成初稿。** 面对空白页时，你可以让AI先搭一个框架。比如：\\\"给我一篇关于远程工作的博客大纲，包含引言、3-4个主体部分和结论。\\\"拿到框架后，再逐段让AI展开。这样你从\\\"从零开始\\\"变成了\\\"从半成品开始\\\"，心理压力小得多，效率也翻倍。\\n\\n**优化表达。** 你写了一段话但觉得不够好？直接丢给AI：\\\"帮我改写这段话，让语气更专业\\\"或\\\"把这段缩短一半，保留核心信息\\\"。AI在句式调整、语气转换、逻辑梳理方面非常擅长，尤其是那些你反复改了好几遍还是不满意的句子，AI往往能给你意想不到的惊喜。\\n\\n**处理重复内容。** 如果你经常要写类似的工作周报、月度总结、产品说明，AI简直是救星。把模板喂给AI，它就能根据每次提供的新信息自动生成新版本，你只需要微调即可。这不仅省时间，还保证了内容的一致性。\\n\\n## 四、结合人工编辑，确保质量\\n\\nAI再聪明，也有它的局限性。它可能会编造数据、混淆事实，或者写出逻辑不通的段落。更关键的是，AI没有你的经验、你的视角、你的情感——而这些恰恰是好文章的灵魂。\\n\\n所以，**AI输出永远只是初稿，不是终稿**。你需要做的是：\\n\\n**第一，事实核查。** AI生成的内容中涉及的数据、引用、案例，务必逐一核实。尤其是那些看起来很具体的数字和\\\"研究表明\\\"，很可能纯属虚构。\\n\\n**第二，保持个人声音。** 你的文章之所以有人读，是因为它有\\\"你\\\"的味道。AI写出来的东西往往四平八稳、缺乏个性。你需要在修改时加入自己的观点、经历和真实案例，让文字重新\\\"活\\\"起来。\\n\\n**第三，检查品牌一致性。** 如果你是代表公司发声，AI可能无法精准把握你的品牌调性。你需要确认语气、用词、价值观是否与品牌形象一致，必要时逐句调整。\\n\\n记住一个原则：**AI负责\\\"快\\\"，你负责\\\"好\\\"**。\\n\\n## 五、实战案例：从零到一完成一篇博客\\n\\n光说不练假把式，我们来看一个完整的实战过程。\\n\\n**场景设定：** 你需要在...[TRUNCATED]\",\"outline\":\"{\\n  \\\"title\\\": \\\"AI写作助手：让你的效率翻倍的5个秘诀\\\",\\n  \\\"sections\\\": [\\n    {\\n      \\\"heading\\\": \\\"引言：写作的痛点与AI的机遇\\\",\\n      \\\"points\\\": [\\n        \\\"职场写作的常见痛点：时间紧张、思路枯竭、表达不畅\\\",\\n        \\\"AI辅助写作的潜力：快速生成、优化表达、激发灵感\\\",\\n        \\\"本文目标：分享实用技巧，帮助读者高效利用AI工具\\\"\\n      ]\\n    },\\n    {\\n      \\\"heading\\\": \\\"选择合适的AI写作工具\\\",\\n      \\\"points\\\": [\\n        \\\"主流工具概览：ChatGPT、文心一言、Notion AI等\\\",\\n        \\\"根据需求选择：长文写作、短文案、邮件、报告等\\\",\\n        \\\"免费与付费工具的权衡：功能、限制与成本\\\"\\n      ]\\n    },\\n    {\\n      \\\"heading\\\": \\\"明确目标，提供清晰指令\\\",\\n      \\\"points\\\": [\\n        \\\"设定写作目标：受众、目的、风格、长度\\\",\\n        \\\"编写有效提示词：具体、详细、包含示例\\\",\\n        \\\"迭代优化：根据输出调整指令，逐步逼近理想结果\\\"\\n      ]\\n    },\\n    {\\n      \\\"heading\\\": \\\"利用AI进行内容生成与优化\\\",\\n      \\\"points\\\": [\\n        \\\"快速生成初稿：用AI搭建框架，填充内容\\\",\\n        \\\"优化表达：改写句子、调整语气、增强逻辑\\\",\\n        \\\"处理重复内容：AI帮你润色，让文字更生动\\\"\\n      ]\\n    },\\n    {\\n      \\\"heading\\\": \\\"结合人工编辑，确保质量\\\",\\n      \\\"points\\\": [\\n        \\\"AI输出的局限性：事实错误、逻辑漏洞、风格偏差\\\",\\n        \\\"人工审核的重要性：检查准确性、一致性和品牌调性\\\",\\n        \\\"修改技巧：保持个人声音，加入真实案例\\\"\\n      ]\\n    },\\n    {\\n      \\\"heading\\\": \\\"实战案例：从零到一完成一篇博客\\\",\\n      \\\"points\\\": [\\n        \\\"场景设定：写一篇关于远程工作的博客\\\",\\n        \\\"步骤演示：使用AI生成大纲、段落、修改建议\\\",\\n        \\\"效果对比：AI辅助前后的时间与质量差异\\\"\\n      ]\\n    },\\n    {\\n      \\\"heading\\\": \\\"常见误区与注意事项\\\",\\n      \\\"points\\\": [\\n        \\\"过度依赖AI：失去个人风格和批判性思维\\\",\\n        \\\"忽视事实核查：AI可能产生错误信息\\\",\\n        \\\"伦理与合规：引用、版权、隐私问题\\\"\\n      ]\\n    },\\n    {\\n      \\\"heading\\\": \\\"结语：拥抱AI，提升写作效率\\\",\\n      \\\"points\\\": [\\n        \\\"AI是工具，不是替代品\\\",\\n        \\\"持续学习：随着AI发展，不断调整方法\\\",\\n        \\\"鼓励实践：从今天开始尝试AI辅助写作\\\"\\n      ]\\n    }\\n  ],\\n  \\\"keyTakeaways\\\": [\\n    \\\"掌握选择和使用AI写作工具的技巧\\\",\\n    \\\"学会编写有效提示词，提高AI输出质量\\\",\\n    \\\"理解AI辅助写作的流程，包括生成、优化和人工编辑\\\",\\n    \\\"避免常见误区，确保内容的准确性和个人风格\\\",\\n    \\\"提升写作效率，将更多时间投入到创意和思考中\\\"\\n  ]\\n}\",\"targetAudience\":\"希望提升写作效率的职场人士\",\"wordCount\":1500,\"keyTakeaways\":[\"掌握选择和使用AI写作工具的技巧\",\"学会编写有效提示词，提高AI输出质量\",\"理解AI辅助写作的流程，包括生成、优化和人工编辑\",\"避免常见误区，确保内容的准确性和个人风格\",\"提升写作效率，将更多时间投入到创意和思考中\"],\"draft\":\"# AI写作助手：让你的效率翻倍的5个秘诀\\n\\n## 引言：写作的痛点与AI的机遇\\n\\n你有没有过这样的经历？打开一个空白文档，光标闪烁，大脑却一片空白。截止日期就在眼前，邮件还没回，报告还没写，方案还没憋出来——这是多少职场人的日常困境。时间紧张、思路枯竭、表达不畅，这三个词几乎概括了职场写作的全部痛点。\\n\\n但好消息是，AI写作工具正在改变这一切。它可以在几秒钟内生成一段结构完整的文字，帮你把\\\"车轱辘话\\\"变得精炼有力，甚至在你毫无头绪时给你一个全新的切入点。AI不是来取代你的，它是来帮你把省下来的时间用在更有价值的事情上——比如思考、创意、决策。\\n\\n这篇文章不会跟你讲什么高深的理论，而是分享五个实操性极强的秘诀，让你从今天开始就能把AI变成自己的写作加速器。\\n\\n## 选择合适的AI写作工具\\n\\n市面上的AI写作工具五花八门，选对工具等于成功了一半。目前主流的选项包括：ChatGPT（通用对话能力强，适合各类写作场景）、文心一言（中文理解优秀，对本土语境把握更准）、Notion AI（集成在笔记软件中，适合知识管理和长文写作）、Claude（长文本处理能力突出）等。\\n\\n怎么选？关键看你的核心需求。如果你主要写长文报告或深度分析，ChatGPT或Claude会更合适；如果你经常写短文案、营销话术，文心一言或Copy.ai这类工具可能更对口；如果你需要边整理资料边写作，Notion AI的无缝集成体验会让人上瘾。\\n\\n免费和付费怎么权衡？免费版通常有次数限制或功能阉割，但对于轻度使用者完全够用。如果你每周都要写大量内容，花几十块钱订阅付费版绝对物超所值——省下的时间成本远超订阅费用。\\n\\n## 明确目标，提供清晰指令\\n\\n很多人在用AI时觉得\\\"它不懂我\\\"，其实问题往往出在指令不够清晰。AI不是读心术师，你给它多模糊的指令，它就还你多模糊的答案。\\n\\n**第一步，设定写作目标。** 在提问前，先想清楚四个问题：读者是谁？写作目的是什么？期望的风格是什么？大概要多长？比如，与其说\\\"帮我写一封邮件\\\"，不如说\\\"帮我写一封给客户的道歉邮件，语气诚恳但不过度卑微，说明项目延期原因并提出补救方案，300字左右\\\"。\\n\\n**第二步，编写有效提示词。** 好的提示词要具体、详细、包含示例。你可以这样写：\\\"以产品经理的口吻，写一段200字的产品介绍，突出三个核心卖点：速度快、操作简单、性价比高。参考风格：Apple官网的产品文案，简洁有力。\\\"\\n\\n**第三步，迭代优化。** 第一次输出不满意？别急着换工具，试着追问\\\"能否更正式一些\\\"\\\"加一个数据支撑\\\"\\\"换个更有感染力的开头\\\"。AI的上下文记忆能力让你可以在对话中不断打磨，直到接近理想效果。\\n\\n## 利用AI进行内容生成与优化\\n\\nAI最强大的能力不是\\\"替你写\\\"，而是\\\"帮你写得更快更好\\\"。具体来说，有三个层面的应用：\\n\\n**快速生成初稿。** 面对空白页时，你可以让AI先搭一个框架。比如：\\\"给我一篇关于远程工作的博客大纲，包含引言、3-4个主体部分和结论。\\\"拿到框架后，再逐段让AI展开。这样你从\\\"从零开始\\\"变成了\\\"从半成品开始\\\"，心理压力小得多，效率也翻倍。\\n\\n**优化表达。** 你写了一段话但觉得不够好？直接丢给AI：\\\"帮我改写这段话，让语气更专业\\\"或\\\"把这段缩短一半，保留核心信息\\\"。AI在句式调整、语气转换、逻辑梳理方面非常擅长，尤其是那些你反复改了好几遍还是不满意的句子，AI往往能给你意想不到的惊喜。\\n\\n**处理重复内容。** 如果你经常要写类似的工作周报、月度总结、产品说明，AI简直是救星。把模板喂给AI，它就能根据每次提供的新信息自动生成新版本，你只需要微调即可。这不仅省时间，还保证了内容的一致性。\\n\\n## 结合人工编辑，确保质量\\n\\nAI再聪明，也有它的局限性。它可能会编造数据、混淆事实，或者写出逻辑不通的段落。更关键的是，AI没有你的经验、你的视角、你的情感——而这些恰恰是好文章的灵魂。\\n\\n所以，**AI输出永远只是初稿，不是终稿**。你需要做的是：\\n\\n**第一，事实核查。** AI生成的内容中涉及的数据、引用、案例，务必逐一核实。尤其是那些看起来很具体的数字和\\\"研究表明\\\"，很可能纯属虚构。\\n\\n**第二，保持个人声音。** 你的文章之所以有人读，是因为它有\\\"你\\\"的味道。AI写出来的东西往往四平八稳、缺乏个性。你需要在修改时加入自己的观点、经历和真实案例，让文字重新\\\"活\\\"起来。\\n\\n**第三，检查品牌一致性。** 如果你是代表公司发声，AI可能无法精准把握你的品牌调性。你需要确认语气、用词、价值观是否与品牌形象一致，必要时逐句调整。\\n\\n记住一个原则：**AI负责\\\"快\\\"，你负责\\\"好\\\"**。\\n\\n## 实战案例：从零到一完成一篇博客\\n\\n光说不练假把式，我们来看一个完整的实战过程。\\n\\n**场景设定：** 你需要在两小时内完...[TRUNCATED]\",\"topic\":\"如何高效使用 AI 辅助写作\",\"style\":\"轻松易懂\",\"title\":\"AI写作助手：让你的效率翻倍的5个秘诀\",\"sections\":[{\"heading\":\"引言：写作的痛点与AI的机遇\",\"points\":[\"职场写作的常见痛点：时间紧张、思路枯竭、表达不畅\",\"AI辅助写作的潜力：快速生成、优化表达、激发灵感\",\"本文目标：分享实用技巧，帮助读者高效利用AI工具\"]},{\"heading\":\"选择合适的AI写作工具\",\"points\":[\"主流工具概览：ChatGPT、文心一言、Notion AI等\",\"根据需求选择：长文写作、短文案、邮件、报告等\",\"免费与付费工具的权衡：功能、限制与成本\"]},{\"heading\":\"明确目标，提供清晰指令\",\"points\":[\"设定写作目标：受众、目的、风格、长度\",\"编写有效提示词：具体、详细、包含示例\",\"迭代优化：根据输出调整指令，逐步逼近理想结果\"]},{\"heading\":\"利用AI进行内容生成与优化\",\"points\":[\"快速生成初稿：用AI搭建框架，填充内容\",\"优化表达：改写句子、调整语气、增强逻辑\",\"处理重复内容：AI帮你润色，让文字更生动\"]},{\"heading\":\"结合人工编辑，确保质量\",\"points\":[\"AI输出的局限性：事实错误、逻辑漏洞、风格偏差\",\"人工审核的重要性：检查准确性、一致性和品牌调性\",\"修改技巧：保持个人声音，加入真实案例\"]},{\"heading\":\"实战案例：从零到一完成一篇博客\",\"points\":[\"场景设定：写一篇关于远程工作的博客\",\"步骤演示：使用AI生成大纲、段落、修改建议\",\"效果对比：AI辅助前后的时间与质量差异\"]},{\"heading\":\"常见误区与注意事项\",\"points\":[\"过度依赖AI：失去个人风格和批判性思维\",\"忽视事实核查：AI可能产生错误信息\",\"伦理与合规：引用、版权、隐私问题\"]},{\"heading\":\"结语：拥抱AI，提升写作效率\",\"points\":[\"AI是工具，不是替代品\",\"持续学习：随着AI发展，不断调整方法\",\"鼓励实践：从今天开始尝试AI辅助写作\"]}]}}', NULL, NULL, '2026-08-11 20:24:52', '2026-08-11 20:25:28', '2026-08-11 20:25:28', '2026-08-11 20:24:52', '2026-08-11 20:25:28');

-- ----------------------------
-- Table structure for ai_knowledge_base
-- ----------------------------
DROP TABLE IF EXISTS `ai_knowledge_base`;
CREATE TABLE `ai_knowledge_base`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '知识库ID',
  `kb_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '知识库编码，全局唯一，被 knowledge_search 工具与导入引用',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '知识库名称',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '描述',
  `embedding_provider` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'embedding 提供者编码（对应 EmbeddingProvider.code()）',
  `embedding_model` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'embedding 模型名称',
  `dimension` int(11) NULL DEFAULT NULL COMMENT '向量维度（须与 embedding 模型一致，读写前校验）',
  `metric` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'COSINE' COMMENT '相似度度量（COSINE/L2/IP）',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态：0=停用 1=启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_kb_code`(`kb_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI知识库表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of ai_knowledge_base
-- ----------------------------

-- ----------------------------
-- Table structure for ai_knowledge_chunk
-- ----------------------------
DROP TABLE IF EXISTS `ai_knowledge_chunk`;
CREATE TABLE `ai_knowledge_chunk`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '切片ID',
  `kb_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属知识库编码',
  `doc_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '来源文档标识',
  `chunk_index` int(11) NOT NULL DEFAULT 0 COMMENT '切片在文档内的序号',
  `content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '切片正文（真相源；向量存 Milvus）',
  `token_count` int(11) NULL DEFAULT NULL COMMENT '粗略 token 数（按字符估算）',
  `metadata` json NULL COMMENT '附加元数据（来源/标题/页码等，JSON对象）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_kb_doc`(`kb_code` ASC, `doc_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI知识库切片表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of ai_knowledge_chunk
-- ----------------------------

-- ----------------------------
-- Table structure for ai_knowledge_document
-- ----------------------------
DROP TABLE IF EXISTS `ai_knowledge_document`;
CREATE TABLE `ai_knowledge_document`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '文档ID',
  `kb_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '所属知识库编码',
  `doc_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文档标识，库内唯一',
  `title` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '标题',
  `source_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '来源类型（text/markdown/url 等）',
  `source_uri` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '来源地址（URL 或原始路径，可空）',
  `char_count` int(11) NULL DEFAULT 0 COMMENT '正文字符数',
  `chunk_count` int(11) NULL DEFAULT 0 COMMENT '切块数',
  `status` tinyint(1) NOT NULL DEFAULT 0 COMMENT '状态：0=索引中 1=完成 2=失败',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_kb_doc`(`kb_code` ASC, `doc_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI知识库文档表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of ai_knowledge_document
-- ----------------------------

-- ----------------------------
-- Table structure for ai_mcp_server
-- ----------------------------
DROP TABLE IF EXISTS `ai_mcp_server`;
CREATE TABLE `ai_mcp_server`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '服务器ID',
  `server_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '服务器编码，全局唯一，被流程/节点引用',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '服务器名称',
  `transport` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '传输类型：stdio/sse/streamable-http',
  `command` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '【stdio】启动命令（npx/uvx/python 等）',
  `args` json NULL COMMENT '【stdio】命令参数数组（JSON数组，如 [\"-y\",\"@scope/server\",\"/data\"]）',
  `env` json NULL COMMENT '【stdio】子进程环境变量（JSON对象，明文，敏感凭证请放 auth_token）',
  `url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '【远程】SSE/HTTP 端点地址',
  `headers` json NULL COMMENT '【远程】自定义请求头（JSON对象，明文非敏感部分）',
  `auth_token` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '【远程】鉴权凭证 Bearer/apiKey（AES加密密文存储，列宽含密文膨胀冗余）',
  `timeout_ms` int(11) NULL DEFAULT NULL COMMENT '连接/请求超时（毫秒）',
  `options` json NULL COMMENT '扩展参数，透传未来新增的 MCP 配置项（JSON对象）',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态：0=停用 1=启用',
  `remark` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_server_code`(`server_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI MCP服务器配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_mcp_server
-- ----------------------------

-- ----------------------------
-- Table structure for ai_memory
-- ----------------------------
DROP TABLE IF EXISTS `ai_memory`;
CREATE TABLE `ai_memory`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '记忆ID（作为 Milvus nebula_memory 的业务主键 pk）',
  `agent_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '归属Agent功能编码（记忆按Agent隔离，Milvus partitionKey）',
  `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '归属用户ID（记忆按用户隔离）',
  `conversation_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '关联会话ID（可空=跨会话）',
  `mem_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '记忆类型：EPISODIC/SEMANTIC/PROCEDURAL/ENTITY',
  `content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '记忆内容（真相源；mode=vector 时向量副本存 Milvus）',
  `metadata` json NULL COMMENT '扩展元数据（JSON对象）',
  `need_reindex` tinyint(1) NOT NULL DEFAULT 0 COMMENT '向量待重索引：0=向量已同步，1=向量写/删失败待对账补偿（批次4 need_reindex 对账）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_agent_user`(`agent_code` ASC, `user_id` ASC) USING BTREE,
  INDEX `idx_need_reindex`(`need_reindex` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI长期记忆表（db=LIKE关键词/vector=Milvus语义召回）' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of ai_memory
-- ----------------------------

-- ----------------------------
-- Table structure for ai_model_profile
-- ----------------------------
DROP TABLE IF EXISTS `ai_model_profile`;
CREATE TABLE `ai_model_profile`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '档案ID',
  `profile_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '档案编码，全局唯一，被流程/节点引用',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '档案名称',
  `provider` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '服务提供商标识（openai/deepseek 等）',
  `base_url` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'API基础地址',
  `api_key` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'API密钥（AES加密密文存储，列宽含密文膨胀冗余）',
  `model` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '模型名称',
  `temperature` double NULL DEFAULT NULL COMMENT '默认采样温度',
  `max_tokens` int(11) NULL DEFAULT NULL COMMENT '默认最大输出token数',
  `top_p` double NULL DEFAULT NULL COMMENT '默认Top P采样参数',
  `timeout_ms` int(11) NULL DEFAULT NULL COMMENT '请求超时（毫秒）',
  `options` json NULL COMMENT '扩展参数，透传厂商私有参数（response_format / frequency_penalty 等，JSON对象）',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态：0=停用 1=启用',
  `remark` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_profile_code`(`profile_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI模型档案表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_model_profile
-- ----------------------------
INSERT INTO `ai_model_profile` VALUES (1, 'DS-V3-001', 'DeepSeek-V3 标准版', 'deepseek', 'https://api.deepseek.com/v1', 'kur4IU7wtV1qY7TrtzunsMq+jB0+XW1vJL7PZil4s7qkBs5pW+XIDpMX1dqRkvKEh0l6FNTjMqJEoWpfszTU', 'deepseek-chat', 0.7, 4096, 0.9, 60000, '{\"response_format\": {\"type\": \"text\"}, \"frequency_penalty\": 0}', 1, 'DeepSeek-V3 通用对话模型，标准参数配置', '2026-06-29 16:31:49', '2026-06-29 16:31:49');
INSERT INTO `ai_model_profile` VALUES (2, 'DS-R1-002', 'DeepSeek-R1 推理增强版', 'deepseek', 'https://api.deepseek.com/v1', 'kur4IU7wtV1qY7TrtzunsMq+jB0+XW1vJL7PZil4s7qkBs5pW+XIDpMX1dqRkvKEh0l6FNTjMqJEoWpfszTU', 'deepseek-reasoner', 0.3, 8192, 0.95, 120000, '{\"response_format\": {\"type\": \"text\"}, \"frequency_penalty\": 0.2}', 1, 'DeepSeek-R1 推理模型，低温低随机性适合代码生成和逻辑推理', '2026-06-29 16:31:49', '2026-07-07 11:58:45');
INSERT INTO `ai_model_profile` VALUES (3, 'DS-V3-CREATIVE-003', 'DeepSeek-V3 创意写作版', 'deepseek', 'https://api.deepseek.com/v1', 'kur4IU7wtV1qY7TrtzunsMq+jB0+XW1vJL7PZil4s7qkBs5pW+XIDpMX1dqRkvKEh0l6FNTjMqJEoWpfszTU', 'deepseek-chat', 0.95, 6144, 0.85, 60000, '{\"response_format\": {\"type\": \"text\"}, \"presence_penalty\": 0.5, \"frequency_penalty\": 0.5}', 1, '高温高随机性，适合创意写作、头脑风暴等开放性任务', '2026-06-29 16:31:49', '2026-07-07 11:58:48');
INSERT INTO `ai_model_profile` VALUES (4, 'DS-V3-JSON-004', 'DeepSeek-V3 JSON输出版', 'deepseek', 'https://api.deepseek.com/v1', 'kur4IU7wtV1qY7TrtzunsMq+jB0+XW1vJL7PZil4s7qkBs5pW+XIDpMX1dqRkvKEh0l6FNTjMqJEoWpfszTU', 'deepseek-chat', 0, 4096, 1, 45000, '{\"response_format\": {\"type\": \"json_object\"}, \"frequency_penalty\": 0.0}', 1, '强制 JSON 格式输出，temperature=0 保证结果确定性', '2026-06-29 16:31:49', '2026-07-07 11:58:49');
INSERT INTO `ai_model_profile` VALUES (5, 'DS-TEST-005', 'DeepSeek 测试实例（停用）', 'deepseek', 'https://api.deepseek.com/v1', 'kur4IU7wtV1qY7TrtzunsMq+jB0+XW1vJL7PZil4s7qkBs5pW+XIDpMX1dqRkvKEh0l6FNTjMqJEoWpfszTU', 'deepseek-chat', 0.5, 2048, 0.9, 30000, '{\"response_format\": {\"type\": \"text\"}}', 1, '用于测试环境，默认停用状态', '2026-06-29 16:31:49', '2026-07-07 11:58:51');

-- ----------------------------
-- Table structure for ai_prompt
-- ----------------------------
DROP TABLE IF EXISTS `ai_prompt`;
CREATE TABLE `ai_prompt`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '提示词ID',
  `prompt_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '提示词编码，全局唯一，被流程/节点引用',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '提示词名称',
  `role` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'system' COMMENT '消息角色：system=系统设定 user=用户输入 assistant=助手示例',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '提示词正文，支持 {{变量名}} 占位符，渲染时由 variables 声明的变量替换',
  `variables` json NULL COMMENT '变量声明数组（JSON数组，如 [{\"name\":\"topic\",\"type\":\"string\",\"required\":true,\"description\":\"主题\"}]），编辑器据此渲染填充表单',
  `remark` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_prompt_code`(`prompt_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI提示词表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of ai_prompt
-- ----------------------------
INSERT INTO `ai_prompt` VALUES (1, 'BLOG_SERIES_OUTLINE', '博客系列大纲生成', 'user', '请围绕主题「{{topic}}」规划一个 {{count}} 篇的系列文章大纲，写作风格为 {{style}}。\n每篇给出标题与一句话简介，输出 JSON 数组。', '[{\"name\": \"topic\", \"type\": \"string\", \"required\": true, \"description\": \"系列主题\"}, {\"name\": \"count\", \"type\": \"integer\", \"required\": true, \"description\": \"文章篇数\"}, {\"name\": \"style\", \"type\": \"string\", \"required\": false, \"description\": \"写作风格，如实战驱动\"}]', '博客系列生成流程的大纲节点提示词', '2026-07-11 15:43:15', '2026-07-11 15:43:15');
INSERT INTO `ai_prompt` VALUES (2, 'TECH_WRITER_PERSONA', '技术写作者人设', 'system', '你是一位资深 Java 工程师兼技术博主，擅长用可运行的代码示例讲解原理。行文简洁、避免空话，每个概念都配一段完整可编译的示例代码。', NULL, '无变量的静态人设提示词，可被多个流程节点复用', '2026-07-11 15:43:15', '2026-07-11 15:43:15');

-- ----------------------------
-- Table structure for ai_relay_model
-- ----------------------------
DROP TABLE IF EXISTS `ai_relay_model`;
CREATE TABLE `ai_relay_model`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '模型ID',
  `code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模型编码（如 gpt-4o-mini / claude-3-5-sonnet）',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模型名称',
  `model_vendor` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '模型厂商（OpenAI/Anthropic/Google等）',
  `model_type` tinyint(4) NOT NULL DEFAULT 1 COMMENT '模型类型（1文本 2图像 3音频 4多模态 5Embedding）',
  `description` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '模型说明',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '展示排序',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态（1正常 0停用）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_code`(`code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 25 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI模型配置' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_model
-- ----------------------------
INSERT INTO `ai_relay_model` VALUES (18, 'gpt-5.4', 'gpt-5.4', 'OpenAI', 1, NULL, 0, 1, '2026-06-11 19:13:54', '2026-06-11 19:13:54');
INSERT INTO `ai_relay_model` VALUES (19, 'gpt-5.4-mini', 'gpt-5.4-mini', 'OpenAI', 1, NULL, 0, 1, '2026-06-11 19:14:15', '2026-06-11 19:14:15');
INSERT INTO `ai_relay_model` VALUES (20, 'gpt-5.4-openai-compact', 'gpt-5.4-openai-compact', 'OpenAI', 1, NULL, 0, 1, '2026-06-11 19:14:32', '2026-06-11 19:14:32');
INSERT INTO `ai_relay_model` VALUES (21, 'gpt-5.5', 'gpt-5.5', 'OpenAI', 1, NULL, 0, 1, '2026-06-11 19:14:49', '2026-06-11 19:14:49');
INSERT INTO `ai_relay_model` VALUES (22, 'claude-haiku-4.5', 'claude-haiku-4.5', 'Anthropic', 1, NULL, 0, 1, '2026-06-11 19:15:12', '2026-06-11 19:15:12');
INSERT INTO `ai_relay_model` VALUES (23, 'claude-sonnet-4.6', 'claude-sonnet-4.6', 'Anthropic', 1, NULL, 0, 1, '2026-06-11 19:19:12', '2026-06-11 19:19:12');
INSERT INTO `ai_relay_model` VALUES (24, 'claude-opus-4.8', 'claude-opus-4.8', 'Anthropic', 1, NULL, 0, 1, '2026-06-11 19:19:46', '2026-06-11 19:19:46');

-- ----------------------------
-- Table structure for ai_relay_package_model
-- ----------------------------
DROP TABLE IF EXISTS `ai_relay_package_model`;
CREATE TABLE `ai_relay_package_model`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '套餐模型ID',
  `package_id` bigint(20) NOT NULL COMMENT '套餐ID（ai_relay_provider_package.id）',
  `model_id` bigint(20) NOT NULL COMMENT '模型ID（ai_relay_model.id）',
  `provider_model_code` varchar(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '服务商侧模型编码，不填则默认使用模型编码',
  `consume_multiplier` decimal(8, 4) NOT NULL DEFAULT 1.0000 COMMENT '消耗倍率，如1.5表示消耗额度*1.5',
  `min_charge_amount` decimal(18, 6) NULL DEFAULT NULL COMMENT '最低扣费额度',
  `max_context_tokens` int(11) NULL DEFAULT NULL COMMENT '最大上下文Token数',
  `input_price_per_million_tokens` decimal(12, 4) NULL DEFAULT NULL COMMENT '输入Token单价（每百万Token），币种沿用套餐currency，跨套餐比价用',
  `output_price_per_million_tokens` decimal(12, 4) NULL DEFAULT NULL COMMENT '输出Token单价（每百万Token），币种沿用套餐currency，跨套餐比价用',
  `is_default` tinyint(4) NOT NULL DEFAULT 0 COMMENT '是否默认模型（1是 0否）',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '展示排序',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态（1正常 0停用）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_package_model`(`package_id` ASC, `model_id` ASC) USING BTREE,
  INDEX `idx_package_id`(`package_id` ASC) USING BTREE,
  INDEX `idx_model_id`(`model_id` ASC) USING BTREE,
  INDEX `idx_package_status_sort`(`package_id` ASC, `status` ASC, `sort_order` ASC) USING BTREE,
  INDEX `idx_model_input_price`(`model_id` ASC, `status` ASC, `input_price_per_million_tokens` ASC) USING BTREE,
  INDEX `idx_model_output_price`(`model_id` ASC, `status` ASC, `output_price_per_million_tokens` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转套餐支持模型及消耗倍率' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_package_model
-- ----------------------------
INSERT INTO `ai_relay_package_model` VALUES (1, 25, 24, NULL, 2.3000, NULL, NULL, 11.5000, 57.5000, 0, 0, 1, '2026-06-11 19:21:05', '2026-06-11 19:21:05');
INSERT INTO `ai_relay_package_model` VALUES (2, 25, 23, NULL, 2.3000, NULL, NULL, 11.5000, 57.5000, 0, 0, 1, '2026-06-11 19:30:43', '2026-06-11 19:30:43');
INSERT INTO `ai_relay_package_model` VALUES (3, 25, 22, NULL, 2.3000, NULL, NULL, 2.3000, 11.5000, 0, 0, 1, '2026-06-11 19:31:30', '2026-06-11 19:31:30');

-- ----------------------------
-- Table structure for ai_relay_package_type
-- ----------------------------
DROP TABLE IF EXISTS `ai_relay_package_type`;
CREATE TABLE `ai_relay_package_type`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '套餐类型ID',
  `code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '套餐类型编码（day/week/month/usage等）',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '套餐类型名称（天卡/周卡/月卡/按量）',
  `billing_mode` tinyint(4) NOT NULL COMMENT '计费模式（1固定周期 2按量计费）',
  `duration_value` int(11) NULL DEFAULT NULL COMMENT '套餐周期数值，如1、7、30，按量计费可为空',
  `duration_unit` tinyint(4) NULL DEFAULT NULL COMMENT '周期单位（1天 2周 3月 4年），按量计费可为空',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '类型说明',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '展示排序',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态（1正常 0停用）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_code`(`code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转套餐类型配置' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_package_type
-- ----------------------------
INSERT INTO `ai_relay_package_type` VALUES (6, 'PAYGO', '按量', 2, NULL, NULL, NULL, 0, 1, '2026-06-11 19:10:36', '2026-06-11 19:10:36');

-- ----------------------------
-- Table structure for ai_relay_payment_method
-- ----------------------------
DROP TABLE IF EXISTS `ai_relay_payment_method`;
CREATE TABLE `ai_relay_payment_method`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '支付方式ID',
  `code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '支付方式编码（alipay/wechat/paypal/usdt等）',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '支付方式名称（支付宝/微信/PayPal/USDT等）',
  `icon_file_id` bigint(20) NULL DEFAULT NULL COMMENT '支付方式图标文件ID（sys_file）',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '支付方式说明',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '展示排序',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态（1正常 0停用）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_code`(`code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 8 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转支付方式配置' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_payment_method
-- ----------------------------
INSERT INTO `ai_relay_payment_method` VALUES (6, 'wechat', '微信', NULL, NULL, 0, 1, '2026-06-11 19:20:14', '2026-06-11 19:20:14');
INSERT INTO `ai_relay_payment_method` VALUES (7, 'zhifubao', '支付宝', NULL, NULL, 0, 1, '2026-06-11 19:20:30', '2026-06-11 19:20:30');

-- ----------------------------
-- Table structure for ai_relay_provider
-- ----------------------------
DROP TABLE IF EXISTS `ai_relay_provider`;
CREATE TABLE `ai_relay_provider`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '服务商ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '服务商名称（如 OpenRouter）',
  `website_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '官网地址',
  `logo_file_id` bigint(20) NULL DEFAULT NULL COMMENT 'Logo文件ID（sys_file）',
  `description` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '服务商简介',
  `recommend_score` decimal(5, 2) NOT NULL DEFAULT 0.00 COMMENT '综合推荐分（核心排序依据）',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '展示排序',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态（1正常 0下线）',
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
INSERT INTO `ai_relay_provider` VALUES (6, 'ikuncode', 'https://api.ikuncode.cc/', NULL, NULL, 0.00, 0, 1, '2026-06-11 17:57:08', '2026-06-11 17:57:10', '2026-06-11 17:57:10');

-- ----------------------------
-- Table structure for ai_relay_provider_advantage
-- ----------------------------
DROP TABLE IF EXISTS `ai_relay_provider_advantage`;
CREATE TABLE `ai_relay_provider_advantage`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '优势ID',
  `provider_id` bigint(20) NOT NULL COMMENT '服务商ID（ai_relay_provider.id）',
  `title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '优势标题',
  `content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '优势说明',
  `advantage_type` tinyint(4) NOT NULL DEFAULT 1 COMMENT '优势类型（1普通优势 2核心优势 3风险提示）',
  `icon_file_id` bigint(20) NULL DEFAULT NULL COMMENT '优势图标文件ID（sys_file）',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '展示排序',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态（1正常 0停用）',
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
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '套餐ID',
  `provider_id` bigint(20) NOT NULL COMMENT '服务商ID（ai_relay_provider.id）',
  `package_type_id` bigint(20) NOT NULL COMMENT '套餐类型ID（ai_relay_package_type.id）',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '套餐名称',
  `price` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '套餐价格',
  `original_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '原价/划线价',
  `currency` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'CNY' COMMENT '币种（CNY/USD等）',
  `is_recommended` tinyint(4) NOT NULL DEFAULT 0 COMMENT '是否推荐（1是 0否）',
  `recommend_score` decimal(5, 2) NOT NULL DEFAULT 0.00 COMMENT '套餐推荐分',
  `description` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '套餐说明',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '展示排序',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态（1正常 0下线）',
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
INSERT INTO `ai_relay_provider_package` VALUES (25, 6, 6, 'Claude Code-稳定', 0.00, NULL, 'CNY', 0, 0.00, NULL, 0, 1, '2026-06-11 19:11:39', '2026-06-11 19:11:39');

-- ----------------------------
-- Table structure for ai_relay_provider_package_limit
-- ----------------------------
DROP TABLE IF EXISTS `ai_relay_provider_package_limit`;
CREATE TABLE `ai_relay_provider_package_limit`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '套餐限制ID',
  `package_id` bigint(20) NOT NULL COMMENT '套餐ID（ai_relay_provider_package.id）',
  `limit_type` tinyint(4) NOT NULL COMMENT '限制类型（1总额度 2每日额度 3每周额度 4每月额度 5单次额度）',
  `quota_amount` decimal(18, 6) NOT NULL COMMENT '额度数量',
  `quota_unit` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '额度单位（token/request/credit等）',
  `reset_cycle` tinyint(4) NOT NULL DEFAULT 0 COMMENT '重置周期（0不重置 1每日 2每周 3每月 4套餐周期）',
  `over_limit_strategy` tinyint(4) NOT NULL DEFAULT 1 COMMENT '超限策略（1禁止使用 2按量计费 3限速）',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '限制说明',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态（1正常 0停用）',
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
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `provider_id` bigint(20) NOT NULL COMMENT '服务商ID（ai_relay_provider.id）',
  `payment_method_id` bigint(20) NOT NULL COMMENT '支付方式ID（ai_relay_payment_method.id）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注说明',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '展示排序',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态（1正常 0停用）',
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
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '充值记录ID',
  `provider_id` bigint(20) NOT NULL COMMENT '服务商ID（ai_relay_provider.id）',
  `package_id` bigint(20) NULL DEFAULT NULL COMMENT '套餐ID（ai_relay_provider_package.id），按量计费/直充可空',
  `amount` decimal(10, 2) NOT NULL COMMENT '充值金额',
  `currency` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'CNY' COMMENT '币种（CNY/USD等）',
  `exchange_rate` decimal(10, 4) NULL DEFAULT NULL COMMENT '汇率（非CNY时折算汇率）',
  `cny_amount` decimal(10, 2) NULL DEFAULT NULL COMMENT '折合人民币金额',
  `payment_method_id` bigint(20) NULL DEFAULT NULL COMMENT '支付方式ID（ai_relay_payment_method.id）',
  `recharge_time` datetime NOT NULL COMMENT '充值时间',
  `order_no` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '订单号/交易流水号',
  `voucher_file_id` bigint(20) NULL DEFAULT NULL COMMENT '充值凭证文件ID（sys_file）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注说明',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态（1正常 0作废）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_provider_id`(`provider_id` ASC) USING BTREE,
  INDEX `idx_package_id`(`package_id` ASC) USING BTREE,
  INDEX `idx_recharge_time`(`recharge_time` ASC) USING BTREE,
  INDEX `idx_provider_recharge_time`(`provider_id` ASC, `recharge_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI中转服务商个人充值记录（用于佐证推荐真实性）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_relay_provider_recharge
-- ----------------------------

-- ----------------------------
-- Table structure for ai_relay_provider_recommend
-- ----------------------------
DROP TABLE IF EXISTS `ai_relay_provider_recommend`;
CREATE TABLE `ai_relay_provider_recommend`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '推荐ID',
  `provider_id` bigint(20) NOT NULL COMMENT '服务商ID（ai_relay_provider.id）',
  `recommend_reason` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '推荐原因（精简一句话/摘要，用于列表展示）',
  `review_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '完整测评内容（支持Markdown）',
  `review_score` decimal(3, 1) NULL DEFAULT NULL COMMENT '个人测评评分（0-10分）',
  `pros` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '优点（多个用换行/分号分隔）',
  `cons` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '缺点（多个用换行/分号分隔）',
  `use_scenario` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '推荐使用场景',
  `first_use_time` datetime NULL DEFAULT NULL COMMENT '首次使用时间',
  `review_time` datetime NULL DEFAULT NULL COMMENT '测评时间',
  `recommend_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '推荐时间',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '展示排序',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态（1正常 0下线）',
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
-- Table structure for ai_tool
-- ----------------------------
DROP TABLE IF EXISTS `ai_tool`;
CREATE TABLE `ai_tool`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '工具ID',
  `tool_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '工具编码，全局唯一，= 代码 ToolDefinition.code()，被流程节点引用',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '工具显示名',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用途描述（兼作 LLM function description）',
  `category` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '分类（http/data/search 等），编辑器分组用',
  `params_schema` json NULL COMMENT '入参 JSON Schema，声明工具所需参数/类型/必填项，编辑器据此渲染参数表单',
  `result_schema` json NULL COMMENT '出参结构描述（可空），给下游节点提示产物形状',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用：0=已下线(代码中已移除) 1=启用',
  `builtin` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否内置工具：1=代码内置(由同步器维护) 0=外部登记(预留扩展位)',
  `sort_no` int(11) NOT NULL DEFAULT 0 COMMENT '排序号',
  `remark` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_tool_code`(`tool_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 22 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI工具定义表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_tool
-- ----------------------------
INSERT INTO `ai_tool` VALUES (1, 'echo', '回声', '把入参 text 原样返回，用于流程联调与工具机制示例', 'util', '{\"type\": \"object\", \"required\": [\"text\"], \"properties\": {\"text\": {\"type\": \"string\", \"description\": \"待回显的文本\"}}}', NULL, 1, 1, 0, NULL, '2026-07-01 09:58:19', '2026-07-01 09:58:19');
INSERT INTO `ai_tool` VALUES (2, 'http', 'HTTP请求', '向指定 URL 发起 HTTP 请求，支持请求方式/头/查询参数/请求体/超时与失败重试，返回状态码、响应头与响应体', 'http', '{\"type\": \"object\", \"required\": [\"url\"], \"properties\": {\"url\": {\"type\": \"string\", \"description\": \"请求地址（必填）\"}, \"body\": {\"type\": \"object\", \"description\": \"请求体；字符串原样发送，对象序列化为 JSON\"}, \"query\": {\"type\": \"object\", \"description\": \"查询参数键值对，拼接到 URL\"}, \"method\": {\"type\": \"string\", \"description\": \"请求方式：GET/POST/PUT/DELETE/PATCH，默认 GET\"}, \"headers\": {\"type\": \"object\", \"description\": \"请求头键值对\"}, \"timeoutMs\": {\"type\": \"integer\", \"description\": \"单次调用整体超时（毫秒），覆盖默认\"}, \"maxRetries\": {\"type\": \"integer\", \"description\": \"失败重试次数，覆盖默认\"}, \"contentType\": {\"type\": \"string\", \"description\": \"请求体 Content-Type，默认 application/json\"}}}', '{\"type\": \"object\", \"properties\": {\"body\": {\"type\": \"string\", \"description\": \"响应体文本\"}, \"status\": {\"type\": \"integer\", \"description\": \"HTTP 状态码\"}, \"headers\": {\"type\": \"object\", \"description\": \"响应头\"}}}', 1, 1, 10, NULL, '2026-07-01 09:58:19', '2026-07-01 09:58:19');
INSERT INTO `ai_tool` VALUES (3, 'list_node_types', '列出双引擎节点类型', '按 DAG 或 STATE_MACHINE 返回权威执行节点类型、图语义角色和约束；不传 engineType 时返回两组供选型。', 'copilot', '{\"type\": \"object\", \"properties\": {\"engineType\": {\"enum\": [\"DAG\", \"STATE_MACHINE\"], \"type\": \"string\"}}, \"additionalProperties\": false}', NULL, 1, 1, 10, NULL, '2026-07-22 14:56:10', '2026-07-22 14:56:10');
INSERT INTO `ai_tool` VALUES (4, 'list_tools', '列出可用业务工具', '列出可在 TOOL 节点引用的已启用业务工具（toolCode + 用途 + 入参 schema）。当流程需要调用工具时先调用本工具获取合法 toolCode。', 'copilot', '{\"type\": \"object\", \"properties\": {\"keyword\": {\"type\": \"string\", \"description\": \"可选：按工具编码/名称/描述过滤的关键词\"}}}', NULL, 1, 1, 11, NULL, '2026-07-22 14:56:10', '2026-07-22 14:56:10');
INSERT INTO `ai_tool` VALUES (5, 'list_model_profiles', '列出模型档案', '列出可用的模型档案（profileCode + 名称 + provider + model）。为流程或节点选择 defaultProfileCode/profileCode 时先调用本工具获取合法编码。', 'copilot', '{\"type\": \"object\", \"properties\": {}}', NULL, 1, 1, 12, NULL, '2026-07-22 14:56:10', '2026-07-22 14:56:10');
INSERT INTO `ai_tool` VALUES (6, 'generate_flow', '生成流程并落库', '根据结构化的流程定义创建一条 AI 流程并落库，成功返回 flowCode 与 version。nodeCode 在流程内唯一；edges 用 nodeCode 连接；PROMPT 节点必须提供 promptTemplate；至少包含一个 START 节点和一个 END 节点。若 flowCode 已存在会被拒绝，请换一个新的 flowCode。', 'copilot', '{\"type\": \"object\", \"required\": [\"flowCode\", \"nodes\", \"edges\"], \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"流程名称\"}, \"edges\": {\"type\": \"array\", \"items\": {\"type\": \"object\", \"required\": [\"fromNode\", \"toNode\"], \"properties\": {\"toNode\": {\"type\": \"string\", \"description\": \"终点节点编码\"}, \"fromNode\": {\"type\": \"string\", \"description\": \"起点节点编码\"}, \"conditionExpr\": {\"type\": \"string\", \"description\": \"出边条件（SpEL），IF 分流用；空表示无条件直达。空表达式的边作为 default\"}}}, \"description\": \"有向边列表，用 nodeCode 连接节点\"}, \"nodes\": {\"type\": \"array\", \"items\": {\"type\": \"object\", \"required\": [\"nodeCode\", \"nodeType\"], \"properties\": {\"x\": {\"type\": \"number\", \"description\": \"画布横坐标，沿主流程从左到右递增（如 120,360,600...）\"}, \"y\": {\"type\": \"number\", \"description\": \"画布纵坐标，同层节点用相同值\"}, \"name\": {\"type\": \"string\", \"description\": \"节点显示名\"}, \"model\": {\"type\": \"string\", \"description\": \"模型名称（覆盖档案），一般留空\"}, \"nodeCode\": {\"type\": \"string\", \"description\": \"节点编码，流程内唯一（= 画布节点ID）\"}, \"nodeType\": {\"enum\": [\"PROMPT\", \"TOOL\", \"START\", \"END\", \"IF\", \"JOIN\", \"LOOP\", \"AGENT\", \"AGENT_REACT\"], \"type\": \"string\", \"description\": \"节点类型，取值见 list_node_types\"}, \"toolCode\": {\"type\": \"string\", \"description\": \"TOOL 节点引用的工具编码，从 list_tools 选取；仅 TOOL 节点需要\"}, \"outputKey\": {\"type\": \"string\", \"description\": \"产物写回上下文的键名，供下游节点用 #{key} 引用\"}, \"outputMode\": {\"enum\": [\"TEXT\", \"JSON\"], \"type\": \"string\", \"default\": \"TEXT\", \"description\": \"产物模式：TEXT 整段写入 / JSON 解析后逐键展开\"}, \"profileCode\": {\"type\": \"string\", \"description\": \"引用的模型档案编码，空则用流程默认档案\"}, \"temperature\": {\"type\": \"number\", \"description\": \"采样温度，一般留空\"}, \"inputMapping\": {\"type\": \"object\", \"description\": \"输入映射：模板变量名 -> 上游上下文键（一般可省略，同名自动匹配）\"}, \"systemPrompt\": {\"type\": \"string\", \"description\": \"系统提示词模板，可空，#{var} 引用上下文键\"}, \"promptTemplate\": {\"type\": \"string\", \"description\": \"用户提示词模板，PROMPT 节点必填，#{var} 引用上游节点 outputKey\"}}}, \"description\": \"节点列表；至少包含一个 START 和一个 END 节点\"}, \"version\": {\"type\": \"integer\", \"default\": 1, \"description\": \"版本号，默认 1\"}, \"flowCode\": {\"type\": \"string\", \"description\": \"流程编码，全局唯一（英文/下划线）。若已存在会被拒绝，请用新编码。\"}, \"engineType\": {\"enum\": [\"DAG\", \"STATE_MACHINE\"], \"type\": \"string\", \"default\": \"DAG\", \"description\": \"执行内核：DAG（默认，一次跑完）或 STATE_MACHINE（可回跳/成环/挂起）\"}, \"description\": {\"type\": \"string\", \"description\": \"流程描述\"}, \"defaultProfileCode\": {\"type\": \"string\", \"description\": \"默认模型档案编码，从 list_model_profiles 选取；节点未指定档案时使用\"}}}', NULL, 0, 1, 20, NULL, '2026-07-22 14:56:10', '2026-08-11 15:22:50');
INSERT INTO `ai_tool` VALUES (7, 'derive_agent', '派生 Agent', '基于一个已存在的流程（flowCode）派生一个 Agent 定义（ai_agent），成功返回 agentCode 与 id。需先有已提交的 flowCode。agentCode 是跨版本稳定的记忆隔离键，须唯一。', 'copilot', '{\"type\": \"object\", \"required\": [\"agentCode\", \"flowCode\"], \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"Agent 名称\"}, \"flowCode\": {\"type\": \"string\", \"description\": \"引用的已提交流程编码\"}, \"agentCode\": {\"type\": \"string\", \"description\": \"Agent 编码，全局唯一（英文/下划线），是记忆隔离键，创建后不建议改\"}, \"description\": {\"type\": \"string\", \"description\": \"Agent 描述\"}, \"flowVersion\": {\"type\": \"integer\", \"default\": 1, \"description\": \"引用的流程版本，默认 1\"}, \"inputSchema\": {\"type\": \"string\", \"description\": \"输入契约 JSON Schema 文本，可空\"}, \"memoryConfig\": {\"type\": \"string\", \"description\": \"记忆配置 JSON 文本（enabled / 导入键 / 导出策略），可空\"}, \"outputSchema\": {\"type\": \"string\", \"description\": \"输出契约 JSON Schema 文本，可空\"}, \"defaultProfileCode\": {\"type\": \"string\", \"description\": \"默认模型档案编码，可空\"}}}', NULL, 1, 1, 21, NULL, '2026-07-22 14:56:10', '2026-07-22 14:56:10');
INSERT INTO `ai_tool` VALUES (8, 'create_draft', '创建流程草稿', '创建空草稿。engineType 必填且创建后不可修改；flowCode 可空，提交前再补齐。', 'copilot', '{\"type\": \"object\", \"required\": [\"engineType\"], \"properties\": {\"name\": {\"type\": \"string\"}, \"flowCode\": {\"type\": \"string\"}, \"engineType\": {\"enum\": [\"DAG\", \"STATE_MACHINE\"], \"type\": \"string\"}, \"description\": {\"type\": \"string\"}}, \"additionalProperties\": false}', NULL, 1, 1, 20, NULL, '2026-08-11 15:22:50', '2026-08-11 15:22:50');
INSERT INTO `ai_tool` VALUES (9, 'update_draft_metadata', '更新草稿元数据', '更新 name、description、flowCode、defaultProfileCode 或 maxTransitions；engineType 不可修改。', 'copilot', '{\"type\": \"object\", \"required\": [\"draftId\", \"expectedRevision\", \"patch\"], \"properties\": {\"patch\": {\"type\": \"object\"}, \"draftId\": {\"type\": \"string\"}, \"clearFields\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}}, \"expectedRevision\": {\"type\": \"integer\"}}, \"additionalProperties\": false}', NULL, 1, 1, 30, NULL, '2026-08-11 15:22:50', '2026-08-11 15:22:50');
INSERT INTO `ai_tool` VALUES (10, 'add_node', '添加草稿节点', '添加单个节点。PROMPT/AGENT_REACT 必须同时给出模型档案、系统提示词、用户提示词与输出配置；状态机字段保持扁平，服务端会转换到 stateType 与 nodeConfig.stateConfig。', 'copilot', '{\"type\": \"object\", \"required\": [\"draftId\", \"expectedRevision\", \"nodeCode\", \"nodeType\"], \"properties\": {\"name\": {\"type\": \"string\"}, \"stop\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}}, \"topP\": {\"type\": \"number\"}, \"model\": {\"type\": \"string\"}, \"apiKey\": {\"type\": \"string\"}, \"sortNo\": {\"type\": \"integer\"}, \"baseUrl\": {\"type\": \"string\"}, \"draftId\": {\"type\": \"string\"}, \"onError\": {\"enum\": [\"FAIL\", \"CONTINUE\", \"GOTO_STATE\"], \"type\": \"string\"}, \"options\": {\"type\": \"object\"}, \"retryOn\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}}, \"suspend\": {\"type\": \"boolean\"}, \"nodeCode\": {\"type\": \"string\"}, \"nodeType\": {\"type\": \"string\"}, \"provider\": {\"type\": \"string\"}, \"backoffMs\": {\"type\": \"integer\"}, \"maxTokens\": {\"type\": \"integer\", \"description\": \"模型最大输出 token 数，必须为正整数\"}, \"outputKey\": {\"type\": \"string\", \"description\": \"节点产物写回上下文的语义化变量名\"}, \"stateType\": {\"enum\": [\"ENTRY\", \"NORMAL\", \"TERMINAL\"], \"type\": \"string\"}, \"timeoutMs\": {\"type\": \"integer\"}, \"errorState\": {\"type\": \"string\"}, \"nodeConfig\": {\"type\": \"object\"}, \"outputMode\": {\"enum\": [\"TEXT\", \"JSON\"], \"type\": \"string\", \"description\": \"模型输出模式；JSON 模式必须在提示词中明确 JSON 结构\"}, \"maxAttempts\": {\"type\": \"integer\"}, \"profileCode\": {\"type\": \"string\", \"description\": \"PROMPT/AGENT_REACT 使用的模型档案编码，必须来自 list_model_profiles\"}, \"temperature\": {\"type\": \"number\", \"description\": \"模型采样温度，范围 0 到 2\"}, \"inputMapping\": {\"type\": \"object\", \"description\": \"模板变量名到上下文键的映射\"}, \"systemPrompt\": {\"type\": \"string\", \"description\": \"PROMPT/AGENT_REACT 的系统提示词：稳定角色、规则和输出约束\"}, \"rememberTrace\": {\"type\": \"boolean\"}, \"awaitingEvents\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}}, \"promptTemplate\": {\"type\": \"string\", \"description\": \"PROMPT/AGENT_REACT 必填的用户提示词模板；用 {{变量名}} 引用上下文\"}, \"stateTimeoutMs\": {\"type\": \"integer\"}, \"expectedRevision\": {\"type\": \"integer\"}}, \"additionalProperties\": false}', NULL, 1, 1, 40, NULL, '2026-08-11 15:22:50', '2026-08-11 15:22:50');
INSERT INTO `ai_tool` VALUES (11, 'update_node', '更新草稿节点', '更新一个节点的指定字段。nodeCode 不可改名；显式清空字段必须使用 clearFields。', 'copilot', '{\"type\": \"object\", \"required\": [\"draftId\", \"expectedRevision\", \"nodeCode\", \"patch\"], \"properties\": {\"patch\": {\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\"}, \"stop\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}}, \"topP\": {\"type\": \"number\"}, \"model\": {\"type\": \"string\"}, \"apiKey\": {\"type\": \"string\"}, \"sortNo\": {\"type\": \"integer\"}, \"baseUrl\": {\"type\": \"string\"}, \"onError\": {\"enum\": [\"FAIL\", \"CONTINUE\", \"GOTO_STATE\"], \"type\": \"string\"}, \"options\": {\"type\": \"object\"}, \"retryOn\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}}, \"suspend\": {\"type\": \"boolean\"}, \"nodeType\": {\"type\": \"string\"}, \"provider\": {\"type\": \"string\"}, \"backoffMs\": {\"type\": \"integer\"}, \"maxTokens\": {\"type\": \"integer\", \"description\": \"模型最大输出 token 数，必须为正整数\"}, \"outputKey\": {\"type\": \"string\", \"description\": \"节点产物写回上下文的语义化变量名\"}, \"stateType\": {\"enum\": [\"ENTRY\", \"NORMAL\", \"TERMINAL\"], \"type\": \"string\"}, \"timeoutMs\": {\"type\": \"integer\"}, \"errorState\": {\"type\": \"string\"}, \"nodeConfig\": {\"type\": \"object\"}, \"outputMode\": {\"enum\": [\"TEXT\", \"JSON\"], \"type\": \"string\", \"description\": \"模型输出模式；JSON 模式必须在提示词中明确 JSON 结构\"}, \"maxAttempts\": {\"type\": \"integer\"}, \"profileCode\": {\"type\": \"string\", \"description\": \"PROMPT/AGENT_REACT 使用的模型档案编码，必须来自 list_model_profiles\"}, \"temperature\": {\"type\": \"number\", \"description\": \"模型采样温度，范围 0 到 2\"}, \"inputMapping\": {\"type\": \"object\", \"description\": \"模板变量名到上下文键的映射\"}, \"systemPrompt\": {\"type\": \"string\", \"description\": \"PROMPT/AGENT_REACT 的系统提示词：稳定角色、规则和输出约束\"}, \"rememberTrace\": {\"type\": \"boolean\"}, \"awaitingEvents\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}}, \"promptTemplate\": {\"type\": \"string\", \"description\": \"PROMPT/AGENT_REACT 必填的用户提示词模板；用 {{变量名}} 引用上下文\"}, \"stateTimeoutMs\": {\"type\": \"integer\"}}, \"additionalProperties\": false}, \"draftId\": {\"type\": \"string\"}, \"nodeCode\": {\"type\": \"string\"}, \"clearFields\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}}, \"expectedRevision\": {\"type\": \"integer\"}}, \"additionalProperties\": false}', NULL, 1, 1, 50, NULL, '2026-08-11 15:22:50', '2026-08-11 15:22:50');
INSERT INTO `ai_tool` VALUES (12, 'remove_node', '删除草稿节点', '删除一个节点并自动清理全部关联边，响应中返回被清理边的摘要。', 'copilot', '{\"type\": \"object\", \"required\": [\"draftId\", \"expectedRevision\", \"nodeCode\"], \"properties\": {\"draftId\": {\"type\": \"string\"}, \"nodeCode\": {\"type\": \"string\"}, \"expectedRevision\": {\"type\": \"integer\"}}, \"additionalProperties\": false}', NULL, 1, 1, 60, NULL, '2026-08-11 15:22:50', '2026-08-11 15:22:50');
INSERT INTO `ai_tool` VALUES (13, 'connect', '连接草稿节点', '创建一条有向边，可附带 SpEL conditionExpr、状态机 eventName 和裁决 sortNo。', 'copilot', '{\"type\": \"object\", \"required\": [\"draftId\", \"expectedRevision\", \"fromNode\", \"toNode\"], \"properties\": {\"sortNo\": {\"type\": \"integer\"}, \"toNode\": {\"type\": \"string\"}, \"draftId\": {\"type\": \"string\"}, \"fromNode\": {\"type\": \"string\"}, \"eventName\": {\"type\": \"string\"}, \"conditionExpr\": {\"type\": \"string\"}, \"expectedRevision\": {\"type\": \"integer\"}}, \"additionalProperties\": false}', NULL, 1, 1, 70, NULL, '2026-08-11 15:22:50', '2026-08-11 15:22:50');
INSERT INTO `ai_tool` VALUES (14, 'disconnect', '断开草稿边', '按 fromNode+toNode 及可选 sortNo/conditionExpr/eventName 精确删除一条边；歧义时不会删除。', 'copilot', '{\"type\": \"object\", \"required\": [\"draftId\", \"expectedRevision\", \"fromNode\", \"toNode\"], \"properties\": {\"sortNo\": {\"type\": \"integer\"}, \"toNode\": {\"type\": \"string\"}, \"draftId\": {\"type\": \"string\"}, \"fromNode\": {\"type\": \"string\"}, \"eventName\": {\"type\": \"string\"}, \"conditionExpr\": {\"type\": \"string\"}, \"expectedRevision\": {\"type\": \"integer\"}}, \"additionalProperties\": false}', NULL, 1, 1, 80, NULL, '2026-08-11 15:22:50', '2026-08-11 15:22:50');
INSERT INTO `ai_tool` VALUES (15, 'read_draft', '读取流程草稿', '默认读取全图索引摘要；传 nodeCodes 获取节点详情，边始终按 edgePage/edgePageSize 分页。', 'copilot', '{\"type\": \"object\", \"required\": [\"draftId\"], \"properties\": {\"draftId\": {\"type\": \"string\"}, \"edgePage\": {\"type\": \"integer\"}, \"nodeCodes\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}}, \"edgePageSize\": {\"type\": \"integer\"}}, \"additionalProperties\": false}', NULL, 1, 1, 90, NULL, '2026-08-11 15:22:50', '2026-08-11 15:22:50');
INSERT INTO `ai_tool` VALUES (16, 'validate_draft', '校验流程草稿', '校验指定 revision 的结构、数据流、条件语法和引擎编译结果；ERROR 阻断提交，WARN 不阻断。', 'copilot', '{\"type\": \"object\", \"required\": [\"draftId\", \"expectedRevision\"], \"properties\": {\"draftId\": {\"type\": \"string\"}, \"expectedRevision\": {\"type\": \"integer\"}}, \"additionalProperties\": false}', NULL, 1, 1, 100, NULL, '2026-08-11 15:22:50', '2026-08-11 15:22:50');
INSERT INTO `ai_tool` VALUES (17, 'simulate_draft', '模拟流程草稿', '重新校验指定 revision，并用根据入口节点输入契约生成的 initialInput 做零 token、零外部副作用模拟；无声明入参时显式传空对象，报告明确标记 guard 假设。', 'copilot', '{\"type\": \"object\", \"required\": [\"draftId\", \"expectedRevision\", \"initialInput\"], \"properties\": {\"draftId\": {\"type\": \"string\"}, \"initialInput\": {\"type\": \"object\", \"description\": \"根据 START/ENTRY 节点 nodeConfig.inputs 生成的测试参数；无入参时传空对象\", \"additionalProperties\": true}, \"expectedRevision\": {\"type\": \"integer\"}}, \"additionalProperties\": false}', NULL, 1, 1, 105, NULL, '2026-08-11 15:22:50', '2026-08-11 15:22:50');
INSERT INTO `ai_tool` VALUES (18, 'commit_draft', '提交流程草稿', '默认要求当前 revision 已完成无 ERROR 模拟，再重新校验并原子创建正式流程；flowCode 已存在时绝不覆盖。', 'copilot', '{\"type\": \"object\", \"required\": [\"draftId\", \"expectedRevision\"], \"properties\": {\"draftId\": {\"type\": \"string\"}, \"expectedRevision\": {\"type\": \"integer\"}}, \"additionalProperties\": false}', NULL, 1, 1, 110, NULL, '2026-08-11 15:22:50', '2026-08-11 15:22:50');
INSERT INTO `ai_tool` VALUES (19, 'real_run_draft', '真实试跑流程草稿', '真实调用模型、工具和子 Agent 验收当前 revision；首次执行及失败重试都先请求用户确认，initialInput 必须根据入口节点输入契约生成，同一 revision 可持续重试直到成功。', 'copilot', '{\"type\": \"object\", \"required\": [\"draftId\", \"expectedRevision\", \"initialInput\"], \"properties\": {\"draftId\": {\"type\": \"string\"}, \"initialInput\": {\"type\": \"object\", \"description\": \"根据 START/ENTRY 节点 nodeConfig.inputs 生成的测试参数；无入参时传空对象\", \"additionalProperties\": true}, \"expectedRevision\": {\"type\": \"integer\"}}, \"additionalProperties\": false}', NULL, 1, 1, 110, NULL, '2026-08-11 15:22:50', '2026-08-11 15:22:50');
INSERT INTO `ai_tool` VALUES (20, 'get_harness_operation', '查询 Harness 操作', '按 operationId 查询 PENDING/RUNNING/SUCCEEDED/FAILED/UNKNOWN 状态和安全结果摘要。', 'copilot', '{\"type\": \"object\", \"required\": [\"operationId\"], \"properties\": {\"operationId\": {\"type\": \"string\"}}, \"additionalProperties\": false}', NULL, 1, 1, 115, NULL, '2026-08-11 15:22:50', '2026-08-11 15:22:50');
INSERT INTO `ai_tool` VALUES (21, 'inspect_context', '查询节点可用上下文', '返回该节点可在 promptTemplate/systemPrompt 中引用的变量：startInputs 为流程入参，guaranteed 为必然可用，conditional 仅部分分支可用，referenced 为当前模板已引用的变量。写模板前调用可避免变量名猜错。', 'copilot', '{\"type\": \"object\", \"required\": [\"draftId\", \"nodeCode\"], \"properties\": {\"draftId\": {\"type\": \"string\"}, \"nodeCode\": {\"type\": \"string\"}}, \"additionalProperties\": false}', NULL, 1, 1, 95, NULL, '2026-08-11 17:24:39', '2026-08-11 17:24:39');

-- ----------------------------
-- Table structure for ai_webhook_delivery
-- ----------------------------
DROP TABLE IF EXISTS `ai_webhook_delivery`;
CREATE TABLE `ai_webhook_delivery`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `delivery_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '投递唯一标识（业务键，重发幂等键之一）',
  `instance_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '来源实例',
  `chain_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '来源迭代链（若来自链）',
  `seq` int(11) NULL DEFAULT NULL COMMENT '迭代链第几轮（幂等：chainId+seq 唯一定位一篇产物）',
  `agent_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `node_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '节点级回调才有',
  `event` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'ITERATION_ADVANCED | INSTANCE_SUCCESS | INSTANCE_FAILED | NODE_SUCCESS | NODE_FAILED',
  `url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '发去哪',
  `mode` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'INLINE' COMMENT 'INLINE(当场发) | DEFER(线程池发)；仅发送时机，与可靠性无关',
  `payload` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '发了什么(JSON，重发直接读它，不重跑 Agent)；不含鉴权 headers',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING | SUCCESS | FAILED | DEAD',
  `attempts` int(11) NOT NULL DEFAULT 0 COMMENT '已尝试次数',
  `max_attempts` int(11) NOT NULL DEFAULT 8 COMMENT '重发上限，达到后置 DEAD 不再自动重发',
  `response_code` int(11) NULL DEFAULT NULL COMMENT '最后一次 HTTP 响应码',
  `last_error` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '最后一次失败原因',
  `next_retry_at` datetime NULL DEFAULT NULL COMMENT '下次重发时间（FAILED 且未达上限时，按指数退避）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_delivery_id`(`delivery_id` ASC) USING BTREE,
  INDEX `idx_chain_seq`(`chain_id` ASC, `seq` ASC) USING BTREE,
  INDEX `idx_retry`(`status` ASC, `next_retry_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 14 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '编排回调投递记录（日志 + 断点续发）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ai_webhook_delivery
-- ----------------------------
INSERT INTO `ai_webhook_delivery` VALUES (8, 'blog_series_writer-chain-demo02-7', 'blog_series-be4a49bafb4e4c30aa40f01f65e23369', 'blog_series_writer-chain-demo02', 7, 'blog_series', NULL, 'ITERATION_ADVANCED', 'http://127.0.0.1:8082/blog/admin/blog/webhook/series-append', 'INLINE', '{\"deliveryId\":\"blog_series_writer-chain-demo02-7\",\"event\":\"ITERATION_ADVANCED\",\"chainId\":\"blog_series_writer-chain-demo02\",\"seq\":7,\"agentCode\":\"blog_series\",\"instanceId\":\"blog_series-be4a49bafb4e4c30aa40f01f65e23369\",\"context\":{\"isSeriesFinished\":false,\"prevSeq\":6,\"updatedHistory\":\"第7篇：反射与注解——框架的基石\",\"article\":{\"title\":\"第7篇：反射与注解——框架的基石\",\"content\":\"## 前言\\n在之前的文章中，我们学习了异常处理、集合框架、I/O流、多线程、网络编程和JDBC，逐步构建了一个功能完整的银行系统。然而，随着系统复杂度的提升，我们开始思考：如何让代码更灵活、更可扩展？如何让框架自动完成配置和依赖注入？答案就是Java的反射与注解。反射让程序在运行时动态获取类的信息并操作对象，注解则为代码添加元数据，两者结合成为Spring、MyBatis等框架的基石。本文将通过银行系统的实际场景，带你掌握反射与注解的核心用法。\\n\\n## 1. 反射机制概述\\n反射（Reflection）是Java语言的一种特性，允许程序在运行时获取任意类的内部信息（如构造方法、字段、方法），并能操作这些成员。反射的核心类位于`java.lang.reflect`包中，包括`Class`、`Constructor`、`Field`、`Method`等。\\n\\n### 1.1 获取Class对象\\n获取`Class`对象有三种方式：\\n- 通过`Class.forName(\\\"全限定类名\\\")`\\n- 通过`对象.getClass()`\\n- 通过`类名.class`\\n\\n```java\\n// 银行账户类\\npublic class BankAccount {\\n    private String accountNo;\\n    private double balance;\\n    \\n    public BankAccount(String accountNo, double balance) {\\n        this.accountNo = accountNo;\\n        this.balance = balance;\\n    }\\n    \\n    public void deposit(double amount) {\\n        balance += amount;\\n    }\\n    \\n    public void withdraw(double amount) {\\n        if (balance >= amount) {\\n            balance -= amount;\\n        }\\n    }\\n    \\n    public double getBalance() {\\n        return balance;\\n    }\\n}\\n\\n// 获取Class对象\\nClass<?> clazz1 = Class.forName(\\\"com.bank.model.BankAccount\\\");\\nClass<?> clazz2 = new BankAccount(\\\"1001\\\", 5000).getClass();\\nClass<?> clazz3 = BankAccount.class;\\n```\\n\\n### 1.2 操作构造方法\\n通过反射创建对象：\\n```java\\nConstructor<?> constructor = clazz1.getConstructor(String.class, double.class);\\nObject account = constructor.newInstance(\\\"1002\\\", 10000.0);\\nSystem.out.println(((BankAccount) account).getBalance()); // 输出 10000.0\\n```\\n\\n### 1.3 操作字段\\n访问和修改私有字段：\\n```java\\nField balanceField = clazz1.getDeclaredField(\\\"balance\\\");\\nbalanceField.setAccessible(true); // 绕过访问检查\\nbalanceField.set(account, 20000.0);\\nSystem.out.println(balanceField.get(account)); // 输出 20000.0\\n```\\n\\n### 1.4 操作方法\\n调用方法：\\n```java\\nMethod depositMethod = clazz1.getMethod(\\\"deposit\\\", double.class);\\ndepositMethod.invoke(account, 5000.0);\\nSystem.out.println(((BankAccount) account).getBalance()); // 输出 25000.0\\n```\\n\\n## 2. 注解基础\\n注解（Annotation）是Java 5引入的一种元数据形式，用于为代码添加额外的信息。注解本身不改变程序的行为，但可以通过反射读取并处理。\\n\\n### 2.1 内置注解\\n- `@Override`：标识方法重写\\n- `@Deprecated`：标记已过时\\n- `@SuppressWarnings`：抑制编译器警告\\n- `@FunctionalInterface`：函数式接口\\n\\n### 2.2 自定义注解\\n以银行系统中的字段校验为例，自定义一个`@NotNull`注解：\\n```java\\nimport java.lang.annotation.ElementType;\\nimport java.lang.annotation.Retention;\\nimport java.lang.annotation.RetentionPolicy;\\nimport java.lang.annotation.Target;\\n\\n@Target(ElementType.FIELD) // 作用于字段\\n@Retention(RetentionPolicy.RUNTIME) // 运行时保留\\npublic @interface NotNull {\\n    String message() default \\\"字段不能为空\\\";\\n}\\n```\\n\\n### 2.3 元注解\\n- `@Target`：指定注解可应用的元素类型（如方法、字段、类）\\n- `@Retention`：指定注解保留策略（SOURCE、CLASS、RUNTIME）\\n- `@Documented`：是否包含在Javadoc中\\n- `@Inherited`：是否允许子类继承\\n\\n## 3. 反射与注解实战：银行系统字段校验器\\n在银行系统中，我们需要确保账户信息、交易记录等数据的合法性。通过反射和注解，可以构建一个通用的字段校验器。\\n\\n### 3.1 定义校验注解\\n```java\\n@Target(ElementType.FIELD)\\n@Retention(RetentionPolicy.RUNTIME)\\npublic @interface NotNull {\\n    String message() default \\\"字段不能为空\\\";\\n}\\n\\n@Target(ElementType.FIELD)\\n@Retention(RetentionPolicy.RUNTIME)\\npublic @interface Min {\\n    double value();\\n    String message() default \\\"值不能小于最小值\\\";\\n}\\n\\n@Target(ElementType.FIELD)\\n@Retention(RetentionPolicy.RUNTIME)\\npublic @interface Max {\\n    double value();\\n    String message() default \\\"值不能大于最大值\\\";\\n}\\n```\\n\\n### 3.2 应用注解到实体类\\n```java\\npublic class Transaction {\\n    @NotNull(message = \\\"交易ID不能为空\\\")\\n    private String transactionId;\\n    \\n    @NotNull(message = \\\"账户号不能为空\\\")\\n    private String accountNo;\\n    \\n    @Min(value = 0.01, message = \\\"交易金额必须大于0\\\")\\n    @Max(value = 100000, message = \\\"单笔交易金额不能超过10万\\\")\\n    private double amount;\\n    \\n    // 构造方法、getter/setter省略\\n}\\n```\\n\\n### 3.3 实现校验器\\n```java\\nimport java.lang.reflect.Field;\\nimport java.util.ArrayList;\\nimport java.util.List;\\n\\npublic class Validator {\\n    public static List<String> validate(Object obj) throws IllegalAccessException {\\n        List<String> errors = new ArrayList<>();\\n        Class<?> clazz = obj.getClass();\\n        Field[] fields = clazz.getDeclaredFields();\\n        for (Field field : fields) {\\n            field.setAccessible(true);\\n            Object value = field.get(obj);\\n            \\n            // 检查 @NotNull\\n            if (field.isAnnotationPresent(NotNull.class)) {\\n                NotNull annotation = field.getAnnotation(NotNull.class);\\n                if (value == null) {\\n                    errors.add(annotation.message());\\n                }\\n            }\\n            \\n            // 检查 @Min 和 @Max\\n            if (value instanceof Number) {\\n                double num = ((Number) value).doubleValue();\\n                if (field.isAnnotationPresent(Min.class)) {\\n                    Min annotation = field.getAnnotation(Min.class);\\n                    if (num < annotation.value()) {\\n                        errors.add(annotation.message());\\n                    }\\n                }\\n                if (field.isAnnotationPresent(Max.class)) {\\n                    Max annotation = field.getAnnotation(Max.class);\\n                    if (num > annotation.value()) {\\n                        errors.add(annotation.message());\\n                    }\\n                }\\n            }\\n        }\\n        return errors;\\n    }\\n}\\n```\\n\\n### 3.4 使用校验器\\n```java\\nTransaction tx = new Transaction(null, \\\"1001\\\", 200000);\\nList<String> errors = Validator.validate(tx);\\nif (!errors.isEmpty()) {\\n    errors.forEach(System.out::println);\\n    // 输出：\\n    // 交易ID不能为空\\n    // 单笔交易金额不能超过10万\\n}\\n```\\n\\n## 4. 反射与注解在框架中的应用\\n### 4.1 Spring中的依赖注入（@Autowired）\\nSpring通过反射读取`@Autowired`注解，自动为字段注入依赖对象。\\n```java\\n@Component\\npublic class TransferService {\\n    @Autowired\\n    private AccountRepository accountRepository;\\n    // ...\\n}\\n```\\n\\n### 4.2 MyBatis中的ORM映射（@Select、@Insert）\\nMyBatis通过反射解析`@Select`等注解，动态生成SQL并映射结果。\\n```java\\npublic interface AccountMapper {\\n    @Select(\\\"SELECT * FROM accounts WHERE account_no = #{accountNo}\\\")\\n    Account findByAccountNo(String accountNo);\\n}\\n```\\n\\n## 5. 性能与安全注意事项\\n- **性能开销**：反射比直接调用慢，建议在框架初始化阶段使用，避免频繁调用。\\n- **安全限制**：反射可以访问私有成员，可能破坏封装性，需谨慎使用。\\n- **注解保留策略**：只有`RUNTIME`级别的注解才能被反射读取。\\n\\n## 6. 总结\\n本文通过银行系统的字段校验器案例，深入讲解了反射与注解的核心概念及实战用法。反射让程序动态化，注解让代码更简洁，两者结合是构建灵活框架的基础。掌握这些知识，你将能更好地理解Spring、MyBatis等框架的工作原理，并为后续学习Spring Boot、MyBatis等打下坚实基础。\\n\\n下一篇我们将学习Lambda表达式与Stream API，用函数式编程简化集合操作，敬请期待！\",\"examples\":[\"银行账户类反射操作\",\"自定义校验注解（@NotNull、@Min、@Max）\",\"通用字段校验器实现\"],\"topics\":[\"反射机制\",\"Class对象获取\",\"构造方法/字段/方法操作\",\"注解定义与元注解\",\"反射与注解结合实战\",\"框架中的应用\"]},\"updatedUsedExamples\":[\"银行账户系统（异常处理）\",\"文件读取（try-with-resources）\",\"自定义异常类\",\"银行账户类反射操作\",\"自定义校验注解（@NotNull、@Min、@Max）\",\"通用字段校验器实现\"],\"outline\":[\"第1篇：异常处理与日志记录——银行系统的稳健之道\",\"第2篇：集合框架——从数组到泛型集合的进化\",\"第3篇：Java I/O流——文件读写与序列化\",\"第4篇：多线程基础——银行系统的并发挑战\",\"第5篇：网络编程——Socket通信与RMI\",\"第6篇：JDBC与数据库操作——持久化银行数据\",\"第7篇：反射与注解——框架的基石\",\"第8篇：Lambda表达式与Stream API——函数式编程实战\",\"第9篇：设计模式——单例、工厂与观察者\",\"第10篇：JVM内存模型与性能调优\",\"第11篇：单元测试与Mock——JUnit与Mockito\",\"第12篇：构建工具——Maven与Gradle\",\"第13篇：版本控制——Git进阶\",\"第14篇：Spring基础——依赖注入与AOP\",\"第15篇：Spring Boot——快速搭建银行系统\",\"第16篇：MyBatis——ORM实战\",\"第17篇：RESTful API设计\",\"第18篇：微服务入门——Spring Cloud\",\"第19篇：Docker容器化部署\",\"第20篇：持续集成/持续部署——Jenkins\",\"第21篇：消息队列——RabbitMQ与Kafka\",\"第22篇：缓存技术——Redis\",\"第23篇：搜索引擎——Elasticsearch\",\"第24篇：安全机制——认证与授权\",\"第25篇：性能测试——JMeter\",\"第26篇：日志聚合——ELK Stack\",\"第27篇：监控与告警——Prometheus与Grafana\",\"第28篇：分布式事务——Seata\",\"第29篇：容器编排——Kubernetes\",\"第30篇：项目总结——银行系统完整实现\"],\"topic\":\"30天Java进阶\",\"updatedCoveredTopics\":[\"异常体系\",\"try-catch-finally\",\"多重捕获\",\"try-with-resources\",\"自定义异常\",\"SLF4J与Logback日志\",\"日志级别\",\"最佳实践\",\"反射机制\",\"Class对象获取\",\"构造方法/字段/方法操作\",\"注解定义与元注解\",\"反射与注解结合实战\",\"框架中的应用\"],\"accumulated\":{\"usedExamples\":[\"银行账户系统（异常处理）\",\"文件读取（try-with-resources）\",\"自定义异常类\"],\"coveredTopics\":[\"异常体系\",\"try-catch-finally\",\"多重捕获\",\"try-with-resources\",\"自定义异常\",\"SLF4J与Logback日志\",\"日志级别\",\"最佳实践\"],\"style\":\"实战驱动，结合银行系统示例，强调异常处理与日志记录的最佳实践。\"},\"seriesState\":\"{\\n  \\\"article\\\": {\\n    \\\"title\\\": \\\"第7篇：反射与注解——框架的基石\\\",\\n    \\\"content\\\": \\\"## 前言\\\\n在之前的文章中，我们学习了异常处理、集合框架、I/O流、多线程、网络编程和JDBC，逐步构建了一个功能完整的银行系统。然而，随着系统复杂度的提升，我们开始思考：如何让代码更灵活、更可扩展？如何让框架自动完成配置和依赖注入？答案就是Java的反射与注解。反射让程序在运行时动态获取类的信息并操作对象，注解则为代码添加元数据，两者结合成为Spring、MyBatis等框架的基石。本文将通过银行系统的实际场景，带你掌握反射与注解的核心用法。\\\\n\\\\n## 1. 反射机制概述\\\\n反射（Reflection）是Java语言的一种特性，允许程序在运行时获取任意类的内部信息（如构造方法、字段、方法），并能操作这些成员。反射的核心类位于`java.lang.reflect`包中，包括`Class`、`Constructor`、`Field`、`Method`等。\\\\n\\\\n### 1.1 获取Class对象\\\\n获取`Class`对象有三种方式：\\\\n- 通过`Class.forName(\\\\\\\"全限定类名\\\\\\\")`\\\\n- 通过`对象.getClass()`\\\\n- 通过`类名.class`\\\\n\\\\n```java\\\\n// 银行账户类\\\\npublic class BankAccount {\\\\n    private String accountNo;\\\\n    private double balance;\\\\n    \\\\n    public BankAccount(String accountNo, double balance) {\\\\n        this.accountNo = accountNo;\\\\n        this.balance = balance;\\\\n    }\\\\n    \\\\n    public void deposit(double amount) {\\\\n        balance += amount;\\\\n    }\\\\n    \\\\n    public void withdraw(double amount) {\\\\n        if (balance >= amount) {\\\\n            balance -= amount;\\\\n        }\\\\n    }\\\\n    \\\\n    public double getBalance() {\\\\n        return balance;\\\\n    }\\\\n}\\\\n\\\\n// 获取Class对象\\\\nClass<?> clazz1 = Class.forName(\\\\\\\"com.bank.model.BankAccount\\\\\\\");\\\\nClass<?> clazz2 = new BankAccount(\\\\\\\"1001\\\\\\\", 5000).getClass();\\\\nClass<?> clazz3 = BankAccount.class;\\\\n```\\\\n\\\\n### 1.2 操作构造方法\\\\n通过反射创建对象：\\\\n```java\\\\nConstructor<?> constructor = clazz1.getConstructor(String.class, double.class);\\\\nObject account = constructor.newInstance(\\\\\\\"1002\\\\\\\", 10000.0);\\\\nSystem.out.println(((BankAccount) account).getBalance()); // 输出 10000.0\\\\n```\\\\n\\\\n### 1.3 操作字段\\\\n访问和修改私有字段：\\\\n```java\\\\nField balanceField = clazz1.getDeclaredField(\\\\\\\"balance\\\\\\\");\\\\nbalanceField.setAccessible(true); // 绕过访问检查\\\\nbalanceField.set(account, 20000.0);\\\\nSystem.out.println(balanceField.get(account)); // 输出 20000.0\\\\n```\\\\n\\\\n### 1.4 操作方法\\\\n调用方法：\\\\n```java\\\\nMethod depositMethod = clazz1.getMethod(\\\\\\\"deposit\\\\\\\", double.class);\\\\ndepositMethod.invoke(account, 5000.0);\\\\nSystem.out.println(((BankAccount) account).getBalance()); // 输出 25000.0\\\\n```\\\\n\\\\n## 2. 注解基础\\\\n注解（Annotation）是Java 5引入的一种元数据形式，用于为代码添加额外的信息。注解本身不改变程序的行为，但可以通过反射读取并处理。\\\\n\\\\n### 2.1 内置注解\\\\n- `@Override`：标识方法重写\\\\n- `@Deprecated`：标记已过时\\\\n- `@SuppressWarnings`：抑制编译器警告\\\\n- `@FunctionalInterface`：函数式接口\\\\n\\\\n### 2.2 自定义注解\\\\n以银行系统中的字段校验为例，自定义一个`@NotNull`注解：\\\\n```java\\\\nimport java.lang.annotation.ElementType;\\\\nimport java.lang.annotation.Retention;\\\\nimport java.lang.annotation.RetentionPolicy;\\\\nimport java.lang.annotation.Target;\\\\n\\\\n@Target(ElementType.FIELD) // 作用于字段\\\\n@Retention(RetentionPolicy.RUNTIME) // 运行时保留\\\\npublic @interface NotNull {\\\\n    String message() default \\\\\\\"字段不能为空\\\\\\\";\\\\n}\\\\n```\\\\n\\\\n### 2.3 元注解\\\\n- `@Target`：指定注解可应用的元素类型（如方法、字段、类）\\\\n- `@Retention`：指定注解保留策略（SOURCE、CLASS、RUNTIME）\\\\n- `@Documented`：是否包含在Javadoc中\\\\n- `@Inherited`：是否允许子类继承\\\\n\\\\n## 3. 反射与注解实战：银行系统字段校验器\\\\n在银行系统中，我们需要确保账户信息、交易记录等数据的合法性。通过反射和注解，可以构建一个通用的字段校验器。\\\\n\\\\n### 3.1 定义校验注解\\\\n```java\\\\n@Target(ElementType.FIELD)\\\\n@Retention(RetentionPolicy.RUNTIME)\\\\npublic @interface NotNull {\\\\n    String message() default \\\\\\\"字段不能为空\\\\\\\";\\\\n}\\\\n\\\\n@Target(ElementType.FIELD)\\\\n@Retention(RetentionPolicy.RUNTIME)\\\\npublic @interface Min {\\\\n    double value();\\\\n    String message() default \\\\\\\"值不能小于最小值\\\\\\\";\\\\n}\\\\n\\\\n@Target(ElementType.FIELD)\\\\n@Retention(RetentionPolicy.RUNTIME)\\\\npublic @interface Max {\\\\n    double value();\\\\n    String message() default \\\\\\\"值不能大于最大值\\\\\\\";\\\\n}\\\\n```\\\\n\\\\n### 3.2 应用注解到实体类\\\\n```java\\\\npublic class Transaction {\\\\n    @NotNull(message = \\\\\\\"交易ID不能为空\\\\\\\")\\\\n    private String transactionId;\\\\n    \\\\n    @NotNull(message = \\\\\\\"账户号不能为空\\\\\\\")\\\\n    private String accountNo;\\\\n    \\\\n    @Min(value = 0.01, message = \\\\\\\"交易金额必须大于0\\\\\\\")\\\\n    @Max(value = 100000, message = \\\\\\\"单笔交易金额不能超过10万\\\\\\\")\\\\n    private double amount;\\\\n    \\\\n    // 构造方法、getter/setter省略\\\\n}\\\\n```\\\\n\\\\n### 3.3 实现校验器\\\\n```java\\\\nimport java.lang.reflect.Field;\\\\nimport java.util.ArrayList;\\\\nimport java.util.List;\\\\n\\\\npublic class Validator {\\\\n    public static List<String> validate(Object obj) throws IllegalAccessException {\\\\n        List<String> errors = new ArrayList<>();\\\\n        Class<?> clazz = obj.getClass();\\\\n        Field[] fields = clazz.getDeclaredFields();\\\\n        for (Field field : fields) {\\\\n            field.setAccessible(true);\\\\n            Object value = field.get(obj);\\\\n            \\\\n            // 检查 @NotNull\\\\n            if (field.isAnnotationPresent(NotNull.class)) {\\\\n                NotNull annotation = field.getAnnotation(NotNull.class);\\\\n                if (value == null) {\\\\n                    errors.add(annotation.message());\\\\n                }\\\\n            }\\\\n            \\\\n            // 检查 @Min 和 @Max\\\\n            if (value instanceof Number) {\\\\n                double num = ((Number) value).doubleValue();\\\\n                if (field.isAnnotationPresent(Min.class)) {\\\\n                    Min annotation = field.getAnnotation(Min.class);\\\\n                    if (num < annotation.value()) {\\\\n                        errors.add(annotation.message());\\\\n                    }\\\\n                }\\\\n                if (field.isAnnotationPresent(Max.class)) {\\\\n                    Max annotation = field.getAnnotation(Max.class);\\\\n                    if (num > annotation.value()) {\\\\n                        errors.add(annotation.message());\\\\n                    }\\\\n                }\\\\n            }\\\\n        }\\\\n        return errors;\\\\n    }\\\\n}\\\\n```\\\\n\\\\n### 3.4 使用校验器\\\\n```java\\\\nTransaction tx = new Transaction(null, \\\\\\\"1001\\\\\\\", 200000);\\\\nList<String> errors = Validator.validate(tx);\\\\nif (!errors.isEmpty()) {\\\\n    errors.forEach(System.out::println);\\\\n    // 输出：\\\\n    // 交易ID不能为空\\\\n    // 单笔交易金额不能超过10万\\\\n}\\\\n```\\\\n\\\\n## 4. 反射与注解在框架中的应用\\\\n### 4.1 Spring中的依赖注入（@Autowired）\\\\nSpring通过反射读取`@Autowired`注解，自动为字段注入依赖对象。\\\\n```java\\\\n@Component\\\\npublic class TransferService {\\\\n    @Autowired\\\\n    private AccountRepository accountRepository;\\\\n    // ...\\\\n}\\\\n```\\\\n\\\\n### 4.2 MyBatis中的ORM映射（@Select、@Insert）\\\\nMyBatis通过反射解析`@Select`等注解，动态生成SQL并映射结果。\\\\n```java\\\\npublic interface AccountMapper {\\\\n    @Select(\\\\\\\"SELECT * FROM accounts WHERE account_no = #{accountNo}\\\\\\\")\\\\n    Account findByAccountNo(String accountNo);\\\\n}\\\\n```\\\\n\\\\n## 5. 性能与安全注意事项\\\\n- **性能开销**：反射比直接调用慢，建议在框架初始化阶段使用，避免频繁调用。\\\\n- **安全限制**：反射可以访问私有成员，可能破坏封装性，需谨慎使用。\\\\n- **注解保留策略**：只有`RUNTIME`级别的注解才能被反射读取。\\\\n\\\\n## 6. 总结\\\\n本文通过银行系统的字段校验器案例，深入讲解了反射与注解的核心概念及实战用法。反射让程序动态化，注解让代码更简洁，两者结合是构建灵活框架的基础。掌握这些知识，你将能更好地理解Spring、MyBatis等框架的工作原理，并为后续学习Spring Boot、MyBatis等打下坚实基础。\\\\n\\\\n下一篇我们将学习Lambda表达式与Stream API，用函数式编程简化集合操作，敬请期待！\\\",\\n    \\\"examples\\\": [\\\"银行账户类反射操作\\\", \\\"自定义校验注解（@NotNull、@Min、@Max）\\\", \\\"通用字段校验器实现\\\"],\\n    \\\"topics\\\": [\\\"反射机制\\\", \\\"Class对象获取\\\", \\\"构造方法/字段/方法操作\\\", \\\"注解定义与元注解\\\", \\\"反射与注解结合实战\\\", \\\"框架中的应用\\\"]\\n  },\\n  \\\"updatedHistory\\\": \\\"第7篇：反射与注解——框架的基石\\\",\\n  \\\"updatedUsedExamples\\\": [\\\"银行账户系统（异常处理）\\\", \\\"文件读取（try-with-resources）\\\", \\\"自定义异常类\\\", \\\"银行账户类反射操作\\\", \\\"自定义校验注解（@NotNull、@Min、@Max）\\\", \\\"通用字段校验器实现\\\"],\\n  \\\"updatedCoveredTopics\\\": [\\\"异常体系\\\", \\\"try-catch-finally\\\", \\\"多重捕获\\\", \\\"try-with-resources\\\", \\\"自定义异常\\\", \\\"SLF4J与Logback日志\\\", \\\"日志级别\\\", \\\"最佳实践\\\", \\\"反射机制\\\", \\\"Class对象获取\\\", \\\"构造方法/字段/方法操作\\\", \\\"注解定义与元注解\\\", \\\"反射与注解结合实战\\\", \\\"框架中的应用\\\"],\\n  \\\"isSeriesFinished\\\": false\\n}\",\"seriesName\":\"30天Java进阶\"}}', 'SUCCESS', 1, 8, 200, NULL, NULL, '2026-07-09 11:59:01', '2026-07-09 11:59:01');
INSERT INTO `ai_webhook_delivery` VALUES (9, 'blog_series_writer-chain-demo02-8', 'blog_series-7ec632e835954611b658c0218d2b2847', 'blog_series_writer-chain-demo02', 8, 'blog_series', NULL, 'ITERATION_ADVANCED', 'http://127.0.0.1:8082/blog/admin/blog/webhook/series-append', 'INLINE', '{\"deliveryId\":\"blog_series_writer-chain-demo02-8\",\"event\":\"ITERATION_ADVANCED\",\"chainId\":\"blog_series_writer-chain-demo02\",\"seq\":8,\"agentCode\":\"blog_series\",\"instanceId\":\"blog_series-7ec632e835954611b658c0218d2b2847\",\"context\":{\"outline\":[\"第1篇：异常处理与日志记录——银行系统的稳健之道\",\"第2篇：集合框架——从数组到泛型集合的进化\",\"第3篇：Java I/O流——文件读写与序列化\",\"第4篇：多线程基础——银行系统的并发挑战\",\"第5篇：网络编程——Socket通信与RMI\",\"第6篇：JDBC与数据库操作——持久化银行数据\",\"第7篇：反射与注解——框架的基石\",\"第8篇：Lambda表达式与Stream API——函数式编程实战\",\"第9篇：设计模式——单例、工厂与观察者\",\"第10篇：JVM内存模型与性能调优\",\"第11篇：单元测试与Mock——JUnit与Mockito\",\"第12篇：构建工具——Maven与Gradle\",\"第13篇：版本控制——Git进阶\",\"第14篇：Spring基础——依赖注入与AOP\",\"第15篇：Spring Boot——快速搭建银行系统\",\"第16篇：MyBatis——ORM实战\",\"第17篇：RESTful API设计\",\"第18篇：微服务入门——Spring Cloud\",\"第19篇：Docker容器化部署\",\"第20篇：持续集成/持续部署——Jenkins\",\"第21篇：消息队列——RabbitMQ与Kafka\",\"第22篇：缓存技术——Redis\",\"第23篇：搜索引擎——Elasticsearch\",\"第24篇：安全机制——认证与授权\",\"第25篇：性能测试——JMeter\",\"第26篇：日志聚合——ELK Stack\",\"第27篇：监控与告警——Prometheus与Grafana\",\"第28篇：分布式事务——Seata\",\"第29篇：容器编排——Kubernetes\",\"第30篇：项目总结——银行系统完整实现\"],\"updatedStatus\":{\"usedExamples\":[\"银行账户系统（异常处理）\",\"文件读取（try-with-resources）\",\"自定义异常类\",\"银行客户筛选系统\",\"交易流水分析\",\"Optional处理空账户\"],\"coveredTopics\":[\"异常体系\",\"try-catch-finally\",\"多重捕获\",\"try-with-resources\",\"自定义异常\",\"SLF4J与Logback日志\",\"日志级别\",\"最佳实践\",\"Lambda表达式\",\"函数式接口\",\"Stream API\",\"方法引用\",\"Optional\"],\"style\":\"实战驱动，结合银行系统示例，强调异常处理与日志记录的最佳实践，并逐步引入函数式编程\"},\"prevSeq\":7,\"updatedOutline\":[\"第1篇：异常处理与日志记录——银行系统的稳健之道\",\"第2篇：集合框架——从数组到泛型集合的进化\",\"第3篇：Java I/O流——文件读写与序列化\",\"第4篇：多线程基础——银行系统的并发挑战\",\"第5篇：网络编程——Socket通信与RMI\",\"第6篇：JDBC与数据库操作——持久化银行数据\",\"第7篇：反射与注解——框架的基石\",\"第8篇：Lambda表达式与Stream API——函数式编程实战\",\"第9篇：设计模式——单例、工厂与观察者\",\"第10篇：JVM内存模型与性能调优\",\"第11篇：单元测试与Mock——JUnit与Mockito\",\"第12篇：构建工具——Maven与Gradle\",\"第13篇：版本控制——Git进阶\",\"第14篇：Spring基础——依赖注入与AOP\",\"第15篇：Spring Boot——快速搭建银行系统\",\"第16篇：MyBatis——ORM实战\",\"第17篇：RESTful API设计\",\"第18篇：微服务入门——Spring Cloud\",\"第19篇：Docker容器化部署\",\"第20篇：持续集成/持续部署——Jenkins\",\"第21篇：消息队列——RabbitMQ与Kafka\",\"第22篇：缓存技术——Redis\",\"第23篇：搜索引擎——Elasticsearch\",\"第24篇：安全机制——认证与授权\",\"第25篇：性能测试——JMeter\",\"第26篇：日志聚合——ELK Stack\",\"第27篇：监控与告警——Prometheus与Grafana\",\"第28篇：分布式事务——Seata\",\"第29篇：容器编排——Kubernetes\",\"第30篇：项目总结——银行系统完整实现\"],\"topic\":\"30天Java进阶\",\"accumulated\":{\"usedExamples\":[\"银行账户系统（异常处理）\",\"文件读取（try-with-resources）\",\"自定义异常类\"],\"coveredTopics\":[\"异常体系\",\"try-catch-finally\",\"多重捕获\",\"try-with-resources\",\"自定义异常\",\"SLF4J与Logback日志\",\"日志级别\",\"最佳实践\"],\"style\":\"实战驱动，结合银行系统示例，强调异常处理与日志记录的最佳实践。\"},\"article\":{\"title\":\"第8篇：Lambda表达式与Stream API——函数式编程实战\",\"content\":\"## 引言\\n在上一篇中，我们通过反射与注解为银行系统注入了灵活性和可扩展性，让框架能够动态处理业务逻辑。今天，我们将踏入Java 8引入的函数式编程世界——Lambda表达式与Stream API。它们不仅让代码更简洁、更易读，还能高效处理集合数据，尤其在银行系统的交易流水分析、客户筛选等场景中，能大幅提升开发效率。本文将通过实战案例，带你掌握函数式编程的核心用法。\\n\\n## 1. Lambda表达式：匿名函数的优雅写法\\nLambda表达式本质上是匿名函数，允许将函数作为方法参数传递。其语法为：`(参数列表) -> { 方法体 }`。\\n\\n### 1.1 从匿名内部类到Lambda\\n回顾银行系统中，我们曾用匿名内部类实现比较器：\\n```java\\nList<Transaction> transactions = ...;\\ntransactions.sort(new Comparator<Transaction>() {\\n    @Override\\n    public int compare(Transaction t1, Transaction t2) {\\n        return t1.getAmount().compareTo(t2.getAmount());\\n    }\\n});\\n```\\n使用Lambda后：\\n```java\\ntransactions.sort((t1, t2) -> t1.getAmount().compareTo(t2.getAmount()));\\n```\\n更简洁，且可读性更强。\\n\\n### 1.2 函数式接口\\nLambda表达式只能用于函数式接口（只有一个抽象方法的接口）。Java 8提供了常用函数式接口：\\n- `Predicate<T>`：判断真假，如筛选交易\\n- `Consumer<T>`：消费数据，如打印日志\\n- `Function<T,R>`：转换数据，如提取金额\\n- `Supplier<T>`：提供数据，如生成随机ID\\n\\n**银行系统示例**：筛选大额交易\\n```java\\nPredicate<Transaction> largeAmount = t -> t.getAmount() > 10000;\\nList<Transaction> largeTransactions = transactions.stream()\\n    .filter(largeAmount)\\n    .collect(Collectors.toList());\\n```\\n\\n## 2. Stream API：数据处理的流水线\\nStream API提供了一种声明式处理集合的方式，支持链式操作，包括中间操作（如filter、map）和终端操作（如collect、forEach）。\\n\\n### 2.1 创建Stream\\n- 从集合：`list.stream()`\\n- 从数组：`Arrays.stream(array)`\\n- 从值：`Stream.of(1, 2, 3)`\\n\\n### 2.2 常用中间操作\\n- **filter**：过滤元素\\n- **map**：转换元素\\n- **sorted**：排序\\n- **distinct**：去重\\n- **limit**：截取前n个\\n\\n### 2.3 常用终端操作\\n- **collect**：收集到集合\\n- **forEach**：遍历\\n- **reduce**：聚合（求和、最大值等）\\n- **count**：计数\\n- **anyMatch/allMatch/noneMatch**：匹配检查\\n\\n**银行系统实战**：分析交易流水\\n```java\\n// 获取所有交易金额大于5000的交易，按金额降序排序，取前10条\\nList<Transaction> topTransactions = transactions.stream()\\n    .filter(t -> t.getAmount() > 5000)\\n    .sorted((t1, t2) -> t2.getAmount().compareTo(t1.getAmount()))\\n    .limit(10)\\n    .collect(Collectors.toList());\\n```\\n\\n### 2.4 并行流\\n对于大数据集，可以使用`parallelStream()`利用多核CPU加速处理：\\n```java\\ndouble totalAmount = transactions.parallelStream()\\n    .filter(t -> t.getStatus() == TransactionStatus.SUCCESS)\\n    .mapToDouble(Transaction::getAmount)\\n    .sum();\\n```\\n注意：并行流需考虑线程安全，避免共享可变状态。\\n\\n## 3. 实战：银行客户筛选系统\\n假设我们需要从客户列表中筛选出活跃客户（交易次数>10且总金额>10000），并按总金额降序返回前5名。\\n\\n### 3.1 定义客户类\\n```java\\npublic class Customer {\\n    private String name;\\n    private List<Transaction> transactions;\\n    // getter/setter\\n}\\n```\\n\\n### 3.2 使用Stream实现\\n```java\\nList<Customer> customers = ...;\\nList<String> topCustomers = customers.stream()\\n    .filter(c -> c.getTransactions().size() > 10)\\n    .filter(c -> c.getTransactions().stream()\\n                  .mapToDouble(Transaction::getAmount).sum() > 10000)\\n    .sorted((c1, c2) -> Double.compare(\\n        c2.getTransactions().stream().mapToDouble(Transaction::getAmount).sum(),\\n        c1.getTransactions().stream().mapToDouble(Transaction::getAmount).sum()))\\n    .limit(5)\\n    .map(Customer::getName)\\n    .collect(Collectors.toList());\\n```\\n\\n## 4. 方法引用与Optional\\n### 4.1 方法引用\\n简化Lambda，如`Customer::getName`等价于`c -> c.getName()`。\\n\\n### 4.2 Optional：优雅处理空值\\n银行系统中，账户可能不存在，用Optional避免NullPointerException：\\n```java\\npublic Optional<Account> findAccount(String accountId) {\\n    // 返回Optional.of(account) 或 Optional.empty()\\n}\\n\\n// 使用\\nfindAccount(\\\"123\\\").ifPresent(account -> {\\n    System.out.println(\\\"余额：\\\" + account.getBalance());\\n});\\n```\\n\\n## 5. 最佳实践与注意事项\\n- **避免过度使用Stream**：简单循环更易读\\n- **注意性能**：并行流不适合小数据集\\n- **避免副作用**：Stream操作应无状态、无干扰\\n- **结合日志**：调试时可先用`peek()`打印中间结果\\n\\n## 结语\\nLambda与Stream API让Java代码更简洁、更具表现力，尤其适合数据处理场景。在银行系统中，无论是交易分析、客户筛选还是报表生成，它们都能发挥巨大作用。下一章，我们将深入设计模式，用单例、工厂与观察者模式重构银行系统的核心模块，敬请期待！\",\"examples\":[\"银行客户筛选系统\",\"交易流水分析\",\"Optional处理空账户\"],\"topics\":[\"Lambda表达式\",\"函数式接口\",\"Stream API\",\"方法引用\",\"Optional\"]},\"isComplete\":false,\"seriesState\":\"{\\n  \\\"article\\\": {\\n    \\\"title\\\": \\\"第8篇：Lambda表达式与Stream API——函数式编程实战\\\",\\n    \\\"content\\\": \\\"## 引言\\\\n在上一篇中，我们通过反射与注解为银行系统注入了灵活性和可扩展性，让框架能够动态处理业务逻辑。今天，我们将踏入Java 8引入的函数式编程世界——Lambda表达式与Stream API。它们不仅让代码更简洁、更易读，还能高效处理集合数据，尤其在银行系统的交易流水分析、客户筛选等场景中，能大幅提升开发效率。本文将通过实战案例，带你掌握函数式编程的核心用法。\\\\n\\\\n## 1. Lambda表达式：匿名函数的优雅写法\\\\nLambda表达式本质上是匿名函数，允许将函数作为方法参数传递。其语法为：`(参数列表) -> { 方法体 }`。\\\\n\\\\n### 1.1 从匿名内部类到Lambda\\\\n回顾银行系统中，我们曾用匿名内部类实现比较器：\\\\n```java\\\\nList<Transaction> transactions = ...;\\\\ntransactions.sort(new Comparator<Transaction>() {\\\\n    @Override\\\\n    public int compare(Transaction t1, Transaction t2) {\\\\n        return t1.getAmount().compareTo(t2.getAmount());\\\\n    }\\\\n});\\\\n```\\\\n使用Lambda后：\\\\n```java\\\\ntransactions.sort((t1, t2) -> t1.getAmount().compareTo(t2.getAmount()));\\\\n```\\\\n更简洁，且可读性更强。\\\\n\\\\n### 1.2 函数式接口\\\\nLambda表达式只能用于函数式接口（只有一个抽象方法的接口）。Java 8提供了常用函数式接口：\\\\n- `Predicate<T>`：判断真假，如筛选交易\\\\n- `Consumer<T>`：消费数据，如打印日志\\\\n- `Function<T,R>`：转换数据，如提取金额\\\\n- `Supplier<T>`：提供数据，如生成随机ID\\\\n\\\\n**银行系统示例**：筛选大额交易\\\\n```java\\\\nPredicate<Transaction> largeAmount = t -> t.getAmount() > 10000;\\\\nList<Transaction> largeTransactions = transactions.stream()\\\\n    .filter(largeAmount)\\\\n    .collect(Collectors.toList());\\\\n```\\\\n\\\\n## 2. Stream API：数据处理的流水线\\\\nStream API提供了一种声明式处理集合的方式，支持链式操作，包括中间操作（如filter、map）和终端操作（如collect、forEach）。\\\\n\\\\n### 2.1 创建Stream\\\\n- 从集合：`list.stream()`\\\\n- 从数组：`Arrays.stream(array)`\\\\n- 从值：`Stream.of(1, 2, 3)`\\\\n\\\\n### 2.2 常用中间操作\\\\n- **filter**：过滤元素\\\\n- **map**：转换元素\\\\n- **sorted**：排序\\\\n- **distinct**：去重\\\\n- **limit**：截取前n个\\\\n\\\\n### 2.3 常用终端操作\\\\n- **collect**：收集到集合\\\\n- **forEach**：遍历\\\\n- **reduce**：聚合（求和、最大值等）\\\\n- **count**：计数\\\\n- **anyMatch/allMatch/noneMatch**：匹配检查\\\\n\\\\n**银行系统实战**：分析交易流水\\\\n```java\\\\n// 获取所有交易金额大于5000的交易，按金额降序排序，取前10条\\\\nList<Transaction> topTransactions = transactions.stream()\\\\n    .filter(t -> t.getAmount() > 5000)\\\\n    .sorted((t1, t2) -> t2.getAmount().compareTo(t1.getAmount()))\\\\n    .limit(10)\\\\n    .collect(Collectors.toList());\\\\n```\\\\n\\\\n### 2.4 并行流\\\\n对于大数据集，可以使用`parallelStream()`利用多核CPU加速处理：\\\\n```java\\\\ndouble totalAmount = transactions.parallelStream()\\\\n    .filter(t -> t.getStatus() == TransactionStatus.SUCCESS)\\\\n    .mapToDouble(Transaction::getAmount)\\\\n    .sum();\\\\n```\\\\n注意：并行流需考虑线程安全，避免共享可变状态。\\\\n\\\\n## 3. 实战：银行客户筛选系统\\\\n假设我们需要从客户列表中筛选出活跃客户（交易次数>10且总金额>10000），并按总金额降序返回前5名。\\\\n\\\\n### 3.1 定义客户类\\\\n```java\\\\npublic class Customer {\\\\n    private String name;\\\\n    private List<Transaction> transactions;\\\\n    // getter/setter\\\\n}\\\\n```\\\\n\\\\n### 3.2 使用Stream实现\\\\n```java\\\\nList<Customer> customers = ...;\\\\nList<String> topCustomers = customers.stream()\\\\n    .filter(c -> c.getTransactions().size() > 10)\\\\n    .filter(c -> c.getTransactions().stream()\\\\n                  .mapToDouble(Transaction::getAmount).sum() > 10000)\\\\n    .sorted((c1, c2) -> Double.compare(\\\\n        c2.getTransactions().stream().mapToDouble(Transaction::getAmount).sum(),\\\\n        c1.getTransactions().stream().mapToDouble(Transaction::getAmount).sum()))\\\\n    .limit(5)\\\\n    .map(Customer::getName)\\\\n    .collect(Collectors.toList());\\\\n```\\\\n\\\\n## 4. 方法引用与Optional\\\\n### 4.1 方法引用\\\\n简化Lambda，如`Customer::getName`等价于`c -> c.getName()`。\\\\n\\\\n### 4.2 Optional：优雅处理空值\\\\n银行系统中，账户可能不存在，用Optional避免NullPointerException：\\\\n```java\\\\npublic Optional<Account> findAccount(String accountId) {\\\\n    // 返回Optional.of(account) 或 Optional.empty()\\\\n}\\\\n\\\\n// 使用\\\\nfindAccount(\\\\\\\"123\\\\\\\").ifPresent(account -> {\\\\n    System.out.println(\\\\\\\"余额：\\\\\\\" + account.getBalance());\\\\n});\\\\n```\\\\n\\\\n## 5. 最佳实践与注意事项\\\\n- **避免过度使用Stream**：简单循环更易读\\\\n- **注意性能**：并行流不适合小数据集\\\\n- **避免副作用**：Stream操作应无状态、无干扰\\\\n- **结合日志**：调试时可先用`peek()`打印中间结果\\\\n\\\\n## 结语\\\\nLambda与Stream API让Java代码更简洁、更具表现力，尤其适合数据处理场景。在银行系统中，无论是交易分析、客户筛选还是报表生成，它们都能发挥巨大作用。下一章，我们将深入设计模式，用单例、工厂与观察者模式重构银行系统的核心模块，敬请期待！\\\",\\n    \\\"examples\\\": [\\\"银行客户筛选系统\\\", \\\"交易流水分析\\\", \\\"Optional处理空账户\\\"],\\n    \\\"topics\\\": [\\\"Lambda表达式\\\", \\\"函数式接口\\\", \\\"Stream API\\\", \\\"方法引用\\\", \\\"Optional\\\"]\\n  },\\n  \\\"updatedOutline\\\": [\\n    \\\"第1篇：异常处理与日志记录——银行系统的稳健之道\\\",\\n    \\\"第2篇：集合框架——从数组到泛型集合的进化\\\",\\n    \\\"第3篇：Java I/O流——文件读写与序列化\\\",\\n    \\\"第4篇：多线程基础——银行系统的并发挑战\\\",\\n    \\\"第5篇：网络编程——Socket通信与RMI\\\",\\n    \\\"第6篇：JDBC与数据库操作——持久化银行数据\\\",\\n    \\\"第7篇：反射与注解——框架的基石\\\",\\n    \\\"第8篇：Lambda表达式与Stream API——函数式编程实战\\\",\\n    \\\"第9篇：设计模式——单例、工厂与观察者\\\",\\n    \\\"第10篇：JVM内存模型与性能调优\\\",\\n    \\\"第11篇：单元测试与Mock——JUnit与Mockito\\\",\\n    \\\"第12篇：构建工具——Maven与Gradle\\\",\\n    \\\"第13篇：版本控制——Git进阶\\\",\\n    \\\"第14篇：Spring基础——依赖注入与AOP\\\",\\n    \\\"第15篇：Spring Boot——快速搭建银行系统\\\",\\n    \\\"第16篇：MyBatis——ORM实战\\\",\\n    \\\"第17篇：RESTful API设计\\\",\\n    \\\"第18篇：微服务入门——Spring Cloud\\\",\\n    \\\"第19篇：Docker容器化部署\\\",\\n    \\\"第20篇：持续集成/持续部署——Jenkins\\\",\\n    \\\"第21篇：消息队列——RabbitMQ与Kafka\\\",\\n    \\\"第22篇：缓存技术——Redis\\\",\\n    \\\"第23篇：搜索引擎——Elasticsearch\\\",\\n    \\\"第24篇：安全机制——认证与授权\\\",\\n    \\\"第25篇：性能测试——JMeter\\\",\\n    \\\"第26篇：日志聚合——ELK Stack\\\",\\n    \\\"第27篇：监控与告警——Prometheus与Grafana\\\",\\n    \\\"第28篇：分布式事务——Seata\\\",\\n    \\\"第29篇：容器编排——Kubernetes\\\",\\n    \\\"第30篇：项目总结——银行系统完整实现\\\"\\n  ],\\n  \\\"updatedStatus\\\": {\\n    \\\"usedExamples\\\": [\\\"银行账户系统（异常处理）\\\", \\\"文件读取（try-with-resources）\\\", \\\"自定义异常类\\\", \\\"银行客户筛选系统\\\", \\\"交易流水分析\\\", \\\"Optional处理空账户\\\"],\\n    \\\"coveredTopics\\\": [\\\"异常体系\\\", \\\"try-catch-finally\\\", \\\"多重捕获\\\", \\\"try-with-resources\\\", \\\"自定义异常\\\", \\\"SLF4J与Logback日志\\\", \\\"日志级别\\\", \\\"最佳实践\\\", \\\"Lambda表达式\\\", \\\"函数式接口\\\", \\\"Stream API\\\", \\\"方法引用\\\", \\\"Optional\\\"],\\n    \\\"style\\\": \\\"实战驱动，结合银行系统示例，强调异常处理与日志记录的最佳实践，并逐步引入函数式编程\\\"\\n  },\\n  \\\"isComplete\\\": false\\n}\",\"seriesName\":\"30天Java进阶\"}}', 'SUCCESS', 1, 8, 200, NULL, NULL, '2026-07-09 12:02:21', '2026-07-09 12:02:28');
INSERT INTO `ai_webhook_delivery` VALUES (10, 'blog_series_writer-chain-demo01-7', 'blog_series_writer-aefcca8e3c684218911af30ed196531c', 'blog_series_writer-chain-demo01', 7, 'blog_series_writer', NULL, 'ITERATION_ADVANCED', 'http://127.0.0.1:8082/blog/admin/blog/webhook/series-append', 'INLINE', '{\"deliveryId\":\"blog_series_writer-chain-demo01-7\",\"event\":\"ITERATION_ADVANCED\",\"chainId\":\"blog_series_writer-chain-demo01\",\"seq\":7,\"agentCode\":\"blog_series_writer\",\"instanceId\":\"blog_series_writer-aefcca8e3c684218911af30ed196531c\",\"context\":{\"outline_update\":{\"changes\":\"无重大调整，保持原有大纲顺序。\",\"new_outline\":[\"1. AI原生应用概述与工程化挑战\",\"2. 数据管道设计与治理\",\"3. 模型训练与实验管理\",\"4. 模型部署与推理优化\",\"5. 监控与可观测性体系\",\"6. 持续交付与CI/CD流水线\",\"7. 安全与合规实践\",\"8. 团队协作与组织架构\",\"9. 案例研究：端到端AI原生应用\",\"10. 未来趋势与总结\"]},\"prevSeq\":6,\"series_complete\":false,\"topic\":\"AI原生应用工程化落地\",\"accumulated_update\":{\"used_examples\":[\"数据管道ETL\",\"MLflow实验跟踪\",\"Kubernetes部署\",\"Prometheus监控\",\"GitLab CI流水线\",\"金丝雀部署\"],\"covered_topics\":[\"数据治理\",\"模型训练\",\"部署策略\",\"监控\",\"CI/CD\"],\"writing_style\":\"技术深度适中，结合案例与工具选型，注重实践指导。\"},\"article\":{\"title\":\"AI原生应用的持续交付：CI/CD流水线设计与实践\",\"content\":\"在之前的文章中，我们探讨了AI原生应用的数据管道、模型训练、部署与监控等关键环节。然而，这些环节的自动化与协同是保障应用持续演进的核心。本文聚焦于AI原生应用的CI/CD流水线设计，阐述如何构建一套高效、可靠的持续交付体系，以应对模型迭代、数据漂移与业务需求变化的挑战。\\n\\n## 1. 传统CI/CD与AI CI/CD的差异\\n传统软件CI/CD主要关注代码变更的构建、测试与部署。AI原生应用则增加了模型、数据与实验的复杂性。例如，模型训练是一个计算密集型过程，且模型性能依赖于数据质量。因此，AI CI/CD需要纳入数据验证、模型评估、版本管理以及A/B测试等环节。\\n\\n## 2. 流水线核心阶段\\n### 2.1 数据准备与验证\\n- 自动触发数据管道，拉取最新训练数据。\\n- 运行数据质量检查（如缺失值、分布偏移、特征完整性）。\\n- 若数据质量不合格，阻断后续流程并告警。\\n\\n### 2.2 模型训练与实验跟踪\\n- 基于最新数据触发训练任务，使用MLflow、Weights & Biases等工具记录超参数、指标与模型产物。\\n- 支持并行实验（如超参数搜索、架构变体）。\\n- 训练完成后，自动将模型注册到模型仓库（如MLflow Model Registry）。\\n\\n### 2.3 模型评估与验证\\n- 在hold-out测试集上计算性能指标（如准确率、召回率、AUC）。\\n- 与生产中的当前模型进行对比，设置性能门限（如提升>1%才通过）。\\n- 运行公平性、鲁棒性测试（如对抗样本测试）。\\n\\n### 2.4 部署与发布\\n- 采用蓝绿部署或金丝雀发布策略，将新模型逐步上线。\\n- 自动配置推理服务（如使用Kubernetes + Istio进行流量管理）。\\n- 部署后执行冒烟测试，验证推理接口可用性。\\n\\n### 2.5 监控与回滚\\n- 实时监控模型性能（如延迟、吞吐、预测分布）。\\n- 若检测到性能退化（如数据漂移、准确率下降），自动触发回滚至上一个稳定版本。\\n- 记录所有发布事件，便于审计。\\n\\n## 3. 工具链选型\\n- **CI/CD平台**：GitLab CI、Jenkins、Argo Workflows（适合Kubernetes原生）。\\n- **实验跟踪**：MLflow、Kubeflow Pipelines。\\n- **模型仓库**：MLflow Model Registry、DVC。\\n- **部署与编排**：Kubernetes、Helm、Seldon Core（用于模型serving）。\\n- **监控**：Prometheus + Grafana，结合Evidently AI检测数据漂移。\\n\\n## 4. 实践案例：基于GitLab CI的端到端流水线\\n假设一个文本分类应用，代码库包含训练脚本、Dockerfile与Kubernetes部署清单。流水线流程如下：\\n1. 开发者提交代码（含模型架构修改）。\\n2. GitLab CI触发：\\n   - 阶段1：运行单元测试与lint。\\n   - 阶段2：执行数据验证（使用Great Expectations）。\\n   - 阶段3：启动训练任务（在GPU节点上），记录指标到MLflow。\\n   - 阶段4：评估模型，若指标不达标则失败。\\n   - 阶段5：构建推理镜像，推送至容器注册表。\\n   - 阶段6：部署至staging环境，运行集成测试。\\n   - 阶段7：手动审批后，金丝雀发布至生产（5%流量），监控1小时后全量。\\n\\n## 5. 挑战与应对\\n- **数据版本控制**：使用DVC或LakeFS管理数据快照，确保可复现。\\n- **模型漂移检测**：集成在线监控，当分布偏移超过阈值时自动触发重新训练。\\n- **资源成本**：训练任务使用spot实例，流水线设置超时与自动缩放。\\n- **安全合规**：扫描模型文件中的恶意代码，限制镜像来源。\\n\\n## 6. 总结\\nAI原生应用的CI/CD流水线需要融合数据工程、机器学习与DevOps的最佳实践。通过自动化数据验证、模型评估与渐进式部署，可以显著提升迭代速度与可靠性。下一篇文章将探讨如何构建AI应用的监控与可观测性体系，确保生产环境中的持续健康。\",\"key_points\":[\"AI CI/CD与传统CI/CD的差异\",\"流水线核心阶段：数据验证、训练、评估、部署、监控\",\"工具链选型建议\",\"基于GitLab CI的端到端案例\",\"挑战：数据版本、漂移检测、成本、安全\"],\"examples_used\":[\"GitLab CI流水线配置\",\"MLflow实验跟踪\",\"金丝雀部署策略\"]},\"seriesState\":\"{\\n  \\\"article\\\": {\\n    \\\"title\\\": \\\"AI原生应用的持续交付：CI/CD流水线设计与实践\\\",\\n    \\\"content\\\": \\\"在之前的文章中，我们探讨了AI原生应用的数据管道、模型训练、部署与监控等关键环节。然而，这些环节的自动化与协同是保障应用持续演进的核心。本文聚焦于AI原生应用的CI/CD流水线设计，阐述如何构建一套高效、可靠的持续交付体系，以应对模型迭代、数据漂移与业务需求变化的挑战。\\\\n\\\\n## 1. 传统CI/CD与AI CI/CD的差异\\\\n传统软件CI/CD主要关注代码变更的构建、测试与部署。AI原生应用则增加了模型、数据与实验的复杂性。例如，模型训练是一个计算密集型过程，且模型性能依赖于数据质量。因此，AI CI/CD需要纳入数据验证、模型评估、版本管理以及A/B测试等环节。\\\\n\\\\n## 2. 流水线核心阶段\\\\n### 2.1 数据准备与验证\\\\n- 自动触发数据管道，拉取最新训练数据。\\\\n- 运行数据质量检查（如缺失值、分布偏移、特征完整性）。\\\\n- 若数据质量不合格，阻断后续流程并告警。\\\\n\\\\n### 2.2 模型训练与实验跟踪\\\\n- 基于最新数据触发训练任务，使用MLflow、Weights & Biases等工具记录超参数、指标与模型产物。\\\\n- 支持并行实验（如超参数搜索、架构变体）。\\\\n- 训练完成后，自动将模型注册到模型仓库（如MLflow Model Registry）。\\\\n\\\\n### 2.3 模型评估与验证\\\\n- 在hold-out测试集上计算性能指标（如准确率、召回率、AUC）。\\\\n- 与生产中的当前模型进行对比，设置性能门限（如提升>1%才通过）。\\\\n- 运行公平性、鲁棒性测试（如对抗样本测试）。\\\\n\\\\n### 2.4 部署与发布\\\\n- 采用蓝绿部署或金丝雀发布策略，将新模型逐步上线。\\\\n- 自动配置推理服务（如使用Kubernetes + Istio进行流量管理）。\\\\n- 部署后执行冒烟测试，验证推理接口可用性。\\\\n\\\\n### 2.5 监控与回滚\\\\n- 实时监控模型性能（如延迟、吞吐、预测分布）。\\\\n- 若检测到性能退化（如数据漂移、准确率下降），自动触发回滚至上一个稳定版本。\\\\n- 记录所有发布事件，便于审计。\\\\n\\\\n## 3. 工具链选型\\\\n- **CI/CD平台**：GitLab CI、Jenkins、Argo Workflows（适合Kubernetes原生）。\\\\n- **实验跟踪**：MLflow、Kubeflow Pipelines。\\\\n- **模型仓库**：MLflow Model Registry、DVC。\\\\n- **部署与编排**：Kubernetes、Helm、Seldon Core（用于模型serving）。\\\\n- **监控**：Prometheus + Grafana，结合Evidently AI检测数据漂移。\\\\n\\\\n## 4. 实践案例：基于GitLab CI的端到端流水线\\\\n假设一个文本分类应用，代码库包含训练脚本、Dockerfile与Kubernetes部署清单。流水线流程如下：\\\\n1. 开发者提交代码（含模型架构修改）。\\\\n2. GitLab CI触发：\\\\n   - 阶段1：运行单元测试与lint。\\\\n   - 阶段2：执行数据验证（使用Great Expectations）。\\\\n   - 阶段3：启动训练任务（在GPU节点上），记录指标到MLflow。\\\\n   - 阶段4：评估模型，若指标不达标则失败。\\\\n   - 阶段5：构建推理镜像，推送至容器注册表。\\\\n   - 阶段6：部署至staging环境，运行集成测试。\\\\n   - 阶段7：手动审批后，金丝雀发布至生产（5%流量），监控1小时后全量。\\\\n\\\\n## 5. 挑战与应对\\\\n- **数据版本控制**：使用DVC或LakeFS管理数据快照，确保可复现。\\\\n- **模型漂移检测**：集成在线监控，当分布偏移超过阈值时自动触发重新训练。\\\\n- **资源成本**：训练任务使用spot实例，流水线设置超时与自动缩放。\\\\n- **安全合规**：扫描模型文件中的恶意代码，限制镜像来源。\\\\n\\\\n## 6. 总结\\\\nAI原生应用的CI/CD流水线需要融合数据工程、机器学习与DevOps的最佳实践。通过自动化数据验证、模型评估与渐进式部署，可以显著提升迭代速度与可靠性。下一篇文章将探讨如何构建AI应用的监控与可观测性体系，确保生产环境中的持续健康。\\\",\\n    \\\"key_points\\\": [\\n      \\\"AI CI/CD与传统CI/CD的差异\\\",\\n      \\\"流水线核心阶段：数据验证、训练、评估、部署、监控\\\",\\n      \\\"工具链选型建议\\\",\\n      \\\"基于GitLab CI的端到端案例\\\",\\n      \\\"挑战：数据版本、漂移检测、成本、安全\\\"\\n    ],\\n    \\\"examples_used\\\": [\\\"GitLab CI流水线配置\\\", \\\"MLflow实验跟踪\\\", \\\"金丝雀部署策略\\\"]\\n  },\\n  \\\"outline_update\\\": {\\n    \\\"changes\\\": \\\"无重大调整，保持原有大纲顺序。\\\",\\n    \\\"new_outline\\\": [\\n      \\\"1. AI原生应用概述与工程化挑战\\\",\\n      \\\"2. 数据管道设计与治理\\\",\\n      \\\"3. 模型训练与实验管理\\\",\\n      \\\"4. 模型部署与推理优化\\\",\\n      \\\"5. 监控与可观测性体系\\\",\\n      \\\"6. 持续交付与CI/CD流水线\\\",\\n      \\\"7. 安全与合规实践\\\",\\n      \\\"8. 团队协作与组织架构\\\",\\n      \\\"9. 案例研究：端到端AI原生应用\\\",\\n      \\\"10. 未来趋势与总结\\\"\\n    ]\\n  },\\n  \\\"accumulated_update\\\": {\\n    \\\"used_examples\\\": [\\\"数据管道ETL\\\", \\\"MLflow实验跟踪\\\", \\\"Kubernetes部署\\\", \\\"Prometheus监控\\\", \\\"GitLab CI流水线\\\", \\\"金丝雀部署\\\"],\\n    \\\"covered_topics\\\": [\\\"数据治理\\\", \\\"模型训练\\\", \\\"部署策略\\\", \\\"监控\\\", \\\"CI/CD\\\"],\\n    \\\"writing_style\\\": \\\"技术深度适中，结合案例与工具选型，注重实践指导。\\\"\\n  },\\n  \\\"series_complete\\\": false\\n}\",\"seriesName\":\"AI原生应用工程化落地\"}}', 'SUCCESS', 1, 8, 200, NULL, NULL, '2026-07-09 15:28:44', '2026-07-09 15:28:44');
INSERT INTO `ai_webhook_delivery` VALUES (11, 'blog_series_writer-chain-demo01-1', 'blog_series_writer-bf2e0e39ce8844f9abbca1cc1f9b942c', 'blog_series_writer-chain-demo01', 1, 'blog_series_writer', NULL, 'ITERATION_ADVANCED', 'http://127.0.0.1:8082/blog/admin/blog/webhook/series-append', 'INLINE', '{\"deliveryId\":\"blog_series_writer-chain-demo01-1\",\"event\":\"ITERATION_ADVANCED\",\"chainId\":\"blog_series_writer-chain-demo01\",\"seq\":1,\"agentCode\":\"blog_series_writer\",\"instanceId\":\"blog_series_writer-bf2e0e39ce8844f9abbca1cc1f9b942c\",\"context\":{\"outline_update\":{\"changes\":\"无变化，大纲已涵盖本内容。\",\"updated_outline\":\"1. AI原生应用工程化落地：引言与核心挑战（已完成）\\n2. AI原生应用的架构设计：从单体到智能体编排（当前）\\n3. 数据管道设计：构建高效、可靠的AI数据流（计划中）\\n4. 模型部署与推理优化：从实验到生产（计划中）\\n5. 监控与可观测性：AI应用的生命线（计划中）\\n6. 安全与伦理：AI原生应用的边界（计划中）\\n7. 总结与未来展望（计划中）\"},\"prevSeq\":0,\"series_complete\":false,\"topic\":\"AI原生应用工程化落地\",\"accumulated_update\":{\"used_examples\":[\"聊天机器人单体架构问题\",\"智能客服智能体\",\"文档问答助手架构\"],\"covered_knowledge\":[\"微服务架构\",\"智能体编排\",\"异步消息驱动\",\"状态管理\",\"可观测性\"],\"writing_style\":\"技术务实，结合案例，逐步深入\"},\"next_article\":{\"title\":\"AI原生应用的架构设计：从单体到智能体编排\",\"content\":\"在前一篇文章中，我们探讨了AI原生应用与传统软件的根本差异，以及工程化落地的核心挑战。今天，我们将深入技术架构层面，讨论如何设计一个既能支撑AI能力（如大模型推理、数据流处理）又能保持工程可维护性的应用架构。\\n\\n### 从单体到微服务：AI场景的演进\\n\\n传统的单体架构在AI应用中往往显得力不从心。例如，一个简单的聊天机器人，如果将所有逻辑（前端、后端、模型推理、知识库检索）都放在一个进程中，当模型推理耗时较长时，整个应用会阻塞；当需要扩展推理能力时，只能整体复制，导致资源浪费。\\n\\n因此，AI原生应用通常采用微服务架构，将不同职责分离：\\n- **推理服务**：负责调用大模型API或本地模型，提供文本生成、图像识别等能力。\\n- **知识库服务**：管理向量数据库，处理文档嵌入、相似性检索。\\n- **编排服务**：协调多个AI组件，实现复杂工作流（如多轮对话、工具调用）。\\n- **网关服务**：负责认证、限流、请求路由。\\n\\n### 智能体编排：AI应用的核心模式\\n\\n更进一步，AI原生应用的核心不再是传统的请求-响应模式，而是**智能体编排（Agent Orchestration）**。智能体是一个拥有自主决策能力的模块，它能够根据用户输入，动态选择工具、调用外部API、组合多个模型输出。\\n\\n例如，一个“智能客服”智能体：\\n1. 接收用户问题“我的订单什么时候到？”\\n2. 调用意图识别模型，判断需要查询订单状态。\\n3. 调用订单API获取实时数据。\\n4. 调用大模型生成自然语言回复。\\n5. 返回给用户。\\n\\n这种编排模式要求架构支持：\\n- **异步消息驱动**：避免长任务阻塞。\\n- **状态管理**：智能体需要记住对话上下文和中间结果。\\n- **可观测性**：追踪每个决策路径，便于调试和优化。\\n\\n### 实战案例：一个简单的智能体架构\\n\\n假设我们构建一个“文档问答助手”，架构如下：\\n- **用户请求** → **网关** → **编排服务**（使用LangChain或自研框架）\\n- 编排服务：\\n  - 调用**意图识别模型**（轻量级BERT）判断用户意图（查询、总结、翻译）。\\n  - 根据意图，调用**知识库服务**检索相关文档片段。\\n  - 调用**大模型服务**（如GPT-4）生成答案。\\n  - 返回结果。\\n- 所有服务通过**消息队列**（如RabbitMQ）解耦，确保高可用。\\n\\n### 架构决策要点\\n\\n在设计AI原生应用架构时，需权衡以下几点：\\n1. **延迟 vs 准确性**：实时推理需要低延迟，但复杂模型可能更准。可考虑缓存或预计算。\\n2. **成本 vs 性能**：大模型API调用成本高，可引入本地小模型做预筛选。\\n3. **灵活性 vs 可维护性**：智能体编排增加灵活性，但需定义清晰的接口和错误处理机制。\\n\\n### 小结\\n\\nAI原生应用的架构核心是**微服务+智能体编排**，它让AI能力像乐高积木一样可组合、可扩展。下一篇文章，我们将深入讨论数据管道设计：如何构建高效、可靠的训练和推理数据流。\\n\\n（本文为系列第2篇，后续将涉及数据工程、模型部署、监控等主题。）\"},\"seriesState\":\"{\\n  \\\"next_article\\\": {\\n    \\\"title\\\": \\\"AI原生应用的架构设计：从单体到智能体编排\\\",\\n    \\\"content\\\": \\\"在前一篇文章中，我们探讨了AI原生应用与传统软件的根本差异，以及工程化落地的核心挑战。今天，我们将深入技术架构层面，讨论如何设计一个既能支撑AI能力（如大模型推理、数据流处理）又能保持工程可维护性的应用架构。\\\\n\\\\n### 从单体到微服务：AI场景的演进\\\\n\\\\n传统的单体架构在AI应用中往往显得力不从心。例如，一个简单的聊天机器人，如果将所有逻辑（前端、后端、模型推理、知识库检索）都放在一个进程中，当模型推理耗时较长时，整个应用会阻塞；当需要扩展推理能力时，只能整体复制，导致资源浪费。\\\\n\\\\n因此，AI原生应用通常采用微服务架构，将不同职责分离：\\\\n- **推理服务**：负责调用大模型API或本地模型，提供文本生成、图像识别等能力。\\\\n- **知识库服务**：管理向量数据库，处理文档嵌入、相似性检索。\\\\n- **编排服务**：协调多个AI组件，实现复杂工作流（如多轮对话、工具调用）。\\\\n- **网关服务**：负责认证、限流、请求路由。\\\\n\\\\n### 智能体编排：AI应用的核心模式\\\\n\\\\n更进一步，AI原生应用的核心不再是传统的请求-响应模式，而是**智能体编排（Agent Orchestration）**。智能体是一个拥有自主决策能力的模块，它能够根据用户输入，动态选择工具、调用外部API、组合多个模型输出。\\\\n\\\\n例如，一个“智能客服”智能体：\\\\n1. 接收用户问题“我的订单什么时候到？”\\\\n2. 调用意图识别模型，判断需要查询订单状态。\\\\n3. 调用订单API获取实时数据。\\\\n4. 调用大模型生成自然语言回复。\\\\n5. 返回给用户。\\\\n\\\\n这种编排模式要求架构支持：\\\\n- **异步消息驱动**：避免长任务阻塞。\\\\n- **状态管理**：智能体需要记住对话上下文和中间结果。\\\\n- **可观测性**：追踪每个决策路径，便于调试和优化。\\\\n\\\\n### 实战案例：一个简单的智能体架构\\\\n\\\\n假设我们构建一个“文档问答助手”，架构如下：\\\\n- **用户请求** → **网关** → **编排服务**（使用LangChain或自研框架）\\\\n- 编排服务：\\\\n  - 调用**意图识别模型**（轻量级BERT）判断用户意图（查询、总结、翻译）。\\\\n  - 根据意图，调用**知识库服务**检索相关文档片段。\\\\n  - 调用**大模型服务**（如GPT-4）生成答案。\\\\n  - 返回结果。\\\\n- 所有服务通过**消息队列**（如RabbitMQ）解耦，确保高可用。\\\\n\\\\n### 架构决策要点\\\\n\\\\n在设计AI原生应用架构时，需权衡以下几点：\\\\n1. **延迟 vs 准确性**：实时推理需要低延迟，但复杂模型可能更准。可考虑缓存或预计算。\\\\n2. **成本 vs 性能**：大模型API调用成本高，可引入本地小模型做预筛选。\\\\n3. **灵活性 vs 可维护性**：智能体编排增加灵活性，但需定义清晰的接口和错误处理机制。\\\\n\\\\n### 小结\\\\n\\\\nAI原生应用的架构核心是**微服务+智能体编排**，它让AI能力像乐高积木一样可组合、可扩展。下一篇文章，我们将深入讨论数据管道设计：如何构建高效、可靠的训练和推理数据流。\\\\n\\\\n（本文为系列第2篇，后续将涉及数据工程、模型部署、监控等主题。）\\\"\\n  },\\n  \\\"outline_update\\\": {\\n    \\\"changes\\\": \\\"无变化，大纲已涵盖本内容。\\\",\\n    \\\"updated_outline\\\": \\\"1. AI原生应用工程化落地：引言与核心挑战（已完成）\\\\n2. AI原生应用的架构设计：从单体到智能体编排（当前）\\\\n3. 数据管道设计：构建高效、可靠的AI数据流（计划中）\\\\n4. 模型部署与推理优化：从实验到生产（计划中）\\\\n5. 监控与可观测性：AI应用的生命线（计划中）\\\\n6. 安全与伦理：AI原生应用的边界（计划中）\\\\n7. 总结与未来展望（计划中）\\\"\\n  },\\n  \\\"accumulated_update\\\": {\\n    \\\"used_examples\\\": [\\\"聊天机器人单体架构问题\\\", \\\"智能客服智能体\\\", \\\"文档问答助手架构\\\"],\\n    \\\"covered_knowledge\\\": [\\\"微服务架构\\\", \\\"智能体编排\\\", \\\"异步消息驱动\\\", \\\"状态管理\\\", \\\"可观测性\\\"],\\n    \\\"writing_style\\\": \\\"技术务实，结合案例，逐步深入\\\"\\n  },\\n  \\\"series_complete\\\": false\\n}\",\"seriesName\":\"AI原生应用工程化落地\"}}', 'SUCCESS', 1, 8, 200, NULL, NULL, '2026-07-09 15:37:18', '2026-07-09 15:37:18');
INSERT INTO `ai_webhook_delivery` VALUES (12, 'blog_series_writer-chain-demo01-2', 'blog_series_writer-1e8144b4f8324e9f94f2747935c69eca', 'blog_series_writer-chain-demo01', 2, 'blog_series_writer', NULL, 'ITERATION_ADVANCED', 'http://127.0.0.1:8082/blog/admin/blog/webhook/series-append', 'INLINE', '{\"deliveryId\":\"blog_series_writer-chain-demo01-2\",\"event\":\"ITERATION_ADVANCED\",\"chainId\":\"blog_series_writer-chain-demo01\",\"seq\":2,\"agentCode\":\"blog_series_writer\",\"instanceId\":\"blog_series_writer-1e8144b4f8324e9f94f2747935c69eca\",\"context\":{\"outline_update\":{\"changes\":\"无调整，当前大纲仍适用。\",\"updated_outline\":\"已有大纲：{{outline}}\"},\"prevSeq\":1,\"series_status\":\"未完成，还有后续文章待撰写。\",\"topic\":\"AI原生应用工程化落地\",\"accumulated_update\":{\"used_examples\":\"智能推荐系统迁移案例\",\"covered_topics\":\"单体架构局限、微服务优势、进化路径、实战案例\",\"writing_style\":\"技术分析为主，结合案例，保持清晰结构\"},\"article\":{\"title\":\"AI原生应用架构：从单体到微服务的进化路径\",\"content\":\"在上一篇文章中，我们探讨了AI原生应用的基础概念和核心原则，强调了数据驱动、模型即服务和持续迭代的重要性。今天，我们将深入架构层面，分析从传统单体应用到AI原生微服务架构的进化路径，这是实现工程化落地的关键一步。\\n\\n## 单体架构的局限\\n传统AI应用常采用单体架构：所有组件（数据预处理、模型推理、业务逻辑）耦合在一个进程中。这种模式在小规模场景下可行，但随着业务复杂度和数据量的增长，问题逐渐显现：\\n- **扩展性差**：无法独立扩展推理或数据组件，导致资源浪费。\\n- **部署困难**：任何修改都需要重新部署整个应用，影响迭代速度。\\n- **维护成本高**：团队协作时，代码冲突和依赖管理成为瓶颈。\\n\\n## 微服务架构的优势\\nAI原生应用推荐采用微服务架构，将系统拆分为独立服务，每个服务负责特定功能，如：\\n- **数据服务**：处理数据采集、清洗和特征工程。\\n- **模型服务**：封装模型推理逻辑，提供RESTful或gRPC接口。\\n- **业务服务**：协调流程，调用模型服务并返回结果。\\n- **监控服务**：跟踪性能、日志和模型漂移。\\n\\n这种架构带来灵活性：\\n- **独立扩展**：根据负载，仅扩展模型服务实例，节省成本。\\n- **技术异构**：不同服务可用不同语言或框架（如Python用于模型，Go用于业务）。\\n- **快速迭代**：单个服务可独立部署，不影响全局。\\n\\n## 进化路径：从单体到微服务\\n迁移不是一蹴而就，建议分步进行：\\n1. **识别边界**：分析现有单体，划分功能模块（如数据、模型、业务）。\\n2. **提取模型服务**：优先将模型推理抽离为独立服务，使用容器化部署（如Docker）。\\n3. **引入API网关**：统一入口，管理路由、限流和认证。\\n4. **逐步解耦**：依次提取数据服务和业务服务，确保接口稳定。\\n5. **完善监控**：集成日志聚合和指标收集（如Prometheus、ELK）。\\n\\n## 实战案例：智能推荐系统\\n假设我们有一个单体推荐系统，包含用户画像、模型推理和结果排序。迁移后：\\n- 用户画像服务：处理实时特征。\\n- 模型服务：调用深度学习模型，返回候选列表。\\n- 排序服务：基于规则重排。\\n- 网关：接收请求，分发到各服务。\\n\\n这样，当流量高峰时，我们只需扩展模型服务，而其他服务保持稳定。\\n\\n## 注意事项\\n- **服务间通信**：选择轻量协议（gRPC优于HTTP），避免过重。\\n- **数据一致性**：使用事件驱动或分布式事务，确保最终一致。\\n- **版本管理**：模型服务需支持多版本，便于回滚。\\n\\n下一篇文章，我们将讨论如何设计可复用的模型服务层，包括模型加载、推理优化和动态更新。\",\"summary\":\"本文介绍了AI原生应用从单体架构向微服务架构的进化路径，分析了单体架构的局限和微服务的优势，并给出了具体的迁移步骤和实战案例。\"},\"seriesState\":\"{\\n  \\\"article\\\": {\\n    \\\"title\\\": \\\"AI原生应用架构：从单体到微服务的进化路径\\\",\\n    \\\"content\\\": \\\"在上一篇文章中，我们探讨了AI原生应用的基础概念和核心原则，强调了数据驱动、模型即服务和持续迭代的重要性。今天，我们将深入架构层面，分析从传统单体应用到AI原生微服务架构的进化路径，这是实现工程化落地的关键一步。\\\\n\\\\n## 单体架构的局限\\\\n传统AI应用常采用单体架构：所有组件（数据预处理、模型推理、业务逻辑）耦合在一个进程中。这种模式在小规模场景下可行，但随着业务复杂度和数据量的增长，问题逐渐显现：\\\\n- **扩展性差**：无法独立扩展推理或数据组件，导致资源浪费。\\\\n- **部署困难**：任何修改都需要重新部署整个应用，影响迭代速度。\\\\n- **维护成本高**：团队协作时，代码冲突和依赖管理成为瓶颈。\\\\n\\\\n## 微服务架构的优势\\\\nAI原生应用推荐采用微服务架构，将系统拆分为独立服务，每个服务负责特定功能，如：\\\\n- **数据服务**：处理数据采集、清洗和特征工程。\\\\n- **模型服务**：封装模型推理逻辑，提供RESTful或gRPC接口。\\\\n- **业务服务**：协调流程，调用模型服务并返回结果。\\\\n- **监控服务**：跟踪性能、日志和模型漂移。\\\\n\\\\n这种架构带来灵活性：\\\\n- **独立扩展**：根据负载，仅扩展模型服务实例，节省成本。\\\\n- **技术异构**：不同服务可用不同语言或框架（如Python用于模型，Go用于业务）。\\\\n- **快速迭代**：单个服务可独立部署，不影响全局。\\\\n\\\\n## 进化路径：从单体到微服务\\\\n迁移不是一蹴而就，建议分步进行：\\\\n1. **识别边界**：分析现有单体，划分功能模块（如数据、模型、业务）。\\\\n2. **提取模型服务**：优先将模型推理抽离为独立服务，使用容器化部署（如Docker）。\\\\n3. **引入API网关**：统一入口，管理路由、限流和认证。\\\\n4. **逐步解耦**：依次提取数据服务和业务服务，确保接口稳定。\\\\n5. **完善监控**：集成日志聚合和指标收集（如Prometheus、ELK）。\\\\n\\\\n## 实战案例：智能推荐系统\\\\n假设我们有一个单体推荐系统，包含用户画像、模型推理和结果排序。迁移后：\\\\n- 用户画像服务：处理实时特征。\\\\n- 模型服务：调用深度学习模型，返回候选列表。\\\\n- 排序服务：基于规则重排。\\\\n- 网关：接收请求，分发到各服务。\\\\n\\\\n这样，当流量高峰时，我们只需扩展模型服务，而其他服务保持稳定。\\\\n\\\\n## 注意事项\\\\n- **服务间通信**：选择轻量协议（gRPC优于HTTP），避免过重。\\\\n- **数据一致性**：使用事件驱动或分布式事务，确保最终一致。\\\\n- **版本管理**：模型服务需支持多版本，便于回滚。\\\\n\\\\n下一篇文章，我们将讨论如何设计可复用的模型服务层，包括模型加载、推理优化和动态更新。\\\",\\n    \\\"summary\\\": \\\"本文介绍了AI原生应用从单体架构向微服务架构的进化路径，分析了单体架构的局限和微服务的优势，并给出了具体的迁移步骤和实战案例。\\\"\\n  },\\n  \\\"outline_update\\\": {\\n    \\\"changes\\\": \\\"无调整，当前大纲仍适用。\\\",\\n    \\\"updated_outline\\\": \\\"已有大纲：{{outline}}\\\"\\n  },\\n  \\\"accumulated_update\\\": {\\n    \\\"used_examples\\\": \\\"智能推荐系统迁移案例\\\",\\n    \\\"covered_topics\\\": \\\"单体架构局限、微服务优势、进化路径、实战案例\\\",\\n    \\\"writing_style\\\": \\\"技术分析为主，结合案例，保持清晰结构\\\"\\n  },\\n  \\\"series_status\\\": \\\"未完成，还有后续文章待撰写。\\\"\\n}\",\"seriesName\":\"AI原生应用工程化落地\"}}', 'SUCCESS', 1, 8, 200, NULL, NULL, '2026-07-09 15:39:30', '2026-07-09 15:39:29');
INSERT INTO `ai_webhook_delivery` VALUES (13, 'blog_series-chain-55f07fe784644443bcb013b66c7993cc-2', 'blog_series-7341c7a6d5614cd5bb91d0573c95af05', 'blog_series-chain-55f07fe784644443bcb013b66c7993cc', 2, 'blog_series', NULL, 'ITERATION_ADVANCED', 'http://127.0.0.1:8082/blog/admin/blog/webhook/series-append', 'INLINE', '{\"deliveryId\":\"blog_series-chain-55f07fe784644443bcb013b66c7993cc-2\",\"event\":\"ITERATION_ADVANCED\",\"chainId\":\"blog_series-chain-55f07fe784644443bcb013b66c7993cc\",\"seq\":2,\"agentCode\":\"blog_series\",\"instanceId\":\"blog_series-7341c7a6d5614cd5bb91d0573c95af05\",\"context\":{\"prevSeq\":1,\"topic\":\"AI原生应用工程化落地\",\"next_article\":{\"title\":\"AI原生应用的架构设计：从单体到智能体编排\",\"content\":\"## 前言\\n在上一篇文章中，我们探讨了AI原生应用与传统软件的本质区别，以及工程化落地的核心挑战。今天，我们将深入架构层面，解析AI原生应用如何从传统的单体架构演进为智能体（Agent）编排模式，并讨论这种转变背后的设计原则与实战经验。\\n\\n## 单体架构的局限\\n传统的单体应用将所有功能模块（包括AI模型调用）打包在一个进程中。虽然初期开发简单，但随着AI能力增强，单体架构暴露出几个问题：\\n- **模型耦合**：模型更新或替换导致整个应用重新部署。\\n- **资源不均**：AI推理消耗大量GPU/CPU资源，与业务逻辑争抢资源。\\n- **扩展困难**：无法独立扩展AI服务与业务服务。\\n\\n## 微服务化的第一步：解耦AI能力\\n将AI模型封装为独立的微服务（如推理服务、特征服务）是常见的演进路径。例如，使用gRPC或REST API将大语言模型（LLM）调用暴露为服务，业务层通过异步消息队列调用。这种模式带来了：\\n- **独立部署**：模型更新不影响业务代码。\\n- **弹性伸缩**：根据推理请求量动态调整AI服务实例数。\\n- **技术多样性**：不同模型（如GPT-4、Claude）可共存于同一架构。\\n\\n## 智能体编排：AI原生架构的核心模式\\n随着AI应用复杂化（如多步骤推理、工具调用、记忆管理），单纯的微服务已不够。智能体编排模式应运而生，它将应用拆分为多个智能体（Agent），每个智能体负责一个子任务，并通过编排引擎协同工作。\\n\\n### 智能体设计原则\\n- **单一职责**：每个智能体只做一件事（如“代码生成智能体”或“数据检索智能体”）。\\n- **上下文隔离**：智能体间通过结构化消息传递，避免状态污染。\\n- **可观测性**：每个智能体暴露日志、指标，便于调试与监控。\\n\\n### 编排引擎的职责\\n编排引擎（如LangGraph、CrewAI或自研框架）负责：\\n- **路由**：根据用户请求，决定调用哪个智能体。\\n- **调度**：管理智能体执行顺序与并行。\\n- **容错**：处理智能体失败或超时，重试或降级。\\n- **记忆**：维护对话历史或任务上下文。\\n\\n### 实战案例：智能客服系统\\n假设我们要构建一个智能客服系统，传统做法是让一个LLM直接回答所有问题。但采用智能体编排后：\\n1. **意图识别智能体**：判断用户问题类型（退款、咨询、投诉）。\\n2. **知识库检索智能体**：从向量数据库检索相关文档。\\n3. **答案生成智能体**：基于检索结果生成回答。\\n4. **情绪检测智能体**：分析用户情绪，决定是否转人工。\\n\\n编排引擎依次调用这些智能体，每个智能体返回结构化结果，最终合成回复。这种设计使得每个智能体可以独立优化（如知识库检索智能体可单独升级为混合检索），且整体系统更鲁棒。\\n\\n## 架构演进中的陷阱与应对\\n- **过度拆分**：智能体过多导致编排复杂度飙升。建议开始时只有3-5个智能体，按需拆分。\\n- **状态同步**：多个智能体共享状态时，使用外部存储（如Redis）而非内存传递。\\n- **延迟累积**：串行调用智能体可能增加响应时间。采用并行调用或流式输出优化。\\n\\n## 总结\\nAI原生应用的架构设计是一个从单体到微服务，再到智能体编排的演进过程。关键在于：\\n1. 解耦AI能力与业务逻辑。\\n2. 以智能体为单位组织功能。\\n3. 使用编排引擎管理协作。\\n\\n下一篇文章，我们将深入讨论智能体编排中的关键技术：提示工程（Prompt Engineering）与工具调用（Function Calling）的最佳实践。\",\"outline_update\":\"无调整，保持原大纲。\",\"accumulated_update\":{\"used_examples\":[\"智能客服系统（意图识别、知识库检索、答案生成、情绪检测智能体）\",\"单体架构与微服务对比\",\"智能体编排引擎职责\"],\"covered_knowledge\":[\"单体架构局限\",\"AI能力微服务化\",\"智能体设计原则\",\"编排引擎职责\",\"架构陷阱与应对\"],\"style\":\"技术性、实战导向，包含案例与设计原则\"},\"series_complete\":false},\"seriesState\":\"{\\n  \\\"next_article\\\": {\\n    \\\"title\\\": \\\"AI原生应用的架构设计：从单体到智能体编排\\\",\\n    \\\"content\\\": \\\"## 前言\\\\n在上一篇文章中，我们探讨了AI原生应用与传统软件的本质区别，以及工程化落地的核心挑战。今天，我们将深入架构层面，解析AI原生应用如何从传统的单体架构演进为智能体（Agent）编排模式，并讨论这种转变背后的设计原则与实战经验。\\\\n\\\\n## 单体架构的局限\\\\n传统的单体应用将所有功能模块（包括AI模型调用）打包在一个进程中。虽然初期开发简单，但随着AI能力增强，单体架构暴露出几个问题：\\\\n- **模型耦合**：模型更新或替换导致整个应用重新部署。\\\\n- **资源不均**：AI推理消耗大量GPU/CPU资源，与业务逻辑争抢资源。\\\\n- **扩展困难**：无法独立扩展AI服务与业务服务。\\\\n\\\\n## 微服务化的第一步：解耦AI能力\\\\n将AI模型封装为独立的微服务（如推理服务、特征服务）是常见的演进路径。例如，使用gRPC或REST API将大语言模型（LLM）调用暴露为服务，业务层通过异步消息队列调用。这种模式带来了：\\\\n- **独立部署**：模型更新不影响业务代码。\\\\n- **弹性伸缩**：根据推理请求量动态调整AI服务实例数。\\\\n- **技术多样性**：不同模型（如GPT-4、Claude）可共存于同一架构。\\\\n\\\\n## 智能体编排：AI原生架构的核心模式\\\\n随着AI应用复杂化（如多步骤推理、工具调用、记忆管理），单纯的微服务已不够。智能体编排模式应运而生，它将应用拆分为多个智能体（Agent），每个智能体负责一个子任务，并通过编排引擎协同工作。\\\\n\\\\n### 智能体设计原则\\\\n- **单一职责**：每个智能体只做一件事（如“代码生成智能体”或“数据检索智能体”）。\\\\n- **上下文隔离**：智能体间通过结构化消息传递，避免状态污染。\\\\n- **可观测性**：每个智能体暴露日志、指标，便于调试与监控。\\\\n\\\\n### 编排引擎的职责\\\\n编排引擎（如LangGraph、CrewAI或自研框架）负责：\\\\n- **路由**：根据用户请求，决定调用哪个智能体。\\\\n- **调度**：管理智能体执行顺序与并行。\\\\n- **容错**：处理智能体失败或超时，重试或降级。\\\\n- **记忆**：维护对话历史或任务上下文。\\\\n\\\\n### 实战案例：智能客服系统\\\\n假设我们要构建一个智能客服系统，传统做法是让一个LLM直接回答所有问题。但采用智能体编排后：\\\\n1. **意图识别智能体**：判断用户问题类型（退款、咨询、投诉）。\\\\n2. **知识库检索智能体**：从向量数据库检索相关文档。\\\\n3. **答案生成智能体**：基于检索结果生成回答。\\\\n4. **情绪检测智能体**：分析用户情绪，决定是否转人工。\\\\n\\\\n编排引擎依次调用这些智能体，每个智能体返回结构化结果，最终合成回复。这种设计使得每个智能体可以独立优化（如知识库检索智能体可单独升级为混合检索），且整体系统更鲁棒。\\\\n\\\\n## 架构演进中的陷阱与应对\\\\n- **过度拆分**：智能体过多导致编排复杂度飙升。建议开始时只有3-5个智能体，按需拆分。\\\\n- **状态同步**：多个智能体共享状态时，使用外部存储（如Redis）而非内存传递。\\\\n- **延迟累积**：串行调用智能体可能增加响应时间。采用并行调用或流式输出优化。\\\\n\\\\n## 总结\\\\nAI原生应用的架构设计是一个从单体到微服务，再到智能体编排的演进过程。关键在于：\\\\n1. 解耦AI能力与业务逻辑。\\\\n2. 以智能体为单位组织功能。\\\\n3. 使用编排引擎管理协作。\\\\n\\\\n下一篇文章，我们将深入讨论智能体编排中的关键技术：提示工程（Prompt Engineering）与工具调用（Function Calling）的最佳实践。\\\",\\n    \\\"outline_update\\\": \\\"无调整，保持原大纲。\\\",\\n    \\\"accumulated_update\\\": {\\n      \\\"used_examples\\\": [\\\"智能客服系统（意图识别、知识库检索、答案生成、情绪检测智能体）\\\", \\\"单体架构与微服务对比\\\", \\\"智能体编排引擎职责\\\"],\\n      \\\"covered_knowledge\\\": [\\\"单体架构局限\\\", \\\"AI能力微服务化\\\", \\\"智能体设计原则\\\", \\\"编排引擎职责\\\", \\\"架构陷阱与应对\\\"],\\n      \\\"style\\\": \\\"技术性、实战导向，包含案例与设计原则\\\"\\n    },\\n    \\\"series_complete\\\": false\\n  }\\n}\",\"seriesName\":\"AI原生应用工程化落地\"}}', 'SUCCESS', 1, 8, 200, NULL, NULL, '2026-07-09 16:11:56', '2026-07-09 16:11:56');

-- ----------------------------
-- Table structure for blog_category
-- ----------------------------
DROP TABLE IF EXISTS `blog_category`;
CREATE TABLE `blog_category`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '分类名称',
  `slug` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '分类URL标识',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '分类描述',
  `parent_id` bigint(20) NULL DEFAULT NULL COMMENT '父分类ID（用于层级分类）',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序序号（升序）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_blog_category_slug`(`slug` ASC) USING BTREE,
  UNIQUE INDEX `uk_blog_category_name`(`name` ASC) USING BTREE,
  INDEX `idx_blog_category_parent_id`(`parent_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 41 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客分类表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_category
-- ----------------------------
INSERT INTO `blog_category` VALUES (39, 'AI', 'ai', '指利用机器学习、深度学习等人工智能技术，对文章内容进行自动化识别与归类的过程。它区别于人工分类，能够快速、批量地根据文章的语义、关键词、情感倾向或主题（如科技、财经、娱乐等）生成标签，常用于内容推荐、垃圾过滤和知识管理。', NULL, 0, '2026-06-08 19:51:31', '2026-06-08 19:51:31');
INSERT INTO `blog_category` VALUES (40, 'AIAgent', 'ai-agent', '指能够感知环境、进行自主理解、决策并执行动作以实现特定目标的智能实体。它区别于传统的人工智能模型（仅提供问答或生成内容），具备自主性、反应性、主动性和社会性。典型特征包括：能调用外部工具（如搜索引擎、计算器、API）、拥有长期与短期记忆、可进行多步推理与任务规划。', 39, 0, '2026-06-08 19:52:21', '2026-06-08 19:52:21');

-- ----------------------------
-- Table structure for blog_content_version
-- ----------------------------
DROP TABLE IF EXISTS `blog_content_version`;
CREATE TABLE `blog_content_version`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '版本ID',
  `post_id` bigint(20) NOT NULL COMMENT '文章ID',
  `version_no` int(11) NOT NULL COMMENT '版本号',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '版本标题',
  `summary` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '版本摘要',
  `content_file_id` bigint(20) NOT NULL COMMENT '版本正文文件ID',
  `cover_file_id` bigint(20) NULL DEFAULT NULL COMMENT '版本封面文件ID',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '该版本的状态快照',
  `visibility` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '该版本的可见性快照',
  `change_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'manual' COMMENT '变更类型：manual(手动)/auto(自动)',
  `change_note` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '变更备注',
  `creator_id` bigint(20) NULL DEFAULT NULL COMMENT '创建者用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_blog_version_post_version`(`post_id` ASC, `version_no` ASC) USING BTREE,
  INDEX `idx_blog_version_post_id`(`post_id` ASC) USING BTREE,
  INDEX `idx_blog_version_file_id`(`content_file_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 38 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客内容版本表（完整快照）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_content_version
-- ----------------------------
INSERT INTO `blog_content_version` VALUES (22, 12, 1, '第十章 智能体通信协议', NULL, 40, NULL, 'draft', 'public', 'manual', NULL, 2052290101098295297, '2026-06-11 11:08:57');
INSERT INTO `blog_content_version` VALUES (23, 3, 1, '第一章 初识智能体', NULL, 31, NULL, 'draft', 'public', 'manual', NULL, 2052290101098295297, '2026-06-11 11:10:03');
INSERT INTO `blog_content_version` VALUES (24, 3, 2, '第一章 初识智能体', NULL, 42, NULL, 'draft', 'public', 'manual', NULL, 2052290101098295297, '2026-06-11 11:10:26');
INSERT INTO `blog_content_version` VALUES (25, 3, 3, '第一章 初识智能体', NULL, 43, NULL, 'draft', 'public', 'manual', NULL, 2052290101098295297, '2026-06-11 11:11:26');
INSERT INTO `blog_content_version` VALUES (26, 4, 1, '第二章 智能体发展史', NULL, 32, NULL, 'draft', 'public', 'manual', NULL, 2052290101098295297, '2026-06-11 11:18:25');
INSERT INTO `blog_content_version` VALUES (27, 5, 1, '第三章 大语言模型基础', NULL, 33, NULL, 'draft', 'public', 'manual', NULL, 2052290101098295297, '2026-06-11 11:19:12');
INSERT INTO `blog_content_version` VALUES (28, 6, 1, '第四章 智能体经典范式构建', NULL, 34, NULL, 'draft', 'public', 'manual', NULL, 2052290101098295297, '2026-06-11 11:20:07');
INSERT INTO `blog_content_version` VALUES (29, 7, 1, '第五章 基于低代码平台的智能体搭建', NULL, 35, NULL, 'draft', 'public', 'manual', NULL, 2052290101098295297, '2026-06-11 11:20:24');
INSERT INTO `blog_content_version` VALUES (30, 8, 1, '第六章 框架开发实践', NULL, 36, NULL, 'draft', 'public', 'manual', NULL, 2052290101098295297, '2026-06-11 11:22:15');
INSERT INTO `blog_content_version` VALUES (31, 9, 1, '第七章 构建你的智能体框架', NULL, 37, NULL, 'draft', 'public', 'manual', NULL, 2052290101098295297, '2026-06-11 11:22:32');
INSERT INTO `blog_content_version` VALUES (32, 10, 1, '第八章 记忆与检索', NULL, 38, NULL, 'draft', 'public', 'manual', NULL, 2052290101098295297, '2026-06-11 11:22:43');
INSERT INTO `blog_content_version` VALUES (33, 11, 1, '第九章 上下文工程', NULL, 39, NULL, 'draft', 'public', 'manual', NULL, 2052290101098295297, '2026-06-11 11:22:56');
INSERT INTO `blog_content_version` VALUES (34, 12, 2, '第十章 智能体通信协议', NULL, 41, NULL, 'draft', 'public', 'manual', NULL, 2052290101098295297, '2026-06-11 11:23:09');
INSERT INTO `blog_content_version` VALUES (35, 5, 2, '第三章 大语言模型基础', NULL, 46, NULL, 'published', 'public', 'manual', NULL, 2052290101098295297, '2026-06-30 10:21:05');
INSERT INTO `blog_content_version` VALUES (36, 6, 2, '第四章 智能体经典范式构建', NULL, 47, NULL, 'published', 'public', 'manual', NULL, 2052290101098295297, '2026-06-30 10:21:14');
INSERT INTO `blog_content_version` VALUES (37, 7, 2, '第五章 基于低代码平台的智能体搭建', NULL, 48, NULL, 'published', 'public', 'manual', NULL, 2052290101098295297, '2026-06-30 10:21:20');

-- ----------------------------
-- Table structure for blog_file_asset
-- ----------------------------
DROP TABLE IF EXISTS `blog_file_asset`;
CREATE TABLE `blog_file_asset`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '文件ID',
  `storage_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '存储类型：local(本地)/oss(对象存储)',
  `bucket` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'OSS bucket名称（本地存储时可为空）',
  `object_key` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '存储路径，如 posts/2026/04/xxx.md',
  `url` varchar(750) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '访问URL',
  `filename` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '原始文件名',
  `extension` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件扩展名（如 .md, .jpg）',
  `mime_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'MIME类型（如 text/markdown, image/jpeg）',
  `size_bytes` bigint(20) NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
  `hash_sha256` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件SHA256哈希值（用于去重/校验）',
  `file_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'other' COMMENT '文件用途：markdown/image/attachment/cover/other',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_blog_file_storage_key`(`storage_type` ASC, `object_key`(100) ASC) USING BTREE,
  INDEX `idx_blog_file_hash`(`hash_sha256` ASC) USING BTREE,
  INDEX `idx_blog_file_type`(`file_type` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 62 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客文件资源表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_file_asset
-- ----------------------------
INSERT INTO `blog_file_asset` VALUES (31, 'oss', 'nebula', 'posts/2026/06/11/e3c79b743d074b4898412b82c8c14b10.md', 'http://oss.orccode.com/nebula/posts/2026/06/11/e3c79b743d074b4898412b82c8c14b10.md', 'post-090bd20c.md', '.md', 'text/markdown', 55420, '08a44f13cecd67df260c3d7de4b7ddfe479a719b578bac9b1fce78c6b30fbbfc', 'markdown', '2026-06-11 09:52:41', '2026-06-11 09:52:41');
INSERT INTO `blog_file_asset` VALUES (32, 'oss', 'nebula', 'posts/2026/06/11/038061f21a384aeab1f6c936b1056460.md', 'http://oss.orccode.com/nebula/posts/2026/06/11/038061f21a384aeab1f6c936b1056460.md', 'post-1eb479fd.md', '.md', 'text/markdown', 55998, 'f84584dab0aa393edda08339122d85ed7270d4084e6c3ca7d5b94e533d3ed56a', 'markdown', '2026-06-11 10:53:39', '2026-06-11 10:53:39');
INSERT INTO `blog_file_asset` VALUES (33, 'oss', 'nebula', 'posts/2026/06/11/db538ed6a5a948eeb97b9f78e3f72f64.md', 'http://oss.orccode.com/nebula/posts/2026/06/11/db538ed6a5a948eeb97b9f78e3f72f64.md', 'post-eb78a873.md', '.md', 'text/markdown', 79354, '02f9774029850e2829eb341de382d6c672b6db8509b73037f7ae888c2d0f8d9a', 'markdown', '2026-06-11 10:53:57', '2026-06-11 10:53:57');
INSERT INTO `blog_file_asset` VALUES (34, 'oss', 'nebula', 'posts/2026/06/11/d338ae6ed70e416baeb87ffc52f434c2.md', 'http://oss.orccode.com/nebula/posts/2026/06/11/d338ae6ed70e416baeb87ffc52f434c2.md', 'post-0b102cb2.md', '.md', 'text/markdown', 75498, '65dae90fdf991470ab564f15d959c5bbec3510e114a2493da78662b9a5ccf5b8', 'markdown', '2026-06-11 10:54:05', '2026-06-11 10:54:05');
INSERT INTO `blog_file_asset` VALUES (35, 'oss', 'nebula', 'posts/2026/06/11/5f8f18b6db6d46fc991f75a5d4291299.md', 'http://oss.orccode.com/nebula/posts/2026/06/11/5f8f18b6db6d46fc991f75a5d4291299.md', 'post-257225ef.md', '.md', 'text/markdown', 74646, '76d8c10db243800a4a10716aba036bc6bb8aaf902fe28a39b200756851f8b572', 'markdown', '2026-06-11 10:56:03', '2026-06-11 10:56:03');
INSERT INTO `blog_file_asset` VALUES (36, 'oss', 'nebula', 'posts/2026/06/11/1f6d44423ea7431b86df7f54a91f219a.md', 'http://oss.orccode.com/nebula/posts/2026/06/11/1f6d44423ea7431b86df7f54a91f219a.md', 'post-89031735.md', '.md', 'text/markdown', 86567, 'e47b38bc7fc7c6a8d361c5d555cf89e92c9a6a99e1fcb2a3792a8e92f26ea8cf', 'markdown', '2026-06-11 10:56:13', '2026-06-11 10:56:13');
INSERT INTO `blog_file_asset` VALUES (37, 'oss', 'nebula', 'posts/2026/06/11/51d766c8f3454059b48c6772462aa2b5.md', 'http://oss.orccode.com/nebula/posts/2026/06/11/51d766c8f3454059b48c6772462aa2b5.md', 'post-ae41e1cd.md', '.md', 'text/markdown', 93882, 'de001f45daef3e73359b4656bf240b25be7f46ecb93e47481de6b90f13a3a7cf', 'markdown', '2026-06-11 10:56:21', '2026-06-11 10:56:21');
INSERT INTO `blog_file_asset` VALUES (38, 'oss', 'nebula', 'posts/2026/06/11/1debb85c84da445fad08ae51f5f06070.md', 'http://oss.orccode.com/nebula/posts/2026/06/11/1debb85c84da445fad08ae51f5f06070.md', 'post-dd715022.md', '.md', 'text/markdown', 95819, '2b2ae03fd679655484309dfaaf191f12648301fc02967a228e1a398fa0032e7a', 'markdown', '2026-06-11 10:56:45', '2026-06-11 10:56:45');
INSERT INTO `blog_file_asset` VALUES (39, 'oss', 'nebula', 'posts/2026/06/11/594a0361d61344fd9a4173302eb7fd86.md', 'http://oss.orccode.com/nebula/posts/2026/06/11/594a0361d61344fd9a4173302eb7fd86.md', 'post-2f380844.md', '.md', 'text/markdown', 106785, '79cbe7c48856540841939c15aabcb341f0c3a426cc575c472f4edf534e49288a', 'markdown', '2026-06-11 10:56:52', '2026-06-11 10:56:52');
INSERT INTO `blog_file_asset` VALUES (40, 'oss', 'nebula', 'posts/2026/06/11/e8dae8b6b9d4486c8b451b767b8cc3c5.md', 'http://oss.orccode.com/nebula/posts/2026/06/11/e8dae8b6b9d4486c8b451b767b8cc3c5.md', 'post-1cdb0829.md', '.md', 'text/markdown', 92163, '513455bc47400b7683e52669c7dbfc30cdf5dadac41c2c87eff5c4d3a22f8c8d', 'markdown', '2026-06-11 10:57:31', '2026-06-11 10:57:31');
INSERT INTO `blog_file_asset` VALUES (41, 'oss', 'nebula', 'posts/2026/06/11/6f10ebeb6f46496fa3ceede18c43a459.md', 'http://oss.orccode.com/nebula/posts/2026/06/11/6f10ebeb6f46496fa3ceede18c43a459.md', 'post-1cdb0829.md', '.md', 'text/markdown', 92163, '513455bc47400b7683e52669c7dbfc30cdf5dadac41c2c87eff5c4d3a22f8c8d', 'markdown', '2026-06-11 11:08:59', '2026-06-11 11:08:59');
INSERT INTO `blog_file_asset` VALUES (42, 'oss', 'nebula', 'posts/2026/06/11/3f14811a47a445019e3ad3cc100436e9.md', 'http://oss.orccode.com/nebula/posts/2026/06/11/3f14811a47a445019e3ad3cc100436e9.md', 'post-090bd20c.md', '.md', 'text/markdown', 54934, '9d14ba64a2998bcbea51e4a609c3a3b704688ef752b0d6113959a73a59ad30fc', 'markdown', '2026-06-11 11:10:04', '2026-06-11 11:10:04');
INSERT INTO `blog_file_asset` VALUES (43, 'oss', 'nebula', 'posts/2026/06/11/ba6ee6c7ff634a508ec11fe29ba21c43.md', 'http://oss.orccode.com/nebula/posts/2026/06/11/ba6ee6c7ff634a508ec11fe29ba21c43.md', 'post-090bd20c.md', '.md', 'text/markdown', 54934, '9d14ba64a2998bcbea51e4a609c3a3b704688ef752b0d6113959a73a59ad30fc', 'markdown', '2026-06-11 11:10:27', '2026-06-11 11:10:27');
INSERT INTO `blog_file_asset` VALUES (44, 'oss', 'nebula', 'posts/2026/06/11/64297a14c16f4127b0034728ef6526f2.md', 'http://oss.orccode.com/nebula/posts/2026/06/11/64297a14c16f4127b0034728ef6526f2.md', 'post-090bd20c.md', '.md', 'text/markdown', 54934, '9d14ba64a2998bcbea51e4a609c3a3b704688ef752b0d6113959a73a59ad30fc', 'markdown', '2026-06-11 11:11:27', '2026-06-11 11:11:27');
INSERT INTO `blog_file_asset` VALUES (45, 'oss', 'nebula', 'posts/2026/06/11/8b61461658c144b59f30bf369d1b85a5.md', 'http://oss.orccode.com/nebula/posts/2026/06/11/8b61461658c144b59f30bf369d1b85a5.md', 'post-1eb479fd.md', '.md', 'text/markdown', 55998, 'f84584dab0aa393edda08339122d85ed7270d4084e6c3ca7d5b94e533d3ed56a', 'markdown', '2026-06-11 11:18:26', '2026-06-11 11:18:26');
INSERT INTO `blog_file_asset` VALUES (46, 'oss', 'nebula', 'posts/2026/06/11/d79ba8bbafae445281a476a24fee74ea.md', 'http://oss.orccode.com/nebula/posts/2026/06/11/d79ba8bbafae445281a476a24fee74ea.md', 'post-eb78a873.md', '.md', 'text/markdown', 79354, '02f9774029850e2829eb341de382d6c672b6db8509b73037f7ae888c2d0f8d9a', 'markdown', '2026-06-11 11:19:13', '2026-06-11 11:19:13');
INSERT INTO `blog_file_asset` VALUES (47, 'oss', 'nebula', 'posts/2026/06/11/722cb7421833482c89ff6a76dd878b38.md', 'http://oss.orccode.com/nebula/posts/2026/06/11/722cb7421833482c89ff6a76dd878b38.md', 'post-0b102cb2.md', '.md', 'text/markdown', 75498, '65dae90fdf991470ab564f15d959c5bbec3510e114a2493da78662b9a5ccf5b8', 'markdown', '2026-06-11 11:20:08', '2026-06-11 11:20:08');
INSERT INTO `blog_file_asset` VALUES (48, 'oss', 'nebula', 'posts/2026/06/11/367111932520426f859223038c511618.md', 'http://oss.orccode.com/nebula/posts/2026/06/11/367111932520426f859223038c511618.md', 'post-257225ef.md', '.md', 'text/markdown', 74646, '76d8c10db243800a4a10716aba036bc6bb8aaf902fe28a39b200756851f8b572', 'markdown', '2026-06-11 11:20:30', '2026-06-11 11:20:30');
INSERT INTO `blog_file_asset` VALUES (49, 'oss', 'nebula', 'posts/2026/06/11/ffdc7f3977044143b18fdd8264e8d204.md', 'http://oss.orccode.com/nebula/posts/2026/06/11/ffdc7f3977044143b18fdd8264e8d204.md', 'post-89031735.md', '.md', 'text/markdown', 86567, 'e47b38bc7fc7c6a8d361c5d555cf89e92c9a6a99e1fcb2a3792a8e92f26ea8cf', 'markdown', '2026-06-11 11:22:16', '2026-06-11 11:22:16');
INSERT INTO `blog_file_asset` VALUES (50, 'oss', 'nebula', 'posts/2026/06/11/4e2d14a88c724e3bb47428d4e6b1bb46.md', 'http://oss.orccode.com/nebula/posts/2026/06/11/4e2d14a88c724e3bb47428d4e6b1bb46.md', 'post-ae41e1cd.md', '.md', 'text/markdown', 93882, 'de001f45daef3e73359b4656bf240b25be7f46ecb93e47481de6b90f13a3a7cf', 'markdown', '2026-06-11 11:22:33', '2026-06-11 11:22:33');
INSERT INTO `blog_file_asset` VALUES (51, 'oss', 'nebula', 'posts/2026/06/11/20eefd8a1bbf4e2f9a79794c2ec9a800.md', 'http://oss.orccode.com/nebula/posts/2026/06/11/20eefd8a1bbf4e2f9a79794c2ec9a800.md', 'post-dd715022.md', '.md', 'text/markdown', 95819, '2b2ae03fd679655484309dfaaf191f12648301fc02967a228e1a398fa0032e7a', 'markdown', '2026-06-11 11:22:45', '2026-06-11 11:22:45');
INSERT INTO `blog_file_asset` VALUES (52, 'oss', 'nebula', 'posts/2026/06/11/2de697f205df4271ab1c1de97437465e.md', 'http://oss.orccode.com/nebula/posts/2026/06/11/2de697f205df4271ab1c1de97437465e.md', 'post-2f380844.md', '.md', 'text/markdown', 106785, '79cbe7c48856540841939c15aabcb341f0c3a426cc575c472f4edf534e49288a', 'markdown', '2026-06-11 11:22:57', '2026-06-11 11:22:57');
INSERT INTO `blog_file_asset` VALUES (53, 'oss', 'nebula', 'posts/2026/06/11/0234db3f19f6405bb6d14649ce7ea28c.md', 'http://oss.orccode.com/nebula/posts/2026/06/11/0234db3f19f6405bb6d14649ce7ea28c.md', 'post-1cdb0829.md', '.md', 'text/markdown', 92163, '513455bc47400b7683e52669c7dbfc30cdf5dadac41c2c87eff5c4d3a22f8c8d', 'markdown', '2026-06-11 11:23:10', '2026-06-11 11:23:10');
INSERT INTO `blog_file_asset` VALUES (54, 'oss', 'nebula', 'covers/2026/06/11/ae1fd4c23cae40f0bb45579f2516be6c.png', 'http://oss.orccode.com/nebula/covers/2026/06/11/ae1fd4c23cae40f0bb45579f2516be6c.png?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=nebula%2F20260611%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20260611T034922Z&X-Amz-Expires=3600&X-Amz-SignedHeaders=host&X-Amz-Signature=eba0391f19ef8723ccfd7e274f19bdeac3ef45dcf7f9269aa30366af74f92576', 'image_199270782777520.png', '.png', 'image/png', 1817591, 'f26fdd7d38e76aac3ad6d709c15e22cddd4d2d1f2f6a48a3082f9a240866de3b', 'cover', '2026-06-11 11:49:22', '2026-06-11 11:49:22');
INSERT INTO `blog_file_asset` VALUES (55, 'oss', 'nebula', 'covers/2026/06/11/5579b5b10eea4160ad8e78b7d511e4a1.jpg', 'http://oss.orccode.com/nebula/covers/2026/06/11/5579b5b10eea4160ad8e78b7d511e4a1.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=nebula%2F20260611%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20260611T092003Z&X-Amz-Expires=3600&X-Amz-SignedHeaders=host&X-Amz-Signature=41b6751b6e0642cf45d80f5c19cfd95dc413337196914d4762d8c7fc87848fab', '0c6ef65c32ac14a9f5aa9f4c3eb9d7f3.jpg', '.jpg', 'image/jpeg', 112251, '764766fda48bd33827beabdb16c7c94d7876af3ec7a346bb962ca23a88b42653', 'cover', '2026-06-11 17:20:04', '2026-06-11 17:20:04');
INSERT INTO `blog_file_asset` VALUES (56, 'oss', 'nebula', 'covers/2026/06/11/3f5e14818c764f90a5048245d834defd.jpg', 'http://oss.orccode.com/nebula/covers/2026/06/11/3f5e14818c764f90a5048245d834defd.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=nebula%2F20260611%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20260611T092526Z&X-Amz-Expires=3600&X-Amz-SignedHeaders=host&X-Amz-Signature=5db5a8b220fd240f9250964742ffa3386ff64d05cc65d408a4fe279fe77df56b', '500c226b10286d6499347f3b5a2a7828.jpg', '.jpg', 'image/jpeg', 201441, '8e0bf1a0b753d7f3aa72327af282f74f3cf3b6cb8cc25ed0996af5b3159bce61', 'cover', '2026-06-11 17:25:26', '2026-06-11 17:25:26');
INSERT INTO `blog_file_asset` VALUES (57, 'oss', 'nebula', 'covers/2026/06/11/66b9b705dfed44a9a357bb82d76fdb5b.jpg', 'http://oss.orccode.com/nebula/covers/2026/06/11/66b9b705dfed44a9a357bb82d76fdb5b.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=nebula%2F20260611%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20260611T094314Z&X-Amz-Expires=3600&X-Amz-SignedHeaders=host&X-Amz-Signature=07d6688cdc9b05f4d218ad5a22815192e3f4777b136d27db1e900b73f1a2cd33', '0371f546c20b9853200d5f0821700331.jpg', '.jpg', 'image/jpeg', 135348, '4bec2bebb6d2db2c47e2101cda76df9db79c8a24634441635b192f98832e3f65', 'cover', '2026-06-11 17:43:15', '2026-06-11 17:43:15');
INSERT INTO `blog_file_asset` VALUES (58, 'oss', 'nebula', 'posts/2026/06/30/01973b11135b451ba33de564cd3e65fd.md', 'http://oss.orccode.com/nebula/posts/2026/06/30/01973b11135b451ba33de564cd3e65fd.md', 'post-eb78a873.md', '.md', 'text/markdown', 79354, '02f9774029850e2829eb341de382d6c672b6db8509b73037f7ae888c2d0f8d9a', 'markdown', '2026-06-30 10:21:05', '2026-06-30 10:21:05');
INSERT INTO `blog_file_asset` VALUES (59, 'oss', 'nebula', 'posts/2026/06/30/fb6e372bf3f34fbebe57542a0e00a980.md', 'http://oss.orccode.com/nebula/posts/2026/06/30/fb6e372bf3f34fbebe57542a0e00a980.md', 'post-0b102cb2.md', '.md', 'text/markdown', 75498, '65dae90fdf991470ab564f15d959c5bbec3510e114a2493da78662b9a5ccf5b8', 'markdown', '2026-06-30 10:21:14', '2026-06-30 10:21:14');
INSERT INTO `blog_file_asset` VALUES (60, 'oss', 'nebula', 'posts/2026/06/30/50c12b246e2749e494dfd1f2d0bea988.md', 'http://oss.orccode.com/nebula/posts/2026/06/30/50c12b246e2749e494dfd1f2d0bea988.md', 'post-257225ef.md', '.md', 'text/markdown', 74646, '76d8c10db243800a4a10716aba036bc6bb8aaf902fe28a39b200756851f8b572', 'markdown', '2026-06-30 10:21:20', '2026-06-30 10:21:20');
INSERT INTO `blog_file_asset` VALUES (61, 'oss', 'nebula', 'posts/2026/07/09/fcaf2a95a28343d299490255ec93dcfb.md', 'http://oss.orccode.com/nebula/posts/2026/07/09/fcaf2a95a28343d299490255ec93dcfb.md', 'series-blogserieswriterchaindemo02-8.md', '.md', 'text/markdown', 5150, 'c61de5824deae49ba8f6a4a061546304a3a71793f4ed50e2de1475ed89759185', 'markdown', '2026-07-09 12:02:27', '2026-07-09 12:02:27');

-- ----------------------------
-- Table structure for blog_post
-- ----------------------------
DROP TABLE IF EXISTS `blog_post`;
CREATE TABLE `blog_post`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '文章ID',
  `author_id` bigint(20) NOT NULL COMMENT '作者用户ID',
  `post_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'article' COMMENT '内容类型：article(文章)/essay(随笔)',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文章标题',
  `slug` varchar(220) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'URL唯一标识',
  `summary` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '摘要',
  `content_file_id` bigint(20) NOT NULL COMMENT 'Markdown正文文件ID（关联blog_file_asset）',
  `cover_file_id` bigint(20) NULL DEFAULT NULL COMMENT '封面文件ID（关联blog_file_asset）',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'draft' COMMENT '状态：draft(草稿)/published(已发布)/archived(已归档)',
  `visibility` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'public' COMMENT '可见性：public(公开)/private(私有)',
  `source_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'manual' COMMENT '来源：manual(手动)/ai(AI生成)/import(导入)',
  `is_original` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否原创：1-是，0-否',
  `view_count` int(10) UNSIGNED NOT NULL DEFAULT 0 COMMENT '浏览次数',
  `like_count` int(10) UNSIGNED NOT NULL DEFAULT 0 COMMENT '点赞次数',
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
) ENGINE = InnoDB AUTO_INCREMENT = 14 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客文章主表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_post
-- ----------------------------
INSERT INTO `blog_post` VALUES (3, 2052290101098295297, 'article', '第一章 初识智能体', 'post-090bd20c', NULL, 44, NULL, 'published', 'public', 'import', 1, 56, 0, '2026-06-11 03:11:24', '2026-06-11 09:52:41', '2026-06-30 12:44:02');
INSERT INTO `blog_post` VALUES (4, 2052290101098295297, 'article', '第二章 智能体发展史', 'post-1eb479fd', NULL, 45, NULL, 'published', 'public', 'import', 1, 8, 0, '2026-06-11 03:18:24', '2026-06-11 10:53:39', '2026-06-30 10:17:45');
INSERT INTO `blog_post` VALUES (5, 2052290101098295297, 'article', '第三章 大语言模型基础', 'post-eb78a873', NULL, 58, NULL, 'published', 'public', 'import', 1, 2, 0, '2026-06-11 03:19:10', '2026-06-11 10:53:57', '2026-06-30 10:17:46');
INSERT INTO `blog_post` VALUES (6, 2052290101098295297, 'article', '第四章 智能体经典范式构建', 'post-0b102cb2', NULL, 59, NULL, 'published', 'public', 'import', 1, 1, 0, '2026-06-11 03:20:06', '2026-06-11 10:54:05', '2026-06-30 10:17:48');
INSERT INTO `blog_post` VALUES (7, 2052290101098295297, 'article', '第五章 基于低代码平台的智能体搭建', 'post-257225ef', NULL, 60, NULL, 'published', 'public', 'import', 1, 1, 0, '2026-06-11 03:20:23', '2026-06-11 10:56:03', '2026-06-30 10:17:58');
INSERT INTO `blog_post` VALUES (8, 2052290101098295297, 'article', '第六章 框架开发实践', 'post-89031735', NULL, 49, NULL, 'published', 'public', 'import', 1, 2, 0, '2026-06-11 03:22:14', '2026-06-11 10:56:13', '2026-06-30 10:17:59');
INSERT INTO `blog_post` VALUES (9, 2052290101098295297, 'article', '第七章 构建你的智能体框架', 'post-ae41e1cd', NULL, 50, NULL, 'published', 'public', 'import', 1, 1, 0, '2026-06-11 03:22:31', '2026-06-11 10:56:21', '2026-06-30 10:18:01');
INSERT INTO `blog_post` VALUES (10, 2052290101098295297, 'article', '第八章 记忆与检索', 'post-dd715022', NULL, 51, NULL, 'published', 'public', 'import', 1, 3, 0, '2026-06-11 03:22:42', '2026-06-11 10:56:45', '2026-06-30 10:14:40');
INSERT INTO `blog_post` VALUES (11, 2052290101098295297, 'article', '第九章 上下文工程', 'post-2f380844', NULL, 52, NULL, 'published', 'public', 'import', 1, 3, 0, '2026-06-11 03:22:55', '2026-06-11 10:56:52', '2026-06-30 10:14:30');
INSERT INTO `blog_post` VALUES (12, 2052290101098295297, 'article', '第十章 智能体通信协议', 'post-1cdb0829', NULL, 53, NULL, 'published', 'public', 'import', 1, 9, 0, '2026-06-11 03:23:08', '2026-06-11 10:57:31', '2026-07-01 16:11:25');

-- ----------------------------
-- Table structure for blog_post_category
-- ----------------------------
DROP TABLE IF EXISTS `blog_post_category`;
CREATE TABLE `blog_post_category`  (
  `post_id` bigint(20) NOT NULL COMMENT '文章ID',
  `category_id` bigint(20) NOT NULL COMMENT '分类ID',
  PRIMARY KEY (`post_id`, `category_id`) USING BTREE,
  INDEX `idx_blog_post_category_category_id`(`category_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客文章分类关系表（多对多）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_post_category
-- ----------------------------
INSERT INTO `blog_post_category` VALUES (6, 39);
INSERT INTO `blog_post_category` VALUES (7, 39);
INSERT INTO `blog_post_category` VALUES (8, 39);
INSERT INTO `blog_post_category` VALUES (9, 39);
INSERT INTO `blog_post_category` VALUES (10, 39);
INSERT INTO `blog_post_category` VALUES (11, 39);
INSERT INTO `blog_post_category` VALUES (3, 40);
INSERT INTO `blog_post_category` VALUES (4, 40);
INSERT INTO `blog_post_category` VALUES (5, 40);
INSERT INTO `blog_post_category` VALUES (6, 40);
INSERT INTO `blog_post_category` VALUES (7, 40);
INSERT INTO `blog_post_category` VALUES (8, 40);
INSERT INTO `blog_post_category` VALUES (9, 40);
INSERT INTO `blog_post_category` VALUES (10, 40);
INSERT INTO `blog_post_category` VALUES (11, 40);
INSERT INTO `blog_post_category` VALUES (12, 40);

-- ----------------------------
-- Table structure for blog_post_import_item
-- ----------------------------
DROP TABLE IF EXISTS `blog_post_import_item`;
CREATE TABLE `blog_post_import_item`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `task_id` bigint(20) NOT NULL COMMENT '所属任务ID',
  `filename` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `success` tinyint(1) NOT NULL DEFAULT 0,
  `article_id` bigint(20) NULL DEFAULT NULL COMMENT '成功时的文章ID',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `slug` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `error` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_blog_post_import_item_task`(`task_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 10 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客文章导入明细表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_post_import_item
-- ----------------------------
INSERT INTO `blog_post_import_item` VALUES (1, 1, '第二章 智能体发展史.md', 1, 4, '第二章 智能体发展史', 'post-1eb479fd', NULL, '2026-06-11 10:53:42');
INSERT INTO `blog_post_import_item` VALUES (2, 1, '第三章 大语言模型基础.md', 1, 5, '第三章 大语言模型基础', 'post-eb78a873', NULL, '2026-06-11 10:53:58');
INSERT INTO `blog_post_import_item` VALUES (3, 1, '第四章 智能体经典范式构建.md', 1, 6, '第四章 智能体经典范式构建', 'post-0b102cb2', NULL, '2026-06-11 10:54:06');
INSERT INTO `blog_post_import_item` VALUES (4, 1, '第五章 基于低代码平台的智能体搭建.md', 1, 7, '第五章 基于低代码平台的智能体搭建', 'post-257225ef', NULL, '2026-06-11 10:56:04');
INSERT INTO `blog_post_import_item` VALUES (5, 1, '第六章 框架开发实践.md', 1, 8, '第六章 框架开发实践', 'post-89031735', NULL, '2026-06-11 10:56:14');
INSERT INTO `blog_post_import_item` VALUES (6, 1, '第七章 构建你的Agent框架.md', 1, 9, '第七章 构建你的智能体框架', 'post-ae41e1cd', NULL, '2026-06-11 10:56:22');
INSERT INTO `blog_post_import_item` VALUES (7, 1, '第八章 记忆与检索.md', 1, 10, '第八章 记忆与检索', 'post-dd715022', NULL, '2026-06-11 10:56:46');
INSERT INTO `blog_post_import_item` VALUES (8, 1, '第九章 上下文工程.md', 1, 11, '第九章 上下文工程', 'post-2f380844', NULL, '2026-06-11 10:56:53');
INSERT INTO `blog_post_import_item` VALUES (9, 1, '第十章 智能体通信协议.md', 1, 12, '第十章 智能体通信协议', 'post-1cdb0829', NULL, '2026-06-11 10:57:32');

-- ----------------------------
-- Table structure for blog_post_import_task
-- ----------------------------
DROP TABLE IF EXISTS `blog_post_import_task`;
CREATE TABLE `blog_post_import_task`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '导入任务ID',
  `user_id` bigint(20) NOT NULL COMMENT '发起导入的用户ID',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'pending' COMMENT 'pending/running/success/failed',
  `total_count` int(11) NOT NULL DEFAULT 0 COMMENT '文件总数',
  `processed_count` int(11) NOT NULL DEFAULT 0 COMMENT '已处理数（进度）',
  `success_count` int(11) NOT NULL DEFAULT 0 COMMENT '成功数',
  `fail_count` int(11) NOT NULL DEFAULT 0 COMMENT '失败数',
  `post_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '统一文章状态',
  `visibility` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `post_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `rehost_images` tinyint(1) NOT NULL DEFAULT 1,
  `error_message` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '任务级错误（整体失败时）',
  `started_at` datetime NULL DEFAULT NULL,
  `finished_at` datetime NULL DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_blog_post_import_task_user`(`user_id` ASC, `status` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客文章导入任务表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_post_import_task
-- ----------------------------
INSERT INTO `blog_post_import_task` VALUES (1, 2052290101098295297, 'success', 9, 9, 9, 0, 'draft', 'public', 'article', 1, NULL, '2026-06-11 10:53:08', '2026-06-11 10:57:32', '2026-06-11 10:53:08', '2026-06-11 10:57:32');

-- ----------------------------
-- Table structure for blog_post_tag
-- ----------------------------
DROP TABLE IF EXISTS `blog_post_tag`;
CREATE TABLE `blog_post_tag`  (
  `post_id` bigint(20) NOT NULL COMMENT '文章ID',
  `tag_id` bigint(20) NOT NULL COMMENT '标签ID',
  PRIMARY KEY (`post_id`, `tag_id`) USING BTREE,
  INDEX `idx_blog_post_tag_tag_id`(`tag_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客文章标签关系表（多对多）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_post_tag
-- ----------------------------
INSERT INTO `blog_post_tag` VALUES (12, 23);
INSERT INTO `blog_post_tag` VALUES (12, 24);

-- ----------------------------
-- Table structure for blog_search_index_task
-- ----------------------------
DROP TABLE IF EXISTS `blog_search_index_task`;
CREATE TABLE `blog_search_index_task`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `post_id` bigint(20) NOT NULL COMMENT '关联的文章ID',
  `index_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'blog_posts' COMMENT '索引名称',
  `action` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '操作类型：upsert(插入/更新)/delete(删除)',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'pending' COMMENT '状态：pending(等待)/running(执行中)/success(成功)/failed(失败)',
  `retry_count` int(11) NOT NULL DEFAULT 0 COMMENT '重试次数',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '错误信息（失败时记录）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `finished_at` datetime NULL DEFAULT NULL COMMENT '完成时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_blog_search_task_status`(`status` ASC) USING BTREE,
  INDEX `idx_blog_search_task_post_id`(`post_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 10 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客搜索索引同步任务表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_search_index_task
-- ----------------------------

-- ----------------------------
-- Table structure for blog_series
-- ----------------------------
DROP TABLE IF EXISTS `blog_series`;
CREATE TABLE `blog_series`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '系列ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '系列名称',
  `slug` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '系列URL标识（唯一）',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '系列简介',
  `cover_file_id` bigint(20) NULL DEFAULT NULL COMMENT '封面文件ID（关联blog_file_asset）',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'draft' COMMENT 'draft/published/archived',
  `visibility` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'public' COMMENT 'public/private',
  `is_finished` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否完结',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '系列列表排序',
  `create_by` bigint(20) NOT NULL COMMENT '创建者ID（关联sys_user）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `chain_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '产出本系列的迭代链 chainId（webhook 落库用；手工建的系列为空）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_slug`(`slug` ASC) USING BTREE,
  UNIQUE INDEX `uk_chain_id`(`chain_id` ASC) USING BTREE,
  INDEX `idx_status_visibility`(`status` ASC, `visibility` ASC) USING BTREE,
  INDEX `idx_cover_file_id`(`cover_file_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 9 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客系列表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_series
-- ----------------------------
INSERT INTO `blog_series` VALUES (2, 'AIAgent', 'aiagent', 'AI Agent（人工智能智能体）是一种能够自主感知环境、进行规划决策、调用工具并执行行动来完成特定目标的智能系统。\n核心特征：\n- 自主性：无需人类逐步指令，可独立完成任务\n- 感知能力：能接收环境信息（用户输入、API返回、数据库查询等）\n- 规划能力：可将复杂任务拆解为多个子步骤\n- 工具使用：能调用外部工具（搜索、代码执行、API、数据库等）\n- 记忆系统：具备短期记忆（上下文）和长期记忆（向量数据库）\n\n经典工作范式：ReAct\nReAct = Reasoning（推理）+ Acting（行动）\n运行逻辑：思考 → 行动 → 观察 → 再思考 → 再行动，直至任务完成。\n与传统程序的区别：\n- 传统程序：执行固定指令、流程硬编码、无工具调用、一次输入一次输出\n- AI Agent：理解意图自主规划、动态调整路径、可调用任意外部工具、多轮思考与行动循环\n简单理解：\nAgent = LLM的“大脑” + 工具的“手脚” + 记忆系统 + 规划能力\n如果把大语言模型比作一个知识丰富但没有行动能力的“大脑”，那么AI Agent就是给这个大脑装上了“眼睛”“耳朵”和“手脚”。', 54, 'published', 'public', 1, 0, 2052290101098295297, '2026-06-11 11:05:37', '2026-06-11 11:05:37', NULL);
INSERT INTO `blog_series` VALUES (8, '30天Java进阶', 'series-blogserieswriterchaindemo02', NULL, NULL, 'draft', 'public', 0, 0, 1, '2026-07-09 12:02:21', '2026-07-09 12:02:21', 'blog_series_writer-chain-demo02');

-- ----------------------------
-- Table structure for blog_series_catalog
-- ----------------------------
DROP TABLE IF EXISTS `blog_series_catalog`;
CREATE TABLE `blog_series_catalog`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `series_id` bigint(20) NOT NULL,
  `parent_id` bigint(20) NULL DEFAULT NULL,
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `node_type` tinyint(4) NOT NULL DEFAULT 0 COMMENT '0目录 1文章集合 2链接',
  `link_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'node_type=2时有效',
  `link_target` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '_blank' COMMENT '_blank/_self',
  `path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '树路径，如 /1/5/12/',
  `level` int(11) NOT NULL DEFAULT 0,
  `sort_order` int(11) NOT NULL DEFAULT 0,
  `children_count` int(11) NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_series_id`(`series_id` ASC) USING BTREE,
  INDEX `idx_parent_id`(`parent_id` ASC) USING BTREE,
  INDEX `idx_series_parent_sort`(`series_id` ASC, `parent_id` ASC, `sort_order` ASC) USING BTREE,
  INDEX `idx_path`(`path`(191) ASC) USING BTREE,
  CONSTRAINT `fk_catalog_parent` FOREIGN KEY (`parent_id`) REFERENCES `blog_series_catalog` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_catalog_series` FOREIGN KEY (`series_id`) REFERENCES `blog_series` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 23 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系列目录节点表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_series_catalog
-- ----------------------------
INSERT INTO `blog_series_catalog` VALUES (6, 2, NULL, '第一章 初识智能体', 1, NULL, '_blank', '/6/', 0, 1, 0, '2026-06-11 11:06:19', '2026-06-11 11:06:19');
INSERT INTO `blog_series_catalog` VALUES (8, 2, NULL, '第二章 智能体发展史', 1, NULL, '_blank', '/8/', 0, 2, 0, '2026-06-11 11:14:00', '2026-06-11 11:14:00');
INSERT INTO `blog_series_catalog` VALUES (9, 2, NULL, '第三章 大语言模型基础', 1, NULL, '_blank', '/9/', 0, 3, 0, '2026-06-11 11:15:00', '2026-06-11 11:15:00');
INSERT INTO `blog_series_catalog` VALUES (10, 2, NULL, '第四章 智能体经典范式构建', 1, NULL, '_blank', '/10/', 0, 4, 0, '2026-06-11 11:15:19', '2026-06-11 11:15:19');
INSERT INTO `blog_series_catalog` VALUES (11, 2, NULL, '第五章 基于低代码平台的智能体搭建', 1, NULL, '_blank', '/11/', 0, 5, 0, '2026-06-11 11:23:53', '2026-06-11 11:23:53');
INSERT INTO `blog_series_catalog` VALUES (12, 2, NULL, '第六章 框架开发实践', 1, NULL, '_blank', '/12/', 0, 5, 0, '2026-06-11 11:24:01', '2026-06-11 11:24:01');
INSERT INTO `blog_series_catalog` VALUES (13, 2, NULL, '第七章 构建你的智能体框架', 1, NULL, '_blank', '/13/', 0, 7, 0, '2026-06-11 11:24:11', '2026-06-11 11:24:11');
INSERT INTO `blog_series_catalog` VALUES (14, 2, NULL, '第八章 记忆与检索', 1, NULL, '_blank', '/14/', 0, 8, 0, '2026-06-11 11:24:19', '2026-06-11 11:24:19');
INSERT INTO `blog_series_catalog` VALUES (15, 2, NULL, '第九章 上下文工程', 1, NULL, '_blank', '/15/', 0, 9, 0, '2026-06-11 11:24:27', '2026-06-11 11:24:27');
INSERT INTO `blog_series_catalog` VALUES (16, 2, NULL, '第十章 智能体通信协议', 1, NULL, '_blank', '/16/', 0, 10, 0, '2026-06-11 11:24:36', '2026-06-11 11:24:36');
INSERT INTO `blog_series_catalog` VALUES (22, 8, NULL, '正文', 0, NULL, '_blank', '/22/', 0, 0, 0, '2026-07-09 12:02:21', '2026-07-09 12:02:21');

-- ----------------------------
-- Table structure for blog_series_catalog_post
-- ----------------------------
DROP TABLE IF EXISTS `blog_series_catalog_post`;
CREATE TABLE `blog_series_catalog_post`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `catalog_id` bigint(20) NOT NULL,
  `post_id` bigint(20) NOT NULL,
  `sort_order` int(11) NOT NULL DEFAULT 0,
  `is_primary` tinyint(1) NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_catalog_post`(`catalog_id` ASC, `post_id` ASC) USING BTREE,
  INDEX `idx_post_id`(`post_id` ASC) USING BTREE,
  INDEX `idx_catalog_sort`(`catalog_id` ASC, `sort_order` ASC) USING BTREE,
  CONSTRAINT `fk_cp_catalog` FOREIGN KEY (`catalog_id`) REFERENCES `blog_series_catalog` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_cp_post` FOREIGN KEY (`post_id`) REFERENCES `blog_post` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 74 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '目录文章关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_series_catalog_post
-- ----------------------------
INSERT INTO `blog_series_catalog_post` VALUES (6, 8, 4, 0, 0, '2026-06-11 11:24:53');
INSERT INTO `blog_series_catalog_post` VALUES (7, 9, 5, 0, 0, '2026-06-11 11:25:14');
INSERT INTO `blog_series_catalog_post` VALUES (8, 10, 6, 0, 0, '2026-06-11 11:25:26');
INSERT INTO `blog_series_catalog_post` VALUES (9, 11, 7, 0, 0, '2026-06-11 11:25:45');
INSERT INTO `blog_series_catalog_post` VALUES (10, 12, 8, 0, 0, '2026-06-11 11:25:56');
INSERT INTO `blog_series_catalog_post` VALUES (11, 13, 9, 0, 0, '2026-06-11 11:26:05');
INSERT INTO `blog_series_catalog_post` VALUES (12, 14, 10, 0, 0, '2026-06-11 11:26:23');
INSERT INTO `blog_series_catalog_post` VALUES (13, 15, 11, 0, 0, '2026-06-11 11:26:36');
INSERT INTO `blog_series_catalog_post` VALUES (14, 16, 12, 0, 0, '2026-06-11 11:26:52');
INSERT INTO `blog_series_catalog_post` VALUES (72, 6, 3, 0, 1, '2026-06-11 14:27:28');

-- ----------------------------
-- Table structure for blog_tag
-- ----------------------------
DROP TABLE IF EXISTS `blog_tag`;
CREATE TABLE `blog_tag`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '标签名称',
  `slug` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '标签URL标识',
  `use_count` int(10) UNSIGNED NOT NULL DEFAULT 0 COMMENT '使用次数（文章数）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_blog_tag_slug`(`slug` ASC) USING BTREE,
  UNIQUE INDEX `uk_blog_tag_name`(`name` ASC) USING BTREE,
  INDEX `idx_blog_tag_use_count`(`use_count` DESC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 31 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '博客标签表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of blog_tag
-- ----------------------------
INSERT INTO `blog_tag` VALUES (23, 'AI ', 'ai', 1, '2026-06-11 11:08:56', '2026-06-11 11:23:10');
INSERT INTO `blog_tag` VALUES (24, 'AIAgent', 'aiagent', 1, '2026-06-11 11:08:57', '2026-06-11 11:23:11');
INSERT INTO `blog_tag` VALUES (29, '24', '24', 0, '2026-06-11 11:19:10', '2026-06-30 10:21:20');
INSERT INTO `blog_tag` VALUES (30, '23', '23', 0, '2026-06-11 11:19:11', '2026-06-30 10:21:20');

-- ----------------------------
-- Table structure for forge_plugin
-- ----------------------------
DROP TABLE IF EXISTS `forge_plugin`;
CREATE TABLE `forge_plugin`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '插件ID',
  `plugin_key` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '插件唯一标识，对应 plugin.json 的 id',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '插件名称',
  `type` varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'inline' COMMENT '插件类型：inline/view',
  `summary` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '一句话简介',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '插件详情，Markdown 或 HTML',
  `keywords` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '搜索关键词，多个用逗号分隔',
  `icon_file_id` bigint(20) NULL DEFAULT NULL COMMENT '图标文件ID，关联 sys_file',
  `cover_file_id` bigint(20) NULL DEFAULT NULL COMMENT '封面文件ID，关联 sys_file',
  `author_user_id` bigint(20) NULL DEFAULT NULL COMMENT '作者用户ID',
  `author_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '作者展示名',
  `homepage_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '主页地址',
  `repo_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '源码仓库地址',
  `license` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '许可证',
  `pricing_type` tinyint(4) NOT NULL DEFAULT 1 COMMENT '定价类型：1免费 2付费 3订阅 4外部购买',
  `price` decimal(10, 2) NULL DEFAULT NULL COMMENT '展示价格，免费可为空',
  `original_price` decimal(10, 2) NULL DEFAULT NULL COMMENT '原价/划线价',
  `currency` varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'CNY' COMMENT '币种',
  `price_text` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '价格展示文案，如 ¥9/月、联系作者、Pro 版可用',
  `purchase_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '外部购买地址',
  `latest_version_id` bigint(20) NULL DEFAULT NULL COMMENT '最新版本ID',
  `latest_version` varchar(50) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '最新版本号',
  `download_count` bigint(20) NOT NULL DEFAULT 0 COMMENT '下载次数',
  `install_count` bigint(20) NOT NULL DEFAULT 0 COMMENT '安装次数',
  `favorite_count` bigint(20) NOT NULL DEFAULT 0 COMMENT '收藏次数',
  `rating_score` decimal(3, 2) NOT NULL DEFAULT 0.00 COMMENT '评分',
  `rating_count` int(11) NOT NULL DEFAULT 0 COMMENT '评分人数',
  `is_featured` tinyint(4) NOT NULL DEFAULT 0 COMMENT '是否推荐：1是 0否',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序',
  `status` tinyint(4) NOT NULL DEFAULT 0 COMMENT '状态：0草稿 1上架 2下架 3封禁',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT 0,
  `delete_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_forge_plugin_key`(`plugin_key` ASC) USING BTREE,
  INDEX `idx_forge_plugin_list`(`deleted` ASC, `status` ASC, `is_featured` ASC, `sort_order` ASC) USING BTREE,
  INDEX `idx_forge_plugin_author`(`deleted` ASC, `author_user_id` ASC, `status` ASC) USING BTREE,
  INDEX `idx_forge_plugin_pricing`(`deleted` ASC, `pricing_type` ASC, `status` ASC) USING BTREE,
  FULLTEXT INDEX `ft_forge_plugin_search`(`name`, `summary`, `keywords`) WITH PARSER `ngram`
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '插件主表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of forge_plugin
-- ----------------------------
INSERT INTO `forge_plugin` VALUES (1, 'ai-code-assistant', 'AI代码助手', 'inline', '智能代码补全与重构，支持多语言', '## 功能介绍\n\nAI代码助手是一款基于大语言模型的智能编程工具，提供以下核心能力：\n\n- **智能代码补全**：根据上下文自动补全代码\n- **代码重构建议**：识别代码坏味道并给出优化方案\n- **Bug检测**：实时扫描潜在问题\n- **文档生成**：一键生成函数注释和API文档', 'AI,代码助手,智能编程,代码补全,重构', 1001, 1002, 100001, '张三', 'https://example.com/homepage', 'https://github.com/example/ai-code-assistant', 'MIT', 1, NULL, NULL, 'CNY', '免费', NULL, 1, '2.1.0', 256, 189, 68, 4.80, 123, 1, 1, 1, '首批推荐插件，已通过安全审核', 1, '2026-06-01 10:00:00', 1, '2026-07-01 14:30:00', 0, NULL);

-- ----------------------------
-- Table structure for forge_plugin_category
-- ----------------------------
DROP TABLE IF EXISTS `forge_plugin_category`;
CREATE TABLE `forge_plugin_category`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '插件分类ID',
  `code` varchar(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '分类编码',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '分类名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '分类说明',
  `icon_file_id` bigint(20) NULL DEFAULT NULL COMMENT '分类图标文件ID，关联 sys_file',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT 0,
  `delete_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_forge_plugin_category_code`(`code` ASC) USING BTREE,
  INDEX `idx_forge_plugin_category_sort`(`deleted` ASC, `status` ASC, `sort_order` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '插件分类表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of forge_plugin_category
-- ----------------------------

-- ----------------------------
-- Table structure for forge_plugin_category_rel
-- ----------------------------
DROP TABLE IF EXISTS `forge_plugin_category_rel`;
CREATE TABLE `forge_plugin_category_rel`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '插件分类关联ID',
  `plugin_id` bigint(20) NOT NULL COMMENT '插件ID',
  `category_id` bigint(20) NOT NULL COMMENT '分类ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_forge_plugin_category_rel`(`plugin_id` ASC, `category_id` ASC) USING BTREE,
  INDEX `idx_forge_plugin_category_rel_category`(`category_id` ASC, `plugin_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '插件分类关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of forge_plugin_category_rel
-- ----------------------------

-- ----------------------------
-- Table structure for forge_plugin_download_log
-- ----------------------------
DROP TABLE IF EXISTS `forge_plugin_download_log`;
CREATE TABLE `forge_plugin_download_log`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '插件下载日志ID',
  `user_id` bigint(20) NULL DEFAULT NULL COMMENT '用户ID，匿名下载可为空',
  `plugin_id` bigint(20) NOT NULL COMMENT '插件ID',
  `version_id` bigint(20) NULL DEFAULT NULL COMMENT '插件版本ID',
  `client_version` varchar(50) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '客户端版本',
  `client_os` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户端系统',
  `ip` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '客户端IP',
  `user_agent` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'User-Agent',
  `result` tinyint(4) NOT NULL DEFAULT 1 COMMENT '结果：1成功 0失败',
  `error_msg` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '失败原因',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`, `create_time`) USING BTREE,
  INDEX `idx_forge_plugin_download_plugin`(`plugin_id` ASC, `version_id` ASC, `create_time` ASC) USING BTREE,
  INDEX `idx_forge_plugin_download_user`(`user_id` ASC, `create_time` ASC) USING BTREE,
  INDEX `idx_forge_plugin_download_result`(`result` ASC, `create_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '插件下载日志表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of forge_plugin_download_log
-- ----------------------------

-- ----------------------------
-- Table structure for forge_plugin_favorite
-- ----------------------------
DROP TABLE IF EXISTS `forge_plugin_favorite`;
CREATE TABLE `forge_plugin_favorite`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
  `plugin_id` bigint(20) NOT NULL COMMENT '插件ID',
  `user_id` bigint(20) NOT NULL COMMENT '收藏用户ID',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_forge_plugin_favorite`(`plugin_id` ASC, `user_id` ASC) USING BTREE,
  INDEX `idx_forge_plugin_favorite_user`(`user_id` ASC, `create_time` ASC) USING BTREE,
  INDEX `idx_forge_plugin_favorite_plugin`(`plugin_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '插件收藏表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of forge_plugin_favorite
-- ----------------------------

-- ----------------------------
-- Table structure for forge_plugin_permission
-- ----------------------------
DROP TABLE IF EXISTS `forge_plugin_permission`;
CREATE TABLE `forge_plugin_permission`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '插件权限ID',
  `plugin_id` bigint(20) NOT NULL COMMENT '插件ID',
  `version_id` bigint(20) NOT NULL COMMENT '插件版本ID',
  `permission_code` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '权限编码，如 clipboard.read/network.request',
  `permission_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '权限名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '权限用途说明',
  `risk_level` tinyint(4) NOT NULL DEFAULT 1 COMMENT '风险等级：1低 2中 3高',
  `required` tinyint(4) NOT NULL DEFAULT 1 COMMENT '是否必需：1必需 0可选',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_forge_plugin_permission`(`version_id` ASC, `permission_code` ASC) USING BTREE,
  INDEX `idx_forge_plugin_permission_plugin`(`plugin_id` ASC, `version_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '插件版本权限声明表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of forge_plugin_permission
-- ----------------------------

-- ----------------------------
-- Table structure for forge_plugin_review
-- ----------------------------
DROP TABLE IF EXISTS `forge_plugin_review`;
CREATE TABLE `forge_plugin_review`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '插件评价ID',
  `plugin_id` bigint(20) NOT NULL COMMENT '插件ID',
  `version_id` bigint(20) NULL DEFAULT NULL COMMENT '评价时安装的版本ID',
  `user_id` bigint(20) NOT NULL COMMENT '评价用户ID',
  `rating` tinyint(4) NOT NULL COMMENT '评分：1-5',
  `content` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '评价内容',
  `reply_content` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '作者/管理员回复',
  `reply_by` bigint(20) NULL DEFAULT NULL COMMENT '回复人ID',
  `reply_time` datetime NULL DEFAULT NULL COMMENT '回复时间',
  `like_count` int(11) NOT NULL DEFAULT 0 COMMENT '点赞数',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：0待审 1展示 2隐藏 3拒绝',
  `audit_remark` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '审核备注',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_forge_plugin_review_user`(`plugin_id` ASC, `user_id` ASC) USING BTREE,
  INDEX `idx_forge_plugin_review_plugin`(`plugin_id` ASC, `status` ASC, `create_time` ASC) USING BTREE,
  INDEX `idx_forge_plugin_review_rating`(`plugin_id` ASC, `status` ASC, `rating` ASC) USING BTREE,
  INDEX `idx_forge_plugin_review_user`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '插件评价表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of forge_plugin_review
-- ----------------------------

-- ----------------------------
-- Table structure for forge_plugin_version
-- ----------------------------
DROP TABLE IF EXISTS `forge_plugin_version`;
CREATE TABLE `forge_plugin_version`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '插件版本ID',
  `plugin_id` bigint(20) NOT NULL COMMENT '插件ID',
  `version` varchar(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '版本号，semver',
  `channel` varchar(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'stable' COMMENT '发布通道：stable/beta/dev',
  `manifest_json` json NOT NULL COMMENT 'plugin.json 快照',
  `package_file_id` bigint(20) NULL DEFAULT NULL COMMENT '插件包文件ID，关联 sys_file',
  `package_url` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '插件包外链，可选',
  `package_sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '插件包 SHA256',
  `package_size` bigint(20) NOT NULL DEFAULT 0 COMMENT '包大小，字节',
  `signature` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '包签名，可选',
  `min_app_version` varchar(50) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '最低宿主版本',
  `max_app_version` varchar(50) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '最高宿主版本',
  `changelog` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '更新日志',
  `download_count` bigint(20) NOT NULL DEFAULT 0 COMMENT '当前版本下载次数',
  `review_status` tinyint(4) NOT NULL DEFAULT 0 COMMENT '审核状态：0待审 1通过 2拒绝',
  `review_remark` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '审核备注',
  `published_time` datetime NULL DEFAULT NULL COMMENT '发布时间',
  `status` tinyint(4) NOT NULL DEFAULT 0 COMMENT '状态：0草稿 1已发布 2已下架 3废弃',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT 0,
  `delete_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_forge_plugin_version`(`plugin_id` ASC, `version` ASC) USING BTREE,
  INDEX `idx_forge_plugin_version_status`(`plugin_id` ASC, `deleted` ASC, `status` ASC, `review_status` ASC) USING BTREE,
  INDEX `idx_forge_plugin_version_publish`(`deleted` ASC, `status` ASC, `published_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '插件版本表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of forge_plugin_version
-- ----------------------------

-- ----------------------------
-- Table structure for forge_user_plugin
-- ----------------------------
DROP TABLE IF EXISTS `forge_user_plugin`;
CREATE TABLE `forge_user_plugin`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '用户插件ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `plugin_id` bigint(20) NOT NULL COMMENT '插件ID',
  `version_id` bigint(20) NULL DEFAULT NULL COMMENT '当前安装版本ID',
  `installed_version` varchar(50) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '当前安装版本号',
  `enabled` tinyint(4) NOT NULL DEFAULT 1 COMMENT '是否启用：1启用 0禁用',
  `auto_update` tinyint(4) NOT NULL DEFAULT 1 COMMENT '是否自动更新：1是 0否',
  `config_json` json NULL COMMENT '用户配置，可选同步',
  `window_state_json` json NULL COMMENT '窗口状态，可选同步',
  `install_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '安装时间',
  `last_used_time` datetime NULL DEFAULT NULL COMMENT '最近使用时间',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL COMMENT '更新人ID',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_forge_user_plugin`(`user_id` ASC, `plugin_id` ASC) USING BTREE,
  INDEX `idx_forge_user_plugin_user`(`user_id` ASC, `enabled` ASC) USING BTREE,
  INDEX `idx_forge_user_plugin_plugin`(`plugin_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户插件安装表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of forge_user_plugin
-- ----------------------------

-- ----------------------------
-- Table structure for space_bookmark
-- ----------------------------
DROP TABLE IF EXISTS `space_bookmark`;
CREATE TABLE `space_bookmark`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '书签ID',
  `user_id` bigint(20) NOT NULL COMMENT '所属用户ID',
  `folder_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '所属目录ID，0表示未分类',
  `title` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '书签标题',
  `url` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '原始URL',
  `normalized_url` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '规范化URL，用于去重',
  `url_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'URL哈希，用于索引和去重',
  `domain` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '域名',
  `description` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '描述',
  `favicon_url` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '站点图标URL',
  `favicon_file_id` bigint(20) NULL DEFAULT NULL COMMENT '站点图标文件ID，关联sys_file',
  `source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'manual' COMMENT '来源：manual/chrome/import',
  `source_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '外部来源标识',
  `status` tinyint(4) NOT NULL DEFAULT 0 COMMENT '状态：0正常 1归档 2失效',
  `visit_count` int(11) NOT NULL DEFAULT 0 COMMENT '访问次数',
  `last_visit_time` datetime NULL DEFAULT NULL COMMENT '最后访问时间',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '目录内排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT 0,
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
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '导出任务ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `file_id` bigint(20) NULL DEFAULT NULL COMMENT '导出文件ID，关联sys_file',
  `export_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'chrome_html' COMMENT '导出类型：chrome_html/json',
  `scope_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'all' COMMENT '范围：all/folder/tag',
  `scope_id` bigint(20) NULL DEFAULT NULL COMMENT '范围ID',
  `status` tinyint(4) NOT NULL DEFAULT 0 COMMENT '状态：0待处理 1处理中 2成功 3失败',
  `total_count` int(11) NOT NULL DEFAULT 0 COMMENT '导出数量',
  `error_msg` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '错误信息',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT 0,
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
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '目录ID',
  `user_id` bigint(20) NOT NULL COMMENT '所属用户ID',
  `parent_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '父目录ID，0表示根目录',
  `ancestors` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '祖级列表，如0,1,2',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '目录名称',
  `level` int(11) NOT NULL DEFAULT 1 COMMENT '目录层级',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序',
  `source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'manual' COMMENT '来源：manual/chrome/import',
  `source_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '外部来源标识',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT 0,
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
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '导入任务ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `file_id` bigint(20) NULL DEFAULT NULL COMMENT '导入文件ID，关联sys_file',
  `source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'chrome' COMMENT '来源：chrome/html/json',
  `status` tinyint(4) NOT NULL DEFAULT 0 COMMENT '状态：0待处理 1处理中 2成功 3失败',
  `total_count` int(11) NOT NULL DEFAULT 0 COMMENT '总数量',
  `success_count` int(11) NOT NULL DEFAULT 0 COMMENT '成功数量',
  `duplicate_count` int(11) NOT NULL DEFAULT 0 COMMENT '重复数量',
  `fail_count` int(11) NOT NULL DEFAULT 0 COMMENT '失败数量',
  `error_msg` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '错误信息',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT 0,
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
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `bookmark_id` bigint(20) NOT NULL COMMENT '书签ID',
  `tag_id` bigint(20) NOT NULL COMMENT '标签ID',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT 0,
  `delete_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_space_bookmark_tag_bookmark`(`bookmark_id` ASC, `deleted` ASC) USING BTREE,
  INDEX `idx_space_bookmark_tag_tag`(`tag_id` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '书签标签关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of space_bookmark_tag
-- ----------------------------

-- ----------------------------
-- Table structure for space_tag
-- ----------------------------
DROP TABLE IF EXISTS `space_tag`;
CREATE TABLE `space_tag`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  `user_id` bigint(20) NOT NULL COMMENT '所属用户ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '标签名称',
  `color` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '标签颜色',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT 0,
  `delete_time` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_space_tag_user_name`(`user_id` ASC, `name` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '空间标签表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of space_tag
-- ----------------------------

-- ----------------------------
-- Table structure for sys_config
-- ----------------------------
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config`  (
  `id` bigint(20) NOT NULL COMMENT '配置ID',
  `config_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '配置键（唯一，如 site.title）',
  `config_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '配置值（支持长文本、JSON等）',
  `config_type` tinyint(4) NOT NULL DEFAULT 1 COMMENT '类型：1=文本 2=数字 3=布尔 4=JSON',
  `config_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '配置名称（如 网站标题）',
  `group_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'default' COMMENT '配置分组（如 system, email, security）',
  `is_frontend` tinyint(4) NOT NULL DEFAULT 0 COMMENT '是否前端可见：1=是（如主题色） 0=否（如数据库密码）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
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
  `id` bigint(20) NOT NULL COMMENT '字典数据ID',
  `dict_type_id` bigint(20) NOT NULL COMMENT '关联的字典类型ID',
  `dict_label` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '字典标签（展示值，如 \"启用\"）',
  `dict_value` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '字典值（实际存储值，如 \"1\"）',
  `css_class` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'CSS类名（前端样式，如 text-success）',
  `list_class` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '列表样式（如 primary, danger）',
  `is_default` tinyint(4) NOT NULL DEFAULT 0 COMMENT '是否默认：1=是',
  `sort` int(11) NOT NULL DEFAULT 0 COMMENT '排序（越小越靠前）',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：1=正常 0=停用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT 0,
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
  `id` bigint(20) NOT NULL COMMENT '字典类型ID',
  `type_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '字典类型编码（唯一，如 user_status）',
  `type_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '字典类型名称（如 用户状态）',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：1=正常 0=停用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `create_by` bigint(20) NULL DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT 0,
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
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '文件ID',
  `target_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '关联业务类型（如：blog_post、user_avatar、travel_note）',
  `target_id` bigint(20) NOT NULL COMMENT '关联业务实体ID',
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
  `size_bytes` bigint(20) NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
  `width` int(11) NULL DEFAULT NULL COMMENT '图片宽度（像素）',
  `height` int(11) NULL DEFAULT NULL COMMENT '图片高度（像素）',
  `duration` int(11) NULL DEFAULT NULL COMMENT '音视频时长（秒）',
  `hash_sha256` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件SHA256哈希值，用于去重和秒传',
  `is_public` tinyint(4) NOT NULL DEFAULT 1 COMMENT '是否公开（1公开 0私有）',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '文件状态（1正常 0删除 2上传中 3上传失败 4禁用）',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序值',
  `metadata` json NULL COMMENT '扩展元数据（JSON格式）',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '上传人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_target`(`target_type` ASC, `target_id` ASC, `sort_order` ASC) USING BTREE COMMENT '业务关联查询索引',
  INDEX `idx_target_status`(`target_type` ASC, `target_id` ASC, `status` ASC) USING BTREE COMMENT '业务状态查询索引',
  INDEX `idx_hash`(`hash_sha256` ASC) USING BTREE COMMENT '文件哈希索引',
  INDEX `idx_create_by`(`create_by` ASC) USING BTREE COMMENT '上传人索引'
) ENGINE = InnoDB AUTO_INCREMENT = 2064904828278157314 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '统一文件资源表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_file
-- ----------------------------
INSERT INTO `sys_file` VALUES (2064888359892967426, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/0c0cd5f48d52451fa2e0c4901ee22bc5.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/0c0cd5f48d52451fa2e0c4901ee22bc5.png', '1757242319667-0.png', '0c0cd5f48d52451fa2e0c4901ee22bc5.png', 'png', 'image/png', 1154233, NULL, NULL, NULL, 'c8b60b0981113e35555c425f37e0a1d7cf7f99069c30e5c0a0fd2d85d9ddaad5', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 09:52:03', '2026-06-11 09:52:03');
INSERT INTO `sys_file` VALUES (2064888371049816065, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/7738068c8203450a95caf52cf97c62cd.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/7738068c8203450a95caf52cf97c62cd.png', '1757242319667-1.png', '7738068c8203450a95caf52cf97c62cd.png', 'png', 'image/png', 72584, NULL, NULL, NULL, '950801be772f7999ef983be483e075e15825dd5bc743dc092bd7c7e31285f8cd', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 09:52:06', '2026-06-11 09:52:06');
INSERT INTO `sys_file` VALUES (2064888386585518081, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/71b7cbe205d648e8affc5caf372c1ade.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/71b7cbe205d648e8affc5caf372c1ade.png', '1757242319667-2.png', '71b7cbe205d648e8affc5caf372c1ade.png', 'png', 'image/png', 132922, NULL, NULL, NULL, '3b44f2ad25bb2e3f903766e45461e05de75f6bf1a5e2c80f8d80cb9036b546c0', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 09:52:10', '2026-06-11 09:52:10');
INSERT INTO `sys_file` VALUES (2064888403249487874, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/a483385b0c9549c08522daa15ce96900.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/a483385b0c9549c08522daa15ce96900.png', '1757242319667-3.png', 'a483385b0c9549c08522daa15ce96900.png', 'png', 'image/png', 315564, NULL, NULL, NULL, 'acf74fc6bf1ef3fb51cc7b32a75a5548626d587b9c6bb9251613a789dd25a568', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 09:52:14', '2026-06-11 09:52:14');
INSERT INTO `sys_file` VALUES (2064888461420290049, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/6d0e90bb011941019d724a91322e541b.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/6d0e90bb011941019d724a91322e541b.png', '1757242319667-4.png', '6d0e90bb011941019d724a91322e541b.png', 'png', 'image/png', 906970, NULL, NULL, NULL, 'e8009637318ab4de263f9adaaa0becb1b0ebdc033b2b124d53d83620e5c78170', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 09:52:28', '2026-06-11 09:52:28');
INSERT INTO `sys_file` VALUES (2064888481544560642, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/8324a174c82c44b783a9bbd5aa650abc.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/8324a174c82c44b783a9bbd5aa650abc.png', '1757242319667-6.png', '8324a174c82c44b783a9bbd5aa650abc.png', 'png', 'image/png', 164039, NULL, NULL, NULL, '6c1f7d84672f594f4f12f1339e9f7bd5cd9e0444d602f2456a353f827152888c', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 09:52:32', '2026-06-11 09:52:32');
INSERT INTO `sys_file` VALUES (2064888497369669634, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/cacd9a256bfc42bdb3f5344613c6fad0.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/cacd9a256bfc42bdb3f5344613c6fad0.png', '1757242319667-5.png', 'cacd9a256bfc42bdb3f5344613c6fad0.png', 'png', 'image/png', 74079, NULL, NULL, NULL, 'af01a3725baa6da04713c4c31b3aa21e24dd304c365304a3f9d3bb94624a8597', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 09:52:36', '2026-06-11 09:52:36');
INSERT INTO `sys_file` VALUES (2064888508815925250, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/b7cedf103d6841578ba37e74172ae38c.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/b7cedf103d6841578ba37e74172ae38c.png', '1757242319667-18.png', 'b7cedf103d6841578ba37e74172ae38c.png', 'png', 'image/png', 89771, NULL, NULL, NULL, '4ad9a8bd60b23be30a84df4f1d1a688571c93d6a0a0e7b7c1b11b6cc51803da4', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 09:52:39', '2026-06-11 09:52:39');
INSERT INTO `sys_file` VALUES (2064903745719906305, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/4a747d89cb6f4154a0f1327efff009a5.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/4a747d89cb6f4154a0f1327efff009a5.png', '1757246501849-00.png', '4a747d89cb6f4154a0f1327efff009a5.png', 'png', 'image/png', 108477, NULL, NULL, NULL, 'b1f34ec65e266a8949696fb440c4dda0aaea5650f9125e4e6dcab3365ea594c1', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:12', '2026-06-11 10:53:12');
INSERT INTO `sys_file` VALUES (2064903751910699010, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/ebe2a225a8c843cdb461bb2914c56941.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/ebe2a225a8c843cdb461bb2914c56941.png', '1757246501849-0.png', 'ebe2a225a8c843cdb461bb2914c56941.png', 'png', 'image/png', 63720, NULL, NULL, NULL, 'b48d18732b1780e57fdca8d6c5b0ee60e9797fc47373679641dd59ff2fe4c78a', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:13', '2026-06-11 10:53:13');
INSERT INTO `sys_file` VALUES (2064903759405920258, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/888c8ba5e01c432cb5cdccb974b3b185.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/888c8ba5e01c432cb5cdccb974b3b185.png', '1757246501849-1.png', '888c8ba5e01c432cb5cdccb974b3b185.png', 'png', 'image/png', 128840, NULL, NULL, NULL, 'adfd9609b5df9a60342d14793f6fcc7d6b4c1452b1f7527563818e9a2f7b4179', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:15', '2026-06-11 10:53:15');
INSERT INTO `sys_file` VALUES (2064903768746635265, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/c3dc9a0ee3da4c498f3b0fd751ff2e5d.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/c3dc9a0ee3da4c498f3b0fd751ff2e5d.png', '1757246501849-2.png', 'c3dc9a0ee3da4c498f3b0fd751ff2e5d.png', 'png', 'image/png', 70630, NULL, NULL, NULL, '8fa4f753959e4ac713ec565e8b5370d71a5e8221ecfb0442d8902bb8f77336e4', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:17', '2026-06-11 10:53:17');
INSERT INTO `sys_file` VALUES (2064903783141486593, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/779e4800fb03499e84169985c61c045b.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/779e4800fb03499e84169985c61c045b.png', '1757246501849-3.png', '779e4800fb03499e84169985c61c045b.png', 'png', 'image/png', 1371298, NULL, NULL, NULL, '9aba24d9bef4235899fd684846bb53e24f18a7833b7a44d44f2aefbbae9164aa', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:21', '2026-06-11 10:53:21');
INSERT INTO `sys_file` VALUES (2064903789290336257, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/bff40f0fad8d466fa09d0387cfc1d0db.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/bff40f0fad8d466fa09d0387cfc1d0db.png', '1757246501849-4.png', 'bff40f0fad8d466fa09d0387cfc1d0db.png', 'png', 'image/png', 55091, NULL, NULL, NULL, 'ae083b1d11f5c5cc65116cdd45e3ea2459b5f8dca5a47bf6e4c70fc2639de81b', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:22', '2026-06-11 10:53:22');
INSERT INTO `sys_file` VALUES (2064903800359104513, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/8ab85a9f6f474d1ca192aa4005c5e1c1.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/8ab85a9f6f474d1ca192aa4005c5e1c1.png', '1757246501849-5.png', '8ab85a9f6f474d1ca192aa4005c5e1c1.png', 'png', 'image/png', 1219243, NULL, NULL, NULL, '9fc2115476c09ecc90f563eccdf150b02838ca15f2b9cd741ac36a5d846fbc3d', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:25', '2026-06-11 10:53:25');
INSERT INTO `sys_file` VALUES (2064903809175531522, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/4c6c0e12fa80400694ac08aea9841051.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/4c6c0e12fa80400694ac08aea9841051.png', '1757246501849-6.png', '4c6c0e12fa80400694ac08aea9841051.png', 'png', 'image/png', 104677, NULL, NULL, NULL, '8e7e8879df8bd42b8229669f96f0a1e24b370a0e2f06a9123887a1f05220b3da', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:27', '2026-06-11 10:53:27');
INSERT INTO `sys_file` VALUES (2064903816947576834, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/971b330abf394c3aac1817c38e761014.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/971b330abf394c3aac1817c38e761014.png', '1757246501849-7.png', '971b330abf394c3aac1817c38e761014.png', 'png', 'image/png', 142695, NULL, NULL, NULL, '885b2b02b5728e9837b24e6e0c8a9b55699cdcd21f38eb466bfb049f3d66db0b', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:29', '2026-06-11 10:53:29');
INSERT INTO `sys_file` VALUES (2064903827433336833, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/df97d5cdb2434fb9a9c6c951d04b1604.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/df97d5cdb2434fb9a9c6c951d04b1604.png', '1757246501849-8.png', 'df97d5cdb2434fb9a9c6c951d04b1604.png', 'png', 'image/png', 306253, NULL, NULL, NULL, '79a45b16440afef8cebd98d6ebd53176378acc55c6f2357e71760cde640710e7', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:31', '2026-06-11 10:53:31');
INSERT INTO `sys_file` VALUES (2064903836329455617, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/824d06ddf8094557855ec64120bb724e.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/824d06ddf8094557855ec64120bb724e.png', '1757246501849-9.png', '824d06ddf8094557855ec64120bb724e.png', 'png', 'image/png', 643767, NULL, NULL, NULL, 'ec4f828e5f325e120536623238c4cafb3701accc31ef1ad897213ec8a028477c', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:33', '2026-06-11 10:53:33');
INSERT INTO `sys_file` VALUES (2064903856269176833, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/cd83d80f9a0b4219975ac4c2e4537b9f.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/cd83d80f9a0b4219975ac4c2e4537b9f.png', '1757246501849-10.png', 'cd83d80f9a0b4219975ac4c2e4537b9f.png', 'png', 'image/png', 4275627, NULL, NULL, NULL, '740689b843b434e56cf22f4f4ec604dcbaa52511af65daf47821621fd4a7faf5', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:38', '2026-06-11 10:53:38');
INSERT INTO `sys_file` VALUES (2064903878272495618, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/a97fe5bd07cc40b48db9f60da03b8e82.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/a97fe5bd07cc40b48db9f60da03b8e82.png', '1757249275674-0.png', 'a97fe5bd07cc40b48db9f60da03b8e82.png', 'png', 'image/png', 37817, NULL, NULL, NULL, '9179d764594d8f48bae641cbbacd023c7a15c41cc5e9aa7e7acf433e259f0f1d', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:43', '2026-06-11 10:53:43');
INSERT INTO `sys_file` VALUES (2064903904746942465, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/1803a4d42749442794a3d433963d8a2d.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/1803a4d42749442794a3d433963d8a2d.png', '1757249275674-1.png', '1803a4d42749442794a3d433963d8a2d.png', 'png', 'image/png', 238696, NULL, NULL, NULL, 'd652cb4c22828061e2bcaadd56e168d0815e2efc4cf8585c5fbec4653d2da610', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:50', '2026-06-11 10:53:50');
INSERT INTO `sys_file` VALUES (2064903910899986434, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/a207e4e441554a5aaa2f9ef2b720f4cd.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/a207e4e441554a5aaa2f9ef2b720f4cd.png', '1757249275674-2.png', 'a207e4e441554a5aaa2f9ef2b720f4cd.png', 'png', 'image/png', 52838, NULL, NULL, NULL, '46356991019082bf5a80f7f317f4923bca2e95c0615c3334bd2698f889ed9999', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:51', '2026-06-11 10:53:51');
INSERT INTO `sys_file` VALUES (2064903918336487425, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/78dbbcb0ac3f497aab4d25c14ff8d0f8.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/78dbbcb0ac3f497aab4d25c14ff8d0f8.png', '1757249275674-3.png', '78dbbcb0ac3f497aab4d25c14ff8d0f8.png', 'png', 'image/png', 214390, NULL, NULL, NULL, '93e4d33d0e9fa59a475e405da641d93ca2456c6c82c1df78e76bf4b46d71a2e8', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:53', '2026-06-11 10:53:53');
INSERT INTO `sys_file` VALUES (2064903925382918146, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/1d0acce9d8ba43fd92dcb252a9c7aef4.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/1d0acce9d8ba43fd92dcb252a9c7aef4.png', '1757249275674-4.png', '1d0acce9d8ba43fd92dcb252a9c7aef4.png', 'png', 'image/png', 162804, NULL, NULL, NULL, 'cca7732908d425db8f3f83c40c145c683e32cde4271d5be3d29f3eca7bda051b', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:55', '2026-06-11 10:53:55');
INSERT INTO `sys_file` VALUES (2064903931133308930, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/acad4252e9174fbd9c529c0903273e29.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/acad4252e9174fbd9c529c0903273e29.png', '1757249275674-5.png', 'acad4252e9174fbd9c529c0903273e29.png', 'png', 'image/png', 114681, NULL, NULL, NULL, '34917fb5f0330f703140c6ca0a735728ef6ef76abaf10b01ede3f6f6803ce961', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:56', '2026-06-11 10:53:56');
INSERT INTO `sys_file` VALUES (2064903945951784962, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/2d9d3ef850cd480cbac0033d6b1a5162.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/2d9d3ef850cd480cbac0033d6b1a5162.png', '4-1.png', '2d9d3ef850cd480cbac0033d6b1a5162.png', 'png', 'image/png', 42472, NULL, NULL, NULL, 'c997de462c19c6ba9c5738bedf89a1ab3c67f93fffb75355705814936365601f', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:53:59', '2026-06-11 10:53:59');
INSERT INTO `sys_file` VALUES (2064903951932862465, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/51ecc6c9a493476a985819da3a0c9c35.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/51ecc6c9a493476a985819da3a0c9c35.png', '4-2.png', '51ecc6c9a493476a985819da3a0c9c35.png', 'png', 'image/png', 94507, NULL, NULL, NULL, 'ae484853fddbf066be140cacf3a4d1e1c0aa0a8f352655a15e75fcfdfded8ee4', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:01', '2026-06-11 10:54:01');
INSERT INTO `sys_file` VALUES (2064903958836686849, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/2c5bdbced70949319f303e0cd2dfd07d.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/2c5bdbced70949319f303e0cd2dfd07d.png', '4-3.png', '2c5bdbced70949319f303e0cd2dfd07d.png', 'png', 'image/png', 114243, NULL, NULL, NULL, '8b9aeb1565c9c26bd32d99903f80f7c8359004843c37d3aa3b69b49b126da1c6', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:03', '2026-06-11 10:54:03');
INSERT INTO `sys_file` VALUES (2064903965761482753, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/44af2672a8e443cea318063651099827.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/44af2672a8e443cea318063651099827.png', '4-4.png', '44af2672a8e443cea318063651099827.png', 'png', 'image/png', 154150, NULL, NULL, NULL, '9bc1f3895bff3c592901f06078e3d869ada7b85ce4807479afc8050d9e992bd0', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:04', '2026-06-11 10:54:04');
INSERT INTO `sys_file` VALUES (2064903991350931457, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/8cb98c8c625b4780b29145ff7790ad8d.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/8cb98c8c625b4780b29145ff7790ad8d.png', 'coze-01.png', '8cb98c8c625b4780b29145ff7790ad8d.png', 'png', 'image/png', 3813198, NULL, NULL, NULL, '68ab64cb8ddbc10cc63bb1d4e27ed238a43055044fc95dfaafef3661f065c9ef', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:10', '2026-06-11 10:54:10');
INSERT INTO `sys_file` VALUES (2064903998426722306, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/b5402d17bb5c4ee8afb8c03ba47bae23.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/b5402d17bb5c4ee8afb8c03ba47bae23.png', 'coze-02.png', 'b5402d17bb5c4ee8afb8c03ba47bae23.png', 'png', 'image/png', 192506, NULL, NULL, NULL, '388bcac25f88c4c33183c34b463769a32bbbbda763839a273d2d68073e3857e0', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:12', '2026-06-11 10:54:12');
INSERT INTO `sys_file` VALUES (2064904009738760194, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/e0f4a7f22d8243c0a7631f3d1d728fdd.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/e0f4a7f22d8243c0a7631f3d1d728fdd.png', 'coze-03.png', 'e0f4a7f22d8243c0a7631f3d1d728fdd.png', 'png', 'image/png', 654909, NULL, NULL, NULL, '92f0d2539e40d100c9f0790d4263efd0e7cabf3803443e2fe90e53423fdd790c', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:15', '2026-06-11 10:54:15');
INSERT INTO `sys_file` VALUES (2064904021210181634, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/76349e51d3d6483a84ee0fd324b1f617.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/76349e51d3d6483a84ee0fd324b1f617.png', 'coze-04.png', '76349e51d3d6483a84ee0fd324b1f617.png', 'png', 'image/png', 336180, NULL, NULL, NULL, 'a7ebfb1733cc78b7d6c866241d722f46809d835b0045c14c951a3d0191104895', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:17', '2026-06-11 10:54:17');
INSERT INTO `sys_file` VALUES (2064904029368102913, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/dd39bdb5c30a4aaa960e38085f3b93be.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/dd39bdb5c30a4aaa960e38085f3b93be.png', 'coze-05.png', 'dd39bdb5c30a4aaa960e38085f3b93be.png', 'png', 'image/png', 373621, NULL, NULL, NULL, '49023cb75545486172f469ef479389de40e874db782848a5e593045ea2ca6a52', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:19', '2026-06-11 10:54:19');
INSERT INTO `sys_file` VALUES (2064904040537534466, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/7c5b0a0e9ebb4aa09be6af9261096392.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/7c5b0a0e9ebb4aa09be6af9261096392.png', 'coze-06.png', '7c5b0a0e9ebb4aa09be6af9261096392.png', 'png', 'image/png', 302861, NULL, NULL, NULL, 'b26c5e7f3327654bdd62787ffecfd2b0c7536016372cfa9e6b2a8d7f6ca7d827', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:22', '2026-06-11 10:54:22');
INSERT INTO `sys_file` VALUES (2064904049202966530, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/b8e009de10874fb7bcf2b33391a83168.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/b8e009de10874fb7bcf2b33391a83168.png', 'coze-07.png', 'b8e009de10874fb7bcf2b33391a83168.png', 'png', 'image/png', 110281, NULL, NULL, NULL, 'd0c3e2bc7d209ac1096de717b085f8ad5998368a5d269f728e469e1711132594', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:24', '2026-06-11 10:54:24');
INSERT INTO `sys_file` VALUES (2064904055720914946, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/7fb0878cf95d4bd9baa77e2f168601f4.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/7fb0878cf95d4bd9baa77e2f168601f4.png', 'coze-08.png', '7fb0878cf95d4bd9baa77e2f168601f4.png', 'png', 'image/png', 118459, NULL, NULL, NULL, '85197a4b0e62dc2cdd277c3bff914fa90de933502105d5da304b3ff6b98467a8', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:26', '2026-06-11 10:54:26');
INSERT INTO `sys_file` VALUES (2064904068656148482, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/151767b00802432489d4affb8299a6e3.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/151767b00802432489d4affb8299a6e3.png', 'coze-09.png', '151767b00802432489d4affb8299a6e3.png', 'png', 'image/png', 616541, NULL, NULL, NULL, 'aa6bf2faff834e62a699d8c2a61865b177d76d7862b226342033604437f5df83', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:29', '2026-06-11 10:54:29');
INSERT INTO `sys_file` VALUES (2064904077296414722, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/62f82901206b413494638114238e5113.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/62f82901206b413494638114238e5113.png', 'coze-10.png', '62f82901206b413494638114238e5113.png', 'png', 'image/png', 186777, NULL, NULL, NULL, '30154c3dbf04c3fa6c438d97869074d19f983339e3562fc5c8404afd41c37c2a', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:31', '2026-06-11 10:54:31');
INSERT INTO `sys_file` VALUES (2064904089262764034, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/3af246a015414b9597c802e067d0630f.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/3af246a015414b9597c802e067d0630f.png', 'coze-11.png', '3af246a015414b9597c802e067d0630f.png', 'png', 'image/png', 324153, NULL, NULL, NULL, '4b0315524d5a63b7a6b91588ba31f4d44b9c3bee6533e76e175c621d8b617611', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:34', '2026-06-11 10:54:34');
INSERT INTO `sys_file` VALUES (2064904102785200130, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/47a7228503864b41ac8b9396714513b0.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/47a7228503864b41ac8b9396714513b0.png', 'coze-12.png', '47a7228503864b41ac8b9396714513b0.png', 'png', 'image/png', 589670, NULL, NULL, NULL, 'a79cce0e37c14cafbb7da4ecc0ad5abc3cbb6992c94275d37fc444c613fb344d', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:37', '2026-06-11 10:54:37');
INSERT INTO `sys_file` VALUES (2064904109739356162, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/f3675746a10546179147448cadc9737b.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/f3675746a10546179147448cadc9737b.png', 'coze-13.png', 'f3675746a10546179147448cadc9737b.png', 'png', 'image/png', 66887, NULL, NULL, NULL, '107b23c3b46123bb9fe8ba7ab0a846493c8d24bceaa00de45bb0d6976023581c', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:38', '2026-06-11 10:54:38');
INSERT INTO `sys_file` VALUES (2064904116873867266, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/66011b70e61c4e159f18b561326fbec2.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/66011b70e61c4e159f18b561326fbec2.png', 'coze-14.png', '66011b70e61c4e159f18b561326fbec2.png', 'png', 'image/png', 84189, NULL, NULL, NULL, 'f33051f820ce1529ee1cb737d292056b072567e6e917230588a4bc3a71dbcc02', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:40', '2026-06-11 10:54:40');
INSERT INTO `sys_file` VALUES (2064904125472190466, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/79318d445de94fd396d16fe0b14f5ce6.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/79318d445de94fd396d16fe0b14f5ce6.png', 'dify-01.png', '79318d445de94fd396d16fe0b14f5ce6.png', 'png', 'image/png', 191972, NULL, NULL, NULL, 'b445ce4edc67ae43a61c0c24abb622ecc764deb69ea5165d0600c6bb04a318e7', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:42', '2026-06-11 10:54:42');
INSERT INTO `sys_file` VALUES (2064904138378063873, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/f597de5e0a5a45128ad3d9419c6e18e4.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/f597de5e0a5a45128ad3d9419c6e18e4.png', 'dify-02.png', 'f597de5e0a5a45128ad3d9419c6e18e4.png', 'png', 'image/png', 315319, NULL, NULL, NULL, '2cfc4b3e3033393305f55c81502bd64d10bec6eaf0db2078b3445f6ac0ea1bdc', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:45', '2026-06-11 10:54:45');
INSERT INTO `sys_file` VALUES (2064904149228728322, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/780b2af3abf14116a6a8ba093172dbd9.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/780b2af3abf14116a6a8ba093172dbd9.png', 'dify-14.png', '780b2af3abf14116a6a8ba093172dbd9.png', 'png', 'image/png', 196443, NULL, NULL, NULL, 'a2e93743862cdeba3e62ae587731824eaac24b060402e2d6cfadfc07e9c4547f', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:48', '2026-06-11 10:54:48');
INSERT INTO `sys_file` VALUES (2064904162050715649, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/319f84b7ae64416183da78e39642c119.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/319f84b7ae64416183da78e39642c119.png', 'dify-18.png', '319f84b7ae64416183da78e39642c119.png', 'png', 'image/png', 373834, NULL, NULL, NULL, '4ba1d7ee0bbf573a1f051b4d0f385a170d1546097864d0524e02e3de3e392a18', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:51', '2026-06-11 10:54:51');
INSERT INTO `sys_file` VALUES (2064904175682203650, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/9a0a6fb38e944c19a5d9e8df0fb694f3.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/9a0a6fb38e944c19a5d9e8df0fb694f3.png', 'dify-15.png', '9a0a6fb38e944c19a5d9e8df0fb694f3.png', 'png', 'image/png', 379467, NULL, NULL, NULL, '79886b03923330e31787915d89e7c2fda84a008cf92a7a1dcb9f7f2c8de2118e', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:54', '2026-06-11 10:54:54');
INSERT INTO `sys_file` VALUES (2064904188072177666, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/787d5887d98b4435b4c9a1bc0debf693.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/787d5887d98b4435b4c9a1bc0debf693.png', 'dify-16.png', '787d5887d98b4435b4c9a1bc0debf693.png', 'png', 'image/png', 376657, NULL, NULL, NULL, '8654e3a74008ec1e932696aece60e4b71a67f3a2cc0d026ef1488b276968172a', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:57', '2026-06-11 10:54:57');
INSERT INTO `sys_file` VALUES (2064904197576470529, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/a7fc33e3ef484102b9b50974457e73f9.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/a7fc33e3ef484102b9b50974457e73f9.png', 'dify-17.png', 'a7fc33e3ef484102b9b50974457e73f9.png', 'png', 'image/png', 207539, NULL, NULL, NULL, 'f4bc344f42d6b52751e537c4c2faf103866b3f65c5f25be9d731a35a5f309616', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:54:59', '2026-06-11 10:54:59');
INSERT INTO `sys_file` VALUES (2064904211405090818, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/cf6240ce65dd4d39b5583001a940b049.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/cf6240ce65dd4d39b5583001a940b049.png', 'dify-19.png', 'cf6240ce65dd4d39b5583001a940b049.png', 'png', 'image/png', 503860, NULL, NULL, NULL, 'dbb5c041fa4093bfd166efb265881c7daa2e8d6d767f788d9a0740675dfdd49e', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:03', '2026-06-11 10:55:03');
INSERT INTO `sys_file` VALUES (2064904229323157506, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/f6967c59b88a4a91912e65454bdfef63.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/f6967c59b88a4a91912e65454bdfef63.png', 'dify-20.png', 'f6967c59b88a4a91912e65454bdfef63.png', 'png', 'image/png', 2014265, NULL, NULL, NULL, '669953d9828ab396b9c631e8388b9082a39b16c5ccb28abfd93cd9dafabc1f6e', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:07', '2026-06-11 10:55:07');
INSERT INTO `sys_file` VALUES (2064904243587985410, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/ef60afbe9514480bacc0a9049bc3cd04.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/ef60afbe9514480bacc0a9049bc3cd04.png', 'dify-21.png', 'ef60afbe9514480bacc0a9049bc3cd04.png', 'png', 'image/png', 1822376, NULL, NULL, NULL, '575e91864cc8f31ca227c3be6e946afe69a14b6920b63423177b9c931596356e', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:10', '2026-06-11 10:55:10');
INSERT INTO `sys_file` VALUES (2064904255617249282, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/45b8a8c2fcb84225afd83d2cc3f0fc8e.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/45b8a8c2fcb84225afd83d2cc3f0fc8e.png', 'dify-12.png', '45b8a8c2fcb84225afd83d2cc3f0fc8e.png', 'png', 'image/png', 1816760, NULL, NULL, NULL, 'b2e326a50f71d8b6750108301653cf481b67ceeffe7f428b1488b55f174a8145', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:13', '2026-06-11 10:55:13');
INSERT INTO `sys_file` VALUES (2064904265985568769, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/f1d1489bf911406197e7aa60675c29e2.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/f1d1489bf911406197e7aa60675c29e2.png', 'dify-03.png', 'f1d1489bf911406197e7aa60675c29e2.png', 'png', 'image/png', 177566, NULL, NULL, NULL, '1cc570c2afa84b9db8fcc0cd0d0d786ca8b677fb745b751172985edaa76fbf13', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:16', '2026-06-11 10:55:16');
INSERT INTO `sys_file` VALUES (2064904276144173057, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/6f7190f12f9449ba8a5a8ded9e03a480.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/6f7190f12f9449ba8a5a8ded9e03a480.png', 'dify-04.png', '6f7190f12f9449ba8a5a8ded9e03a480.png', 'png', 'image/png', 355773, NULL, NULL, NULL, '313589e3101b6411c74cf400e37be6cce2684b67f5f6e29a9a3bcef503ca9290', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:18', '2026-06-11 10:55:18');
INSERT INTO `sys_file` VALUES (2064904285363253250, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/e87847523875417f8f1d2af1cec0ccf8.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/e87847523875417f8f1d2af1cec0ccf8.png', 'dify-13.png', 'e87847523875417f8f1d2af1cec0ccf8.png', 'png', 'image/png', 170711, NULL, NULL, NULL, '549b4e1f46d345b2be7c2b19e4b171c6993d1ac4074bce621cd162a777fdb868', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:20', '2026-06-11 10:55:20');
INSERT INTO `sys_file` VALUES (2064904293554728961, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/a7e73800967d412191445c6f45fba6fb.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/a7e73800967d412191445c6f45fba6fb.png', 'dify-05.png', 'a7e73800967d412191445c6f45fba6fb.png', 'png', 'image/png', 437192, NULL, NULL, NULL, '96d08e9dd995866b93ec495bcf90fa376ca2a95f5adf391568fc19d5386507c0', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:22', '2026-06-11 10:55:22');
INSERT INTO `sys_file` VALUES (2064904300294975490, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/66c7566b61c541d4ac2581d01729ae4d.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/66c7566b61c541d4ac2581d01729ae4d.png', 'dify-06.png', '66c7566b61c541d4ac2581d01729ae4d.png', 'png', 'image/png', 113032, NULL, NULL, NULL, '8132307610de839d29cc0f6069a48b188224159d9bc756965c1c743e4cb52556', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:24', '2026-06-11 10:55:24');
INSERT INTO `sys_file` VALUES (2064904307572092929, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/ea62e4344c244512bb8bc6ca06b85632.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/ea62e4344c244512bb8bc6ca06b85632.png', 'dify-22.png', 'ea62e4344c244512bb8bc6ca06b85632.png', 'png', 'image/png', 221300, NULL, NULL, NULL, 'ddc1e3b86246930cd043f99e8584cfbdc7267f55bec7a8569b7262cf50b4eb13', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:26', '2026-06-11 10:55:26');
INSERT INTO `sys_file` VALUES (2064904315302195202, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/58d9e8a945594a3ea96eda89b197de13.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/58d9e8a945594a3ea96eda89b197de13.png', 'dify-07.png', '58d9e8a945594a3ea96eda89b197de13.png', 'png', 'image/png', 109395, NULL, NULL, NULL, 'cda20825f4f7610a59ab3577f4c0fffae029f3b6787eadea648c8fcf4011a8a3', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:27', '2026-06-11 10:55:27');
INSERT INTO `sys_file` VALUES (2064904322638032898, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/13a6f89e30f642399b8d3f8be1e403dc.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/13a6f89e30f642399b8d3f8be1e403dc.png', 'dify-08.png', '13a6f89e30f642399b8d3f8be1e403dc.png', 'png', 'image/png', 208304, NULL, NULL, NULL, 'c27951fa4d698e2f30d248d1e42e91dad6c4dde0f979248314cdaa0d60deab2e', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:29', '2026-06-11 10:55:29');
INSERT INTO `sys_file` VALUES (2064904332767277057, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/f92a1b75001d4bec99831ac4f21e6a1d.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/f92a1b75001d4bec99831ac4f21e6a1d.png', 'dify-23.png', 'f92a1b75001d4bec99831ac4f21e6a1d.png', 'png', 'image/png', 80972, NULL, NULL, NULL, '82abb9f34bf70aeedac6ca026d31872e0b4d7e1897ab3d010f39627dcbe66df0', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:32', '2026-06-11 10:55:32');
INSERT INTO `sys_file` VALUES (2064904340082143233, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/e13316a5b55840a5a7fd27ff0b035aaf.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/e13316a5b55840a5a7fd27ff0b035aaf.png', 'dify-09.png', 'e13316a5b55840a5a7fd27ff0b035aaf.png', 'png', 'image/png', 178776, NULL, NULL, NULL, 'b5487539a9c4bc7a349073d38a387335b5736b2e663512553df0cbaf3bcda18d', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:33', '2026-06-11 10:55:33');
INSERT INTO `sys_file` VALUES (2064904348462362625, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/4d83f03824fd4434a70a49f59196d4db.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/4d83f03824fd4434a70a49f59196d4db.png', 'dify-10.png', '4d83f03824fd4434a70a49f59196d4db.png', 'png', 'image/png', 217348, NULL, NULL, NULL, 'f574b5f8f33a5940e49c577737f2e4d3061ee7852faf026d88ca1b6ffbee794c', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:35', '2026-06-11 10:55:35');
INSERT INTO `sys_file` VALUES (2064904355630428161, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/bc78023eac964fa494770162e8be2f0b.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/bc78023eac964fa494770162e8be2f0b.png', 'dify-11.png', 'bc78023eac964fa494770162e8be2f0b.png', 'png', 'image/png', 144783, NULL, NULL, NULL, 'f5666da008b13324dc2f07305a530a461688df250ebb1decb6897fabb5257d62', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:37', '2026-06-11 10:55:37');
INSERT INTO `sys_file` VALUES (2064904362911739906, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/dc8cf53590a44118bb17b592bda58e71.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/dc8cf53590a44118bb17b592bda58e71.png', 'n8n-01.png', 'dc8cf53590a44118bb17b592bda58e71.png', 'png', 'image/png', 174064, NULL, NULL, NULL, '6be74e75b84134c502387b33f81a61351e3e6f758168b490b290258c830cec3e', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:39', '2026-06-11 10:55:39');
INSERT INTO `sys_file` VALUES (2064904370042056706, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/a9a2a492fa8a44b8ae608de46f77f8c2.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/a9a2a492fa8a44b8ae608de46f77f8c2.png', 'n8n-02.png', 'a9a2a492fa8a44b8ae608de46f77f8c2.png', 'png', 'image/png', 183483, NULL, NULL, NULL, '74af58bc8e2b400dc66fb08f42ed1606078dfc06280ab4fdb224c335d585b49d', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:41', '2026-06-11 10:55:41');
INSERT INTO `sys_file` VALUES (2064904377256259586, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/97eea5d9fd9740389e6252c1c22aeeac.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/97eea5d9fd9740389e6252c1c22aeeac.png', 'n8n-03.png', '97eea5d9fd9740389e6252c1c22aeeac.png', 'png', 'image/png', 166133, NULL, NULL, NULL, 'ee2c2d7e07562b433ef0bfa3f913e8ae3a45c469b1fadd65a9e47603be7493d1', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:42', '2026-06-11 10:55:42');
INSERT INTO `sys_file` VALUES (2064904384378187777, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/1628e692a090466ead4f7952a4ea98f8.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/1628e692a090466ead4f7952a4ea98f8.png', 'n8n-04.png', '1628e692a090466ead4f7952a4ea98f8.png', 'png', 'image/png', 178269, NULL, NULL, NULL, 'c07fb77a89e2656a77e46b6346318d3b75e06ad9f3a60238c89702b5772c87b3', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:44', '2026-06-11 10:55:44');
INSERT INTO `sys_file` VALUES (2064904391558836225, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/1a393d5936364715847d93f43a9a1780.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/1a393d5936364715847d93f43a9a1780.png', 'n8n-05.png', '1a393d5936364715847d93f43a9a1780.png', 'png', 'image/png', 102379, NULL, NULL, NULL, '7940ce71c09729618ffe593a8aba46fd0321935de4be101270f6dc5a19575320', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:46', '2026-06-11 10:55:46');
INSERT INTO `sys_file` VALUES (2064904397984509953, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/f7fe2182283a4cbb9602bb5ebc6fef48.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/f7fe2182283a4cbb9602bb5ebc6fef48.png', 'n8n-06.png', 'f7fe2182283a4cbb9602bb5ebc6fef48.png', 'png', 'image/png', 79096, NULL, NULL, NULL, '5655082069e6773be4ba9d51652d3f59c9b510fd094301e932bc20942f18c5a4', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:47', '2026-06-11 10:55:47');
INSERT INTO `sys_file` VALUES (2064904404447932417, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/20f4b89a729d47ef80e8497d26855a9b.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/20f4b89a729d47ef80e8497d26855a9b.png', 'n8n-07.png', '20f4b89a729d47ef80e8497d26855a9b.png', 'png', 'image/png', 83351, NULL, NULL, NULL, '3f71fb2f839fe29d10dbfcd924ebf764a8d34c8c80503ee5a0f66cc3cc2153c6', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:49', '2026-06-11 10:55:49');
INSERT INTO `sys_file` VALUES (2064904411590832130, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/213d9fe2d2174cc3b698192b8912ebf4.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/213d9fe2d2174cc3b698192b8912ebf4.png', 'n8n-08.png', '213d9fe2d2174cc3b698192b8912ebf4.png', 'png', 'image/png', 181541, NULL, NULL, NULL, 'a5a099f27cc7caa79fb455347769eafcc5cab6acd714e31d171b8902bf0af17e', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:50', '2026-06-11 10:55:50');
INSERT INTO `sys_file` VALUES (2064904418830200834, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/b8c50ef807e143899836dadc9887b441.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/b8c50ef807e143899836dadc9887b441.png', 'n8n-09.png', 'b8c50ef807e143899836dadc9887b441.png', 'png', 'image/png', 154952, NULL, NULL, NULL, '77a9ca4034648e0fbe1a5526116034529d4f103e16ea64aac64ff619c0169ef6', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:52', '2026-06-11 10:55:52');
INSERT INTO `sys_file` VALUES (2064904426430279681, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/80814c16d877401fa1bbed9df50266f6.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/80814c16d877401fa1bbed9df50266f6.png', 'n8n-10.png', '80814c16d877401fa1bbed9df50266f6.png', 'png', 'image/png', 247590, NULL, NULL, NULL, '5e02327b80d26fd814c2ac66e4d9f7bd72c7be69ac839fdf30c77ebe695ab65f', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:54', '2026-06-11 10:55:54');
INSERT INTO `sys_file` VALUES (2064904433594150914, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/4af1832f7fb641008326deac057e6b91.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/4af1832f7fb641008326deac057e6b91.png', 'n8n-11.png', '4af1832f7fb641008326deac057e6b91.png', 'png', 'image/png', 172794, NULL, NULL, NULL, 'e53c1319513563c3d8577217192f6b558363baac85c07c7678c145a801161371', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:56', '2026-06-11 10:55:56');
INSERT INTO `sys_file` VALUES (2064904441194229762, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/6bc1f3a385654d15a3277cd628edb043.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/6bc1f3a385654d15a3277cd628edb043.png', 'n8n-12.png', '6bc1f3a385654d15a3277cd628edb043.png', 'png', 'image/png', 99348, NULL, NULL, NULL, 'c96425cfb3f95be82c955bc11526f3cca75a021122bba93b3629f8aca3534a58', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:58', '2026-06-11 10:55:58');
INSERT INTO `sys_file` VALUES (2064904447934476290, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/1d699036f60b4f38bd759bdb4d931ec9.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/1d699036f60b4f38bd759bdb4d931ec9.png', 'n8n-13.png', '1d699036f60b4f38bd759bdb4d931ec9.png', 'png', 'image/png', 106291, NULL, NULL, NULL, '3319d9ef6eb68195f571342b96e5ae6ecae6188df91b29c712e75a7671f7be73', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:55:59', '2026-06-11 10:55:59');
INSERT INTO `sys_file` VALUES (2064904455056404482, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/1c142c3dd93d4c6387480e89b0fdc806.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/1c142c3dd93d4c6387480e89b0fdc806.png', 'n8n-14.png', '1c142c3dd93d4c6387480e89b0fdc806.png', 'png', 'image/png', 165662, NULL, NULL, NULL, 'cce3157cde3c7ff19022b6d1681f11809b4b4bdab21d2ad5db5eb624df92eafc', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:01', '2026-06-11 10:56:01');
INSERT INTO `sys_file` VALUES (2064904476803870721, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/7ecf71e995dc4a3cbba0366dd63656bb.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/7ecf71e995dc4a3cbba0366dd63656bb.png', '01.png', '7ecf71e995dc4a3cbba0366dd63656bb.png', 'png', 'image/png', 226970, NULL, NULL, NULL, '8d4f1e7c8d4ffc7308105b84ba1d45d6df78088ffd8ceec3bbf6f85127126d57', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:06', '2026-06-11 10:56:06');
INSERT INTO `sys_file` VALUES (2064904483250515969, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/6972a14f730046d9aa411a8697f32ffa.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/6972a14f730046d9aa411a8697f32ffa.png', '02.png', '6972a14f730046d9aa411a8697f32ffa.png', 'png', 'image/png', 281903, NULL, NULL, NULL, '972a05dd901d02e0bc62131ed02bfdbbde51cf1db9b46735381d12ea2214cc7d', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:08', '2026-06-11 10:56:08');
INSERT INTO `sys_file` VALUES (2064904492654145538, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/b7d3b79936014cd48079284fe436043a.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/b7d3b79936014cd48079284fe436043a.png', '03.png', 'b7d3b79936014cd48079284fe436043a.png', 'png', 'image/png', 955244, NULL, NULL, NULL, '107d4419c97e046838ab6f1159bd70807f3a453b674570e0203dbf97cff2bf7e', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:10', '2026-06-11 10:56:10');
INSERT INTO `sys_file` VALUES (2064904500447162369, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/9ff2c7d4f539419ba496f907d4946493.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/9ff2c7d4f539419ba496f907d4946493.png', '04.png', '9ff2c7d4f539419ba496f907d4946493.png', 'png', 'image/png', 366360, NULL, NULL, NULL, '40741f2994dd26ce98aaac87cdf765dba9e56952de8aa30a97daa3aa75f2daf3', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:12', '2026-06-11 10:56:12');
INSERT INTO `sys_file` VALUES (2064904517849329666, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/d2878312442049b6b0f0362300aab706.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/d2878312442049b6b0f0362300aab706.png', 'table-01.png', 'd2878312442049b6b0f0362300aab706.png', 'png', 'image/png', 167336, NULL, NULL, NULL, 'a4e1fb8852b191e5eeb5bc5bde801a302fc63301f8d7023a04995a66099aea8a', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:16', '2026-06-11 10:56:16');
INSERT INTO `sys_file` VALUES (2064904525239693314, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/d9240f0dd95f4256a91f33e15a1a6e59.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/d9240f0dd95f4256a91f33e15a1a6e59.png', 'table-02.png', 'd9240f0dd95f4256a91f33e15a1a6e59.png', 'png', 'image/png', 234130, NULL, NULL, NULL, '21438bff460bba08789561571ff219da572cb4634b59247f7d7a900dc98336b1', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:18', '2026-06-11 10:56:18');
INSERT INTO `sys_file` VALUES (2064904534718820354, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/8a9c133d83474f10a5d34aa4810b6af6.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/8a9c133d83474f10a5d34aa4810b6af6.png', '01.png', '8a9c133d83474f10a5d34aa4810b6af6.png', 'png', 'image/png', 1126211, NULL, NULL, NULL, '13f290c6f2dd802a86599e778eeb87a5688faf8def7e084e88beba55d7de14aa', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:20', '2026-06-11 10:56:20');
INSERT INTO `sys_file` VALUES (2064904549415661570, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/2886fd90656f4a46b19d10585d9147c1.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/2886fd90656f4a46b19d10585d9147c1.png', '8-1.png', '2886fd90656f4a46b19d10585d9147c1.png', 'png', 'image/png', 140133, NULL, NULL, NULL, 'd5fde21ad58653e1af819184d14e05b8fb44342f25acea0d3f9fb11fcd18b3f2', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:23', '2026-06-11 10:56:23');
INSERT INTO `sys_file` VALUES (2064904557284175874, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/0acb3d08000243d48943924cd80fab7e.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/0acb3d08000243d48943924cd80fab7e.png', '8-2.png', '0acb3d08000243d48943924cd80fab7e.png', 'png', 'image/png', 361499, NULL, NULL, NULL, '1b9eb648b4ee0d5aac31f813264b9b25554691e0ee722d3c93b99b60c326317c', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:25', '2026-06-11 10:56:25');
INSERT INTO `sys_file` VALUES (2064904563705655297, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/de0c835c7d894c29b87fc42a1be3da65.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/de0c835c7d894c29b87fc42a1be3da65.png', '8-3.png', 'de0c835c7d894c29b87fc42a1be3da65.png', 'png', 'image/png', 83756, NULL, NULL, NULL, '909a00e6fb4cd0411330fe0c9d018072846381b52b46a379a6ac47de409462d3', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:27', '2026-06-11 10:56:27');
INSERT INTO `sys_file` VALUES (2064904578096312322, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/52e26a1cfbb447959c8035130c9cebf8.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/52e26a1cfbb447959c8035130c9cebf8.png', '8-4.png', '52e26a1cfbb447959c8035130c9cebf8.png', 'png', 'image/png', 379260, NULL, NULL, NULL, '69d63d6b346c6e4a18b67df8c73d8c49c70e7d8cb3c0786cabd5dd4241223b3c', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:30', '2026-06-11 10:56:30');
INSERT INTO `sys_file` VALUES (2064904587420250114, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/7f9e6ec3694b4e188d692d6aba3b0942.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/7f9e6ec3694b4e188d692d6aba3b0942.png', '8-5.png', '7f9e6ec3694b4e188d692d6aba3b0942.png', 'png', 'image/png', 393216, NULL, NULL, NULL, 'c38e8321e8955de1e7b275f5595a7010c57ac0bcab8196363d2e7a33b5fca470', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:32', '2026-06-11 10:56:32');
INSERT INTO `sys_file` VALUES (2064904598010867714, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/f74e472644994fdc9f92cf45f207cafd.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/f74e472644994fdc9f92cf45f207cafd.png', '8-6.png', 'f74e472644994fdc9f92cf45f207cafd.png', 'png', 'image/png', 63391, NULL, NULL, NULL, '61155f4d14553665b468c081943ae9470a6e52f038e273c8c5abf33135cb09d8', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:35', '2026-06-11 10:56:35');
INSERT INTO `sys_file` VALUES (2064904604419764226, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/d52b20d9c2cb4bf08e231b75e8173dfe.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/d52b20d9c2cb4bf08e231b75e8173dfe.png', '8-7.png', 'd52b20d9c2cb4bf08e231b75e8173dfe.png', 'png', 'image/png', 80390, NULL, NULL, NULL, '129d205a2ab5c360c1c6707660b0137890bcec64ca081345d0959799ad499e60', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:36', '2026-06-11 10:56:36');
INSERT INTO `sys_file` VALUES (2064904611763990530, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/d65d2ee72da64ffd90d4c0230c0fed6a.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/d65d2ee72da64ffd90d4c0230c0fed6a.png', '8-8.png', 'd65d2ee72da64ffd90d4c0230c0fed6a.png', 'png', 'image/png', 216470, NULL, NULL, NULL, 'beeab6c99dff34a160f010ef74a61a03c9ccfb30284f8a38501ace250e73d0a7', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:38', '2026-06-11 10:56:38');
INSERT INTO `sys_file` VALUES (2064904618831392770, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/bf2d61dc418948fd805cf1cce750f7dc.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/bf2d61dc418948fd805cf1cce750f7dc.png', '8-9.png', 'bf2d61dc418948fd805cf1cce750f7dc.png', 'png', 'image/png', 74728, NULL, NULL, NULL, 'ff5850387bcd5902f62575c089d7c4810be348f571afd940363454351504bc68', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:40', '2026-06-11 10:56:40');
INSERT INTO `sys_file` VALUES (2064904625366118402, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/18ccb43aca1148689f5e557494bb88b5.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/18ccb43aca1148689f5e557494bb88b5.png', '8-10.png', '18ccb43aca1148689f5e557494bb88b5.png', 'png', 'image/png', 86037, NULL, NULL, NULL, 'b309d55d9651f6be0f17df6def019aa8a5cdaa1089195919f563133eb13894ee', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:41', '2026-06-11 10:56:41');
INSERT INTO `sys_file` VALUES (2064904634245459970, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/11136fcaa21f4b19b57e8fc94170ce15.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/11136fcaa21f4b19b57e8fc94170ce15.png', '8-11.png', '11136fcaa21f4b19b57e8fc94170ce15.png', 'png', 'image/png', 808923, NULL, NULL, NULL, 'ee94426313f79a0ecbfe965c8ea2a6ad16b96b23c3ac95e308ae85eb928d4550', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:44', '2026-06-11 10:56:44');
INSERT INTO `sys_file` VALUES (2064904650783600642, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/ee719377fe354487a9a923026f9e1a3d.webp', 'http://oss.orccode.com/public/blog/images/2026/06/11/ee719377fe354487a9a923026f9e1a3d.webp', '9-1.webp', 'ee719377fe354487a9a923026f9e1a3d.webp', 'webp', 'image/webp', 68676, NULL, NULL, NULL, '2c80407789814d462d3e75d2f530b82a8461ef52c7d5fa7bd3207e57f957f6fe', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:47', '2026-06-11 10:56:47');
INSERT INTO `sys_file` VALUES (2064904657481904130, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/6d9ccb7b597c48fb9fcba622eed14af6.webp', 'http://oss.orccode.com/public/blog/images/2026/06/11/6d9ccb7b597c48fb9fcba622eed14af6.webp', '9-2.webp', '6d9ccb7b597c48fb9fcba622eed14af6.webp', 'webp', 'image/webp', 136390, NULL, NULL, NULL, '9afca72306eb25f348bcd2c924f19e4f679fa877242e2c4f6eb8fa1136a0d2a7', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:49', '2026-06-11 10:56:49');
INSERT INTO `sys_file` VALUES (2064904665220395009, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/f009299ce4644b97bffa5e99abc438cc.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/f009299ce4644b97bffa5e99abc438cc.png', '9-3.png', 'f009299ce4644b97bffa5e99abc438cc.png', 'png', 'image/png', 325206, NULL, NULL, NULL, 'a6cd8da1a7e49e1243fb8b79ec7b79947584b342b6e8aed5df2efb8ecf05458a', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:51', '2026-06-11 10:56:51');
INSERT INTO `sys_file` VALUES (2064904681930502146, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/2165403995294a94b17b8487fc683a0e.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/2165403995294a94b17b8487fc683a0e.png', '10-1.png', '2165403995294a94b17b8487fc683a0e.png', 'png', 'image/png', 224681, NULL, NULL, NULL, '659f3fa6941d9303d0a72c559b76c41b60a57a17ceea4445c31d1d36536069f6', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:55', '2026-06-11 10:56:55');
INSERT INTO `sys_file` VALUES (2064904690130366465, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/c063db26442d4b4e866b94a4b7f058e4.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/c063db26442d4b4e866b94a4b7f058e4.png', '10-2.png', 'c063db26442d4b4e866b94a4b7f058e4.png', 'png', 'image/png', 293945, NULL, NULL, NULL, 'fc2b1e3393cc211a0ba23326b0e6ad1e29412812ab1932a7d3d06b704cb1012e', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:57', '2026-06-11 10:56:57');
INSERT INTO `sys_file` VALUES (2064904698141487105, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/a07c03328a8043f78939644c701f0331.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/a07c03328a8043f78939644c701f0331.png', '10-3.png', 'a07c03328a8043f78939644c701f0331.png', 'png', 'image/png', 278386, NULL, NULL, NULL, '6ba525f633c8be9b25b158722844fd257fe4d30d4c4c1ee20a8297c52105179d', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:56:59', '2026-06-11 10:56:59');
INSERT INTO `sys_file` VALUES (2064904705531850753, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/a8b0cc643f0f47ff8c88dd283ccd74d2.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/a8b0cc643f0f47ff8c88dd283ccd74d2.png', '10-table-1.png', 'a8b0cc643f0f47ff8c88dd283ccd74d2.png', 'png', 'image/png', 239680, NULL, NULL, NULL, '52b03b6c1fc87d0b0c5165717c2f216b9aa560b69cd428877bdd15e6a7874018', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:01', '2026-06-11 10:57:01');
INSERT INTO `sys_file` VALUES (2064904713182261249, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/38b4714c06f04fdc8239074a87efd81e.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/38b4714c06f04fdc8239074a87efd81e.png', '10-4.png', '38b4714c06f04fdc8239074a87efd81e.png', 'png', 'image/png', 312996, NULL, NULL, NULL, 'f02046f5709c83a845f531b64514011d9ea2649bfb25be0ea08ea11128894d93', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:02', '2026-06-11 10:57:02');
INSERT INTO `sys_file` VALUES (2064904721596035073, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/0e9f2cd051e94da383d79be0a947569d.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/0e9f2cd051e94da383d79be0a947569d.png', '10-5.png', '0e9f2cd051e94da383d79be0a947569d.png', 'png', 'image/png', 269969, NULL, NULL, NULL, '5e51e7c2d9b0e5b6ee0bfc3c00c1a265bf8d7706809ac79aa4718bf95fa24ab1', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:04', '2026-06-11 10:57:04');
INSERT INTO `sys_file` VALUES (2064904728235618306, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/17877542c9fd4e2aa59a54185ce525e5.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/17877542c9fd4e2aa59a54185ce525e5.png', '10-table-2.png', '17877542c9fd4e2aa59a54185ce525e5.png', 'png', 'image/png', 131323, NULL, NULL, NULL, '1bd13e85fd22d50f050d189357cebdb8454e8aa9ba240d5da932019b1648e911', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:06', '2026-06-11 10:57:06');
INSERT INTO `sys_file` VALUES (2064904736452259841, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/6e7149480fd845d488d90e76a9c61375.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/6e7149480fd845d488d90e76a9c61375.png', '10-6.png', '6e7149480fd845d488d90e76a9c61375.png', 'png', 'image/png', 422368, NULL, NULL, NULL, '6b9aff6a4a53974d395ea888284566f63664a24feaf5960c038a28c79c6c5653', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:08', '2026-06-11 10:57:08');
INSERT INTO `sys_file` VALUES (2064904743431581697, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/bc7cb28e105240baafebc5e779c996e7.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/bc7cb28e105240baafebc5e779c996e7.png', '10-table-3.png', 'bc7cb28e105240baafebc5e779c996e7.png', 'png', 'image/png', 196087, NULL, NULL, NULL, '67aaf3d529858b6d32e287a7f98611af097339a705faf56187656a44721d520d', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:10', '2026-06-11 10:57:10');
INSERT INTO `sys_file` VALUES (2064904750951968770, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/402452cdb5e248a29e7ffe320557192f.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/402452cdb5e248a29e7ffe320557192f.png', '10-table-4.png', '402452cdb5e248a29e7ffe320557192f.png', 'png', 'image/png', 207528, NULL, NULL, NULL, '00c677ee4faa04a424a92ff6f0a04205bcd76f2f75acb61e34f906642cdc7087', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:11', '2026-06-11 10:57:11');
INSERT INTO `sys_file` VALUES (2064904758619156481, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/9f55c578c9524011ab1d681110da8ff2.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/9f55c578c9524011ab1d681110da8ff2.png', '10-table-5.png', '9f55c578c9524011ab1d681110da8ff2.png', 'png', 'image/png', 253553, NULL, NULL, NULL, '821e393436c626592b1614d09b65ca195653c828907bf10e94bf67ac8f965cd5', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:13', '2026-06-11 10:57:13');
INSERT INTO `sys_file` VALUES (2064904773630570497, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/4b3ae378ec674fd29f5124bc2850701d.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/4b3ae378ec674fd29f5124bc2850701d.png', '10-table-6.png', '4b3ae378ec674fd29f5124bc2850701d.png', 'png', 'image/png', 367610, NULL, NULL, NULL, '3789070977809d4bbc9170e8559f0994e668831d1df7c5ae1d9501f0c6fb9b5d', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:17', '2026-06-11 10:57:17');
INSERT INTO `sys_file` VALUES (2064904781092237314, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/37620f78ca9040ecbee4bce2b5acd5b0.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/37620f78ca9040ecbee4bce2b5acd5b0.png', '10-table-7.png', '37620f78ca9040ecbee4bce2b5acd5b0.png', 'png', 'image/png', 203744, NULL, NULL, NULL, '6cb86b64f503603cab5424aea220003900e4b7e6ca862de7074da1e75342552c', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:19', '2026-06-11 10:57:19');
INSERT INTO `sys_file` VALUES (2064904788214165505, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/5e1b7f7e1f7c4c899f9f3febab9d5dd2.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/5e1b7f7e1f7c4c899f9f3febab9d5dd2.png', '10-7.png', '5e1b7f7e1f7c4c899f9f3febab9d5dd2.png', 'png', 'image/png', 131749, NULL, NULL, NULL, 'fadc8b034b9f5f100589c11d12ef791a430ac49b385953236d8f07cd1b380646', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:20', '2026-06-11 10:57:20');
INSERT INTO `sys_file` VALUES (2064904796242063361, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/13e3e7a8f284421193d6ef7ccbbf985f.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/13e3e7a8f284421193d6ef7ccbbf985f.png', '10-8.png', '13e3e7a8f284421193d6ef7ccbbf985f.png', 'png', 'image/png', 361775, NULL, NULL, NULL, 'a3fd0bb7bd060f5c8214e1f083e134d99de8b247a236901f715e6d9270d24744', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:22', '2026-06-11 10:57:22');
INSERT INTO `sys_file` VALUES (2064904803712118786, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/02397c8e36fa4fafa93680789ea37be9.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/02397c8e36fa4fafa93680789ea37be9.png', '10-table-8.png', '02397c8e36fa4fafa93680789ea37be9.png', 'png', 'image/png', 175253, NULL, NULL, NULL, '0cbe7612b794716c71974ec2cb50ce9698152ffb1511cc4f7d259690e32a6ff2', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:24', '2026-06-11 10:57:24');
INSERT INTO `sys_file` VALUES (2064904812381745153, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/fbe3fa17691e4fe39b32fbb14a0f7640.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/fbe3fa17691e4fe39b32fbb14a0f7640.png', '10-9.png', 'fbe3fa17691e4fe39b32fbb14a0f7640.png', 'png', 'image/png', 393253, NULL, NULL, NULL, '970d363039cfcf252f2976b88c7b30fa6d77fb60c7cabcc04836d3fd85636509', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:26', '2026-06-11 10:57:26');
INSERT INTO `sys_file` VALUES (2064904821030400002, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/4cd61c5f489049a29f8e20b37373ae7d.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/4cd61c5f489049a29f8e20b37373ae7d.png', '10-10.png', '4cd61c5f489049a29f8e20b37373ae7d.png', 'png', 'image/png', 79783, NULL, NULL, NULL, '72c279fc51fe94fc1c4d4a83b3c97171c15ad96364a14c19b1c616f3400360d3', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:28', '2026-06-11 10:57:28');
INSERT INTO `sys_file` VALUES (2064904828278157313, 'blog_post', 0, 'image', 'minio', NULL, 'public', 'blog/images/2026/06/11/0ed1d504064f4b9ba07248381707da55.png', 'http://oss.orccode.com/public/blog/images/2026/06/11/0ed1d504064f4b9ba07248381707da55.png', '10-11.png', '0ed1d504064f4b9ba07248381707da55.png', 'png', 'image/png', 147223, NULL, NULL, NULL, 'd4fa0763165ce2029b14b46ddd1ffdc2d552959024b48768d17f6b175e37bece', 1, 1, 0, NULL, 2052290101098295297, '2026-06-11 10:57:30', '2026-06-11 10:57:30');

-- ----------------------------
-- Table structure for sys_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu`  (
  `id` bigint(20) NOT NULL COMMENT '菜单ID',
  `parent_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '父菜单ID，0表示根节点',
  `menu_type` tinyint(4) NOT NULL COMMENT '类型：1目录 2菜单 3按钮 4内嵌 5外链',
  `menu_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '名称',
  `route_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '路由名（vue-router name），跨菜单唯一',
  `path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '前端路由路径（如 /user）',
  `component` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '前端组件路径（如 system/user/index）',
  `perms` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '权限标识（如 user:list, user:delete）',
  `icon` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '图标',
  `active_icon` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '激活态图标',
  `active_path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '高亮指定路径',
  `link_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '外链/内嵌地址（type=link 用 link，type=embedded 用 iframeSrc）',
  `keep_alive` tinyint(4) NOT NULL DEFAULT 0 COMMENT '是否缓存：1是 0否',
  `affix_tab` tinyint(4) NOT NULL DEFAULT 0 COMMENT '是否固定 tab：1是 0否',
  `hide_in_menu` tinyint(4) NOT NULL DEFAULT 0 COMMENT '是否在菜单隐藏：1是 0否',
  `hide_children_in_menu` tinyint(4) NOT NULL DEFAULT 0 COMMENT '是否隐藏子菜单：1是 0否',
  `hide_in_breadcrumb` tinyint(4) NOT NULL DEFAULT 0 COMMENT '是否在面包屑隐藏：1是 0否',
  `hide_in_tab` tinyint(4) NOT NULL DEFAULT 0 COMMENT '是否在多页签隐藏：1是 0否',
  `badge_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '徽章类型：dot/normal',
  `badge` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '徽章文本',
  `badge_variants` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '徽章样式 default/destructive/primary/success/warning',
  `sort` int(11) NOT NULL DEFAULT 0 COMMENT '排序（越小越靠前）',
  `visible` tinyint(4) NOT NULL DEFAULT 1 COMMENT '是否显示：1是 0否',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：1正常 0禁用',
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
INSERT INTO `sys_menu` VALUES (40, 0, 1, '个人空间', 'Space', '/space', 'BasicLayout', NULL, 'lucide:bookmark', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 250, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (41, 40, 2, '书签管理', 'SpaceBookmark', '/space/bookmark', 'space/bookmark/index', 'space:bookmark:list', 'lucide:bookmark-plus', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (42, 40, 2, '目录管理', 'SpaceFolder', '/space/folder', 'space/folder/index', 'space:folder:list', 'lucide:folder-tree', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (43, 40, 2, '标签管理', 'SpaceTag', '/space/tag', 'space/tag/index', 'space:tag:list', 'lucide:tags', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (44, 40, 2, '导入任务', 'SpaceImportTask', '/space/import-task', 'space/import-task/index', 'space:bookmark-import:list', 'lucide:download', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (45, 40, 2, '导出任务', 'SpaceExportTask', '/space/export-task', 'space/export-task/index', 'space:bookmark-export:list', 'lucide:upload', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 5, 1, 1, NULL, '2026-05-30 00:00:00', '2026-05-30 00:00:00');
INSERT INTO `sys_menu` VALUES (50, 0, 1, '插件商城', 'Forge', '/forge', 'BasicLayout', NULL, 'lucide:blocks', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 400, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (51, 50, 2, '插件分类', 'ForgeCategory', '/forge/category', 'forge/category/index', 'forge:category:list', 'lucide:folder-tree', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (52, 50, 2, '插件管理', 'ForgePlugin', '/forge/plugin', 'forge/plugin/index', 'forge:plugin:list', 'lucide:package', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (53, 50, 2, '插件版本', 'ForgePluginVersion', '/forge/plugin/:id/versions', 'forge/plugin/versions', 'forge:version:list', 'lucide:git-branch', NULL, '/forge/plugin', NULL, 0, 0, 1, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, '插件版本与权限声明管理（详情页）', '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (54, 50, 2, '评价管理', 'ForgeReview', '/forge/review', 'forge/review/index', 'forge:review:list', 'lucide:star', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (55, 50, 2, '下载日志', 'ForgeDownloadLog', '/forge/download-log', 'forge/download-log/index', 'forge:download-log:list', 'lucide:download', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 5, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (80, 0, 1, '智能体', 'AiAgent', '/ai-agent', 'BasicLayout', NULL, 'lucide:bot', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 315, 1, 1, NULL, '2026-07-06 00:00:00', '2026-07-06 00:00:00');
INSERT INTO `sys_menu` VALUES (81, 80, 2, '智能体定义', 'AiAgentDefinition', '/ai-agent/agents', 'ai-agent/agents', 'manager:ai-agent:list', 'lucide:bot', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-07-06 00:00:00', '2026-07-06 00:00:00');
INSERT INTO `sys_menu` VALUES (82, 80, 2, '智能体实例', 'AiAgentInstance', '/ai-agent/instances', 'ai-agent/index', 'manager:ai-agent:list', 'lucide:activity', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-07-06 00:00:00', '2026-07-06 00:00:00');
INSERT INTO `sys_menu` VALUES (83, 80, 2, '实例回放', 'AiAgentReplay', '/ai-agent/replay', 'ai-agent/replay/index', 'manager:ai-agent:query', 'lucide:play-circle', NULL, '/ai-agent/instances', NULL, 0, 0, 1, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-07-06 00:00:00', '2026-07-06 00:00:00');
INSERT INTO `sys_menu` VALUES (84, 80, 2, '流程列表', 'AiFlowList', '/ai-flow/list', 'ai-flow/index', 'manager:ai-flow:list', 'lucide:list-tree', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-06-27 00:00:00', '2026-07-07 10:05:46');
INSERT INTO `sys_menu` VALUES (85, 80, 2, '流程编辑器', 'AiFlowEditor', '/ai-flow/editor', 'ai-flow/editor/index', 'manager:ai-flow:query', 'lucide:pencil-ruler', NULL, '/ai-flow/list', NULL, 0, 0, 1, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-06-27 00:00:00', '2026-07-07 10:05:46');
INSERT INTO `sys_menu` VALUES (86, 80, 2, '新建流程', 'AiFlowCreate', '/ai-flow/create', 'ai-flow/create/index', 'manager:ai-flow:save', 'lucide:file-plus', NULL, '/ai-flow/list', NULL, 0, 0, 1, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-06-27 00:00:00', '2026-07-07 10:05:46');
INSERT INTO `sys_menu` VALUES (87, 80, 2, '模型档案', 'AiModelProfile', '/ai-model/profiles', 'ai-model-profile/index', 'manager:ai-model-profile:list', 'lucide:server-cog', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-06-29 00:00:00', '2026-07-07 10:07:29');
INSERT INTO `sys_menu` VALUES (88, 80, 2, 'MCP服务器', 'AiMcpServer', '/ai-model/mcp-servers', 'ai-mcp-server/index', 'manager:ai-mcp-server:list', 'lucide:plug', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-06-29 00:00:00', '2026-07-07 10:07:32');
INSERT INTO `sys_menu` VALUES (89, 80, 2, 'AI工具', 'AiTool', '/ai-model/tools', 'ai-tool/index', 'manager:ai-tool:list', 'lucide:wrench', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-06-29 00:00:00', '2026-07-07 10:07:35');
INSERT INTO `sys_menu` VALUES (90, 80, 2, '迭代链(系列)', 'AiIterationChain', '/ai-agent/iterations', 'ai-agent/iterations', 'manager:ai-iteration:list', 'lucide:repeat', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 10, 1, 1, NULL, '2026-07-08 00:00:00', '2026-07-08 00:00:00');
INSERT INTO `sys_menu` VALUES (91, 80, 2, '提示词', 'AiPrompt', '/ai-model/prompts', 'ai-prompt/index', 'manager:ai-prompt:list', 'lucide:message-square-text', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-07-10 00:00:00', '2026-07-10 00:00:00');
INSERT INTO `sys_menu` VALUES (230, 20, 2, '文章导入任务', 'BlogImportTask', '/blog/import-task', 'blog/import-task/index', 'blog:article:list', 'lucide:download', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 8, 1, 1, '文章 Markdown 批量导入任务进度与明细', '2026-06-11 00:00:00', '2026-06-11 00:00:00');
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
INSERT INTO `sys_menu` VALUES (5101, 51, 3, '查询分类', NULL, NULL, NULL, 'forge:category:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (5102, 51, 3, '新增分类', NULL, NULL, NULL, 'forge:category:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (5103, 51, 3, '修改分类', NULL, NULL, NULL, 'forge:category:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (5104, 51, 3, '删除分类', NULL, NULL, NULL, 'forge:category:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (5201, 52, 3, '查询插件', NULL, NULL, NULL, 'forge:plugin:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (5202, 52, 3, '新增插件', NULL, NULL, NULL, 'forge:plugin:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (5203, 52, 3, '修改插件', NULL, NULL, NULL, 'forge:plugin:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (5204, 52, 3, '删除插件', NULL, NULL, NULL, 'forge:plugin:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (5301, 53, 3, '查询版本', NULL, NULL, NULL, 'forge:version:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (5302, 53, 3, '新增版本', NULL, NULL, NULL, 'forge:version:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (5303, 53, 3, '修改版本', NULL, NULL, NULL, 'forge:version:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (5304, 53, 3, '删除版本', NULL, NULL, NULL, 'forge:version:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (5305, 53, 3, '审核版本', NULL, NULL, NULL, 'forge:version:review', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 5, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (5401, 54, 3, '查询评价', NULL, NULL, NULL, 'forge:review:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (5402, 54, 3, '审核/回复评价', NULL, NULL, NULL, 'forge:review:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (5403, 54, 3, '删除评价', NULL, NULL, NULL, 'forge:review:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-06-16 00:00:00', '2026-06-16 00:00:00');
INSERT INTO `sys_menu` VALUES (8101, 81, 3, '查询Agent', NULL, NULL, NULL, 'manager:ai-agent:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-07-06 00:00:00', '2026-07-06 00:00:00');
INSERT INTO `sys_menu` VALUES (8102, 81, 3, '新增Agent', NULL, NULL, NULL, 'manager:ai-agent:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-07-06 00:00:00', '2026-07-06 00:00:00');
INSERT INTO `sys_menu` VALUES (8103, 81, 3, '编辑Agent', NULL, NULL, NULL, 'manager:ai-agent:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-07-06 00:00:00', '2026-07-06 00:00:00');
INSERT INTO `sys_menu` VALUES (8104, 81, 3, '删除Agent', NULL, NULL, NULL, 'manager:ai-agent:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-07-06 00:00:00', '2026-07-06 00:00:00');
INSERT INTO `sys_menu` VALUES (8105, 81, 3, '运行Agent', NULL, NULL, NULL, 'manager:ai-agent:run', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 5, 1, 1, NULL, '2026-07-06 00:00:00', '2026-07-06 00:00:00');
INSERT INTO `sys_menu` VALUES (8106, 81, 3, '版本管理', NULL, NULL, NULL, 'manager:ai-agent:version', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 6, 1, 1, NULL, '2026-07-06 00:00:00', '2026-07-06 00:00:00');
INSERT INTO `sys_menu` VALUES (8201, 82, 3, '查询实例', NULL, NULL, NULL, 'manager:ai-agent:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-07-06 00:00:00', '2026-07-06 00:00:00');
INSERT INTO `sys_menu` VALUES (8202, 82, 3, '运行/唤醒/续跑', NULL, NULL, NULL, 'manager:ai-agent:run', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-07-06 00:00:00', '2026-07-06 00:00:00');
INSERT INTO `sys_menu` VALUES (8401, 84, 3, '查询流程', NULL, NULL, NULL, 'manager:ai-flow:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-06-27 00:00:00', '2026-07-07 11:55:33');
INSERT INTO `sys_menu` VALUES (8402, 84, 3, '保存流程', NULL, NULL, NULL, 'manager:ai-flow:save', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-06-27 00:00:00', '2026-07-07 11:55:36');
INSERT INTO `sys_menu` VALUES (8403, 84, 3, '删除流程', NULL, NULL, NULL, 'manager:ai-flow:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-06-27 00:00:00', '2026-07-07 11:55:39');
INSERT INTO `sys_menu` VALUES (8404, 84, 3, '运行流程', NULL, NULL, NULL, 'manager:ai-flow:run', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-06-27 00:00:00', '2026-07-07 11:55:44');
INSERT INTO `sys_menu` VALUES (8405, 84, 3, 'AI生成流程', NULL, NULL, NULL, 'manager:ai-copilot:stream', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 5, 1, 1, NULL, '2026-07-22 00:00:00', '2026-07-22 00:00:00');
INSERT INTO `sys_menu` VALUES (8701, 87, 3, '查询档案', NULL, NULL, NULL, 'manager:ai-model-profile:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-06-29 00:00:00', '2026-07-07 11:55:49');
INSERT INTO `sys_menu` VALUES (8702, 87, 3, '新增档案', NULL, NULL, NULL, 'manager:ai-model-profile:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-06-29 00:00:00', '2026-07-07 11:55:51');
INSERT INTO `sys_menu` VALUES (8703, 87, 3, '编辑档案', NULL, NULL, NULL, 'manager:ai-model-profile:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-06-29 00:00:00', '2026-07-07 11:55:54');
INSERT INTO `sys_menu` VALUES (8704, 87, 3, '删除档案', NULL, NULL, NULL, 'manager:ai-model-profile:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-06-29 00:00:00', '2026-07-07 11:55:59');
INSERT INTO `sys_menu` VALUES (8801, 88, 3, '查询服务器', NULL, NULL, NULL, 'manager:ai-mcp-server:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-06-29 00:00:00', '2026-07-07 11:56:04');
INSERT INTO `sys_menu` VALUES (8802, 88, 3, '新增服务器', NULL, NULL, NULL, 'manager:ai-mcp-server:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-06-29 00:00:00', '2026-07-07 11:56:08');
INSERT INTO `sys_menu` VALUES (8803, 88, 3, '编辑服务器', NULL, NULL, NULL, 'manager:ai-mcp-server:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-06-29 00:00:00', '2026-07-07 11:56:11');
INSERT INTO `sys_menu` VALUES (8804, 88, 3, '删除服务器', NULL, NULL, NULL, 'manager:ai-mcp-server:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-06-29 00:00:00', '2026-07-07 11:56:15');
INSERT INTO `sys_menu` VALUES (8901, 89, 3, '查询工具', NULL, NULL, NULL, 'manager:ai-tool:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-06-29 00:00:00', '2026-07-07 11:56:56');
INSERT INTO `sys_menu` VALUES (9001, 90, 3, '查询迭代链', NULL, NULL, NULL, 'manager:ai-iteration:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-07-08 00:00:00', '2026-07-08 00:00:00');
INSERT INTO `sys_menu` VALUES (9002, 90, 3, '新增迭代链', NULL, NULL, NULL, 'manager:ai-iteration:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-07-08 00:00:00', '2026-07-08 00:00:00');
INSERT INTO `sys_menu` VALUES (9003, 90, 3, '编辑迭代链', NULL, NULL, NULL, 'manager:ai-iteration:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-07-08 00:00:00', '2026-07-08 00:00:00');
INSERT INTO `sys_menu` VALUES (9004, 90, 3, '删除迭代链', NULL, NULL, NULL, 'manager:ai-iteration:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-07-08 00:00:00', '2026-07-08 00:00:00');
INSERT INTO `sys_menu` VALUES (9005, 90, 3, '立即推进', NULL, NULL, NULL, 'manager:ai-iteration:run', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 5, 1, 1, NULL, '2026-07-08 00:00:00', '2026-07-08 00:00:00');
INSERT INTO `sys_menu` VALUES (9006, 90, 3, '查回调投递', NULL, NULL, NULL, 'manager:ai-webhook:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 6, 1, 1, NULL, '2026-07-08 00:00:00', '2026-07-08 00:00:00');
INSERT INTO `sys_menu` VALUES (9007, 90, 3, '重发回调', NULL, NULL, NULL, 'manager:ai-webhook:retry', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 7, 1, 1, NULL, '2026-07-08 00:00:00', '2026-07-08 00:00:00');
INSERT INTO `sys_menu` VALUES (9101, 91, 3, '查询提示词', NULL, NULL, NULL, 'manager:ai-prompt:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-07-10 00:00:00', '2026-07-10 00:00:00');
INSERT INTO `sys_menu` VALUES (9102, 91, 3, '新增提示词', NULL, NULL, NULL, 'manager:ai-prompt:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-07-10 00:00:00', '2026-07-10 00:00:00');
INSERT INTO `sys_menu` VALUES (9103, 91, 3, '编辑提示词', NULL, NULL, NULL, 'manager:ai-prompt:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-07-10 00:00:00', '2026-07-10 00:00:00');
INSERT INTO `sys_menu` VALUES (9104, 91, 3, '删除提示词', NULL, NULL, NULL, 'manager:ai-prompt:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-07-10 00:00:00', '2026-07-10 00:00:00');
INSERT INTO `sys_menu` VALUES (2080137523908308993, 84, 3, 'Ai流程生成', 'managerchat', NULL, NULL, 'manager:ai-chat:stream', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 0, 1, 1, NULL, '2026-07-23 11:46:47', '2026-07-23 11:46:47');

-- ----------------------------
-- Table structure for sys_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role`  (
  `id` bigint(20) NOT NULL COMMENT '角色ID（雪花算法）',
  `role_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色编码（唯一，用于程序判断，如 admin, user）',
  `role_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色名称（展示用）',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：1正常 0禁用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
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
  `role_id` bigint(20) NOT NULL,
  `menu_id` bigint(20) NOT NULL,
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
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 50);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 51);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 52);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 53);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 54);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 55);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 80);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 81);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 82);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 83);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 84);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 85);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 86);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 87);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 88);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 89);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 90);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 91);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 230);
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
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5101);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5102);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5103);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5104);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5201);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5202);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5203);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5204);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5301);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5302);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5303);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5304);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5305);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5401);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5402);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 5403);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8101);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8102);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8103);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8104);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8105);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8106);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8201);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8202);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8401);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8402);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8403);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8404);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8405);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8701);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8702);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8703);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8704);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8801);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8802);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8803);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8804);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 8901);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 9001);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 9002);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 9003);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 9004);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 9005);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 9006);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 9007);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 9101);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 9102);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 9103);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 9104);
INSERT INTO `sys_role_menu` VALUES (1, 2080137523908308993);
INSERT INTO `sys_role_menu` VALUES (2052706759021424642, 2080137523908308993);

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`  (
  `id` bigint(20) NOT NULL COMMENT '用户ID（雪花算法）',
  `username` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '登录账号',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '登录密码（加密存储）',
  `nickname` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用户昵称',
  `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '头像地址',
  `mobile` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '手机号',
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '邮箱',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：1正常 0禁用',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` bigint(20) NULL DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint(20) NULL DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(4) NOT NULL DEFAULT 0,
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
  `user_id` bigint(20) NOT NULL,
  `role_id` bigint(20) NOT NULL,
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
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `trip_day_id` bigint(20) NOT NULL COMMENT '所属行程日ID',
  `destination_id` bigint(20) NULL DEFAULT NULL COMMENT '关联的目的地ID（可选）',
  `custom_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '自定义地点名称',
  `custom_longitude` decimal(10, 7) NULL DEFAULT NULL COMMENT '自定义经度',
  `custom_latitude` decimal(10, 7) NULL DEFAULT NULL COMMENT '自定义纬度',
  `arrival_time` datetime NULL DEFAULT NULL COMMENT '到达时间',
  `departure_time` datetime NULL DEFAULT NULL COMMENT '离开时间',
  `notes` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '游玩笔记',
  `rating` decimal(2, 1) NULL DEFAULT NULL COMMENT '个人评分',
  `photos` json NULL COMMENT '照片ID数组（blog_file_asset的id）',
  `sort_order` int(11) NOT NULL DEFAULT 0,
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
INSERT INTO `travel_checkin` VALUES (2, 2, 4, NULL, NULL, NULL, '2026-06-06 14:03:00', NULL, NULL, NULL, NULL, 0, '2026-06-11 17:33:43', '2026-06-11 17:33:43');

-- ----------------------------
-- Table structure for travel_destination
-- ----------------------------
DROP TABLE IF EXISTS `travel_destination`;
CREATE TABLE `travel_destination`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '目的地ID',
  `parent_id` bigint(20) NULL DEFAULT NULL COMMENT '父级目的地ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '名称',
  `slug` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'URL标识',
  `type` tinyint(4) NOT NULL DEFAULT 0 COMMENT '类型：0国家 1省份/州 2城市 3景点/POI',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '描述',
  `cover_file_id` bigint(20) NULL DEFAULT NULL COMMENT '封面图（关联blog_file_asset）',
  `longitude` decimal(10, 7) NULL DEFAULT NULL COMMENT '经度',
  `latitude` decimal(10, 7) NULL DEFAULT NULL COMMENT '纬度',
  `address` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '详细地址',
  `visit_count` int(11) NOT NULL DEFAULT 0 COMMENT '访问/打卡次数',
  `rating` decimal(2, 1) NULL DEFAULT NULL COMMENT '评分（0.0-5.0）',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1启用',
  `sort_order` int(11) NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_slug`(`slug` ASC) USING BTREE,
  INDEX `idx_parent_id`(`parent_id` ASC) USING BTREE,
  INDEX `idx_type`(`type` ASC) USING BTREE,
  INDEX `idx_coord`(`longitude` ASC, `latitude` ASC) USING BTREE,
  CONSTRAINT `fk_destination_parent` FOREIGN KEY (`parent_id`) REFERENCES `travel_destination` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '旅游目的地表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of travel_destination
-- ----------------------------
INSERT INTO `travel_destination` VALUES (2, NULL, '中国', 'dest-mq9aspri', 0, NULL, NULL, NULL, NULL, NULL, 0, NULL, 1, 0, '2026-06-11 17:31:34', '2026-06-11 17:31:34');
INSERT INTO `travel_destination` VALUES (3, 2, '内蒙古自治区', 'dest-mq9at3fh', 1, NULL, NULL, NULL, NULL, NULL, 0, NULL, 1, 0, '2026-06-11 17:31:57', '2026-06-11 17:31:57');
INSERT INTO `travel_destination` VALUES (4, 3, '乌兰察布', 'dest-mq9atpas', 2, NULL, NULL, NULL, NULL, NULL, 0, NULL, 1, 0, '2026-06-11 17:32:44', '2026-06-11 17:32:44');

-- ----------------------------
-- Table structure for travel_trip
-- ----------------------------
DROP TABLE IF EXISTS `travel_trip`;
CREATE TABLE `travel_trip`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '游记ID',
  `user_id` bigint(20) NOT NULL COMMENT '作者用户ID（关联sys_user）',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '标题',
  `slug` varchar(220) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'URL标识',
  `summary` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '摘要',
  `cover_file_id` bigint(20) NULL DEFAULT NULL COMMENT '封面图（关联blog_file_asset）',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'draft' COMMENT 'draft/published/archived',
  `visibility` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'public' COMMENT 'public/private',
  `start_date` date NULL DEFAULT NULL COMMENT '开始日期',
  `end_date` date NULL DEFAULT NULL COMMENT '结束日期',
  `days_count` int(11) NULL DEFAULT NULL COMMENT '总天数',
  `persons` int(11) NULL DEFAULT NULL COMMENT '人数',
  `cost_total` decimal(10, 2) NULL DEFAULT NULL COMMENT '总花费',
  `cost_currency` char(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'CNY' COMMENT '货币',
  `view_count` int(11) NOT NULL DEFAULT 0,
  `like_count` int(11) NOT NULL DEFAULT 0,
  `published_at` datetime NULL DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_slug`(`slug` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_status_visibility_published`(`status` ASC, `visibility` ASC, `published_at` ASC) USING BTREE,
  INDEX `idx_dates`(`start_date` ASC, `end_date` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '旅行游记主表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of travel_trip
-- ----------------------------
INSERT INTO `travel_trip` VALUES (1, 2052290101098295297, '乌兰察布2天1夜', 'trip-mq9a8cy2', '乌兰察布位于乌兰察布中部，地处华北地区与蒙古高原过渡地带，是连接京津冀与内蒙古中西部的重要交通枢纽。全市总面积约5.45万平方公里，下辖多个旗县区，拥有丰富的自然资源和独特的草原风光。\n\n乌兰察布历史悠久，文化底蕴深厚，是北方游牧文化与农耕文化交汇的重要区域。这里气候凉爽，夏季平均气温适宜，被誉为“中国草原避暑之都”，每年吸引众多游客前来观光度假。境内拥有辉腾锡勒草原、乌兰哈达火山地质公园等知名景区，其中火山群保存完整，被誉为“天然火山博物馆”，近年来成为热门旅游打卡地。\n\n在经济发展方面，乌兰察布依托优越的区位条件和丰富的风光资源，大力发展现代农牧业、新能源、大数据和现代物流产业，是国家重要的绿色农畜产品生产基地和新能源基地。近年来，随着交通基础设施不断完善和数字经济快速发展，乌兰察布正逐步建设成为内蒙古面向京津冀协同发展的重要节点城市。\n\n如今的乌兰察布，正以独特的自然风光、深厚的历史文化和蓬勃的发展活力，向外界展示着草原城市的独特魅力。', 56, 'published', 'public', '2026-06-06', '2026-06-07', 2, 4, NULL, 'CNY', 11, 0, '2026-06-11 17:21:01', '2026-06-11 17:17:59', '2026-06-30 10:16:14');
INSERT INTO `travel_trip` VALUES (2, 2052290101098295297, '威海3天2夜', 'trip-mq9b5ckb', '威海，位于山东半岛最东端，是一座三面环海的滨海城市，东、南、北皆临黄海，与朝鲜半岛隔海相望。这里海岸线绵长曲折，海水清澈见底，空气质量常年优良，是典型的海洋性气候城市，夏季凉爽宜人，冬季温和不严寒，因此也被认为是中国最适合居住与休闲度假的城市之一。\n\n来到威海，最不能错过的就是海与历史的交织。刘公岛承载着中国近代海军的记忆，这里曾是北洋水师的基地，也是甲午海战的重要见证地，岛上既有历史遗迹，也有自然风光，登岛游览仿佛穿越时空。向东而行，成山头被誉为“中国海岸线最早迎接日出的地方”，清晨站在海崖之上，看太阳从海平面缓缓升起，海天一线，极具震撼力。\n\n威海的城市节奏舒缓，适合慢慢体验。国际海水浴场沙质细腻，海浪温和，适合散步、游泳和发呆；环海路骑行则能一路欣赏海岸线风光，感受海风拂面的惬意。市区干净整洁，建筑与自然融合度高，随处可见绿树与海景相映成趣。\n\n如果说旅行的意义是放松与治愈，那么威海就是一座天然的“海边疗愈城市”。在这里，可以放慢脚步，看海、吹风、等日落，也可以在清晨追一场中国大陆最早的日出。威海不喧嚣，却足够让人记住它的安静与辽阔。', 57, 'published', 'public', '2025-04-04', '2025-04-06', 3, 2, NULL, 'CNY', 3, 0, '2026-06-11 17:41:35', '2026-06-11 17:41:31', '2026-07-01 16:12:06');

-- ----------------------------
-- Table structure for travel_trip_blog_post
-- ----------------------------
DROP TABLE IF EXISTS `travel_trip_blog_post`;
CREATE TABLE `travel_trip_blog_post`  (
  `trip_id` bigint(20) NOT NULL,
  `post_id` bigint(20) NOT NULL,
  `post_type` tinyint(4) NOT NULL DEFAULT 0 COMMENT '关联类型：0主要文章 1相关推荐',
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
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `trip_id` bigint(20) NOT NULL COMMENT '所属游记ID',
  `day_number` int(11) NOT NULL COMMENT '第几天',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '当日标题',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '当日描述',
  `accommodation` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '住宿地点',
  `meal_cost` decimal(10, 2) NULL DEFAULT NULL COMMENT '餐饮花费',
  `transport_cost` decimal(10, 2) NULL DEFAULT NULL COMMENT '交通花费',
  `other_cost` decimal(10, 2) NULL DEFAULT NULL COMMENT '其他花费',
  `sort_order` int(11) NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_trip_day`(`trip_id` ASC, `day_number` ASC) USING BTREE,
  INDEX `idx_trip_id`(`trip_id` ASC) USING BTREE,
  CONSTRAINT `fk_day_trip` FOREIGN KEY (`trip_id`) REFERENCES `travel_trip` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '行程日表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of travel_trip_day
-- ----------------------------
INSERT INTO `travel_trip_day` VALUES (2, 1, 1, '黄花沟之行', '下午抵达乌兰察布后，我们前往著名的黄花沟景区游玩。进入景区时购买了套票，费用为300元/人，包含景区内的大部分游览项目和交通服务，无需再额外购票。黄花沟位于辉腾锡勒草原腹地，以广袤的草原风光、独特的沟谷地貌和丰富的游玩项目而闻名。\n\n进入景区后，映入眼帘的是一望无际的绿色草原，微风吹过，草浪起伏，让人瞬间感受到草原的辽阔与自由。沿途可以体验观光小火车、索道以及景区摆渡车等项目，在轻松游览的同时欣赏草原与沟谷交织形成的壮丽景色。黄花沟不仅拥有优美的自然风光，还保留着浓厚的蒙古族文化特色，随处可见具有民族风情的建筑和装饰。\n\n傍晚时分，夕阳洒在草原之上，为整个景区披上一层金色的光辉。远处的风车缓缓转动，与蓝天白云、青青草原共同构成了一幅令人难忘的画卷。作为乌兰察布之行的重要一站，黄花沟不仅让我们领略到了内蒙古草原的独特魅力，也为这次旅程留下了美好的回忆。', '如家', NULL, NULL, NULL, 0, '2026-06-11 17:30:41', '2026-06-11 17:30:41');
INSERT INTO `travel_trip_day` VALUES (3, 1, 2, '乌兰哈达火山', '乌兰哈达火山地质公园位于乌兰察布察哈尔右翼后旗境内，距离市区约100公里，是中国保存较为完整的第四纪火山群之一。景区分布着30多座火山，其中多座火山口保存完好，因此被誉为“天然火山博物馆”。', NULL, NULL, NULL, NULL, 1, '2026-06-11 17:35:48', '2026-06-11 17:35:48');

-- ----------------------------
-- Table structure for worker_node
-- ----------------------------
DROP TABLE IF EXISTS `worker_node`;
CREATE TABLE `worker_node`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '自增主键，作为 workerId',
  `host_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'host：ACTUAL=IP，CONTAINER=hostname',
  `port` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'port：ACTUAL=时间戳-随机数，CONTAINER=容器端口',
  `type` int(11) NOT NULL COMMENT '节点类型：1=CONTAINER 2=ACTUAL',
  `launch_date` date NOT NULL COMMENT '启动日期',
  `modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  `created` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'UID Generator workerId 分配表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of worker_node
-- ----------------------------

SET FOREIGN_KEY_CHECKS = 1;
