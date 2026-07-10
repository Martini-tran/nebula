/**
 * monaco-editor 的子路径类型补声明。
 *
 * monaco 的 package.json exports 用 `"./*": "./*"` 通配导出子路径，但通配项
 * 没带 types 条件，TS（moduleResolution: node）解析不到旁边的 .d.ts。
 * 运行时没问题（Vite/rolldown 能解析），只是类型缺失，这里手动接回来。
 *
 * 本文件必须保持为「全局脚本」而非模块——一旦出现顶层 import/export，
 * 下面的 declare module 就只在本文件内可见，补声明会失效。
 */

declare module 'monaco-editor/esm/vs/editor/editor.api' {
  export * from 'monaco-editor';
}

declare module 'monaco-editor/esm/vs/editor/editor.all.js';

declare module 'monaco-editor/esm/vs/language/json/monaco.contribution';

declare module 'monaco-editor/esm/vs/editor/editor.worker?worker' {
  const WorkerCtor: new () => Worker;
  export default WorkerCtor;
}

declare module 'monaco-editor/esm/vs/language/json/json.worker?worker' {
  const WorkerCtor: new () => Worker;
  export default WorkerCtor;
}

/** Monaco 通过该全局钩子获取语言服务 worker */
declare var MonacoEnvironment: {
  getWorker: (workerId: string, label: string) => Worker;
};
