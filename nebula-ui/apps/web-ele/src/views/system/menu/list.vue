<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@nebula/common-ui';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElSwitch,
  ElTag,
  ElTreeSelect,
} from 'element-plus';

import { usenebulaVxeGrid } from '#/adapter/vxe-table';
import {
  createMenu,
  deleteMenu,
  getMenuList,
  SystemMenuApi,
  updateMenu,
} from '#/api/system/menu';
import { componentKeys } from '#/router/routes';

defineOptions({ name: 'SystemMenu' });

// ===== 静态选项 =====
const TYPE_OPTIONS: Array<{
  label: string;
  tagType: '' | 'danger' | 'info' | 'primary' | 'success' | 'warning';
  value: SystemMenuApi.MenuType;
}> = [
  { label: '目录', tagType: 'primary', value: 'catalog' },
  { label: '菜单', tagType: '', value: 'menu' },
  { label: '按钮', tagType: 'danger', value: 'button' },
  { label: '内嵌', tagType: 'success', value: 'embedded' },
  { label: '外链', tagType: 'warning', value: 'link' },
];
const BADGE_TYPE_OPTIONS = [
  { label: '无', value: '' },
  { label: '小圆点', value: 'dot' },
  { label: '文本', value: 'normal' },
];
const BADGE_VARIANT_OPTIONS = SystemMenuApi.BadgeVariants.map((v) => ({
  label: v,
  value: v,
}));

function typeLabel(type?: string) {
  return TYPE_OPTIONS.find((o) => o.value === type)?.label ?? type ?? '';
}
function typeTagType(type?: string) {
  return TYPE_OPTIONS.find((o) => o.value === type)?.tagType ?? '';
}

// ===== 表格 =====
const gridOptions: VxeTableGridOptions<SystemMenuApi.SystemMenu> = {
  columns: [
    {
      align: 'left',
      field: 'meta.title',
      title: '标题',
      treeNode: true,
      minWidth: 240,
      slots: { default: 'title' },
    },
    {
      field: 'type',
      title: '类型',
      width: 90,
      slots: { default: 'type' },
    },
    { field: 'authCode', title: '权限标识', minWidth: 180 },
    { field: 'path', title: '路径', minWidth: 180 },
    { field: 'component', title: '组件', minWidth: 200 },
    {
      field: 'status',
      title: '状态',
      width: 80,
      slots: { default: 'status' },
    },
    { field: 'sort', title: '排序', width: 70 },
    {
      field: 'action',
      title: '操作',
      width: 240,
      fixed: 'right',
      slots: { default: 'action' },
    },
  ],
  data: [],
  height: 'auto',
  pagerConfig: { enabled: false },
  proxyConfig: { enabled: false },
  rowConfig: { keyField: 'id' },
  toolbarConfig: { custom: true, refresh: { code: 'query' }, zoom: true },
  treeConfig: {
    parentField: 'pid',
    rowField: 'id',
    transform: false,
    expandAll: true,
  },
};

const [Grid, gridApi] = usenebulaVxeGrid({ gridOptions });
const treeData = ref<SystemMenuApi.SystemMenu[]>([]);

async function reloadGrid() {
  const data = await getMenuList();
  treeData.value = data ?? [];
  await gridApi.setGridOptions({ data: treeData.value });
}
onMounted(reloadGrid);

/**
 * 父级菜单可选项：去掉按钮（按钮不能做父）和当前正在编辑的子树（避免循环）
 */
const parentOptions = computed(() => {
  const exclude = new Set<number | string>();
  if (form.id != null) {
    collectIds(form.id, treeData.value, exclude);
  }
  const filter = (
    nodes: SystemMenuApi.SystemMenu[],
  ): SystemMenuApi.SystemMenu[] =>
    nodes
      .filter((n) => n.type !== 'button' && !exclude.has(n.id))
      .map((n) => ({
        ...n,
        children: n.children ? filter(n.children) : undefined,
      }));
  const root: SystemMenuApi.SystemMenu = {
    id: 0,
    type: 'catalog',
    status: 1,
    meta: { title: '根节点' },
    children: filter(treeData.value),
  } as SystemMenuApi.SystemMenu;
  return [root];
});

function collectIds(
  rootId: number | string,
  nodes: SystemMenuApi.SystemMenu[],
  out: Set<number | string>,
) {
  for (const n of nodes) {
    if (n.id === rootId) {
      out.add(n.id);
      walk(n.children ?? [], out);
      return true;
    }
    if (n.children && collectIds(rootId, n.children, out)) return true;
  }
  return false;
}
function walk(nodes: SystemMenuApi.SystemMenu[], out: Set<number | string>) {
  for (const n of nodes) {
    out.add(n.id);
    if (n.children) walk(n.children, out);
  }
}

