import type { SpaceTagApi } from './tag';

import { requestClient } from '#/api/request';

export namespace SpaceBookmarkApi {
  export interface BookmarkTagRaw {
    id: number | string;
    name: string;
    color?: string;
  }

  export interface BookmarkItemRaw {
    id: number | string;
    user_id?: number | string;
    folder_id?: number | string;
    title: string;
    url: string;
    normalized_url?: string;
    url_hash?: string;
    domain?: string;
    description?: string;
    favicon_url?: string;
    favicon_file_id?: number | string;
    source?: string;
    source_key?: string;
    status?: number;
    visit_count?: number;
    last_visit_time?: string;
    sort_order?: number;
    remark?: string;
    tags?: BookmarkTagRaw[];
    create_time?: string;
    update_time?: string;
  }

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

function normalizeTag(raw: SpaceBookmarkApi.BookmarkTagRaw): SpaceTagApi.TagItem {
  return {
    id: raw.id,
    name: raw.name,
    color: raw.color,
  };
}

function normalize(
  raw: SpaceBookmarkApi.BookmarkItemRaw,
): SpaceBookmarkApi.BookmarkItem {
  return {
    id: raw.id,
    userId: raw.user_id,
    folderId: raw.folder_id,
    title: raw.title,
    url: raw.url,
    normalizedUrl: raw.normalized_url,
    urlHash: raw.url_hash,
    domain: raw.domain,
    description: raw.description,
    faviconUrl: raw.favicon_url,
    faviconFileId: raw.favicon_file_id,
    source: raw.source,
    sourceKey: raw.source_key,
    status: raw.status,
    visitCount: raw.visit_count,
    lastVisitTime: raw.last_visit_time,
    sortOrder: raw.sort_order,
    remark: raw.remark,
    tags: raw.tags?.map(normalizeTag) ?? [],
    createTime: raw.create_time,
    updateTime: raw.update_time,
  };
}

function serializeCreate(data: SpaceBookmarkApi.BookmarkCreateParams) {
  return {
    title: data.title,
    url: data.url,
    folder_id: data.folderId,
    description: data.description,
    favicon_url: data.faviconUrl,
    favicon_file_id: data.faviconFileId,
    source: data.source,
    source_key: data.sourceKey,
    sort_order: data.sortOrder,
    remark: data.remark,
    tag_ids: data.tagIds,
  };
}

function serializeUpdate(data: SpaceBookmarkApi.BookmarkUpdateParams) {
  const payload: Record<string, unknown> = {};
  if ('title' in data) payload.title = data.title;
  if ('url' in data) payload.url = data.url;
  if ('folderId' in data) payload.folder_id = data.folderId;
  if ('description' in data) payload.description = data.description;
  if ('faviconUrl' in data) payload.favicon_url = data.faviconUrl;
  if ('faviconFileId' in data) payload.favicon_file_id = data.faviconFileId;
  if ('sortOrder' in data) payload.sort_order = data.sortOrder;
  if ('remark' in data) payload.remark = data.remark;
  if ('tagIds' in data) payload.tag_ids = data.tagIds;
  return payload;
}

export async function getSpaceBookmarkPageApi(
  params: SpaceBookmarkApi.BookmarkPageQuery,
) {
  const result = await requestClient.get<{
    records: SpaceBookmarkApi.BookmarkItemRaw[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }>('/space/admin/bookmarks/page', {
    params: {
      pageNum: params.pageNum,
      pageSize: params.pageSize,
      keyword: params.keyword,
      userId: params.userId,
      folderId: params.folderId,
      tagId: params.tagId,
      status: params.status,
      source: params.source,
      domain: params.domain,
    },
  });

  return {
    ...result,
    records: (result.records ?? []).map(normalize),
  } as SpaceBookmarkApi.BookmarkPageResult;
}

export async function getSpaceBookmarkDetailApi(id: number | string) {
  const raw = await requestClient.get<SpaceBookmarkApi.BookmarkItemRaw>(
    `/space/admin/bookmarks/${id}`,
  );
  return normalize(raw);
}

export async function createSpaceBookmarkApi(
  data: SpaceBookmarkApi.BookmarkCreateParams,
) {
  return requestClient.post<number | string>(
    '/space/admin/bookmarks',
    serializeCreate(data),
  );
}

export async function updateSpaceBookmarkApi(
  id: number | string,
  data: SpaceBookmarkApi.BookmarkUpdateParams,
) {
  return requestClient.put<void>(
    `/space/admin/bookmarks/${id}`,
    serializeUpdate(data),
  );
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
    tag_ids: tagIds,
  });
}

export async function moveSpaceBookmarksApi(
  bookmarkIds: Array<number | string>,
  targetFolderId: number | string,
) {
  return requestClient.post<number>('/space/admin/bookmarks/move', {
    bookmark_ids: bookmarkIds,
    target_folder_id: targetFolderId,
  });
}

export async function batchDeleteSpaceBookmarksApi(
  bookmarkIds: Array<number | string>,
) {
  return requestClient.post<number>('/space/admin/bookmarks/batch-delete', {
    bookmark_ids: bookmarkIds,
  });
}

export async function deleteSpaceBookmarkApi(id: number | string) {
  return requestClient.delete<void>(`/space/admin/bookmarks/${id}`);
}
