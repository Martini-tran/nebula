import type { Router } from 'vue-router';

import { LOGIN_PATH } from '@nebula/constants';
import { preferences, updatePreferences } from '@nebula/preferences';
import { useAccessStore, useUserStore } from '@nebula/stores';
import { startProgress, stopProgress } from '@nebula/utils';

import { getMenuPermsApi } from '#/api';
import { accessRoutes, coreRouteNames } from '#/router/routes';
import { useAuthStore } from '#/store';

import { generateAccess } from './access';

/**
 * 通用守卫配置
 * @param router
 */
function setupCommonGuard(router: Router) {
  // 记录已经加载的页面
  const loadedPaths = new Set<string>();

  router.beforeEach((to) => {
    to.meta.loaded = loadedPaths.has(to.path);

    // 页面加载进度条
    if (!to.meta.loaded && preferences.transition.progress) {
      startProgress();
    }
    return true;
  });

  router.afterEach((to) => {
    // 记录页面是否加载,如果已经加载，后续的页面切换动画等效果不在重复执行

    loadedPaths.add(to.path);

    // 关闭页面加载进度条
    if (preferences.transition.progress) {
      stopProgress();
    }
  });
}

/**
 * 权限访问守卫配置
 * @param router
 */
function setupAccessGuard(router: Router) {
  router.beforeEach(async (to, from) => {
    const accessStore = useAccessStore();
    const userStore = useUserStore();
    const authStore = useAuthStore();

    // 基本路由，这些路由不需要进入权限拦截
    if (coreRouteNames.includes(to.name as string)) {
      if (to.path === LOGIN_PATH && accessStore.accessToken) {
        return decodeURIComponent(
          (to.query?.redirect as string) ||
            userStore.userInfo?.homePath ||
            preferences.app.defaultHomePath,
        );
      }
      return true;
    }

    // accessToken 检查
    if (!accessStore.accessToken) {
      // 明确声明忽略权限访问权限，则可以访问
      if (to.meta.ignoreAccess) {
        return true;
      }

      // 没有访问权限，跳转登录页面
      if (to.fullPath !== LOGIN_PATH) {
        return {
          path: LOGIN_PATH,
          // 如不需要，直接删除 query
          query:
            to.fullPath === preferences.app.defaultHomePath
              ? {}
              : { redirect: encodeURIComponent(to.fullPath) },
          // 携带当前跳转的页面，登录后重新跳转该页面
          replace: true,
        };
      }
      return to;
    }

    // 是否已经生成过动态路由
    if (accessStore.isAccessChecked) {
      return true;
    }

    // 生成路由表
    // 当前登录用户拥有的角色标识列表
    const userInfo = userStore.userInfo || (await authStore.fetchUserInfo());
    const userRoles = userInfo.roles ?? [];

    // 生成菜单和路由
    const { accessibleMenus, accessibleRoutes } = await generateAccess({
      roles: userRoles,
      router,
      // 则会在菜单中显示，但是访问会被重定向到403
      routes: accessRoutes,
    });

    // 同步拉取按钮权限码，保证刷新后与后端配置一致
    const perms = await getMenuPermsApi().catch(() => [] as string[]);
    accessStore.setAccessCodes(perms ?? []);

    // 保存菜单信息和路由信息
    accessStore.setAccessMenus(accessibleMenus);
    accessStore.setAccessRoutes(accessibleRoutes);
    accessStore.setIsAccessChecked(true);
    const redirectPath = (from.query.redirect ??
      (to.path === preferences.app.defaultHomePath
        ? userInfo.homePath || preferences.app.defaultHomePath
        : to.fullPath)) as string;

    return {
      ...router.resolve(decodeURIComponent(redirectPath)),
      replace: true,
    };
  });
}

/**
 * AI 流程编辑器独占全屏布局守卫。
 *
 * 编辑器需要 full-content 布局（隐藏侧边/顶栏/tab）。进出全屏若挂在编辑器组件
 * 生命周期上并不可靠：布局内容包在 KeepAlive 里（tabbar.keepAlive 默认开），
 * 路由离开时组件只被缓存不卸载，onBeforeUnmount 不触发；且 updatePreferences
 * 会持久化，一旦漏恢复就跨刷新卡死在无菜单的全屏。故统一收到路由层：
 *   - 进入编辑器：把原布局记进 localStorage 标记，再切 full-content；
 *   - 导航到任何非编辑器路由（含应用首次加载）：只要标记存在就恢复并清标记，
 *     上次异常退出（刷新/崩溃）遗留的全屏也能自愈。
 * 标记仅由本守卫读写；用户在偏好设置里主动选的「内容全屏」不产生标记、不受影响。
 */
const FLOW_EDITOR_LAYOUT_KEY = 'nebula-ai-flow-editor-prev-layout';

function setupFlowEditorLayoutGuard(router: Router) {
  type AppLayout = typeof preferences.app.layout;

  router.afterEach((to) => {
    if (to.name === 'AiFlowEditor') {
      if (preferences.app.layout !== 'full-content') {
        localStorage.setItem(FLOW_EDITOR_LAYOUT_KEY, preferences.app.layout);
        updatePreferences({ app: { layout: 'full-content' } });
      }
      return;
    }

    const prev = localStorage.getItem(FLOW_EDITOR_LAYOUT_KEY);
    if (prev === null) return;
    localStorage.removeItem(FLOW_EDITOR_LAYOUT_KEY);
    if (preferences.app.layout === 'full-content') {
      // 标记值异常（如手改过 localStorage）时退回默认布局兜底
      const layout =
        prev && prev !== 'full-content' ? (prev as AppLayout) : 'sidebar-nav';
      updatePreferences({ app: { layout } });
    }
  });
}

/**
 * 项目守卫配置
 * @param router
 */
function createRouterGuard(router: Router) {
  /** 通用 */
  setupCommonGuard(router);
  /** 权限访问 */
  setupAccessGuard(router);
  /** AI 流程编辑器全屏布局进出 */
  setupFlowEditorLayoutGuard(router);
}

export { createRouterGuard };