// ===== 表单状态 =====
type FormState = {
  id: null | number | string;
  pid: null | number | string;
  type: SystemMenuApi.MenuType;
  name: string;
  metaTitle: string;
  path: string;
  component: string;
  authCode: string;
  linkSrc: string;
  activePath: string;
  metaIcon: string;
  metaActiveIcon: string;
  keepAlive: boolean;
  affixTab: boolean;
  hideInMenu: boolean;
  hideChildrenInMenu: boolean;
  hideInBreadcrumb: boolean;
  hideInTab: boolean;
  badgeType: '' | 'dot' | 'normal';
  badge: string;
  badgeVariants: string;
  status: number;
  sort: number;
  remark: string;
};

function blankForm(): FormState {
  return {
    id: null,
    pid: 0,
    type: 'menu',
    name: '',
    metaTitle: '',
    path: '',
    component: '',
    authCode: '',
    linkSrc: '',
    activePath: '',
    metaIcon: '',
    metaActiveIcon: '',
    keepAlive: false,
    affixTab: false,
    hideInMenu: false,
    hideChildrenInMenu: false,
    hideInBreadcrumb: false,
    hideInTab: false,
    badgeType: '',
    badge: '',
    badgeVariants: '',
    status: 1,
    sort: 0,
    remark: '',
  };
}

const dialogVisible = ref(false);
const dialogTitle = ref('新增菜单');
const submitLoading = ref(false);
const form = reactive<FormState>(blankForm());
const formRef = ref<FormInstance>();

const showPath = computed(() =>
  ['catalog', 'embedded', 'menu'].includes(form.type),
);
const showComponent = computed(() => form.type === 'menu');
const showLinkSrc = computed(() =>
  ['embedded', 'link'].includes(form.type),
);
const showAuthCode = computed(() =>
  ['button', 'catalog', 'embedded', 'menu'].includes(form.type),
);
const showActivePath = computed(() =>
  ['embedded', 'menu'].includes(form.type),
);
const showIcon = computed(() => form.type !== 'button');
const showAdvanced = computed(() => !['button', 'link'].includes(form.type));

const formRules = computed<FormRules>(() => ({
  name: [
    { required: true, message: '请输入路由名', trigger: 'blur' },
    { min: 2, max: 50, message: '长度 2-50', trigger: 'blur' },
  ],
  metaTitle: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  path: showPath.value
    ? [
        { required: true, message: '请输入路径', trigger: 'blur' },
        {
          validator: (_r, v, cb) =>
            !v || v.startsWith('/')
              ? cb()
              : cb(new Error('路径必须以 / 开头')),
          trigger: 'blur',
        },
      ]
    : [],
  component: showComponent.value
    ? [{ required: true, message: '请选择组件', trigger: 'change' }]
    : [],
  authCode:
    form.type === 'button'
      ? [{ required: true, message: '请输入权限标识', trigger: 'blur' }]
      : [],
  linkSrc: showLinkSrc.value
    ? [{ required: true, message: '请输入地址', trigger: 'blur' }]
    : [],
}));

function openCreate(parent?: SystemMenuApi.SystemMenu) {
  Object.assign(form, blankForm());
  if (parent) form.pid = parent.id;
  dialogTitle.value = parent ? `新增「${parent.meta?.title}」下级` : '新增菜单';
  formRef.value?.clearValidate();
  dialogVisible.value = true;
}

function openEdit(row: SystemMenuApi.SystemMenu) {
  Object.assign(form, blankForm());
  form.id = row.id;
  form.pid = (row.pid ?? 0) as number | string;
  form.type = row.type;
  form.name = row.name ?? '';
  form.metaTitle = row.meta?.title ?? '';
  form.path = row.path ?? '';
  form.component = row.component ?? '';
  form.authCode = row.authCode ?? '';
  form.linkSrc =
    row.type === 'link'
      ? (row.meta?.link ?? '')
      : row.type === 'embedded'
        ? (row.meta?.iframeSrc ?? '')
        : '';
  form.activePath = row.activePath ?? row.meta?.activePath ?? '';
  form.metaIcon = row.meta?.icon ?? '';
  form.metaActiveIcon = row.meta?.activeIcon ?? '';
  form.keepAlive = row.meta?.keepAlive ?? false;
  form.affixTab = row.meta?.affixTab ?? false;
  form.hideInMenu = row.meta?.hideInMenu ?? false;
  form.hideChildrenInMenu = row.meta?.hideChildrenInMenu ?? false;
  form.hideInBreadcrumb = row.meta?.hideInBreadcrumb ?? false;
  form.hideInTab = row.meta?.hideInTab ?? false;
  form.badgeType = (row.meta?.badgeType ?? '') as '' | 'dot' | 'normal';
  form.badge = row.meta?.badge ?? '';
  form.badgeVariants = row.meta?.badgeVariants ?? '';
  form.status = row.status ?? 1;
  form.sort = row.sort ?? 0;
  form.remark = row.remark ?? '';
  dialogTitle.value = `编辑「${row.meta?.title ?? row.name}」`;
  formRef.value?.clearValidate();
  dialogVisible.value = true;
}

