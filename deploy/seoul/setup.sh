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
# 可重复执行：证书已存在就跳过申请，只更新配置并 reload。改了 nginx.conf / snippets 后重跑即可。
#
# 前置条件：
#   - 6 个子域名（admin/blog/space/forge/scribe/s3）的 DNS 已解析到本机公网 IP
#   - 安全组放行 80/443，否则 Let's Encrypt 验证不通过

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOMAIN="${DOMAIN:-orccode.com}"
EMAIL="${EMAIL:-}"
DEPLOY_USER="${DEPLOY_USER:-${SUDO_USER:-root}}"

SITES=(admin blog space forge scribe)
HOSTS=(admin blog space forge scribe s3)
WEB_ROOT=/var/www/nebula
CERT_NAME=nebula

log()  { printf '\033[0;32m[setup]\033[0m %s\n' "$*"; }
die()  { printf '\033[0;31m[error]\033[0m %s\n' "$*" >&2; exit 1; }

[[ $EUID -eq 0 ]] || die "请用 root 或 sudo 执行"
[[ -f "$HERE/nginx.conf" ]] || die "找不到 $HERE/nginx.conf"
id "$DEPLOY_USER" >/dev/null 2>&1 || die "用户 $DEPLOY_USER 不存在，用 DEPLOY_USER=xxx 指定"

FQDNS=()
for h in "${HOSTS[@]}"; do FQDNS+=("$h.$DOMAIN"); done

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
if [[ ! -f "/etc/letsencrypt/live/$CERT_NAME/fullchain.pem" ]]; then
  if [[ -n "$EMAIL" ]]; then
    email_args=(--email "$EMAIL" --no-eff-email)
  else
    email_args=(--register-unsafely-without-email)
  fi

  # 完整配置引用的证书文件此时还不存在，nginx -t 会失败。
  # 先装一个只响应 ACME 验证的临时配置，签完证书再换成完整配置。
  log "申请证书：${FQDNS[*]}"
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

  domain_args=()
  for d in "${FQDNS[@]}"; do domain_args+=(-d "$d"); done
  certbot certonly --webroot -w /var/www/certbot \
    --cert-name "$CERT_NAME" "${domain_args[@]}" \
    "${email_args[@]}" --agree-tos --non-interactive \
    || die "证书申请失败：确认 6 个子域名都已解析到本机，且 80 端口对公网开放"
else
  log "证书已存在，跳过申请（/etc/letsencrypt/live/$CERT_NAME）"
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

log "完成。检查 WireGuard 与后端连通性："
log "  curl -s http://10.100.0.2:19000/actuator/health"
log "  curl -s https://admin.$DOMAIN/api/manager/actuator/health"
