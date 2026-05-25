import { requestClient } from '#/api/request';

export namespace BlogFileApi {
  export interface FileUploadResult {
    /** 文件资产 ID（对应 blog_file_asset.id） */
    id: number | string;
    /** OSS 访问 URL */
    url: string;
    /** 原始文件名 */
    filename?: string;
    /** 文件用途：image / cover / attachment / other */
    fileType?: string;
  }
}

/**
 * 上传博客文件（图片、封面等）到 OSS
 *
 * @param file     要上传的文件对象
 * @param fileType 文件用途，默认 'image'；封面传 'cover'
 */
export async function uploadBlogFileApi(
  file: File,
  fileType: string = 'image',
): Promise<BlogFileApi.FileUploadResult> {
  return requestClient.upload<BlogFileApi.FileUploadResult>(
    '/blog/admin/files/upload',
    { file, fileType },
  );
}
