# Nebula 部署套件

两台机器，只部署基础服务、Java 服务和 nginx：

```
浏览器 ──HTTPS──> 首尔 2c4g：nginx
                   ├─ admin / blog / space / forge / scribe.<域名>   前端静态文件
                   │     └─ /api/**  ──WireGuard(~100ms)──> 4c4g Gateway :19000
                   │                                          └─> nebula-all（manager/blog/space/forge/scribe，同一个 JVM）
                   └─ s3.<域名>      ──WireGuard──────────> 4c4g MinIO :9000

4c4g：MySQL / Redis / MinIO / Meilisearch / Gateway / nebula-all，全部 Docker Compose
```

- 所有请求都先到首尔 nginx，只有 nginx 对公网开放 80/443。
- 4c4g 不对公网暴露任何业务端口；Gateway 和 MinIO 只监听 WireGuard 地址 `10.8.0.2`。
- 每个前端域名下的 `/api/**` 同源转发到 Gateway，前端代码里的 `baseURL=/api` 原样可用，不需要 CORS。
- 一次用户请求只跨一次首尔↔4c4g 链路。Gateway 查 Redis、服务查 MySQL 这些每请求多次的往返都在 4c4g 本机完成。

## 目录结构

```
deploy/
├── Dockerfile                    # 通用 Java 镜像，SERVICE_NAME 区分服务
├── docker-compose.yml            # 4c4g：基础服务 + gateway（+ 独立部署模式的各服务）
├── docker-compose.all-in-one.yml # 4c4g：nebula-all 聚合容器（本方案使用）
├── .env.example                  # 4c4g 配置模板
├── mysql-init/                   # 首次初始化 SQL（init-db.sh 生成）
├── scripts/
│   ├── deploy.sh                 # 4c4g：构建 + 启动 + 健康验证
│   ├── init-db.sh                # 生成有序 initdb 脚本（deploy.sh 自动调用）
│   └── health.sh                 # 4c4g：巡检容器 / 内存 / 连通性 / 端口暴露
└── seoul/
    ├── nginx.conf                # 首尔站点配置（唯一真相源）
    ├── snippets/                 # nginx 公共片段：TLS / SPA / API 转发 / S3
    ├── setup.sh                  # 首尔：装 nginx + certbot，签证书，装配置
    ├── deploy-frontend.sh        # 本地：构建 5 个前端并推送到首尔
    └── wireguard.md              # 首尔 ↔ 4c4g 私网搭建
```

`deploy/ci/`、根目录 `.woodpecker.yml`（Woodpecker CI）不属于本方案，不用部署。

## 部署步骤

### 0. 准备

- **DNS**：6 条 A 记录都指向**首尔公网 IP**：`admin`、`blog`、`space`、`forge`、`scribe`、`s3`。
  用 Cloudflare 橙云代理的话，SSL/TLS 模式必须选 **Full (strict)**，否则 80→443 跳转会死循环。
- **两台机器**：Ubuntu 22.04 / 24.04。
- **代码**：两台都用 `git clone` 拉仓库。不要从 Windows 直接 scp 脚本过去，Windows 工作区里的
  `.sh` 是 CRLF 换行，Linux 上会报 `$'\r': command not found`。
- **4c4g 装 Docker**（含 compose v2）。国内机器要配镜像加速，否则拉不到 Docker Hub 镜像，
  例如腾讯云：`/etc/docker/daemon.json` 写 `{"registry-mirrors": ["https://mirror.ccs.tencentyun.com"]}`。
- **4c4g 加 2G swap**：4G 内存跑满后余量很小，更新时在本机构建镜像也要额外约 1G。swap 只用来扛峰值：

  ```bash
  fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
  ```

### 1. WireGuard 私网（两台都要做）

按 [`seoul/wireguard.md`](seoul/wireguard.md) 第 1～5 步做完。完成标志：首尔上 `ping 10.8.0.2` 能通。
第 5 步（Docker 在 wg0 之后启动）不能省，否则 4c4g 重启后网站会挂。

### 2. 4c4g：基础服务 + Java 服务

```bash
cp deploy/.env.example deploy/.env
vi deploy/.env
#   GATEWAY_BIND_ADDR=10.8.0.2
#   MINIO_BIND_ADDR=10.8.0.2
#   MINIO_PUBLIC_DOMAIN=https://s3.<域名>
#   其余 [必填] 项用 openssl rand -base64 24 生成

./deploy/scripts/deploy.sh --all-in-one   # 构建 gateway + nebula-all 镜像，启动全部容器
./deploy/scripts/health.sh                # 巡检，失败项会标红
```

首次构建要在容器里跑 Maven、下载整个依赖树，需要十几分钟。MySQL 只在第一次启动（数据卷为空）时
导入 `script/mysql/` 下的初始化 SQL。

