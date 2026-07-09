/**
 * LLM 节点配置类型与读写归一化（node.data.nodeConfig.llm 的结构）。
 *
 * LlmConfigDialog 五个 Tab（Model / Prompt / Parameters / Context / Output）
 * 的草稿都由本模块的类型驱动；读入用 normalizeLlmConfig 兜底缺省，
 * 写回前用 serializeLlmConfig 归一化（去空、深拷贝）。集中在此避免弹窗
 * 组件散落默认值，也便于后续运行时/后端对齐字段。
 */

/** 1. Model：模型提供商与具体模型 */
export interface LlmModelConfig {
  /**
   * 引用的模型档案编码（可选）。选中档案时把其 provider/model/baseUrl/参数
   * 带出填入下方字段作为默认；下方字段可手动覆盖，也可完全不选档案纯手填。
   * 落库时同步写到节点顶层 profileCode，供后端「节点 > Agent > Flow」三层定档。
   */
  profileCode?: string;
  /** 提供商（OpenAI / Anthropic / DeepSeek / Qwen 等） */
  provider?: string;
  /** 具体模型 */
  model?: string;
  /** 自定义模型地址（可选） */
  baseUrl?: string;
  /** API Key 或 Credential 引用（可选） */
  credential?: string;
}

/** 2. Prompt：提示词构建 */
export interface LlmPromptConfig {
  /** 系统提示词 */
  systemPrompt?: string;
  /** 用户提示模板，支持变量 */
  userPromptTemplate?: string;
  /** 模板变量定义（变量名 → 上下文键，可选） */
  variables?: Record<string, string>;
}

/** 3a. Parameters 基础模式参数 */
export interface LlmBasicParams {
  temperature?: null | number;
  topP?: null | number;
  maxTokens?: null | number;
  stream?: boolean;
  seed?: null | number;
}

/** 3. Parameters：调用参数（基础 / 高级 JSON 二选一） */
export interface LlmParametersConfig {
  /** 当前模式：基础表单 / 高级 JSON */
  mode: 'advanced' | 'basic';
  /** 基础模式参数 */
  basic: LlmBasicParams;
  /** 高级模式：直接编辑的请求参数 JSON 字符串（headers/body） */
  advanced: string;
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
export type LlmOutputType = 'JSON' | 'MARKDOWN' | 'TEXT';

/** 5. Output：模型输出方式 */
export interface LlmOutputConfig {
  /** 输出类型 */
  type: LlmOutputType;
  /** 结构化输出定义（JSON Schema 字符串，可选） */
  jsonSchema?: string;
  /** 输出变量映射（输出字段 → 流程变量，可选） */
  mapping?: Record<string, string>;
}

/** LLM 节点完整配置（落库到 nodeConfig.llm） */
export interface LlmConfig {
  model: LlmModelConfig;
  prompt: LlmPromptConfig;
  parameters: LlmParametersConfig;
  context: LlmContextConfig;
  output: LlmOutputConfig;
}

/** Provider 候选（可自由输入，此列表仅作下拉建议） */
export const LLM_PROVIDERS = [
  'OpenAI',
  'Anthropic',
  'DeepSeek',
  'Qwen',
] as const;

/** 输出类型候选 */
export const LLM_OUTPUT_TYPES: { label: string; value: LlmOutputType }[] = [
  { label: 'Text', value: 'TEXT' },
  { label: 'Markdown', value: 'MARKDOWN' },
  { label: 'JSON', value: 'JSON' },
];

/** 高级参数 JSON 的占位示例 */
export const LLM_ADVANCED_PLACEHOLDER = `{
  "headers": {
    "Authorization": "Bearer xxx"
  },
  "body": {
    "temperature": 0.7,
    "top_p": 0.9,
    "max_tokens": 4096,
    "stream": true
  }
}`;

/** 全新 LLM 配置默认值（Context 开关默认全开，参数走基础模式） */
export function defaultLlmConfig(): LlmConfig {
  return {
    model: {},
    prompt: {},
    parameters: {
      mode: 'basic',
      basic: {
        temperature: 0.7,
        topP: 1,
        maxTokens: 4096,
        stream: true,
        seed: null,
      },
      advanced: '',
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
      jsonSchema: '',
      mapping: {},
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
  const basic = obj(params.basic);
  const ctx = obj(src.context);
  const output = obj(src.output);

  return {
    model: {
      profileCode: model.profileCode ?? '',
      provider: model.provider ?? '',
      model: model.model ?? '',
      baseUrl: model.baseUrl ?? '',
      credential: model.credential ?? '',
    },
    prompt: {
      systemPrompt: prompt.systemPrompt ?? '',
      userPromptTemplate: prompt.userPromptTemplate ?? '',
      variables: obj(prompt.variables) as Record<string, string>,
    },
    parameters: {
      mode: params.mode === 'advanced' ? 'advanced' : 'basic',
      basic: {
        temperature: basic.temperature ?? d.parameters.basic.temperature,
        topP: basic.topP ?? d.parameters.basic.topP,
        maxTokens: basic.maxTokens ?? d.parameters.basic.maxTokens,
        stream: basic.stream ?? d.parameters.basic.stream,
        seed: basic.seed ?? null,
      },
      advanced: typeof params.advanced === 'string' ? params.advanced : '',
    },
    context: {
      messages: ctx.messages ?? d.context.messages,
      memory: ctx.memory ?? d.context.memory,
      knowledge: ctx.knowledge ?? d.context.knowledge,
      variables: ctx.variables ?? d.context.variables,
      artifacts: ctx.artifacts ?? d.context.artifacts,
    },
    output: {
      type: (['TEXT', 'MARKDOWN', 'JSON'] as const).includes(output.type)
        ? output.type
        : 'TEXT',
      jsonSchema: output.jsonSchema ?? '',
      mapping: obj(output.mapping) as Record<string, string>,
    },
  };
}

/** 写回前深拷贝草稿（弹窗草稿是 reactive，落库需普通对象快照） */
export function serializeLlmConfig(cfg: LlmConfig): LlmConfig {
  return JSON.parse(JSON.stringify(cfg));
}
