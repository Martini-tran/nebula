/**
 * X6 自定义节点/边形状注册 + 运行态绘制。
 *
 * 节点采用富卡片 markup（比裸 rect 现代）：
 *   body(圆角状态边框) · bar(左侧类型色条) · label(节点名) ·
 *   typeBadge(类型短标) · runBadge(运行态圆点) · runBadgeText(✓/○/✕)
 *
 * 运行态用 node.data.__run_state + attr 双写：attr 负责渲染，data 供 toJSON 幂等
 * （codec 落库前会剔除 __run_state）。
 */
import type { Node } from '@antv/x6';

import type { RunState } from '../constants';

import type { AiFlowApi } from '#/api';

import { Graph } from '@antv/x6';

import {
  EDGE_SHAPE,
  NODE_HEIGHT,
  NODE_SHAPE,
  NODE_WIDTH,
  nodeMetaOf,
  RUN_STATE_COLOR,
  RUN_STATE_ICON,
} from '../constants';

let registered = false;

/** 注册一次自定义节点/边形状（多次调用幂等） */
export function registerShapes() {
  if (registered) return;
  registered = true;

  Graph.registerNode(
    NODE_SHAPE,
    {
      width: NODE_WIDTH,
      height: NODE_HEIGHT,
      markup: [
        { tagName: 'rect', selector: 'body' },
        { tagName: 'rect', selector: 'bar' },
        { tagName: 'text', selector: 'label' },
        { tagName: 'text', selector: 'typeBadge' },
        { tagName: 'circle', selector: 'runBadge' },
        { tagName: 'text', selector: 'runBadgeText' },
      ],
      attrs: {
        body: {
          rx: 8,
          ry: 8,
          fill: '#ffffff',
          stroke: '#409eff',
          strokeWidth: 1.5,
          // 轻微阴影感
          filter: {
            name: 'dropShadow',
            args: { dx: 0, dy: 1, blur: 3, color: 'rgba(0,0,0,0.12)' },
          },
        },
        bar: {
          x: 0,
          y: 0,
          width: 5,
          height: NODE_HEIGHT,
          rx: 2,
          ry: 2,
          fill: '#409eff',
        },
        label: {
          refX: 18,
          refY: 0.42,
          refX2: 0,
          textAnchor: 'start',
          textVerticalAnchor: 'middle',
          fontSize: 14,
          fontWeight: 600,
          fill: '#303133',
          textWrap: { width: NODE_WIDTH - 40, height: 20, ellipsis: true },
        },
        typeBadge: {
          refX: 18,
          refY: 0.74,
          textAnchor: 'start',
          textVerticalAnchor: 'middle',
          fontSize: 11,
          fill: '#909399',
        },
        runBadge: {
          r: 8,
          refX: NODE_WIDTH - 16,
          refY: 16,
          fill: 'transparent',
          stroke: 'none',
        },
        runBadgeText: {
          refX: NODE_WIDTH - 16,
          refY: 16,
          textAnchor: 'middle',
          textVerticalAnchor: 'middle',
          fontSize: 11,
          fontWeight: 700,
          fill: '#ffffff',
          text: '',
        },
      },
      ports: {
        groups: {
          in: {
            position: 'top',
            attrs: {
              circle: {
                r: 4,
                magnet: true,
                stroke: '#409eff',
                strokeWidth: 1,
                fill: '#fff',
              },
            },
          },
          out: {
            position: 'bottom',
            attrs: {
              circle: {
                r: 4,
                magnet: true,
                stroke: '#409eff',
                strokeWidth: 1,
                fill: '#fff',
              },
            },
          },
        },
        items: [
          { id: 'in', group: 'in' },
          { id: 'out', group: 'out' },
        ],
      },
    },
    true,
  );

  Graph.registerEdge(
    EDGE_SHAPE,
    {
      attrs: {
        line: {
          stroke: '#a0a0a0',
          strokeWidth: 1.5,
          targetMarker: { name: 'block', size: 8 },
        },
      },
    },
    true,
  );
}

/**
 * 用节点 data 刷新节点卡片：名称、类型徽标、类型主题色（无运行态时的静态外观）。
 * 若节点当前带运行态，主题色让位给运行态色（见 paintRunState）。
 */
export function refreshNodeCard(node: Node) {
  const data =
    node.getData<AiFlowApi.FlowNodeRaw & { __run_state?: RunState }>() ??
    ({} as AiFlowApi.FlowNodeRaw);
  const meta = nodeMetaOf(data.nodeType);

  node.setAttrByPath('label/text', data.name || node.id);
  node.setAttrByPath('typeBadge/text', meta.badge);

  // 无运行态时用类型主题色
  if (!data.__run_state) {
    node.setAttrByPath('body/stroke', meta.color);
    node.setAttrByPath('bar/fill', meta.color);
    node.setAttrByPath('body/opacity', 1);
  }
}

/**
 * 绘制/清除节点运行态。state 为 null 时清除高亮、复原类型主题色。
 */
export function paintRunState(node: Node, state: null | RunState) {
  const data =
    node.getData<AiFlowApi.FlowNodeRaw & { __run_state?: RunState }>() ??
    ({} as AiFlowApi.FlowNodeRaw);

  if (state) {
    node.setData({ __run_state: state }, { deep: false });
    const color = RUN_STATE_COLOR[state];
    node.setAttrByPath('body/stroke', color);
    node.setAttrByPath('bar/fill', color);
    node.setAttrByPath('body/opacity', state === 'skipped' ? 0.5 : 1);
    node.setAttrByPath('runBadge/fill', color);
    node.setAttrByPath('runBadgeText/text', RUN_STATE_ICON[state]);
  } else {
    // 清除运行态：从 data 移除并复原静态外观
    const next = { ...data };
    delete next.__run_state;
    node.setData(next, { overwrite: true });
    node.setAttrByPath('runBadge/fill', 'transparent');
    node.setAttrByPath('runBadgeText/text', '');
    refreshNodeCard(node);
  }
}
