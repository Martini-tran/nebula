package com.nebula.space.dto.me;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * 纪念日新建 / 局部保存请求
 *
 * <p>区分「没传」和「传了 null」（不提醒是 remindDays: null，改日期后 taskFor: null 作废已生成的任务标记），
 * setter 记下请求里出现的字段。</p>
 */
@Getter
public class AnniversarySaveRequest {

    @JsonIgnore
    private final Set<String> present = new HashSet<>();

    @Size(max = 60, message = "名称最长 60 字")
    private String title;

    /**
     * Iconify 名称（集合:名字），如 lucide:target
     */
    @Size(max = 64)
    @Pattern(regexp = "[a-z0-9-]+:[a-z0-9-]+", message = "图标应为 Iconify 名称，如 lucide:target")
    private String icon;

    @Pattern(regexp = "countdown|annual|countup", message = "不支持的纪念日类型")
    private String type;

    private LocalDate date;

    @Pattern(regexp = "solar|lunar", message = "不支持的历法")
    private String calendar;

    @Min(1)
    @Max(12)
    private Integer lunarMonth;

    @Min(1)
    @Max(30)
    private Integer lunarDay;

    @Min(0)
    @Max(366)
    private Integer remindDays;

    private Boolean createTask;

    @Size(max = 100, message = "任务标题最长 100 字")
    private String taskTitle;

    private LocalDate taskFor;

    @Size(max = 64)
    private String fileId;

    @Size(max = 200, message = "备注最长 200 字")
    private String note;

    @Size(max = 32, message = "标签最长 32 字")
    private String tag;

    /**
     * 请求里是否带了这个字段（值可以是 null）
     */
    public boolean has(String field) {
        return present.contains(field);
    }

    public void setTitle(String title) {
        this.title = title;
        present.add("title");
    }

    public void setIcon(String icon) {
        this.icon = icon;
        present.add("icon");
    }

    public void setType(String type) {
        this.type = type;
        present.add("type");
    }

    public void setDate(LocalDate date) {
        this.date = date;
        present.add("date");
    }

    public void setCalendar(String calendar) {
        this.calendar = calendar;
        present.add("calendar");
    }

    public void setLunarMonth(Integer lunarMonth) {
        this.lunarMonth = lunarMonth;
        present.add("lunarMonth");
    }

    public void setLunarDay(Integer lunarDay) {
        this.lunarDay = lunarDay;
        present.add("lunarDay");
    }

    public void setRemindDays(Integer remindDays) {
        this.remindDays = remindDays;
        present.add("remindDays");
    }

    public void setCreateTask(Boolean createTask) {
        this.createTask = createTask;
        present.add("createTask");
    }

    public void setTaskTitle(String taskTitle) {
        this.taskTitle = taskTitle;
        present.add("taskTitle");
    }

    public void setTaskFor(LocalDate taskFor) {
        this.taskFor = taskFor;
        present.add("taskFor");
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
        present.add("fileId");
    }

    public void setNote(String note) {
        this.note = note;
        present.add("note");
    }

    public void setTag(String tag) {
        this.tag = tag;
        present.add("tag");
    }
}
