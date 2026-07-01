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
  packageId: number
  limitType?: number | null
  quotaAmount?: number | null
  quotaUnit?: string | null
  resetCycle?: number | null
  overLimitStrategy?: number | null
  description?: string | null
}

export interface RelayPackageModel {
  id: number
  packageId: number
  modelId: number
  modelCode?: string | null
  modelName?: string | null
  modelVendor?: string | null
  providerModelCode?: string | null
  consumeMultiplier?: number | null
  minChargeAmount?: number | null
  maxContextTokens?: number | null
  /** 输入Token单价（每百万Token），币种沿用所属套餐 currency */
  inputPricePerMillionTokens?: number | null
  /** 输出Token单价（每百万Token），币种沿用所属套餐 currency */
  outputPricePerMillionTokens?: number | null
  isDefault?: boolean | null
}

export interface RelayProviderPackage {
  id: number
  providerId: number
  providerName?: string | null
  packageTypeId?: number | null
  packageTypeCode?: string | null
  packageTypeName?: string | null
  /** 计费模式：usage / subscription（来自类型字典） */
  billingMode?: 'usage' | 'subscription' | null
  name: string
  price?: number | null
  originalPrice?: number | null
  currency?: string | null
  recommended?: boolean | null
  recommendScore?: number | null
  description?: string | null
  quotaSummary?: string | null
  limits?: RelayPackageLimit[]
  models?: RelayPackageModel[]
  sortOrder?: number | null
}

export interface RelayProviderAdvantage {
  id: number
  providerId: number
  title: string
  content?: string | null
  /** 1 普通 / 2 核心 / 3 风险 */
  advantageType?: number | null
  iconUrl?: string | null
}

export interface RelayPaymentMethod {
  id: number
  code: string
  name: string
  iconUrl?: string | null
  description?: string | null
}

export interface RelayModel {
  id: number
  code?: string | null
  name?: string | null
  modelVendor?: string | null
  modelType?: number | null
  description?: string | null
}

export interface RelayProvider {
  id: number
  name: string
  logoText?: string | null
  logoUrl?: string | null
  websiteUrl?: string | null
  description?: string | null
  recommendScore?: number | null
  sortOrder?: number | null
  packages?: RelayProviderPackage[]
  advantages?: RelayProviderAdvantage[]
  paymentMethods?: RelayPaymentMethod[]
  models?: RelayModel[]
  vendorTypes?: string[]
  billingModes?: string[]
  packageTypeCodes?: string[]
  /** 收录时间（创建时间） */
  createTime?: string | null
  /** 最近一次同步时间，可为空 */
  lastSyncTime?: string | null
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
  /** 排序：recommend(默认) / price_asc / price_desc / latest */
  sortBy?: 'recommend' | 'price_asc' | 'price_desc' | 'latest'
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
  billingMode?: 'usage' | 'subscription' | null
  durationValue?: number | null
  durationUnit?: number | null
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
      sortBy: params.sortBy,
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
  providerId: number
  providerName: string
  providerLogoText?: string | null
  providerLogoUrl?: string | null
  websiteUrl?: string | null
  providerDescription?: string | null
  recommendReason: string
  reviewContent?: string | null
  reviewScore?: number | null
  pros?: string | null
  cons?: string | null
  useScenario?: string | null
  firstUseTime?: string | null
  reviewTime?: string | null
  recommendTime?: string | null
  sortOrder?: number | null
  rechargeCount?: number | null
  totalCnyAmount?: number | null
  lastRechargeTime?: string | null
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

// ============ 比价（前台） ============

export interface RelayCompareRow {
  // limit
  limitId: number
  limitType?: number | null
  limitTypeText?: string | null
  quotaAmount?: number | null
  quotaUnit?: string | null
  resetCycle?: number | null
  resetCycleText?: string | null
  overLimitStrategy?: number | null
  overLimitStrategyText?: string | null
  limitDescription?: string | null

  // package
  packageId: number
  packageName?: string | null
  packageTypeCode?: string | null
  packageTypeName?: string | null
  packagePrice?: number | null
  packageOriginalPrice?: number | null
  packageCurrency?: string | null
  packageDescription?: string | null
  packageRecommended?: number | null
  packageRecommendScore?: number | null

