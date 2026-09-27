/**
 * 任务与清单接口。后端还没有（设计见 space-tasks.html「后端待补」），路径按设计拟定为 /space/me/tasks；
 * 未接通时走下面的 mock，数据存在浏览器 localStorage。
 *
 * 规则（后端实现时照搬）：
 * - 视图：收件箱 = 未完成且没日期；今天 = 未完成且日期 ≤ 今天（含过期）；计划 = 未完成且在未来 7 天内
 * - 完成一条重复任务时，按规则生成下一次（子任务重置为未完成），不在原任务上改日期
 */
import { del, get, post, put } from '../utils/request'
import { createMockTable, delay, nextId, useMockFor } from './mock'
import { addDays, nextWeekday, nowStamp, todayYmd } from '../utils/date'
import { nextOccurrence } from '../utils/repeat'
import type { EntityId } from '../types/space'
import type { Task, TaskList, TaskQuery, TaskSaveRequest } from '../types/tasks'

export interface TaskStats {
  inbox: number
  today: number
  overdue: number
  plan: number
  lists: Record<string, number>
}

export interface CompleteResult {
  task: Task
  /** 重复任务完成后生成的下一次 */
  next: Task | null
}

const BASE = '/space/me'

const real = {
  fetchTasks: (query: TaskQuery = {}) => get<Task[]>(`${BASE}/tasks`, { params: query }),
  fetchTask: (id: EntityId) => get<Task>(`${BASE}/tasks/${id}`),
  fetchTaskStats: () => get<TaskStats>(`${BASE}/tasks/stats`),
  createTask: (body: TaskSaveRequest & { title: string }) => post<Task>(`${BASE}/tasks`, body),
  updateTask: (id: EntityId, body: TaskSaveRequest) => put<Task>(`${BASE}/tasks/${id}`, body),
  completeTask: (id: EntityId, done: boolean) => put<CompleteResult>(`${BASE}/tasks/${id}/done`, { done }),
  deleteTask: (id: EntityId) => del<void>(`${BASE}/tasks/${id}`),
  fetchTaskLists: () => get<TaskList[]>(`${BASE}/task-lists`),
  createTaskList: (body: { name: string; color: string }) => post<TaskList>(`${BASE}/task-lists`, body),
  updateTaskList: (id: EntityId, body: { name?: string; color?: string }) => put<void>(`${BASE}/task-lists/${id}`, body),
  /** 清单删除后，其中的任务回到「无清单」 */
  deleteTaskList: (id: EntityId) => del<void>(`${BASE}/task-lists/${id}`),
}

// ── mock ──

type TaskRow = Task & { id: string }
type ListRow = TaskList & { id: string }

const lists = createMockTable<ListRow>('task-lists.v1', () => [
  { id: 'l1', name: '工作', color: '#4f46e5' },
  { id: 'l2', name: '生活', color: '#0d9488' },
  { id: 'l3', name: '学习', color: '#d97706' },
])

const blank = (): Omit<TaskRow, 'id' | 'title'> => ({
  listId: null,
  dueDate: null,
  dueTime: null,
  priority: 0,
  done: false,
  doneTime: null,
  estimateMin: null,
  remindBefore: null,
  repeat: null,
  subtasks: [],
  source: null,
  note: '',
  createTime: nowStamp(),
})

