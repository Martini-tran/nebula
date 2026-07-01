/**
 * AI Flow 编辑器常量：X6 形状标识、节点尺寸、节点类型元信息、运行态配色。
 *
 * 这些常量原先散落在 index.vue / codec.ts，统一收敛到此处，便于
 * registerShapes、useFlowGraph、useRunHighlight、属性面板共享同一套定义。
 */

export const NODE_SHAPE = 'ai-flow-node';
export const EDGE_SHAPE = 'ai-flow-edge';
export const NODE_WIDTH = 200;
export const NODE_HEIGHT = 64;

/** 节点运行态（纯 UI 态，落库前必须从 node.data 剔除） */
export type RunState = 'executed' | 'failed' | 'skipped';

/** 节点类型元信息：驱动节点卡片配色/图标与左侧面板展示 */
export interface NodeTypeMeta {
  /** 类型标识，与后端 FlowNodeExecutor.type() 对齐 */
  type: string;
  /** 展示名（node-types 接口未返回时的兜底） */
  name: string;
  /** 主题色（节点边框、类型徽标、色条） */
  color: string;
  /** 类型徽标短文本 */
  badge: string;
}

/**
 * 已知节点类型的静态元信息。node-types 接口动态返回类型列表，
 * 此表提供每种类型的配色/徽标；未知类型走 DEFAULT_NODE_META 兜底。
 */
export const NODE_TYPE_META: Record<string, NodeTypeMeta> = {
  PROMPT: { type: 'PROMPT', name: '模型调用', color: '#409eff', badge: 'MODEL' },
  TOOL: { type: 'TOOL', name: '工具调用', color: '#67c23a', badge: 'TOOL' },
};

export const DEFAULT_NODE_META: NodeTypeMeta = {
  type: 'PROMPT',
  name: '节点',
  color: '#909399',
  badge: 'NODE',
};

export function nodeMetaOf(type?: string): NodeTypeMeta {
  return (type && NODE_TYPE_META[type]) || DEFAULT_NODE_META;
}

/**
 * 左侧面板分组项：区分「可拖入画布的节点类型」与「非节点能力」。
 * - draggable=true：对应后端某个 FlowNodeExecutor，可拖拽/双击加入画布（如 模型调用=PROMPT、工具调用=TOOL）。
 * - draggable=false：非独立节点或后端未就绪的占位（如 Mcp服务在模型节点内配置、Skill 敬请期待），仅展示不可拖。
 */
export interface PaletteItem {
  /** 分组标识 */
  key: string;
  /** 展示名 */
  name: string;
  /** 主题色 */
  color: string;
  /** 可拖入画布时对应的节点类型（draggable=true 时必填） */
  nodeType?: string;
  /** 是否可拖拽/双击加入画布 */
  draggable: boolean;
  /** 不可拖时的引导说明 */
  hint?: string;
}

/**
 * 左侧面板固定分组。模型调用/工具调用为可拖节点；Mcp服务在模型节点表单里配置、
 * Skill 后端未就绪，二者仅占位展示不可拖。
 */
export const PALETTE_ITEMS: PaletteItem[] = [
  {
    key: 'MODEL',
    name: '模型调用',
    color: nodeMetaOf('PROMPT').color,
    nodeType: 'PROMPT',
    draggable: true,
  },
  {
    key: 'TOOL',
    name: '工具调用',
    color: nodeMetaOf('TOOL').color,
    nodeType: 'TOOL',
    draggable: true,
  },
  {
    key: 'MCP',
    name: 'Mcp服务',
    color: '#e6a23c',
    draggable: false,
    hint: '在「模型调用」节点属性里关联',
  },
  {
    key: 'SKILL',
    name: 'Skill',
    color: '#909399',
    draggable: false,
    hint: '敬请期待',
  },
];

/** 运行态配色（画布回放高亮用） */
export const RUN_STATE_COLOR: Record<RunState, string> = {
  executed: '#67c23a',
  failed: '#f56c6c',
  skipped: '#c0c4cc',
};

/** 运行态徽标图标（画布节点右上角小标记） */
export const RUN_STATE_ICON: Record<RunState, string> = {
  executed: '✓',
  failed: '✕',
  skipped: '○',
};

/** 落库前需从 node.data 剔除的纯 UI 键（__x6 坐标保留供回显，不在此列） */
export const TRANSIENT_DATA_KEYS = ['__run_state'] as const;
