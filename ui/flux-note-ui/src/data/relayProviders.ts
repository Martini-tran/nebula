/*
 * Mock 中转站数据
 * 字段对齐后端 ai_relay_* 表结构。前台接口接通后可替换为 API 拉取。
 */

export type AdvantageKind = 'core' | 'normal' | 'risk'

export interface RelayProviderAdvantage {
  title: string
  content?: string
  kind: AdvantageKind
}

export interface RelayProviderPackage {
  id: string
  typeCode: 'day' | 'week' | 'month' | 'usage'
  typeName: string
  name: string
  price: number
  originalPrice?: number
  currency: string
  recommended?: boolean
  description?: string
  /** 套餐主要限额简述，供卡片展示 */
  quotaSummary?: string
}

export interface RelayProviderModel {
  code: string
  name: string
  vendor?: string
  /** 可选：3 日可用率（0-100） */
  availability?: number
  /** 可选：缓存命中率（0-100） */
  cacheHit?: number
}

export interface RelayProvider {
  id: string
  rank: number
  name: string
  logoText: string
  websiteUrl: string
  description: string
  /** 综合推荐分 0-100 */
  recommendScore: number
  /** 24h 可用率 0-100 */
  uptime24h: number
  /** 3 日可用率 0-100 */
  uptime3d: number
  /** 标签（开票 / 退款 / 文档质量等） */
  tags: string[]
  packages: RelayProviderPackage[]
  paymentMethods: string[]
  advantages: RelayProviderAdvantage[]
  improvements?: string[]
  models: RelayProviderModel[]
  recentUpdates?: { time: string; text: string }[]
  vendorTypes: ('claude' | 'gpt' | 'gemini')[]
  /** 计费模式标识（仅用于筛选过滤） */
  billingModes: ('usage' | 'subscription')[]
}

