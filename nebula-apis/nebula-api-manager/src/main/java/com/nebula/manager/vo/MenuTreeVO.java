package com.nebula.manager.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class MenuTreeVO {

    private Long id;
    private Long pid;
    private String name;
    private String type;
    private String path;
    private String component;
    private String authCode;
    private Integer status;
    private String activePath;
    private MenuMetaVO meta;
    private List<MenuTreeVO> children;
    private Integer sort;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
