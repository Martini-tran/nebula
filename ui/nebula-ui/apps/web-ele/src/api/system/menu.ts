import { requestClient } from '#/api/request';

/**
 * 系统菜单管理后台 API
 * 后端实现位于 nebula-service-manager，context-path=/manager
 */
export namespace SystemMenuApi {
  /** 徽章样式可选值（与后端 sys_menu.badge_variants 对齐） */
  export const BadgeVariants = [
    'default',
    'destructive',
    'primary',
    'success',
    'warning',
  ] as const;

  export type BadgeVariant = (typeof BadgeVariants)[number];

  export type MenuType = 'button' | 'catalog' | 'embedded' | 'link' | 'menu';

  /** 路由 meta，对齐后端 MenuMetaVO */
  export interface MenuMeta {
    title: string;
    icon?: string;
    activeIcon?: string;
    order?: number;
    keepAlive?: boolean;
    affixTab?: boolean;
    hideInMenu?: boolean;
    hideChildrenInMenu?: boolean;
    hideInBreadcrumb?: boolean;
    hideInTab?: boolean;
    activePath?: string;
    /** type=link 时使用 */
    link?: string;
    /** type=embedded 时使用 */
    iframeSrc?: string;
    badgeType?: 'dot' | 'normal';
    badge?: string;
    badgeVariants?: BadgeVariant | string;
  }

  /** 菜单节点，对齐后端 MenuTreeVO + 前端表单字段 */
  export interface SystemMenu {
    id: number | string;
    /** 父菜单 ID，0 / null 表示根 */
    pid?: number | string | null;
    /** 路由 name，跨菜单唯一 */
    name?: string;
    type: MenuType;
    path?: string;
    component?: string;
    /** 权限标识（type=button 时必填，落库到 sys_menu.perms） */
    authCode?: string;
    /** 1 启用 / 0 禁用 */
    status: number;
    activePath?: string;
    sort?: number;
    remark?: string;
    meta?: MenuMeta;
    children?: SystemMenu[];

    /**
     * 表单内部使用的临时字段：
     * type=link 时映射为 meta.link，type=embedded 时映射为 meta.iframeSrc
     * 提交前由 form.vue 处理，不直接发到后端
     */
    linkSrc?: string;
  }

  /** 创建入参，对齐后端 MenuCreateRequest */
  export type MenuCreateParams = Omit<SystemMenu, 'children' | 'id'>;

  /** 更新入参，对齐后端 MenuUpdateRequest（所有字段可选） */
  export type MenuUpdateParams = Partial<MenuCreateParams>;
}

/** 菜单管理树（含按钮、含禁用） */
export async function getMenuList() {
  return requestClient.get<SystemMenuApi.SystemMenu[]>('/manager/menu');
}

/** 创建菜单，返回新建主键 */
export async function createMenu(data: SystemMenuApi.MenuCreateParams) {
  return requestClient.post<number | string>('/manager/menu', data);
}

/** 更新菜单 */
export async function updateMenu(
  id: number | string,
  data: SystemMenuApi.MenuUpdateParams,
) {
  return requestClient.put<void>(`/manager/menu/${id}`, data);
}

/** 删除菜单（存在子菜单时后端返回业务错误） */
export async function deleteMenu(id: number | string) {
  return requestClient.delete<void>(`/manager/menu/${id}`);
}

/** 路由 name 是否已存在；excludeId 不为空时排除该菜单 */
export async function isMenuNameExists(
  name: string,
  excludeId?: number | string,
) {
  return requestClient.get<boolean>('/manager/menu/check-name', {
    params: { name, id: excludeId },
  });
}

/** 路由 path 是否已存在；excludeId 不为空时排除该菜单 */
export async function isMenuPathExists(
  path: string,
  excludeId?: number | string,
) {
  return requestClient.get<boolean>('/manager/menu/check-path', {
    params: { path, id: excludeId },
  });
}
