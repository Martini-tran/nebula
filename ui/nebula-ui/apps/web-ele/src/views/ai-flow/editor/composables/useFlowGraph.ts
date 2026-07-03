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

// X6 3.x：插件已并入主包 @antv/x6，不再从独立 @antv/x6-plugin-* 导入
import { Graph, History, Keyboard, MiniMap, Selection, Snapline } from '@antv/x6';

import {
  EDGE_SHAPE,
  PORT_COLOR_CONNECTED,
  PORT_COLOR_IDLE,
} from '../constants';
import { registerShapes } from '../shapes/registerShapes';
import { fitLoopToChildren, isLoopNode } from './useLoopGroup';

// ---------------- 端口交互（对齐官方 AgentFlow）----------------
// 端口默认隐藏；hover 节点时全显，离开时仅保留已连接端口。已连=蓝、未连=灰。

function isPortConnected(g: GraphType, node: Node, portId: string): boolean {
  return g.getConnectedEdges(node).some(
    (e) =>
      (e.getSourceCellId() === node.id && e.getSourcePortId() === portId) ||
      (e.getTargetCellId() === node.id && e.getTargetPortId() === portId),
  );
}

function setPortVisible(node: Node, portId: string, visible: boolean) {
  node.setPortProp(
    portId,
    'attrs/circle/style/visibility',
    visible ? 'visible' : 'hidden',
  );
}

function setPortColor(node: Node, portId: string, color: string) {
  node.setPortProp(portId, 'attrs/circle/fill', color);
  node.setPortProp(portId, 'attrs/circle/stroke', color);
}

/** 显隐节点全部端口：show=false 时仅保留已连端口并按连接态着色 */
function showNodePorts(g: GraphType, node: Node, show: boolean) {
  node.getPorts().forEach((p) => {
    const id = p.id as string;
    if (show) {
      setPortVisible(node, id, true);
    } else {
      const connected = isPortConnected(g, node, id);
      setPortVisible(node, id, connected);
      setPortColor(node, id, connected ? PORT_COLOR_CONNECTED : PORT_COLOR_IDLE);
    }
  });
}

function withNodePort(
  g: GraphType,
  cellId: null | string | undefined,
  portId: null | string | undefined,
  fn: (node: Node, portId: string) => void,
) {
  if (!cellId || !portId) return;
  const cell = g.getCellById(cellId);
  if (cell?.isNode()) fn(cell as Node, portId);
}

/** 绑定端口显隐/连接态 + 边删除按钮的图事件 */
function setupPortInteractions(g: GraphType) {
  g.on('node:mouseenter', ({ node }) => showNodePorts(g, node, true));
  g.on('node:mouseleave', ({ node }) => showNodePorts(g, node, false));

  // 边 hover 出删除按钮
  g.on('edge:mouseenter', ({ edge }) => {
    edge.addTools({ name: 'button-remove', args: { distance: -30 } });
  });
  g.on('edge:mouseleave', ({ edge }) => edge.removeTools());

  // 连线后：两端端口变蓝点常显
  const markPortConnected = (
    cellId?: null | string,
    portId?: null | string,
  ) => {
    withNodePort(g, cellId, portId, (node, id) => {
      setPortVisible(node, id, true);
      setPortColor(node, id, PORT_COLOR_CONNECTED);
    });
  };
  g.on('edge:added', ({ edge }) => {
    markPortConnected(edge.getSourceCellId(), edge.getSourcePortId());
    markPortConnected(edge.getTargetCellId(), edge.getTargetPortId());
  });
  // 删边后：若端口不再有连线则复原为隐藏灰点
  const resetPortIfIdle = (cellId?: null | string, portId?: null | string) => {
    withNodePort(g, cellId, portId, (node, id) => {
      if (!isPortConnected(g, node, id)) {
        setPortVisible(node, id, false);
        setPortColor(node, id, PORT_COLOR_IDLE);
      }
    });
  };
  g.on('edge:removed', ({ edge }) => {
    resetPortIfIdle(edge.getSourceCellId(), edge.getSourcePortId());
    resetPortIfIdle(edge.getTargetCellId(), edge.getTargetPortId());
  });
}

