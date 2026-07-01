/**
 * 流程持久化 composable：封装 图↔定义 的构建、加载回显、保存、运行、续跑。
 *
 * 从 index.vue 抽出（原 L400-478），使壳组件只负责编排。图实例由外部
 * 通过 getGraph() 提供（useFlowGraph 持有），本 composable 不直接创建图。
 */
import type { Graph as GraphType } from '@antv/x6';

import type { FlowMeta, X6GraphJson } from '../codec';

import type { AiFlowApi } from '#/api';

import {
  getFlowDetailApi,
  resumeFlowApi,
  runFlowApi,
  saveFlowApi,
} from '#/api';

import { EDGE_SHAPE, graphToFlow, NODE_HEIGHT, NODE_SHAPE, NODE_WIDTH } from '../codec';

export interface UseFlowPersistenceOptions {
  /** 返回当前 X6 图实例（可能尚未初始化） */
  getGraph: () => GraphType | undefined;
  /** 头部元信息（响应式） */
  meta: FlowMeta;
}

export function useFlowPersistence(options: UseFlowPersistenceOptions) {
  const { getGraph, meta } = options;

  /** 从当前图构建可落库的流程定义 */
  function buildDefinition(): AiFlowApi.FlowDefinitionRaw {
    const g = getGraph();
    const json = (g ? g.toJSON() : { cells: [] }) as any;
    const cells: any[] = json.cells ?? [];
    const nodes = cells.filter((c) => c.shape === NODE_SHAPE);
    const edges = cells.filter((c) => c.shape === EDGE_SHAPE);
    const graphJson: X6GraphJson = {
      nodes: nodes.map((n) => ({
        id: n.id,
        shape: n.shape,
        x: n.position?.x ?? 0,
        y: n.position?.y ?? 0,
        width: n.size?.width ?? NODE_WIDTH,
        height: n.size?.height ?? NODE_HEIGHT,
        data: n.data ?? {},
      })),
      edges: edges.map((e) => ({
        id: e.id,
        shape: e.shape,
        source: { cell: e.source?.cell },
        target: { cell: e.target?.cell },
        data: e.data ?? {},
      })),
    };
    return graphToFlow(graphJson, { ...meta });
  }

  /** 拉取流程定义（供画布回显），返回原始定义 */
  async function loadDefinition(flowCode: string) {
    return getFlowDetailApi(flowCode);
  }

  /** 保存整图 */
  async function save() {
    return saveFlowApi(buildDefinition());
  }

  /** 运行（调用方决定是否先 save） */
  async function run(input: Record<string, any>) {
    return runFlowApi(meta.flow_code, { input });
  }

  /** 从断点续跑 */
  async function resume(runId: string) {
    return resumeFlowApi(runId);
  }

  return { buildDefinition, loadDefinition, resume, run, save };
}

/** 便于外部按需引用（避免深层解构） */
export type FlowPersistence = ReturnType<typeof useFlowPersistence>;
