# Nebula 部署套件

配套文档：[`docs/部署方式/当前部署架构.md`](../docs/部署方式/当前部署架构.md)（架构决策与风险说明）。
本目录是那份架构的**可执行实现**。

## 目录结构

```
deploy/
├── Dockerfile              # 通用多阶段镜像，SERVICE_NAME 区分 5 个服务
├── docker-compose.yml      # 北京节点全栈编排
├── .env.example            # 配置模板（复制为 .env 后填写）
├── mysql-init/             # 按依赖排序的首次初始化 SQL（自动生成）
├── scripts/
│   ├── deploy.sh           # 一键构建 + 启动 + 健康验证
│   ├── init-db.sh          # 生成有序 initdb 脚本（deploy.sh 自动调用）
│   └── health.sh           # 巡检：容器/内存/连通性/端口暴露
└── seoul/
    ├── nginx.conf          # 首尔入口反向代理
    └── wireguard.md        # 首尔 ↔ 北京 私网搭建
```

## 快速开始（北京 4c4g）

```bash
# 1. 准备配置
cp deploy/.env.example deploy/.env
vi deploy/.env                      # 填写全部 [必填] 项

# 2. 部署核心服务（gateway + manager + blog + 基础设施）
chmod +x deploy/scripts/*.sh
./deploy/scripts/deploy.sh

# 3. 巡检
./deploy/scripts/health.sh

# 4. 内存有余量时再加 space / forge
./deploy/scripts/deploy.sh --extra
```

首次部署刻意只起核心服务。默认 profile 约占 **2.6G**，加上 `--extra` 约 **3.35G**，
在 4G 机器上留给系统的余量已经不多，务必先用 `health.sh` 确认水位再扩。

## 常用操作

```bash
# 只重建某个服务（改了单个服务的代码）
./deploy/scripts/deploy.sh --service blog

# 不重新构建，仅重启（改了 .env）
./deploy/scripts/deploy.sh --no-build

# 看日志
docker compose --env-file deploy/.env -f deploy/docker-compose.yml logs -f manager

# 推倒重来（删除全部数据卷后重新初始化）
docker compose --env-file deploy/.env -f deploy/docker-compose.yml down -v
./deploy/scripts/deploy.sh
```

## 服务端口与路由

Gateway 是唯一入口，按前缀路由到下游；下游服务不对外暴露端口。

| 服务 | 容器端口 | context-path | 网关前缀 |
|---|---|---|---|
| gateway | 19000 | — | — |
| manager | 8081 | `/manager` | `/manager/**` |
| blog | 8082 | `/blog` | `/blog/**` |
| space | 8083 | `/space` | `/space/**` |
| forge | 8884 | `/forge` | `/forge/**` |

健康检查路径包含 context-path，例如 Blog 是 `/blog/actuator/health`。

## 容易踩的坑

**`MINIO_PUBLIC_DOMAIN` 必须是浏览器可访问的域名。** 后端用它拼接返回给前端的
公开桶直链；留空会拼出容器内的 `minio:9000`，浏览器无法解析。

**公开桶的匿名读策略不是代码建的。** 服务的 `auto-create` 只建桶，不设策略。
`minio-init` 容器负责 `mc anonymous set download`，缺了它公开桶直链会 403。

**MySQL 初始化脚本只在数据卷为空时跑一次。** 改了 `script/*.sql` 后重启无效，
必须删卷重来（会丢数据），详见 [`mysql-init/README.md`](mysql-init/README.md)。

**`AI_PROFILE_SECRET` 上线后不能改。** 它是 `ai_model_profile.api_key` 的可逆加密
密钥，改了之后历史档案全部无法解密。

**容器间不能用 `127.0.0.1` 互访。** 一律用 compose service name（`mysql`/`redis`/
`minio`/`meilisearch`），compose 文件里已统一注入。

**Java 服务被 OOMKill 后会静默重启。** 日志里看不出异常，`health.sh` 会检查
`OOMKilled` 标志和重启次数，发现后下调对应服务的 `JAVA_OPTS`。

## 与架构文档的差异说明

架构文档写于部署套件之前，以下几点以本目录为准：

- **Meilisearch**：架构文档未提及，但 `blog` 和 `forge` 的 `application.yml` 都启用了
  它（全文检索），已纳入编排。
- **不提供备份脚本**：与架构文档一致——当前数据丢失后重新部署即可，不值得为它引入
  备份和恢复流程。出问题就 `down -v` 清库重来。
- **Space / Forge**：架构文档按 5 服务全量描述，实际编排把这两个放进 `extra`
  profile 按需启动，以适配 4c4g 内存。
