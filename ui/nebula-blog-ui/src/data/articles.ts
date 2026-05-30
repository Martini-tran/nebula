export type ArticleSummary = {
  id: number
  category: string
  title: string
  summary: string
  createdAt: string
  tags: string[]
}

export type Article = ArticleSummary & {
  readTime: string
  author: { name: string }
  content: string
}

export const articles: Article[] = [
  {
    id: 1,
    category: 'Java',
    title: 'Java 中如何优雅处理文件路径？',
    summary: '记录跨平台文件路径处理、Linux 分隔符使用以及项目中常见的路径拼接问题。',
    createdAt: '2026-04-27',
    readTime: '8 分钟阅读',
    tags: ['Java', 'Linux', '文件处理'],
    author: { name: 'FluxLu' },
    content: `## 背景

在跨平台开发中，文件路径的处理是一个常见的坑。Windows 使用反斜杠 \`\\\\\`，而 Linux/macOS 使用正斜杠 \`/\`，直接硬编码路径分隔符会导致代码在不同系统上行为不一致。

## 推荐做法

### 1. 使用 \`Path\` 和 \`Paths\`

Java 7 引入的 \`java.nio.file.Path\` 是处理路径的最佳方式：

\`\`\`java
Path path = Paths.get("src", "main", "resources", "config.yml");
System.out.println(path.toString()); // 自动适配当前系统分隔符
\`\`\`

### 2. 避免直接拼接字符串

\`\`\`java
// 不推荐
String bad = "src" + "/" + "main" + "/" + "config.yml";

// 推荐
Path good = Paths.get("src", "main", "config.yml");
\`\`\`

### 3. 获取资源文件路径

\`\`\`java
URL resource = getClass().getClassLoader().getResource("config.yml");
Path path = Paths.get(resource.toURI());
\`\`\`

## 总结

- 优先使用 \`Paths.get()\` 多参数形式
- 避免硬编码 \`/\` 或 \`\\\\\`
- 读取 classpath 资源用 \`ClassLoader.getResource()\`
`,
  },
  {
    id: 2,
    category: '数据库',
    title: '一次慢 SQL 排查的完整过程',
    summary: '从执行计划、索引命中、数据量分布几个角度复盘一次真实的慢查询优化。',
    createdAt: '2026-04-22',
    readTime: '10 分钟阅读',
    tags: ['MySQL', '索引', 'SQL优化'],
    author: { name: 'FluxLu' },
    content: `## 背景

线上一条订单查询接口偶发超时，本文复盘整个排查过程。

## 排查步骤

### 1. 拿到慢 SQL

通过慢查询日志定位具体语句。

### 2. 看执行计划

使用 \`EXPLAIN\` 检查是否走了索引、扫描行数是否合理。

### 3. 分析数据分布

某些场景下数据倾斜会导致索引失效。

## 总结

- 先看执行计划，再谈优化
- 索引设计要结合实际查询模式
`,
  },
  {
    id: 3,
    category: '工具效率',
    title: '大文件如何压缩成 tar.gz?',
    summary: '整理 Linux 环境下大文件压缩、分卷、校验与解压的常用命令。',
    createdAt: '2026-04-18',
    readTime: '5 分钟阅读',
    tags: ['Linux', 'tar.gz', '命令行'],
    author: { name: 'FluxLu' },
    content: `## 常用命令

### 压缩

\`\`\`bash
tar -czvf archive.tar.gz /path/to/dir
\`\`\`

### 解压

\`\`\`bash
tar -xzvf archive.tar.gz
\`\`\`

### 分卷

\`\`\`bash
split -b 100M archive.tar.gz archive.tar.gz.part-
\`\`\`

## 总结

- \`-c\` 创建，\`-x\` 解压，\`-z\` 走 gzip
- 大文件分卷传输更稳
`,
  },
]

export const articleSummaries: ArticleSummary[] = articles.map(
  ({ id, category, title, summary, createdAt, tags }) => ({
    id,
    category,
    title,
    summary,
    createdAt,
    tags,
  }),
)

export const findArticle = (id: number | string | undefined): Article | undefined => {
  if (id === undefined || id === null || id === '') return undefined
  const numeric = typeof id === 'number' ? id : Number(id)
  if (Number.isNaN(numeric)) return undefined
  return articles.find((item) => item.id === numeric)
}
