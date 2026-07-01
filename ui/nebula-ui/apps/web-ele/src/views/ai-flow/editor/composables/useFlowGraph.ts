/**
 * X6 图初始化 + 官方插件接入 + 交互桥接。
 *
 * 用官方插件替换原 index.vue 手搓的 lastClickedCellId + window keydown：
 * - Selection：框选/多选（rubberband 走左键空白，panning 保留右键）
 * - Keyboard：delete/backspace 删选中、ctrl+c/v 复制粘贴、ctrl+z/y 撤销重做
 * - History：撤销重做栈（回显/高亮需 disable 包裹，防止污染栈）
 * - Snapline：拖拽对齐线
 * - MiniMap：小地图（需容器布局完成后再 use）
 *
 * 选择以 selection:changed 为单一事实源：单选 node/edge 触发对应回调，
 * 多选/空选触发清空回调。
 */
import type { Cell, Edge, Graph as GraphType, Node } from '@antv/x6';

import type { Ref } from 'vue';

import { onBeforeUnmount, ref, shallowRef } from 'vue';

import { Graph } from '@antv/x6';
import { History } from '@antv/x6-plugin-history';
import { Keyboard } from '@antv/x6-plugin-keyboard';
import { MiniMap } from '@antv/x6-plugin-minimap';
import { Selection } from '@antv/x6-plugin-selection';
import { Snapline } from '@antv/x6-plugin-snapline';

import { EDGE_SHAPE } from '../constants';
import { registerShapes } from '../shapes/registerShapes';

export interface UseFlowGraphOptions {
  containerRef: Ref<HTMLDivElement | undefined>;
  minimapRef: Ref<HTMLDivElement | undefined>;
  /** 单选节点回调（打开节点属性面板） */
  onSelectNode?: (node: Node) => void;
  /** 单选边回调（打开边属性面板） */
  onSelectEdge?: (edge: Edge) => void;
  /** 清空选择回调（关闭属性面板） */
  onClearSelection?: () => void;
}

export function useFlowGraph(options: UseFlowGraphOptions) {
  const { containerRef, minimapRef, onClearSelection, onSelectEdge, onSelectNode } =
    options;

  const graph = shallowRef<GraphType>();
  const canUndo = ref(false);
  const canRedo = ref(false);

  /** 初始化图 + 插件（需容器已挂载） */
  function init() {
    if (!containerRef.value) return;
    registerShapes();

    const g = new Graph({
      container: containerRef.value,
      autoResize: true,
      background: { color: '#f7f8fa' },
      grid: { size: 10, visible: true },
      // 右键平移，左键留给框选
      panning: { enabled: true, eventTypes: ['rightMouseDown'] },
      mousewheel: { enabled: true, modifiers: ['ctrl', 'meta'] },
      connecting: {
        allowBlank: false,
        allowLoop: false,
        allowMulti: false,
        router: 'manhattan',
        connector: 'rounded',
        snap: true,
        createEdge() {
          return this.createEdge({ shape: EDGE_SHAPE });
        },
        validateConnection({ sourceCell, targetCell }) {
          return (
            Boolean(sourceCell) &&
            Boolean(targetCell) &&
            sourceCell !== targetCell
          );
        },
      },
      highlighting: {
        magnetAdsorbed: {
          name: 'stroke',
          args: { attrs: { stroke: '#409eff', strokeWidth: 2 } },
        },
      },
    });

    // ---- 插件 ----
    g.use(
      new Selection({
        enabled: true,
        multiple: true,
        rubberband: true,
        showNodeSelectionBox: true,
        showEdgeSelectionBox: true,
        movable: true,
        // 框选只作用于节点，避免误选边框
        filter(cell: Cell) {
          return cell.isNode();
        },
      }),
    );
    g.use(new Keyboard({ enabled: true, global: false }));
    g.use(new History({ enabled: true }));
    g.use(new Snapline({ enabled: true, sharp: true }));

    // ---- 快捷键 ----
    g.bindKey(['delete', 'backspace'], () => {
      const cells = g.getSelectedCells();
      if (cells.length > 0) g.removeCells(cells);
      return false;
    });
    g.bindKey(['ctrl+c', 'meta+c'], () => {
      const cells = g.getSelectedCells();
      if (cells.length > 0) g.copy(cells);
      return false;
    });
    g.bindKey(['ctrl+v', 'meta+v'], () => {
      if (!g.isClipboardEmpty()) {
        const pasted = g.paste({ offset: 32 });
        g.cleanSelection();
        g.select(pasted);
      }
      return false;
    });
    g.bindKey(['ctrl+z', 'meta+z'], () => {
      if (g.canUndo()) g.undo();
      return false;
    });
    g.bindKey(['ctrl+shift+z', 'meta+shift+z', 'ctrl+y'], () => {
      if (g.canRedo()) g.redo();
      return false;
    });

    // ---- 选择 → 属性面板（单一事实源）----
    g.on('selection:changed', () => {
      const cells = g.getSelectedCells();
      if (cells.length !== 1) {
        onClearSelection?.();
        return;
      }
      const cell = cells[0]!;
      if (cell.isNode()) onSelectNode?.(cell as Node);
      else if (cell.isEdge()) onSelectEdge?.(cell as Edge);
    });

    // ---- 撤销重做按钮态 ----
    const syncHistory = () => {
      canUndo.value = g.canUndo();
      canRedo.value = g.canRedo();
    };
    g.on('history:change', syncHistory);
    syncHistory();

    graph.value = g;
  }

  /** 挂载小地图（容器布局完成后调用） */
  function mountMinimap() {
    const g = graph.value;
    if (!g || !minimapRef.value) return;
    g.use(
      new MiniMap({
        container: minimapRef.value,
        width: 180,
        height: 120,
        padding: 8,
      }),
    );
  }

  /**
   * 在禁用历史记录的情况下执行操作（回显加载、高亮绘制用），
   * 避免这些非用户操作污染撤销栈导致 undo 清空画布。
   */
  function withoutHistory(fn: () => void) {
    const g = graph.value;
    if (!g) return;
    g.disableHistory();
    try {
      fn();
    } finally {
      g.enableHistory();
    }
  }

  function undo() {
    graph.value?.undo();
  }
  function redo() {
    graph.value?.redo();
  }

  onBeforeUnmount(() => {
    graph.value?.dispose();
  });

  return {
    graph,
    canUndo,
    canRedo,
    init,
    mountMinimap,
    redo,
    undo,
    withoutHistory,
  };
}
