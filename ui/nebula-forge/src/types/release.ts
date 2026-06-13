export type ReleaseChannel = 'stable' | 'beta'

export type ReleasePlatform = 'windows' | 'macos' | 'linux'

export interface ReleaseAsset {
  /** 目标平台 */
  platform: ReleasePlatform
  /** 按钮文案，例如「Windows 安装包 (.exe)」 */
  label: string
  /** 下载地址（静态阶段可指向占位/真实分发地址） */
  url: string
  /** 文件大小展示，例如「86 MB」 */
  size?: string
}

export interface ReleaseChangelog {
  /** 新增 */
  added?: string[]
  /** 改进 */
  changed?: string[]
  /** 修复 */
  fixed?: string[]
}

export interface Release {
  /** 语义化版本号，例如「1.0.0」 */
  version: string
  /** 发布日期 ISO 字符串，例如「2026-06-12」 */
  date: string
  /** 发布渠道 */
  channel: ReleaseChannel
  /** 是否为最新稳定版 */
  isLatest: boolean
  /** 一句话亮点列表 */
  highlights: string[]
  /** 分组变更日志 */
  changelog: ReleaseChangelog
  /** 各平台下载资源 */
  assets: ReleaseAsset[]
}
