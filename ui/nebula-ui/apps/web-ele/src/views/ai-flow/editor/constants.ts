/**
 * AI Flow 编辑器常量：X6 形状标识、节点尺寸、节点类型元信息、运行态配色。
 *
 * 这些常量原先散落在 index.vue / codec.ts，统一收敛到此处，便于
 * registerShapes、useFlowGraph、useRunHighlight、属性面板共享同一套定义。
 */

export const NODE_SHAPE = 'ai-flow-node';
export const EDGE_SHAPE = 'ai-flow-edge';
// Vue 卡片需容纳「类型行 + 摘要行」，尺寸较原手绘卡片加宽加高
export const NODE_WIDTH = 240;
export const NODE_HEIGHT = 104;

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
 * 拖入画布的通用节点默认类型。
 * 面板已收敛为单一「通用节点」入口：拖入后默认为 PROMPT（模型调用），
 * 用户在属性面板可切换为 TOOL（工具调用）。卡片按 nodeType 区分模型/工具样式。
 */
export const DEFAULT_NODE_TYPE = 'PROMPT';

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
