# 简介

**orccode** 是一个 Windows 快速启动器：常驻后台，按下全局快捷键唤出命令面板，输入几个字母即可搜索并打开应用、文件或插件，支持拼音匹配。

启动器的能力可以通过**插件**扩展。插件分为两类：

- **`inline`（内联）插件**：直接向搜索结果列表贡献条目，用户输入时实时出现。
- **`view`（视图）插件**：作为一个入口出现在列表里，选中后打开插件自带的 UI（运行在隔离、受限的 `<webview>` 沙箱中）。

## 下一步

- 想开发自己的插件？请阅读 [插件开发文档](/guide/plugin-development)。
- 内置的 `plugins/clipboard`（剪贴板历史，view 类型）是一个可参考的完整示例。

## 下载

前往主站的[更新记录](/versions)页面或 [GitHub Releases](https://github.com/Martini-tran/forge/releases) 下载安装包。
