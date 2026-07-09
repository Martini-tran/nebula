/**
 * 开始节点入参定义类型（node.data.nodeConfig.inputs 的元素结构）。
 *
 * 原先随行编辑器组件 StartInputsEditor.vue 一起声明；入参配置改为直接
 * 编辑 JSON 后行编辑器下线，类型收敛到本模块，供配置弹窗（JSON 校验）
 * 与运行动态表单（RunInputForm）共用。
 */

/** 入参类型枚举 */
export type StartInputType =
  | 'Array'
  | 'Boolean'
  | 'File'
  | 'Image'
  | 'Number'
  | 'Object'
  | 'String';

/** 全部合法入参类型（JSON 校验与文档提示用） */
export const START_INPUT_TYPES: StartInputType[] = [
  'String',
  'Number',
  'Boolean',
  'Object',
  'Array',
  'File',
  'Image',
];

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

/** 按 JSON 值推断入参类型 */
export function inferStartInputType(value: unknown): StartInputType {
  if (typeof value === 'number') return 'Number';
  if (typeof value === 'boolean') return 'Boolean';
  if (Array.isArray(value)) return 'Array';
  if (value !== null && typeof value === 'object') return 'Object';
  return 'String';
}

/**
 * 归一化开始节点入参（nodeConfig.inputs）为 StartInputParam[]。
 *
 * 现行形态是 JSON 对象：键为入参标识（key），值为默认值，类型按值推断；
 * 历史数据可能是 StartInputParam[] 数组，直接过滤空项后透传。
 * 卡片摘要与运行动态表单统一走本函数消费。
 */
export function normalizeStartInputs(raw: unknown): StartInputParam[] {
  if (Array.isArray(raw)) {
    return (raw as StartInputParam[]).filter((it) => it && (it.key || it.name));
  }
  if (raw && typeof raw === 'object') {
    return Object.entries(raw as Record<string, any>).map(([key, value]) => ({
      key,
      name: key,
      type: inferStartInputType(value),
      defaultValue: typeof value === 'string' ? value : JSON.stringify(value),
    }));
  }
  return [];
}
