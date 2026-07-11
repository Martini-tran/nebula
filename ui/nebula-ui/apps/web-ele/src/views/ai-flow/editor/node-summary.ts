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
 * 输出摘要：输出键 · 模式。输出键缺省用节点编码。
 * LLM 走 nodeConfig.llm.output.type；TOOL 走 nodeConfig.tool.output.key。
 */
export function outputSummary(data: AiFlowApi.FlowNodeRaw): string {
  const key =
    (data.outputKey ?? '').trim() || data.nodeCode || '节点编码';
  const llmType = obj(obj(data.nodeConfig?.llm).output).type;
  const toolKey = obj(obj(data.nodeConfig?.tool).output).key;
  const mode = llmType || (toolKey ? '' : data.outputMode) || 'TEXT';
  return mode ? `${key} · ${mode}` : key;
}
