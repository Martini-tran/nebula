<script lang="ts" setup>
/**
 * 开始节点入参编辑器（行内表格 + 每行可展开高级项）。
 *
 * 主行展示四个常用字段：名称 / 标识 / 类型 / 必填；展开区放高级字段：
 * 默认值 / 描述 / 示例值 / 校验规则。校验规则按类型分化：
 *   - String  → 最小/最大长度、正则、枚举
 *   - Number  → 最小/最大值、枚举
 *   - Boolean → 无校验项
 *   - File/Image → 大小上限(MB)、允许格式
 *   - Object/Array → 无结构化校验项（后续可扩展）
 *
 * 数据形如 StartInputParam[]，最终落到 node.data.nodeConfig.inputs。
 * 内部以「有序数组 + 稳定 uid」编辑，避免 v-for 复用错行。
 */
import { computed, ref } from 'vue';

import {
  ElButton,
  ElInput,
  ElInputNumber,
  ElOption,
  ElSelect,
  ElSwitch,
} from 'element-plus';

defineOptions({ name: 'StartInputsEditor' });

/** 入参类型枚举 */
export type StartInputType =
  | 'Array'
  | 'Boolean'
  | 'File'
  | 'Image'
  | 'Number'
  | 'Object'
  | 'String';

/** 校验规则（按类型取用其中子集，未用到的键保持缺省） */
export interface StartInputValidation {
  /** String：最小/最大长度 */
  minLength?: null | number;
  maxLength?: null | number;
  /** String：正则 */
  pattern?: string;
  /** Number：最小/最大值 */
  min?: null | number;
  max?: null | number;
  /** String/Number：枚举（逗号分隔的候选值） */
  enumValues?: string;
  /** File/Image：大小上限（MB） */
  maxSizeMb?: null | number;
  /** File/Image：允许格式（逗号分隔，如 png,jpg） */
  accept?: string;
}

/** 单条入参定义 */
export interface StartInputParam {
  name?: string;
  key?: string;
  type?: StartInputType;
  required?: boolean;
  defaultValue?: string;
  description?: string;
  example?: string;
  validation?: StartInputValidation;
}

const model = defineModel<StartInputParam[]>({ default: () => [] });

const TYPE_OPTIONS: StartInputType[] = [
  'String',
  'Number',
  'Boolean',
  'Object',
  'Array',
  'File',
  'Image',
];

/** 展开态：按行索引记录（用行 uid 更稳，但索引在本组件内也够用） */
const expanded = ref<Set<number>>(new Set());
function toggleExpand(index: number) {
  const next = new Set(expanded.value);
  if (next.has(index)) next.delete(index);
  else next.add(index);
  expanded.value = next;
}

/** 更新某行的某个字段（浅拷贝整数组，触发 v-model 更新） */
function patchRow(index: number, patch: Partial<StartInputParam>) {
  const next = model.value.map((it, i) =>
    i === index ? { ...it, ...patch } : it,
  );
  model.value = next;
}

/** 更新某行的校验子字段 */
function patchValidation(
  index: number,
  patch: Partial<StartInputValidation>,
) {
  const row = model.value[index];
  if (!row) return;
  patchRow(index, { validation: { ...row.validation, ...patch } });
}

function addRow() {
  model.value = [
    ...model.value,
    { name: '', key: '', type: 'String', required: false },
  ];
  // 新增行默认展开，方便立即填写
  expanded.value = new Set([...expanded.value, model.value.length - 1]);
}

function removeRow(index: number) {
  model.value = model.value.filter((_, i) => i !== index);
  const next = new Set<number>();
  for (const i of expanded.value) {
    if (i < index) next.add(i);
    else if (i > index) next.add(i - 1);
  }
  expanded.value = next;
}

/** 各类型对应的校验分区标题（无校验项的类型返回空） */
function validationHint(type?: StartInputType): string {
  switch (type) {
    case 'File':
    case 'Image': {
      return '大小 / 格式';
    }
    case 'Number': {
      return '范围 / 枚举';
    }
    case 'String': {
      return '长度 / 正则 / 枚举';
    }
    default: {
      return '';
    }
  }
}

