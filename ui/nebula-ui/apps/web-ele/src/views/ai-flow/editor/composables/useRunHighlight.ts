/**
 * 运行结果回放高亮：根据 nodeResults 在画布上标记走过/跳过的节点。
 *
 * 后端 run 是同步一次性返回（非流式），故这里做「运行完成后回放」而非
 * 实时逐节点点亮。nodeResults 的 key 集合 = 本次实际执行的节点。
 *
 * 打高亮会改 node.attr/data，必须在 disableHistory 下进行（withoutHistory），
 * 否则高亮进撤销栈，用户 undo 会撤销「高亮」这一非用户操作。
 */
import type { Graph as GraphType } from '@antv/x6';

import type { AiFlowApi } from '#/api';

import { NODE_SHAPE } from '../constants';
import { paintRunState, refreshNodeCard } from '../shapes/registerShapes';

export interface UseRunHighlightOptions {
  getGraph: () => GraphType | undefined;
  /** 在禁用历史下执行（来自 useFlowGraph） */
  withoutHistory: (fn: () => void) => void;
}

export function useRunHighlight(options: UseRunHighlightOptions) {
  const { getGraph, withoutHistory } = options;

  /** 根据运行结果打高亮：命中 nodeResults → executed，未命中 → skipped */
  function applyRunResult(result: AiFlowApi.FlowRunResultRaw) {
    const g = getGraph();
    if (!g) return;
    const ran = new Set(Object.keys(result.nodeResults ?? {}));
    withoutHistory(() => {
      g.getNodes().forEach((node) => {
        if (node.shape !== NODE_SHAPE) return;
        paintRunState(node, ran.has(node.id) ? 'executed' : 'skipped');
      });
    });
  }

  /** 清除全部运行态高亮，复原静态外观 */
  function clearHighlight() {
    const g = getGraph();
    if (!g) return;
    withoutHistory(() => {
      g.getNodes().forEach((node) => {
        if (node.shape !== NODE_SHAPE) return;
        paintRunState(node, null);
        refreshNodeCard(node);
      });
    });
  }

  return { applyRunResult, clearHighlight };
}
