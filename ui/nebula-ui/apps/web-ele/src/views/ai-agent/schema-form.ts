/**
 * JSON Schema → 运行动态表单入参定义（StartInputParam[]）转换器。
 *
 * Agent 定义的 input_schema / output_schema 是标准 JSON Schema 文本；运行入口与
 * AGENT 节点契约对齐都需要把它变成可渲染的字段列表。此处只承接平铺一层的
 * object schema：type/title/description/enum/default/required 与常用约束键
 * （minLength/maxLength/pattern/minimum/maximum）；嵌套 object/array 字段
 * 保持 Object/Array 类型，由 RunInputForm 以 JSON 文本域承接。
 *
 * 解析失败 / 非 object schema / properties 为空 → 返回 []，调用方据此退回
 * 裸 JSON 文本框模式（与 RunPanel 的 hasForm 逻辑一致），不报错打断。
 */
import type {
  StartInputParam,
  StartInputType,
  StartInputValidation,
} from '../ai-flow/editor/start-input';

/** JSON Schema type → 表单入参类型 */
function mapType(t: unknown): StartInputType {
  switch (t) {
    case 'array': {
      return 'Array';
    }
    case 'boolean': {
      return 'Boolean';
    }
    case 'integer':
    case 'number': {
      return 'Number';
    }
    case 'object': {
      return 'Object';
    }
    default: {
      return 'String';
    }
  }
}

/** 单个 property 定义 → 入参定义 */
function toParam(key: string, rawDef: unknown, required: boolean): StartInputParam {
  const def =
    rawDef && typeof rawDef === 'object' && !Array.isArray(rawDef)
      ? (rawDef as Record<string, any>)
      : {};

  const validation: StartInputValidation = {};
  if (Array.isArray(def.enum) && def.enum.length > 0) {
    validation.enumValues = def.enum.map(String).join(',');
  }
  if (typeof def.minLength === 'number') validation.minLength = def.minLength;
  if (typeof def.maxLength === 'number') validation.maxLength = def.maxLength;
  if (typeof def.pattern === 'string' && def.pattern) {
    validation.pattern = def.pattern;
  }
  if (typeof def.minimum === 'number') validation.min = def.minimum;
  if (typeof def.maximum === 'number') validation.max = def.maximum;

  let defaultValue: string | undefined;
  if (def.default !== undefined) {
    defaultValue =
      typeof def.default === 'string'
        ? def.default
        : JSON.stringify(def.default);
  }

  return {
    defaultValue,
    description:
      typeof def.description === 'string' ? def.description : undefined,
    key,
    name: typeof def.title === 'string' && def.title ? def.title : key,
    required,
    type: mapType(def.type),
    validation:
      Object.keys(validation).length > 0 ? validation : undefined,
  };
}

/**
 * 解析 JSON Schema 文本为入参定义列表。
 * 任何形态不符（非法 JSON / 非 object schema / 无 properties）都返回 []。
 */
export function schemaToStartInputs(
  schemaText?: null | string,
): StartInputParam[] {
  const text = (schemaText ?? '').trim();
  if (!text) return [];

  let schema: any;
  try {
    schema = JSON.parse(text);
  } catch {
    return [];
  }
  if (!schema || typeof schema !== 'object' || Array.isArray(schema)) return [];
  // 显式声明了非 object 的 schema（如 {"type":"array"}）不可展开为字段表单
  if (schema.type !== undefined && schema.type !== 'object') return [];

  const props = schema.properties;
  if (!props || typeof props !== 'object' || Array.isArray(props)) return [];

  const requiredList: string[] = Array.isArray(schema.required)
    ? schema.required.filter((it: unknown) => typeof it === 'string')
    : [];

  return Object.entries(props).map(([key, def]) =>
    toParam(key, def, requiredList.includes(key)),
  );
}