function buildPayload(): SystemMenuApi.MenuCreateParams {
  const meta: SystemMenuApi.MenuMeta = {
    title: form.metaTitle,
    icon: form.metaIcon || undefined,
    activeIcon: form.metaActiveIcon || undefined,
  };
  if (form.type === 'link') meta.link = form.linkSrc || undefined;
  if (form.type === 'embedded') meta.iframeSrc = form.linkSrc || undefined;
  if (showAdvanced.value) {
    meta.keepAlive = form.keepAlive;
    meta.affixTab = form.affixTab;
    meta.hideInMenu = form.hideInMenu;
    meta.hideChildrenInMenu = form.hideChildrenInMenu;
    meta.hideInBreadcrumb = form.hideInBreadcrumb;
    meta.hideInTab = form.hideInTab;
  }
  if (form.badgeType) {
    meta.badgeType = form.badgeType;
    if (form.badgeType === 'normal') meta.badge = form.badge || undefined;
    meta.badgeVariants = form.badgeVariants || undefined;
  }

  return {
    pid: form.pid ?? 0,
    type: form.type,
    name: form.name,
    path: showPath.value ? form.path : undefined,
    component: showComponent.value ? form.component : undefined,
    authCode: showAuthCode.value ? form.authCode || undefined : undefined,
    activePath: showActivePath.value
      ? form.activePath || undefined
      : undefined,
    status: form.status,
    sort: form.sort,
    remark: form.remark || undefined,
    meta,
  };
}

async function submitForm() {
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;

  submitLoading.value = true;
  try {
    const payload = buildPayload();
    if (form.id == null) {
      await createMenu(payload);
      ElMessage.success('创建成功');
    } else {
      await updateMenu(form.id, payload);
      ElMessage.success('保存成功');
    }
    dialogVisible.value = false;
    await reloadGrid();
  } finally {
    submitLoading.value = false;
  }
}

