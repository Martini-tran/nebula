import type { AiFlowApi } from '#/api/ai-flow';

import {
  DEFAULT_NODE_TYPE,
  EDGE_SHAPE,
  LOOP_SHAPE,
  NODE_HEIGHT,
  NODE_SHAPE,
  NODE_WIDTH,
} from './constants';
import { branchIdFromPort } from './if-config';

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
  /** X6 embedding：父容器 id / 直接子节点 id 列表（LOOP 容器成员关系） */
  parent?: string;
  children?: string[];
  zIndex?: number;
}

export interface X6EdgeJson {
  id: string;
  shape: string;
  source: { cell: string; port?: string };
  target: { cell: string; port?: string };
  data: { branchId?: string; conditionExpr?: string };
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
  // 子→父映射：从每个 LOOP 容器的 nodeConfig.members 反推（回显重建 embedding）
  const parentOf = new Map<string, string>();
  (def.nodes ?? []).forEach((n) => {
    if (n.nodeType !== 'LOOP') return;
    const members = (n.nodeConfig ?? {}).members;
    if (Array.isArray(members)) {
      members.forEach((m: string) => parentOf.set(m, n.nodeCode));
    }
  });

  const nodes: X6NodeJson[] = (def.nodes ?? []).map((node, index) => {
    const saved = (node.nodeConfig ?? {}).__x6 as
      | undefined
      | { h?: number; w?: number; x: number; y: number };
    const pos =
      saved && typeof saved.x === 'number' && typeof saved.y === 'number'
        ? saved
        : autoPosition(index);
    const isLoop = node.nodeType === 'LOOP';
    const parent = parentOf.get(node.nodeCode);
    return {
      id: node.nodeCode,
      shape: isLoop ? LOOP_SHAPE : NODE_SHAPE,
      x: pos.x,
      y: pos.y,
      // 容器尺寸随内容存过则回填，否则给个初始值；普通节点用固定尺寸
      width: isLoop ? (saved?.w ?? 320) : NODE_WIDTH,
      height: isLoop ? (saved?.h ?? 200) : NODE_HEIGHT,
      data: { ...node },
      ...(parent ? { parent } : {}),
      // 容器垫底，成员浮其上
      ...(isLoop ? { zIndex: 0 } : {}),
    };
  });

  // 节点类型索引：回显连边时据此挑端口（IF 出边接 out:<branchId>，入边接 in）
  const typeOf = new Map<string, string | undefined>(
    (def.nodes ?? []).map((n) => [n.nodeCode, n.nodeType]),
  );

  const edges: X6EdgeJson[] = (def.edges ?? []).map((edge, index) => {
    const fromIsIf = typeOf.get(edge.fromNode) === 'IF';
    const toIsIf = typeOf.get(edge.toNode) === 'IF';
    // IF 出边连回其分支输出端口；IF 入边连到 in 端口；其余走默认四向端口（right→left）
    const sourcePort =
      fromIsIf && edge.branchId ? `out:${edge.branchId}` : 'right';
    const targetPort = toIsIf ? 'in' : 'left';
    return {
      id: `edge-${edge.fromNode}-${edge.toNode}-${index}`,
      shape: EDGE_SHAPE,
      source: { cell: edge.fromNode, port: sourcePort },
      target: { cell: edge.toNode, port: targetPort },
      data: { conditionExpr: edge.conditionExpr, branchId: edge.branchId },
      labels: edge.conditionExpr
        ? [{ attrs: { label: { text: edge.conditionExpr } } }]
        : [],
    };
  });

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
    const isLoop = data.nodeType === 'LOOP';
    // 持久化画布坐标到 nodeConfig.__x6；容器额外存尺寸（回显重建虚线框大小）
    const nodeConfig: Record<string, any> = {
      ...data.nodeConfig,
      __x6: isLoop
        ? { x: cell.x, y: cell.y, w: cell.width, h: cell.height }
        : { x: cell.x, y: cell.y },
    };
    // 容器落成员列表（直接子节点 nodeCode），回显据此重建 embedding 父子关系
    if (isLoop) {
      nodeConfig.members = Array.isArray(cell.children) ? [...cell.children] : [];
    }
    return {
      ...(data as AiFlowApi.FlowNodeRaw),
      nodeCode: cell.id,
      nodeType: data.nodeType || DEFAULT_NODE_TYPE,
      nodeConfig,
      sortNo: index,
    };
  });

  const edges: AiFlowApi.FlowEdgeRaw[] = (graphJson.edges ?? []).map(
    (cell, index) => {
      // IF 出边：从源端口 out:<branchId> 解析分支 id 落库
      const branchId = branchIdFromPort(cell.source?.port) || undefined;
      return {
        fromNode: cell.source?.cell,
        toNode: cell.target?.cell,
        conditionExpr: cell.data?.conditionExpr || undefined,
        branchId,
        sortNo: index,
      };
    },
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
