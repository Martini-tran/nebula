package com.nebula.common.file.dto;

import lombok.Data;

/**
 * 文件绑定请求参数
 * 用于将已上传的文件绑定到具体业务实体
 *
 * @author nebula
 */
@Data
public class FileBindRequest {

    /**
     * 关联业务类型
     */
    private String targetType;

    /**
     * 关联业务实体ID
     */
    private Long targetId;
}
