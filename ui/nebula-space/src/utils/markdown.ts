/**
 * 随手记用的轻量 Markdown：标题、列表、待办、引用、代码块、分割线、行内代码 / 粗体 / 链接。
 * 先整体转义再拼标签，输出可直接 v-html；只认 http(s) 链接。
 * 待办项带 data-line（源文本行号），卡片上点复选框可以回写到原文。
 */

const escapeHtml = (text: string) =>
  text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

const URL_RE = /https?:\/\/[^\s<>"'）)】」，。]+/g

/** 显示用的短网址：去协议、去 www、截断 */
export const shortUrl = (url: string, max = 36) => {
  const text = url.replace(/^https?:\/\//, '').replace(/^www\./, '').replace(/\/$/, '')
  return text.length > max ? `${text.slice(0, max - 1)}…` : text
}

/** 已转义文本上的行内格式 */
const link = (href: string, label: string) =>
  `<a class="md-link" href="${href}" target="_blank" rel="noopener noreferrer">${label}</a>`

const inline = (escaped: string) => {
  // 行内代码与链接先换成占位符，里面的内容不再参与后面的替换
  const held: string[] = []
  const hold = (html: string) => {
    held.push(html)
    return `\u0000${held.length - 1}\u0000`
  }
  let text = escaped.replace(/`([^`]+)`/g, (_, code: string) => hold(`<code>${code}</code>`))
  text = text.replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, (_, label: string, url: string) => hold(link(url, label)))
  text = text.replace(URL_RE, (match) => {
    // 转义后的引号、尖括号不属于网址
    const tail = /&(?:quot|lt|gt);.*$/.exec(match)
    const url = tail ? match.slice(0, tail.index) : match
    return hold(link(url, escapeHtml(shortUrl(url.replace(/&amp;/g, '&'))))) + (tail?.[0] ?? '')
  })
  text = text.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  return text.replace(/\u0000(\d+)\u0000/g, (_, i: string) => held[Number(i)]!)
}

export const renderMarkdown = (source: string, options: { hideBareUrls?: boolean } = {}) => {
  const lines = source.replace(/\r\n/g, '\n').split('\n')
  const out: string[] = []
  let list: 'ul' | 'ol' | null = null
  let inCode = false
  let code: string[] = []

  const closeList = () => {
    if (list) out.push(`</${list}>`)
    list = null
  }
  const openList = (kind: 'ul' | 'ol') => {
    if (list === kind) return
    closeList()
    out.push(`<${kind}>`)
    list = kind
  }

  lines.forEach((raw, index) => {
    if (raw.trim().startsWith('```')) {
      if (inCode) {
        out.push(`<pre><code>${escapeHtml(code.join('\n'))}</code></pre>`)
        code = []
        inCode = false
      } else {
        closeList()
        inCode = true
      }
      return
    }
    if (inCode) {
      code.push(raw)
      return
    }

    // 行号仍按原文计算，跳过的行不影响待办回写
    if (options.hideBareUrls && /^\s*https?:\/\/\S+\s*$/.test(raw)) return
    const line = escapeHtml(raw)
    let m: RegExpExecArray | null
    if ((m = /^\s*[-*] \[( |x|X)\] (.*)$/.exec(line))) {
      openList('ul')
      const done = m[1] !== ' '
      out.push(
        `<li class="md-todo${done ? ' md-todo--done' : ''}"><input type="checkbox" data-line="${index}"${done ? ' checked' : ''} /><span>${inline(m[2]!)}</span></li>`,
      )
    } else if ((m = /^\s*[-*] (.*)$/.exec(line))) {
      openList('ul')
      out.push(`<li>${inline(m[1]!)}</li>`)
    } else if ((m = /^\s*\d+[.)] (.*)$/.exec(line))) {
      openList('ol')
      out.push(`<li>${inline(m[1]!)}</li>`)
    } else {
      closeList()
      if ((m = /^(#{1,3}) (.*)$/.exec(line))) out.push(`<p class="md-h md-h${m[1]!.length}">${inline(m[2]!)}</p>`)
      else if (/^\s*(-{3,}|\*{3,})\s*$/.test(line)) out.push('<hr />')
      else if ((m = /^&gt; ?(.*)$/.exec(line))) out.push(`<blockquote>${inline(m[1]!)}</blockquote>`)
      else if (line.trim()) out.push(`<p>${inline(line)}</p>`)
      else out.push('<p class="md-gap"></p>')
    }
  })
  if (inCode) out.push(`<pre><code>${escapeHtml(code.join('\n'))}</code></pre>`)
  closeList()
  return out.join('')
}

/** 切换第 line 行待办的勾选状态，返回新原文 */
export const toggleTodoLine = (source: string, line: number) => {
  const lines = source.split('\n')
  const current = lines[line]
  if (current === undefined) return source
  lines[line] = /\[( )\]/.test(current) ? current.replace('[ ]', '[x]') : current.replace(/\[(x|X)\]/, '[ ]')
  return lines.join('\n')
}

/** 正文里出现的网址（去重，保持顺序） */
export const extractUrls = (source: string) => [...new Set(source.match(URL_RE) ?? [])]

/** 第一行（去掉 Markdown 标记）作标题 */
export const firstLine = (source: string) =>
  (source.split('\n').find((line) => line.trim()) ?? '')
    .replace(/^#{1,3} /, '')
    .replace(/^\s*[-*] (\[[ xX]\] )?/, '')
    .trim()

/** 纯文本摘要，用于列表与搜索高亮 */
export const plainText = (source: string) =>
  source
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/^#{1,3} /gm, '')
    .replace(/^\s*[-*] (\[[ xX]\] )?/gm, '')
    .replace(/\*\*|`/g, '')
    .replace(/\s+/g, ' ')
    .trim()
