import { createBookmark, findDuplicate, hostOf } from '../api/space'
import { errorText, toast } from './useToast'

/** 把随手记、任务里出现的网址存进书签（未分类）；已收藏过就只提示 */
export const saveLinkAsBookmark = async (url: string, title?: string) => {
  try {
    const existing = await findDuplicate(url)
    if (existing) {
      toast.info(`书签里已经有了：「${existing.title}」`)
      return
    }
    await createBookmark({ url, title: title?.trim() || hostOf(url).replace(/^www\./, ''), folderId: 0 })
    toast.ok('已存进书签 · 未分类')
  } catch (error) {
    toast.error(errorText(error, '存书签失败'))
  }
}
