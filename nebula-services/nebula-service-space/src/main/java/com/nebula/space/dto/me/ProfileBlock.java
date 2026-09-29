package com.nebula.space.dto.me;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 公开主页的一个区块：顺序即列表顺序，on 为是否公开（存在 JSON 列里）
 */
@Data
public class ProfileBlock implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * intro / links / now / collections / reading / goals / quotes
     */
    private String key;

    private boolean on;

    public static ProfileBlock of(String key, boolean on) {
        ProfileBlock b = new ProfileBlock();
        b.setKey(key);
        b.setOn(on);
        return b;
    }
}
