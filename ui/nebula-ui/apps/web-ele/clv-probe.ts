// 视觉隔离探针：复刻 EditorSideDock 完整结构（活动栏 side + 面板头部 tab + closeType），
// 量右侧各区配色/活动栏/关闭按钮的真实 DOM，用于定位「深色不合主题 + 无关闭按钮」。
import { createApp, defineComponent, h, nextTick, onMounted, reactive, ref, shallowRef } from 'vue';
import {
  CodeLayout,
  CodeLayoutRootGrid,
  defaultCodeLayoutConfig,
} from 'vue-code-layout';
import 'vue-code-layout/lib/vue-code-layout.css';

function icon(char: string) {
  return () => h('span', { style: 'font-size:16px;line-height:1' }, char);
}

// 浅色主题变量覆盖（贴合 element-plus）：把 CodeLayout 整套深色 :root 变量在 dock 作用域内改浅色
const LIGHT_VARS = [
  '--code-layout-color-background:#ffffff',
  '--code-layout-color-background-second:#f5f7fa',
  '--code-layout-color-background-light:#ebeef5',
  '--code-layout-color-background-highlight:#ecf5ff',
  '--code-layout-color-background-hover:#f2f6fc',
  '--code-layout-color-background-hover-light:#e6e8eb',
  '--code-layout-color-highlight:#409eff',
  '--code-layout-color-text:#303133',
  '--code-layout-color-text-light:#000000',
  '--code-layout-color-text-highlight:#409eff',
  '--code-layout-color-text-gray:#909399',
  '--code-layout-color-text-disabled:#c0c4cc',
  '--code-layout-color-border:#dcdfe6',
  '--code-layout-color-border-light:#e4e7ed',
  '--code-layout-color-border-background:#ebeef5',
  '--code-layout-color-shadow:rgba(0,0,0,.08)',
].map((v) => v + ';').join('');

const SideDock = defineComponent({
  name: 'SideDockV',
  setup(_, { slots }) {
    const layoutRef = ref<any>();
    const layoutData = shallowRef(new CodeLayoutRootGrid());
    // 方案改用 primarySideBar 放右侧 + 主 activityBar：主活动栏才平铺图标+点击toggle，
    // 视觉/交互符合诉求；次级侧栏的活动栏会把 group 收进汉堡溢出菜单，不合适。
    const config = reactive({
      ...defaultCodeLayoutConfig,
      titleBar: false, menuBar: false, statusBar: false,
      activityBar: true,
      primarySideBar: true, primarySideBarPosition: 'left',
      secondarySideBar: false, bottomPanel: false,
      primarySideBarWidth: 34,
    });
    onMounted(async () => {
      await nextTick();
      const inst = layoutRef.value;
      if (!inst) return;
      (window as any).__inst = inst;
      // 两个 group 放 primarySideBar：主活动栏平铺「配置 / AI 生成」两个图标，点击 toggle 开关。
      const gConfig = inst.addGroup({ name: 'g-config', title: '配置', tooltip: '节点配置', iconLarge: icon('⚙'), tabStyle: 'text' }, 'primarySideBar');
      gConfig.addPanel({ name: 'config', title: '配置', iconSmall: icon('⚙') });
      const gAi = inst.addGroup({ name: 'g-ai', title: 'AI 生成', tooltip: 'AI 对话', iconLarge: icon('✨'), tabStyle: 'text' }, 'primarySideBar');
      gAi.addPanel({ name: 'ai', title: 'AI 生成', iconSmall: icon('✨') });
      await nextTick();
      inst.relayoutAll?.();
    });
    return () =>
      h('div', { class: 'editor-dock', style: 'position:relative;flex:1;min-width:0;min-height:0;overflow:hidden;' + LIGHT_VARS }, [
        h(CodeLayout,
          { ref: layoutRef, layoutConfig: config, layoutData: layoutData.value },
          {
            centerArea: () => slots.center?.(),
            panelRender: ({ panel }: any) => h('div', { style: 'width:100%;height:100%;padding:8px;background:#fff;color:#333;' }, `面板：${panel.name}`),
          }),
      ]);
  },
});

const Root = defineComponent({
  setup() {
    return () =>
      h('div', { style: 'position:absolute;inset:0;display:flex;flex-direction:column;' }, [
        h('div', { style: 'height:48px;flex:0 0 auto;background:#eee;line-height:48px;padding:0 12px;' }, '工具栏'),
        h('div', { style: 'display:flex;min-height:0;flex:1;' }, [
          h('div', { style: 'width:120px;flex:0 0 auto;background:#f5f5f5;' }, '节点面板'),
          h(SideDock, null, {
            center: () => h('div', { style: 'position:relative;height:100%;width:100%;background:#eaf4ff;border:2px solid #bcd;' }, '画布'),
          }),
        ]),
      ]);
  },
});

createApp(Root).mount('#app');

(window as any).__diag = () => {
  const info = (sel: string) => {
    const el = document.querySelector(sel) as HTMLElement | null;
    if (!el) return null;
    const cs = getComputedStyle(el);
    const r = el.getBoundingClientRect();
    return { bg: cs.backgroundColor, color: cs.color, w: Math.round(r.width), h: Math.round(r.height) };
  };
  // 活动栏、面板头部、tab、关闭按钮
  const activityBars = Array.from(document.querySelectorAll('.code-layout-activity-bar')).map((e) => {
    const el = e as HTMLElement; const r = el.getBoundingClientRect(); const cs = getComputedStyle(el);
    return { cls: el.className, bg: cs.backgroundColor, w: Math.round(r.width), h: Math.round(r.height), items: el.querySelectorAll('.item').length };
  });
  return {
    root: info('.code-layout-root'),
    activity: info('.code-layout-activity'),
    activityBars,
    secondaryArea: info('.code-layout-inner-0'),
    group: info('.code-layout-group'),
    // tab 列表 & 关闭按钮探测
    tabList: info('.code-layout-group .code-layout-split-tab-list, .code-layout-group .tab-list, .code-layout-group [class*="tab"]'),
    closeButtons: document.querySelectorAll('[class*="close"], .code-layout-actions [class*="close"]').length,
    rightActivityBarHtml: (document.querySelector('.code-layout-activity-bar.right') as HTMLElement | null)?.innerHTML?.slice(0, 800) ?? null,
    secondarySideBarChildrenCount: (() => {
      // 通过全局暴露的 inst 读 grid children 数
      const inst = (window as any).__inst;
      if (!inst) return 'no-inst';
      try { return inst.getRootGrid('secondarySideBar')?.children?.length ?? 'no-grid'; } catch (e) { return 'err:' + (e as Error).message; }
    })(),
    // 面板头部（含关闭）常见 class
    panelHeaderHtml: (document.querySelector('.code-layout-group') as HTMLElement | null)?.querySelector('[class*="header"],[class*="tab-list"],[class*="title"]')?.outerHTML?.slice(0, 400) ?? null,
  };
};
