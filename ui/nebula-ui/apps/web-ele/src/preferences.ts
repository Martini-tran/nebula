import { defineOverridesPreferences } from '@nebula/preferences';

/**
 * @description 项目配置文件
 * 只需要覆盖项目中的一部分配置，不需要的配置不用覆盖，会自动使用默认配置
 * !!! 更改配置后请清空缓存，否则可能不生效
 */
export const overridesPreferences = defineOverridesPreferences({
  // overrides
  app: {
    // 路由由后端 /system/menu/all 下发，前端 routes/modules 仅作 fallback
    accessMode: 'backend',
    name: import.meta.env.VITE_APP_TITLE,
  },
});






