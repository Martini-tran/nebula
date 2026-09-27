/**
 * textarea 里某个字符位置的像素坐标（相对 textarea 左上角，已减去滚动）。
 * 做法：建一个样式完全相同的隐藏 div，把光标前的文字放进去，量末尾标记的位置。
 */
const COPIED = [
  'boxSizing', 'width', 'paddingTop', 'paddingRight', 'paddingBottom', 'paddingLeft',
  'borderTopWidth', 'borderRightWidth', 'borderBottomWidth', 'borderLeftWidth',
  'fontFamily', 'fontSize', 'fontWeight', 'fontStyle', 'letterSpacing', 'lineHeight',
  'textTransform', 'wordSpacing', 'textIndent', 'tabSize',
] as const

export const caretCoords = (el: HTMLTextAreaElement, position: number) => {
  const div = document.createElement('div')
  const style = getComputedStyle(el)
  for (const prop of COPIED) div.style[prop] = style[prop]
  div.style.position = 'absolute'
  div.style.visibility = 'hidden'
  div.style.whiteSpace = 'pre-wrap'
  div.style.overflowWrap = 'break-word'
  div.style.top = '0'
  div.style.left = '-9999px'
  div.textContent = el.value.slice(0, position)
  const marker = document.createElement('span')
  marker.textContent = el.value.slice(position) || '.'
  div.appendChild(marker)
  document.body.appendChild(div)
  const top = marker.offsetTop - el.scrollTop
  const left = marker.offsetLeft - el.scrollLeft
  const height = parseFloat(style.lineHeight) || parseFloat(style.fontSize) * 1.5
  document.body.removeChild(div)
  return { top, left, height }
}
