/**
 * AGENT_REACT 节点配置类型与读写归一化（node.data.nodeConfig.agentReact 的结构）。
 *
 * 对应后端 AgentReactNodeExecutor（type=AGENT_REACT）：给模型一批工具白名单，由模型在
 * 里程碑内自主多轮选调不同工具（ReAct loop），区别于 TOOL 节点的「固定单工具」。
 *
 * ⚠ 与 llm-config / tool-config 的一个关键差异：**本节点的配置需要被后端真正读到**。
 * 后端执行器读的是扁平契约——nodeConfig.toolCodes（字符串数组）+ 节点顶层
 * promptTemplate / systemPrompt / outputMode / outputKey / profileCode。故弹窗确认时
 * 除了落嵌套的 nodeConfig.agentReact（供前端回显与卡片摘要），还会用 toBackendFields
 * 同步出这份扁平契约。两者以嵌套结构为准，扁平字段是它的投影。
 */

/** 1. Model：仅引用模型档案（模型/地址/密钥/参数由档案承载） */
export interface AgentReactModelConfig {
  /** 引用的模型档案编码；同步到节点顶层 profileCode 供后端定档 */
  profileCode?: string;
}

/** 2. Prompt：提示词构建（同步到节点顶层 systemPrompt / promptTemplate） */
export interface AgentReactPromptConfig {
  /** 引用的系统提示词编码（提示词管理 role=system），选中时带出正文 */
  systemPromptCode?: string;
  /** 系统提示词正文 */
  systemPrompt?: string;
  /** 引用的用户提示词编码（role=user） */
  userPromptCode?: string;
  /** 用户提示模板，支持 {{变量}} 占位符 */
  userPromptTemplate?: string;
}

/** 3. Tools：模型可自选的工具白名单（本节点的核心配置） */
export interface AgentReactToolsConfig {
  /**
   * 工具编码数组（对应 ai_tool.toolCode / 后端 ToolDefinition.code()）。
   * 落库时同步到 nodeConfig.toolCodes 供后端 AgentReactNodeExecutor 读取。
   * 留空 = 模型无工具可选，退化为普通对话（后端不报错）。
   */
  toolCodes: string[];
}

/** 输出类型 */
export type AgentReactOutputType = 'JSON' | 'TEXT';

/** 4. Output：终轮产物如何写回上下文（同步到节点顶层 outputMode / outputKey） */
export interface AgentReactOutputConfig {
  /**
   * 输出类型。JSON 模式下后端会把产物**逐键展开**进上下文——
   * 「计划-执行」成环结构靠这一点让 IF 的 guard 读到 hasPending / plan。
   */
  type: AgentReactOutputType;
  /** 输出键（留空用节点编码） */
  key?: string;
}

/** AGENT_REACT 节点完整配置（落库到 nodeConfig.agentReact） */
export interface AgentReactConfig {
  model: AgentReactModelConfig;
  prompt: AgentReactPromptConfig;
  tools: AgentReactToolsConfig;
  output: AgentReactOutputConfig;
}

/** 输出类型候选 */
export const AGENT_REACT_OUTPUT_TYPES: {
  label: string;
  value: AgentReactOutputType;
}[] = [
  { label: '文本', value: 'TEXT' },
  { label: 'JSON（逐键展开进上下文）', value: 'JSON' },
];

/** 全新 AGENT_REACT 配置默认值 */
export function defaultAgentReactConfig(): AgentReactConfig {
  return {
    model: {},
    prompt: {},
    tools: { toolCodes: [] },
    output: { type: 'TEXT', key: '' },
  };
}

/** 取对象子键，非对象返回空对象（读入兜底用） */
function obj(v: unknown): Record<string, any> {
  return v && typeof v === 'object' && !Array.isArray(v)
    ? (v as Record<string, any>)
    : {};
}

/** 取字符串数组（过滤空白项），非数组返回空数组 */
function strArray(v: unknown): string[] {
  if (!Array.isArray(v)) return [];
  return v
    .filter((item) => item != null && String(item).trim() !== '')
    .map(String);
}

