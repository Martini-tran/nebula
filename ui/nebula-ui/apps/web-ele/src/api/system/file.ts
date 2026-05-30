import { requestClient } from '#/api/request';

/**
 * 系统文件管理 API
 * 后端实现位于 nebula-service-manager，context-path=/manager
 */
export namespace SystemFileApi {
  /** 文件分页查询入参，对齐后端 FilePageQuery（继承 PageQuery） */
  export interface FilePageQuery {
    pageNum?: number;
    pageSize?: number;
    orderBy?: string;
    targetType?: string;
    targetId?: number | string;
    fileType?: string;
    storageType?: string;
    status?: number;
    createBy?: number | string;
    originalFilename?: string;
  }

  /** 文件元信息，对齐后端 FileInfoVO */
  export interface FileInfo {
    id: number | string;
    targetType?: string;
    targetId?: number | string;
    fileType?: string;
    storageType?: string;
    storagePlatform?: string;
    bucket?: string;
    objectKey?: string;
    /** 公开文件为永久URL，私有文件为带签名的临时URL */
    url?: string;
    originalFilename?: string;
    storedFilename?: string;
    extension?: string;
    mimeType?: string;
    sizeBytes?: number;
    width?: number;
    height?: number;
    duration?: number;
    hashSha256?: string;
    /** 1 公开 0 私有 */
    isPublic?: number;
    /** 1 正常 0 删除 2 上传中 3 上传失败 4 禁用 */
    status?: number;
    sortOrder?: number;
    createBy?: number | string;
    createTime?: string;
    updateTime?: string;
  }

  /** 上传额外参数，对齐后端 FileUploadRequest */
  export interface FileUploadParams {
    targetType?: string;
    targetId?: number | string;
    fileType?: string;
    bucket?: string;
    /** 1 公开 0 私有 */
    isPublic?: number;
    sortOrder?: number;
    prefix?: string;
  }

  /** 绑定参数，对齐后端 FileBindRequest */
  export interface FileBindParams {
    targetType: string;
    targetId: number | string;
  }

  /** 分页结果，对齐后端 PageResult<FileInfoVO> */
  export interface FilePageResult {
    records: FileInfo[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }
}

const BASE = '/manager/file';

/** 上传文件，返回文件元信息 */
export async function uploadSystemFileApi(
  file: Blob | File,
  params?: SystemFileApi.FileUploadParams,
) {
  return requestClient.upload<SystemFileApi.FileInfo>(`${BASE}/upload`, {
    file,
    ...(params ?? {}),
  });
}

/** 获取文件元信息 */
export async function getSystemFileApi(id: number | string) {
  return requestClient.get<SystemFileApi.FileInfo>(`${BASE}/${id}`);
}

/** 分页查询文件 */
export async function getSystemFilePageApi(
  params: SystemFileApi.FilePageQuery,
) {
  return requestClient.get<SystemFileApi.FilePageResult>(`${BASE}/page`, {
    params,
  });
}

/** 业务关联文件列表 */
export async function listSystemFileByTargetApi(params: {
  targetType: string;
  targetId: number | string;
  fileType?: string;
}) {
  return requestClient.get<SystemFileApi.FileInfo[]>(`${BASE}/list`, {
    params,
  });
}

/** 取访问URL：公开文件返回永久URL，私有文件返回默认有效期签名URL */
export async function getSystemFileUrlApi(id: number | string) {
  return requestClient.get<string>(`${BASE}/${id}/url`);
}

/** 取临时签名URL，可指定有效期（秒） */
export async function getSystemFilePresignedUrlApi(
  id: number | string,
  expirySeconds?: number,
) {
  return requestClient.get<string>(`${BASE}/${id}/presigned-url`, {
    params: { expirySeconds },
  });
}

/** 文件下载链接（直链，浏览器可点开） */
export function buildSystemFileDownloadUrl(id: number | string) {
  return `${BASE}/${id}/download`;
}

/** 绑定文件到业务实体 */
export async function bindSystemFileApi(
  id: number | string,
  data: SystemFileApi.FileBindParams,
) {
  return requestClient.put<void>(`${BASE}/${id}/bind`, data);
}

/** 删除文件 */
export async function deleteSystemFileApi(
  id: number | string,
  removeStorage = false,
) {
  return requestClient.delete<void>(`${BASE}/${id}`, {
    params: { removeStorage },
  });
}
