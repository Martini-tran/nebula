#!/usr/bin/env bash
#
# Nebula 部署健康巡检
#
# 用法：./deploy/scripts/health.sh
# 逐项验证容器状态、内存水位、依赖连通性和对外链路，输出可直接贴进值班记录。

set -uo pipefail   # 故意不加 -e：巡检要跑完全部检查项，不能中途退出

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(dirname "$SCRIPT_DIR")"
ENV_FILE="$DEPLOY_DIR/.env"
COMPOSE_FILE="$DEPLOY_DIR/docker-compose.yml"

PASS=0; FAIL=0; WARN=0
ok()   { printf '  \033[0;32m✓\033[0m %s\n' "$*"; PASS=$((PASS+1)); }
bad()  { printf '  \033[0;31m✗\033[0m %s\n' "$*"; FAIL=$((FAIL+1)); }
warn() { printf '  \033[0;33m!\033[0m %s\n' "$*"; WARN=$((WARN+1)); }
head_() { printf '\n\033[1m%s\033[0m\n' "$*"; }

[[ -f "$ENV_FILE" ]] || { echo "缺少 $ENV_FILE"; exit 1; }
# shellcheck disable=SC1090
set -a; source "$ENV_FILE"; set +a

# ---------- 容器状态 ----------
head_ "容器状态"
for c in nebula-mysql nebula-redis nebula-minio nebula-meilisearch \
         nebula-gateway nebula-manager nebula-blog; do
  if ! docker ps -a --format '{{.Names}}' | grep -qx "$c"; then
    warn "$c 未创建（可能属于 extra profile 或尚未部署）"
    continue
  fi
  state="$(docker inspect -f '{{.State.Status}}' "$c" 2>/dev/null)"
  health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}-{{end}}' "$c" 2>/dev/null)"
  if [[ "$state" == "running" && ( "$health" == "healthy" || "$health" == "-" ) ]]; then
    ok "$c ($state${health:+/$health})"
  else
    bad "$c 状态异常：$state/$health"
  fi
done

# ---------- 内存水位 ----------
# 4c4g 是这套部署最硬的约束，容器内存逼近 limit 时会被 OOMKill 并静默重启。
head_ "内存水位（4c4g 关键指标）"
if command -v free >/dev/null 2>&1; then
  free -h | awk '/^Mem:/{printf "  总计 %s / 已用 %s / 可用 %s\n", $2, $3, $7}'
  avail_mb=$(free -m | awk '/^Mem:/{print $7}')
  if   [[ "$avail_mb" -lt 200 ]]; then bad "可用内存仅 ${avail_mb}MB，OOM 风险高"
  elif [[ "$avail_mb" -lt 500 ]]; then warn "可用内存 ${avail_mb}MB 偏低"
  else ok "可用内存 ${avail_mb}MB"; fi
fi

echo "  --- 各容器占用 ---"
docker stats --no-stream --format '  {{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}' 2>/dev/null \
  | grep nebula || echo "  (无运行中容器)"

# 检查是否发生过 OOMKill。容器被杀后会自动重启，日志里看不出异常，
# 只有 RestartCount 和 OOMKilled 标志能暴露真相。
for c in nebula-manager nebula-blog nebula-gateway; do
  docker ps -a --format '{{.Names}}' | grep -qx "$c" || continue
  oom="$(docker inspect -f '{{.State.OOMKilled}}' "$c" 2>/dev/null)"
  restarts="$(docker inspect -f '{{.RestartCount}}' "$c" 2>/dev/null)"
  [[ "$oom" == "true" ]] && bad "$c 曾被 OOMKill，需下调 JAVA_OPTS 堆上限"
  [[ "${restarts:-0}" -gt 3 ]] && warn "$c 重启 ${restarts} 次，可能存在崩溃循环"
done

# ---------- 依赖连通性 ----------
head_ "依赖连通性"
docker exec nebula-mysql mysqladmin ping -h localhost -p"$MYSQL_ROOT_PASSWORD" >/dev/null 2>&1 \
  && ok "MySQL 可连接" || bad "MySQL 不可连接"

docker exec nebula-redis redis-cli -a "$REDIS_PASSWORD" ping >/dev/null 2>&1 \
  && ok "Redis 可连接" || bad "Redis 不可连接"

curl -fsS "http://127.0.0.1:7700/health" >/dev/null 2>&1 \
  && ok "Meilisearch 可连接" || bad "Meilisearch 不可连接"

curl -fsS "http://127.0.0.1:9000/minio/health/live" >/dev/null 2>&1 \
  && ok "MinIO 可连接" || bad "MinIO 不可连接"

# ---------- 服务健康端点 ----------
head_ "服务健康端点"
check_http() {
  local name="$1" url="$2"
  local code
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$url" 2>/dev/null)"
  [[ "$code" == "200" ]] && ok "$name -> 200" || bad "$name -> ${code:-无响应} ($url)"
}
check_http "Gateway"  "http://${GATEWAY_BIND_ADDR:-127.0.0.1}:19000/actuator/health"
# 经 Gateway 路由访问下游，同时验证路由规则和下游存活。
check_http "Manager"  "http://${GATEWAY_BIND_ADDR:-127.0.0.1}:19000/manager/actuator/health"
check_http "Blog"     "http://${GATEWAY_BIND_ADDR:-127.0.0.1}:19000/blog/actuator/health"

# ---------- 安全检查 ----------
head_ "安全检查（端口暴露）"
if command -v ss >/dev/null 2>&1; then
  for port in 3306 13306 6379 16379 9000 9001 7700; do
    if ss -lnt 2>/dev/null | awk '{print $4}' | grep -qE "^(0\.0\.0\.0|\[::\]):$port$"; then
      bad "端口 $port 监听在 0.0.0.0，存在公网暴露风险"
    else
      ok "端口 $port 未对公网监听"
    fi
  done
  if ss -lnt 2>/dev/null | awk '{print $4}' | grep -qE "^(0\.0\.0\.0|\[::\]):19000$"; then
    warn "Gateway 19000 监听 0.0.0.0，建议设 GATEWAY_BIND_ADDR 为 WireGuard 内网 IP"
  fi
fi

# ---------- 磁盘 ----------
head_ "磁盘"
df -h / | awk 'NR==2{printf "  根分区 已用 %s / 共 %s (%s)\n", $3, $2, $5}'
use=$(df / | awk 'NR==2{gsub(/%/,"",$5); print $5}')
[[ "$use" -gt 85 ]] && bad "磁盘使用率 ${use}%，请清理日志或旧镜像（docker system prune）" || ok "磁盘使用率 ${use}%"

# ---------- 汇总 ----------
printf '\n\033[1m汇总：\033[0m通过 %d，警告 %d，失败 %d\n' "$PASS" "$WARN" "$FAIL"
[[ "$FAIL" -gt 0 ]] && exit 1 || exit 0
