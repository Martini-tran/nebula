<script lang="ts" setup>
/**
 * 运行入参动态表单：消费开始节点定义的入参（StartInputParam[]），
 * 让「配置的入参」在运行时变成真正的表单，替代裸 JSON 文本框。
 *
 * 控件映射（按类型）：
 *   - String  → 输入框；配了枚举则转下拉
 *   - Number  → 数字输入（min/max 直接约束）；配了枚举则转下拉
 *   - Boolean → 开关
 *   - Object/Array → JSON 文本域（collect 时解析并校验合法性）
 *   - File/Image   → URL 输入框（暂无上传通道，先以地址传参）
 *
 * 默认值在入参定义变化时按类型转换后预填（不覆盖用户已填内容）；
 * collect() 做必填 + 校验规则（长度/正则/范围/枚举）检查，通过则返回
 * 可直接作为 run 请求 input 的对象。snapshot()/merge() 供 JSON 模式互转。
 */
import type { StartInputParam, StartInputType } from '../start-input';

import { computed, ref, watch } from 'vue';

import {
  ElInput,
  ElInputNumber,
  ElOption,
  ElSelect,
  ElSwitch,
} from 'element-plus';

import JsonField from './JsonField.vue';

defineOptions({ name: 'RunInputForm' });

const props = defineProps<{ inputs: StartInputParam[] }>();

/** collect() 的返回：通过则带 input，不通过带首条错误消息 */
export interface CollectResult {
  ok: boolean;
  input?: Record<string, any>;
  message?: string;
}

/** 表单值（Object/Array 字段以文本草稿单独存放，collect 时解析） */
const values = ref<Record<string, any>>({});
const jsonDrafts = ref<Record<string, string>>({});

/** 过滤掉未填标识的行（无 key 无法入库到 input） */
const fields = computed(() =>
  (props.inputs ?? []).filter(
    (it): it is StartInputParam & { key: string } => Boolean(it.key),
  ),
);

function isJsonType(type?: StartInputType) {
  return type === 'Array' || type === 'Object';
}

