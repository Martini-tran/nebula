# WireGuard 首尔 ↔ 4c4g 私网配置

首尔 Nginx 通过这条私网把 API 和文件请求转发到 4c4g。4c4g 的 `19000`（Gateway）/
`9000`（MinIO）只监听 WireGuard 网卡地址，不出现在公网上——这是整套部署的安全边界。
请求里带着登录 token，走加密隧道而不是明文跨公网。

## 地址规划

| 节点 | WireGuard IP | 角色 |
|---|---|---|
| 首尔 `2c4g` | `10.8.0.1` | 公网入口：Nginx + 前端静态文件 |
| 应用节点 `4c4g` | `10.8.0.2` | Gateway、Java 服务、MySQL/Redis/MinIO/Meilisearch |

首尔是监听端（`51820/udp`），4c4g 主动连首尔。这样 4c4g 即使在 NAT 后面也能连通。

## 1. 两端安装并生成密钥

```bash
apt update && apt install -y wireguard

# 各自在本机执行，私钥不要跨机器复制
cd /etc/wireguard
wg genkey | tee privatekey | wg pubkey > publickey
chmod 600 privatekey
cat publickey          # 把公钥抄给对端
```

## 2. 首尔 `/etc/wireguard/wg0.conf`

```ini
[Interface]
Address    = 10.8.0.1/24
PrivateKey = <首尔私钥>
ListenPort = 51820

[Peer]
# 4c4g
PublicKey  = <4c4g 公钥>
AllowedIPs = 10.8.0.2/32
# 4c4g 若在 NAT 后（多数云主机是），由它主动连首尔，
# 因此首尔这侧不写 Endpoint，等对端连入即可。
```

## 3. 4c4g `/etc/wireguard/wg0.conf`

```ini
[Interface]
Address    = 10.8.0.2/24
PrivateKey = <4c4g 私钥>

[Peer]
# 首尔
PublicKey  = <首尔公钥>
Endpoint   = <首尔公网IP>:51820
AllowedIPs = 10.8.0.1/32
# 关键：NAT 会话表会在几十秒无流量后回收映射，届时首尔发往 4c4g 的包会被丢弃，
# 表现为"平时好好的，闲置一会就 502"。25s 心跳保活可以避免这个问题。
PersistentKeepalive = 25
```

## 4. 启动并设为开机自启

```bash
systemctl enable --now wg-quick@wg0
wg show                      # 两端都应看到 latest handshake
ping -c 3 10.8.0.2           # 在首尔执行
```

## 5. 4c4g：Docker 必须在 WireGuard 之后启动

Gateway 和 MinIO 的端口绑在 `10.8.0.2` 上。服务器重启时如果 Docker 先于 wg0 起来，
这个地址还不存在，端口绑定失败（`cannot assign requested address`），容器起不来，
表现为"重启服务器后网站全挂"。

```bash
mkdir -p /etc/systemd/system/docker.service.d
cat > /etc/systemd/system/docker.service.d/after-wireguard.conf <<'EOF'
[Unit]
After=wg-quick@wg0.service
Wants=wg-quick@wg0.service
EOF
systemctl daemon-reload
```

## 6. 4c4g：让 Gateway / MinIO 只监听私网地址

编辑 `deploy/.env`：

```bash
GATEWAY_BIND_ADDR=10.8.0.2
MINIO_BIND_ADDR=10.8.0.2
```

然后重启：`./deploy/scripts/deploy.sh --all-in-one --no-build`

验证端口确实没挂公网：

```bash
ss -lntp | grep -E ':(19000|9000) '   # 应显示 10.8.0.2:xxx，而非 0.0.0.0:xxx
```

## 7. 防火墙

云厂商的**安全组**和系统里的 `ufw` 是两层，两层都要放行。

首尔：对公网开 `80/443`（网站）、`51820/udp`（WireGuard 监听）和 SSH。

```bash
ufw allow 22/tcp           # 先放行 SSH，否则 enable 后当前会话立刻断开
ufw allow 80,443/tcp
ufw allow 51820/udp
ufw enable
```

4c4g：只开 SSH。它主动连首尔，WireGuard 不需要入站端口；业务端口都绑在
`127.0.0.1` / `10.8.0.2` 上，本来就不在公网监听。

```bash
ufw allow 22/tcp           # 先放行 SSH
ufw enable
```

> Docker 发布的端口不经过 ufw 的 INPUT 规则，`ufw deny 9000` 之类对容器端口无效。
> 真正的保护是端口只绑私网/本机地址，`deploy/scripts/health.sh` 会检查有没有端口监听在 `0.0.0.0`。

## 排查

| 现象 | 原因 |
|---|---|
| `wg show` 无 handshake | 首尔安全组 / ufw 未放行 `51820/udp`，或公钥填反 |
| 闲置后 502，重连即恢复 | 4c4g 侧漏配 `PersistentKeepalive` |
| 重启服务器后 502，`docker ps` 里 gateway 不在 | 漏了第 5 步，Docker 比 wg0 先启动 |
| Nginx 报 `connect() failed` | `GATEWAY_BIND_ADDR` 仍是 `127.0.0.1`，容器端口没绑到私网 |
| s3 域名 502，API 正常 | `MINIO_BIND_ADDR` 没改成 `10.8.0.2` |
| 延迟远高于 ping 值（约 100ms） | MTU 问题，两端 `[Interface]` 加 `MTU = 1380` |