/**
 * 归一化读入：把 nodeConfig.agentReact 补全为完整 AgentReactConfig。
 *
 * 扁平契约回迁：嵌套结构缺字段时，回读后端契约位置（nodeConfig.toolCodes 与节点顶层
 * promptTemplate/systemPrompt/outputMode/outputKey/profileCode）。这样手写 SQL 落的
 * 节点、或历史只落了扁平字段的节点，打开配置面板也能正确回显。
 *
 * @param raw    node.data.nodeConfig.agentReact（嵌套结构）
 * @param legacy node.data（含 nodeConfig.toolCodes 与顶层扁平字段），可选
 */
export function normalizeAgentReactConfig(
  raw: unknown,
  legacy?: Record<string, any>,
): AgentReactConfig {
  const d = defaultAgentReactConfig();
  const src = obj(raw);
  const model = obj(src.model);
  const prompt = obj(src.prompt);
  const tools = obj(src.tools);
  const output = obj(src.output);

  // 后端扁平契约（回迁兜底）
  const lg = obj(legacy);
  const flatToolCodes = strArray(obj(lg.nodeConfig).toolCodes);
  const nestedToolCodes = strArray(tools.toolCodes);

  const type = output.type ?? lg.outputMode;

  return {
    model: {
      profileCode: model.profileCode ?? lg.profileCode ?? '',
    },
    prompt: {
      systemPromptCode: prompt.systemPromptCode ?? '',
      systemPrompt: prompt.systemPrompt ?? lg.systemPrompt ?? '',
      userPromptCode: prompt.userPromptCode ?? '',
      userPromptTemplate:
        prompt.userPromptTemplate ?? lg.promptTemplate ?? '',
    },
    tools: {
      // 嵌套结构优先；为空时回迁后端扁平契约
      toolCodes:
        nestedToolCodes.length > 0 ? nestedToolCodes : flatToolCodes,
    },
    output: {
      type: type === 'JSON' ? 'JSON' : 'TEXT',
      key: output.key ?? lg.outputKey ?? d.output.key,
    },
  };
}

/**
 * 写回前深拷贝草稿（弹窗草稿是 reactive，落库需普通对象快照）。
 *
 * 用 JSON 深拷贝而非 structuredClone：草稿是 Vue reactive 代理，直接交给
 * structuredClone 会抛 DataCloneError。与同目录 llm-config / tool-config 一致。
 */
export function serializeAgentReactConfig(
  cfg: AgentReactConfig,
): AgentReactConfig {
  // eslint-disable-next-line unicorn/prefer-structured-clone -- reactive 代理不能交给 structuredClone
  return JSON.parse(JSON.stringify(cfg));
}

/** 后端执行器真正读取的扁平契约（节点顶层字段 + nodeConfig.toolCodes） */
export interface AgentReactBackendFields {
  /** nodeConfig.toolCodes：模型可自选的工具白名单 */
  toolCodes: string[];
  /** 节点顶层字段 */
  profileCode?: string;
  systemPrompt?: string;
  promptTemplate?: string;
  outputMode?: string;
  outputKey?: string;
}

/**
 * 把配置草稿投影为后端 AgentReactNodeExecutor 真正读取的扁平契约。
 *
 * 这是本节点与 LLM/TOOL 节点的关键区别：那两者只落嵌套 nodeConfig（前端自用），
 * 而本节点画布配完即可直接运行，无需再手写 SQL 补字段。
 * 空值一律给 undefined（而非空串），避免落库产生无意义的空字段。
 */
export function toBackendFields(cfg: AgentReactConfig): AgentReactBackendFields {
  const trim = (v?: string) => {
    const s = (v ?? '').trim();
    return s === '' ? undefined : s;
  };
  return {
    toolCodes: strArray(cfg.tools.toolCodes),
    profileCode: trim(cfg.model.profileCode),
    systemPrompt: trim(cfg.prompt.systemPrompt),
    promptTemplate: trim(cfg.prompt.userPromptTemplate),
    outputMode: cfg.output.type,
    outputKey: trim(cfg.output.key),
  };
}