const seedTasks = (): TaskRow[] => {
  const t = todayYmd()
  const rows: (Partial<TaskRow> & { title: string })[] = [
    { title: '提交 v1.0.4 发布说明', listId: 'l1', dueDate: addDays(t, -1), dueTime: '18:00', priority: 2 },
    {
      title: '评审会前看完书签导入方案', listId: 'l1', dueDate: t, dueTime: '14:00', priority: 2, remindBefore: 30, estimateMin: 60,
      subtasks: [
        { id: 's1', title: '看 PorterServiceImpl 解析逻辑', done: true },
        { id: 's2', title: '列出失败条目的返回格式', done: false },
        { id: 's3', title: '确认大文件是否改异步', done: false },
      ],
      note: '重点看重复判定和同名目录复用，评审会上要回答「再次导入会不会重复」。',
      source: { type: 'meeting', id: 'm1', label: '产品周会' },
    },
    { title: '补齐 space 服务的前台接口权限', listId: 'l1', dueDate: t, dueTime: '16:00', priority: 3, estimateMin: 120, source: { type: 'note', id: 'n1', label: '书签前台接口拆分' } },
    { title: '读 Prompt caching 文档', listId: 'l3', dueDate: t, dueTime: '20:30', repeat: { type: 'weekly', days: [6] }, estimateMin: 45 },
    { title: '买猫粮（低敏配方）', listId: 'l2', dueDate: t, source: { type: 'note', id: 'n5', label: '猫粮换成低敏配方，找上次那个链接' } },
    { title: '和运维确认 MinIO 扩容', listId: 'l1', dueDate: addDays(t, 2), dueTime: '10:00', estimateMin: 30, source: { type: 'meeting', id: 'm1', label: '产品周会' } },
    { title: '/space/me 接口开发', listId: 'l1', dueDate: addDays(t, 2), estimateMin: 240, priority: 2 },
    { title: '周报', listId: 'l1', dueDate: addDays(t, 2), estimateMin: 30, repeat: { type: 'weekly', days: [1] } },
    { title: '数据库迁移窗口确认', listId: 'l1', dueDate: addDays(t, 3), estimateMin: 30 },
    { title: '给出前台接口清单', listId: 'l1', dueDate: nextWeekday(1), source: { type: 'meeting', id: 'm4', label: '1:1 · 张工' } },
    { title: '整理相册', listId: 'l2' },
    { title: '看看 Nuxt 4 的变化', listId: 'l3' },
    { title: '换季衣物收纳', listId: 'l2' },
    { title: '研究一下 chrono 中文日期解析' },
    // 过去两周做完的，周回顾与周报才有东西可看
    ...[
      ['space 书签 CRUD 与目录树', 'l1', 4, '17:30', 240],
      ['书签导入导出联调', 'l1', 3, '16:10', 120],
      ['scribe 实时脚本', 'l1', 2, '19:05', 90],
      ['space 前端提交代码', 'l1', 1, '21:40', 30],
      ['Chrome 书签 HTML 解析单测', 'l1', 5, '15:20', 60],
      ['修复同名目录重复创建', 'l1', 6, '11:00', 45],
      ['读《数据密集型应用系统设计》第 5 章', 'l3', 2, '22:30', 120],
      ['交物业费', 'l2', 3, '12:15', null],
      ['换空调滤网', 'l2', 5, '20:00', 30],
      ['整理 v1.0.3 发布说明', 'l1', 8, '18:00', 60],
      ['评审 porter 导入方案初稿', 'l1', 9, '15:30', 60],
      ['预约体检', 'l2', 10, '10:20', null],
    ].map(([title, listId, ago, hm, est]) => ({
      title: title as string,
      listId: listId as string,
      dueDate: addDays(t, -(ago as number)),
      done: true,
      doneTime: `${addDays(t, -(ago as number))} ${hm}:00`,
      estimateMin: est as number | null,
      createTime: `${addDays(t, -(ago as number) - 2)} 09:00:00`,
    })),
    { title: '晨跑 3 公里', listId: 'l2', dueDate: t, dueTime: '07:00', done: true, doneTime: `${t} 07:40:00`, repeat: { type: 'daily' } },
    { title: '回复 HR 邮件', listId: 'l1', dueDate: t, done: true, doneTime: `${t} 09:12:00` },
  ]
  return rows.map((row, index) => ({ ...blank(), ...row, id: `t${index + 1}` }))
}

const tasks = createMockTable<TaskRow>('tasks.v3', seedTasks)

const inView = (task: Task, view: TaskQuery['view'], today: string) => {
  switch (view) {
    case 'inbox':
      return !task.done && !task.dueDate
    case 'today':
      return !task.done && Boolean(task.dueDate) && task.dueDate! <= today
    case 'plan':
      return !task.done && Boolean(task.dueDate) && task.dueDate! >= today && task.dueDate! <= addDays(today, 6)
    case 'done':
      return task.done
    default:
      return true
  }
}

