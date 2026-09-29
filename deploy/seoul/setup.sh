#!/usr/bin/env bash
#
# 首尔节点（2c4g）安装脚本：nginx + certbot + 站点配置 + HTTPS 证书
#
# 用法（在首尔机器上，仓库已 git clone 下来）：
#   sudo DOMAIN=orccode.com bash deploy/seoul/setup.sh
#
# 可选：
#   EMAIL=you@example.com  证书到期提醒邮箱；不填也能签发和自动续期，只是收不到提醒
#   DEPLOY_USER=ubuntu   /var/www/nebula 的属主，deploy-frontend.sh 用这个用户 ssh 上传（默认 sudo 前的用户）
#
# 可重复执行：证书已覆盖全部域名就跳过申请，只更新配置并 reload；域名清单变多时，
# 会把现有证书扩展到新域名。改了 nginx.conf / snippets 后重跑即可。
#
# 前置条件：
#   - 主域名、www 与 6 个子域名（admin/blog/space/forge/scribe/s3）的 DNS 已解析到本机公网 IP
#   - 安全组放行 80/443，否则 Let's Encrypt 验证不通过

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOMAIN="${DOMAIN:-orccode.com}"
EMAIL="${EMAIL:-}"
DEPLOY_USER="${DEPLOY_USER:-${SUDO_USER:-root}}"

SITES=(home admin blog space forge scribe)
SUBDOMAINS=(admin blog space forge scribe s3)
WEB_ROOT=/var/www/nebula
CERT_NAME=nebula

log()  { printf '\033[0;32m[setup]\033[0m %s\n' "$*"; }
die()  { printf '\033[0;31m[error]\033[0m %s\n' "$*" >&2; exit 1; }

[[ $EUID -eq 0 ]] || die "请用 root 或 sudo 执行"
[[ -f "$HERE/nginx.conf" ]] || die "找不到 $HERE/nginx.conf"
id "$DEPLOY_USER" >/dev/null 2>&1 || die "用户 $DEPLOY_USER 不存在，用 DEPLOY_USER=xxx 指定"

# 主站 home 用主域名本身；www 在 nginx 里跳回主域名，但证书也要覆盖它。
FQDNS=("$DOMAIN" "www.$DOMAIN")
for h in "${SUBDOMAINS[@]}"; do FQDNS+=("$h.$DOMAIN"); done

# ---------- 1. 安装 ----------
if ! command -v nginx >/dev/null 2>&1 || ! command -v certbot >/dev/null 2>&1; then
  log "安装 nginx 与 certbot"
  apt-get update -qq
  apt-get install -y -qq nginx certbot
fi

# ---------- 2. 目录 ----------
mkdir -p /var/www/certbot
for s in "${SITES[@]}"; do
  mkdir -p "$WEB_ROOT/$s"
  # 占位页：前端还没推送时访问看到的是提示而不是 403。
  [[ -f "$WEB_ROOT/$s/index.html" ]] \
    || printf '<!doctype html><meta charset="utf-8"><p>%s 尚未发布</p>\n' "$s" > "$WEB_ROOT/$s/index.html"
done
# 前端由 DEPLOY_USER 经 ssh 推送，不需要 sudo。
chown -R "$DEPLOY_USER": "$WEB_ROOT"

# ---------- 3. 配置片段 ----------
install -m 644 "$HERE"/snippets/*.conf /etc/nginx/snippets/
# Ubuntu 默认站点占着 80 端口的 default_server，对本部署无用。
rm -f /etc/nginx/sites-enabled/default

systemctl enable --now nginx >/dev/null

# ---------- 4. 证书 ----------
CERT_DIR="/etc/letsencrypt/live/$CERT_NAME"

# 一张证书覆盖 FQDNS 全部域名。--expand：已有证书缺域名时原地扩展，不另起一张。
request_cert() {
  local domain_args=()
  for d in "${FQDNS[@]}"; do domain_args+=(-d "$d"); done
  certbot certonly --webroot -w /var/www/certbot \
    --cert-name "$CERT_NAME" "${domain_args[@]}" \
    --expand --agree-tos --non-interactive "$@"
}

# 签证书前先确认这些域名都有 DNS 记录。缺记录时 certbot 注定失败，
# 还会消耗 Let's Encrypt 的验证失败配额，不如直接指出是哪个域名。
require_dns() {
  local none=()
  for d in "$@"; do getent ahosts "$d" >/dev/null || none+=("$d"); done
  [[ ${#none[@]} -eq 0 ]] \
    || die "这些域名还没有 DNS 记录：${none[*]}。先加 A 记录指向本机公网 IP（Cloudflare 用灰云），再重跑本脚本"
}

# 列出 FQDNS 中现有证书还没覆盖的域名。
missing_domains() {
  local sans
  sans="$(openssl x509 -in "$CERT_DIR/cert.pem" -noout -ext subjectAltName 2>/dev/null \
    | grep -o 'DNS:[^,[:space:]]*' || true)"
  for d in "${FQDNS[@]}"; do
    grep -Fxq "DNS:$d" <<<"$sans" || echo "$d"
  done
}

if [[ ! -f "$CERT_DIR/fullchain.pem" ]]; then
  if [[ -n "$EMAIL" ]]; then
    email_args=(--email "$EMAIL" --no-eff-email)
  else
    email_args=(--register-unsafely-without-email)
  fi

  # 完整配置引用的证书文件此时还不存在，nginx -t 会失败。
  # 先装一个只响应 ACME 验证的临时配置，签完证书再换成完整配置。
  log "申请证书：${FQDNS[*]}"
  require_dns "${FQDNS[@]}"
  cat > /etc/nginx/conf.d/nebula.conf <<EOF
server {
    listen 80;
    listen [::]:80;
    server_name ${FQDNS[*]};
    location ^~ /.well-known/acme-challenge/ { root /var/www/certbot; }
    location / { return 503; }
}
EOF
  nginx -t && systemctl reload nginx

  request_cert "${email_args[@]}" \
    || die "证书申请失败：确认 ${FQDNS[*]} 都已解析到本机，且 80 端口对公网开放"
else
  log "证书已存在（$CERT_DIR）"
fi

# 续期成功后 reload，让 nginx 读到新证书。
mkdir -p /etc/letsencrypt/renewal-hooks/deploy
cat > /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh <<'EOF'
#!/bin/sh
systemctl reload nginx
EOF
chmod 755 /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh

# ---------- 5. 完整站点配置 ----------
log "安装站点配置（域名：$DOMAIN）"
sed "s/orccode\.com/$DOMAIN/g" "$HERE/nginx.conf" > /etc/nginx/conf.d/nebula.conf
nginx -t
systemctl reload nginx

# ---------- 6. 扩展证书 ----------
# 域名清单变多时（例如新加了主站），新 server 块先挂着旧证书（缺这些域名，浏览器会报不匹配）。
# 上一步的完整配置已在 80 端口放行这些域名的 ACME 验证，这里把证书扩展上去。
mapfile -t MISSING < <(missing_domains)
if [[ ${#MISSING[@]} -gt 0 ]]; then
  log "证书缺少 ${MISSING[*]}，扩展证书"
  require_dns "${MISSING[@]}"
  request_cert \
    || die "证书扩展失败：确认 ${MISSING[*]} 已解析到本机（Cloudflare 要灰云），且 80 端口对公网开放"
  systemctl reload nginx
fi

log "完成。检查 WireGuard 与后端连通性："
log "  curl -s http://10.100.0.2:19000/actuator/health"
log "  curl -s https://admin.$DOMAIN/api/manager/actuator/health"
log "  curl -sI https://$DOMAIN"
