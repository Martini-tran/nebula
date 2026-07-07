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
  data: { branchId?: string; conditionExpr?: string; eventName?: string };
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
  /** 执行内核：DAG（默认）| STATE_MACHINE（可回跳/成环/挂起） */
  engineType?: string;
  /** 状态机全局转移次数上限，防死循环（仅 STATE_MACHINE 生效） */
  maxTransitions?: number;
}

/**
 * 按前端 nodeType 派生状态机语义类型 stateType（用户零学习成本，不手选）：
 * START→ENTRY（唯一入口态）、END→TERMINAL（终态）、其余→NORMAL。
 * 仅 engineType=STATE_MACHINE 时有意义；DAG 内核忽略 stateType。
 */
function deriveStateType(nodeType?: string): string {
  if (nodeType === 'START') return 'ENTRY';
  if (nodeType === 'END') return 'TERMINAL';
  return 'NORMAL';
}

/**
 * 前端节点类型 → 后端执行器类型。
 * 前端 LLM 节点对应后端 {@code PromptNodeExecutor}（type=PROMPT）：前端语境无独立 PROMPT 类型，
 * LLM 即提示词节点，故落库时统一写成 PROMPT，与后端执行器对齐。其余类型原样落库。
 */
function toBackendNodeType(nodeType?: string): string {
  if (nodeType === 'LLM') return 'PROMPT';
  return nodeType || DEFAULT_NODE_TYPE;
}

/**
 * 后端节点类型 → 前端画布类型（{@link toBackendNodeType} 的逆映射）。
 * 后端 PROMPT 回显为前端 LLM 卡片（前端面板无 PROMPT 类型，两者在前端等价）。其余原样。
 */
function toFrontendNodeType(nodeType?: string): string | undefined {
  if (nodeType === 'PROMPT') return 'LLM';
  return nodeType;
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

/** 取 LOOP 容器落库的成员列表（过滤掉已不存在的节点编码） */
function loopMembers(
  node: AiFlowApi.FlowNodeRaw,
  knownCodes: Set<string>,
): string[] {
  const members = (node.nodeConfig ?? {}).members;
  return Array.isArray(members)
    ? members.filter((m: string) => knownCodes.has(m))
    : [];
}

/** FlowDefinition → X6 graph.fromJSON() 入参 */
export function flowToGraph(def: AiFlowApi.FlowDefinitionRaw): X6GraphJson {
  const knownCodes = new Set((def.nodes ?? []).map((n) => n.nodeCode));
  // 子→父映射：从每个 LOOP 容器的 nodeConfig.members 反推（回显重建 embedding）
  const parentOf = new Map<string, string>();
  (def.nodes ?? []).forEach((n) => {
    if (n.nodeType !== 'LOOP') return;
    loopMembers(n, knownCodes).forEach((m) => parentOf.set(m, n.nodeCode));
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
    const members = isLoop ? loopMembers(node, knownCodes) : [];
    return {
      id: node.nodeCode,
      shape: isLoop ? LOOP_SHAPE : NODE_SHAPE,
      x: pos.x,
      y: pos.y,
      // 容器尺寸随内容存过则回填，否则给个初始值；普通节点用固定尺寸
      width: isLoop ? (saved?.w ?? 320) : NODE_WIDTH,
      height: isLoop ? (saved?.h ?? 200) : NODE_HEIGHT,
      // 后端 PROMPT 回显为前端 LLM 卡片，其余类型原样
      data: { ...node, nodeType: toFrontendNodeType(node.nodeType) },
      ...(parent ? { parent } : {}),
      // X6 的父子关系两侧独立存储（child 的 parent / 容器的 children），
      // 只回填 parent 会得到单向关系：容器 getChildren() 为 null，自适应包裹、
      // 整体拖动、解散、再保存 members 全部失效（嵌套时内层最先坏）。
      ...(members.length > 0 ? { children: members } : {}),
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
      data: {
        conditionExpr: edge.conditionExpr,
        eventName: edge.eventName,
        branchId: edge.branchId,
      },
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
      // 前端 LLM → 后端执行器类型 PROMPT，其余原样（与后端 FlowNodeExecutor 对齐）
      nodeType: toBackendNodeType(data.nodeType),
      // 状态机语义类型按前端 nodeType 派生（START→ENTRY / END→TERMINAL / 其余→NORMAL），DAG 忽略
      stateType: deriveStateType(data.nodeType),
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
        // 状态机 signal 唤醒时匹配的事件名（DAG 忽略）
        eventName: cell.data?.eventName || undefined,
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
    engineType: meta.engineType || 'DAG',
    maxTransitions:
      meta.maxTransitions && meta.maxTransitions > 0 ? meta.maxTransitions : 100,
    nodes,
    edges,
  };
}
