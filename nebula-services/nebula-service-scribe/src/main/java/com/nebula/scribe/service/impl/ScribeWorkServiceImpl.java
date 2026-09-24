package com.nebula.scribe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import com.nebula.scribe.dto.WorkCreateRequest;
import com.nebula.scribe.dto.WorkPageQuery;
import com.nebula.scribe.dto.WorkUpdateRequest;
import com.nebula.scribe.entity.ScribeWork;
import com.nebula.scribe.enums.WorkAudience;
import com.nebula.scribe.enums.WorkStatus;
import com.nebula.scribe.mapper.ScribeWorkMapper;
import com.nebula.scribe.service.ScribeWorkService;
import com.nebula.scribe.vo.WorkDetailVO;
import com.nebula.scribe.vo.WorkListVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 作品服务实现
 *
 * <p>归属校验在服务端：user_id 一律取自 {@link UserContext}，从不信任请求参数。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScribeWorkServiceImpl implements ScribeWorkService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    /**
     * 仅用于标签/主角名这类字符串数组列的读写，不需要全局 Jackson 配置；
     * 容器里有多个 JsonMapper（含 redisJsonMapper），按类型注入会歧义，故自持一个。
     */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ScribeWorkMapper workMapper;

    @Override
    public PageResult<WorkListVO> page(WorkPageQuery query) {
        WorkPageQuery safe = query == null ? new WorkPageQuery() : query;
        Long userId = requireUserId();
        String keyword = StringUtils.hasText(safe.getKeyword()) ? safe.getKeyword().trim() : null;
        // 非法状态值按「不筛选」处理，与前端「全部」选项的空串语义一致
        String status = WorkStatus.isValid(safe.getStatus()) ? safe.getStatus() : null;

        LambdaQueryWrapper<ScribeWork> wrapper = new LambdaQueryWrapper<ScribeWork>()
                .eq(ScribeWork::getUserId, userId)
                .eq(status != null, ScribeWork::getStatus, status)
                .and(keyword != null, w -> w
                        .like(ScribeWork::getTitle, keyword)
                        .or().like(ScribeWork::getSummary, keyword));
        applySort(wrapper, safe.getSort());

        Page<ScribeWork> result = workMapper.selectPage(new Page<>(safe.safePageNum(), safe.safePageSize()), wrapper);
        List<WorkListVO> rows = result.getRecords().stream().map(this::toListVO).toList();
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public WorkListVO create(WorkCreateRequest req) {
        validateForm(req);

        ScribeWork work = new ScribeWork();
        work.setUserId(requireUserId());
        work.setTitle(req.getTitle().trim());
        work.setSummary(trimToNull(req.getSummary()));
        work.setLogline(trimToNull(req.getLogline()));
        work.setIntro(trimToNull(req.getIntro()));
        work.setAudience(trimToNull(req.getAudience()));
        work.setGenre(trimToNull(req.getGenre()));
        work.setTags(writeList(req.getTags()));
        work.setProtagonists(writeList(req.getProtagonists()));
        work.setStatus(WorkStatus.DRAFT.getCode());
        work.setTargetWordCount(req.getTargetWordCount());
        work.setWordCount(0);
        work.setChapterCount(0);
        workMapper.insert(work);
        log.info("作品已创建, workId={}, userId={}", work.getId(), work.getUserId());
        return toListVO(work);
    }

    @Override
    public WorkDetailVO detail(Long id) {
        return toDetailVO(requireOwned(id));
    }

    @Override
    public WorkDetailVO update(Long id, WorkUpdateRequest req) {
        validateForm(req);
        if (StringUtils.hasText(req.getStatus()) && !WorkStatus.isValid(req.getStatus())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "作品状态取值不合法");
        }
        ScribeWork current = requireOwned(id);

        // 用 set 显式写列：updateById 默认跳过 null，清空可选字段会静默失效
        LambdaUpdateWrapper<ScribeWork> wrapper = new LambdaUpdateWrapper<ScribeWork>()
                .eq(ScribeWork::getId, current.getId())
                .eq(ScribeWork::getUserId, current.getUserId())
                .set(ScribeWork::getTitle, req.getTitle().trim())
                .set(ScribeWork::getSummary, trimToNull(req.getSummary()))
                .set(ScribeWork::getLogline, trimToNull(req.getLogline()))
                .set(ScribeWork::getIntro, trimToNull(req.getIntro()))
                .set(ScribeWork::getAudience, trimToNull(req.getAudience()))
                .set(ScribeWork::getGenre, trimToNull(req.getGenre()))
                .set(ScribeWork::getTags, writeList(req.getTags()))
                .set(ScribeWork::getProtagonists, writeList(req.getProtagonists()))
                .set(ScribeWork::getTargetWordCount, req.getTargetWordCount())
                .set(StringUtils.hasText(req.getStatus()), ScribeWork::getStatus, req.getStatus());
        // 传空实体以触发 update_by/update_time 自动填充
        workMapper.update(new ScribeWork(), wrapper);
        log.info("作品已修改, workId={}, userId={}", current.getId(), current.getUserId());
        return toDetailVO(workMapper.selectById(current.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ScribeWork current = requireOwned(id);
        ScribeWork patch = new ScribeWork();
        patch.setId(current.getId());
        patch.setDeleteTime(LocalDateTime.now());
        workMapper.updateById(patch);
        workMapper.deleteById(current.getId());
        log.info("作品已移入回收站, workId={}, userId={}", current.getId(), current.getUserId());
    }

    // ----------------------------------------------------------------- 内部工具

    private void validateForm(WorkCreateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        if (StringUtils.hasText(req.getAudience()) && !WorkAudience.isValid(req.getAudience())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "目标读者取值不合法");
        }
    }

    /**
     * 按「id + 当前用户」取作品；别人的作品与不存在一律 404，不暴露他人作品是否存在
     */
    private ScribeWork requireOwned(Long id) {
        Long userId = requireUserId();
        ScribeWork work = id == null ? null : workMapper.selectOne(new LambdaQueryWrapper<ScribeWork>()
                .eq(ScribeWork::getId, id)
                .eq(ScribeWork::getUserId, userId));
        if (work == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "作品不存在");
        }
        return work;
    }

    private void applySort(LambdaQueryWrapper<ScribeWork> wrapper, String sort) {
        switch (sort == null ? "recent" : sort) {
            case "created" -> wrapper.orderByDesc(ScribeWork::getCreateTime);
            case "words" -> wrapper.orderByDesc(ScribeWork::getWordCount);
            case "title" -> wrapper.orderByAsc(ScribeWork::getTitle);
            default -> wrapper.orderByDesc(ScribeWork::getUpdateTime);
        }
        // 主键兜底，保证同值时分页稳定
        wrapper.orderByDesc(ScribeWork::getId);
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "未获取到当前用户");
        }
        return userId;
    }

    /**
     * 去空白、去重后序列化；空列表存 null，避免库里出现大量 "[]"
     */
    private String writeList(List<String> values) {
        if (values == null) {
            return null;
        }
        List<String> cleaned = values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        if (cleaned.isEmpty()) {
            return null;
        }
        return JSON.writeValueAsString(cleaned);
    }

    private List<String> readList(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return JSON.readValue(json, STRING_LIST);
        } catch (JacksonException e) {
            log.warn("作品 JSON 数组字段解析失败，按空处理: {}", json);
            return Collections.emptyList();
        }
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private WorkListVO toListVO(ScribeWork work) {
        return fillListFields(work, new WorkListVO());
    }

    private WorkDetailVO toDetailVO(ScribeWork work) {
        WorkDetailVO vo = fillListFields(work, new WorkDetailVO());
        vo.setIntro(work.getIntro());
        vo.setLogline(work.getLogline());
        return vo;
    }

    private <T extends WorkListVO> T fillListFields(ScribeWork work, T vo) {
        vo.setId(work.getId());
        vo.setTitle(work.getTitle());
        vo.setSummary(work.getSummary());
        vo.setAudience(work.getAudience());
        vo.setGenre(work.getGenre());
        vo.setTags(readList(work.getTags()));
        vo.setProtagonists(readList(work.getProtagonists()));
        vo.setStatus(work.getStatus());
        vo.setWordCount(work.getWordCount());
        vo.setChapterCount(work.getChapterCount());
        vo.setTargetWordCount(work.getTargetWordCount());
        vo.setCreateTime(work.getCreateTime());
        vo.setUpdateTime(work.getUpdateTime());
        return vo;
    }
}
