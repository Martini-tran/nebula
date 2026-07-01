import type { SpaceTagApi } from './tag';

import { requestClient } from '#/api/request';

/**
 * 空间-书签 API
 * space 服务 Jackson 已回归默认 camelCase（移除了 SNAKE_CASE 配置），出入参均为 camelCase，本层直接透传。
 */
export namespace SpaceBookmarkApi {
  export interface BookmarkItem {
    id: number | string;
    userId?: number | string;
    folderId?: number | string;
    title: string;
    url: string;
    normalizedUrl?: string;
    urlHash?: string;
    domain?: string;
    description?: string;
    faviconUrl?: string;
    faviconFileId?: number | string;
    source?: string;
    sourceKey?: string;
    status?: number;
    visitCount?: number;
    lastVisitTime?: string;
    sortOrder?: number;
    remark?: string;
    tags?: SpaceTagApi.TagItem[];
    createTime?: string;
    updateTime?: string;
  }

  export interface BookmarkPageQuery {
    pageNum?: number;
    pageSize?: number;
    keyword?: string;
    userId?: number | string;
    folderId?: number | string;
    tagId?: number | string;
    status?: number;
    source?: string;
    domain?: string;
  }

  export interface BookmarkCreateParams {
    title: string;
    url: string;
    folderId?: number | string;
    description?: string;
    faviconUrl?: string;
    faviconFileId?: number | string;
    source?: string;
    sourceKey?: string;
    sortOrder?: number;
    remark?: string;
    tagIds?: Array<number | string>;
  }

  export interface BookmarkUpdateParams {
    title?: string;
    url?: string;
    folderId?: number | string;
    description?: string;
    faviconUrl?: string;
    faviconFileId?: number | string;
    sortOrder?: number;
    remark?: string;
    tagIds?: Array<number | string> | null;
  }

  export interface BookmarkPageResult {
    records: BookmarkItem[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }
}

export async function getSpaceBookmarkPageApi(
  params: SpaceBookmarkApi.BookmarkPageQuery,
) {
  return requestClient.get<SpaceBookmarkApi.BookmarkPageResult>(
    '/space/admin/bookmarks/page',
    { params },
  );
}

export async function getSpaceBookmarkDetailApi(id: number | string) {
  return requestClient.get<SpaceBookmarkApi.BookmarkItem>(
    `/space/admin/bookmarks/${id}`,
  );
}

export async function createSpaceBookmarkApi(
  data: SpaceBookmarkApi.BookmarkCreateParams,
) {
  return requestClient.post<number | string>('/space/admin/bookmarks', data);
}

export async function updateSpaceBookmarkApi(
  id: number | string,
  data: SpaceBookmarkApi.BookmarkUpdateParams,
) {
  return requestClient.put<void>(`/space/admin/bookmarks/${id}`, data);
}

export async function updateSpaceBookmarkStatusApi(
  id: number | string,
  status: number,
) {
  return requestClient.put<void>(`/space/admin/bookmarks/${id}/status`, {
    status,
  });
}

export async function bindSpaceBookmarkTagsApi(
  id: number | string,
  tagIds: Array<number | string>,
) {
  return requestClient.put<void>(`/space/admin/bookmarks/${id}/tags`, {
    tagIds,
  });
}

export async function moveSpaceBookmarksApi(
  bookmarkIds: Array<number | string>,
  targetFolderId: number | string,
) {
  return requestClient.post<number>('/space/admin/bookmarks/move', {
    bookmarkIds,
    targetFolderId,
  });
}

export async function batchDeleteSpaceBookmarksApi(
  bookmarkIds: Array<number | string>,
) {
  return requestClient.post<number>('/space/admin/bookmarks/batch-delete', {
    bookmarkIds,
  });
}

export async function deleteSpaceBookmarkApi(id: number | string) {
  return requestClient.delete<void>(`/space/admin/bookmarks/${id}`);
}
