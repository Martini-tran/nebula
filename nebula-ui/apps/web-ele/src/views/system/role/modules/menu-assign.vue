<script lang="ts" setup>
import { computed, ref, watch } from 'vue';

import {
  ElButton,
  ElCheckbox,
  ElDialog,
  ElEmpty,
  ElMessage,
  ElTree,
} from 'element-plus';

import { getMenuList, type SystemMenuApi } from '#/api/system/menu';
import {
  assignSystemRoleMenusApi,
  getSystemRoleMenuIdsApi,
  type SystemRoleApi,
} from '#/api/system/role';

defineOptions({ name: 'SystemRoleMenuAssign' });

const props = defineProps<{
  modelValue: boolean;
  role?: null | SystemRoleApi.RoleListItem;
}>();

const emit = defineEmits<{
  'update:modelValue': [boolean];
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
});

interface MenuNode {
  id: number | string;
  label: string;
  type: SystemMenuApi.MenuType;
  disabled: boolean;
  children?: MenuNode[];
}

const treeData = ref<MenuNode[]>([]);
const allMenuIds = ref<Array<number | string>>([]);
const checkedKeys = ref<Array<number | string>>([]);
const loading = ref(false);
const submitting = ref(false);
/** 树父子联动；关闭后可单独勾选父或子 */
const strictly = ref(false);

const treeRef = ref<InstanceType<typeof ElTree>>();

function flattenIds(nodes: SystemMenuApi.SystemMenu[], out: Array<number | string>) {
  for (const n of nodes) {
    out.push(n.id);
    if (n.children?.length) flattenIds(n.children, out);
  }
}

function toMenuNode(n: SystemMenuApi.SystemMenu): MenuNode {
  return {
    id: n.id,
    label: n.meta?.title || n.name || String(n.id),
    type: n.type,
    disabled: n.status === 0,
    children: n.children?.length ? n.children.map(toMenuNode) : undefined,
  };
}

async function loadTree() {
  loading.value = true;
  try {
    const data = (await getMenuList()) ?? [];
    treeData.value = data.map(toMenuNode);
    const ids: Array<number | string> = [];
    flattenIds(data, ids);
    allMenuIds.value = ids;
  } finally {
    loading.value = false;
  }
}

async function loadChecked(roleId: number | string) {
  const ids = (await getSystemRoleMenuIdsApi(roleId)) ?? [];
  checkedKeys.value = ids;
}

watch(
  () => props.modelValue,
  async (open) => {
    if (!open || !props.role) return;
    checkedKeys.value = [];
    await loadTree();
    await loadChecked(props.role.id);
  },
);

function selectAll() {
  if (!treeRef.value) return;
  treeRef.value.setCheckedKeys(allMenuIds.value);
}

function clearAll() {
  if (!treeRef.value) return;
  treeRef.value.setCheckedKeys([]);
}

async function submit() {
  if (!props.role || !treeRef.value) return;
  const checked = treeRef.value.getCheckedKeys() as Array<number | string>;
  const halfChecked = treeRef.value.getHalfCheckedKeys() as Array<number | string>;
  // 父级为半选时，权限上一般也要带上父级，否则后端按勾选 ID 判断时父菜单可能不可见
  const merged = Array.from(new Set([...checked, ...halfChecked]));
  submitting.value = true;
  try {
    await assignSystemRoleMenusApi(props.role.id, merged);
    ElMessage.success('授权已保存');
    visible.value = false;
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <ElDialog
    v-model="visible"
    :close-on-click-modal="false"
    :title="`分配菜单权限：${role?.roleName ?? ''}`"
    width="560"
    top="6vh"
  >
    <div class="mb-3 flex items-center justify-between">
      <div class="flex items-center gap-2">
        <ElButton link type="primary" @click="selectAll">全选</ElButton>
        <ElButton link type="info" @click="clearAll">清空</ElButton>
      </div>
      <ElCheckbox v-model="strictly">父子节点独立勾选</ElCheckbox>
    </div>

    <div
      v-loading="loading"
      class="max-h-[55vh] overflow-auto rounded border p-2"
    >
      <ElEmpty v-if="!loading && treeData.length === 0" description="暂无菜单" />
      <ElTree
        v-else
        ref="treeRef"
        :check-strictly="strictly"
        :data="treeData"
        :default-checked-keys="checkedKeys"
        :props="{ label: 'label', children: 'children', disabled: 'disabled' }"
        default-expand-all
        node-key="id"
        show-checkbox
      />
    </div>

    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton :loading="submitting" type="primary" @click="submit">
        保存授权
      </ElButton>
    </template>
  </ElDialog>
</template>
