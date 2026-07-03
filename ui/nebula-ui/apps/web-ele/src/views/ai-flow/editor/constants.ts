/**
 * AI Flow 编辑器常量：X6 形状标识、节点尺寸、节点类型元信息、运行态配色。
 *
 * 这些常量原先散落在 index.vue / codec.ts，统一收敛到此处，便于
 * registerShapes、useFlowGraph、useRunHighlight、属性面板共享同一套定义。
 */

export const NODE_SHAPE = 'ai-flow-node';
export const EDGE_SHAPE = 'ai-flow-edge';
/** FOR 循环容器形状（虚线分组框，X6 embedding 父节点，无连线端口） */
export const LOOP_SHAPE = 'ai-flow-loop-group';
/** 循环容器新建时的默认尺寸 / 内边距（框选生成时按包围盒外扩） */
export const LOOP_PADDING = 28;
export const LOOP_HEAD_SPACE = 20;
// 对齐官方 AgentFlow 卡片尺寸
export const NODE_WIDTH = 260;
export const NODE_HEIGHT = 96;

/**
 * 流程编辑器弹窗统一规格：节点属性、开始节点配置、全局记忆等所有配置类
 * 弹窗共用同一宽度/顶距/样式类（flow-prop-dialog 的全局样式限制 body
 * 高度 78vh 超出滚动，定义在 PropertyPanel.vue 的非 scoped 样式块）。
 */
export const FLOW_DIALOG = {
  width: '820px',
  top: '5vh',
  class: 'flow-prop-dialog',
} as const;

/** 端口连接态配色（对齐官方：已连蓝、未连灰） */
export const PORT_COLOR_CONNECTED = '#5F95FF';
export const PORT_COLOR_IDLE = '#C2C8D5';
/** 连线主题色 */
export const EDGE_COLOR = '#5F95FF';

/** 节点运行态（纯 UI 态，落库前必须从 node.data 剔除） */
export type RunState = 'executed' | 'failed' | 'skipped';

/** 卡片主题：图标底色 / 图标字色 / 卡片边框色（四主题 + AGENT 专用紫） */
export type NodeTheme = 'blue' | 'green' | 'orange' | 'purple' | 'red';

export interface ThemeColor {
  iconBg: string;
  iconColor: string;
  border: string;
}

export const THEME_COLORS: Record<NodeTheme, ThemeColor> = {
  blue: { iconBg: '#F0F5FF', iconColor: '#1D39C4', border: '#5F95FF' },
  green: { iconBg: '#E6FFFB', iconColor: '#08979C', border: '#13C2C2' },
  orange: { iconBg: '#FFF7E6', iconColor: '#FA8C16', border: '#FA8C16' },
  purple: { iconBg: '#F9F0FF', iconColor: '#531DAB', border: '#722ED1' },
  red: { iconBg: '#FFF1F0', iconColor: '#CF1322', border: '#FF7875' },
};

/** 面板分组 */
export type NodeGroup = 'biz' | 'data' | 'flow';

/**
 * Agent 节点类型元信息：驱动左侧面板、画布卡片、属性面板类型下拉。
 * 前端单一事实源（不再依赖后端 node-types 接口）。
 */
export interface AgentTypeMeta {
  /** 前端类型键（= 后端 nodeType，占位类型后端暂无执行器） */
  nodeType: string;
  /** 图标短文本（如 LLM、IF、FOR） */
  iconText: string;
  /** 展示标题 */
  title: string;
  /** 描述（占位类型卡片摘要用） */
  desc: string;
  /** 主题 */
  theme: NodeTheme;
  /** 面板分组 */
  group: NodeGroup;
  /** 后端是否已有执行器（false=占位，运行时暂不可用） */
  runnable: boolean;
}

/**
 * 面板可选节点类型：仅保留有明确定义/可用的节点。
 * 占位节点（END/PROMPT/CODE/BRANCH/LOOP/KB/MCP/DB）已移除，不在面板展示。
 * - START：流程开始节点（专属卡片 + 配置弹窗）
 * - LLM：大模型节点（专属卡片 + 配置弹窗）
 * - TOOL：工具调用节点（专属卡片 + 配置弹窗，后端已有执行器）
 * - AGENT：调用另一个已设计好的 Agent（复用 Workflow）（专属卡片 + 右键菜单，配置弹窗待补）
 */
export const AGENT_NODE_TYPES: AgentTypeMeta[] = [
  { nodeType: 'START', iconText: 'S', title: '开始', desc: 'Agent 开始节点', theme: 'blue', group: 'flow', runnable: false },
  { nodeType: 'LLM', iconText: 'LLM', title: 'LLM', desc: 'Agent LLM 节点', theme: 'green', group: 'biz', runnable: false },
  { nodeType: 'TOOL', iconText: 'TOOL', title: '工具调用', desc: '调用已注册工具。', theme: 'green', group: 'biz', runnable: true },
  { nodeType: 'AGENT', iconText: 'AGENT', title: 'Agent 调用', desc: '调用另一个已设计好的 Agent（复用 Workflow）。', theme: 'purple', group: 'biz', runnable: false },
  { nodeType: 'IF', iconText: 'IF', title: '条件判断', desc: '多分支条件判断（自上而下首中）。', theme: 'orange', group: 'flow', runnable: false },
];

/** 面板分组标题 */
export const NODE_GROUP_TITLES: Record<NodeGroup, string> = {
  flow: '流程',
  biz: '业务逻辑',
  data: '知识库&数据',
};

const AGENT_TYPE_INDEX: Record<string, AgentTypeMeta> = Object.fromEntries(
  AGENT_NODE_TYPES.map((t) => [t.nodeType, t]),
);

export const DEFAULT_AGENT_META: AgentTypeMeta = {
  nodeType: 'TOOL',
  iconText: 'TOOL',
  title: '节点',
  desc: '',
  theme: 'green',
  group: 'biz',
  runnable: true,
};

/** 按 nodeType 反查类型元信息，未知类型走默认 */
export function agentMetaOf(nodeType?: string): AgentTypeMeta {
  return (nodeType && AGENT_TYPE_INDEX[nodeType]) || DEFAULT_AGENT_META;
}

/** 拖入画布的默认节点类型 */
export const DEFAULT_NODE_TYPE = 'TOOL';

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
