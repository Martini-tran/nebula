# WireGuard 首尔 ↔ 北京 私网配置

首尔 Nginx 通过这条私网把 API 请求转发到北京 Gateway。北京的 `19000` / `9000`
只监听 WireGuard 网卡地址，不出现在公网上——这是整套部署的安全边界。

## 地址规划

| 节点 | WireGuard IP | 角色 |
|---|---|---|
| 首尔 `2c4g` | `10.8.0.1` | 公网入口，Nginx |
| 北京 `4c4g` | `10.8.0.2` | 应用与数据节点 |

## 1. 两端安装并生成密钥

```bash
apt update && apt install -y wireguard

# 各自在本机执行，私钥不要跨机器复制
wg genkey | tee privatekey | wg pubkey > publickey
chmod 600 privatekey
```

## 2. 首尔 `/etc/wireguard/wg0.conf`

```ini
[Interface]
Address    = 10.8.0.1/24
PrivateKey = <首尔私钥>
ListenPort = 51820

[Peer]
# 北京
PublicKey  = <北京公钥>
AllowedIPs = 10.8.0.2/32
# 北京若在 NAT 后（多数云主机是），由北京主动连首尔，
# 因此首尔这侧不写 Endpoint，等对端连入即可。
```

## 3. 北京 `/etc/wireguard/wg0.conf`

```ini
[Interface]
Address    = 10.8.0.2/24
PrivateKey = <北京私钥>

[Peer]
# 首尔
PublicKey  = <首尔公钥>
Endpoint   = <首尔公网IP>:51820
AllowedIPs = 10.8.0.1/32
# 关键：NAT 会话表会在几十秒无流量后回收映射，届时首尔发往北京的包会被丢弃，
# 表现为"平时好好的，闲置一会就 502"。25s 心跳保活可以避免这个问题。
PersistentKeepalive = 25
```

## 4. 启动并设为开机自启

```bash
systemctl enable --now wg-quick@wg0
wg show                      # 两端都应看到 latest handshake
ping -c 3 10.8.0.2           # 首尔 -> 北京
```

## 5. 让 Gateway 只监听私网地址

编辑 `deploy/.env`：

```bash
GATEWAY_BIND_ADDR=10.8.0.2
```

然后重启：`./deploy/scripts/deploy.sh --no-build`

验证端口确实没挂公网：

```bash
ss -lntp | grep 19000        # 应显示 10.8.0.2:19000，而非 0.0.0.0:19000
```

## 6. 防火墙

北京侧：只放行首尔的 WireGuard 握手和私网流量，业务端口一律不对公网开放。

```bash
ufw allow 51820/udp                       # WireGuard
ufw allow from 10.8.0.1 to any port 19000 # 仅首尔可访问 Gateway
ufw allow from 10.8.0.1 to any port 9000  # 仅首尔可访问 MinIO
ufw deny 3306 && ufw deny 6379            # 数据库与缓存永不开放
ufw enable
```

首尔侧：只开 `80/443`（建议进一步限制为 Cloudflare IP 段）和 SSH。

```bash
ufw allow 80,443/tcp
ufw allow 22/tcp
ufw enable
```

## 排查

| 现象 | 原因 |
|---|---|
| `wg show` 无 handshake | 首尔安全组未放行 `51820/udp`，或公钥填反 |
| 闲置后 502，重连即恢复 | 北京侧漏配 `PersistentKeepalive` |
| Nginx 报 `connect() failed` | `GATEWAY_BIND_ADDR` 仍是 `127.0.0.1`，容器端口没绑到私网 |
| 延迟远高于 50ms | MTU 问题，尝试在 `[Interface]` 加 `MTU = 1380` |