/** 枚举串（逗号分隔）→ 候选值数组 */
function enumList(raw?: string): string[] {
  return (raw ?? '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
}

function hasEnum(it: StartInputParam): boolean {
  return enumList(it.validation?.enumValues).length > 0;
}

function numberEnum(it: StartInputParam): number[] {
  return enumList(it.validation?.enumValues)
    .map(Number)
    .filter((n) => !Number.isNaN(n));
}

/** 默认值文本 → 按类型转换后的初值（转不动则返回 undefined 不预填） */
function convertDefault(type: StartInputType | undefined, raw?: string): any {
  if (raw === undefined || raw === '') return undefined;
  switch (type) {
    case 'Boolean': {
      return raw === '1' || raw === 'true';
    }
    case 'Number': {
      const n = Number(raw);
      return Number.isNaN(n) ? undefined : n;
    }
    default: {
      return raw;
    }
  }
}

/** 入参定义变化时重建表单：保留用户已填值，新键按默认值预填 */
watch(
  fields,
  (list) => {
    const nextValues: Record<string, any> = {};
    const nextDrafts: Record<string, string> = {};
    for (const it of list) {
      if (isJsonType(it.type)) {
        nextDrafts[it.key] = jsonDrafts.value[it.key] ?? it.defaultValue ?? '';
        continue;
      }
      const prev = values.value[it.key];
      if (prev === undefined) {
        const dv = convertDefault(it.type, it.defaultValue);
        if (dv !== undefined) nextValues[it.key] = dv;
        // Boolean 无默认值时预置 false，避免提交时缺键
        else if (it.type === 'Boolean') nextValues[it.key] = false;
      } else {
        nextValues[it.key] = prev;
      }
    }
    values.value = nextValues;
    jsonDrafts.value = nextDrafts;
  },
  { deep: true, immediate: true },
);

function placeholderOf(it: StartInputParam): string {
  if (it.example) return `如 ${it.example}`;
  if (it.type === 'File' || it.type === 'Image') return '文件 URL';
  if (it.type === 'Array') return 'JSON 数组，如 ["a", "b"]';
  if (it.type === 'Object') return 'JSON 对象，如 { "k": "v" }';
  return '';
}

/** 解析某个 JSON 字段草稿；空串返回 undefined，非法返回 Error */
function parseDraft(key: string): any {
  const text = (jsonDrafts.value[key] ?? '').trim();
  if (!text) return undefined;
  try {
    return JSON.parse(text);
  } catch {
    return new Error('invalid');
  }
}

/** 必填 + 校验规则检查，全部通过则产出 input 对象 */
function collect(): CollectResult {
  const input: Record<string, any> = {};
  for (const it of fields.value) {
    const label = it.name || it.key;
    const value = isJsonType(it.type) ? parseDraft(it.key) : values.value[it.key];

    if (value instanceof Error) {
      return { message: `「${label}」不是合法 JSON`, ok: false };
    }
    if (value === undefined || value === null || value === '') {
      if (it.required) return { message: `请填写「${label}」`, ok: false };
      continue;
    }

    const rule = it.validation ?? {};
    if (it.type === 'String' && typeof value === 'string') {
      if (rule.minLength != null && value.length < rule.minLength) {
        return { message: `「${label}」长度不能小于 ${rule.minLength}`, ok: false };
      }
      if (rule.maxLength != null && value.length > rule.maxLength) {
        return { message: `「${label}」长度不能大于 ${rule.maxLength}`, ok: false };
      }
      if (rule.pattern) {
        try {
          if (!new RegExp(rule.pattern).test(value)) {
            return { message: `「${label}」不符合格式要求（${rule.pattern}）`, ok: false };
          }
        } catch {
          // 无效正则视为未配置，不拦截运行
        }
      }
      const candidates = enumList(rule.enumValues);
      if (candidates.length > 0 && !candidates.includes(value)) {
        return { message: `「${label}」必须是候选值之一：${candidates.join('、')}`, ok: false };
      }
    }
    if (it.type === 'Number' && typeof value === 'number') {
      if (rule.min != null && value < rule.min) {
        return { message: `「${label}」不能小于 ${rule.min}`, ok: false };
      }
      if (rule.max != null && value > rule.max) {
        return { message: `「${label}」不能大于 ${rule.max}`, ok: false };
      }
      const candidates = numberEnum(it);
      if (candidates.length > 0 && !candidates.includes(value)) {
        return { message: `「${label}」必须是候选值之一：${candidates.join('、')}`, ok: false };
      }
    }

    input[it.key] = value;
  }
  return { input, ok: true };
}

/** 当前表单值的宽松快照（JSON 草稿尽力解析，非法保留原文），供切 JSON 模式回显 */
function snapshot(): Record<string, any> {
  const out: Record<string, any> = {};
  for (const it of fields.value) {
    const value = isJsonType(it.type) ? parseDraft(it.key) : values.value[it.key];
    if (value === undefined || value === null || value === '') continue;
    out[it.key] = value instanceof Error ? jsonDrafts.value[it.key] : value;
  }
  return out;
}

/** 从 JSON 模式切回时把外部对象合并进表单（按字段类型分流） */
function merge(obj: Record<string, any>) {
  for (const it of fields.value) {
    if (!(it.key in obj)) continue;
    const value = obj[it.key];
    if (isJsonType(it.type)) {
      jsonDrafts.value[it.key] =
        typeof value === 'string' ? value : JSON.stringify(value, null, 2);
    } else {
      values.value[it.key] = value;
    }
  }
}

defineExpose({ collect, merge, snapshot });
</script>

<template>
  <div class="run-input-form">
    <div v-for="it in fields" :key="it.key" class="field">
      <div class="field-label">
        <span v-if="it.required" class="req">*</span>
        <span class="name">{{ it.name || it.key }}</span>
        <span class="key">{{ it.key }}</span>
      </div>

      <!-- Boolean -->
      <ElSwitch v-if="it.type === 'Boolean'" v-model="values[it.key]" />

      <!-- Number：枚举转下拉，否则数字输入 -->
      <ElSelect
        v-else-if="it.type === 'Number' && hasEnum(it)"
        v-model="values[it.key]"
        clearable
        style="width: 100%"
      >
        <ElOption
          v-for="opt in numberEnum(it)"
          :key="opt"
          :label="String(opt)"
          :value="opt"
        />
      </ElSelect>
      <ElInputNumber
        v-else-if="it.type === 'Number'"
        v-model="values[it.key]"
        :max="it.validation?.max ?? undefined"
        :min="it.validation?.min ?? undefined"
        :placeholder="placeholderOf(it)"
        controls-position="right"
        style="width: 100%"
      />

      <!-- String：枚举转下拉，否则输入框 -->
      <ElSelect
        v-else-if="it.type === 'String' && hasEnum(it)"
        v-model="values[it.key]"
        clearable
        style="width: 100%"
      >
        <ElOption
          v-for="opt in enumList(it.validation?.enumValues)"
          :key="opt"
          :label="opt"
          :value="opt"
        />
      </ElSelect>

      <!-- Object / Array：JSON 编辑器（运行入参通常较短，不给全屏按钮） -->
      <JsonField
        v-else-if="isJsonType(it.type)"
        v-model="jsonDrafts[it.key]"
        :fullscreen="false"
        :height="120"
        :placeholder="placeholderOf(it)"
      />

      <!-- String / File / Image：输入框 -->
      <ElInput
        v-else
        v-model="values[it.key]"
        :placeholder="placeholderOf(it)"
      />

      <div v-if="it.description" class="field-desc">{{ it.description }}</div>
    </div>
  </div>
</template>

<style scoped>
.run-input-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.field-label {
  display: flex;
  gap: 6px;
  align-items: baseline;
  font-size: 13px;
}

.req {
  color: var(--el-color-danger);
}

.name {
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.key {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.field-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