async function handleDelete(row: SystemMenuApi.SystemMenu) {
  try {
    await ElMessageBox.confirm(
      `确认删除「${row.meta?.title ?? row.name}」？`,
      '提示',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  await deleteMenu(row.id);
  ElMessage.success('删除成功');
  await reloadGrid();
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-tools>
        <ElButton type="primary" @click="openCreate()">新增菜单</ElButton>
      </template>

      <template #title="{ row }">
        <span>{{ row.meta?.title || row.name }}</span>
      </template>

      <template #type="{ row }">
        <ElTag :type="typeTagType(row.type)" size="small">
          {{ typeLabel(row.type) }}
        </ElTag>
      </template>

      <template #status="{ row }">
        <ElTag :type="row.status === 1 ? 'success' : 'info'" size="small">
          {{ row.status === 1 ? '正常' : '禁用' }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-2">
          <ElButton link type="primary" @click="openCreate(row)">
            新增下级
          </ElButton>
          <ElButton link type="primary" @click="openEdit(row)">编辑</ElButton>
          <ElButton link type="danger" @click="handleDelete(row)">
            删除
          </ElButton>
        </div>
      </template>
    </Grid>

    <ElDialog
      v-model="dialogVisible"
      :close-on-click-modal="false"
      :title="dialogTitle"
      width="640"
    >
      <ElForm
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="100px"
      >
        <ElFormItem label="类型" prop="type">
          <ElRadioGroup v-model="form.type">
            <ElRadioButton
              v-for="opt in TYPE_OPTIONS"
              :key="opt.value"
              :value="opt.value"
            >
              {{ opt.label }}
            </ElRadioButton>
          </ElRadioGroup>
        </ElFormItem>

        <ElFormItem label="父级菜单" prop="pid">
          <ElTreeSelect
            v-model="form.pid"
            :data="parentOptions"
            :props="{ label: 'meta.title', value: 'id', children: 'children' }"
            check-strictly
            default-expand-all
            style="width: 100%"
          />
        </ElFormItem>

        <ElFormItem label="路由名" prop="name">
          <ElInput v-model="form.name" placeholder="如 SystemMenu" />
        </ElFormItem>

        <ElFormItem label="标题" prop="metaTitle">
          <ElInput v-model="form.metaTitle" placeholder="支持 i18n key" />
        </ElFormItem>

        <ElFormItem v-if="showPath" label="路径" prop="path">
          <ElInput v-model="form.path" placeholder="必须以 / 开头" />
        </ElFormItem>

        <ElFormItem v-if="showComponent" label="组件" prop="component">
          <ElSelect
            v-model="form.component"
            allow-create
            default-first-option
            filterable
            placeholder="如 system/menu/list"
            style="width: 100%"
          >
            <ElOption
              v-for="key in componentKeys"
              :key="key"
              :label="key"
              :value="key"
            />
          </ElSelect>
        </ElFormItem>

        <ElFormItem v-if="showLinkSrc" label="地址" prop="linkSrc">
          <ElInput v-model="form.linkSrc" placeholder="https://..." />
        </ElFormItem>

        <ElFormItem v-if="showAuthCode" label="权限标识" prop="authCode">
          <ElInput v-model="form.authCode" placeholder="如 system:menu:list" />
        </ElFormItem>

        <ElFormItem v-if="showActivePath" label="高亮路径" prop="activePath">
          <ElInput v-model="form.activePath" placeholder="可选" />
        </ElFormItem>

        <ElFormItem v-if="showIcon" label="图标" prop="metaIcon">
          <ElInput v-model="form.metaIcon" placeholder="如 lucide:menu" />
        </ElFormItem>

        <ElFormItem v-if="showAdvanced" label="徽章">
          <div class="flex w-full gap-2">
            <ElSelect
              v-model="form.badgeType"
              placeholder="类型"
              style="flex: 0 0 110px"
            >
              <ElOption
                v-for="opt in BADGE_TYPE_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </ElSelect>
            <ElInput
              v-if="form.badgeType === 'normal'"
              v-model="form.badge"
              placeholder="文本"
              style="flex: 1"
            />
            <ElSelect
              v-if="form.badgeType"
              v-model="form.badgeVariants"
              clearable
              placeholder="样式"
              style="flex: 0 0 130px"
            >
              <ElOption
                v-for="opt in BADGE_VARIANT_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </ElSelect>
          </div>
        </ElFormItem>

        <ElFormItem v-if="showAdvanced" label="高级">
          <div class="flex flex-wrap items-center gap-x-6 gap-y-2">
            <label class="flex items-center gap-1">
              <ElSwitch v-model="form.keepAlive" />
              <span>缓存页面</span>
            </label>
            <label class="flex items-center gap-1">
              <ElSwitch v-model="form.affixTab" />
              <span>固定 Tab</span>
            </label>
            <label class="flex items-center gap-1">
              <ElSwitch v-model="form.hideInMenu" />
              <span>菜单中隐藏</span>
            </label>
            <label class="flex items-center gap-1">
              <ElSwitch v-model="form.hideChildrenInMenu" />
              <span>隐藏子菜单</span>
            </label>
            <label class="flex items-center gap-1">
              <ElSwitch v-model="form.hideInBreadcrumb" />
              <span>面包屑隐藏</span>
            </label>
            <label class="flex items-center gap-1">
              <ElSwitch v-model="form.hideInTab" />
              <span>多页签隐藏</span>
            </label>
          </div>
        </ElFormItem>

        <ElFormItem label="状态" prop="status">
          <ElRadioGroup v-model="form.status">
            <ElRadioButton :value="1">正常</ElRadioButton>
            <ElRadioButton :value="0">禁用</ElRadioButton>
          </ElRadioGroup>
        </ElFormItem>

        <ElFormItem label="排序" prop="sort">
          <ElInputNumber v-model="form.sort" :min="-9999" :max="9999" />
        </ElFormItem>

        <ElFormItem label="备注" prop="remark">
          <ElInput v-model="form.remark" :rows="2" type="textarea" />
        </ElFormItem>
      </ElForm>

      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton :loading="submitLoading" type="primary" @click="submitForm">
          确认
        </ElButton>
      </template>
    </ElDialog>
  </Page>
</template>