  // provider
  providerId: number
  providerName?: string | null
  providerLogoText?: string | null
  providerLogoUrl?: string | null
  providerWebsiteUrl?: string | null
  providerRecommendScore?: number | null

  // model（仅 modelId 指定时有值）
  modelId?: number | null
  modelCode?: string | null
  modelName?: string | null
  modelVendor?: string | null
  providerModelCode?: string | null
  consumeMultiplier?: number | null
  inputPricePerMillionTokens?: number | null
  outputPricePerMillionTokens?: number | null
  effectiveInputPricePerMillionTokens?: number | null
  effectiveOutputPricePerMillionTokens?: number | null
  maxContextTokens?: number | null
}

export interface FetchCompareParams {
  pageNum?: number
  pageSize?: number
  modelId?: number | string
  /** 多选服务商 ID（与 providerId 二选一，优先生效） */
  providerIds?: Array<number | string>
  providerId?: number | string
  packageTypeCode?: string
  limitType?: number
  /** 排序：input_price / output_price / quota / recommend（默认） */
  sortBy?: 'input_price' | 'output_price' | 'quota' | 'recommend'
  keyword?: string
}

export const fetchRelayCompare = (params: FetchCompareParams = {}) =>
  get<PageResult<RelayCompareRow>>('/blog/front/ai-relay/compare', {
    params: omitEmpty({
      pageNum: params.pageNum,
      pageSize: params.pageSize,
      modelId: params.modelId,
      providerIds:
        params.providerIds && params.providerIds.length > 0
          ? params.providerIds.join(',')
          : undefined,
      providerId: params.providerId,
      packageTypeCode: params.packageTypeCode,
      limitType: params.limitType,
      sortBy: params.sortBy,
      keyword: params.keyword,
    }),
  })

// ============ 模型选择站点（前台，以 ai_relay_package_model 为主表） ============

export interface RelayModelStationRow {
  // package_model（主表）
  id: number
  packageId: number
  modelId: number
  providerModelCode?: string | null
  consumeMultiplier?: number | null
  minChargeAmount?: number | null
  maxContextTokens?: number | null
  inputPricePerMillionTokens?: number | null
  outputPricePerMillionTokens?: number | null
  effectiveInputPricePerMillionTokens?: number | null
  effectiveOutputPricePerMillionTokens?: number | null
  isDefault?: boolean | null

  // model
  modelCode?: string | null
  modelName?: string | null
  modelVendor?: string | null

  // package
  packageName?: string | null
  packageTypeCode?: string | null
  packageTypeName?: string | null
  packagePrice?: number | null
  packageOriginalPrice?: number | null
  packageCurrency?: string | null
  packageDescription?: string | null
  packageRecommended?: number | null
  packageRecommendScore?: number | null

  // provider（主站）
  providerId: number
  providerName?: string | null
  providerLogoText?: string | null
  providerLogoUrl?: string | null
  providerWebsiteUrl?: string | null
  providerRecommendScore?: number | null
}

export interface FetchModelStationsParams {
  pageNum?: number
  pageSize?: number
  /** 必填：先选模型，再看支持该模型的站点 */
  modelId: number | string
  /** 多选服务商（主站）ID（与 providerId 二选一，优先生效） */
  providerIds?: Array<number | string>
  providerId?: number | string
  packageTypeCode?: string
  /** 排序：input_price / output_price / multiplier / context / recommend（默认） */
  sortBy?: 'input_price' | 'output_price' | 'multiplier' | 'context' | 'recommend'
  keyword?: string
}

export const fetchRelayModelStations = (params: FetchModelStationsParams) =>
  get<PageResult<RelayModelStationRow>>('/blog/front/ai-relay/model-stations', {
    params: omitEmpty({
      pageNum: params.pageNum,
      pageSize: params.pageSize,
      modelId: params.modelId,
      providerIds:
        params.providerIds && params.providerIds.length > 0
          ? params.providerIds.join(',')
          : undefined,
      providerId: params.providerId,
      packageTypeCode: params.packageTypeCode,
      sortBy: params.sortBy,
      keyword: params.keyword,
    }),
  })