export const relayProviders: RelayProvider[] = [
  {
    id: 'openrouter',
    rank: 1,
    name: 'OpenRouter',
    logoText: 'OR',
    websiteUrl: 'https://openrouter.ai',
    description:
      '聚合多家厂商模型，按量计费，价格公开透明。适合需要在多个模型间切换、对延迟和稳定性都有要求的开发者。',
    recommendScore: 96.4,
    uptime24h: 99.92,
    uptime3d: 99.81,
    tags: ['支持开票', '7 天无理由退款', '文档完整'],
    packages: [
      {
        id: 'or-usage',
        typeCode: 'usage',
        typeName: '按量',
        name: '按量计费',
        price: 0,
        currency: 'USD',
        description: '充值多少用多少，不绑定周期',
        quotaSummary: '账户余额，按 token 实时计费',
      },
      {
        id: 'or-month',
        typeCode: 'month',
        typeName: '月卡',
        name: '专业版月卡',
        price: 198,
        originalPrice: 258,
        currency: 'CNY',
        recommended: true,
        description: '全模型 + 高优先级队列',
        quotaSummary: '每月 1.5 亿 token / 单次 64K context',
      },
    ],
    paymentMethods: ['支付宝', '微信支付', '银行卡', 'PayPal', 'USDT'],
    advantages: [
      { kind: 'core', title: '聚合 80+ 模型', content: 'Claude / GPT / Gemini / DeepSeek 等主流模型一站式可用' },
      { kind: 'core', title: '透明定价', content: '按 token 实时显示每次调用成本' },
      { kind: 'normal', title: '中国大陆免代理', content: '提供 cn 节点，平均延迟 80ms 内' },
    ],
    improvements: ['缓存命中率波动较大', '退款需联系客服人工处理'],
    models: [
      { code: 'claude-sonnet-4', name: 'Claude Sonnet 4', vendor: 'Anthropic', availability: 99.6, cacheHit: 78 },
      { code: 'gpt-4o', name: 'GPT-4o', vendor: 'OpenAI', availability: 99.9, cacheHit: 85 },
      { code: 'gemini-2.0-pro', name: 'Gemini 2.0 Pro', vendor: 'Google', availability: 98.4, cacheHit: 62 },
    ],
    recentUpdates: [
      { time: '2 小时前', text: '上线 Claude Sonnet 4.7，价格与官方一致' },
      { time: '1 天前', text: '修复 cn 节点偶发 502，缓存命中率提升 12%' },
    ],
    vendorTypes: ['claude', 'gpt', 'gemini'],
    billingModes: ['usage', 'subscription'],
  },
  {
    id: 'aiproxy',
    rank: 2,
    name: 'AIProxy',
    logoText: 'AP',
    websiteUrl: 'https://aiproxy.example.com',
    description:
      '老牌中转，国内主流支付齐全，适合中小团队快速接入。月卡性价比突出，但高峰期 Claude 排队较明显。',
    recommendScore: 92.1,
    uptime24h: 99.46,
    uptime3d: 98.92,
    tags: ['支持开票', '企业账户', '中文文档'],
    packages: [
      {
        id: 'ap-day',
        typeCode: 'day',
        typeName: '天卡',
        name: '体验天卡',
        price: 9.9,
        currency: 'CNY',
        description: '24 小时内不限模型',
        quotaSummary: '500 万 token / 单次 32K',
      },
      {
        id: 'ap-month',
        typeCode: 'month',
        typeName: '月卡',
        name: '团队月卡',
        price: 268,
        originalPrice: 328,
        currency: 'CNY',
        recommended: true,
        quotaSummary: '2 亿 token / 5 人共享',
      },
    ],
    paymentMethods: ['支付宝', '微信支付', '对公转账'],
    advantages: [
      { kind: 'core', title: '国内开票完整', content: '13% 增值税专票次日寄出' },
      { kind: 'normal', title: '5 人席位', content: '团队月卡可拉同事共用配额' },
      { kind: 'risk', title: '高峰排队', content: '工作日 21-23 点 Claude 系列偶有 30s 等待' },
    ],
    improvements: ['Claude 高峰排队体感明显', '日志查询页面较粗糙'],
    models: [
      { code: 'claude-3-5-sonnet', name: 'Claude 3.5 Sonnet', vendor: 'Anthropic', availability: 98.1, cacheHit: 64 },
      { code: 'gpt-4o-mini', name: 'GPT-4o mini', vendor: 'OpenAI', availability: 99.7, cacheHit: 80 },
    ],
    recentUpdates: [
      { time: '昨天', text: '新增对公转账渠道，企业用户开通时长缩短至 4 小时' },
    ],
    vendorTypes: ['claude', 'gpt'],
    billingModes: ['subscription'],
  },
  {
    id: 'fastgpt-relay',
    rank: 3,
    name: 'FastRelay',
    logoText: 'FR',
    websiteUrl: 'https://fastrelay.example.com',
    description:
      '主打按量计费，价格贴近官方，对延迟极敏感的场景表现出色。适合做评测脚本、自动化流水线等高并发用法。',
    recommendScore: 89.5,
    uptime24h: 99.18,
    uptime3d: 98.74,
    tags: ['不绑定周期', '支持开票', '低延迟'],
    packages: [
      {
        id: 'fr-usage',
        typeCode: 'usage',
        typeName: '按量',
        name: '余额计费',
        price: 0,
        currency: 'CNY',
        description: '最低 1 元起充，按 token 实时计费',
        quotaSummary: '账户余额，超额自动暂停',
      },
    ],
    paymentMethods: ['支付宝', '微信支付', 'USDT'],
    advantages: [
      { kind: 'core', title: '极低首字节延迟', content: '香港 / 上海双节点，平均 60ms' },
      { kind: 'normal', title: '官方倍率 1.0', content: '不加价，仅按汇率结算' },
    ],
    improvements: ['不提供月卡', 'Gemini 系列暂未接入'],
    models: [
      { code: 'gpt-4o', name: 'GPT-4o', vendor: 'OpenAI', availability: 99.5, cacheHit: 70 },
      { code: 'claude-sonnet-4', name: 'Claude Sonnet 4', vendor: 'Anthropic', availability: 97.2, cacheHit: 55 },
    ],
    recentUpdates: [
      { time: '3 天前', text: '上海节点扩容，并发上限提升至 200 RPS' },
    ],
    vendorTypes: ['claude', 'gpt'],
    billingModes: ['usage'],
  },
  {
    id: 'devkey',
    rank: 4,
    name: 'DevKey',
    logoText: 'DK',
    websiteUrl: 'https://devkey.example.com',
    description:
      '开发者向中转，主打 SDK 集成和细粒度配额管理。提供独立的 dashboard 和 webhook 告警，适合内嵌进自家产品。',
    recommendScore: 86.7,
    uptime24h: 99.03,
    uptime3d: 98.41,
    tags: ['SDK 完善', '配额告警', 'Webhook'],
    packages: [
      {
        id: 'dk-month',
        typeCode: 'month',
        typeName: '月卡',
        name: '开发者月卡',
        price: 99,
        currency: 'CNY',
        quotaSummary: '5000 万 token / 月，超额按量',
      },
      {
        id: 'dk-usage',
        typeCode: 'usage',
        typeName: '按量',
        name: '按量计费',
        price: 0,
        currency: 'CNY',
        quotaSummary: '账户余额，无最低消费',
      },
    ],
    paymentMethods: ['支付宝', '微信支付', '银行卡'],
    advantages: [
      { kind: 'core', title: 'SDK 完善', content: '提供 Node / Python / Go SDK 和详细错误码文档' },
      { kind: 'normal', title: '配额告警', content: '可设置阈值通过 webhook 推送' },
    ],
    improvements: ['月卡价格略高', '客户端 UI 仍在迭代'],
    models: [
      { code: 'gpt-4o-mini', name: 'GPT-4o mini', vendor: 'OpenAI', availability: 99.8, cacheHit: 82 },
      { code: 'claude-3-5-haiku', name: 'Claude 3.5 Haiku', vendor: 'Anthropic', availability: 99.1, cacheHit: 73 },
      { code: 'gemini-2.0-flash', name: 'Gemini 2.0 Flash', vendor: 'Google', availability: 98.9, cacheHit: 68 },
    ],
    recentUpdates: [
      { time: '5 天前', text: '发布 v1.4 SDK，支持 streaming 重连' },
    ],
    vendorTypes: ['claude', 'gpt', 'gemini'],
    billingModes: ['usage', 'subscription'],
  },
]
