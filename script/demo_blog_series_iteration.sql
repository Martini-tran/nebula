-- =====================================================================================
-- 跨实例迭代层 · 博客系列 Demo（阶段 I 可跑通脚本）
--
-- 场景：开一个"系列"（如"30天Java进阶"）→ 每天定时生成一篇 → 每篇基于上一轮产出去写、大纲边写边规划。
-- 对应设计：docs/跨实例迭代层设计.md
--
-- 本脚本三部分：
--   ① ai-iteration 菜单权限（接现有 80 号"智能体"菜单）
--   ② 一个最小可跑的"博客系列 Agent"（STATE_MACHINE 引擎：START→PROMPT→END）
--   ③ 一条迭代链（= 一个系列实例），演示 carryOver 如何把上一轮产物喂进下一轮
--
-- 执行前提：ai_agent_iteration / ai_agent / ai_flow / ai_flow_node / ai_flow_edge 表已建（script/nebula.sql）。
-- 执行后：manager 服务的 IterationScheduler 每分钟扫描，link ACTIVE 且 next_run_at<=now 即跑首轮。
-- =====================================================================================


-- =====================================================================================
-- ① 菜单权限（父菜单 80 = 智能体；新增"迭代链"子菜单 90 + 权限点 9001-9005）
--    注：80 下 81-89 已被智能体/流程/模型系占用，二级菜单取 90、按钮取 9001-9005（均为空号，防撞）。
-- =====================================================================================
INSERT INTO `sys_menu` VALUES (90, 80, 2, '迭代链(系列)', 'AiIterationChain', '/ai-agent/iterations', 'ai-agent/iterations', 'manager:ai-iteration:list', 'lucide:repeat', NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 10, 1, 1, NULL, '2026-07-08 00:00:00', '2026-07-08 00:00:00');