const isEmpty = computed(() => model.value.length === 0);
</script>

<template>
  <div class="inputs-editor">
    <!-- 表头 -->
    <div v-if="!isEmpty" class="tbl-head">
      <span class="col-expand"></span>
      <span class="col-name">名称</span>
      <span class="col-key">标识</span>
      <span class="col-type">类型</span>
      <span class="col-req">必填</span>
      <span class="col-op"></span>
    </div>

    <!-- 行 -->
    <div v-for="(row, index) in model" :key="index" class="tbl-row-wrap">
      <div class="tbl-row">
        <span class="col-expand">
          <span
            class="expand-toggle"
            :class="{ open: expanded.has(index) }"
            title="展开高级项"
            @click="toggleExpand(index)"
          >
            ▸
          </span>
        </span>
        <span class="col-name">
          <ElInput
            :model-value="row.name"
            placeholder="如 用户问题"
            size="small"
            @update:model-value="patchRow(index, { name: $event })"
          />
        </span>
        <span class="col-key">
          <ElInput
            :model-value="row.key"
            placeholder="如 question"
            size="small"
            @update:model-value="patchRow(index, { key: $event })"
          />
        </span>
        <span class="col-type">
          <ElSelect
            :model-value="row.type"
            size="small"
            style="width: 100%"
            @update:model-value="patchRow(index, { type: $event })"
          >
            <ElOption
              v-for="t in TYPE_OPTIONS"
              :key="t"
              :label="t"
              :value="t"
            />
          </ElSelect>
        </span>
        <span class="col-req">
          <ElSwitch
            :model-value="row.required"
            size="small"
            @update:model-value="patchRow(index, { required: !!$event })"
          />
        </span>
        <span class="col-op">
          <ElButton
            link
            type="danger"
            size="small"
            @click="removeRow(index)"
          >
            删除
          </ElButton>
        </span>
      </div>

      <!-- 展开区：高级字段 -->
      <div v-if="expanded.has(index)" class="tbl-adv">
        <div class="adv-grid">
          <label class="adv-item">
            <span class="adv-label">默认值</span>
            <ElInput
              :model-value="row.defaultValue"
              placeholder="未传入时使用"
              size="small"
              @update:model-value="patchRow(index, { defaultValue: $event })"
            />
          </label>
          <label class="adv-item">
            <span class="adv-label">示例值</span>
            <ElInput
              :model-value="row.example"
              placeholder="调试/测试用"
              size="small"
              @update:model-value="patchRow(index, { example: $event })"
            />
          </label>
          <label class="adv-item adv-span-2">
            <span class="adv-label">描述</span>
            <ElInput
              :model-value="row.description"
              placeholder="参数用途说明"
              size="small"
              @update:model-value="patchRow(index, { description: $event })"
            />
          </label>
        </div>

        <!-- 校验规则：按类型分化 -->
        <template v-if="validationHint(row.type)">
          <div class="adv-sub-title">校验规则 · {{ validationHint(row.type) }}</div>
          <div class="adv-grid">
            <!-- String -->
            <template v-if="row.type === 'String'">
              <label class="adv-item">
                <span class="adv-label">最小长度</span>
                <ElInputNumber
                  :model-value="row.validation?.minLength ?? undefined"
                  :min="0"
                  controls-position="right"
                  size="small"
                  style="width: 100%"
                  @update:model-value="
                    patchValidation(index, { minLength: $event ?? null })
                  "
                />
              </label>
              <label class="adv-item">
                <span class="adv-label">最大长度</span>
                <ElInputNumber
                  :model-value="row.validation?.maxLength ?? undefined"
                  :min="0"
                  controls-position="right"
                  size="small"
                  style="width: 100%"
                  @update:model-value="
                    patchValidation(index, { maxLength: $event ?? null })
                  "
                />
              </label>
              <label class="adv-item adv-span-2">
                <span class="adv-label">正则</span>
                <ElInput
                  :model-value="row.validation?.pattern"
                  placeholder="如 ^[a-z]+$"
                  size="small"
                  @update:model-value="
                    patchValidation(index, { pattern: $event })
                  "
                />
              </label>
              <label class="adv-item adv-span-2">
                <span class="adv-label">枚举</span>
                <ElInput
                  :model-value="row.validation?.enumValues"
                  placeholder="逗号分隔，如 A,B,C"
                  size="small"
                  @update:model-value="
                    patchValidation(index, { enumValues: $event })
                  "
                />
              </label>
            </template>

            <!-- Number -->
            <template v-else-if="row.type === 'Number'">
              <label class="adv-item">
                <span class="adv-label">最小值</span>
                <ElInputNumber
                  :model-value="row.validation?.min ?? undefined"
                  controls-position="right"
                  size="small"
                  style="width: 100%"
                  @update:model-value="
                    patchValidation(index, { min: $event ?? null })
                  "
                />
              </label>
              <label class="adv-item">
                <span class="adv-label">最大值</span>
                <ElInputNumber
                  :model-value="row.validation?.max ?? undefined"
                  controls-position="right"
                  size="small"
                  style="width: 100%"
                  @update:model-value="
                    patchValidation(index, { max: $event ?? null })
                  "
                />
              </label>
              <label class="adv-item adv-span-2">
                <span class="adv-label">枚举</span>
                <ElInput
                  :model-value="row.validation?.enumValues"
                  placeholder="逗号分隔，如 1,2,3"
                  size="small"
                  @update:model-value="
                    patchValidation(index, { enumValues: $event })
                  "
                />
              </label>
            </template>

            <!-- File / Image -->
            <template v-else-if="row.type === 'File' || row.type === 'Image'">
              <label class="adv-item">
                <span class="adv-label">大小上限(MB)</span>
                <ElInputNumber
                  :model-value="row.validation?.maxSizeMb ?? undefined"
                  :min="0"
                  controls-position="right"
                  size="small"
                  style="width: 100%"
                  @update:model-value="
                    patchValidation(index, { maxSizeMb: $event ?? null })
                  "
                />
              </label>
              <label class="adv-item">
                <span class="adv-label">允许格式</span>
                <ElInput
                  :model-value="row.validation?.accept"
                  :placeholder="
                    row.type === 'Image' ? '如 png,jpg,webp' : '如 pdf,docx'
                  "
                  size="small"
                  @update:model-value="patchValidation(index, { accept: $event })"
                />
              </label>
            </template>
          </div>
        </template>
      </div>
    </div>

    <ElButton class="add-btn" size="small" @click="addRow">
      + 添加入参
    </ElButton>
  </div>
</template>

<style scoped>
.inputs-editor {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
}

/* 列宽通过 grid 统一控制，表头与行共用 */
.tbl-head,
.tbl-row {
  display: grid;
  grid-template-columns: 24px 1.4fr 1.2fr 110px 48px 52px;
  gap: 8px;
  align-items: center;
}

.tbl-head {
  padding: 2px 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.tbl-row-wrap {
  padding: 6px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.tbl-row-wrap + .tbl-row-wrap {
  margin-top: 6px;
}

.col-req {
  display: flex;
  justify-content: center;
}

.col-op {
  display: flex;
  justify-content: flex-end;
}

.expand-toggle {
  display: inline-block;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  transition: transform 0.15s;
}

.expand-toggle.open {
  transform: rotate(90deg);
}

/* 展开区 */
.tbl-adv {
  margin-top: 8px;
  padding: 8px;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
}

.adv-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
}

.adv-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.adv-span-2 {
  grid-column: 1 / -1;
}

.adv-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.adv-sub-title {
  margin: 10px 0 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.add-btn {
  align-self: flex-start;
  margin-top: 2px;
}
</style>
