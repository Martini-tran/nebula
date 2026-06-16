import { requestClient } from '#/api/request';

export namespace ForgeVersionApi {
  /** 后端 SNAKE_CASE 序列化的原始版本结构 */
  export interface VersionItemRaw {
    id: number | string;
    plugin_id: number | string;
    version: string;
    channel?: string;
    manifest_json?: string;
    package_file_id?: number | string | null;
    package_url?: string;
    package_sha256?: string;
    package_size?: number | string;
    signature?: string;
    min_app_version?: string;
    max_app_version?: string;
    changelog?: string;
    download_count?: number;
    review_status?: number;
    review_remark?: string;
    published_time?: string;
    status?: number;
    remark?: string;
    create_time?: string;
    update_time?: string;
  }

  /** 规范化后的版本条目（camelCase） */
  export interface VersionItem {
    id: number | string;
    pluginId: number | string;
    version: string;
    channel?: string;
    manifestJson?: string;
    packageFileId?: number | string | null;
    packageUrl?: string;
    packageSha256?: string;
    packageSize?: number | string;
    signature?: string;
    minAppVersion?: string;
    maxAppVersion?: string;
    changelog?: string;
    downloadCount?: number;
    reviewStatus?: number;
    reviewRemark?: string;
    publishedTime?: string;
    status?: number;
    remark?: string;
    createTime?: string;
    updateTime?: string;
  }

  export interface VersionPageQuery {
    pageNum?: number;
    pageSize?: number;
    status?: number;
    reviewStatus?: number;
    channel?: string;
  }

  export interface VersionCreateParams {
    version: string;
    channel?: string;
    manifestJson: string;
    packageFileId?: number | string | null;
    packageUrl?: string;
    packageSha256: string;
    packageSize?: number | string | null;
    signature?: string;
    minAppVersion?: string;
    maxAppVersion?: string;
    changelog?: string;
    status?: number;
    remark?: string;
  }

  export type VersionUpdateParams = Partial<Omit<VersionCreateParams, 'status'>>;

  export interface VersionPageResult {
    records: VersionItem[];
    total: number;
    current: number;
    size: number;
  }

  /** 权限声明原始结构 */
  export interface PermissionItemRaw {
    id: number | string;
    plugin_id: number | string;
    version_id: number | string;
    permission_code: string;
    permission_name: string;
    description?: string;
    risk_level?: number;
    required?: number;
    create_time?: string;
  }

  export interface PermissionItem {
    id?: number | string;
    permissionCode: string;
    permissionName: string;
    description?: string;
    riskLevel?: number;
    required?: number;
  }
}

function normalizeVersion(
  raw: ForgeVersionApi.VersionItemRaw,
): ForgeVersionApi.VersionItem {
  return {
    id: raw.id,
    pluginId: raw.plugin_id,
    version: raw.version,
    channel: raw.channel,
    manifestJson: raw.manifest_json,
    packageFileId: raw.package_file_id ?? null,
    packageUrl: raw.package_url,
    packageSha256: raw.package_sha256,
    packageSize: raw.package_size,
    signature: raw.signature,
    minAppVersion: raw.min_app_version,
    maxAppVersion: raw.max_app_version,
    changelog: raw.changelog,
    downloadCount: raw.download_count ?? 0,
    reviewStatus: raw.review_status ?? 0,
    reviewRemark: raw.review_remark,
    publishedTime: raw.published_time,
    status: raw.status ?? 0,
    remark: raw.remark,
    createTime: raw.create_time,
    updateTime: raw.update_time,
  };
}

function normalizePermission(
  raw: ForgeVersionApi.PermissionItemRaw,
): ForgeVersionApi.PermissionItem {
  return {
    id: raw.id,
    permissionCode: raw.permission_code,
    permissionName: raw.permission_name,
    description: raw.description,
    riskLevel: raw.risk_level ?? 1,
    required: raw.required ?? 1,
  };
}

