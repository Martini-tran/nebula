/**
 * 实例回放状态机：驱动画布按转移历史（transitions）逐步重现执行路径。
 *
 * 步骤模型：steps = transitions 按 seq 升序；「折叠重试」开关打开时滤掉
 * outcome=RETRY 的中间行（与后端"回放时按 outcome 折叠"语义一致）。
 *
 * 染色规则（applyStep(i) 重算 0..i 的累计视图）：
 *   - 当前步 toState → 'current'（失败步 → 'failed'）
 *   - 走过的其余节点 → 'executed'；FAILED 步的 toState 恒 'failed'
 *   - 未访问节点不染色（保持静态主题色，区别于编辑器一次性回放的 skipped 置灰）
 *   - 走过的边加深加粗；节点染色走 paintRunState（node.data.__run_state），
 *     vue-shape 卡片不吃 attrs，绝不 setAttrByPath
 */
import type { Graph as GraphType } from '@antv/x6';

import type { AiAgentApi } from '#/api';

import { computed, onBeforeUnmount, ref } from 'vue';

import { EDGE_COLOR, NODE_SHAPE } from '../../ai-flow/editor/constants';
import { paintRunState } from '../../ai-flow/editor/shapes/registerShapes';

/** 走过的边高亮色（与节点 current 蓝一致） */
const ACTIVE_EDGE_COLOR = '#409eff';

export interface UsePlaybackOptions {
  getGraph: () => GraphType | undefined;
}

export function usePlayback(options: UsePlaybackOptions) {
  const { getGraph } = options;

  const allTransitions = ref<AiAgentApi.AgentTransition[]>([]);
  /** 折叠重试：滤掉 outcome=RETRY 的中间行（默认开） */
  const collapseRetry = ref(true);

  const steps = computed(() => {
    const sorted = [...allTransitions.value].sort(
      (a, b) => (a.seq ?? 0) - (b.seq ?? 0),
    );
    return collapseRetry.value
      ? sorted.filter((t) => t.outcome !== 'RETRY')
      : sorted;
  });

  /** 当前步下标；-1=未开始（全图静态） */
  const cursor = ref(-1);
  const playing = ref(false);
  const speed = ref(1);
  let timer: null | ReturnType<typeof setInterval> = null;

  const activeSeq = computed<null | number>(
    () => steps.value[cursor.value]?.seq ?? null,
  );
  const atEnd = computed(() => cursor.value >= steps.value.length - 1);

  /** 重算并绘制 0..i 的累计视图（i=-1 清空高亮） */
  function applyStep(i: number) {
    const g = getGraph();
    cursor.value = Math.min(Math.max(i, -1), steps.value.length - 1);
    if (!g) return;

    const upto = steps.value.slice(0, cursor.value + 1);
    const visited = new Set<string>();
    const failedNodes = new Set<string>();
    const traversedEdges = new Set<string>();
    for (const t of upto) {
      if (t.fromState) {
        visited.add(t.fromState);
        traversedEdges.add(`${t.fromState}->${t.toState}`);
      }
      if (t.toState) visited.add(t.toState);
      if (t.outcome === 'FAILED' && t.toState) failedNodes.add(t.toState);
    }
    const current = steps.value[cursor.value];
    const currentNode = current?.toState;

    g.getNodes().forEach((node) => {
      // LOOP 容器等非卡片形状不参与运行态染色（沿用 useRunHighlight 的过滤）
      if (node.shape !== NODE_SHAPE) return;
      if (node.id === currentNode) {
        paintRunState(node, current?.outcome === 'FAILED' ? 'failed' : 'current');
      } else if (failedNodes.has(node.id)) {
        paintRunState(node, 'failed');
      } else if (visited.has(node.id)) {
        paintRunState(node, 'executed');
      } else {
        paintRunState(node, null);
      }
    });

    g.getEdges().forEach((edge) => {
      const key = `${edge.getSourceCellId()}->${edge.getTargetCellId()}`;
      // 同一对节点多条边（多 guard 出边）无法从 transition 精确定位是哪条，按端点对整体高亮
      if (traversedEdges.has(key)) {
        edge.attr('line/stroke', ACTIVE_EDGE_COLOR);
        edge.attr('line/strokeWidth', 3);
      } else {
        edge.attr('line/stroke', EDGE_COLOR);
        edge.attr('line/strokeWidth', 2);
      }
    });
  }

  /** 装载转移历史并定位到末步（默认呈现完整路径与当前状态） */
  function setTransitions(transitions: AiAgentApi.AgentTransition[]) {
    allTransitions.value = transitions ?? [];
    applyStep(steps.value.length - 1);
  }

  function next() {
    if (!atEnd.value) applyStep(cursor.value + 1);
    else pause();
  }

  function prev() {
    applyStep(cursor.value - 1);
  }

  function toEnd() {
    pause();
    applyStep(steps.value.length - 1);
  }

  /** 重置到未开始（清空高亮），常接「播放」从头走一遍 */
  function resetToStart() {
    pause();
    applyStep(-1);
  }

  function pause() {
    playing.value = false;
    if (timer) {
      clearInterval(timer);
      timer = null;
    }
  }

  function play() {
    if (steps.value.length === 0) return;
    // 已到末步则从头播
    if (atEnd.value) applyStep(-1);
    playing.value = true;
    startTimer();
  }

  function startTimer() {
    if (timer) clearInterval(timer);
    timer = setInterval(() => {
      if (atEnd.value) {
        pause();
        return;
      }
      applyStep(cursor.value + 1);
    }, 800 / speed.value);
  }

  function setSpeed(v: number) {
    speed.value = v;
    if (playing.value) startTimer();
  }

  /** 折叠开关切换后步骤集变化，重新夹取并重绘 */
  function setCollapseRetry(v: boolean) {
    pause();
    collapseRetry.value = v;
    applyStep(steps.value.length - 1);
  }

  onBeforeUnmount(pause);

  return {
    activeSeq,
    applyStep,
    atEnd,
    collapseRetry,
    cursor,
    next,
    pause,
    play,
    playing,
    prev,
    resetToStart,
    setCollapseRetry,
    setSpeed,
    setTransitions,
    speed,
    steps,
    toEnd,
  };
}
