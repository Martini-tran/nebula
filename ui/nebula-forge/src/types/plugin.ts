/**
 * 插件商城相关类型定义。
 *
 * 字段命名与后端响应保持一致（forge 服务启用 Jackson SNAKE_CASE，
 * 因此响应体为下划线命名）。查询参数走 @ModelAttribute 绑定，使用驼峰。
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
  plugin_key: string
  name: string
  summary?: string | null
  /** 插件类型：inline/view */
  type?: string | null
  icon_file_id?: number | null
  icon_url?: string | null
  cover_file_id?: number | null
  cover_url?: string | null
  author_name?: string | null
  /** 定价类型：1免费 2付费 3订阅 4外部购买 */
  pricing_type?: number | null
  price?: number | null
  original_price?: number | null
  currency?: string | null
  price_text?: string | null
  latest_version?: string | null
  download_count?: number | null
  rating_score?: number | null
  rating_count?: number | null
  /** 是否推荐：1是 0否 */
  is_featured?: number | null
  category_names?: string[] | null
}

/** 插件详情（对应 ForgePluginDetailFrontVO，继承列表项字段）。 */
export interface PluginDetail extends PluginListItem {
  /** 插件详情，Markdown 或 HTML */
  description?: string | null
  keywords?: string | null
  homepage_url?: string | null
  repo_url?: string | null
  license?: string | null
  purchase_url?: string | null
  latest_version_id?: number | null
  install_count?: number | null
  favorite_count?: number | null
  create_time?: string | null
  update_time?: string | null
  category_ids?: number[] | null
}

/** 插件版本（对应 ForgePluginVersionFrontVO）。 */
export interface PluginVersion {
  id: number
  plugin_id: number
  version: string
  /** 发布通道：stable/beta/dev */
  channel?: string | null
  manifest_json?: string | null
  package_file_id?: number | null
  package_url?: string | null
  package_sha256?: string | null
  package_size?: number | null
  signature?: string | null
  min_app_version?: string | null
  max_app_version?: string | null
  changelog?: string | null
  download_count?: number | null
  published_time?: string | null
}

/** 插件权限声明（对应 ForgePluginPermissionFrontVO）。 */
export interface PluginPermission {
  permission_code: string
  permission_name?: string | null
  description?: string | null
  /** 风险等级：1低 2中 3高 */
  risk_level?: number | null
  /** 是否必需：1必需 0可选 */
  required?: number | null
}

/** 下载结果（对应 ForgePluginDownloadResultVO）。 */
export interface PluginDownloadResult {
  plugin_id: number
  version_id: number
  version: string
  package_file_id?: number | null
  package_url?: string | null
  package_sha256?: string | null
  package_size?: number | null
  signature?: string | null
}

/** 插件分类（对应 ForgePluginCategoryFrontVO）。 */
export interface PluginCategory {
  id: number
  code?: string | null
  name: string
  description?: string | null
  icon_file_id?: number | null
  icon_url?: string | null
  sort_order?: number | null
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

/** 下载请求体（下划线，对应后端 @RequestBody）。 */
export interface PluginDownloadRequest {
  client_version?: string
  client_os?: string
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
