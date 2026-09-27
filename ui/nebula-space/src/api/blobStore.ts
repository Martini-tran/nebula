/**
 * mock 用的文件内容存储：浏览器 IndexedDB，按键存 Blob。只在后端还没接通时用，
 * 让上传、预览、下载在本机能走通；清浏览器数据就没了。
 */
const DB = 'nebula-space-files'
const STORE = 'blobs'

let opening: Promise<IDBDatabase> | null = null
const db = () =>
  (opening ??= new Promise((resolve, reject) => {
    const req = indexedDB.open(DB, 1)
    req.onupgradeneeded = () => req.result.createObjectStore(STORE)
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  }))

const run = async <T>(mode: IDBTransactionMode, fn: (store: IDBObjectStore) => IDBRequest<T>) => {
  const conn = await db()
  return new Promise<T>((resolve, reject) => {
    const req = fn(conn.transaction(STORE, mode).objectStore(STORE))
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
}

export const putBlob = (key: string, blob: Blob) => run('readwrite', (s) => s.put(blob, key))
export const getBlob = (key: string) => run<Blob | undefined>('readonly', (s) => s.get(key))
export const deleteBlob = (key: string) => run('readwrite', (s) => s.delete(key))
