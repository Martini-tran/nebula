import type { MenuRecordRaw } from '@nebula/types';

/** 精确匹配菜单路径，同时遵守父级隐藏和禁用状态。 */
export function findNavigationMenu(
  menus: MenuRecordRaw[],
  path: string,
): MenuRecordRaw | undefined {
  for (const menu of menus) {
    if (menu.show === false || menu.disabled) continue;
    if (menu.path === path) return menu;
    const child = findNavigationMenu(menu.children ?? [], path);
    if (child) return child;
  }
}

/** 仅从授权树选择可用叶子，避免跳转到虚拟业务域或无组件的目录。 */
export function firstNavigationMenu(
  menus: MenuRecordRaw[],
): MenuRecordRaw | undefined {
  for (const menu of menus) {
    if (menu.show === false || menu.disabled) continue;
    if (!menu.children?.length) return menu;
    const child = firstNavigationMenu(menu.children);
    if (child) return child;
  }
}
