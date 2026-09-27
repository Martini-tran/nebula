/** 日视图、周视图共用：时间换算，以及把重叠的时间块分列并排。 */

export const toMin = (hm: string) => {
  const [h, m] = hm.split(':').map(Number)
  return (h ?? 0) * 60 + (m ?? 0)
}

export const toHm = (min: number) => `${String(Math.floor(min / 60)).padStart(2, '0')}:${String(min % 60).padStart(2, '0')}`

export const dur = (min: number) => {
  const h = Math.floor(min / 60)
  const m = min % 60
  return h ? `${h}h${m ? ` ${m}m` : ''}` : `${m}m`
}

/** 重叠的块分列并排：lane 是第几列，lanes 是这一簇一共几列 */
export const layLanes = <T extends { start: number; end: number }>(blocks: T[]) => {
  const sorted = [...blocks].sort((a, b) => a.start - b.start || b.end - a.end)
  const out: (T & { lane: number; lanes: number })[] = []
  let cluster: (T & { lane: number; lanes: number })[] = []
  let clusterEnd = -1
  const flush = () => {
    const lanes = Math.max(1, ...cluster.map((b) => b.lane + 1))
    cluster.forEach((b) => (b.lanes = lanes))
    out.push(...cluster)
    cluster = []
  }
  for (const b of sorted) {
    if (b.start >= clusterEnd && cluster.length) flush()
    const used = new Set(cluster.filter((c) => c.end > b.start).map((c) => c.lane))
    let lane = 0
    while (used.has(lane)) lane += 1
    cluster.push({ ...b, lane, lanes: 1 })
    clusterEnd = Math.max(clusterEnd, b.end)
  }
  if (cluster.length) flush()
  return out
}
