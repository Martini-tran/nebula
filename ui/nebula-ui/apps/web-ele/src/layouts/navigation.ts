import type { DualSidebarGroup } from '@nebula/layouts';
import type { MenuRecordRaw } from '@nebula/types';

/** 业务域只负责呈现；叶子菜单及权限仍以 accessMenus 为准。 */
const domains = [
  {
    id: 'workspace',
    name: '工作台',
    label: '工作台',
    icon: 'lucide:house',
    paths: ['/dashboard', '/analytics', '/workspace'],
  },
  {
    id: 'ai',
    name: 'AI 工作室',
    label: 'AI',
    icon: 'lucide:sparkles',
    paths: [
      '/ai-agent',
      '/ai-flow',
      '/ai-chat',
      '/ai-knowledge',
      '/ai-tool',
      '/ai-mcp-server',
      '/ai-model-profile',
      '/ai-prompt',
      '/ai-skill',
    ],
  },
  {
    id: 'relay',
    name: 'AI 中转管理',
    label: '中转',
    icon: 'lucide:boxes',
    paths: ['/ai-relay'],
  },
  {
    id: 'blog',
    name: '内容管理',
    label: '内容',
    icon: 'lucide:file-text',
    paths: ['/blog'],
  },
  {
    id: 'space',
    name: '我的空间',
    label: '空间',
    icon: 'lucide:folder',
    paths: ['/space'],
  },
  {
    id: 'forge',
    name: '插件中心',
    label: '插件',
    icon: 'lucide:blocks',
    paths: ['/forge'],
  },
  {
    id: 'system',
    name: '系统管理',
    label: '系统',
    icon: 'lucide:settings',
    paths: ['/system'],
  },
];

function domainForPath(path: string) {
  return domains.find((domain) =>
    domain.paths.some(
      (prefix) => path === prefix || path.startsWith(`${prefix}/`),
    ),
  );
}

function cloneVisible(menu: MenuRecordRaw): MenuRecordRaw | undefined {
  if (menu.show === false) return;
  const children = menu.children
    ?.map((child) => cloneVisible(child))
    .filter((child): child is MenuRecordRaw => !!child);
  if (menu.children?.length && !children?.length) return;
  return { ...menu, children };
}

/** 不注册虚拟路由，不向权限树注入静态页面；未知模块保留为独立业务域。 */
export function buildNavigationGroups(
  accessMenus: MenuRecordRaw[],
): DualSidebarGroup[] {
  const groups = new Map<string, DualSidebarGroup>();
  const unknown: DualSidebarGroup[] = [];
  for (const original of accessMenus) {
    const menu = cloneVisible(original);
    if (!menu) continue;
    const childDomains = menu.children?.map((child) =>
      domainForPath(child.path),
    );
    const firstChildDomain = childDomains?.[0];
    const domain =
      domainForPath(menu.path) ??
      (firstChildDomain &&
      childDomains?.every((item) => item?.id === firstChildDomain.id)
        ? firstChildDomain
        : undefined);
    if (!domain) {
      unknown.push({
        id: `custom:${menu.path}`,
        name: menu.name,
        label: menu.name,
        icon: menu.icon,
        menus: [menu],
      });
      continue;
    }
    let group = groups.get(domain.id);
    if (!group) {
      group = {
        id: domain.id,
        name: domain.name,
        label: domain.label,
        icon: domain.icon,
        menus: [],
      };
      groups.set(domain.id, group);
    }
    // 一项目录去掉重复标题；禁用父级仍保持原层级。
    const child = menu.children?.[0];
    group.menus.push(
      !menu.disabled && menu.children?.length === 1 && child ? child : menu,
    );
  }
  return [
    ...domains.flatMap((domain) => groups.get(domain.id) ?? []),
    ...unknown,
  ];
}
