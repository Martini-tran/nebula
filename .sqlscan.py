"""扫描 SQL 文件中 CREATE TABLE 列数与 INSERT 值数不匹配的地方。"""
import re, sys, pathlib

BACKSLASH = chr(92)

def split_values(v):
    """按顶层逗号切分 VALUES(...) 内容，正确处理引号与转义。"""
    parts, depth, instr, esc, cur = [], 0, False, False, []
    for ch in v:
        if esc:
            cur.append(ch); esc = False; continue
        if ch == BACKSLASH:
            cur.append(ch); esc = True; continue
        if ch == "'":
            instr = not instr; cur.append(ch); continue
        if not instr:
            if ch in '([{':
                depth += 1
            elif ch in ')]}':
                depth -= 1
            elif ch == ',' and depth == 0:
                parts.append(''.join(cur)); cur = []; continue
        cur.append(ch)
    parts.append(''.join(cur))
    return parts


def scan(path):
    s = pathlib.Path(path).read_text(encoding='utf-8', errors='replace')

    # 表名 -> 列数
    cols = {}
    for m in re.finditer(r'CREATE TABLE `(\w+)`\s*\((.*?)\n\)\s*ENGINE', s, re.S):
        body = m.group(2)
        names = []
        for line in body.split('\n'):
            cm = re.match(r'\s+`(\w+)`\s+\w', line)
            if cm:
                names.append(cm.group(1))
        cols[m.group(1)] = names

    bad = []
    # 每条 INSERT 的值数
    for m in re.finditer(r'INSERT INTO `(\w+)`(?:\s*\([^)]*\))?\s*VALUES\s*(.+?);\s*$', s, re.S | re.M):
        tbl = m.group(1)
        if tbl not in cols:
            continue
        if re.match(r'INSERT INTO `\w+`\s*\(', m.group(0)):
            continue  # 显式列名的 INSERT 不受影响
        line_no = s[:m.start()].count('\n') + 1
        raw = m.group(2).strip()
        # 可能是多行 VALUES (...),(...)
        for tup in re.finditer(r'\((.*?)\)(?=\s*,\s*\(|\s*$)', raw, re.S):
            n = len(split_values(tup.group(1)))
            if n != len(cols[tbl]):
                bad.append((tbl, line_no, len(cols[tbl]), n))
                break
    return cols, bad


for f in sys.argv[1:]:
    cols, bad = scan(f)
    name = pathlib.Path(f).name
    print(f"\n{'='*66}\n{name}   (建表 {len(cols)} 张)")
    if not bad:
        print("  ✅ 未发现列数/值数不匹配")
    else:
        print(f"  ❌ 发现 {len(bad)} 张表不匹配：")
        print(f"  {'表名':<34}{'行号':>7}{'建表列':>8}{'INSERT值':>9}  差")
        for t, ln, c, n in bad:
            print(f"  {t:<34}{ln:>7}{c:>8}{n:>9}  {n-c:+d}")
