package com.nebula.system.vo;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户详情
 * 不返回 password / deleted / delete_time
 *
 * @author nebula
 */
@Data
public class UserDetailVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String mobile;
    private String email;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
