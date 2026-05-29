import { get } from '../utils/request'

// ============ 通用 ============

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

// ============ Provider 聚合 VO ============

export type AdvantageKind = 'core' | 'normal' | 'risk'

export interface RelayPackageLimit {
  id: number
  package_id: number
  limit_type?: number | null
  quota_amount?: number | null
  quota_unit?: string | null
  reset_cycle?: number | null
  over_limit_strategy?: number | null
  description?: string | null
}

export interface RelayPackageModel {
  id: number
  package_id: number
  model_id: number
  model_code?: string | null
  model_name?: string | null
  model_vendor?: string | null
  provider_model_code?: string | null
  consume_multiplier?: number | null
  min_charge_amount?: number | null
  max_context_tokens?: number | null
  is_default?: boolean | null
}

export interface RelayProviderPackage {
  id: number
  provider_id: number
  provider_name?: string | null
  package_type_id?: number | null
  package_type_code?: string | null
  package_type_name?: string | null
  /** 计费模式：usage / subscription（来自类型字典） */
  billing_mode?: 'usage' | 'subscription' | null
  name: string
  price?: number | null
  original_price?: number | null
  currency?: string | null
  recommended?: boolean | null
  recommend_score?: number | null
  description?: string | null
  quota_summary?: string | null
  limits?: RelayPackageLimit[]
  models?: RelayPackageModel[]
  sort_order?: number | null
}

export interface RelayProviderAdvantage {
  id: number
  provider_id: number
  title: string
  content?: string | null
  /** 1 普通 / 2 核心 / 3 风险 */
  advantage_type?: number | null
  icon_url?: string | null
}

export interface RelayPaymentMethod {
  id: number
  code: string
  name: string
  icon_url?: string | null
  description?: string | null
}

export interface RelayModel {
  id: number
  code?: string | null
  name?: string | null
  model_vendor?: string | null
  model_type?: number | null
  description?: string | null
}

export interface RelayProvider {
  id: number
  name: string
  logo_text?: string | null
  logo_url?: string | null
  website_url?: string | null
  description?: string | null
  recommend_score?: number | null
  sort_order?: number | null
  packages?: RelayProviderPackage[]
  advantages?: RelayProviderAdvantage[]
  payment_methods?: RelayPaymentMethod[]
  models?: RelayModel[]
  vendor_types?: string[]
  billing_modes?: string[]
  package_type_codes?: string[]
  /** 收录时间（创建时间） */
  create_time?: string | null
  /** 最近一次同步时间，可为空 */
  last_sync_time?: string | null
}

// ============ DTOs ============

export interface FetchProvidersParams {
  pageNum?: number
  pageSize?: number
  keyword?: string
  modelVendor?: string
  billingMode?: string
  packageTypeCode?: string
  sortBy?: 'recommend' | 'price' | 'stability'
  /** 同步时间起，yyyy-MM-dd */
  lastSyncTimeStart?: string
  /** 同步时间止，yyyy-MM-dd */
  lastSyncTimeEnd?: string
}

export interface FetchPackagesParams {
  pageNum?: number
  pageSize?: number
  providerId?: number
  packageTypeId?: number
  packageTypeCode?: string
  keyword?: string
}

export interface FetchModelsParams {
  pageNum?: number
  pageSize?: number
  keyword?: string
  modelVendor?: string
  modelType?: number
}

export interface RelayPackageType {
  id: number
  code: string
  name: string
  billing_mode?: 'usage' | 'subscription' | null
  duration_value?: number | null
  duration_unit?: number | null
  description?: string | null
}

export interface RelayOption {
  value: string
  label: string
}

const omitEmpty = <T extends Record<string, unknown>>(obj: T) =>
  Object.fromEntries(
    Object.entries(obj).filter(([, v]) => v !== undefined && v !== null && v !== ''),
  )

// ============ API ============

