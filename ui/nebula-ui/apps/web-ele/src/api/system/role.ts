import { requestClient } from '#/api/request';

/**
 * 系统角色管理 API
 * 后端实现位于 nebula-service-manager，context-path=/manager
 */
export namespace SystemRoleApi {
  /** 角色分页查询入参，对齐后端 RolePageQuery（继承 PageQuery） */
  export interface RolePageQuery {
    pageNum?: number;
    pageSize?: number;
    orderBy?: string;
    roleCode?: string;
    roleName?: string;
    status?: number;
  }

  /** 列表行 / 详情，对齐后端 RoleListVO */
  export interface RoleListItem {
    id: number | string;
    roleCode: string;
    roleName: string;
    status: number;
    remark?: string;
    createTime?: string;
    updateTime?: string;
  }

  /** 下拉用精简结构，对齐后端 RoleSimpleVO */
  export interface RoleSimple {
    id: number | string;
    roleCode: string;
    roleName: string;
  }

  /** 创建入参，对齐后端 RoleCreateRequest */
  export interface RoleCreateParams {
    roleCode: string;
    roleName: string;
    status?: number;
    remark?: string;
  }

  /** 更新入参，对齐后端 RoleUpdateRequest（roleCode 不可改） */
  export interface RoleUpdateParams {
    roleName?: string;
    status?: number;
    remark?: string;
  }

  /** 分页结果，对齐后端 PageResult<RoleListVO> */
  export interface RolePageResult {
    records: RoleListItem[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }
}

const BASE = '/manager/role';

/** 分页查询 */
export async function getSystemRolePageApi(params: SystemRoleApi.RolePageQuery) {
  return requestClient.get<SystemRoleApi.RolePageResult>(`${BASE}/page`, {
    params,
  });
}

/** 启用状态下的全量角色，下拉/勾选场景使用 */
export async function getSystemRoleListApi() {
  return requestClient.get<SystemRoleApi.RoleSimple[]>(`${BASE}/list`);
}

/** 角色详情 */
export async function getSystemRoleDetailApi(id: number | string) {
  return requestClient.get<SystemRoleApi.RoleListItem>(`${BASE}/${id}`);
}

/** 创建角色 */
export async function createSystemRoleApi(data: SystemRoleApi.RoleCreateParams) {
  return requestClient.post<number | string>(BASE, data);
}

/** 更新角色 */
export async function updateSystemRoleApi(
  id: number | string,
  data: SystemRoleApi.RoleUpdateParams,
) {
  return requestClient.put<void>(`${BASE}/${id}`, data);
}

/** 删除角色（仍有绑定用户时后端拒绝） */
export async function deleteSystemRoleApi(id: number | string) {
  return requestClient.delete<void>(`${BASE}/${id}`);
}

/** 启用 / 禁用 */
export async function updateSystemRoleStatusApi(
  id: number | string,
  status: number,
) {
  return requestClient.put<void>(`${BASE}/${id}/status`, { status });
}

/** 取角色当前已授权的菜单 ID 列表 */
export async function getSystemRoleMenuIdsApi(id: number | string) {
  return requestClient.get<Array<number | string>>(`${BASE}/${id}/menus`);
}

/** 全量覆盖角色菜单授权 */
export async function assignSystemRoleMenusApi(
  id: number | string,
  menuIds: Array<number | string>,
) {
  return requestClient.put<void>(`${BASE}/${id}/menus`, { menuIds });
}

/** 取角色当前已绑定的用户 ID 列表 */
export async function getSystemRoleUserIdsApi(id: number | string) {
  return requestClient.get<Array<number | string>>(`${BASE}/${id}/users`);
}

/** 全量覆盖角色 - 用户关联 */
export async function assignSystemRoleUsersApi(
  id: number | string,
  userIds: Array<number | string>,
) {
  return requestClient.put<void>(`${BASE}/${id}/users`, { userIds });
}
