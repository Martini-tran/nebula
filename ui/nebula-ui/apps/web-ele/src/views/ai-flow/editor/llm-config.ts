/**
 * LLM 节点配置类型与读写归一化（node.data.nodeConfig.llm 的结构）。
 *
 * LlmConfigDialog 五个 Tab（Model / Prompt / Parameters / Context / Output）
 * 的草稿都由本模块的类型驱动；读入用 normalizeLlmConfig 兜底缺省，
 * 写回前用 serializeLlmConfig 归一化（去空、深拷贝）。集中在此避免弹窗
 * 组件散落默认值，也便于后续运行时/后端对齐字段。
 */

/** 1. Model：仅引用模型档案 */
export interface LlmModelConfig {
  /**
   * 引用的模型档案编码。模型的 provider/model/地址/密钥/参数全部由档案承载，
   * 节点只存档案编码。落库时同步写到节点顶层 profileCode，供后端
   * 「节点 > Agent > Flow」三层定档。
   */
  profileCode?: string;
}

/** 2. Prompt：提示词构建 */
export interface LlmPromptConfig {
  /**
   * 引用的系统提示词编码（提示词管理 role=system）。选中时把其 content
   * 带出填入 systemPrompt 作为默认；下方文本可手动覆盖。落库便于后端定位。
   */
  systemPromptCode?: string;
  /** 系统提示词 */
  systemPrompt?: string;
  /**
   * 引用的用户提示词编码（提示词管理 role=user）。选中时把其 content
   * 带出填入 userPromptTemplate 作为默认；下方文本可手动覆盖。
   */
  userPromptCode?: string;
  /** 用户提示模板，支持变量 */
  userPromptTemplate?: string;
}

/** 3. Parameters：调用参数（基础表单） */
export interface LlmParametersConfig {
  temperature?: null | number;
  topP?: null | number;
  maxTokens?: null | number;
  stream?: boolean;
  seed?: null | number;
}

/** 4. Context：本次推理可用的上下文（均为开关） */
export interface LlmContextConfig {
  /** 是否携带历史消息 */
  messages: boolean;
  /** 是否读取 Agent Memory */
  memory: boolean;
  /** 是否使用知识库 */
  knowledge: boolean;
  /** 是否允许读取流程变量 */
  variables: boolean;
  /** 是否携带文件 */
  artifacts: boolean;
}

/** 输出类型 */
export type LlmOutputType = 'JSON' | 'TEXT';

/** 5. Output：模型输出方式 */
export interface LlmOutputConfig {
  /** 输出类型（文本 / JSON） */
  type: LlmOutputType;
}

/** LLM 节点完整配置（落库到 nodeConfig.llm） */
export interface LlmConfig {
  model: LlmModelConfig;
  prompt: LlmPromptConfig;
  parameters: LlmParametersConfig;
  context: LlmContextConfig;
  output: LlmOutputConfig;
}

/** 输出类型候选（仅文本 / JSON） */
export const LLM_OUTPUT_TYPES: { label: string; value: LlmOutputType }[] = [
  { label: '文本', value: 'TEXT' },
  { label: 'JSON', value: 'JSON' },
];

/** 全新 LLM 配置默认值（Context 开关默认全开） */
export function defaultLlmConfig(): LlmConfig {
  return {
    model: {},
    prompt: {},
    parameters: {
      temperature: 0.7,
      topP: 1,
      maxTokens: 4096,
      stream: true,
      seed: null,
    },
    context: {
      messages: true,
      memory: true,
      knowledge: true,
      variables: true,
      artifacts: false,
    },
    output: {
      type: 'TEXT',
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
 * 归一化读入：把 nodeConfig.llm（可能缺失 / 部分字段）补全为完整 LlmConfig。
 * 缺省值取 defaultLlmConfig；已有字段覆盖默认。
 */
export function normalizeLlmConfig(raw: unknown): LlmConfig {
  const d = defaultLlmConfig();
  const src = obj(raw);
  const model = obj(src.model);
  const prompt = obj(src.prompt);
  const params = obj(src.parameters);
  const ctx = obj(src.context);
  const output = obj(src.output);

  return {
    model: {
      profileCode: model.profileCode ?? '',
    },
    prompt: {
      systemPromptCode: prompt.systemPromptCode ?? '',
      systemPrompt: prompt.systemPrompt ?? '',
      userPromptCode: prompt.userPromptCode ?? '',
      userPromptTemplate: prompt.userPromptTemplate ?? '',
    },
    parameters: {
      temperature: params.temperature ?? d.parameters.temperature,
      topP: params.topP ?? d.parameters.topP,
      maxTokens: params.maxTokens ?? d.parameters.maxTokens,
      stream: params.stream ?? d.parameters.stream,
      seed: params.seed ?? null,
    },
    context: {
      messages: ctx.messages ?? d.context.messages,
      memory: ctx.memory ?? d.context.memory,
      knowledge: ctx.knowledge ?? d.context.knowledge,
      variables: ctx.variables ?? d.context.variables,
      artifacts: ctx.artifacts ?? d.context.artifacts,
    },
    output: {
      type: output.type === 'JSON' ? 'JSON' : 'TEXT',
    },
  };
}

/** 写回前深拷贝草稿（弹窗草稿是 reactive，落库需普通对象快照） */
export function serializeLlmConfig(cfg: LlmConfig): LlmConfig {
  return JSON.parse(JSON.stringify(cfg));
}