export const fetchRelayProviders = (params: FetchProvidersParams = {}) =>
  get<PageResult<RelayProvider>>('/blog/front/ai-relay/providers', {
    params: omitEmpty({
      pageNum: params.pageNum,
      pageSize: params.pageSize,
      keyword: params.keyword,
      modelVendor: params.modelVendor,
      billingMode: params.billingMode,
      packageTypeCode: params.packageTypeCode,
      sortBy: params.sortBy,
      lastSyncTimeStart: params.lastSyncTimeStart,
      lastSyncTimeEnd: params.lastSyncTimeEnd,
    }),
  })

export const fetchRelayProviderDetail = (id: number | string) =>
  get<RelayProvider>(`/blog/front/ai-relay/providers/${encodeURIComponent(String(id))}`)

export const fetchRelayPackages = (params: FetchPackagesParams = {}) =>
  get<PageResult<RelayProviderPackage>>('/blog/front/ai-relay/packages', {
    params: omitEmpty({
      pageNum: params.pageNum,
      pageSize: params.pageSize,
      providerId: params.providerId,
      packageTypeId: params.packageTypeId,
      packageTypeCode: params.packageTypeCode,
      keyword: params.keyword,
    }),
  })

export const fetchRelayPackagesByProvider = (providerId: number | string) =>
  get<RelayProviderPackage[]>('/blog/front/ai-relay/packages/by-provider', {
    params: { providerId },
  })

export const fetchRelayPackageDetail = (id: number | string) =>
  get<RelayProviderPackage>(`/blog/front/ai-relay/packages/${encodeURIComponent(String(id))}`)

export const fetchRelayModels = (params: FetchModelsParams = {}) =>
  get<PageResult<RelayModel>>('/blog/front/ai-relay/models', {
    params: omitEmpty({
      pageNum: params.pageNum,
      pageSize: params.pageSize,
      keyword: params.keyword,
      modelVendor: params.modelVendor,
      modelType: params.modelType,
    }),
  })

export const fetchRelayModelDetail = (id: number | string) =>
  get<RelayModel>(`/blog/front/ai-relay/models/${encodeURIComponent(String(id))}`)

export const fetchRelayPackageTypes = () =>
  get<RelayPackageType[]>('/blog/front/ai-relay/package-types')

export const fetchRelayPaymentMethods = () =>
  get<RelayPaymentMethod[]>('/blog/front/ai-relay/payment-methods')

export const fetchRelayVendorOptions = () =>
  get<RelayOption[]>('/blog/front/ai-relay/models/vendor-options')

// ============ 推荐 / 测评（前台） ============

export interface RelayRecommend {
  id: number
  provider_id: number
  provider_name: string
  provider_logo_text?: string | null
  provider_logo_url?: string | null
  website_url?: string | null
  provider_description?: string | null
  recommend_reason: string
  review_content?: string | null
  review_score?: number | null
  pros?: string | null
  cons?: string | null
  use_scenario?: string | null
  first_use_time?: string | null
  review_time?: string | null
  recommend_time?: string | null
  sort_order?: number | null
  recharge_count?: number | null
  total_cny_amount?: number | null
  last_recharge_time?: string | null
}

export interface FetchRecommendsParams {
  pageNum?: number
  pageSize?: number
  providerId?: number | string
  keyword?: string
  /** score(默认按评分倒序) / time(按推荐时间) / sort(按 sort_order) */
  sortBy?: 'score' | 'time' | 'sort'
}

export const fetchRelayRecommends = (params: FetchRecommendsParams = {}) =>
  get<PageResult<RelayRecommend>>('/blog/front/ai-relay/recommends', {
    params: omitEmpty({
      pageNum: params.pageNum,
      pageSize: params.pageSize,
      providerId: params.providerId,
      keyword: params.keyword,
      sortBy: params.sortBy,
    }),
  })

export const fetchRelayRecommendDetail = (id: number | string) =>
  get<RelayRecommend>(
    `/blog/front/ai-relay/recommends/${encodeURIComponent(String(id))}`,
  )

export const fetchRelayRecommendByProvider = (providerId: number | string) =>
  get<RelayRecommend>(
    `/blog/front/ai-relay/recommends/by-provider/${encodeURIComponent(String(providerId))}`,
  )
