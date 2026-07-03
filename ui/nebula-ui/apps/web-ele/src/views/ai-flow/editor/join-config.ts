/**
 * JOIN 汇总节点配置类型与读写归一化（node.data.nodeConfig.join 的结构）。
 *
 * JOIN 节点承载「并行分支汇聚（fan-in）」：普通节点的多条出边即并行分叉
 * （互斥分支由 IF 节点显式承载，故非 IF 多出边语义无歧义），并行链路最终
 * 汇入 JOIN。JOIN 的职责是同步屏障 + 策略，不做智能数据合并——各前驱输出
 * 本就写进 context 的 outputKey，下游节点直接引用即可：
 *   - mode：等待策略（ALL 等全部前驱 / ANY 任一先到即继续）
 *   - onError：分支失败策略（FAIL_FAST 任一失败即失败 / IGNORE 忽略失败分支）
 *   - timeoutMs：汇聚超时（0 = 不限）
 *   - output：汇总输出（key + 合并模式：原样留 context / 收成数组 / 模板拼接）
 *
 * JoinConfigDialog 的草稿由本模块类型驱动；读入用 normalizeJoinConfig 兜底缺省，
 * 写回前用 serializeJoinConfig 深拷贝。仿 ./agent-config.ts。
 * 本项目只做前端配置落库，不关心后端执行字段对齐。
 */

/** 等待策略：ALL 等全部前驱完成；ANY 任一先到即继续（赛马） */
export type JoinMode = 'ALL' | 'ANY';

/** 分支失败策略：FAIL_FAST 任一失败即整体失败；IGNORE 忽略失败分支继续 */
export type JoinErrorPolicy = 'FAIL_FAST' | 'IGNORE';

/** 输出合并模式：CONTEXT 原样留 context / ARRAY 收成数组 / TEMPLATE 模板拼接 */
export type JoinMergeMode = 'ARRAY' | 'CONTEXT' | 'TEMPLATE';

/** 汇总输出配置 */
export interface JoinOutputConfig {
  /** 输出键（merge 为 ARRAY / TEMPLATE 时结果写入 context 的键；空则用节点编码） */
  key: string;
  /** 合并模式 */
  merge: JoinMergeMode;
  /** merge=TEMPLATE 时的拼接模板，支持 {{outputKey}} 变量引用各分支输出 */
  template: string;
}

/** JOIN 节点完整配置（落库到 nodeConfig.join） */
export interface JoinConfig {
  mode: JoinMode;
  onError: JoinErrorPolicy;
  /** 汇聚超时毫秒数，0 = 不限 */
  timeoutMs: number;
  output: JoinOutputConfig;
}

/** 模板拼接的占位示例 */
export const JOIN_TEMPLATE_PLACEHOLDER = `检索结果：{{search_result}}

分析结果：{{analyze_result}}`;

/** 全新 JOIN 配置默认值 */
export function defaultJoinConfig(): JoinConfig {
  return {
    mode: 'ALL',
    onError: 'FAIL_FAST',
    timeoutMs: 0,
    output: { key: '', merge: 'CONTEXT', template: '' },
  };
}

/** 取对象子键，非对象返回空对象（读入兜底用） */
function obj(v: unknown): Record<string, any> {
  return v && typeof v === 'object' && !Array.isArray(v)
    ? (v as Record<string, any>)
    : {};
}

/**
 * 归一化读入：把 nodeConfig.join（可能缺失 / 部分字段）补全为完整 JoinConfig。
 *
 * @param raw node.data.nodeConfig.join
 */
export function normalizeJoinConfig(raw: unknown): JoinConfig {
  const src = obj(raw);
  const output = obj(src.output);
  return {
    mode: src.mode === 'ANY' ? 'ANY' : 'ALL',
    onError: src.onError === 'IGNORE' ? 'IGNORE' : 'FAIL_FAST',
    timeoutMs:
      typeof src.timeoutMs === 'number' && src.timeoutMs > 0
        ? src.timeoutMs
        : 0,
    output: {
      key: typeof output.key === 'string' ? output.key : '',
      merge:
        output.merge === 'ARRAY' || output.merge === 'TEMPLATE'
          ? output.merge
          : 'CONTEXT',
      template: typeof output.template === 'string' ? output.template : '',
    },
  };
}

/** 写回前深拷贝草稿（弹窗草稿是 reactive，落库需普通对象快照） */
export function serializeJoinConfig(cfg: JoinConfig): JoinConfig {
  return JSON.parse(JSON.stringify(cfg));
}
