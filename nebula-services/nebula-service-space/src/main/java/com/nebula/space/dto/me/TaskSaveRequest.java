package com.nebula.space.dto.me;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 任务新建 / 局部保存请求
 *
 * <p>局部保存要区分「没传」和「传了 null」（如 dueDate: null 表示放回收件箱），
 * Jackson 只对请求里出现的字段调用 setter，setter 顺手记下字段名。</p>
 */
@Getter
public class TaskSaveRequest {

    @JsonIgnore
    private final Set<String> present = new HashSet<>();

    @Size(max = 200, message = "标题最长 200 字")
    private String title;

    private Long listId;

    private LocalDate dueDate;

    @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d", message = "时间格式应为 HH:mm")
    private String dueTime;

    @Min(0)
    @Max(3)
    private Integer priority;

    @Min(1)
    @Max(24 * 60)
    private Integer estimateMin;

    @Min(0)
    @Max(7 * 24 * 60)
    private Integer remindBefore;

    @Valid
    private RepeatRule repeat;

    @Valid
    @Size(max = 100, message = "子任务最多 100 个")
    private List<SubTask> subtasks;

    @Valid
    private TaskSource source;

    @Size(max = 20000, message = "备注过长")
    private String note;

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

    public void setListId(Long listId) {
        this.listId = listId;
        present.add("listId");
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
        present.add("dueDate");
    }

    public void setDueTime(String dueTime) {
        this.dueTime = dueTime;
        present.add("dueTime");
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
        present.add("priority");
    }

    public void setEstimateMin(Integer estimateMin) {
        this.estimateMin = estimateMin;
        present.add("estimateMin");
    }

    public void setRemindBefore(Integer remindBefore) {
        this.remindBefore = remindBefore;
        present.add("remindBefore");
    }

    public void setRepeat(RepeatRule repeat) {
        this.repeat = repeat;
        present.add("repeat");
    }

    public void setSubtasks(List<SubTask> subtasks) {
        this.subtasks = subtasks;
        present.add("subtasks");
    }

    public void setSource(TaskSource source) {
        this.source = source;
        present.add("source");
    }

    public void setNote(String note) {
        this.note = note;
        present.add("note");
    }
}
