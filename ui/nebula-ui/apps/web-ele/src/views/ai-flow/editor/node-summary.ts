/**
 * 节点卡片「带值摘要」计算：从节点配置提炼一行人可读的关键信息，
 * 让画布上不点开也能看懂这一步在做什么（模型名、工具名、输出键等）。
 *
 * 数据源是前端落库结构 nodeConfig.<type>（llm/tool/…），与卡片指示点同源。
 * 由壳组件 FlowNodeCard 统一计算后传给各专属卡片展示，避免每张卡片各算一套。
 */
import type { AiFlowApi } from '#/api';

/** 取对象子键，非对象返回空对象 */
function obj(v: unknown): Record<string, any> {
  return v && typeof v === 'object' && !Array.isArray(v)
    ? (v as Record<string, any>)
    : {};
}

/**
 * LLM 节点摘要：模型档案编码（节点只引用档案）。
 * 读 nodeConfig.llm.model.profileCode（配置弹窗落库），兜底顶层扁平字段。
 */
export function llmSummary(data: AiFlowApi.FlowNodeRaw): string {
  const model = obj(obj(data.nodeConfig?.llm).model);
  const profile = model.profileCode || data.profileCode;
  return profile ? String(profile) : '未选模型';
}

/**
 * TOOL 节点摘要：引用的工具编码（含版本）。
 * 读 nodeConfig.tool.tool.toolCode（新结构），兜底旧 nodeConfig.toolCode。
 */
export function toolSummary(data: AiFlowApi.FlowNodeRaw): string {
  const cfg = obj(data.nodeConfig);
  const sel = obj(obj(cfg.tool).tool);
  const code = sel.toolCode || cfg.toolCode;
  if (!code) return '未选工具';
  const version = sel.version ? `@${sel.version}` : '';
  return `${code}${version}`;
}

/**
 * AGENT_REACT 节点摘要：模型档案 · 工具×N。
 *
 * 与 TOOL 摘要（展示某一个工具编码）的语义差别：本节点给模型的是一批**可自选**的
 * 工具白名单，具体调哪个由模型在 ReAct loop 里逐轮决定，故只展示数量而非具体某个。
 * 读 nodeConfig.agentReact.*，兜底后端扁平契约（nodeConfig.toolCodes / 顶层 profileCode）。
 */
export function agentReactSummary(data: AiFlowApi.FlowNodeRaw): string {
  const cfg = obj(data.nodeConfig);
  const react = obj(cfg.agentReact);
  const profile = obj(react.model).profileCode || data.profileCode;
  // 嵌套结构优先，兜底后端扁平契约 nodeConfig.toolCodes
  const nested = obj(react.tools).toolCodes;
  const flat = cfg.toolCodes;
  let codes: unknown[] = [];
  if (Array.isArray(nested)) {
    codes = nested;
  } else if (Array.isArray(flat)) {
    codes = flat;
  }
  const model = profile ? String(profile) : '未选模型';
  return codes.length > 0
    ? `${model} · 工具×${codes.length}`
    : `${model} · 无工具`;
}

/**
 * 输出摘要：输出键 · 模式。输出键缺省用节点编码。
 * LLM 走 nodeConfig.llm.output.type；TOOL 走 nodeConfig.tool.output.key；
 * AGENT_REACT 的输出已同步落顶层 outputMode/outputKey，走通用兜底即可。
 */
export function outputSummary(data: AiFlowApi.FlowNodeRaw): string {
  const key =
    (data.outputKey ?? '').trim() || data.nodeCode || '节点编码';
  const llmType = obj(obj(data.nodeConfig?.llm).output).type;
  const toolKey = obj(obj(data.nodeConfig?.tool).output).key;
  const mode = llmType || (toolKey ? '' : data.outputMode) || 'TEXT';
  return mode ? `${key} · ${mode}` : key;
}
