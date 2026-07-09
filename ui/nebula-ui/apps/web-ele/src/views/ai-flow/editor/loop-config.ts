/**
 * FOR 循环容器配置类型与读写归一化（node.data.nodeConfig.loop 的结构）。
 *
 * FOR 循环不是单节点，而是「框选一组节点 + 右键生成的虚线容器」：容器是一个
 * X6 节点（nodeType='LOOP'），被圈的节点通过 X6 embedding 成为其子节点。容器
 * 承载循环配置：循环次数（COUNT）+ 状态跳出（break 条件，复用 ./condition）。
 * 支持嵌套（循环里套循环 / 套 IF），成员关系逐层由 X6 parent/child 维护。
 *
 * LoopConfigDialog 的草稿由本模块类型驱动；读入用 normalizeLoopConfig 兜底缺省，
 * 写回前用 serializeLoopConfig 深拷贝。本项目只做前端配置落库。
 */
import type { CondGroup } from './condition';

import {
  defaultCondGroup,
  isCondGroupEmpty,
  normalizeCondGroup,
} from './condition';

/** 循环模式：按次数 / 遍历列表（FOREACH 预留，先做 COUNT） */
export type LoopMode = 'COUNT' | 'FOREACH';

/** FOR 循环完整配置（落库到 nodeConfig.loop） */
export interface LoopConfig {
  /** 循环模式 */
  mode: LoopMode;
  /** COUNT：循环次数 */
  count?: null | number;
  /** FOREACH：遍历的列表变量引用（预留） */
  itemsExpr?: string;
  /** 是否启用状态跳出（break） */
  breakEnabled: boolean;
  /** 跳出条件（breakEnabled 时生效，命中即 break） */
  breakCondition?: CondGroup;
  /** 安全上限：防死循环的最大迭代次数 */
  maxIterations?: null | number;
}

/** 循环模式候选 */
export const LOOP_MODES: { label: string; value: LoopMode }[] = [
  { label: '按次数循环', value: 'COUNT' },
  { label: '遍历列表（预留）', value: 'FOREACH' },
];

/** 默认安全上限（防死循环） */
export const LOOP_DEFAULT_MAX = 100;

/** 全新 FOR 配置默认值 */
export function defaultLoopConfig(): LoopConfig {
  return {
    mode: 'COUNT',
    count: 1,
    itemsExpr: '',
    breakEnabled: false,
    breakCondition: defaultCondGroup(),
    maxIterations: LOOP_DEFAULT_MAX,
  };
}

/** 取对象子键，非对象返回空对象（读入兜底用） */
function obj(v: unknown): Record<string, any> {
  return v && typeof v === 'object' && !Array.isArray(v)
    ? (v as Record<string, any>)
    : {};
}

/** 归一化读入：把 nodeConfig.loop 补全为完整 LoopConfig */
export function normalizeLoopConfig(raw: unknown): LoopConfig {
  const d = defaultLoopConfig();
  const src = obj(raw);
  const mode: LoopMode = src.mode === 'FOREACH' ? 'FOREACH' : 'COUNT';
  return {
    mode,
    count: typeof src.count === 'number' ? src.count : d.count,
    itemsExpr: typeof src.itemsExpr === 'string' ? src.itemsExpr : '',
    breakEnabled: src.breakEnabled === true,
    breakCondition: normalizeCondGroup(src.breakCondition),
    maxIterations:
      typeof src.maxIterations === 'number'
        ? src.maxIterations
        : d.maxIterations,
  };
}

/** 写回前深拷贝草稿（弹窗草稿是 reactive，落库需普通对象快照） */
export function serializeLoopConfig(cfg: LoopConfig): LoopConfig {
  return JSON.parse(JSON.stringify(cfg));
}

/** 循环摘要文本（容器框头展示）：次数 + 是否有 break */
export function loopSummary(cfg: LoopConfig): string {
  const times =
    cfg.mode === 'COUNT'
      ? `×${cfg.count ?? 1}`
      : cfg.itemsExpr
        ? `foreach ${cfg.itemsExpr}`
        : 'foreach';
  const brk =
    cfg.breakEnabled && !isCondGroupEmpty(cfg.breakCondition) ? ' · break' : '';
  return `${times}${brk}`;
}
