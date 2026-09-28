package com.nebula.space.dto.me;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 人物卡新建 / 局部保存请求
 *
 * <p>区分「没传」和「传了 null」（清空生日、关掉联系提醒时传 null），setter 记下请求里出现的字段。
 * 页面上改备忘、加承诺、记联系都只传那一个字段，数组整份覆盖。</p>
 */
@Getter
public class PersonSaveRequest {

    @JsonIgnore
    private final Set<String> present = new HashSet<>();

    @Size(max = 50, message = "姓名最长 50 字")
    private String name;

    @Size(max = 50, message = "称呼最长 50 字")
    private String alias;

    /**
     * 会议、随手记里的其他叫法；不到两个字的丢掉，避免误配
     */
    @Size(max = 20, message = "其他叫法最多 20 个")
    private List<@Size(max = 20, message = "其他叫法每个最长 20 字") String> extraNames;

    @Size(max = 20, message = "分组最长 20 字")
    private String group;

    @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "颜色格式应为 #rrggbb")
    private String color;

    /**
     * MM-DD
     */
    @Pattern(regexp = "\\d{2}-\\d{2}", message = "生日格式应为 MM-DD")
    private String birthday;

    @Min(value = 1, message = "联系提醒至少 1 天")
    @Max(value = 3650, message = "联系提醒最多 3650 天")
    private Integer contactEvery;

    @Size(max = 200, message = "介绍最长 200 字")
    private String intro;

    @Valid
    @Size(max = 50, message = "信息最多 50 条")
    private List<PersonFact> facts;

    @Size(max = 5000, message = "备忘最长 5000 字")
    private String memo;

    @Valid
    @Size(max = 1000, message = "联系记录最多 1000 条")
    private List<PersonContact> contacts;

    @Valid
    @Size(max = 500, message = "承诺最多 500 条")
    private List<PersonPromise> promises;

    /**
     * 请求里是否带了这个字段（值可以是 null）
     */
    public boolean has(String field) {
        return present.contains(field);
    }

    public void setName(String name) {
        this.name = name;
        present.add("name");
    }

    public void setAlias(String alias) {
        this.alias = alias;
        present.add("alias");
    }

    public void setExtraNames(List<String> extraNames) {
        this.extraNames = extraNames;
        present.add("extraNames");
    }

    public void setGroup(String group) {
        this.group = group;
        present.add("group");
    }

    public void setColor(String color) {
        this.color = color;
        present.add("color");
    }

    public void setBirthday(String birthday) {
        this.birthday = birthday;
        present.add("birthday");
    }

    public void setContactEvery(Integer contactEvery) {
        this.contactEvery = contactEvery;
        present.add("contactEvery");
    }

    public void setIntro(String intro) {
        this.intro = intro;
        present.add("intro");
    }

    public void setFacts(List<PersonFact> facts) {
        this.facts = facts;
        present.add("facts");
    }

    public void setMemo(String memo) {
        this.memo = memo;
        present.add("memo");
    }

    public void setContacts(List<PersonContact> contacts) {
        this.contacts = contacts;
        present.add("contacts");
    }

    public void setPromises(List<PersonPromise> promises) {
        this.promises = promises;
        present.add("promises");
    }
}
