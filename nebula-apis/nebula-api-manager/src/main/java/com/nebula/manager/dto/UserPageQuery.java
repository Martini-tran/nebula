package com.nebula.manager.dto;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserPageQuery extends PageQuery {

    private String username;
    private String nickname;
    private String mobile;
    private String email;
    private Integer status;
    private String createTimeStart;
    private String createTimeEnd;
}
