import { get, post } from '../utils/request'
import type {
  PageResult,
  PluginCategory,
  PluginDetail,
  PluginDownloadRequest,
  PluginDownloadResult,
  PluginListItem,
  PluginPageQuery,
  PluginPermission,
  PluginVersion,
} from '../types/plugin'

/**
 * 插件商城前台接口。
 *
 * 网关按 /forge/** 路由到 forge 服务，故路径需带 forge 前缀。
 * request.ts 的 baseURL=/api，vite 会把 /api 重写删除后转发到网关，
 * 因此这里写 /forge/front/... ，最终 /api/forge/front/... → 网关 → forge。
 */

const BASE = '/forge/front'

/** 分页查询上架插件。 */
export const fetchPlugins = (query: PluginPageQuery = {}) =>
  get<PageResult<PluginListItem>>(`${BASE}/plugins`, { params: query })

/** 插件详情。 */
export const fetchPluginDetail = (id: number | string) =>
  get<PluginDetail>(`${BASE}/plugins/${id}`)

/** 插件的已发布、审核通过版本列表。 */
export const fetchPluginVersions = (id: number | string) =>
  get<PluginVersion[]>(`${BASE}/plugins/${id}/versions`)

/** 指定版本的权限声明。 */
export const fetchPluginPermissions = (
  id: number | string,
  versionId: number | string,
) =>
  get<PluginPermission[]>(`${BASE}/plugins/${id}/versions/${versionId}/permissions`)

/** 分类列表。 */
export const fetchCategories = () =>
  get<PluginCategory[]>(`${BASE}/plugin-categories`)

/** 下载最新版本。 */
export const downloadLatest = (
  id: number | string,
  body?: PluginDownloadRequest,
) => post<PluginDownloadResult>(`${BASE}/plugins/${id}/download`, body ?? {})

/** 下载指定版本。 */
export const downloadVersion = (
  id: number | string,
  versionId: number | string,
  body?: PluginDownloadRequest,
) =>
  post<PluginDownloadResult>(
    `${BASE}/plugins/${id}/versions/${versionId}/download`,
    body ?? {},
  )