### 3. 首尔：nginx + HTTPS 证书

```bash
sudo DOMAIN=<域名> EMAIL=<你的邮箱> bash deploy/seoul/setup.sh
curl -s http://10.8.0.2:19000/actuator/health     # 经隧道直连网关，应返回 {"status":"UP"}
```

`setup.sh` 会装 nginx 和 certbot，给 6 个子域名签一张证书（自动续期），然后装上站点配置。
可以重复执行：证书已存在就跳过申请，只更新配置。

### 4. 本地：构建并发布前端

在开发机上执行（需要 Node 20、pnpm 10.33，以及能用 ssh 密钥登录首尔）：

```bash
SEOUL=ubuntu@<首尔公网IP> bash deploy/seoul/deploy-frontend.sh          # 全部 5 个
SEOUL=ubuntu@<首尔公网IP> bash deploy/seoul/deploy-frontend.sh admin    # 只发一个
```

前端不在首尔上构建：web-ele 构建要 8G 堆，2c4g 扛不住。

### 5. 验证

```bash
curl -s https://admin.<域名>/api/manager/actuator/health   # 浏览器 -> nginx -> 隧道 -> 网关 -> manager
curl -s https://blog.<域名>/api/blog/actuator/health
```

然后浏览器打开 `https://admin.<域名>` 登录。每个前端在各自的子域名下，登录态互不共享，
每个站点需要各自登录。

## 日常操作

4c4g 上的 compose 命令需要叠加两个文件，下面用 `DC` 代指：

```bash
DC="docker compose --env-file deploy/.env -f deploy/docker-compose.yml -f deploy/docker-compose.all-in-one.yml"
```

| 要做的事 | 命令 |
|---|---|
| 更新后端代码 | `git pull && ./deploy/scripts/deploy.sh --all-in-one --service nebula-all` |
| 更新网关 | `./deploy/scripts/deploy.sh --all-in-one --service gateway` |
| 只改了 `.env` | `./deploy/scripts/deploy.sh --all-in-one --no-build` |
| 看日志 | `$DC logs -f nebula-all` |
| 更新前端 | 本地 `SEOUL=... bash deploy/seoul/deploy-frontend.sh <站点>` |
| 改 nginx 配置 | 改 `deploy/seoul/` 下的文件并提交，首尔 `git pull` 后重跑 `setup.sh` |
| 推倒重来（**删除全部数据**） | `$DC down -v && ./deploy/scripts/deploy.sh --all-in-one` |

## 4c4g 内存预算

| 容器 | 上限 |
|---|---|
| MySQL | 448M |
| Redis | 96M |
| MinIO | 320M |
| Meilisearch | 192M |
| Gateway | 320M |
| nebula-all（5 个服务） | 1408M |
| 宿主机（系统 + Docker + 云 agent） | ~700M |
| **合计** | **≈ 3.5G** |

五个业务服务放在同一个 JVM 里（多个隔离的 Spring 上下文），只占一份 JVM 基线。分开部署要五份，
4G 内存放不下。`health.sh` 会检查容器有没有被 OOMKill；如果被杀过，调 `docker-compose.all-in-one.yml`
里 `nebula-all` 的 `JAVA_OPTS` 和 `memory` 上限。

## 容易踩的坑

**`MINIO_PUBLIC_DOMAIN` 必须是浏览器可访问的域名。** 后端用它拼接返回给前端的文件直链；
留空会拼出容器内的 `minio:9000`，浏览器无法解析。

**`MINIO_BIND_ADDR` 要改成 `10.8.0.2`。** 默认 `127.0.0.1` 时首尔回源不到 MinIO，s3 域名 502，
而 API 一切正常，很容易看漏。

**公开桶的匿名读策略不是代码建的。** 服务的 `auto-create` 只建桶，不设策略；
`minio-init` 容器负责 `mc anonymous set download`，缺了它公开桶直链会 403。

**MySQL 初始化脚本只在数据卷为空时跑一次。** 改了 `script/mysql/*.sql` 后重启无效，
必须删卷重来（会丢数据），详见 [`mysql-init/README.md`](mysql-init/README.md)。

**`AI_PROFILE_SECRET` 上线后不能改。** 它是 `ai_model_profile.api_key` 的可逆加密密钥，
改了之后历史档案全部无法解密。

**容器间不能用 `127.0.0.1` 互访。** 一律用 compose service name（`mysql`/`redis`/`minio`/
`meilisearch`），compose 文件里已统一注入。

**Java 服务被 OOMKill 后会静默重启。** 日志里看不出异常，`health.sh` 会检查 `OOMKilled`
标志和重启次数。

**不做备份。** 数据丢失后清库重来即可（见上表"推倒重来"）。如果以后有了需要保留的数据，要重新评估。
