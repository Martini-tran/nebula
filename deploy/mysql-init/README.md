# MySQL 首次初始化

MySQL 官方镜像会按**文件名字典序**执行 `/docker-entrypoint-initdb.d` 下的脚本，
且**只在数据卷为空时执行一次**（后续重启不会重复导入）。

直接挂载仓库的 `script/` 目录会失败：字典序下 `ai_copilot.sql` 排在 `nebula.sql`
之前，而它 `INSERT INTO sys_menu` —— 那张表要等 `nebula.sql` 才建出来，
首次启动会直接报 `Table 'nebula.sys_menu' doesn't exist`。

因此这里用数字前缀显式固定顺序，由 `deploy/scripts/init-db.sh` 生成软链：

| 顺序 | 来源 | 说明 |
|---|---|---|
| `01_nebula.sql` | `script/nebula.sql` | 主表结构，必须最先执行 |
| `02_sys_menu.sql` | `script/sys_menu.sql` | 菜单数据，依赖 `sys_menu` 表 |
| `03_ai_prompt.sql` | `script/ai_prompt.sql` | 提示词表与初始数据 |
| `04_ai_copilot.sql` | `script/ai_copilot.sql` | Copilot 菜单，依赖 `sys_menu` / `sys_role_menu` |
| `05_V20260807__create_ai_flow_draft.sql` | 同名 | 流程草稿表 |
| `06_V20260808__create_ai_harness_real_run.sql` | 同名 | Harness 运行表 |

`script/demo_blog_series_iteration.sql` 是**演示数据**，不自动导入。
需要时手动执行：

```bash
docker exec -i nebula-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" nebula \
  < script/demo_blog_series_iteration.sql
```

## 重新初始化

已经启动过一次后，改这里的脚本不会生效——因为数据卷非空。要重来一遍必须清库：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.yml down -v
./deploy/scripts/deploy.sh
```

`-v` 删除该 compose 项目的全部数据卷（MySQL / Redis / MinIO / Meilisearch），
比 `docker volume rm` 手写卷名可靠——卷名会随项目名变化。

本项目不做备份，数据丢失后重新部署即可，因此清库重来是预期内的常规操作。
