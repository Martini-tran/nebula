#!/usr/bin/env bash
#
# 生成 MySQL 首次初始化脚本目录
#
# MySQL 镜像按字典序执行 initdb 脚本。script/mysql/nebula.sql 是整库导出（建表 + 菜单/角色/
# 用户等初始数据），其余脚本是导出里缺的增量，必须排在它后面。本脚本按正确顺序复制并加
# 数字前缀，供 compose 挂载。
#
# 用法：./deploy/scripts/init-db.sh   （deploy.sh 会自动调用，一般无需手动执行）

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(dirname "$SCRIPT_DIR")"
REPO_ROOT="$(dirname "$DEPLOY_DIR")"
SRC_DIR="$REPO_ROOT/script/mysql"
OUT_DIR="$DEPLOY_DIR/mysql-init"

log() { printf '\033[0;32m[init-db]\033[0m %s\n' "$*"; }
die() { printf '\033[0;31m[error]\033[0m %s\n' "$*" >&2; exit 1; }

# 执行顺序即依赖顺序，不要随意调整：
#   nebula.sql 建全部主表并写入初始数据 -> 其余增量脚本才能 INSERT / ALTER
ORDERED=(
  "nebula.sql"
  # 整库导出时漏了 ai_skill 表（代码里 AiSkill 实体在用），这里补上表、种子数据和菜单 92。
  # 它会 DELETE + INSERT sys_menu 行，必须排在 nebula.sql 之后。
  "ai_skill.sql"
)

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
