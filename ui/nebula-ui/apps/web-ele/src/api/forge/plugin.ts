import { requestClient } from '#/api/request';

export namespace ForgePluginApi {
  /** 后端 SNAKE_CASE 序列化的原始结构，仅内部规范化使用 */
  export interface PluginItemRaw {
    id: number | string;
    plugin_key: string;
    name: string;
    type?: string;
    summary?: string;
    description?: string;
    keywords?: string;
    icon_file_id?: number | string | null;
    cover_file_id?: number | string | null;
    author_user_id?: number | string | null;
    author_name?: string;
    homepage_url?: string;
    repo_url?: string;
    license?: string;
    pricing_type?: number;
    price?: number | string;
    original_price?: number | string;
    currency?: string;
    price_text?: string;
    purchase_url?: string;
    latest_version_id?: number | string | null;
    latest_version?: string;
    download_count?: number;
    install_count?: number;
    favorite_count?: number;
    rating_score?: number | string;
    rating_count?: number;
    is_featured?: number;
    sort_order?: number;
    status?: number;
    remark?: string;
    create_time?: string;
    update_time?: string;
    category_ids?: Array<number | string>;
    category_names?: string[];
  }

  /** 规范化后的插件条目（camelCase） */
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

function normalizePlugin(
  raw: ForgePluginApi.PluginItemRaw,
): ForgePluginApi.PluginItem {
  return {
    id: raw.id,
    pluginKey: raw.plugin_key,
    name: raw.name,
    type: raw.type,
    summary: raw.summary,
    description: raw.description,
    keywords: raw.keywords,
    iconFileId: raw.icon_file_id ?? null,
    coverFileId: raw.cover_file_id ?? null,
    authorUserId: raw.author_user_id ?? null,
    authorName: raw.author_name,
    homepageUrl: raw.homepage_url,
    repoUrl: raw.repo_url,
    license: raw.license,
    pricingType: raw.pricing_type ?? 1,
    price: raw.price,
    originalPrice: raw.original_price,
    currency: raw.currency,
    priceText: raw.price_text,
    purchaseUrl: raw.purchase_url,
    latestVersionId: raw.latest_version_id ?? null,
    latestVersion: raw.latest_version,
    downloadCount: raw.download_count ?? 0,
    installCount: raw.install_count ?? 0,
    favoriteCount: raw.favorite_count ?? 0,
    ratingScore: raw.rating_score,
    ratingCount: raw.rating_count ?? 0,
    isFeatured: raw.is_featured ?? 0,
    sortOrder: raw.sort_order ?? 0,
    status: raw.status ?? 0,
    remark: raw.remark,
    createTime: raw.create_time,
    updateTime: raw.update_time,
    categoryIds: raw.category_ids ?? [],
    categoryNames: raw.category_names ?? [],
  };
}

function serializePlugin(
  data: ForgePluginApi.PluginCreateParams | ForgePluginApi.PluginUpdateParams,
) {
  const payload: Record<string, unknown> = {};
  if ('pluginKey' in data) payload.plugin_key = data.pluginKey;
  if ('name' in data) payload.name = data.name;
  if ('type' in data) payload.type = data.type;
  if ('summary' in data) payload.summary = data.summary;
  if ('description' in data) payload.description = data.description;
  if ('keywords' in data) payload.keywords = data.keywords;
  if ('iconFileId' in data) payload.icon_file_id = data.iconFileId;
  if ('coverFileId' in data) payload.cover_file_id = data.coverFileId;
  if ('authorUserId' in data) payload.author_user_id = data.authorUserId;
  if ('authorName' in data) payload.author_name = data.authorName;
  if ('homepageUrl' in data) payload.homepage_url = data.homepageUrl;
  if ('repoUrl' in data) payload.repo_url = data.repoUrl;
  if ('license' in data) payload.license = data.license;
  if ('pricingType' in data) payload.pricing_type = data.pricingType;
  if ('price' in data) payload.price = data.price;
  if ('originalPrice' in data) payload.original_price = data.originalPrice;
  if ('currency' in data) payload.currency = data.currency;
  if ('priceText' in data) payload.price_text = data.priceText;
  if ('purchaseUrl' in data) payload.purchase_url = data.purchaseUrl;
  if ('isFeatured' in data) payload.is_featured = data.isFeatured;
  if ('sortOrder' in data) payload.sort_order = data.sortOrder;
  if ('status' in data) payload.status = data.status;
  if ('remark' in data) payload.remark = data.remark;
  if ('categoryIds' in data) payload.category_ids = data.categoryIds;
  return payload;
}

/** 分页查询插件 */
export async function getForgePluginPageApi(
  params: ForgePluginApi.PluginPageQuery,
) {
  const result = await requestClient.get<{
    records: ForgePluginApi.PluginItemRaw[];
    total: number;
    current: number;
    size: number;
  }>('/forge/admin/plugins/page', { params });
  return {
    ...result,
    records: (result.records ?? []).map(normalizePlugin),
  } as ForgePluginApi.PluginPageResult;
}

/** 插件详情（含已绑定分类） */
export async function getForgePluginDetailApi(id: number | string) {
  const raw = await requestClient.get<ForgePluginApi.PluginItemRaw>(
    `/forge/admin/plugins/${id}`,
  );
  return normalizePlugin(raw);
}

/** 创建插件，返回新建 id */
export async function createForgePluginApi(
  data: ForgePluginApi.PluginCreateParams,
) {
  return requestClient.post<number | string>(
    '/forge/admin/plugins',
    serializePlugin(data),
  );
}

/** 更新插件 */
export async function updateForgePluginApi(
  id: number | string,
  data: ForgePluginApi.PluginUpdateParams,
) {
  return requestClient.put<void>(
    `/forge/admin/plugins/${id}`,
    serializePlugin(data),
  );
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
    category_ids: categoryIds,
  });
}

/** 删除插件（存在版本时后端拒绝） */
export async function deleteForgePluginApi(id: number | string) {
  return requestClient.delete<void>(`/forge/admin/plugins/${id}`);
}
