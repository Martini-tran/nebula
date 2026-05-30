import { requestClient } from '#/api/request';

export namespace BlogTagApi {
  export interface TagItem {
    id: number | string;
    name: string;
    slug: string;
    useCount?: number;
    createTime?: string;
    updateTime?: string;
  }

  export interface TagCreateParams {
    name: string;
    slug: string;
  }

  export type TagUpdateParams = Partial<TagCreateParams>;
}

export async function getBlogTagListApi() {
  return requestClient.get<BlogTagApi.TagItem[]>('/blog/admin/tags');
}

export async function getBlogTagDetailApi(id: number | string) {
  return requestClient.get<BlogTagApi.TagItem>(`/blog/admin/tags/${id}`);
}

export async function createBlogTagApi(data: BlogTagApi.TagCreateParams) {
  return requestClient.post<number | string>('/blog/admin/tags', data);
}

export async function updateBlogTagApi(
  id: number | string,
  data: BlogTagApi.TagUpdateParams,
) {
  return requestClient.put<void>(`/blog/admin/tags/${id}`, data);
}

export async function deleteBlogTagApi(id: number | string) {
  return requestClient.delete<void>(`/blog/admin/tags/${id}`);
}
