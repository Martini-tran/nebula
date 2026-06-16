import { requestClient } from '#/api/request';

export namespace ForgeReviewApi {
  /** 后端 SNAKE_CASE 序列化的原始评价结构 */
  export interface ReviewItemRaw {
    id: number | string;
    plugin_id: number | string;
    version_id?: number | string | null;
    user_id: number | string;
    rating?: number;
    content?: string;
    reply_content?: string;
    reply_by?: number | string | null;
    reply_time?: string;
    like_count?: number;
    status?: number;
    audit_remark?: string;
    create_time?: string;
    update_time?: string;
  }

  /** 规范化后的评价条目（camelCase） */
  export interface ReviewItem {
    id: number | string;
    pluginId: number | string;
    versionId?: number | string | null;
    userId: number | string;
    rating?: number;
    content?: string;
    replyContent?: string;
    replyBy?: number | string | null;
    replyTime?: string;
    likeCount?: number;
    status?: number;
    auditRemark?: string;
    createTime?: string;
    updateTime?: string;
  }

  export interface ReviewPageQuery {
    pageNum?: number;
    pageSize?: number;
    pluginId?: number | string;
    userId?: number | string;
    status?: number;
    rating?: number;
  }

  export interface ReviewPageResult {
    records: ReviewItem[];
    total: number;
    current: number;
    size: number;
  }
}

function normalizeReview(
  raw: ForgeReviewApi.ReviewItemRaw,
): ForgeReviewApi.ReviewItem {
  return {
    id: raw.id,
    pluginId: raw.plugin_id,
    versionId: raw.version_id ?? null,
    userId: raw.user_id,
    rating: raw.rating,
    content: raw.content,
    replyContent: raw.reply_content,
    replyBy: raw.reply_by ?? null,
    replyTime: raw.reply_time,
    likeCount: raw.like_count ?? 0,
    status: raw.status ?? 0,
    auditRemark: raw.audit_remark,
    createTime: raw.create_time,
    updateTime: raw.update_time,
  };
}

/** 分页查询评价 */
export async function getForgeReviewPageApi(
  params: ForgeReviewApi.ReviewPageQuery,
) {
  const result = await requestClient.get<{
    records: ForgeReviewApi.ReviewItemRaw[];
    total: number;
    current: number;
    size: number;
  }>('/forge/admin/plugin-reviews/page', { params });
  return {
    ...result,
    records: (result.records ?? []).map(normalizeReview),
  } as ForgeReviewApi.ReviewPageResult;
}

/** 评价详情 */
export async function getForgeReviewDetailApi(id: number | string) {
  const raw = await requestClient.get<ForgeReviewApi.ReviewItemRaw>(
    `/forge/admin/plugin-reviews/${id}`,
  );
  return normalizeReview(raw);
}

/** 审核评价（status：0 待审 / 1 展示 / 2 隐藏 / 3 拒绝） */
export async function auditForgeReviewApi(
  id: number | string,
  data: { status: number; auditRemark?: string },
) {
  return requestClient.put<void>(`/forge/admin/plugin-reviews/${id}/audit`, {
    status: data.status,
    audit_remark: data.auditRemark,
  });
}

/** 回复评价 */
export async function replyForgeReviewApi(
  id: number | string,
  replyContent: string,
) {
  return requestClient.put<void>(`/forge/admin/plugin-reviews/${id}/reply`, {
    reply_content: replyContent,
  });
}

/** 删除评价（物理删除） */
export async function deleteForgeReviewApi(id: number | string) {
  return requestClient.delete<void>(`/forge/admin/plugin-reviews/${id}`);
}
