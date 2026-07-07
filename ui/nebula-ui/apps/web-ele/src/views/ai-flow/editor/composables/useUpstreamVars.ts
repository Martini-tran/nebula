/**
 * 收集某节点的「上游可用变量」——供配置里引用上游产物时下拉选择，替代手打 {{outputKey}}。
 *
 * 变量来源两类：
 * 1. 上游祖先节点（X6 graph.getPredecessors 全量上溯）的产物键：
 *    优先 outputKey，缺省用 nodeCode；LLM/PROMPT 的输出即写到该键。
 * 2. START 节点的入参（nodeConfig.inputs，经 normalizeStartInputs），运行时注入上下文。
 *
 * 只依赖图结构（连线），不依赖后端；节点未连线则收集不到其变量——符合直觉。
 */
import type { Graph, Node } from '@antv/x6';

import type { AiFlowApi } from '#/api';

import { normalizeStartInputs } from '../start-input';

/** 一条可引用的上游变量 */
export interface UpstreamVar {
  /** 变量键（引用时写进 {{key}}） */
  key: string;
  /** 下拉展示标签：键（来源节点名） */
  label: string;
  /** 来源节点编码 */
  fromNode: string;
}

/** 节点产物键：优先 outputKey，缺省节点编码 */
function outputKeyOf(data: AiFlowApi.FlowNodeRaw, nodeId: string): string {
  const k = (data.outputKey ?? '').trim();
  return k || nodeId;
}

/**
 * 收集 node 的全部上游可用变量。
 *
 * @param graph 当前 X6 图
 * @param node  目标节点（收集它的上游）
 * @returns 去重后的变量列表（按 key）
 */
export function collectUpstreamVars(
  graph: Graph | undefined,
  node: Node | undefined,
): UpstreamVar[] {
  if (!graph || !node) return [];

  // getPredecessors 上溯全部祖先（深度不限）；deep 默认 true
  const ancestors = graph.getPredecessors(node) as Node[];
  const seen = new Set<string>();
  const vars: UpstreamVar[] = [];

  const push = (key: string, label: string, fromNode: string) => {
    const k = key.trim();
    if (!k || seen.has(k)) return;
    seen.add(k);
    vars.push({ key: k, label, fromNode });
  };

  for (const anc of ancestors) {
    if (!anc?.isNode?.()) continue;
    const data = (anc.getData<AiFlowApi.FlowNodeRaw>() ??
      {}) as AiFlowApi.FlowNodeRaw;
    const nodeName = data.name || anc.id;

    if (data.nodeType === 'START') {
      // START：把每个入参 key 作为可用变量
      normalizeStartInputs(data.nodeConfig?.inputs).forEach((p) => {
        if (p.key) push(p.key, `${p.key}（${nodeName}·入参）`, anc.id);
      });
      continue;
    }

    // 其余节点：产物键（outputKey/nodeCode）
    const key = outputKeyOf(data, anc.id);
    push(key, `${key}（${nodeName}）`, anc.id);
  }

  return vars;
}