import { requestClient } from '#/api/request';

/**
 * Forge-插件评价 API
 * forge 服务 Jackson 已回归默认 camelCase（yml 未配置 SNAKE_CASE），出入参均为 camelCase，本层直接透传。
 */
export namespace ForgeReviewApi {
  /** 评价条目（camelCase） */
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

/** 分页查询评价 */
export async function getForgeReviewPageApi(
  params: ForgeReviewApi.ReviewPageQuery,
) {
  return requestClient.get<ForgeReviewApi.ReviewPageResult>(
    '/forge/admin/plugin-reviews/page',
    { params },
  );
}

/** 评价详情 */
export async function getForgeReviewDetailApi(id: number | string) {
  return requestClient.get<ForgeReviewApi.ReviewItem>(
    `/forge/admin/plugin-reviews/${id}`,
  );
}

/** 审核评价（status：0 待审 / 1 展示 / 2 隐藏 / 3 拒绝） */
export async function auditForgeReviewApi(
  id: number | string,
  data: { status: number; auditRemark?: string },
) {
  return requestClient.put<void>(`/forge/admin/plugin-reviews/${id}/audit`, data);
}

/** 回复评价 */
export async function replyForgeReviewApi(
  id: number | string,
  replyContent: string,
) {
  return requestClient.put<void>(`/forge/admin/plugin-reviews/${id}/reply`, {
    replyContent,
  });
}

/** 删除评价（物理删除） */
export async function deleteForgeReviewApi(id: number | string) {
  return requestClient.delete<void>(`/forge/admin/plugin-reviews/${id}`);
}
