import { describe, expect, it } from 'vitest';

import { flowToGraph, NODE_HEIGHT, NODE_WIDTH } from './codec';

function node(nodeCode: string, x6?: { x: number; y: number }) {
  return {
    nodeCode,
    nodeType: 'TOOL',
    nodeConfig: x6 ? { __x6: x6 } : {},
  };
}

describe('flow editor fallback layout', () => {
  it('leaves enough horizontal and vertical space between generated nodes', () => {
    const graph = flowToGraph({
      flowCode: 'layout-test',
      engineType: 'DAG',
      nodes: [node('node_1'), node('node_2'), node('node_3'), node('node_4')],
      edges: [],
    });

    expect(graph.nodes[1]!.x - graph.nodes[0]!.x - NODE_WIDTH).toBe(120);
    expect(graph.nodes[3]!.y - graph.nodes[0]!.y - NODE_HEIGHT).toBe(96);
  });

  it('preserves coordinates saved by the user', () => {
    const graph = flowToGraph({
      flowCode: 'saved-layout-test',
      engineType: 'DAG',
      nodes: [node('node_1', { x: 45, y: 75 })],
      edges: [],
    });

    expect(graph.nodes[0]).toMatchObject({ x: 45, y: 75 });
  });
});
