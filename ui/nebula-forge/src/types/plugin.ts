/**
 * 插件商城相关类型定义。
 *
 * 字段命名与后端响应保持一致（forge 服务 Jackson 已回归默认 camelCase，
 * 因此响应体为驼峰命名）。查询参数走 @ModelAttribute 绑定，同为驼峰。
 */

/** 统一分页返回（对应 com.nebula.common.core.domain.PageResult）。 */
export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

/** 插件列表项（对应 ForgePluginFrontVO）。 */
export interface PluginListItem {
  id: number
  pluginKey: string
  name: string
  summary?: string | null
  /** 插件类型：inline/view */
  type?: string | null
  iconFileId?: number | null
  iconUrl?: string | null
  coverFileId?: number | null
  coverUrl?: string | null
  authorName?: string | null
  /** 定价类型：1免费 2付费 3订阅 4外部购买 */
  pricingType?: number | null
  price?: number | null
  originalPrice?: number | null
  currency?: string | null
  priceText?: string | null
  latestVersion?: string | null
  downloadCount?: number | null
  ratingScore?: number | null
  ratingCount?: number | null
  /** 是否推荐：1是 0否 */
  isFeatured?: number | null
  categoryNames?: string[] | null
}

/** 插件详情（对应 ForgePluginDetailFrontVO，继承列表项字段）。 */
export interface PluginDetail extends PluginListItem {
  /** 插件详情，Markdown 或 HTML */
  description?: string | null
  keywords?: string | null
  homepageUrl?: string | null
  repoUrl?: string | null
  license?: string | null
  purchaseUrl?: string | null
  latestVersionId?: number | null
  installCount?: number | null
  favoriteCount?: number | null
  createTime?: string | null
  updateTime?: string | null
  categoryIds?: number[] | null
}

/** 插件版本（对应 ForgePluginVersionFrontVO）。 */
export interface PluginVersion {
  id: number
  pluginId: number
  version: string
  /** 发布通道：stable/beta/dev */
  channel?: string | null
  manifestJson?: string | null
  packageFileId?: number | null
  packageUrl?: string | null
  packageSha256?: string | null
  packageSize?: number | null
  signature?: string | null
  minAppVersion?: string | null
  maxAppVersion?: string | null
  changelog?: string | null
  downloadCount?: number | null
  publishedTime?: string | null
}

/** 插件权限声明（对应 ForgePluginPermissionFrontVO）。 */
export interface PluginPermission {
  permissionCode: string
  permissionName?: string | null
  description?: string | null
  /** 风险等级：1低 2中 3高 */
  riskLevel?: number | null
  /** 是否必需：1必需 0可选 */
  required?: number | null
}

/** 下载结果（对应 ForgePluginDownloadResultVO）。 */
export interface PluginDownloadResult {
  pluginId: number
  versionId: number
  version: string
  packageFileId?: number | null
  packageUrl?: string | null
  packageSha256?: string | null
  packageSize?: number | null
  signature?: string | null
}

/** 插件分类（对应 ForgePluginCategoryFrontVO）。 */
export interface PluginCategory {
  id: number
  code?: string | null
  name: string
  description?: string | null
  iconFileId?: number | null
  iconUrl?: string | null
  sortOrder?: number | null
}

/** 列表查询参数（驼峰，对应后端 @ModelAttribute 绑定）。 */
export interface PluginPageQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
  categoryId?: number
  type?: string
  pricingType?: number
  sort?: string
}

/** 下载请求体（驼峰，对应后端 @RequestBody）。 */
export interface PluginDownloadRequest {
  clientVersion?: string
  clientOs?: string
}

/** 定价类型文案映射。 */
export const PRICING_TYPE_LABEL: Record<number, string> = {
  1: '免费',
  2: '付费',
  3: '订阅',
  4: '外部购买',
}

/** 插件类型文案映射。 */
export const PLUGIN_TYPE_LABEL: Record<string, string> = {
  inline: '内联',
  view: '视图',
}

/** 风险等级文案映射。 */
export const RISK_LEVEL_LABEL: Record<number, string> = {
  1: '低',
  2: '中',
  3: '高',
}

/** 排序选项。 */
export interface SortOption {
  value: string
  label: string
  icon: string
}

export const SORT_OPTIONS: SortOption[] = [
  { value: 'featured', label: '推荐', icon: 'lucide:sparkles' },
  { value: 'new', label: '最新', icon: 'lucide:clock' },
  { value: 'hot', label: '下载最多', icon: 'lucide:flame' },
  { value: 'rating', label: '评分最高', icon: 'lucide:star' },
]
