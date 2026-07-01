/**
 * X6 自定义节点/边形状注册（X6 3.x + vue-shape）。
 *
 * 节点改用 Vue 组件（FlowNodeCard.vue）渲染，替代原手绘 markup：卡片按 nodeType
 * 区分模型/工具样式，并展示模型档案、MCP 关联、输出键等摘要。组件内部监听
 * node 的 change:data 自刷新，故本文件的 refreshNodeCard/paintRunState 只需驱动
 * node.data，不再直接 setAttrByPath。
 *
 * 运行态用 node.data.__run_state 承载：组件读它渲染边框配色；codec 落库前会剔除。
 */
import type { Node } from '@antv/x6';

import type { RunState } from '../constants';

import type { AiFlowApi } from '#/api';

import { Graph } from '@antv/x6';
import { register } from '@antv/x6-vue-shape';

import {
  EDGE_COLOR,
  EDGE_SHAPE,
  NODE_HEIGHT,
  NODE_SHAPE,
  NODE_WIDTH,
  PORT_COLOR_IDLE,
} from '../constants';
import FlowNodeCard from './FlowNodeCard.vue';

let registered = false;

/** 四向端口基础样式（默认隐藏，hover/连接态由 useFlowGraph 图事件控制显隐与配色） */
const basePortCircle = {
  r: 3,
  magnet: true,
  stroke: PORT_COLOR_IDLE,
  strokeWidth: 1,
  fill: PORT_COLOR_IDLE,
  style: { visibility: 'hidden' },
};

const portGroup = (position: 'bottom' | 'left' | 'right' | 'top') => ({
  position,
  attrs: { circle: { ...basePortCircle } },
});

/** 四向端口配置（对齐官方：top/right/bottom/left） */
export const NODE_PORTS = {
  groups: {
    top: portGroup('top'),
    right: portGroup('right'),
    bottom: portGroup('bottom'),
    left: portGroup('left'),
  },
  items: [
    { id: 'top', group: 'top' },
    { id: 'right', group: 'right' },
    { id: 'bottom', group: 'bottom' },
    { id: 'left', group: 'left' },
  ],
};

/** 注册一次自定义节点/边形状（多次调用幂等） */
export function registerShapes() {
  if (registered) return;
  registered = true;

  register({
    shape: NODE_SHAPE,
    width: NODE_WIDTH,
    height: NODE_HEIGHT,
    component: FlowNodeCard,
    ports: { ...NODE_PORTS },
  });

  Graph.registerEdge(
    EDGE_SHAPE,
    {
      inherit: 'edge',
      attrs: {
        line: {
          stroke: EDGE_COLOR,
          strokeWidth: 2,
          targetMarker: { name: 'block', size: 8 },
        },
      },
    },
    true,
  );
}

/**
 * 刷新节点卡片：Vue 组件通过 change:data 自动重渲染，这里只需触发一次 data 重设。
 * 保留导出以兼容既有调用方（回显、清除运行态后复原）。
 */
export function refreshNodeCard(node: Node) {
  const data = node.getData<AiFlowApi.FlowNodeRaw>() ?? {};
  // 浅拷贝重设，触发 change:data 让卡片组件重算摘要
  node.setData({ ...data }, { overwrite: true });
}

/**
 * 绘制/清除节点运行态。写入/移除 node.data.__run_state，卡片组件据此渲染边框配色。
 * state 为 null 时清除高亮。
 */
export function paintRunState(node: Node, state: null | RunState) {
  const data =
    node.getData<AiFlowApi.FlowNodeRaw & { __run_state?: RunState }>() ?? {};

  if (state) {
    node.setData({ ...data, __run_state: state }, { overwrite: true });
  } else {
    const next = { ...data };
    delete next.__run_state;
    node.setData(next, { overwrite: true });
  }
}
