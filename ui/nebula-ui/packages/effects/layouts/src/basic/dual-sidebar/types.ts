import type { MenuRecordRaw } from '@nebula/types';

/** 双列导航的展示分组，不是可注册或可跳转的路由。 */
export interface DualSidebarGroup {
  icon?: MenuRecordRaw['icon'];
  id: string;
  label: string;
  menus: MenuRecordRaw[];
  name: string;
}
