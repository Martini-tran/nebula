import { requestClient } from '#/api/request';

/**
 * Forge-插件版本 API
 * forge 服务 Jackson 已回归默认 camelCase（yml 未配置 SNAKE_CASE），出入参均为 camelCase，本层直接透传。
 */
export namespace ForgeVersionApi {
  /** 版本条目（camelCase） */
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

  /** 权限声明条目（camelCase） */
  export interface PermissionItem {
    id?: number | string;
    permissionCode: string;
    permissionName: string;
    description?: string;
    riskLevel?: number;
    required?: number;
  }
}

function base(pluginId: number | string) {
  return `/forge/admin/plugins/${pluginId}/versions`;
}

/** 分页查询某插件的版本 */
export async function getForgeVersionPageApi(
  pluginId: number | string,
  params: ForgeVersionApi.VersionPageQuery,
) {
  return requestClient.get<ForgeVersionApi.VersionPageResult>(
    `${base(pluginId)}/page`,
    { params },
  );
}

/** 版本详情 */
export async function getForgeVersionDetailApi(
  pluginId: number | string,
  versionId: number | string,
) {
  return requestClient.get<ForgeVersionApi.VersionItem>(
    `${base(pluginId)}/${versionId}`,
  );
}

/** 创建版本，返回新建 id */
export async function createForgeVersionApi(
  pluginId: number | string,
  data: ForgeVersionApi.VersionCreateParams,
) {
  return requestClient.post<number | string>(base(pluginId), data);
}

/** 更新版本 */
export async function updateForgeVersionApi(
  pluginId: number | string,
  versionId: number | string,
  data: ForgeVersionApi.VersionUpdateParams,
) {
  return requestClient.put<void>(`${base(pluginId)}/${versionId}`, data);
}

/** 审核版本（reviewStatus：0 待审 / 1 通过 / 2 拒绝） */
export async function reviewForgeVersionApi(
  pluginId: number | string,
  versionId: number | string,
  data: { reviewStatus: number; reviewRemark?: string },
) {
  return requestClient.put<void>(`${base(pluginId)}/${versionId}/review`, data);
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
  const list = await requestClient.get<ForgeVersionApi.PermissionItem[]>(
    `${base(pluginId)}/${versionId}/permissions`,
  );
  return list ?? [];
}

/** 全量替换版本权限声明 */
export async function bindForgeVersionPermissionsApi(
  pluginId: number | string,
  versionId: number | string,
  permissions: ForgeVersionApi.PermissionItem[],
) {
  return requestClient.put<void>(`${base(pluginId)}/${versionId}/permissions`, {
    permissions: permissions.map((p) => ({
      permissionCode: p.permissionCode,
      permissionName: p.permissionName,
      description: p.description,
      riskLevel: p.riskLevel,
      required: p.required,
    })),
  });
}
