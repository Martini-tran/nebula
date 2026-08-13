#!/usr/bin/env bash
#
# 生成 MySQL 首次初始化脚本目录
#
# MySQL 镜像按字典序执行 initdb 脚本，而仓库 script/ 下的文件名顺序是错的：
# ai_copilot.sql 会排在 nebula.sql 前面，但它依赖后者建的 sys_menu 表。
# 本脚本把源文件按正确顺序复制并加数字前缀，供 compose 挂载。
#
# 用法：./deploy/scripts/init-db.sh   （deploy.sh 会自动调用，一般无需手动执行）

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(dirname "$SCRIPT_DIR")"
REPO_ROOT="$(dirname "$DEPLOY_DIR")"
SRC_DIR="$REPO_ROOT/script"
OUT_DIR="$DEPLOY_DIR/mysql-init"

log() { printf '\033[0;32m[init-db]\033[0m %s\n' "$*"; }
die() { printf '\033[0;31m[error]\033[0m %s\n' "$*" >&2; exit 1; }

# 执行顺序即依赖顺序，不要随意调整：
#   nebula.sql 建全部主表 -> 其余脚本才能 INSERT / ALTER
ORDERED=(
  "nebula.sql"
  "sys_menu.sql"
  "ai_prompt.sql"
  "ai_copilot.sql"
  "V20260807__create_ai_flow_draft.sql"
  "V20260808__create_ai_harness_real_run.sql"
)
# demo_blog_series_iteration.sql 是演示数据，刻意排除，避免污染生产库。

[[ -d "$SRC_DIR" ]] || die "找不到源目录 $SRC_DIR"
mkdir -p "$OUT_DIR"

# 清掉上一次生成的产物（保留 README.md）
find "$OUT_DIR" -maxdepth 1 -name '*.sql' -delete

idx=1
for f in "${ORDERED[@]}"; do
  src="$SRC_DIR/$f"
  if [[ ! -f "$src" ]]; then
    die "缺少 $src —— 请确认仓库完整，或更新本脚本的 ORDERED 列表"
  fi
  dst="$(printf '%s/%02d_%s' "$OUT_DIR" "$idx" "$f")"
  cp "$src" "$dst"
  log "$(printf '%02d' "$idx") <- $f"
  idx=$((idx + 1))
done

log "已生成 $((idx - 1)) 个初始化脚本到 deploy/mysql-init/"
log "提示：这些脚本只在 MySQL 数据卷为空（首次启动）时执行一次。"
