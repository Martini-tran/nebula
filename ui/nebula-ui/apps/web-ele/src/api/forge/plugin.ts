import { requestClient } from '#/api/request';

/**
 * Forge-插件 API
 * forge 服务 Jackson 已回归默认 camelCase（yml 未配置 SNAKE_CASE），出入参均为 camelCase，本层直接透传。
 */
export namespace ForgePluginApi {
  /** 插件条目（camelCase） */
  export interface PluginItem {
    id: number | string;
    pluginKey: string;
    name: string;
    type?: string;
    summary?: string;
    description?: string;
    keywords?: string;
    iconFileId?: number | string | null;
    coverFileId?: number | string | null;
    authorUserId?: number | string | null;
    authorName?: string;
    homepageUrl?: string;
    repoUrl?: string;
    license?: string;
    pricingType?: number;
    price?: number | string;
    originalPrice?: number | string;
    currency?: string;
    priceText?: string;
    purchaseUrl?: string;
    latestVersionId?: number | string | null;
    latestVersion?: string;
    downloadCount?: number;
    installCount?: number;
    favoriteCount?: number;
    ratingScore?: number | string;
    ratingCount?: number;
    isFeatured?: number;
    sortOrder?: number;
    status?: number;
    remark?: string;
    createTime?: string;
    updateTime?: string;
    categoryIds?: Array<number | string>;
    categoryNames?: string[];
  }

  export interface PluginPageQuery {
    pageNum?: number;
    pageSize?: number;
    keyword?: string;
    status?: number;
    type?: string;
    pricingType?: number;
    isFeatured?: number;
    categoryId?: number | string;
  }

  export interface PluginCreateParams {
    pluginKey: string;
    name: string;
    type?: string;
    summary?: string;
    description?: string;
    keywords?: string;
    iconFileId?: number | string | null;
    coverFileId?: number | string | null;
    authorUserId?: number | string | null;
    authorName?: string;
    homepageUrl?: string;
    repoUrl?: string;
    license?: string;
    pricingType?: number;
    price?: number | string | null;
    originalPrice?: number | string | null;
    currency?: string;
    priceText?: string;
    purchaseUrl?: string;
    isFeatured?: number;
    sortOrder?: number;
    status?: number;
    remark?: string;
    categoryIds?: Array<number | string>;
  }

  export type PluginUpdateParams = Partial<PluginCreateParams>;

  export interface PluginPageResult {
    records: PluginItem[];
    total: number;
    current: number;
    size: number;
  }
}

/** 分页查询插件 */
export async function getForgePluginPageApi(
  params: ForgePluginApi.PluginPageQuery,
) {
  return requestClient.get<ForgePluginApi.PluginPageResult>(
    '/forge/admin/plugins/page',
    { params },
  );
}

/** 插件详情（含已绑定分类） */
export async function getForgePluginDetailApi(id: number | string) {
  return requestClient.get<ForgePluginApi.PluginItem>(
    `/forge/admin/plugins/${id}`,
  );
}

/** 创建插件，返回新建 id */
export async function createForgePluginApi(
  data: ForgePluginApi.PluginCreateParams,
) {
  return requestClient.post<number | string>('/forge/admin/plugins', data);
}

/** 更新插件 */
export async function updateForgePluginApi(
  id: number | string,
  data: ForgePluginApi.PluginUpdateParams,
) {
  return requestClient.put<void>(`/forge/admin/plugins/${id}`, data);
}

/** 更新插件状态（0 草稿 / 1 上架 / 2 下架 / 3 封禁） */
export async function updateForgePluginStatusApi(
  id: number | string,
  status: number,
) {
  return requestClient.put<void>(`/forge/admin/plugins/${id}/status`, undefined, {
    params: { status },
  });
}

/** 更新插件推荐标记（0 否 / 1 是） */
export async function updateForgePluginFeaturedApi(
  id: number | string,
  isFeatured: number,
) {
  return requestClient.put<void>(
    `/forge/admin/plugins/${id}/featured`,
    undefined,
    { params: { isFeatured } },
  );
}

/** 全量重绑插件分类 */
export async function bindForgePluginCategoriesApi(
  id: number | string,
  categoryIds: Array<number | string>,
) {
  return requestClient.put<void>(`/forge/admin/plugins/${id}/categories`, {
    categoryIds,
  });
}

/** 删除插件（存在版本时后端拒绝） */
export async function deleteForgePluginApi(id: number | string) {
  return requestClient.delete<void>(`/forge/admin/plugins/${id}`);
}
