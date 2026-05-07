import type { RouteRecordStringComponent } from '@nebula/types';

import { requestClient } from '#/api/request';

/**
 * 获取用户所有菜单
 * 后端实现在 service-system，context-path=/system，所以前端打 /system/menu/all
 */
export async function getAllMenusApi() {
  return requestClient.get<RouteRecordStringComponent[]>('/system/menu/all');
}






