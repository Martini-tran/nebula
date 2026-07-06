/**
 * AGENT 节点配置类型与读写归一化。
 *
 * AGENT 节点「调用另一个已设计好的 Agent（复用 Workflow）」，递归执行子 Agent。契约**平铺在 node.data.nodeConfig**，
 * 与后端 AgentNodeExecutor 严格一致：
 *   - refAgentCode：被调子 Agent 的 agentCode（Agent 定义，含自己的记忆/IO 契约）。
 *   - inputMapping：子入参键 ← 父上下文键（key=子入参键，value=父 ctx 键）。
 *   - outputMapping：父上下文键 ← 子产物键（key=父 ctx 键，value=子产物键）。
 *
 * AgentConfigDialog 的草稿由本模块类型驱动；读入用 normalizeAgentConfig 兜底缺省（含旧结构兼容），
 * 写回前用 serializeAgentConfig 归一化（深拷贝）。
 */

/** AGENT 节点完整配置（平铺落库到 nodeConfig：refAgentCode/inputMapping/outputMapping） */
export interface AgentConfig {
  /** 被调子 Agent 的 agentCode */
  refAgentCode: string;
  /** Input Mapping：子入参键 → 父上下文键（key=子入参键，value=父 ctx 键） */
  inputMapping: Record<string, string>;
  /** Output Mapping：父上下文键 → 子产物键（key=父 ctx 键，value=子产物键） */
  outputMapping: Record<string, string>;
  /** 被调 Agent 展示名（仅前端回显用，不参与后端语义） */
  refName?: string;
}

/** 全新 AGENT 配置默认值 */
export function defaultAgentConfig(): AgentConfig {
  return {
    refAgentCode: '',
    inputMapping: {},
    outputMapping: {},
    refName: '',
  };
}

/** 取对象子键，非对象返回空对象（读入兜底用） */
function obj(v: unknown): Record<string, any> {
  return v && typeof v === 'object' && !Array.isArray(v)
    ? (v as Record<string, any>)
    : {};
}

/** 取字符串映射，非法项过滤（key/value 均需字符串） */
function strMap(v: unknown): Record<string, string> {
  const src = obj(v);
  const out: Record<string, string> = {};
  for (const [k, val] of Object.entries(src)) {
    if (k && typeof val === 'string') out[k] = val;
  }
  return out;
}

/**
 * 归一化读入：把 node.data.nodeConfig（可能缺失 / 旧结构）补全为完整 AgentConfig。
 *
 * 兼容策略：优先读新平铺键（refAgentCode/inputMapping/outputMapping）；
 * 旧结构 nodeConfig.agent.ref.flowCode 读到 refAgentCode 兜底（flowCode 当 agentCode，允许用户重选），
 * 旧 params.json 无法结构化转换，丢弃（用户需在弹窗补配 Input/Output Mapping）。
 *
 * @param raw node.data.nodeConfig
 */
export function normalizeAgentConfig(raw: unknown): AgentConfig {
  const cfg = obj(raw);
  const legacy = obj(cfg.agent);
  const legacyRef = obj(legacy.ref);
  const refAgentCode =
    typeof cfg.refAgentCode === 'string' && cfg.refAgentCode
      ? cfg.refAgentCode
      : (legacyRef.flowCode ?? '');
  const refName =
    typeof cfg.__agentRefName === 'string'
      ? cfg.__agentRefName
      : (legacyRef.name ?? '');
  return {
    refAgentCode,
    inputMapping: strMap(cfg.inputMapping),
    outputMapping: strMap(cfg.outputMapping),
    refName,
  };
}

/** 写回前深拷贝草稿（弹窗草稿是 reactive，落库需普通对象快照） */
export function serializeAgentConfig(cfg: AgentConfig): AgentConfig {
  return JSON.parse(JSON.stringify(cfg));
}
