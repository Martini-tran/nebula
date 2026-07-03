/**
 * 条件模型：可视化条件构造器的数据结构与读写归一化。
 *
 * IF 节点的分支条件、FOR 循环的跳出（break）条件都复用这一套结构，避免各写各的。
 * 语义：一个 CondGroup = 若干 CondClause 用 AND/OR 连接；一个 CondClause =
 * 左值（变量引用）+ 运算符 + 右值（部分算子无右值）。
 *
 * 本项目只做前端配置落库，不在前端求值——结构化 JSON 落到 nodeConfig，运行交后端。
 * 读入用 normalizeCondGroup 兜底缺省，写回前用 serializeCondGroup 深拷贝快照。
 */

/** 运算符。isEmpty/isNotEmpty/isTrue/isFalse 无右值 */
export type CondOp =
  | 'contains'
  | 'eq'
  | 'gt'
  | 'gte'
  | 'isEmpty'
  | 'isFalse'
  | 'isNotEmpty'
  | 'isTrue'
  | 'lt'
  | 'lte'
  | 'neq'
  | 'notContains';

/** 单个条件子句：左值 运算符 右值 */
export interface CondClause {
  /** 左值：变量引用，如 inputs.intent / node_3.output.score */
  left: string;
  /** 运算符 */
  op: CondOp;
  /** 右值（无右值算子忽略此字段） */
  right?: string;
}

/** 条件组：组内多子句用 AND/OR 连接 */
export interface CondGroup {
  logic: 'AND' | 'OR';
  clauses: CondClause[];
}

/** 运算符候选（下拉用）+ 是否需要右值 */
export const COND_OPS: { label: string; needRight: boolean; value: CondOp }[] = [
  { label: '等于 ==', value: 'eq', needRight: true },
  { label: '不等于 !=', value: 'neq', needRight: true },
  { label: '大于 >', value: 'gt', needRight: true },
  { label: '大于等于 >=', value: 'gte', needRight: true },
  { label: '小于 <', value: 'lt', needRight: true },
  { label: '小于等于 <=', value: 'lte', needRight: true },
  { label: '包含', value: 'contains', needRight: true },
  { label: '不包含', value: 'notContains', needRight: true },
  { label: '为空', value: 'isEmpty', needRight: false },
  { label: '不为空', value: 'isNotEmpty', needRight: false },
  { label: '为真', value: 'isTrue', needRight: false },
  { label: '为假', value: 'isFalse', needRight: false },
];

const OP_INDEX: Record<string, { needRight: boolean }> = Object.fromEntries(
  COND_OPS.map((o) => [o.value, { needRight: o.needRight }]),
);

const OP_VALUES = new Set(COND_OPS.map((o) => o.value));

/** 该运算符是否需要右值（isEmpty 等无右值算子返回 false） */
export function opNeedsRight(op: CondOp): boolean {
  return OP_INDEX[op]?.needRight ?? true;
}

/** 全新空子句默认值 */
export function defaultClause(): CondClause {
  return { left: '', op: 'eq', right: '' };
}

/** 全新空条件组默认值（默认 AND，一条空子句） */
export function defaultCondGroup(): CondGroup {
  return { logic: 'AND', clauses: [defaultClause()] };
}

/** 取对象子键，非对象返回空对象（读入兜底用） */
function obj(v: unknown): Record<string, any> {
  return v && typeof v === 'object' && !Array.isArray(v)
    ? (v as Record<string, any>)
    : {};
}

/** 归一化单个子句：补全缺省，非法运算符退回 eq */
function normalizeClause(raw: unknown): CondClause {
  const src = obj(raw);
  const op = OP_VALUES.has(src.op) ? (src.op as CondOp) : 'eq';
  return {
    left: typeof src.left === 'string' ? src.left : '',
    op,
    right: typeof src.right === 'string' ? src.right : '',
  };
}

/**
 * 归一化读入：把 raw（可能缺失 / 部分字段）补全为完整 CondGroup。
 * 空子句列表补一条空子句，保证 UI 至少有一行可编辑。
 */
export function normalizeCondGroup(raw: unknown): CondGroup {
  const src = obj(raw);
  const logic = src.logic === 'OR' ? 'OR' : 'AND';
  const clauses = Array.isArray(src.clauses)
    ? src.clauses.map((c) => normalizeClause(c))
    : [];
  return {
    logic,
    clauses: clauses.length > 0 ? clauses : [defaultClause()],
  };
}

/** 写回前深拷贝草稿（弹窗草稿是 reactive，落库需普通对象快照） */
export function serializeCondGroup(cfg: CondGroup): CondGroup {
  return JSON.parse(JSON.stringify(cfg));
}

/** 条件组是否为空（无任何有效左值子句）——用于卡片能力点亮/暗判定 */
export function isCondGroupEmpty(g: CondGroup | undefined): boolean {
  if (!g || !Array.isArray(g.clauses)) return true;
  return !g.clauses.some((c) => (c.left ?? '').trim());
}
