/**
 * FOR 循环容器：从框选节点生成虚线容器、成员自适应包裹、解散、嵌套处理。
 *
 * 容器是一个 LOOP_SHAPE 节点（虚线分组框），被圈节点经 X6 embedding 成为其子节点。
 * - createLoopFromSelection：按选中节点包围盒建容器并 addChild，支持嵌套（选中项已
 *   同属某容器时，新容器嵌进该父容器）。
 * - fitLoopToChildren：按直接子节点包围盒重算容器尺寸/位置，并向上递归到祖先容器。
 * - dissolveLoop：解散容器（子节点归还到更外层父或自由态）。
 *
 * 尺寸/坐标只影响画布展示；成员关系由 X6 parent/child 承载，落库时 codec 落 members。
 */
import type { Cell, Graph, Node } from '@antv/x6';

import type { AiFlowApi } from '#/api';

import { LOOP_HEAD_SPACE, LOOP_PADDING, LOOP_SHAPE } from '../constants';
import { defaultLoopConfig } from '../loop-config';

/** 是否 LOOP 容器节点 */
export function isLoopNode(cell?: Cell | null): boolean {
  return Boolean(
    cell?.isNode() &&
      (cell.getData<AiFlowApi.FlowNodeRaw>()?.nodeType === 'LOOP'),
  );
}

/** 求一组节点的包围盒（本地坐标） */
function boundingBox(nodes: Node[]) {
  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  for (const n of nodes) {
    const { x, y } = n.getPosition();
    const { width, height } = n.getSize();
    minX = Math.min(minX, x);
    minY = Math.min(minY, y);
    maxX = Math.max(maxX, x + width);
    maxY = Math.max(maxY, y + height);
  }
  return { minX, minY, maxX, maxY };
}

/**
 * 按直接子节点包围盒重算容器尺寸/位置（外扩 padding + 顶部让出框头），
 * 再向上递归让祖先容器一起自适应。无子节点时不动。
 */
export function fitLoopToChildren(graph: Graph, loop: Node) {
  const children = (loop.getChildren() ?? []).filter((c): c is Node =>
    c.isNode(),
  );
  if (children.length === 0) return;
  const bb = boundingBox(children);
  const x = bb.minX - LOOP_PADDING;
  const y = bb.minY - LOOP_PADDING - LOOP_HEAD_SPACE;
  const width = bb.maxX - bb.minX + LOOP_PADDING * 2;
  const height = bb.maxY - bb.minY + LOOP_PADDING * 2 + LOOP_HEAD_SPACE;
  loop.prop({ position: { x, y }, size: { width, height } }, { silent: false });

  // 向上递归：若本容器也在更外层容器内，外层跟着重算
  const parent = loop.getParent();
  if (parent && parent.isNode() && isLoopNode(parent)) {
    fitLoopToChildren(graph, parent as Node);
  }
}

/**
 * 从当前选中节点创建 FOR 循环容器。
 * 返回新建的容器节点；选中为空则返回 undefined。
 *
 * 嵌套：若所有选中节点同属某个已存在容器 P，则新容器嵌入 P（成为 P 的子、
 * 选中节点的新父）；混选（父不一致）时以「无共同父」处理，建在顶层。
 *
 * @param genNodeCode 供容器取唯一 nodeCode
 */
export function createLoopFromSelection(
  graph: Graph,
  selected: Node[],
  genNodeCode: () => string,
): Node | undefined {
  // 只圈真实业务节点，排除已选中的容器自身（避免把容器当成员）
  const members = selected.filter((n) => !isLoopNode(n));
  if (members.length === 0) return undefined;

  // 共同父容器（嵌套判定）：所有成员的直接父一致且为 LOOP，则新容器嵌进去
  const parents = new Set(members.map((n) => n.getParent()?.id ?? ''));
  const commonParentId = parents.size === 1 ? [...parents][0] : '';
  const commonParent = commonParentId
    ? graph.getCellById(commonParentId)
    : null;
  const nestUnder =
    commonParent && commonParent.isNode() && isLoopNode(commonParent)
      ? (commonParent as Node)
      : null;

  const bb = boundingBox(members);
  const code = genNodeCode();
  const data: AiFlowApi.FlowNodeRaw = {
    nodeCode: code,
    name: '',
    nodeType: 'LOOP',
    outputMode: 'TEXT',
    nodeConfig: { loop: defaultLoopConfig() },
  };
  const loop = graph.addNode({
    id: code,
    shape: LOOP_SHAPE,
    x: bb.minX - LOOP_PADDING,
    y: bb.minY - LOOP_PADDING - LOOP_HEAD_SPACE,
    width: bb.maxX - bb.minX + LOOP_PADDING * 2,
    height: bb.maxY - bb.minY + LOOP_PADDING * 2 + LOOP_HEAD_SPACE,
    data,
    zIndex: 0,
  });

  // 收编成员为子节点
  members.forEach((n) => loop.addChild(n));
  // 嵌套：新容器再挂到共同父容器下
  if (nestUnder) nestUnder.addChild(loop);

  // 容器垫底，成员浮在其上
  loop.toBack();
  fitLoopToChildren(graph, loop);
  if (nestUnder) fitLoopToChildren(graph, nestUnder);
  return loop;
}

/**
 * 解散 FOR 循环容器：把直接子节点从容器移出（若容器本身在更外层容器内，
 * 子节点改挂到外层，保持嵌套一致），再删除容器节点。
 */
export function dissolveLoop(graph: Graph, loop: Node) {
  const outer = loop.getParent();
  const outerLoop =
    outer && outer.isNode() && isLoopNode(outer) ? (outer as Node) : null;
  const children = (loop.getChildren() ?? []).filter((c): c is Node =>
    c.isNode(),
  );
  children.forEach((child) => {
    loop.removeChild(child);
    if (outerLoop) outerLoop.addChild(child);
  });
  loop.remove();
  if (outerLoop) fitLoopToChildren(graph, outerLoop);
}
