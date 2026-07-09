/**
 * 只读回放画布：复用编辑器的形状注册（registerShapes 幂等）与卡片组件，
 * 但不装 Selection/History/Keyboard 等编辑插件，interacting 关死一切
 * 节点/边交互——回放页只看不改，故高亮也无需 withoutHistory 包裹。
 */
import type { Graph as GraphType } from '@antv/x6';

import type { Ref } from 'vue';

import { onBeforeUnmount, shallowRef } from 'vue';

import { Graph } from '@antv/x6';

import { registerShapes } from '../../ai-flow/editor/shapes/registerShapes';

export function useReplayGraph(containerRef: Ref<HTMLElement | undefined>) {
  const graphRef = shallowRef<GraphType>();

  function init(): GraphType | undefined {
    if (!containerRef.value) return undefined;
    registerShapes();
    const g = new Graph({
      autoResize: true,
      background: { color: '#f7f8fa' },
      container: containerRef.value,
      grid: { size: 10, visible: true },
      // 只读：禁节点拖动/连线等交互；平移左右键都放开（无框选竞争）
      interacting: false,
      mousewheel: { enabled: true, modifiers: ['ctrl', 'meta'] },
      panning: {
        enabled: true,
        eventTypes: ['leftMouseDown', 'rightMouseDown'],
      },
    });
    graphRef.value = g;
    return g;
  }

  onBeforeUnmount(() => {
    graphRef.value?.dispose();
    graphRef.value = undefined;
  });

  return { graphRef, init };
}
