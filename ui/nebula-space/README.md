# nebula-space

个人空间前台：管理自己的书签、目录与标签，支持导入/导出 Chrome 书签 HTML。

对接后端 `nebula-service-space`（网关路由 `/space/**`），登录复用 `nebula-service-manager` 的账号体系与滑块验证码。

## 技术栈

Vue 3 + TypeScript + Vite + Pinia + Vue Router + Tailwind CSS 4 + Iconify

## 开发

```bash
npm install
npm run dev      # http://localhost:28261
npm run build
```

开发服务器把 `/api/**` 代理到网关（默认 `http://localhost:19000`），并去掉 `/api` 前缀。
可在 `.env.local` 里用 `VITE_PROXY_TARGET` 覆盖网关地址。

## 目录

```
src/
├── api/            # auth（登录/验证码）、space（书签/目录/标签/导入导出）
├── components/     # 品牌标识、主题切换、滑块验证码、状态块、页头
├── layouts/        # 默认外壳
├── router/         # 路由与登录守卫
├── stores/         # auth、theme、space（目录树与标签缓存）
├── styles/         # 设计令牌、主题、基础样式、共享原子类
├── types/
├── utils/          # request（axios 封装）、theme、format
└── views/
    ├── home/       # 书签工作台：侧栏（视图/目录树/标签）+ 书签网格
    └── login/
```

## 说明

- 空间服务目前只提供 `/space/admin/**` 端点，数据按登录用户隔离，前台直接调用；登录账号需要具备 `space:*` 相关权限。
- 列表默认只展示「正常」状态的书签，「已归档」单独一个视图。
