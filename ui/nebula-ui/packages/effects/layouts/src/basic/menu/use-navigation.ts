import type { RouteRecordNormalized } from 'vue-router';

import type { MenuRecordRaw } from '@nebula/types';

import { onUnmounted } from 'vue';
import { useRouter } from 'vue-router';

import { isHttpUrl, openRouteInNewWindow, openWindow } from '@nebula/utils';

function useNavigation() {
  const router = useRouter();
  const routeMetaMap = new Map<string, RouteRecordNormalized>();

  // 初始化路由映射
  const initRouteMetaMap = () => {
    const routes = router.getRoutes();
    routes.forEach((route) => {
      routeMetaMap.set(route.path, route);
    });
  };

  initRouteMetaMap();

  // 监听路由变化
  const removeAfterEach = router.afterEach(() => {
    initRouteMetaMap();
  });
  onUnmounted(removeAfterEach);

  // 检查是否应该在新窗口打开
  const shouldOpenInNewWindow = (path: string): boolean => {
    if (isHttpUrl(path)) {
      return true;
    }
    const route = routeMetaMap.get(path);
    // 如果有外链或者设置了在新窗口打开，返回 true
    return !!(route?.meta?.link || route?.meta?.openInNewWindow);
  };

  const navigation = async (
    path: string,
    menuQuery?: MenuRecordRaw['query'],
  ) => {
    try {
      const route = routeMetaMap.get(path);
      const { openInNewWindow = false, query = {}, link } = route?.meta ?? {};

      // 检查是否有外链
      if (link && typeof link === 'string') {
        openWindow(link, { target: '_blank' });
        return;
      }

      if (isHttpUrl(path)) {
        openWindow(path, { target: '_blank' });
      } else if (openInNewWindow) {
        openRouteInNewWindow(
          router.resolve({ path, query: menuQuery ?? query }).href,
        );
      } else {
        await router.push({
          path,
          query: menuQuery ?? query,
        });
      }
    } catch (error) {
      console.error('Navigation failed:', error);
      throw error;
    }
  };

  const willOpenedByWindow = (path: string) => {
    return shouldOpenInNewWindow(path);
  };

  return { navigation, willOpenedByWindow };
}

export { useNavigation };
