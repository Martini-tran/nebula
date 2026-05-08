package com.nebula.manager.dto;

import com.nebula.manager.vo.MenuMetaVO;
import lombok.Data;

@Data
public class MenuCreateRequest {

    private Long pid;
    private String name;
    private String type;
    private String path;
    private String component;
    private String authCode;
    private Integer status;
    private String activePath;
    private Integer sort;
    private String remark;
    private MenuMetaVO meta;
}
