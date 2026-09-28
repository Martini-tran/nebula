package com.nebula.space.vo.me;

import com.nebula.space.dto.me.PersonContact;
import com.nebula.space.dto.me.PersonFact;
import com.nebula.space.dto.me.PersonPromise;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 人物卡视图对象
 */
@Data
public class PersonVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private String alias;

    private List<String> extraNames;

    private String group;

    private String color;

    /**
     * MM-DD
     */
    private String birthday;

    /**
     * 多少天没联系就提醒；null 不提醒
     */
    private Integer contactEvery;

    private String intro;

    private List<PersonFact> facts;

    private String memo;

    private List<PersonContact> contacts;

    private List<PersonPromise> promises;

    private LocalDateTime createTime;
}
