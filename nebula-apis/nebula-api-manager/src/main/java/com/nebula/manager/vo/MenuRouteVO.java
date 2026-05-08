package com.nebula.manager.vo;

import java.util.List;
import lombok.Data;

@Data
public class MenuRouteVO {

    private String name;
    private String path;
    private String component;
    private MenuMetaVO meta;
    private List<MenuRouteVO> children;
}
