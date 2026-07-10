/**
 * Monaco 精简入口：只装编辑器核心 + JSON 语言，替代 monaco-editor 的默认导出。
 *
 * 默认入口 `monaco-editor`（module 字段 → esm/vs/editor/editor.main.js）会拉进
 * 82 门语言的 tokenizer 与 CSS/HTML/JSON/TypeScript 四套语言服务，单 chunk 3.7 MB。
 * 我们只在 ai-flow 里编辑 JSON，其余全是死代码。
 *
 * 通过 vite.config.ts 的 alias 把 `monaco-editor` 指到这里，
 * @idss-d/json-editor-vue3 内部的 `import * as monaco from 'monaco-editor'` 也随之收敛。
 *
 * 顺带接上 JSON 语言服务的 worker：该编辑器包自己只用 JSON.parse 做校验，
 * 挂上 worker 后可获得 monaco 原生的实时语法诊断与括号/属性补全。
 */
import * as monaco from 'monaco-editor/esm/vs/editor/editor.api';

import 'monaco-editor/esm/vs/editor/editor.all.js';
import 'monaco-editor/esm/vs/language/json/monaco.contribution';

import EditorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker';
import JsonWorker from 'monaco-editor/esm/vs/language/json/json.worker?worker';

/**
 * Monaco 通过全局 MonacoEnvironment.getWorker 拿 worker 实例。
 * 不挂这个钩子时它会尝试用 Blob/CDN 兜底，在本项目的 CSP 与打包方式下拿不到
 * 语言服务，只剩语法高亮。
 */
globalThis.MonacoEnvironment = {
  getWorker(_workerId: string, label: string) {
    return label === 'json' ? new JsonWorker() : new EditorWorker();
  },
};

export * from 'monaco-editor/esm/vs/editor/editor.api';
export default monaco;
