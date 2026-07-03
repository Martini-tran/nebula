/**
 * AGENT 节点配置类型与读写归一化（node.data.nodeConfig.agent 的结构）。
 *
 * AGENT 节点用于「调用另一个已设计好的 Agent（复用 Workflow）」：选一个已保存的
 * Agent（流程），并传入调用参数（JSON）。AgentConfigDialog 的草稿由本模块类型驱动；
 * 读入用 normalizeAgentConfig 兜底缺省，写回前用 serializeAgentConfig 归一化（深拷贝）。
 *
 * 仿 ./tool-config.ts。本项目只做前端配置落库，不关心后端执行字段对齐
 * （后端整体待重构，选择器数据源暂为前端占位）。
 */

/** 1. Agent：被调用的 Agent 引用（已保存的流程） */
export interface AgentRefConfig {
  /** 被调 Agent 的流程编码（flowCode） */
  flowCode?: string;
  /** 被调 Agent 的展示名（回显用，可选） */
  name?: string;
}

/** 2. Params：调用参数（JSON 字符串，传给被调 Agent 的入参） */
export interface AgentParamsConfig {
  /** 调用参数 JSON 文本（可选） */
  json: string;
}

/** AGENT 节点完整配置（落库到 nodeConfig.agent） */
export interface AgentConfig {
  ref: AgentRefConfig;
  params: AgentParamsConfig;
}

/** 调用参数 JSON 的占位示例 */
export const AGENT_PARAMS_PLACEHOLDER = `{
  "question": "{{inputs.question}}"
}`;

/** 全新 AGENT 配置默认值 */
export function defaultAgentConfig(): AgentConfig {
  return {
    ref: {},
    params: { json: '' },
  };
}

/** 取对象子键，非对象返回空对象（读入兜底用） */
function obj(v: unknown): Record<string, any> {
  return v && typeof v === 'object' && !Array.isArray(v)
    ? (v as Record<string, any>)
    : {};
}

/**
 * 归一化读入：把 nodeConfig.agent（可能缺失 / 部分字段）补全为完整 AgentConfig。
 *
 * @param raw node.data.nodeConfig.agent
 */
export function normalizeAgentConfig(raw: unknown): AgentConfig {
  const src = obj(raw);
  const ref_ = obj(src.ref);
  const params = obj(src.params);
  return {
    ref: {
      flowCode: ref_.flowCode ?? '',
      name: ref_.name ?? '',
    },
    params: {
      json: typeof params.json === 'string' ? params.json : '',
    },
  };
}

/** 写回前深拷贝草稿（弹窗草稿是 reactive，落库需普通对象快照） */
export function serializeAgentConfig(cfg: AgentConfig): AgentConfig {
  return JSON.parse(JSON.stringify(cfg));
}
