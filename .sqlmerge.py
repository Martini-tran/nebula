"""从 docs/nebula.sql 提取配置类数据，生成 script/ai_flow_seed.sql。

只取「配置」（画布上建立的编排图定义、Agent 定义），
排除「运行记录」（实例、执行记录、演练数据、编辑器草稿）。

两个必须处理的坑：
  1. docs 的 ai_agent 是 14 列，script 已加到 15 列（多 skill_codes）。
     按位置解析后在第 11 位插入 NULL，不能用字符串替换——
     id=8 的 default_profile_code 是 NULL 而非字符串，替换模式会落空。
  2. docs 与 nebula.sql 都含 blog_series 的节点，但主键 ID 不重叠。
     若只按 ID 去重会同时插入新旧两套（3+10=13 个节点），流程图错乱。
     故对本文件覆盖的 flow_code 先 DELETE 再插入，让 docs 版本成为唯一真相。
"""
import re
import pathlib

DOCS = pathlib.Path("docs/nebula.sql")
BASE = pathlib.Path("script/nebula.sql")
OUT = pathlib.Path("script/ai_flow_seed.sql")

BACKSLASH = chr(92)

docs = DOCS.read_text(encoding="utf-8", errors="replace")
base = BASE.read_text(encoding="utf-8", errors="replace")


def split_vals(v):
    parts, depth, instr, esc, cur = [], 0, False, False, []
    for ch in v:
        if esc:
            cur.append(ch); esc = False; continue
        if ch == BACKSLASH:
            cur.append(ch); esc = True; continue
        if ch == "'":
            instr = not instr; cur.append(ch); continue
        if not instr:
            if ch in "([{":
                depth += 1
            elif ch in ")]}":
                depth -= 1
            elif ch == "," and depth == 0:
                parts.append("".join(cur)); cur = []; continue
        cur.append(ch)
    parts.append("".join(cur))
    return [p.strip() for p in parts]


def inserts(text, table):
    return re.findall(r"^INSERT INTO `%s` VALUES \((.*?)\);\s*$" % table, text, re.M)


def columns(text, table):
    m = re.search(r"CREATE TABLE `%s`\s*\((.*?)\n\)\s*ENGINE" % table, text, re.S)
    return re.findall(r"^\s+`(\w+)`\s+\w", m.group(1), re.M)


base_agent_cols = columns(base, "ai_agent")
docs_agent_cols = columns(docs, "ai_agent")
missing = [c for c in base_agent_cols if c not in docs_agent_cols]
assert missing == ["skill_codes"], f"ai_agent 列差异超出预期: {missing}"
skill_idx = base_agent_cols.index("skill_codes")

flow_code_idx = columns(base, "ai_flow_node").index("flow_code")

# 本文件接管的流程：docs 中出现过节点的所有 flow_code
covered = []
for v in inserts(docs, "ai_flow_node"):
    fc = split_vals(v)[flow_code_idx]
    if fc not in covered:
        covered.append(fc)

L = [
    "-- ============================================================",
    "-- 流程图与 Agent 配置种子数据",
    "--",
    "-- 来源：docs/nebula.sql（开发环境导出）中的配置类数据。",
    "-- 仅含画布上建立的编排图定义与 Agent 定义，刻意排除运行时记录",
    "-- (ai_agent_instance / ai_flow_run / ai_harness_* / ai_flow_draft)，",
    "-- 避免把开发环境的执行痕迹带进生产库。",
    "--",
    "-- 本文件由 .sqlmerge.py 生成，勿手工编辑。",
    "-- ============================================================",
    "",
    "SET NAMES utf8mb4;",
    "",
    "-- nebula.sql 的种子里夹带了开发环境的运行记录（Agent 实例、状态转移、",
    "-- 迭代链等）。留着会让 IterationDriver 的 heartbeat 反复去跑这些历史链条，",
    "-- 在新环境里必然失败并刷满错误日志，故在此清空。",
    "-- 注意只清运行记录，编排图与 Agent 定义属于配置，必须保留。",
    "DELETE FROM `ai_agent_instance_transition`;",
    "DELETE FROM `ai_agent_instance`;",
    "DELETE FROM `ai_agent_iteration`;",
    "DELETE FROM `ai_flow_run_node`;",
    "DELETE FROM `ai_flow_run`;",
    "DELETE FROM `ai_harness_confirmation`;",
    "DELETE FROM `ai_harness_operation`;",
    "DELETE FROM `ai_flow_draft`;",
    "",
    "-- nebula.sql 里也有 blog_series 的旧版节点，且主键与此处不重叠。",
    "-- 不先清掉会新旧并存导致流程图错乱，故本文件接管的流程整体重建。",
    "DELETE FROM `ai_flow_edge` WHERE `flow_code` IN (%s);" % ", ".join(covered),
    "DELETE FROM `ai_flow_node` WHERE `flow_code` IN (%s);" % ", ".join(covered),
    "DELETE FROM `ai_flow`      WHERE `flow_code` IN (%s);" % ", ".join(covered),
    "",
]

stats = []

# ai_flow / ai_flow_node / ai_flow_edge：接管的 flow_code 全量搬运
for t in ["ai_flow", "ai_flow_node", "ai_flow_edge"]:
    idx = columns(base, t).index("flow_code")
    rows = [v for v in inserts(docs, t) if split_vals(v)[idx] in covered]
    L.append(f"-- ---------- {t} ({len(rows)} 条) ----------")
    L += [f"INSERT INTO `{t}` VALUES ({v});" for v in rows]
    L.append("")
    stats.append((t, len(rows)))

# ai_agent：只补 base 中不存在的 id，并在第 11 位插入 skill_codes=NULL
have = {split_vals(v)[0] for v in inserts(base, "ai_agent")}
rows = []
for v in inserts(docs, "ai_agent"):
    parts = split_vals(v)
    if parts[0] in have:
        continue
    assert len(parts) == len(docs_agent_cols), f"ai_agent 值数异常: {len(parts)}"
    parts.insert(skill_idx, "NULL")
    rows.append(", ".join(parts))
L.append(f"-- ---------- ai_agent ({len(rows)} 条) ----------")
L += [f"INSERT INTO `ai_agent` VALUES ({v});" for v in rows]
L.append("")
stats.append(("ai_agent", len(rows)))

# 强制 LF：Windows 上 write_text 会把 \n 转成 \r\n
OUT.write_bytes(("\n".join(L) + "\n").encode("utf-8"))

print(f"已生成 {OUT}\n")
print("接管的流程：" + ", ".join(c.strip("'") for c in covered))
print(f"\n{'表':<18}{'条数':>6}")
print("-" * 26)
for t, n in stats:
    print(f"{t:<18}{n:>6}")
print("-" * 26)
print(f"{'合计':<18}{sum(n for _, n in stats):>6}")
