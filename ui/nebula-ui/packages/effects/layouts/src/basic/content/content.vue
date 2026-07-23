<script lang="ts" setup>
import type { RouteLocationNormalizedLoadedGeneric } from 'vue-router';

import { unref, watch } from 'vue';
import { RouterView, useRoute } from 'vue-router';

import { ELEMENT_ID_MAIN_CONTENT } from '@nebula-core/shared/constants';

import { usePreferences } from '@nebula/preferences';
import { getTabKey, storeToRefs, useTabbarStore } from '@nebula/stores';

import { transformComponent, useLayoutHook } from '../../hooks';
import { IFrameRouterView } from '../../iframe';
import { RouteCachedPage, RouteCachedView } from '../../route-cached';

defineOptions({ name: 'LayoutContent' });

const tabbarStore = useTabbarStore();
const { keepAlive } = usePreferences();

const { getCachedTabs, getExcludeCachedTabs, renderRouteView } =
  storeToRefs(tabbarStore);

const { getEnabledTransition, getTransitionName } = useLayoutHook();

// 滚动发生在内容区 <main> 内部（不再是 window），路由切换时手动回到顶部，
// 否则从滚动过的长页面切走会保留上一页的滚动位置
const route = useRoute();
watch(
  () => route.path,
  () => {
    document.getElementById(ELEMENT_ID_MAIN_CONTENT)?.scrollTo({ top: 0 });
  },
);

/**
 * 是否显示component
 * @param route
 */
const showComponent = (route: RouteLocationNormalizedLoadedGeneric) => {
  return !route.meta.domCached && unref(renderRouteView);
};
</script>

<template>
  <div class="relative h-full">
    <IFrameRouterView />
    <RouteCachedView />
    <RouterView v-slot="{ Component, route }">
      <RouteCachedPage
        :component="Component"
        :route="route"
        v-if="route.meta.domCached"
      />
      <Transition
        v-if="getEnabledTransition && !route.meta.disableTransition"
        :name="getTransitionName(route)"
        appear
        mode="out-in"
      >
        <KeepAlive
          v-if="keepAlive"
          :exclude="getExcludeCachedTabs"
          :include="getCachedTabs"
        >
          <component
            :is="transformComponent(Component, route)"
            v-if="showComponent(route)"
            v-show="!route.meta.iframeSrc"
            :key="getTabKey(route)"
          />
        </KeepAlive>
        <component
          :is="Component"
          v-else-if="showComponent(route)"
          :key="getTabKey(route)"
        />
      </Transition>
      <template v-else>
        <KeepAlive
          v-if="keepAlive"
          :exclude="getExcludeCachedTabs"
          :include="getCachedTabs"
        >
          <component
            :is="transformComponent(Component, route)"
            v-if="showComponent(route)"
            v-show="!route.meta.iframeSrc"
            :key="getTabKey(route)"
          />
        </KeepAlive>
        <component
          :is="Component"
          v-else-if="showComponent(route)"
          :key="getTabKey(route)"
        />
      </template>
    </RouterView>
  </div>
</template>





