package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.me.ProfileBlock;
import com.nebula.space.dto.me.ProfileCollection;
import com.nebula.space.dto.me.ProfileLink;
import com.nebula.space.dto.me.ProfileSaveRequest;
import com.nebula.space.entity.SpaceBookmarkFolder;
import com.nebula.space.entity.SpaceProfile;
import com.nebula.space.mapper.SpaceBookmarkFolderMapper;
import com.nebula.space.mapper.SpaceProfileMapper;
import com.nebula.space.mapper.SpaceUserMapper;
import com.nebula.space.profile.ProfileVisitCounter;
import com.nebula.space.profile.PublicPageBuilder;
import com.nebula.space.service.SpaceProfileService;
import com.nebula.space.vo.me.HandleCheckVO;
import com.nebula.space.vo.me.ProfilePublicVO;
import com.nebula.space.vo.me.ProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 公开主页服务实现
 */
@Service
@RequiredArgsConstructor
public class SpaceProfileServiceImpl implements SpaceProfileService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<ProfileLink>> LINK_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<ProfileBlock>> BLOCK_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<ProfileCollection>> COLLECTION_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<Long>> ID_LIST = new TypeReference<>() {
    };

    /**
     * 与前端 api/profile.ts 的 handleProblem 同一套规则
     */
    private static final Pattern HANDLE = Pattern.compile("^[a-z0-9_-]{3,20}$");
    private static final Set<String> RESERVED = Set.of("admin", "api", "space", "login", "logout", "www", "root", "nebula",
            "me", "public", "help", "settings", "system");
    /**
     * 全部区块，也是第一次打开时的顺序
     */
    static final List<String> BLOCK_KEYS = List.of("intro", "links", "now", "collections", "reading", "goals", "quotes");
    private static final Set<String> DEFAULT_ON = Set.of("intro", "links", "now", "collections", "reading");
    private static final String NOT_FOUND = "这个主页不存在，或者主人没有公开";

    private final SpaceProfileMapper profileMapper;
    private final SpaceBookmarkFolderMapper folderMapper;
    private final SpaceUserMapper userMapper;
    private final PublicPageBuilder pageBuilder;
    private final ProfileVisitCounter visitCounter;

    @Override
    public ProfileVO get() {
        Long userId = requireUserId();
        SpaceProfile row = find(userId);
        ProfileVO vo = row == null ? defaults(userId) : toVO(row);
        vo.setStats(stats(row, userId));
        return vo;
    }

    @Override
    public ProfileVO save(ProfileSaveRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        Long userId = requireUserId();
        String handle = normalizeHandle(req.getHandle());
        String problem = handleProblem(handle);
        if (problem != null) {
            throw new BizException(HttpStatus.BAD_REQUEST, problem);
        }
        if (takenByOthers(handle, userId)) {
            throw handleTaken();
        }
        List<ProfileLink> links = cleanLinks(req.getLinks());
        for (ProfileLink l : links) {
            if (!PublicPageBuilder.isSafeLink(l.getUrl())) {
                throw new BizException(HttpStatus.BAD_REQUEST, "链接「" + l.getLabel() + "」要以 http:// 或 https:// 开头，或者填邮箱");
            }
        }

        SpaceProfile row = find(userId);
        boolean isNew = row == null;
        if (isNew) {
            row = new SpaceProfile();
            row.setUserId(userId);
            row.setCollectionViews(0);
            row.setImportCount(0);
        }
        String now = trim(req.getNow());
        row.setNowUpdated(nowUpdated(isNew ? null : row, now));
        row.setNowText(now);
        row.setEnabled(Boolean.TRUE.equals(req.getEnabled()) ? 1 : 0);
        row.setHandle(handle);
        row.setBio(trim(req.getBio()));
        row.setLinks(writeJson(links));
        row.setBlocks(writeJson(normalizeBlocks(req.getBlocks())));
        row.setCollections(writeJson(ownCollections(userId, req.getCollections())));
        row.setHiddenReading(writeJson(distinctIds(req.getHiddenReading())));
        row.setQuoteIds(writeJson(distinctIds(req.getQuoteIds())));

        if (isNew) {
            try {
                profileMapper.insert(row);
            } catch (DuplicateKeyException e) {
                // 短名刚被别人抢走，或者两个标签页同时第一次保存
                if (takenByOthers(handle, userId)) {
                    throw handleTaken();
                }
                SpaceProfile existing = find(userId);
                if (existing == null) {
                    throw e;
                }
                row.setId(existing.getId());
                row.setCollectionViews(existing.getCollectionViews());
                row.setImportCount(existing.getImportCount());
                update(row);
            }
        } else {
            update(row);
        }
        ProfileVO vo = toVO(row);
        vo.setStats(stats(row, userId));
        return vo;
    }

    @Override
    public HandleCheckVO checkHandle(String handle) {
        Long userId = requireUserId();
        String h = normalizeHandle(handle);
        String problem = handleProblem(h);
        if (problem != null) {
            return new HandleCheckVO(false, problem);
        }
        if (takenByOthers(h, userId)) {
            return new HandleCheckVO(false, "已经被别人用了");
        }
        return new HandleCheckVO(true, null);
    }

    @Override
    public ProfilePublicVO preview(ProfileSaveRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        Long userId = requireUserId();
        String now = trim(req.getNow());
        ProfileVO draft = new ProfileVO();
        draft.setEnabled(Boolean.TRUE.equals(req.getEnabled()));
        draft.setHandle(normalizeHandle(req.getHandle()));
        draft.setBio(trim(req.getBio()));
        draft.setLinks(cleanLinks(req.getLinks()));
        draft.setNow(now);
        draft.setNowUpdated(nowUpdated(find(userId), now));
        draft.setBlocks(normalizeBlocks(req.getBlocks()));
        draft.setCollections(cleanCollections(req.getCollections()));
        draft.setHiddenReading(distinctIds(req.getHiddenReading()));
        draft.setQuoteIds(distinctIds(req.getQuoteIds()));
        return pageBuilder.build(userId, userMapper.displayName(userId), draft, LocalDate.now());
    }

    @Override
    public ProfilePublicVO publicPage(String handle) {
        SpaceProfile row = requirePublic(handle);
        LocalDate today = LocalDate.now();
        ProfilePublicVO page = pageBuilder.build(row.getUserId(), userMapper.displayName(row.getUserId()), toVO(row), today);
        visitCounter.hit(row.getUserId(), today);
        return page;
    }

    @Override
    public void collectionViewed(String handle, String collectionId) {
        profileMapper.increaseCollectionViews(requirePublicCollection(handle, collectionId).getId());
    }

    @Override
    public void collectionImported(String handle, String collectionId) {
        profileMapper.increaseImports(requirePublicCollection(handle, collectionId).getId());
    }

    // ----------------------------------------------------------------- 短名

    static String normalizeHandle(String handle) {
        return handle == null ? "" : handle.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 不合规的原因；合规返回 null
     */
    static String handleProblem(String handle) {
        if (!HANDLE.matcher(handle).matches()) {
            return "3-20 位小写字母、数字、- 或 _";
        }
        if (RESERVED.contains(handle)) {
            return "这个名字是保留的，换一个";
        }
        return null;
    }

    private boolean takenByOthers(String handle, Long userId) {
        return profileMapper.selectCount(
                new LambdaQueryWrapper<SpaceProfile>()
                        .eq(SpaceProfile::getHandle, handle)
                        .ne(SpaceProfile::getUserId, userId)
        ) > 0;
    }

    private static BizException handleTaken() {
        return new BizException(HttpStatus.BAD_REQUEST, "这个短名已经被别人用了，换一个");
    }

    /**
     * 第一次打开时的短名：账号名里能用的部分；不合规或被占用时用「u-」加 ID 的 36 进制
     */
    private String suggestHandle(Long userId) {
        String username = UserContext.getUsername();
        String candidate = username == null ? "" : username.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        if (candidate.length() > 20) {
            candidate = candidate.substring(0, 20);
        }
        if (handleProblem(candidate) == null && !takenByOthers(candidate, userId)) {
            return candidate;
        }
        return "u-" + Long.toString(userId, 36);
    }

    // ----------------------------------------------------------------- 整理请求

    /**
     * 名称和地址都空的行是页面上点了「加一个」没填，丢掉
     */
    private static List<ProfileLink> cleanLinks(List<ProfileLink> links) {
        if (links == null) {
            return List.of();
        }
        List<ProfileLink> out = new ArrayList<>();
        for (ProfileLink l : links) {
            if (l == null || (!StringUtils.hasText(l.getLabel()) && !StringUtils.hasText(l.getUrl()))) {
                continue;
            }
            ProfileLink c = new ProfileLink();
            c.setLabel(trim(l.getLabel()));
            c.setUrl(trim(l.getUrl()));
            out.add(c);
        }
        return out;
    }

    /**
     * 七个区块都在、不重复：认识的按传来的顺序，漏掉的补在后面并关着；没传用默认
     */
    static List<ProfileBlock> normalizeBlocks(List<ProfileBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return BLOCK_KEYS.stream().map(k -> ProfileBlock.of(k, DEFAULT_ON.contains(k))).toList();
        }
        Map<String, Boolean> ordered = new LinkedHashMap<>();
        for (ProfileBlock b : blocks) {
            if (b != null && BLOCK_KEYS.contains(b.getKey())) {
                ordered.putIfAbsent(b.getKey(), b.isOn());
            }
        }
        BLOCK_KEYS.forEach(k -> ordered.putIfAbsent(k, false));
        return ordered.entrySet().stream().map(e -> ProfileBlock.of(e.getKey(), e.getValue())).toList();
    }

    private static List<ProfileCollection> cleanCollections(List<ProfileCollection> collections) {
        if (collections == null) {
            return List.of();
        }
        Map<Long, ProfileCollection> byFolder = new LinkedHashMap<>();
        for (ProfileCollection c : collections) {
            if (c == null || c.getFolderId() == null) {
                continue;
            }
            ProfileCollection out = new ProfileCollection();
            out.setFolderId(c.getFolderId());
            out.setTitle(trim(c.getTitle()));
            out.setDescription(trim(c.getDescription()));
            byFolder.putIfAbsent(c.getFolderId(), out);
        }
        return new ArrayList<>(byFolder.values());
    }

    /**
     * 只留自己的、还在的目录；目录删了之后再保存一次，合集就跟着去掉
     */
    private List<ProfileCollection> ownCollections(Long userId, List<ProfileCollection> collections) {
        List<ProfileCollection> cleaned = cleanCollections(collections);
        if (cleaned.isEmpty()) {
            return cleaned;
        }
        Set<Long> own = folderMapper.selectList(
                new LambdaQueryWrapper<SpaceBookmarkFolder>()
                        .select(SpaceBookmarkFolder::getId)
                        .eq(SpaceBookmarkFolder::getUserId, userId)
                        .in(SpaceBookmarkFolder::getId, cleaned.stream().map(ProfileCollection::getFolderId).toList())
        ).stream().map(SpaceBookmarkFolder::getId).collect(Collectors.toSet());
        return cleaned.stream().filter(c -> own.contains(c.getFolderId())).toList();
    }

    private static List<Long> distinctIds(List<Long> ids) {
        return ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    /**
     * Now 改了就记今天；没改沿用原来的日期
     */
    private static LocalDate nowUpdated(SpaceProfile existing, String now) {
        if (existing == null) {
            return now.isEmpty() ? null : LocalDate.now();
        }
        return Objects.equals(now, trim(existing.getNowText())) ? existing.getNowUpdated() : LocalDate.now();
    }

    // ----------------------------------------------------------------- 访客

    private SpaceProfile requirePublic(String handle) {
        String h = normalizeHandle(handle);
        SpaceProfile row = h.isEmpty() ? null : profileMapper.selectOne(
                new LambdaQueryWrapper<SpaceProfile>().eq(SpaceProfile::getHandle, h)
        );
        if (row == null || !Objects.equals(row.getEnabled(), 1)) {
            throw new BizException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        return row;
    }

    /**
     * 合集得是公开区块里正在公开的那几个
     */
    private SpaceProfile requirePublicCollection(String handle, String collectionId) {
        SpaceProfile row = requirePublic(handle);
        ProfileVO vo = toVO(row);
        boolean on = vo.getBlocks().stream().anyMatch(b -> b.isOn() && "collections".equals(b.getKey()));
        boolean listed = vo.getCollections().stream().anyMatch(c -> String.valueOf(c.getFolderId()).equals(collectionId));
        if (!on || !listed) {
            throw new BizException(HttpStatus.NOT_FOUND, "合集不存在");
        }
        return row;
    }

    // ----------------------------------------------------------------- 内部工具

    private ProfileVO defaults(Long userId) {
        ProfileVO vo = new ProfileVO();
        vo.setEnabled(false);
        vo.setHandle(suggestHandle(userId));
        vo.setBio("");
        vo.setLinks(List.of());
        vo.setNow("");
        vo.setNowUpdated(null);
        vo.setBlocks(normalizeBlocks(null));
        vo.setCollections(List.of());
        vo.setHiddenReading(List.of());
        vo.setQuoteIds(List.of());
        return vo;
    }

    private ProfileVO.Stats stats(SpaceProfile row, Long userId) {
        ProfileVO.Stats s = new ProfileVO.Stats();
        s.setVisits(visitCounter.recent(userId, LocalDate.now()));
        s.setCollectionViews(row == null || row.getCollectionViews() == null ? 0 : row.getCollectionViews());
        s.setImports(row == null || row.getImportCount() == null ? 0 : row.getImportCount());
        return s;
    }

    private void update(SpaceProfile row) {
        // 审计填充是严格模式，已有值不会覆盖，这里显式刷新
        row.setUpdateTime(LocalDateTime.now());
        try {
            profileMapper.updateById(row);
        } catch (DuplicateKeyException e) {
            throw handleTaken();
        }
    }

    private SpaceProfile find(Long userId) {
        return profileMapper.selectOne(
                new LambdaQueryWrapper<SpaceProfile>().eq(SpaceProfile::getUserId, userId)
        );
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "未获取到当前用户");
        }
        return userId;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String writeJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(HttpStatus.BAD_REQUEST, "数据格式不正确");
        }
    }

    private static <T> List<T> readList(String json, TypeReference<List<T>> type) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<T> list = JSON.readValue(json, type);
            return list == null ? List.of() : list;
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private ProfileVO toVO(SpaceProfile row) {
        ProfileVO vo = new ProfileVO();
        vo.setEnabled(Objects.equals(row.getEnabled(), 1));
        vo.setHandle(row.getHandle());
        vo.setBio(row.getBio() == null ? "" : row.getBio());
        vo.setLinks(readList(row.getLinks(), LINK_LIST));
        vo.setNow(row.getNowText() == null ? "" : row.getNowText());
        vo.setNowUpdated(row.getNowUpdated());
        vo.setBlocks(StringUtils.hasText(row.getBlocks()) ? normalizeBlocks(readList(row.getBlocks(), BLOCK_LIST)) : normalizeBlocks(null));
        vo.setCollections(readList(row.getCollections(), COLLECTION_LIST));
        vo.setHiddenReading(readList(row.getHiddenReading(), ID_LIST));
        vo.setQuoteIds(readList(row.getQuoteIds(), ID_LIST));
        return vo;
    }
}
