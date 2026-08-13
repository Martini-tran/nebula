#!/usr/bin/env bash
#
# Nebula 北京节点部署脚本
#
# 用法：
#   ./deploy/scripts/deploy.sh              # 部署核心服务（gateway/manager/blog + 基础设施）
#   ./deploy/scripts/deploy.sh --extra      # 同时部署 space/forge
#   ./deploy/scripts/deploy.sh --no-build   # 跳过构建，直接用现有镜像重启
#   ./deploy/scripts/deploy.sh --service blog  # 只重新构建并重启单个服务
#
# 设计约束：北京只有 4c4g，因此镜像串行构建。并行构建会让多个 Maven JVM
# 同时占用内存，构建阶段 OOM 是这台机器上最常见的失败原因。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(dirname "$SCRIPT_DIR")"
REPO_ROOT="$(dirname "$DEPLOY_DIR")"
COMPOSE_FILE="$DEPLOY_DIR/docker-compose.yml"
ENV_FILE="$DEPLOY_DIR/.env"

# 核心服务：首次部署只起这些，确认稳定后再加 extra。
CORE_SERVICES=(gateway manager blog)
EXTRA_SERVICES=(space forge)

WITH_EXTRA=false
DO_BUILD=true
SINGLE_SERVICE=""

log()  { printf '\033[0;32m[deploy]\033[0m %s\n' "$*"; }
warn() { printf '\033[0;33m[warn]\033[0m %s\n' "$*"; }
die()  { printf '\033[0;31m[error]\033[0m %s\n' "$*" >&2; exit 1; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --extra)     WITH_EXTRA=true; shift ;;
    --no-build)  DO_BUILD=false; shift ;;
    --service)   SINGLE_SERVICE="${2:-}"; [[ -n "$SINGLE_SERVICE" ]] || die "--service 需要参数"; shift 2 ;;
    -h|--help)   sed -n '2,20p' "$0"; exit 0 ;;
    *)           die "未知参数：$1" ;;
  esac
done

# ---------- 前置检查 ----------
command -v docker >/dev/null 2>&1 || die "未找到 docker"
docker compose version >/dev/null 2>&1 || die "未找到 docker compose v2"
[[ -f "$ENV_FILE" ]] || die "缺少 $ENV_FILE，请先执行：cp deploy/.env.example deploy/.env 并填写"

# 校验必填项非空。compose 的 ?: 语法只能拦住"未定义"，拦不住"定义为空字符串"，
# 而空密码恰恰是最危险的情况，所以这里显式再查一遍。
REQUIRED_VARS=(
  MYSQL_ROOT_PASSWORD MYSQL_PASSWORD REDIS_PASSWORD
  MINIO_ACCESS_KEY MINIO_SECRET_KEY MINIO_PUBLIC_DOMAIN
  MEILISEARCH_API_KEY AI_API_KEY AI_PROFILE_SECRET
)
missing=()
for var in "${REQUIRED_VARS[@]}"; do
  value="$(grep -E "^${var}=" "$ENV_FILE" | head -1 | cut -d= -f2- || true)"
  [[ -z "$value" ]] && missing+=("$var")
done
if [[ ${#missing[@]} -gt 0 ]]; then
  die "以下必填配置为空，请编辑 $ENV_FILE：$(printf '%s ' "${missing[@]}")"
fi

# MINIO_PUBLIC_DOMAIN 指向内部主机名时，浏览器拿到的直链会失效。
public_domain="$(grep -E '^MINIO_PUBLIC_DOMAIN=' "$ENV_FILE" | cut -d= -f2-)"
if [[ "$public_domain" == *"minio:9000"* || "$public_domain" == *"127.0.0.1"* ]]; then
  warn "MINIO_PUBLIC_DOMAIN=$public_domain 看起来是内网地址，浏览器将无法访问公开桶直链"
fi

# 内存体检：4c4g 上如果可用内存过低，构建阶段大概率 OOM。
if command -v free >/dev/null 2>&1; then
  avail_mb=$(free -m | awk '/^Mem:/{print $7}')
  log "当前可用内存：${avail_mb}MB"
  [[ "$avail_mb" -lt 800 ]] && warn "可用内存低于 800MB，构建可能因 OOM 失败；建议先 swapon 或停掉非必要容器"
fi

cd "$REPO_ROOT"

# 生成带序号的 MySQL 初始化脚本（幂等）。必须在起 mysql 之前，
# 否则首次启动会挂载到空目录，建不出表。
log "准备 MySQL 初始化脚本"
"$SCRIPT_DIR/init-db.sh"

COMPOSE=(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE")
$WITH_EXTRA && COMPOSE+=(--profile extra)

# ---------- 确定目标服务 ----------
if [[ -n "$SINGLE_SERVICE" ]]; then
  TARGETS=("$SINGLE_SERVICE")
else
  TARGETS=("${CORE_SERVICES[@]}")
  $WITH_EXTRA && TARGETS+=("${EXTRA_SERVICES[@]}")
fi

# ---------- 构建（串行）----------
if $DO_BUILD; then
  for svc in "${TARGETS[@]}"; do
    log "构建镜像：$svc（串行，避免内存峰值叠加）"
    "${COMPOSE[@]}" build "$svc"
  done
else
  log "跳过构建（--no-build）"
fi

# ---------- 启动 ----------
if [[ -n "$SINGLE_SERVICE" ]]; then
  log "重启单个服务：$SINGLE_SERVICE"
  "${COMPOSE[@]}" up -d --no-deps "$SINGLE_SERVICE"
else
  # 先起基础设施并等待健康，Java 服务的 depends_on 会自动串起顺序。
  log "启动基础设施：mysql / redis / minio / meilisearch"
  "${COMPOSE[@]}" up -d mysql redis minio meilisearch

  log "等待基础设施健康检查通过……"
  "${COMPOSE[@]}" up -d minio-init

  log "启动应用服务：${TARGETS[*]}"
  "${COMPOSE[@]}" up -d "${TARGETS[@]}"
fi

# ---------- 健康验证 ----------
log "等待服务就绪（最多 180 秒）……"
deadline=$((SECONDS + 180))
while (( SECONDS < deadline )); do
  unhealthy=0
  for svc in "${TARGETS[@]}"; do
    cid="$("${COMPOSE[@]}" ps -q "$svc" 2>/dev/null || true)"
    [[ -z "$cid" ]] && { unhealthy=1; continue; }
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cid" 2>/dev/null || echo unknown)"
    [[ "$status" == "healthy" || "$status" == "running" ]] || unhealthy=1
  done
  (( unhealthy == 0 )) && break
  sleep 5
done

echo
"${COMPOSE[@]}" ps
echo

failed=()
for svc in "${TARGETS[@]}"; do
  cid="$("${COMPOSE[@]}" ps -q "$svc" 2>/dev/null || true)"
  status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cid" 2>/dev/null || echo missing)"
  [[ "$status" == "healthy" || "$status" == "running" ]] || failed+=("$svc($status)")
done

if [[ ${#failed[@]} -gt 0 ]]; then
  warn "以下服务未就绪：${failed[*]}"
  warn "查看日志：docker compose --env-file deploy/.env -f deploy/docker-compose.yml logs -f ${failed[0]%%(*}"
  exit 1
fi

log "部署完成。Gateway 健康检查："
log "  curl http://127.0.0.1:19000/actuator/health"
$WITH_EXTRA || log "如需 space/forge，确认内存充足后执行：./deploy/scripts/deploy.sh --extra"
