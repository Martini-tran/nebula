export type SeriesChapter = {
  id: number
  title: string
  summary: string
  status: 'published' | 'draft'
  minutes: number
}

export type SeriesItem = {
  id: number
  slug: string
  navLabel: string
  title: string
  description: string
  cover: string
  articleCount: number
  tags: string[]
  updatedAt: string
  level: string
  chapters: SeriesChapter[]
}

export const seriesList: SeriesItem[] = [
  {
    id: 1,
    slug: 'mysql-deep-dive',
    navLabel: 'MySQL',
    title: 'MySQL 深入理解',
    description: '从索引原理到事务隔离，系统梳理 MySQL 核心机制，适合有一定基础的后端开发者。',
    cover: '',
    articleCount: 8,
    tags: ['MySQL', '数据库', '后端'],
    updatedAt: '2024-12',
    level: '进阶',
    chapters: [
      {
        id: 1,
        title: '索引为什么能让查询变快',
        summary: '从 B+Tree 的结构开始，理解索引命中、回表和覆盖索引。',
        status: 'published',
        minutes: 12,
      },
      {
        id: 2,
        title: '事务隔离级别与一致性读',
        summary: '梳理 MVCC、当前读、一致性读以及常见并发问题。',
        status: 'published',
        minutes: 15,
      },
      {
        id: 3,
        title: '慢 SQL 排查的完整路径',
        summary: '用执行计划、索引选择和数据分布定位真实性能瓶颈。',
        status: 'draft',
        minutes: 10,
      },
    ],
  },
  {
    id: 2,
    slug: 'ai-agent-practice',
    navLabel: 'AI Agent',
    title: 'AI Agent 实战笔记',
    description: '围绕工具调用、记忆管理、任务编排与评估体系，整理 AI Agent 从原型到可用产品的关键路径。',
    cover: '',
    articleCount: 7,
    tags: ['AI Agent', 'LLM', '工程化'],
    updatedAt: '2025-05',
    level: '实战',
    chapters: [
      {
        id: 1,
        title: 'Agent 不只是套一层对话',
        summary: '拆开规划、工具、记忆和反馈循环，理解 Agent 的基本组成。',
        status: 'published',
        minutes: 11,
      },
      {
        id: 2,
        title: '工具调用的边界与失败恢复',
        summary: '设计工具 schema、执行确认、重试策略和可观测日志。',
        status: 'draft',
        minutes: 14,
      },
      {
        id: 3,
        title: '如何评估一个 Agent 是否可靠',
        summary: '从任务成功率、成本、延迟和人工接管率建立评估指标。',
        status: 'draft',
        minutes: 13,
      },
    ],
  },
  {
    id: 3,
    slug: 'go-concurrency-practice',
    navLabel: 'Go',
    title: 'Go 并发编程实践',
    description: 'Goroutine、Channel、sync 包的实战用法，结合真实场景讲解并发模型与陷阱。',
    cover: '',
    articleCount: 6,
    tags: ['Go', '并发', '后端'],
    updatedAt: '2025-01',
    level: '实战',
    chapters: [
      {
        id: 1,
        title: 'Goroutine 的生命周期管理',
        summary: '从启动、取消到资源释放，避免后台任务泄漏。',
        status: 'published',
        minutes: 9,
      },
      {
        id: 2,
        title: 'Channel 不只是队列',
        summary: '用通信组织并发流程，理解阻塞、关闭和 select。',
        status: 'published',
        minutes: 11,
      },
      {
        id: 3,
        title: '并发安全与 sync 包选择',
        summary: '对比 Mutex、RWMutex、Once、WaitGroup 的适用边界。',
        status: 'draft',
        minutes: 13,
      },
    ],
  },
  {
    id: 4,
    slug: 'system-design-notes',
    navLabel: '系统设计',
    title: '系统设计笔记',
    description: '分布式系统、缓存策略、消息队列等内容，记录设计大型系统时的思考与权衡。',
    cover: '',
    articleCount: 5,
    tags: ['系统设计', '架构', '分布式'],
    updatedAt: '2025-03',
    level: '架构',
    chapters: [
      {
        id: 1,
        title: '缓存策略与一致性边界',
        summary: '从旁路缓存到失效策略，判断什么时候接受最终一致。',
        status: 'published',
        minutes: 14,
      },
      {
        id: 2,
        title: '消息队列在系统中的位置',
        summary: '理解削峰、异步解耦、重试与死信队列的设计成本。',
        status: 'draft',
        minutes: 12,
      },
    ],
  },
  {
    id: 5,
    slug: 'docker-k8s-startup',
    navLabel: 'Docker/K8s',
    title: 'Docker & K8s 入门到实战',
    description: '容器化部署从零开始，覆盖镜像构建、编排调度、服务发现等核心概念。',
    cover: '',
    articleCount: 10,
    tags: ['Docker', 'Kubernetes', 'DevOps'],
    updatedAt: '2025-04',
    level: '入门',
    chapters: [
      {
        id: 1,
        title: '镜像、容器与 Dockerfile',
        summary: '用最小示例理解容器运行时和镜像构建流程。',
        status: 'published',
        minutes: 10,
      },
      {
        id: 2,
        title: 'Compose 到 Kubernetes 的迁移思路',
        summary: '把本地编排映射到 Deployment、Service 和 ConfigMap。',
        status: 'draft',
        minutes: 16,
      },
    ],
  },
]

export const findSeries = (slug: string | string[] | undefined) => {
  const normalized = Array.isArray(slug) ? slug[0] : slug
  if (!normalized) return undefined
  return seriesList.find((item) => item.slug === normalized)
}
