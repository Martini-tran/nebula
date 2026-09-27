/** 标签色板：新建时轮流取色免去挑选，整理页的调色板也用这 12 色 */
export const TAG_COLORS = [
  '#4f46e5',
  '#0d9488',
  '#d97706',
  '#db2777',
  '#2563eb',
  '#65a30d',
  '#7c3aed',
  '#dc2626',
  '#0891b2',
  '#ca8a04',
  '#9333ea',
  '#64748b',
]

export const nextTagColor = (count: number) => TAG_COLORS[count % TAG_COLORS.length]!
