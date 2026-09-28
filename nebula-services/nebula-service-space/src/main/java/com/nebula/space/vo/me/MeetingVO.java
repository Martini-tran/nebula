package com.nebula.space.vo.me;

import com.nebula.space.dto.me.MeetingAgendaItem;
import com.nebula.space.dto.me.MeetingAttendee;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 会议视图对象
 */
@Data
public class MeetingVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String title;

    private LocalDate date;

    /**
     * HH:mm
     */
    private String startTime;

    private Integer durationMin;

    private String template;

    private List<MeetingAttendee> attendees;

    private List<MeetingAgendaItem> agenda;

    private String currentAgendaId;

    private String content;

    private String status;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    private Map<String, Object> syncedTasks;

    private LocalDateTime createTime;
}
