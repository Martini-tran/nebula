package com.nebula.common.file.enums;

import com.nebula.common.core.enums.IResultCode;

/**
 * 文件模块结果码枚举
 *
 * @author nebula
 */
public enum FileResultCode implements IResultCode {

    /** 文件不存在 */
    FILE_NOT_FOUND(40501, "文件不存在"),
    /** 文件已被删除 */
    FILE_DELETED(40502, "文件已被删除"),
    /** 上传文件不能为空 */
    FILE_EMPTY(40503, "上传文件不能为空"),
    /** 文件大小超出限制 */
    FILE_SIZE_EXCEEDED(40504, "文件大小超出限制"),
    /** 文件类型不允许 */
    FILE_TYPE_NOT_ALLOWED(40505, "文件类型不允许"),
    /** 文件上传失败 */
    FILE_UPLOAD_FAILED(40506, "文件上传失败"),
    /** 文件下载失败 */
    FILE_DOWNLOAD_FAILED(40507, "文件下载失败"),
    /** 临时URL生成失败 */
    PRESIGNED_URL_FAILED(40508, "临时URL生成失败"),
    /** 文件参数不合法 */
    FILE_PARAM_INVALID(40509, "文件参数不合法");

    private final int code;
    private final String message;

    FileResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
