package com.nebula.space.service.impl;

import com.nebula.space.dto.me.FileQuery;
import com.nebula.space.dto.me.FocusSessionQuery;
import com.nebula.space.dto.me.HabitLogQuery;
import com.nebula.space.dto.me.HighlightQuery;
import com.nebula.space.dto.me.LedgerEntryQuery;
import com.nebula.space.dto.me.MeetingQuery;
import com.nebula.space.dto.me.NoteQuery;
import com.nebula.space.dto.me.ReadingQuery;
import com.nebula.space.dto.me.ReportQuery;
import com.nebula.space.dto.me.TaskQuery;
import com.nebula.space.service.SpaceAnniversaryService;
import com.nebula.space.service.SpaceExportService;
import com.nebula.space.service.SpaceFileService;
import com.nebula.space.service.SpaceFocusService;
import com.nebula.space.service.SpaceGoalService;
import com.nebula.space.service.SpaceHabitService;
import com.nebula.space.service.SpaceLedgerRecurringService;
import com.nebula.space.service.SpaceLedgerService;
import com.nebula.space.service.SpaceMeetingService;
import com.nebula.space.service.SpaceNoteService;
import com.nebula.space.service.SpacePersonService;
import com.nebula.space.service.SpaceProfileService;
import com.nebula.space.service.SpaceReadingService;
import com.nebula.space.service.SpaceReportService;
import com.nebula.space.service.SpaceSettingService;
import com.nebula.space.service.SpaceShareService;
import com.nebula.space.service.SpaceTaskListService;
import com.nebula.space.service.SpaceTaskService;
import com.nebula.space.vo.me.LedgerEntryVO;
import com.nebula.space.vo.me.NoteVO;
import com.nebula.space.vo.me.PersonVO;
import com.nebula.space.vo.me.ReadingItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 导出全部数据：逐个模块调用列表查询，拼成一份
 *
 * <p>任何一个模块取不到就整体失败，不导出缺了一块的文件（前端拼的时候会把取失败的模块当成空的）。</p>
 */
@Service
@RequiredArgsConstructor
public class SpaceExportServiceImpl implements SpaceExportService {

    static final String APP = "nebula-space";
    static final int FORMAT_VERSION = 1;

    private final SpaceSettingService settingService;
    private final SpaceTaskService taskService;
    private final SpaceTaskListService taskListService;
    private final SpaceNoteService noteService;
    private final SpaceMeetingService meetingService;
    private final SpaceHabitService habitService;
    private final SpaceFocusService focusService;
    private final SpaceReportService reportService;
    private final SpaceReadingService readingService;
    private final SpaceLedgerService ledgerService;
    private final SpaceLedgerRecurringService recurringService;
    private final SpaceGoalService goalService;
    private final SpaceAnniversaryService anniversaryService;
    private final SpacePersonService personService;
    private final SpaceFileService fileService;
    private final SpaceShareService shareService;
    private final SpaceProfileService profileService;

    @Override
    public Map<String, Object> snapshot() {
        TaskQuery allTasks = new TaskQuery();
        allTasks.setView("all");
        NoteQuery liveNotes = new NoteQuery();
        liveNotes.setView("all");
        NoteQuery archivedNotes = new NoteQuery();
        archivedNotes.setView("archived");
        ReadingQuery liveReading = new ReadingQuery();
        liveReading.setArchived(false);
        ReadingQuery archivedReading = new ReadingQuery();
        archivedReading.setArchived(true);
        FileQuery allFiles = new FileQuery();
        allFiles.setView("all");

        List<?> tasks = taskService.list(allTasks);
        List<?> taskLists = taskListService.list();
        List<NoteVO> notes = concat(noteService.list(liveNotes), noteService.list(archivedNotes));
        List<?> meetings = meetingService.list(new MeetingQuery());
        List<?> habits = habitService.list(true);
        List<?> habitLogs = habitService.logs(new HabitLogQuery());
        List<?> focusSessions = focusService.list(new FocusSessionQuery());
        List<?> reports = reportService.list(new ReportQuery());
        List<ReadingItemVO> reading = concat(readingService.list(liveReading), readingService.list(archivedReading));
        List<?> highlights = readingService.listHighlights(new HighlightQuery());
        List<LedgerEntryVO> entries = ledgerService.listEntries(new LedgerEntryQuery());
        List<?> goals = goalService.listAll();
        List<?> anniversaries = anniversaryService.list();
        List<PersonVO> people = personService.list();
        List<?> files = fileService.list(allFiles);

        Map<String, Object> ledger = new LinkedHashMap<>();
        ledger.put("categories", ledgerService.listCategories());
        ledger.put("entries", entries);
        ledger.put("recurring", recurringService.list());
        ledger.put("budgets", ledgerService.listBudgets());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("app", APP);
        out.put("version", FORMAT_VERSION);
        out.put("exportedAt", LocalDateTime.now().withNano(0));
        out.put("settings", settingService.get());
        out.put("tasks", tasks);
        out.put("taskLists", taskLists);
        out.put("notes", notes);
        out.put("meetings", meetings);
        out.put("habits", habits);
        out.put("habitLogs", habitLogs);
        out.put("focusSessions", focusSessions);
        out.put("reports", reports);
        out.put("reading", reading);
        out.put("highlights", highlights);
        out.put("ledger", ledger);
        out.put("goals", goals);
        out.put("anniversaries", anniversaries);
        // 文件柜只导出文件信息，文件本身请在文件柜里下载
        out.put("files", files);
        out.put("shares", shareService.list());
        out.put("publicProfile", profileService.get());
        // 人物卡单独列出，方便自行决定是否保留在导出文件里
        out.put("privatePeople", people);

        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("tasks", tasks.size());
        counts.put("taskLists", taskLists.size());
        counts.put("notes", notes.size());
        counts.put("meetings", meetings.size());
        counts.put("habits", habits.size());
        counts.put("habitLogs", habitLogs.size());
        counts.put("focusSessions", focusSessions.size());
        counts.put("reports", reports.size());
        counts.put("reading", reading.size());
        counts.put("highlights", highlights.size());
        counts.put("ledgerEntries", entries.size());
        counts.put("goals", goals.size());
        counts.put("anniversaries", anniversaries.size());
        counts.put("people", people.size());
        counts.put("files", files.size());
        out.put("counts", counts);
        out.put("total", counts.values().stream().mapToInt(Integer::intValue).sum());
        return out;
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        return Stream.concat(a.stream(), b.stream()).toList();
    }
}
