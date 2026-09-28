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
import java.util.Map;
import java.util.Set;

/**
 * 会议新建 / 局部保存请求
 *
 * <p>与任务一样区分「没传」和「传了 null」（如结束会议时 currentAgendaId: null），
 * setter 记下请求里出现的字段。startedAt / endedAt 前端传「yyyy-MM-dd HH:mm:ss」，按字符串收、服务端解析。</p>
 */
@Getter
public class MeetingSaveRequest {

    @JsonIgnore
    private final Set<String> present = new HashSet<>();

    @Size(max = 200, message = "标题最长 200 字")
    private String title;

    private LocalDate date;

    @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d", message = "时间格式应为 HH:mm")
    private String startTime;

    @Min(5)
    @Max(24 * 60)
    private Integer durationMin;

    @Size(max = 32)
    private String template;

    @Valid
    @Size(max = 100, message = "参会人最多 100 位")
    private List<MeetingAttendee> attendees;

    @Valid
    @Size(max = 50, message = "议程最多 50 项")
    private List<MeetingAgendaItem> agenda;

    @Size(max = 32)
    private String currentAgendaId;

    @Size(max = 200000, message = "正文过长")
    private String content;

    @Pattern(regexp = "planned|live|done", message = "不支持的会议状态")
    private String status;

    private String startedAt;

    private String endedAt;

    /**
     * 已同步进任务的待办：待办文本 → 任务ID
     */
    private Map<String, Object> syncedTasks;

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

    public void setDate(LocalDate date) {
        this.date = date;
        present.add("date");
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
        present.add("startTime");
    }

    public void setDurationMin(Integer durationMin) {
        this.durationMin = durationMin;
        present.add("durationMin");
    }

    public void setTemplate(String template) {
        this.template = template;
        present.add("template");
    }

    public void setAttendees(List<MeetingAttendee> attendees) {
        this.attendees = attendees;
        present.add("attendees");
    }

    public void setAgenda(List<MeetingAgendaItem> agenda) {
        this.agenda = agenda;
        present.add("agenda");
    }

    public void setCurrentAgendaId(String currentAgendaId) {
        this.currentAgendaId = currentAgendaId;
        present.add("currentAgendaId");
    }

    public void setContent(String content) {
        this.content = content;
        present.add("content");
    }

    public void setStatus(String status) {
        this.status = status;
        present.add("status");
    }

    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
        present.add("startedAt");
    }

    public void setEndedAt(String endedAt) {
        this.endedAt = endedAt;
        present.add("endedAt");
    }

    public void setSyncedTasks(Map<String, Object> syncedTasks) {
        this.syncedTasks = syncedTasks;
        present.add("syncedTasks");
    }
}
