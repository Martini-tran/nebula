/**
 * 把公开主页上的一个书签合集导入到自己的书签：新建同名目录（重名时加「（来自 @某人）」），
 * 逐条收藏；已经收藏过的网址跳过（后端对重复网址会覆盖标签，所以先查重）。
 */
import { createBookmark, createFolder, findDuplicate } from '../../api/space'
import type { PublicPage } from '../../types/profile'

export const importCollection = async (page: PublicPage, collectionId: string) => {
  const c = page.collections.find((x) => x.id === collectionId)
  if (!c) throw new Error('合集不存在')
  let folderId
  try {
    folderId = await createFolder({ name: c.title })
  } catch {
    folderId = await createFolder({ name: `${c.title}（来自 @${page.handle}）` })
  }
  let added = 0
  let skipped = 0
  for (const b of c.bookmarks) {
    if (await findDuplicate(b.url)) {
      skipped += 1
      continue
    }
    await createBookmark({ url: b.url, title: b.title, folderId })
    added += 1
  }
  return { added, skipped, title: c.title }
}
