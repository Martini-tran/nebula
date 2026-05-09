import { requestClient } from '#/api/request';

export namespace SystemRoleApi {
  /** 角色分页查询入参，匹配后端 RolePageQuery */
  export interface RolePageQuery {
    pageNum?: number;
    pageSize?: number;
    orderBy?: string;
    name?: string;
    code?: string;
    status?: number;
    createTimeStart?: string;
    createTimeEnd?: string;
  }

  /** 角色列表行，匹配后端 RoleListVO */
  export interface RoleListItem {
    id: number | string;
    name: string;
    code: string;
    status: number;
    sort?: number;
    remark?: string;
    createTime?: string;
    updateTime?: string;
  }

  /** 角色简要信息，匹配后端 RoleSimpleVO（用于下拉选择） */
  export interface RoleSimpleItem {
    id: number | string;
    name: string;
    code: string;
  }

  /** 创建角色入参，匹配后端 RoleCreateRequest */
  export interface RoleCreateParams {
    name: string;
    code: string;
    sort?: number;
    status?: number;
    remark?: string;
  }

  /** 更新角色入参，匹配后端 RoleUpdateRequest */
  export type RoleUpdateParams = RoleCreateParams;

  /** 分页结果，匹配后端 PageResult<RoleListVO> */
  export interface RolePageResult {
    records: RoleListItem[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }
}

/** 分页查询角色 */
export async function getSystemRolePageApi(
  params: SystemRoleApi.RolePageQuery,
) {
  return requestClient.get<SystemRoleApi.RolePageResult>(
    '/manager/role/page',
    { params },
  );
}

/** 获取所有启用角色（用于下拉选择） */
export async function getSystemRoleListApi() {
  return requestClient.get<SystemRoleApi.RoleSimpleItem[]>('/manager/role/list');
}

/** 角色详情 */
export async function getSystemRoleDetailApi(id: number | string) {
  return requestClient.get<SystemRoleApi.RoleListItem>(`/manager/role/${id}`);
}

/** 创建角色 */
export async function createSystemRoleApi(
  data: SystemRoleApi.RoleCreateParams,
) {
  return requestClient.post<number | string>('/manager/role', data);
}

/** 更新角色 */
export async function updateSystemRoleApi(
  id: number | string,
  data: SystemRoleApi.RoleUpdateParams,
) {
  return requestClient.put<void>(`/manager/role/${id}`, data);
}

/** 删除角色 */
export async function deleteSystemRoleApi(id: number | string) {
  return requestClient.delete<void>(`/manager/role/${id}`);
}

/** 启用 / 禁用角色 */
export async function updateSystemRoleStatusApi(
  id: number | string,
  status: number,
) {
  return requestClient.put<void>(`/manager/role/${id}/status`, { status });
}

/** 获取角色已分配的菜单 ID 列表 */
export async function getSystemRoleMenusApi(id: number | string) {
  return requestClient.get<(number | string)[]>(`/manager/role/${id}/menus`);
}

/** 为角色分配菜单 */
export async function assignSystemRoleMenusApi(
  id: number | string,
  menuIds: (number | string)[],
) {
  return requestClient.put<void>(`/manager/role/${id}/menus`, { menuIds });
}

/** 获取角色已分配的用户 ID 列表 */
export async function getSystemRoleUsersApi(id: number | string) {
  return requestClient.get<(number | string)[]>(`/manager/role/${id}/users`);
}

/** 为角色分配用户 */
export async function assignSystemRoleUsersApi(
  id: number | string,
  userIds: (number | string)[],
) {
  return requestClient.put<void>(`/manager/role/${id}/users`, { userIds });
}
