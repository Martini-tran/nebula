<script lang="ts" setup>
/**
 * END 结束节点配置弹窗（独立弹窗，不与 PropertyPanel 耦合）。
 *
 * 由画布上 END 节点右键菜单「配置」触发：卡片 → graph.trigger('start:menu')
 * → useFlowGraph → 编辑器主页面按节点类型分发 → 本弹窗 open(node)。
 *
 * 配置流程的**固定格式 JSON 输出**：
 *   - 输出 JSON：直接编辑 JSON 对象模板（键=输出字段，字符串值可用
 *     {{outputKey}} 引用上游输出），确认时校验必须是合法 JSON 对象
 * 配置结构与读写归一化集中在 ../end-config（落库到 nodeConfig.end）。
 *
 * 弹窗自持草稿：打开时从节点读入并补全缺省，确认时校验后写回并刷新卡片；
 * 取消丢弃草稿。仅写回 end 配置，节点名称保持不变。
 */
import type { Node } from '@antv/x6';

import type { EndConfig } from '../end-config';

import { reactive, ref } from 'vue';

import { ElButton, ElForm, ElFormItem, ElMessage } from 'element-plus';

import { FLOW_DIALOG } from '../constants';
import EmbeddableDialog from './EmbeddableDialog.vue';
import JsonField from './JsonField.vue';
import {
  defaultEndConfig,
  END_OUTPUT_PLACEHOLDER,
  normalizeEndConfig,
  parseEndOutputJson,
  serializeEndConfig,
} from '../end-config';
import { refreshNodeCard } from '../shapes/registerShapes';

defineOptions({ name: 'EndConfigDialog' });

/** embedded：内嵌到 NodeConfigPanel 时去掉弹窗外壳 */
withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false });

/** 确认写回节点后通知外层（NodeConfigPanel → 触发流程保存接口） */
const emit = defineEmits<{ apply: [] }>();

/** END 节点主题色（与 EndNodeCard 卡片描边一致），驱动小节标题左边条 */
const END_THEME_COLOR = '#5f95ff';

/**
 * 输出模板说明文案。变量示例含双花括号，直接写进模板会被 Vue 编译器当
 * 嵌套插值解析报错，故提到常量里以整段文本插值。
 */
const OUTPUT_HINT =
  '固定输出结构（JSON 对象）：键=输出字段，字符串值可用 {{outputKey}} 引用上游输出';

const visible = ref(false);
let target: Node | undefined;

/** 配置属性草稿：END 配置 */
const draft = reactive<EndConfig>(defaultEndConfig());

/** 打开弹窗：读入目标节点当前配置为草稿（缺省字段由 normalize 补全） */
function open(node: Node) {
  target = node;
  const data = node.getData<Record<string, any>>() ?? {};
  Object.assign(draft, normalizeEndConfig(data.nodeConfig?.end));
  visible.value = true;
}


/** 确认：校验输出 JSON 模板，通过后归一化写回 nodeConfig.end 与名称，刷新卡片 */
function handleConfirm() {
  const result = parseEndOutputJson(draft.outputJson);
  if (!result.ok) {
    ElMessage.error(result.message);
    return;
  }
  if (target) {
    const data = target.getData<Record<string, any>>() ?? {};
    const nodeConfig = {
      ...data.nodeConfig,
      end: serializeEndConfig(draft),
    };
    target.setData({ ...data, nodeConfig }, { overwrite: true });
    refreshNodeCard(target);
  }
  emit('apply');
  visible.value = false;
}

function handleClose() {
  visible.value = false;
}

defineExpose({ open });
</script>

<template>
  <!-- FLOW_DIALOG：流程编辑器弹窗统一规格；embedded 时去弹窗外壳内嵌抽屉 -->
  <EmbeddableDialog
    v-model:visible="visible"
    :embedded="embedded"
    :dialog-class="FLOW_DIALOG.class"
    title="结束节点配置"
    :top="FLOW_DIALOG.top"
    :width="FLOW_DIALOG.width"
  >
    <ElForm
      class="prop-form"
      label-position="top"
      :style="{ '--type-color': END_THEME_COLOR }"
      @submit.prevent
    >
      <div class="prop-grid-1">
        <ElFormItem label="输出 JSON">
          <div class="w-full">
            <div class="hint-block">
              {{ OUTPUT_HINT }}
            </div>
            <JsonField
              v-model="draft.outputJson"
              :height="260"
              :placeholder="END_OUTPUT_PLACEHOLDER"
            />
          </div>
        </ElFormItem>
      </div>
    </ElForm>

    <template #footer>
      <ElButton v-if="!embedded" @click="handleClose">取消</ElButton>
      <ElButton type="primary" @click="handleConfirm">
        {{ embedded ? '应用配置' : '保存配置' }}
      </ElButton>
    </template>
  </EmbeddableDialog>
</template>

<style scoped>
/* ===== 呼吸感：容器内边距 + 表单项间距（form-ux-spec 规范） ===== */
.prop-form {
  padding: 8px 4px;
}

/* 表单项垂直间距 ≥20px */
.prop-form :deep(.el-form-item) {
  margin-bottom: 22px;
}

/* 标签在上：标签与输入框间距 6-8px；标签字重/字号提升可读性 */
.prop-form :deep(.el-form-item__label) {
  padding-bottom: 7px;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--el-text-color-primary);
}

/* 逻辑分组之间：分割线 + 32px 呼吸间距 */
.prop-form :deep(.el-divider) {
  margin: 32px 0;
}

/* ===== 布局：单列网格 ===== */
.prop-grid-1 {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
}

.prop-grid-1 :deep(.el-form-item) {
  min-width: 0;
}

/* ===== 视觉层级：输入框统一高度 42px / 圆角 8px / 五态 ===== */
.prop-form :deep(.el-input__wrapper),
.prop-form :deep(.el-select__wrapper) {
  min-height: 42px;
  border-radius: 8px;
  transition:
    box-shadow 0.2s,
    border-color 0.2s;
}

/* hover：边框中性灰略深 */
.prop-form :deep(.el-input__wrapper:hover),
.prop-form :deep(.el-select__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--el-border-color-hover) inset;
}

/* focus：主题色边框 + 外发光阴影 */
.prop-form :deep(.el-input__wrapper.is-focus),
.prop-form :deep(.el-select__wrapper.is-focused) {
  box-shadow:
    0 0 0 1px var(--type-color, var(--el-color-primary)) inset,
    0 0 0 3px color-mix(in srgb, var(--type-color, var(--el-color-primary)) 20%, transparent);
}

/* disabled：置灰、禁用光标 */
.prop-form :deep(.el-input.is-disabled .el-input__wrapper) {
  background: var(--el-disabled-bg-color);
  box-shadow: 0 0 0 1px var(--el-disabled-border-color) inset;
  cursor: not-allowed;
}

/* error：红框（提示文案由 el-form-item__error 贴底显示） */
.prop-form :deep(.el-form-item.is-error .el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-danger) inset;
}

/* success：绿框 */
.prop-form :deep(.el-form-item.is-success .el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-success) inset;
}

/* 错误提示：紧贴输入框下方红色小字（禁用弹窗） */
.prop-form :deep(.el-form-item__error) {
  padding-top: 4px;
  font-size: 12px;
}

/* 占位符：比正文浅 2 级 */
.prop-form :deep(.el-input__inner::placeholder) {
  color: var(--el-text-color-placeholder);
}

/* 字段上方的说明文字 */
.hint-block {
  margin-bottom: 8px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
</style>
