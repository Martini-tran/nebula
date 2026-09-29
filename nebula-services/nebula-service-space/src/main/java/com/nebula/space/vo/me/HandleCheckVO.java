package com.nebula.space.vo.me;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 公开主页短名是否可用
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HandleCheckVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private boolean available;

    /**
     * 不可用的原因；可用为空
     */
    private String reason;
}
