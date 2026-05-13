import type { RouteRecordStringComponent } from '@nebula/types';

import { requestClient } from '#/api/request';

export namespace MenuApi {
  /** 菜单树节点，匹配后端 MenuTreeVO */
  export interface MenuTreeItem {
    id: number | string;
    parentId: number | string | null;
    name: string;
    path?: string;
    component?: string;
    redirect?: string;
    icon?: string;
    title?: string;
    type: number;
    sort?: number;
    status: number;
    hidden?: boolean;
    keepAlive?: boolean;
    remark?: string;
    children?: MenuTreeItem[];
  }

  /** 创建菜单入参，匹配后端 MenuCreateRequest */
  export interface MenuCreateParams {
    parentId?: number | string | null;
    name: string;
    path?: string;
    component?: string;
    redirect?: string;
    icon?: string;
    title?: string;
    type: number;
    sort?: number;
    status?: number;
    hidden?: boolean;
    keepAlive?: boolean;
    remark?: string;
  }

  /** 更新菜单入参，匹配后端 MenuUpdateRequest */
  export type MenuUpdateParams = MenuCreateParams;
}

/** 获取当前用户菜单路由（扁平列表，用于前端路由注册） */
export async function getMenuRoutesApi() {
  return requestClient.get<RouteRecordStringComponent[]>('/manager/menu/routes');
}

/** 获取菜单树形结构 */
export async function getMenuTreeApi() {
  return requestClient.get<MenuApi.MenuTreeItem[]>('/manager/menu');
}

/** 创建菜单 */
export async function createMenuApi(data: MenuApi.MenuCreateParams) {
  return requestClient.post<number | string>('/manager/menu', data);
}

/** 更新菜单 */
export async function updateMenuApi(
  id: number | string,
  data: MenuApi.MenuUpdateParams,
) {
  return requestClient.put<void>(`/manager/menu/${id}`, data);
}

/** 删除菜单 */
export async function deleteMenuApi(id: number | string) {
  return requestClient.delete<void>(`/manager/menu/${id}`);
}

/** 检查菜单名称是否已存在 */
export async function checkMenuNameApi(name: string, id?: number | string) {
  return requestClient.get<boolean>('/manager/menu/check-name', {
    params: { name, id },
  });
}

/** 检查菜单路径是否已存在 */
export async function checkMenuPathApi(path: string, id?: number | string) {
  return requestClient.get<boolean>('/manager/menu/check-path', {
    params: { path, id },
  });
}






