/**
 * AI Flow 编辑器常量：X6 形状标识、节点尺寸、节点类型元信息、运行态配色。
 *
 * 这些常量原先散落在 index.vue / codec.ts，统一收敛到此处，便于
 * registerShapes、useFlowGraph、useRunHighlight、属性面板共享同一套定义。
 */

export const NODE_SHAPE = 'ai-flow-node';
export const EDGE_SHAPE = 'ai-flow-edge';
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

/** 卡片主题：图标底色 / 图标字色 / 卡片边框色（对齐官方四主题） */
export type NodeTheme = 'blue' | 'green' | 'orange' | 'red';

export interface ThemeColor {
  iconBg: string;
  iconColor: string;
  border: string;
}

export const THEME_COLORS: Record<NodeTheme, ThemeColor> = {
  blue: { iconBg: '#F0F5FF', iconColor: '#1D39C4', border: '#5F95FF' },
  green: { iconBg: '#E6FFFB', iconColor: '#08979C', border: '#13C2C2' },
  orange: { iconBg: '#FFF7E6', iconColor: '#FA8C16', border: '#FA8C16' },
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
 * 全部节点类型（对齐官方 AgentFlow 九种 + 我们后端已有的 TOOL 工具节点）。
 * runnable=true 的（PROMPT/TOOL）能真正运行；其余为占位，后端就绪后再对接。
 */
export const AGENT_NODE_TYPES: AgentTypeMeta[] = [
  { nodeType: 'START', iconText: 'S', title: '开始', desc: 'Agent 开始节点', theme: 'blue', group: 'flow', runnable: false },
  { nodeType: 'END', iconText: 'E', title: '结束', desc: 'Agent 结束节点', theme: 'red', group: 'flow', runnable: false },
  { nodeType: 'LLM', iconText: 'LLM', title: 'LLM', desc: 'Agent LLM 节点', theme: 'green', group: 'biz', runnable: false },
  { nodeType: 'PROMPT', iconText: 'LLM', title: '文本大模型', desc: '处理文本指令与上下文。', theme: 'blue', group: 'biz', runnable: true },
  { nodeType: 'TOOL', iconText: 'TOOL', title: '工具调用', desc: '调用已注册工具。', theme: 'green', group: 'biz', runnable: true },
  { nodeType: 'CODE', iconText: '</>', title: '代码', desc: '运行脚本和逻辑。', theme: 'green', group: 'biz', runnable: false },
  { nodeType: 'BRANCH', iconText: 'IF', title: '分支', desc: '根据条件执行不同业务逻辑。', theme: 'orange', group: 'biz', runnable: false },
  { nodeType: 'LOOP', iconText: 'FOR', title: '循环', desc: '迭代处理重复执行步骤。', theme: 'orange', group: 'biz', runnable: false },
  { nodeType: 'KB', iconText: 'KB', title: '知识库', desc: '检索信息，提供丰富上下文。', theme: 'blue', group: 'data', runnable: false },
  { nodeType: 'MCP', iconText: 'MCP', title: 'MCP 插件', desc: '扩展外部能力。', theme: 'green', group: 'data', runnable: false },
  { nodeType: 'DB', iconText: 'DB', title: '数据库', desc: '读写数据，支撑数据持久化。', theme: 'blue', group: 'data', runnable: false },
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
  nodeType: 'PROMPT',
  iconText: 'LLM',
  title: '节点',
  desc: '',
  theme: 'blue',
  group: 'biz',
  runnable: true,
};

/** 按 nodeType 反查类型元信息，未知类型走默认 */
export function agentMetaOf(nodeType?: string): AgentTypeMeta {
  return (nodeType && AGENT_TYPE_INDEX[nodeType]) || DEFAULT_AGENT_META;
}

/** 拖入画布的默认节点类型 */
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
