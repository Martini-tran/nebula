/**
 * 按 agentCode 解析 Agent 定义详情。
 *
 * 详情端点走主键 id，但运行入口 / AGENT 节点回显场景手里常常只有 agentCode
 * （画布 nodeConfig.refAgentCode、选择器 allow-create 手输值）。此处用分页
 * 列表按 keyword 检索后精确匹配 agentCode，同 code 多版本时取 version 最大
 * 的一行，再拉详情。匹配不到（code 不存在 / 手输兜底值）返回 undefined。
 */
import type { AiAgentApi } from '#/api';

import { getAgentDetailApi, getAgentPageApi } from '#/api';

export async function fetchAgentDetailByCode(
  code: string,
): Promise<AiAgentApi.AgentDetail | undefined> {
  const trimmed = (code ?? '').trim();
  if (!trimmed) return undefined;

  const res = await getAgentPageApi({
    keyword: trimmed,
    pageNum: 1,
    pageSize: 50,
  });
  const rows = (res.records ?? []).filter((r) => r.agentCode === trimmed);
  if (rows.length === 0) return undefined;

  const latest = rows.reduce((a, b) =>
    (b.version ?? 0) > (a.version ?? 0) ? b : a,
  );
  return await getAgentDetailApi(latest.id);
}
