package com.nebula.space.util;

import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.exception.BizException;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * 前端时间戳解析：前端 nowStamp() 给的是「yyyy-MM-dd HH:mm:ss」，也兼容 ISO 的「T」写法
 */
public final class Stamps {

    private Stamps() {
    }

    /**
     * 空串视为 null；格式不对抛 400
     */
    public static LocalDateTime parse(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim().replace(' ', 'T'));
        } catch (DateTimeParseException e) {
            throw new BizException(HttpStatus.BAD_REQUEST, "时间格式不正确：" + value);
        }
    }
}
