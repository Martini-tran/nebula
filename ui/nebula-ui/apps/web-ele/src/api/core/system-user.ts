import { requestClient } from '#/api/request';

export namespace SystemUserApi {
  /** 用户分页查询入参，匹配后端 UserPageQuery（继承 PageQuery） */
  export interface UserPageQuery {
    pageNum?: number;
    pageSize?: number;
    orderBy?: string;
    username?: string;
    nickname?: string;
    mobile?: string;
    email?: string;
    status?: number;
    /** ISO 时间，yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd'T'HH:mm:ss */
    createTimeStart?: string;
    createTimeEnd?: string;
  }

  /** 列表行，匹配后端 UserListVO */
  export interface UserListItem {
    id: number | string;
    username: string;
    nickname?: string;
    avatar?: string;
    mobile?: string;
    email?: string;
    status: number;
    remark?: string;
    createTime?: string;
    updateTime?: string;
    /** 是否为超级管理员（用于禁用编辑/删除/禁用/重置密码按钮） */
    superAdmin?: boolean;
  }

  /** 详情，匹配后端 UserDetailVO（字段同列表） */
  export type UserDetail = UserListItem;

  /** 创建入参，匹配后端 UserCreateRequest */
  export interface UserCreateParams {
    username: string;
    password: string;
    nickname?: string;
    avatar?: string;
    mobile?: string;
    email?: string;
    status?: number;
    remark?: string;
  }

  /** 更新入参，匹配后端 UserUpdateRequest（不允许动 username/password） */
  export interface UserUpdateParams {
    nickname?: string;
    avatar?: string;
    mobile?: string;
    email?: string;
    status?: number;
    remark?: string;
  }

  /** 分页结果，匹配后端 PageResult<UserListVO> */
  export interface UserPageResult {
    records: UserListItem[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }
}

/** 分页查询用户 */
export async function getSystemUserPageApi(params: SystemUserApi.UserPageQuery) {
  return requestClient.get<SystemUserApi.UserPageResult>('/manager/user/page', {
    params,
  });
}

/** 用户详情 */
export async function getSystemUserDetailApi(id: number | string) {
  return requestClient.get<SystemUserApi.UserDetail>(`/manager/user/${id}`);
}

/** 创建用户，返回新建用户主键 */
export async function createSystemUserApi(data: SystemUserApi.UserCreateParams) {
  return requestClient.post<number | string>('/manager/user', data);
}

/** 更新用户资料 */
export async function updateSystemUserApi(
  id: number | string,
  data: SystemUserApi.UserUpdateParams,
) {
  return requestClient.put<void>(`/manager/user/${id}`, data);
}

/** 软删除用户 */
export async function deleteSystemUserApi(id: number | string) {
  return requestClient.delete<void>(`/manager/user/${id}`);
}

/** 启用 / 禁用 */
export async function updateSystemUserStatusApi(
  id: number | string,
  status: number,
) {
  return requestClient.put<void>(`/manager/user/${id}/status`, { status });
}

/** 管理员重置密码 */
export async function resetSystemUserPasswordApi(
  id: number | string,
  newPassword: string,
) {
  return requestClient.put<void>(`/manager/user/${id}/password`, { newPassword });
}
