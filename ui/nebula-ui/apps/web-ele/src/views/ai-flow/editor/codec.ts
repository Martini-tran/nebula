import type { AiFlowApi } from '#/api/ai-flow';

import {
  EDGE_SHAPE,
  NODE_HEIGHT,
  NODE_SHAPE,
  NODE_WIDTH,
} from './constants';

/**
 * X6 图 ↔ FlowDefinition（snake_case）双向编解码器。
 *
 * 约定：
 * - X6 node.id = nodeCode；node.data 挂完整节点字段（snake_case）。
 * - 画布坐标存入 nodeConfig.__x6 = { x, y }，不污染运行语义
 *   （PromptNodeExecutor 不读该键），用于二次编辑回显位置。
 * - X6 edge.source/target = fromNode/toNode；edge.data.conditionExpr 存条件。
 * - node.data 上的纯 UI 键（如 __run_state）由 stripTransientKeys 在落库前剔除。
 */

// 形状标识与尺寸统一由 constants 提供，此处 re-export 以兼容既有 import 路径
export { EDGE_SHAPE, NODE_HEIGHT, NODE_SHAPE, NODE_WIDTH };

/** X6 fromJSON 入参的最小结构（仅用到的字段） */
export interface X6NodeJson {
  id: string;
  shape: string;
  x: number;
  y: number;
  width: number;
  height: number;
  data: AiFlowApi.FlowNodeRaw;
  attrs?: Record<string, any>;
}

export interface X6EdgeJson {
  id: string;
  shape: string;
  source: { cell: string };
  target: { cell: string };
  data: { conditionExpr?: string };
  labels?: any[];
}

export interface X6GraphJson {
  nodes: X6NodeJson[];
  edges: X6EdgeJson[];
}

/** 流程头部元信息（画布工具栏维护，不在画布节点里） */
export interface FlowMeta {
  flowCode: string;
  name?: string;
  description?: string;
  version?: number;
  defaultProfileCode?: string;
}

/** 自动布局：未带坐标的节点按索引竖向排开 */
function autoPosition(index: number): { x: number; y: number } {
  return { x: 120 + (index % 3) * 240, y: 80 + Math.floor(index / 3) * 140 };
}

/** 剔除 node.data 上的纯 UI 态键（如 __run_state），返回可落库的浅拷贝 */
function stripTransientKeys(
  data: AiFlowApi.FlowNodeRaw | undefined,
): AiFlowApi.FlowNodeRaw {
  // __run_state 是运行态高亮的临时键，不参与运行语义，落库前剔除
  const { __run_state, ...rest } = (data ?? {}) as AiFlowApi.FlowNodeRaw & {
    __run_state?: unknown;
  };
  void __run_state;
  return rest as AiFlowApi.FlowNodeRaw;
}

/** FlowDefinition → X6 graph.fromJSON() 入参 */
export function flowToGraph(def: AiFlowApi.FlowDefinitionRaw): X6GraphJson {
  const nodes: X6NodeJson[] = (def.nodes ?? []).map((node, index) => {
    const saved = (node.nodeConfig ?? {}).__x6 as
      | undefined
      | { x: number; y: number };
    const pos =
      saved && typeof saved.x === 'number' && typeof saved.y === 'number'
        ? saved
        : autoPosition(index);
    return {
      id: node.nodeCode,
      shape: NODE_SHAPE,
      x: pos.x,
      y: pos.y,
      width: NODE_WIDTH,
      height: NODE_HEIGHT,
      data: { ...node },
    };
  });

  const edges: X6EdgeJson[] = (def.edges ?? []).map((edge, index) => ({
    id: `edge-${edge.fromNode}-${edge.toNode}-${index}`,
    shape: EDGE_SHAPE,
    source: { cell: edge.fromNode },
    target: { cell: edge.toNode },
    data: { conditionExpr: edge.conditionExpr },
    labels: edge.conditionExpr
      ? [{ attrs: { label: { text: edge.conditionExpr } } }]
      : [],
  }));

  return { nodes, edges };
}

/** X6 graph.toJSON() → FlowDefinition（snake_case，可直接落库） */
export function graphToFlow(
  graphJson: X6GraphJson,
  meta: FlowMeta,
): AiFlowApi.FlowDefinitionRaw {
  // 节点按 y 坐标排序得到稳定 sortNo
  const sortedNodes = [...(graphJson.nodes ?? [])].toSorted((a, b) => a.y - b.y);

  const nodes: AiFlowApi.FlowNodeRaw[] = sortedNodes.map((cell, index) => {
    // 剔除纯 UI 态键（如 __run_state），避免落库污染
    const data = stripTransientKeys(cell.data);
    // 持久化画布坐标到 nodeConfig.__x6
    const nodeConfig: Record<string, any> = {
      ...data.nodeConfig,
      __x6: { x: cell.x, y: cell.y },
    };
    return {
      ...(data as AiFlowApi.FlowNodeRaw),
      nodeCode: cell.id,
      nodeType: data.nodeType || 'PROMPT',
      nodeConfig: nodeConfig,
      sortNo: index,
    };
  });

  const edges: AiFlowApi.FlowEdgeRaw[] = (graphJson.edges ?? []).map(
    (cell, index) => ({
      fromNode: cell.source?.cell,
      toNode: cell.target?.cell,
      conditionExpr: cell.data?.conditionExpr || undefined,
      sortNo: index,
    }),
  );

  return {
    flowCode: meta.flowCode,
    name: meta.name,
    description: meta.description,
    version: meta.version && meta.version > 0 ? meta.version : 1,
    defaultProfileCode: meta.defaultProfileCode,
    nodes,
    edges,
  };
}
