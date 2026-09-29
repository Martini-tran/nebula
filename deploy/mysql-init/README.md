# MySQL 首次初始化

MySQL 官方镜像会按**文件名字典序**执行 `/docker-entrypoint-initdb.d` 下的脚本，
且**只在数据卷为空时执行一次**（后续重启不会重复导入）。

源文件在仓库的 `script/mysql/` 下：`nebula.sql` 是整库导出（全部建表 + 菜单、角色、
用户、系统配置等初始数据），其余文件是导出里缺的增量，必须在它之后执行。
这里用数字前缀显式固定顺序，由 `deploy/scripts/init-db.sh` 生成副本：

| 顺序 | 来源 | 说明 |
|---|---|---|
| `01_nebula.sql` | `script/mysql/nebula.sql` | 整库结构与初始数据，必须最先执行 |
| `02_ai_skill.sql` | `script/mysql/ai_skill.sql` | 导出时漏掉的 `ai_skill` 表、种子技能与「技能」菜单（id 92） |

以后再补增量 SQL：放进 `script/mysql/`，然后追加到 `init-db.sh` 的 `ORDERED` 列表末尾。

## 重新初始化

已经启动过一次后，改这里的脚本不会生效——因为数据卷非空。要重来一遍必须清库：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.yml down -v
./deploy/scripts/deploy.sh
```

`-v` 删除该 compose 项目的全部数据卷（MySQL / Redis / MinIO / Meilisearch），
比 `docker volume rm` 手写卷名可靠——卷名会随项目名变化。

本项目不做备份，数据丢失后重新部署即可，因此清库重来是预期内的常规操作。