INSERT INTO `sys_menu` VALUES (9001, 90, 3, '查询迭代链', NULL, NULL, NULL, 'manager:ai-iteration:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 1, 1, 1, NULL, '2026-07-08 00:00:00', '2026-07-08 00:00:00');
INSERT INTO `sys_menu` VALUES (9002, 90, 3, '新增迭代链', NULL, NULL, NULL, 'manager:ai-iteration:add', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 2, 1, 1, NULL, '2026-07-08 00:00:00', '2026-07-08 00:00:00');
INSERT INTO `sys_menu` VALUES (9003, 90, 3, '编辑迭代链', NULL, NULL, NULL, 'manager:ai-iteration:edit', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 3, 1, 1, NULL, '2026-07-08 00:00:00', '2026-07-08 00:00:00');
INSERT INTO `sys_menu` VALUES (9004, 90, 3, '删除迭代链', NULL, NULL, NULL, 'manager:ai-iteration:delete', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 4, 1, 1, NULL, '2026-07-08 00:00:00', '2026-07-08 00:00:00');
INSERT INTO `sys_menu` VALUES (9005, 90, 3, '立即推进', NULL, NULL, NULL, 'manager:ai-iteration:run', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 5, 1, 1, NULL, '2026-07-08 00:00:00', '2026-07-08 00:00:00');

-- 回调投递管理权限点（挂迭代链菜单 90 下：查投递记录 / 手动重发，对应 WebhookAdminController）
INSERT INTO `sys_menu` VALUES (9006, 90, 3, '查回调投递', NULL, NULL, NULL, 'manager:ai-webhook:query', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 6, 1, 1, NULL, '2026-07-08 00:00:00', '2026-07-08 00:00:00');
INSERT INTO `sys_menu` VALUES (9007, 90, 3, '重发回调', NULL, NULL, NULL, 'manager:ai-webhook:retry', NULL, NULL, NULL, NULL, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL, 7, 1, 1, NULL, '2026-07-08 00:00:00', '2026-07-08 00:00:00');


-- =====================================================================================
-- ② 博客系列 Agent（STATE_MACHINE 引擎，最小三节点）
--    START(node_1) ──▶ PROMPT(node_2, 写一篇+更新大纲) ──▶ END(node_3)
--
--    ★关键：PROMPT 节点 output_mode=JSON、output_key='seriesState'，让 LLM 产出一个 JSON 对象：
--      {
--        "articleTitle": "第N篇标题",
--        "articleBody":  "正文markdown",
--        "outline":      "更新后的整体大纲（边写边规划）",
--        "accumulated":  "累积写作状态：已用例子/已讲知识点/风格基调",
--        "outlineDone":  "false"   // 全系列写完时置 "true" → 触发 until 出链
--      }
--    这些键就是 carryOver 的数据源（见 ③）。
-- =====================================================================================

-- Flow 头：engine_type=STATE_MACHINE（走状态机内核，可成环/回跳；本 demo 线性即可）
INSERT INTO `ai_flow`
  (`flow_code`, `name`, `description`, `version`, `default_profile_code`, `status`, `create_time`, `update_time`, `engine_type`, `max_transitions`, `max_agent_depth`)
VALUES
  ('blog_series', '博客系列生成', '按系列大纲逐篇生成博客，边写边规划', 1, 'DS-V3-001', 1, NOW(), NOW(), 'STATE_MACHINE', 100, 8);

-- START 节点（ENTRY）：声明入参 —— 系列主题 topic、上一轮带来的 outline/accumulated/history
INSERT INTO `ai_flow_node`
  (`flow_code`, `node_code`, `name`, `node_type`, `output_mode`, `node_config`, `sort_no`, `state_type`)
VALUES
  ('blog_series', 'node_1', '开始', 'START', 'TEXT',
   '{"__x6":{"x":120,"y":200},"inputs":{"topic":"系列主题","outline":"上一轮大纲","accumulated":"累积状态","history":"已写文章索引","prevSeq":"上一篇序号"}}',
   0, 'ENTRY');

-- PROMPT 节点（NORMAL）：写一篇 + 更新大纲，产出 JSON 到 seriesState
INSERT INTO `ai_flow_node`
  (`flow_code`, `node_code`, `name`, `node_type`, `profile_code`, `output_key`, `output_mode`, `node_config`, `remember_trace`, `sort_no`, `state_type`)
VALUES
  ('blog_series', 'node_2', '写一篇并更新大纲', 'PROMPT', 'DS-V3-001', 'seriesState', 'JSON',
   '{"llm":{"prompt":{"systemPrompt":"你是一个技术博客连载作者。围绕系列主题《{{topic}}》持续创作。\\n\\n已有整体大纲：{{outline}}\\n累积写作状态（已用例子/已讲知识点/风格）：{{accumulated}}\\n已写文章索引：{{history}}\\n这是第 {{prevSeq}} 篇之后的下一篇。\\n\\n请：\\n1. 基于大纲与累积状态，决定并撰写下一篇文章（承接前文、不重复已讲内容）。\\n2. 如有需要，微调整体大纲（边写边规划）。\\n3. 更新累积写作状态。\\n4. 判断系列是否已写完。\\n\\n只输出 JSON，无其它文字：","userPromptTemplate":"请生成下一篇。"},"output":{"type":"JSON","jsonSchema":"{\\"type\\":\\"object\\",\\"properties\\":{\\"articleTitle\\":{\\"type\\":\\"string\\"},\\"articleBody\\":{\\"type\\":\\"string\\"},\\"outline\\":{\\"type\\":\\"string\\"},\\"accumulated\\":{\\"type\\":\\"string\\"},\\"outlineDone\\":{\\"type\\":\\"string\\",\\"description\\":\\"全系列写完置 true，否则 false\\"}},\\"required\\":[\\"articleTitle\\",\\"articleBody\\",\\"outline\\",\\"accumulated\\",\\"outlineDone\\"]}"},"parameters":{"mode":"basic","basic":{"temperature":0.8,"maxTokens":4096}}},"__x6":{"x":420,"y":200}}',
   1, 1, 'NORMAL');

-- END 节点（TERMINAL）：到达即实例 SUCCESS，引擎把 seriesState 刷进 context_snapshot（= 本轮产物）
INSERT INTO `ai_flow_node`
  (`flow_code`, `node_code`, `name`, `node_type`, `output_mode`, `node_config`, `sort_no`, `state_type`)
VALUES
  ('blog_series', 'node_3', '结束', 'END', 'TEXT', '{"__x6":{"x":760,"y":200}}', 2, 'TERMINAL');

-- 边：START → PROMPT → END（无 guard，线性推进）
INSERT INTO `ai_flow_edge` (`flow_code`, `from_node`, `to_node`, `condition_expr`, `sort_no`) VALUES
  ('blog_series', 'node_1', 'node_2', NULL, 0),
  ('blog_series', 'node_2', 'node_3', NULL, 1);

-- Agent 定义：引用 blog_series 流程。input_schema 里 topic 必填；不启用长期记忆（系列递进走链，不走 ai_memory）
INSERT INTO `ai_agent`
  (`agent_code`, `name`, `description`, `flow_code`, `flow_version`, `input_schema`, `output_schema`, `memory_config`, `default_profile_code`, `version`, `status`, `create_time`, `update_time`)
VALUES
  ('blog_series_writer', '博客系列作者', '每轮写一篇并更新大纲，供迭代链逐日推进',
   'blog_series', 1,
   '{"type":"object","properties":{"topic":{"type":"string"}},"required":["topic"]}',
   '{"type":"object","properties":{"seriesState":{"type":"object"}}}',
   '{"enabled":false}',
   'DS-V3-001', 1, 1, NOW(), NOW());


-- =====================================================================================
-- ③ 一条迭代链（= 一个系列实例）
--
--    carry_over：把上一轮产物（PROMPT 产出的 seriesState.* 会被展平进 context）映射为下一轮 START 的入参。
--      注：引擎把 output_key='seriesState' 的 JSON 对象按其子键写进 context（outline/accumulated/…），
--      故 carry_over 的 key 用子键名。若你的引擎是整体写 seriesState，则改成 {"seriesState":"prev"} 由提示词自解析。
--
--    until_expr：对本轮产物 context 求值。outlineDone 为字符串 "true" 时出链（SpEL 走 OrchestrationContext）。
--
--    seed_inputs：首轮（seq=0，无上一轮）用它启动 —— 只给系列主题，大纲从空开始由 Agent 涌现。
-- =====================================================================================
INSERT INTO `ai_agent_iteration`
  (`chain_id`, `name`, `agent_code`, `cron`, `next_run_at`, `seq`, `max_iterations`, `until_expr`, `carry_over`, `seed_inputs`, `webhook_url`, `status`, `consecutive_fails`, `lock_version`, `create_time`, `update_time`)
VALUES
  ('blog_series_writer-chain-demo01',
   '30天Java进阶',
   'blog_series_writer',
   '0 0 9 * * ?',                 -- 每天 9:00 推进一篇
   NOW(),                          -- 立即到点：脚本执行后下次扫描即跑首轮
   0,
   30,                             -- 最多 30 篇封顶
   'getString(''outlineDone'') == ''true''',   -- 大纲写完则出链（SpEL：以 context 为根）
   '{"outline":"outline","accumulated":"accumulated","articleTitle":"history","prevSeq":"prevSeq"}',
   '{"topic":"30天Java进阶"}',
   -- ★每轮 advance 成功后回调此 URL，blog 落库（建系列+文章草稿+挂目录）。改成你 blog 服务实际地址/网关路由
   'http://127.0.0.1:8080/blog/admin/blog/webhook/series-append',
   'ACTIVE', 0, 0, NOW(), NOW());


-- =====================================================================================
-- 等价 REST 剧本（若走接口而非直接 INSERT ③）
-- =====================================================================================
-- 建链（返回 chainId）：
--   POST /admin/ai-iteration/chains
--   {
--     "name": "30天Java进阶",
--     "agentCode": "blog_series_writer",
--     "cron": "0 0 9 * * ?",
--     "maxIterations": 30,
--     "untilExpr": "getString('outlineDone') == 'true'",
--     "carryOver": {"outline":"outline","accumulated":"accumulated","articleTitle":"history","prevSeq":"prevSeq"},
--     "seedInputs": {"topic":"30天Java进阶"}
--   }
--
-- 立即跑一轮（不等定时，next_run_at 置当前，下次扫描≤60s 即跑）：
--   POST /admin/ai-iteration/chains/{chainId}/run-now
--
-- 看链详情（seq=写到第几篇、lastInstanceId=最后一篇实例、时间线）：
--   GET  /admin/ai-iteration/chains/{chainId}
--
-- 暂停 / 恢复：
--   POST /admin/ai-iteration/chains/{chainId}/pause
--   POST /admin/ai-iteration/chains/{chainId}/resume


-- =====================================================================================
-- 逐轮数据流（这就是"基于上次记忆生成"的真相 —— 走链上一轮实例产物，不走 ai_memory）
-- =====================================================================================
--   第1轮(seq0→1)  inputs = seedInputs{topic}                     ── Agent ─▶ 实例A.seriesState{outline1, article1, outlineDone=false}
--                  advance: last_instance_id=A, seq=1, next_run_at=次日9点
--
--   第2轮(seq1→2)  inputs = seed{topic} + carryOver(读 A 产物)     ── Agent ─▶ 实例B.seriesState{outline2(调整过), article2, ...}
--                    outline=outline1, accumulated=..., history=article1标题, prevSeq=1
--                  advance: last_instance_id=B, seq=2, ...
--
--   ...                                                            （每天一篇，大纲边写边调，前文不重复）
--
--   第N轮  Agent 判定写完 → seriesState.outlineDone="true"
--         until 命中 → advance status=COMPLETED，链停，定时不再扫它
-- =====================================================================================