const mock: typeof real = {
  fetchTasks: (query = {}) => {
    const today = todayYmd()
    const rows = tasks
      .all()
      .filter((task) => inView(task, query.view ?? 'all', today))
      .filter((task) => query.listId === undefined || String(task.listId) === String(query.listId))
      .filter(
        (task) =>
          !query.sourceType ||
          (task.source?.type === query.sourceType && (query.sourceId === undefined || String(task.source.id) === String(query.sourceId))),
      )
      .sort((a, b) =>
        query.view === 'done'
          ? String(b.doneTime).localeCompare(String(a.doneTime))
          : String(a.dueDate ?? '9999').localeCompare(String(b.dueDate ?? '9999')) ||
            String(a.dueTime ?? '99').localeCompare(String(b.dueTime ?? '99')) ||
            b.priority - a.priority,
      )
    return delay(structuredClone(query.view === 'done' ? rows.slice(0, 200) : rows), 160)
  },

  fetchTask: async (id) => {
    const task = tasks.find(id)
    if (!task) throw new Error('任务不存在或已删除')
    return delay(structuredClone(task), 100)
  },

  fetchTaskStats: () => {
    const today = todayYmd()
    const open = tasks.all().filter((task) => !task.done)
    const byList: Record<string, number> = {}
    open.forEach((task) => {
      if (task.listId) byList[String(task.listId)] = (byList[String(task.listId)] ?? 0) + 1
    })
    return delay(
      {
        inbox: open.filter((task) => inView(task, 'inbox', today)).length,
        today: open.filter((task) => inView(task, 'today', today)).length,
        overdue: open.filter((task) => task.dueDate && task.dueDate < today).length,
        plan: open.filter((task) => inView(task, 'plan', today)).length,
        lists: byList,
      },
      100,
    )
  },

  createTask: async (body) => {
    const row = tasks.insert({ ...blank(), ...body, id: nextId(), subtasks: body.subtasks ?? [] } as TaskRow)
    return delay(structuredClone(row), 140)
  },

  updateTask: async (id, body) => {
    const row = tasks.update(id, body as Partial<TaskRow>)
    if (!row) throw new Error('任务不存在或已删除')
    return delay(structuredClone(row), 100)
  },

  completeTask: async (id, done) => {
    const task = tasks.find(id)
    if (!task) throw new Error('任务不存在或已删除')
    tasks.update(id, { done, doneTime: done ? nowStamp() : null })
    let next: TaskRow | null = null
    // 重复任务完成时生成下一次；已经生成过（撤销后再完成）就不重复生成
    if (done && task.repeat) {
      const date = nextOccurrence(task.repeat, task.dueDate ?? todayYmd())
      const exists = tasks
        .all()
        .some((t) => !t.done && t.title === task.title && t.dueDate === date && String(t.listId) === String(task.listId))
      if (!exists) {
        next = tasks.insert({
          ...structuredClone(task),
          id: nextId(),
          dueDate: date,
          done: false,
          doneTime: null,
          subtasks: task.subtasks.map((s) => ({ ...s, done: false })),
          createTime: nowStamp(),
        })
      }
    }
    return delay({ task: structuredClone(tasks.find(id)!), next: next && structuredClone(next) }, 120)
  },

  deleteTask: async (id) => {
    tasks.remove(id)
    return delay(undefined, 100)
  },

  fetchTaskLists: () => delay(structuredClone(lists.all()), 100),

  createTaskList: async (body) => {
    if (lists.all().some((list) => list.name === body.name.trim())) throw new Error('已有同名清单')
    const row = { id: nextId(), name: body.name.trim(), color: body.color }
    lists.all().push(row)
    lists.save()
    return delay(row, 120)
  },

  updateTaskList: async (id, body) => {
    lists.update(id, body)
    return delay(undefined, 100)
  },

  deleteTaskList: async (id) => {
    lists.remove(id)
    tasks.all().forEach((task) => {
      if (String(task.listId) === String(id)) task.listId = null
    })
    tasks.save()
    return delay(undefined, 100)
  },
}

const api = useMockFor('tasks') ? mock : real

export const {
  fetchTasks,
  fetchTask,
  fetchTaskStats,
  createTask,
  updateTask,
  completeTask,
  deleteTask,
  fetchTaskLists,
  createTaskList,
  updateTaskList,
  deleteTaskList,
} = api
