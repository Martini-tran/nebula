import { createApp, defineComponent, h } from 'vue';
import '@nebula/styles';
import '@nebula/styles/ele';
import { initComponentAdapter } from '../../src/adapter/component';
import { initSetupnebulaForm } from '../../src/adapter/form';
import { usenebulaVxeGrid } from '../../src/adapter/vxe-table';
import { setupI18n } from '../../src/locales';

await initComponentAdapter();
await initSetupnebulaForm();
const Case = defineComponent({
  props: { count: { type: Number, required: true } },
  setup(props) {
    const [Grid] = usenebulaVxeGrid({
      tableTitle: `${props.count} 个搜索条件`,
      formOptions: {
        collapsed: true,
        schema: Array.from({ length: props.count }, (_, index) => ({
          fieldName: `field${index}`,
          label: `条件 ${index + 1}`,
          component: 'Input',
        })),
      },
      gridOptions: {
        columns: [{ field: 'name', title: '名称' }],
        data: [{ name: '本地组件回归检查，无后端请求' }],
        proxyConfig: { enabled: false },
        pagerConfig: { enabled: true, total: 1 },
        toolbarConfig: { refresh: true, custom: true, zoom: true, search: true },
      },
    });
    return () => h('section', { id: `case-${props.count}`, style: 'margin-bottom: 24px' }, [h(Grid)]);
  },
});
const app = createApp({
  render: () => h('main', { style: 'padding: 24px; background: #f5f7f9' }, [2, 3, 4].map((count) => h(Case, { count }))),
});
await setupI18n(app);
app.mount('#app');
