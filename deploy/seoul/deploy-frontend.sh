#!/usr/bin/env bash
#
# 构建前端并发布到首尔 nginx
#
# 在本地开发机上运行（Git Bash / Linux / macOS 均可），不在首尔上构建：
# web-ele 构建要 8G 堆（nebula-ui/package.json 里 max-old-space-size=8192），2c4g 扛不住。
#
# 用法：
#   SEOUL=ubuntu@<首尔公网IP> bash deploy/seoul/deploy-frontend.sh               # 全部 5 个
#   SEOUL=ubuntu@<首尔公网IP> bash deploy/seoul/deploy-frontend.sh admin blog    # 只发指定的
#   SEOUL=ubuntu@<首尔公网IP> bash deploy/seoul/deploy-frontend.sh --skip-build  # 只上传现有 dist
#
# 站点名：admin(web-ele) blog space forge scribe
# SEOUL 里的用户要和 setup.sh 的 DEPLOY_USER 一致（它拥有 /var/www/nebula）。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
WEB_ROOT=/var/www/nebula
ALL_SITES=(admin blog space forge scribe)

log() { printf '\033[0;32m[frontend]\033[0m %s\n' "$*"; }
die() { printf '\033[0;31m[error]\033[0m %s\n' "$*" >&2; exit 1; }

SEOUL="${SEOUL:-}"
[[ -n "$SEOUL" ]] || die "请设置 SEOUL=用户@首尔公网IP"

DO_BUILD=true
SITES=()
for arg in "$@"; do
  case "$arg" in
    --skip-build) DO_BUILD=false ;;
    -h|--help)    sed -n '2,16p' "$0"; exit 0 ;;
    admin|blog|space|forge|scribe) SITES+=("$arg") ;;
    *) die "未知参数：$arg（可选站点：${ALL_SITES[*]}）" ;;
  esac
done
[[ ${#SITES[@]} -gt 0 ]] || SITES=("${ALL_SITES[@]}")

# 站点 -> 前端工程目录
project_dir() {
  case "$1" in
    admin)  echo "$REPO_ROOT/ui/nebula-ui" ;;
    blog)   echo "$REPO_ROOT/ui/nebula-blog-ui" ;;
    space)  echo "$REPO_ROOT/ui/nebula-space" ;;
    forge)  echo "$REPO_ROOT/ui/nebula-forge" ;;
    scribe) echo "$REPO_ROOT/ui/nebula-scribe" ;;
  esac
}

# 站点 -> 构建产物目录
dist_dir() {
  case "$1" in
    admin) echo "$REPO_ROOT/ui/nebula-ui/apps/web-ele/dist" ;;
    *)     echo "$(project_dir "$1")/dist" ;;
  esac
}

build() {
  local site="$1" dir
  dir="$(project_dir "$site")"
  log "构建 $site（$dir）"
  (
    cd "$dir"
    if [[ "$site" == admin ]]; then
      # nebula-ui 是 pnpm monorepo，build:ele 只构建 web-ele 及其依赖包。
      [[ -d node_modules ]] || pnpm install --frozen-lockfile
      pnpm build:ele
    else
      # 其余四个是独立的 npm 工程（各自有 package-lock.json）。
      [[ -d node_modules ]] || npm ci
      npm run build
    fi
  )
}

# 打包上传并原子切换：先解压到 <站点>.new，再整体替换，避免用户访问到半套文件。
publish() {
  local site="$1" dist target
  dist="$(dist_dir "$site")"
  target="$WEB_ROOT/$site"
  [[ -f "$dist/index.html" ]] || die "$dist/index.html 不存在，先构建（去掉 --skip-build）"

  log "上传 $site -> $SEOUL:$target"
  tar -C "$dist" -czf - . | ssh "$SEOUL" "set -e
    rm -rf $target.new $target.old
    mkdir -p $target.new
    tar -xzf - -C $target.new
    if [ -d $target ]; then mv $target $target.old; fi
    mv $target.new $target
    rm -rf $target.old"
}

for site in "${SITES[@]}"; do
  $DO_BUILD && build "$site"
  publish "$site"
done

log "完成：${SITES[*]}"
