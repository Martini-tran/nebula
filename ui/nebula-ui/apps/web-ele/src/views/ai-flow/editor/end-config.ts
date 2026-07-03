/**
 * END 结束节点配置类型与读写归一化（node.data.nodeConfig.end 的结构）。
 *
 * END 节点是流程唯一出口：只承接上游、无出边。它的职责是把流程最终输出
 * 收敛为**固定格式 JSON**——outputJson 存一个 JSON 对象模板文本（键=输出
 * 字段，字符串值里可用 {{outputKey}} 引用上游节点/入参的输出），运行结束时
 * 按模板产出结构固定的结果。
 *
 * EndConfigDialog 的草稿由本模块类型驱动；读入用 normalizeEndConfig 兜底缺省，
 * 写回前用 serializeEndConfig 深拷贝；parseEndOutputJson 校验模板必须是
 * JSON 对象（数组/标量拒绝）。仿 ./join-config.ts。
 * 本项目只做前端配置落库，不关心后端执行字段对齐。
 */

/** END 节点完整配置（落库到 nodeConfig.end） */
export interface EndConfig {
  /** 固定输出 JSON 模板文本（JSON 对象；字符串值可用 {{outputKey}} 引用） */
  outputJson: string;
}

/** 输出 JSON 模板的占位示例 */
export const END_OUTPUT_PLACEHOLDER = `{
  "answer": "{{llm_answer}}",
  "sources": "{{search_result}}",
  "success": true
}`;

/** 全新 END 配置默认值 */
export function defaultEndConfig(): EndConfig {
  return { outputJson: '' };
}

/** 取对象子键，非对象返回空对象（读入兜底用） */
function obj(v: unknown): Record<string, any> {
  return v && typeof v === 'object' && !Array.isArray(v)
    ? (v as Record<string, any>)
    : {};
}

/** 归一化读入：把 nodeConfig.end（可能缺失 / 部分字段）补全为完整 EndConfig */
export function normalizeEndConfig(raw: unknown): EndConfig {
  const src = obj(raw);
  return {
    outputJson: typeof src.outputJson === 'string' ? src.outputJson : '',
  };
}

/** 写回前深拷贝草稿（弹窗草稿是 reactive，落库需普通对象快照） */
export function serializeEndConfig(cfg: EndConfig): EndConfig {
  return JSON.parse(JSON.stringify(cfg));
}

/**
 * 解析并校验输出 JSON 模板。空文本视为未配置（合法）；非空时必须是
 * JSON 对象（键=输出字段），数组或标量均拒绝。
 */
export function parseEndOutputJson(
  text: string,
): { message: string; ok: false } | { ok: true; output: Record<string, any> } {
  const trimmed = text.trim();
  if (!trimmed) return { ok: true, output: {} };

  let parsed: unknown;
  try {
    parsed = JSON.parse(trimmed);
  } catch {
    return { message: '输出模板不是合法 JSON', ok: false };
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    return { message: '输出模板必须是 JSON 对象（key: value）', ok: false };
  }
  return { ok: true, output: parsed as Record<string, any> };
}

/** 是否已配置输出模板（卡片指示点亮灭用：非空且是合法 JSON 对象） */
export function isEndOutputConfigured(cfg: EndConfig): boolean {
  const trimmed = cfg.outputJson.trim();
  if (!trimmed) return false;
  return parseEndOutputJson(trimmed).ok;
}
