/**
 * IF 条件节点配置类型与读写归一化（node.data.nodeConfig.if 的结构）。
 *
 * IF 节点承载「多分支条件判断」：若干分支自上而下评估，第一个条件成立的分支
 * 生效（互斥、首中语义），末尾可选 else 兜底分支。每个分支 = 一个条件组
 * （复用 ./condition 的 CondGroup）+ 一个 IF 节点右侧输出端口，连边时把
 * edge.data.branchId 落上，取代手写的边上 SpEL。
 *
 * IfConfigDialog 的草稿由本模块类型驱动；读入用 normalizeIfConfig 兜底缺省，
 * 写回前用 serializeIfConfig 深拷贝。卡片按分支数动态长高、端口按分支行对齐，
 * 由 applyIfNodeShape 在写回/回显时同步到 X6 node（尺寸 + ports）。
 *
 * 本项目只做前端配置落库，不关心后端执行字段对齐。
 */
import type { Node } from '@antv/x6';

import type { CondGroup } from './condition';

import { NODE_WIDTH } from './constants';
import {
  defaultCondGroup,
  isCondGroupEmpty,
  normalizeCondGroup,
} from './condition';

/** 单个分支：条件组 + 稳定 id（连边靠它对应输出口）+ 展示名 */
export interface IfBranch {
  /** 稳定分支 id（连边 edge.data.branchId 关联；跨编辑不变） */
  id: string;
  /** 分支名（画布输出口标签 / 边 label） */
  label: string;
  /** 分支条件（isElse=true 时忽略） */
  condition: CondGroup;
  /** 末尾兜底分支：无条件，永远垫底 */
  isElse?: boolean;
}

/** IF 节点完整配置（落库到 nodeConfig.if） */
export interface IfConfig {
  /** 有序分支列表，自上而下首中；含 else 时 else 恒在末尾 */
  branches: IfBranch[];
}

// ---------------- 卡片尺寸 / 端口布局 ----------------
/** 卡片头部高度（图标 + 标题行） */
export const IF_HEADER_H = 44;
/** 每个分支行高 */
export const IF_ROW_H = 28;
/** 卡片上下内边距合计 */
export const IF_PADDING = 16;

/** 按分支数算卡片总高：头 + N 行 + 内边距，最少给一行的量 */
export function ifNodeHeight(branchCount: number): number {
  const rows = Math.max(branchCount, 1);
  return IF_HEADER_H + rows * IF_ROW_H + IF_PADDING;
}

/** 生成稳定分支 id（非随机——Date.now/Math.random 在部分环境受限，用序号 + 计数） */
let branchSeq = 0;
export function genBranchId(): string {
  branchSeq += 1;
  return `br_${branchSeq}`;
}

/** 全新空分支（默认一条空条件组） */
export function defaultBranch(label = '分支'): IfBranch {
  return { id: genBranchId(), label, condition: defaultCondGroup() };
}

/** 全新 IF 配置默认值：一个条件分支 + 一个 else 兜底 */
export function defaultIfConfig(): IfConfig {
  return {
    branches: [
      defaultBranch('分支1'),
      { id: genBranchId(), label: '否则', condition: defaultCondGroup(), isElse: true },
    ],
  };
}

/** 取对象子键，非对象返回空对象（读入兜底用） */
function obj(v: unknown): Record<string, any> {
  return v && typeof v === 'object' && !Array.isArray(v)
    ? (v as Record<string, any>)
    : {};
}

/** 归一化单个分支：补全 id/label/condition，回收 isElse */
function normalizeBranch(raw: unknown, index: number): IfBranch {
  const src = obj(raw);
  const id = typeof src.id === 'string' && src.id ? src.id : genBranchId();
  const label =
    typeof src.label === 'string' && src.label ? src.label : `分支${index + 1}`;
  const isElse = src.isElse === true;
  return {
    id,
    label,
    condition: normalizeCondGroup(src.condition),
    ...(isElse ? { isElse: true } : {}),
  };
}

/**
 * 归一化读入：把 nodeConfig.if（可能缺失 / 部分字段）补全为完整 IfConfig。
 * 无分支时退回默认（一条件 + else）。else 分支强制排到末尾，保证首中语义正确。
 */
export function normalizeIfConfig(raw: unknown): IfConfig {
  const src = obj(raw);
  const list = Array.isArray(src.branches) ? src.branches : [];
  if (list.length === 0) return defaultIfConfig();

  const branches = list.map((b, i) => normalizeBranch(b, i));
  // else 恒在末尾：拆出非 else + else，重新拼接
  const normal = branches.filter((b) => !b.isElse);
  const elses = branches.filter((b) => b.isElse);
  // 至多保留一个 else
  const ordered = elses.length > 0 ? [...normal, elses[0]!] : normal;
  return { branches: ordered.length > 0 ? ordered : defaultIfConfig().branches };
}

/** 写回前深拷贝草稿（弹窗草稿是 reactive，落库需普通对象快照） */
export function serializeIfConfig(cfg: IfConfig): IfConfig {
  return JSON.parse(JSON.stringify(cfg));
}

/** 某分支是否「已配置」（else 视为已配；普通分支需条件非空）——卡片行点亮用 */
export function isBranchConfigured(b: IfBranch): boolean {
  if (b.isElse) return true;
  return !isCondGroupEmpty(b.condition);
}

// ---------------- X6 节点形状同步（尺寸 + 动态输出端口）----------------
/**
 * 把 IF 分支布局同步到 X6 node：
 *  - resize 卡片高度 = ifNodeHeight(分支数)
 *  - 重设端口：左侧一个输入端口（in），右侧每分支一个输出端口（out:<branchId>），
 *    输出端口 y 坐标按分支行居中定位，与卡片上分支行视觉对齐。
 *
 * 连边时用 out:<branchId> 端口，落库 edge.data.branchId 即由端口 id 解析得到。
 * 在配置写回、回显重建时调用。
 */
export function applyIfNodeShape(node: Node, branches: IfBranch[]): void {
  const count = Math.max(branches.length, 1);
  const height = ifNodeHeight(count);
  node.resize(NODE_WIDTH, height);

  // 每个输出端口在其分支行垂直居中：行区起点在 header 之下。
  // 用 left-abs / right-abs 组（absolute 定位），args.y 传百分比精确对齐分支行。
  const items: Array<Record<string, any>> = [
    // 左侧输入端口（整卡垂直居中）
    {
      id: 'in',
      group: 'left-abs',
      args: { x: '0%', y: '50%' },
    },
  ];
  branches.forEach((b, i) => {
    const rowCenter = IF_HEADER_H + i * IF_ROW_H + IF_ROW_H / 2;
    const ratio = rowCenter / height;
    items.push({
      id: `out:${b.id}`,
      group: 'right-abs',
      args: { x: '100%', y: `${(ratio * 100).toFixed(2)}%` },
    });
  });

  node.prop('ports/items', items, { rewrite: true });
}

/** 从输出端口 id 解析分支 id（out:br_1 → br_1）；非输出口返回空 */
export function branchIdFromPort(portId: string | null | undefined): string {
  if (!portId) return '';
  return portId.startsWith('out:') ? portId.slice(4) : '';
}