function serializeVersion(
  data: ForgeVersionApi.VersionCreateParams | ForgeVersionApi.VersionUpdateParams,
) {
  const payload: Record<string, unknown> = {};
  if ('version' in data) payload.version = data.version;
  if ('channel' in data) payload.channel = data.channel;
  if ('manifestJson' in data) payload.manifest_json = data.manifestJson;
  if ('packageFileId' in data) payload.package_file_id = data.packageFileId;
  if ('packageUrl' in data) payload.package_url = data.packageUrl;
  if ('packageSha256' in data) payload.package_sha256 = data.packageSha256;
  if ('packageSize' in data) payload.package_size = data.packageSize;
  if ('signature' in data) payload.signature = data.signature;
  if ('minAppVersion' in data) payload.min_app_version = data.minAppVersion;
  if ('maxAppVersion' in data) payload.max_app_version = data.maxAppVersion;
  if ('changelog' in data) payload.changelog = data.changelog;
  if ('status' in data) payload.status = (data as ForgeVersionApi.VersionCreateParams).status;
  if ('remark' in data) payload.remark = data.remark;
  return payload;
}

function base(pluginId: number | string) {
  return `/forge/admin/plugins/${pluginId}/versions`;
}

/** 分页查询某插件的版本 */
export async function getForgeVersionPageApi(
  pluginId: number | string,
  params: ForgeVersionApi.VersionPageQuery,
) {
  const result = await requestClient.get<{
    records: ForgeVersionApi.VersionItemRaw[];
    total: number;
    current: number;
    size: number;
  }>(`${base(pluginId)}/page`, { params });
  return {
    ...result,
    records: (result.records ?? []).map(normalizeVersion),
  } as ForgeVersionApi.VersionPageResult;
}

/** 版本详情 */
export async function getForgeVersionDetailApi(
  pluginId: number | string,
  versionId: number | string,
) {
  const raw = await requestClient.get<ForgeVersionApi.VersionItemRaw>(
    `${base(pluginId)}/${versionId}`,
  );
  return normalizeVersion(raw);
}

/** 创建版本，返回新建 id */
export async function createForgeVersionApi(
  pluginId: number | string,
  data: ForgeVersionApi.VersionCreateParams,
) {
  return requestClient.post<number | string>(
    base(pluginId),
    serializeVersion(data),
  );
}

/** 更新版本 */
export async function updateForgeVersionApi(
  pluginId: number | string,
  versionId: number | string,
  data: ForgeVersionApi.VersionUpdateParams,
) {
  return requestClient.put<void>(
    `${base(pluginId)}/${versionId}`,
    serializeVersion(data),
  );
}

/** 审核版本（reviewStatus：0 待审 / 1 通过 / 2 拒绝） */
export async function reviewForgeVersionApi(
  pluginId: number | string,
  versionId: number | string,
  data: { reviewStatus: number; reviewRemark?: string },
) {
  return requestClient.put<void>(`${base(pluginId)}/${versionId}/review`, {
    review_status: data.reviewStatus,
    review_remark: data.reviewRemark,
  });
}

/** 更新版本状态（0 草稿 / 1 已发布 / 2 已下架 / 3 已废弃） */
export async function updateForgeVersionStatusApi(
  pluginId: number | string,
  versionId: number | string,
  status: number,
) {
  return requestClient.put<void>(
    `${base(pluginId)}/${versionId}/status`,
    undefined,
    { params: { status } },
  );
}

/** 删除版本（级联删除权限声明） */
export async function deleteForgeVersionApi(
  pluginId: number | string,
  versionId: number | string,
) {
  return requestClient.delete<void>(`${base(pluginId)}/${versionId}`);
}

/** 查询版本权限声明 */
export async function getForgeVersionPermissionsApi(
  pluginId: number | string,
  versionId: number | string,
) {
  const list = await requestClient.get<ForgeVersionApi.PermissionItemRaw[]>(
    `${base(pluginId)}/${versionId}/permissions`,
  );
  return (list ?? []).map(normalizePermission);
}

/** 全量替换版本权限声明 */
export async function bindForgeVersionPermissionsApi(
  pluginId: number | string,
  versionId: number | string,
  permissions: ForgeVersionApi.PermissionItem[],
) {
  return requestClient.put<void>(`${base(pluginId)}/${versionId}/permissions`, {
    permissions: permissions.map((p) => ({
      permission_code: p.permissionCode,
      permission_name: p.permissionName,
      description: p.description,
      risk_level: p.riskLevel,
      required: p.required,
    })),
  });
}