export interface UseFlowGraphOptions {
  containerRef: Ref<HTMLDivElement | undefined>;
  minimapRef: Ref<HTMLDivElement | undefined>;
  /** 单选节点回调（打开节点属性面板） */
  onSelectNode?: (node: Node) => void;
  /** 单选边回调（打开边属性面板） */
  onSelectEdge?: (edge: Edge) => void;
  /** 清空选择回调（关闭属性面板） */
  onClearSelection?: () => void;
  /** 开始节点右键菜单项点击（编辑器主页面按 key 分发到对应弹窗/提示） */
  onStartMenu?: (node: Node, key: string, label: string) => void;
  /**
   * 空白处右键（带屏幕坐标 + 当前选中节点）：编辑器据此决定是否弹「For 循环」菜单。
   * 有 ≥1 选中业务节点时弹菜单圈成循环，否则由 panning 平移处理。
   */
  onBlankContextMenu?: (
    pos: { x: number; y: number },
    selected: Node[],
  ) => void;
}

export function useFlowGraph(options: UseFlowGraphOptions) {
  const {
    containerRef,
    minimapRef,
    onBlankContextMenu,
    onClearSelection,
    onSelectEdge,
    onSelectNode,
    onStartMenu,
  } = options;

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
        allowEdge: false,
        // 对齐官方：平滑曲线 + 端口锚点吸附
        connector: { name: 'smooth' },
        connectionPoint: 'anchor',
        snap: { radius: 20 },
        highlight: true,
        createEdge() {
          return this.createEdge({ shape: EDGE_SHAPE });
        },
        validateConnection({ sourceMagnet, targetMagnet }) {
          // 必须从端口连到端口
          return Boolean(sourceMagnet) && Boolean(targetMagnet);
        },
      },
      highlighting: {
        magnetAdsorbed: {
          name: 'stroke',
          args: {
            attrs: { fill: PORT_COLOR_CONNECTED, stroke: PORT_COLOR_CONNECTED },
          },
        },
      },
      // FOR 循环容器：允许把节点拖入/拖出容器（embedding 父子关系）。
      // frontOnly:false 支持多层嵌套；findParent 只认 LOOP 容器为父，避免普通节点相互吞并。
      embedding: {
        enabled: true,
        frontOnly: false,
        findParent({ node }) {
          const bbox = node.getBBox();
          return this.getNodes().filter((candidate) => {
            if (candidate.id === node.id) return false;
            if (candidate.getData()?.nodeType !== 'LOOP') return false;
            const target = candidate.getBBox();
            return target.isIntersectWithRect(bbox);
          });
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

    // ---- 开始节点右键菜单 → 编辑器主页面分发（卡片经 graph.trigger 抛出）----
    g.on(
      'start:menu',
      ({ key, label, node }: { key: string; label: string; node: Node }) => {
        onStartMenu?.(node, key, label);
      },
    );

    // ---- 端口交互（hover 显隐 / 连接态 / 边删除按钮）----
    setupPortInteractions(g);

    // ---- FOR 循环：子节点移动/缩放时，父容器自适应包裹（向上递归到祖先）----
    const refitParents = (node: Node) => {
      const parent = node.getParent();
      if (parent && parent.isNode() && isLoopNode(parent)) {
        fitLoopToChildren(g, parent as Node);
      }
    };
    g.on('node:moved', ({ node }) => refitParents(node));
    g.on('node:resized', ({ node }) => refitParents(node));
    // 拖入/拖出容器（embedding 变更父子）后，新旧父都重算
    g.on('node:change:parent', ({ node, previous }) => {
      refitParents(node);
      if (previous) {
        const prev = g.getCellById(previous as string);
        if (prev?.isNode() && isLoopNode(prev)) fitLoopToChildren(g, prev as Node);
      }
    });

    // ---- 空白右键：有选中节点时交编辑器弹「For 循环」菜单，否则放行给 panning ----
    g.on('blank:contextmenu', ({ e }) => {
      const selected = g
        .getSelectedCells()
        .filter((c): c is Node => c.isNode() && !isLoopNode(c));
      if (selected.length === 0) return;
      // 有选中业务节点：阻止默认（平移/浏览器菜单），弹自定义菜单
      e.preventDefault?.();
      onBlankContextMenu?.({ x: e.clientX, y: e.clientY }, selected);
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
