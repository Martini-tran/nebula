/**
 * TOOL 节点配置类型与读写归一化（node.data.nodeConfig.tool 的结构）。
 *
 * ToolConfigDialog 三段（工具 / 输入输出 / 异常）的草稿都由本模块类型驱动；
 * 读入用 normalizeToolConfig 兜底缺省（并回迁旧 ToolNodeForm 落的散字段），
 * 写回前用 serializeToolConfig 归一化（深拷贝）。集中在此避免弹窗散落默认值。
 *
 * 仿 ./llm-config.ts。本项目只做前端配置落库，不关心后端执行字段对齐。
 */

/** 1. Tool：选择要调用的工具 */
export interface ToolSelectConfig {
  /** 引用的工具编码（ai_tool.toolCode） */
  toolCode?: string;
  /** 工具版本（可选） */
  version?: string;
}

/** 2. Input：工具入参映射（工具入参名 → 上下文键） */
export interface ToolInputConfig {
  mapping: Record<string, string>;
}

/** 3. Parameters：调用参数（控制工具执行行为） */
export interface ToolParametersConfig {
  /** 超时时间（毫秒，可选） */
  timeout?: null | number;
  /** 重试次数（可选） */
  retry?: null | number;
  /** 是否忽略错误 */
  ignoreError: boolean;
  /** 是否异步执行 */
  async: boolean;
}

/** 输出模式：整个结果写入 / 按字段映射 */
export type ToolOutputMode = 'FIELD' | 'FULL';

/** 4. Output：工具返回值如何保存 */
export interface ToolOutputConfig {
  /** 输出模式 */
  mode: ToolOutputMode;
  /** 输出键（留空用节点编码） */
  key?: string;
  /** 字段映射（mode=FIELD 时：返回字段 → 流程变量） */
  mapping: Record<string, string>;
}

/** 异常处理策略 */
export type ToolErrorStrategy = 'CONTINUE' | 'DEFAULT' | 'RETRY' | 'THROW';

/** 5. Error：工具失败后的策略 */
export interface ToolErrorConfig {
  /** 失败策略 */
  strategy: ToolErrorStrategy;
  /** 默认值（strategy=DEFAULT 时使用，JSON 字符串，可选） */
  defaultValue?: string;
}

/** TOOL 节点完整配置（落库到 nodeConfig.tool） */
export interface ToolConfig {
  tool: ToolSelectConfig;
  input: ToolInputConfig;
  parameters: ToolParametersConfig;
  output: ToolOutputConfig;
  error: ToolErrorConfig;
}

/** 输出模式候选 */
export const TOOL_OUTPUT_MODES: { label: string; value: ToolOutputMode }[] = [
  { label: '整个结果', value: 'FULL' },
  { label: '按字段映射', value: 'FIELD' },
];

/** 异常策略候选 */
export const TOOL_ERROR_STRATEGIES: {
  label: string;
  value: ToolErrorStrategy;
}[] = [
  { label: 'Throw（抛出异常）', value: 'THROW' },
  { label: 'Continue（继续执行）', value: 'CONTINUE' },
  { label: 'Retry（自动重试）', value: 'RETRY' },
  { label: 'Default Value（使用默认值）', value: 'DEFAULT' },
];

/** 默认值 JSON 的占位示例 */
export const TOOL_DEFAULT_VALUE_PLACEHOLDER = `{}`;

/** 全新 TOOL 配置默认值 */
export function defaultToolConfig(): ToolConfig {
  return {
    tool: {},
    input: { mapping: {} },
    parameters: {
      timeout: null,
      retry: null,
      ignoreError: false,
      async: false,
    },
    output: {
      mode: 'FULL',
      key: '',
      mapping: {},
    },
    error: {
      strategy: 'THROW',
      defaultValue: '',
    },
  };
}

/** 取对象子键，非对象返回空对象（读入兜底用） */
function obj(v: unknown): Record<string, any> {
  return v && typeof v === 'object' && !Array.isArray(v)
    ? (v as Record<string, any>)
    : {};
}

/**
 * 归一化读入：把 nodeConfig.tool（可能缺失 / 部分字段）补全为完整 ToolConfig。
 *
 * 兼容旧 ToolNodeForm 落的散字段：旧结构把 toolCode 落到 nodeConfig.toolCode、
 * 入参映射落到顶层 inputMapping、输出键落到顶层 outputKey。这里在读不到新结构
 * 对应字段时回迁旧值，保证老流程打开不丢配置。
 *
 * @param raw   node.data.nodeConfig.tool（新结构）
 * @param legacy node.data（含旧散字段 toolCode/inputMapping/outputKey），可选
 */
export function normalizeToolConfig(
  raw: unknown,
  legacy?: Record<string, any>,
): ToolConfig {
  const d = defaultToolConfig();
  const src = obj(raw);
  const tool = obj(src.tool);
  const input = obj(src.input);
  const params = obj(src.parameters);
  const output = obj(src.output);
  const error = obj(src.error);

  // 旧散字段（回迁兜底）
  const lg = obj(legacy);
  const legacyToolCode = obj(lg.nodeConfig).toolCode ?? lg.toolCode;
  const legacyMapping = obj(lg.inputMapping);
  const legacyOutputKey = lg.outputKey;

  return {
    tool: {
      toolCode: tool.toolCode ?? legacyToolCode ?? '',
      version: tool.version ?? '',
    },
    input: {
      mapping:
        Object.keys(obj(input.mapping)).length > 0
          ? (obj(input.mapping) as Record<string, string>)
          : (legacyMapping as Record<string, string>),
    },
    parameters: {
      timeout: params.timeout ?? d.parameters.timeout,
      retry: params.retry ?? d.parameters.retry,
      ignoreError: params.ignoreError ?? d.parameters.ignoreError,
      async: params.async ?? d.parameters.async,
    },
    output: {
      mode: output.mode === 'FIELD' ? 'FIELD' : 'FULL',
      key: output.key ?? legacyOutputKey ?? '',
      mapping: obj(output.mapping) as Record<string, string>,
    },
    error: {
      strategy: (
        ['THROW', 'CONTINUE', 'RETRY', 'DEFAULT'] as const
      ).includes(error.strategy)
        ? error.strategy
        : 'THROW',
      defaultValue: error.defaultValue ?? '',
    },
  };
}

/** 写回前深拷贝草稿（弹窗草稿是 reactive，落库需普通对象快照） */
export function serializeToolConfig(cfg: ToolConfig): ToolConfig {
  return JSON.parse(JSON.stringify(cfg));
}
