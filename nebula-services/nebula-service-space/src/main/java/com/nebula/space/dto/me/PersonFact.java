package com.nebula.space.dto.me;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 人物卡上手填的一条信息：团队、偏好、家庭……（存在 JSON 列里）
 */
@Data
public class PersonFact implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Size(max = 20, message = "信息名称最长 20 字")
    private String label;

    @Size(max = 500, message = "信息内容最长 500 字")
    private String value;
}
