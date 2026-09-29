package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import com.nebula.space.vo.me.ProfilePublicVO;
import com.nebula.space.vo.me.ProfileVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpaceProfileServiceImplTest {

    private SpaceProfileMapper profileMapper;
    private SpaceBookmarkFolderMapper folderMapper;
    private SpaceUserMapper userMapper;
    private PublicPageBuilder pageBuilder;
    private ProfileVisitCounter visitCounter;
    private SpaceProfileServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SpaceProfile.class);
        TableInfoHelper.initTableInfo(assistant, SpaceBookmarkFolder.class);
    }

    @BeforeEach
    void setUp() {
        profileMapper = mock(SpaceProfileMapper.class);
        folderMapper = mock(SpaceBookmarkFolderMapper.class);
        userMapper = mock(SpaceUserMapper.class);
        pageBuilder = mock(PublicPageBuilder.class);
        visitCounter = mock(ProfileVisitCounter.class);
        service = new SpaceProfileServiceImpl(profileMapper, folderMapper, userMapper, pageBuilder, visitCounter);
        UserContext.set(42L, "Zhang.San", Collections.emptyList(), Collections.emptyList());
        when(profileMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private static ProfileLink link(String label, String url) {
        ProfileLink l = new ProfileLink();
        l.setLabel(label);
        l.setUrl(url);
        return l;
    }

    private static ProfileCollection collection(Long folderId, String title) {
        ProfileCollection c = new ProfileCollection();
        c.setFolderId(folderId);
        c.setTitle(title);
        return c;
    }

    private static SpaceBookmarkFolder folder(Long id) {
        SpaceBookmarkFolder f = new SpaceBookmarkFolder();
        f.setId(id);
        return f;
    }

    private static ProfileSaveRequest request(String handle) {
        ProfileSaveRequest req = new ProfileSaveRequest();
        req.setEnabled(true);
        req.setHandle(handle);
        return req;
    }

    private static SpaceProfile stored() {
        SpaceProfile p = new SpaceProfile();
        p.setId(3L);
        p.setUserId(42L);
        p.setEnabled(1);
        p.setHandle("zhangsan");
        p.setBio("写代码的");
        p.setLinks("[]");
        p.setNowText("在跑步");
        p.setNowUpdated(LocalDate.of(2026, 1, 1));
        p.setBlocks("[{\"key\":\"collections\",\"on\":true}]");
        p.setCollections("[{\"folderId\":5,\"title\":\"工具\",\"description\":\"\"}]");
        p.setHiddenReading("[]");
        p.setQuoteIds("[]");
        p.setCollectionViews(4);
        p.setImportCount(2);
        return p;
    }

    @Test
    void firstOpenGivesClosedDefaultsWithHandleFromUsername() {
        when(visitCounter.recent(eq(42L), any(LocalDate.class))).thenReturn(0);
        ProfileVO vo = service.get();
        assertFalse(vo.isEnabled());
        assertEquals("zhangsan", vo.getHandle());
        assertEquals("", vo.getBio());
        assertEquals(List.of("intro", "links", "now", "collections", "reading", "goals", "quotes"),
                vo.getBlocks().stream().map(ProfileBlock::getKey).toList());
        assertEquals(List.of(true, true, true, true, true, false, false),
                vo.getBlocks().stream().map(ProfileBlock::isOn).toList());
        assertEquals(0, vo.getStats().getImports());
    }

    @Test
    void unusableUsernameFallsBackToIdHandle() {
        UserContext.set(42L, "张三", Collections.emptyList(), Collections.emptyList());
        assertEquals("u-16", service.get().getHandle());
    }

    @Test
    void storedStatsComeBack() {
        when(profileMapper.selectOne(any(Wrapper.class))).thenReturn(stored());
        when(visitCounter.recent(eq(42L), any(LocalDate.class))).thenReturn(17);
        ProfileVO vo = service.get();
        assertEquals(17, vo.getStats().getVisits());
        assertEquals(4, vo.getStats().getCollectionViews());
        assertEquals(2, vo.getStats().getImports());
        // 存的区块不全时补齐，漏掉的关着
        assertEquals(7, vo.getBlocks().size());
        assertEquals("collections", vo.getBlocks().get(0).getKey());
        assertFalse(vo.getBlocks().get(1).isOn());
    }

    @Test
    void handleRules() {
        assertNull(SpaceProfileServiceImpl.handleProblem("zhang-san_1"));
        assertEquals("3-20 位小写字母、数字、- 或 _", SpaceProfileServiceImpl.handleProblem("ab"));
        assertEquals("3-20 位小写字母、数字、- 或 _", SpaceProfileServiceImpl.handleProblem("zhang san"));
        assertEquals("这个名字是保留的，换一个", SpaceProfileServiceImpl.handleProblem("admin"));
        assertEquals("zhangsan", SpaceProfileServiceImpl.normalizeHandle(" ZhangSan "));
    }

    @Test
    void badHandleIs400() {
        BizException e = assertThrows(BizException.class, () -> service.save(request("me")));
        assertEquals(HttpStatus.BAD_REQUEST, e.getCode());
        verify(profileMapper, never()).insert(any(SpaceProfile.class));
    }

    @Test
    void handleTakenByOthersIs400() {
        when(profileMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        BizException e = assertThrows(BizException.class, () -> service.save(request("zhangsan")));
        assertEquals(HttpStatus.BAD_REQUEST, e.getCode());
        assertTrue(e.getMessage().contains("别人"));
        assertFalse(service.checkHandle("zhangsan").isAvailable());
    }

    @Test
    void scriptLinksAreRejected() {
        ProfileSaveRequest req = request("zhangsan");
        req.setLinks(List.of(link("博客", "javascript:alert(1)")));
        BizException e = assertThrows(BizException.class, () -> service.save(req));
        assertEquals(HttpStatus.BAD_REQUEST, e.getCode());
        assertTrue(e.getMessage().contains("博客"));
    }

    @Test
    void firstSaveInsertsCleanedSettings() {
        when(folderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(folder(5L)));
        ProfileSaveRequest req = request(" ZhangSan ");
        req.setBio(" 写代码的 ");
        req.setNow("在跑步");
        req.setLinks(List.of(link("GitHub", "https://github.com/zs"), link(" ", " "), link("邮箱", "zs@example.com")));
        req.setBlocks(List.of(ProfileBlock.of("now", true), ProfileBlock.of("bogus", true), ProfileBlock.of("now", false), ProfileBlock.of("intro", true)));
        // 9 不是自己的目录
        req.setCollections(List.of(collection(5L, " 工具 "), collection(9L, "别人的"), collection(5L, "重复")));
        req.setQuoteIds(List.of(8L, 8L, 7L));

        ProfileVO vo = service.save(req);

        ArgumentCaptor<SpaceProfile> row = ArgumentCaptor.forClass(SpaceProfile.class);
        verify(profileMapper).insert(row.capture());
        assertEquals(1, row.getValue().getEnabled());
        assertEquals("zhangsan", vo.getHandle());
        assertEquals("写代码的", vo.getBio());
        assertEquals(2, vo.getLinks().size());
        assertEquals(LocalDate.now(), vo.getNowUpdated());
        assertEquals(List.of("now", "intro", "links", "collections", "reading", "goals", "quotes"),
                vo.getBlocks().stream().map(ProfileBlock::getKey).toList());
        assertTrue(vo.getBlocks().get(0).isOn());
        assertFalse(vo.getBlocks().get(2).isOn());
        assertEquals(1, vo.getCollections().size());
        assertEquals("工具", vo.getCollections().get(0).getTitle());
        assertEquals(List.of(8L, 7L), vo.getQuoteIds());
    }

    @Test
    void nowDateOnlyMovesWhenNowChanges() {
        when(profileMapper.selectOne(any(Wrapper.class))).thenReturn(stored());
        ProfileSaveRequest same = request("zhangsan");
        same.setNow("在跑步");
        assertEquals(LocalDate.of(2026, 1, 1), service.save(same).getNowUpdated());

        when(profileMapper.selectOne(any(Wrapper.class))).thenReturn(stored());
        ProfileSaveRequest changed = request("zhangsan");
        changed.setNow("在游泳");
        assertEquals(LocalDate.now(), service.save(changed).getNowUpdated());
        verify(profileMapper, never()).insert(any(SpaceProfile.class));
    }

    @Test
    void handleGrabbedDuringInsertIs400() {
        when(profileMapper.insert(any(SpaceProfile.class))).thenThrow(new DuplicateKeyException("uk_space_profile_handle"));
        when(profileMapper.selectCount(any(Wrapper.class))).thenReturn(0L, 1L);
        BizException e = assertThrows(BizException.class, () -> service.save(request("zhangsan")));
        assertEquals(HttpStatus.BAD_REQUEST, e.getCode());
    }

    @Test
    void closedOrMissingPageIs404() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(BizException.class, () -> service.publicPage("nobody")).getCode());
        SpaceProfile off = stored();
        off.setEnabled(0);
        when(profileMapper.selectOne(any(Wrapper.class))).thenReturn(off);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(BizException.class, () -> service.publicPage("zhangsan")).getCode());
        verify(visitCounter, never()).hit(anyLong(), any(LocalDate.class));
    }

    @Test
    void openPageIsBuiltForOwnerAndCounted() {
        when(profileMapper.selectOne(any(Wrapper.class))).thenReturn(stored());
        when(userMapper.displayName(42L)).thenReturn("张三");
        ProfilePublicVO page = new ProfilePublicVO();
        when(pageBuilder.build(eq(42L), eq("张三"), any(ProfileVO.class), any(LocalDate.class))).thenReturn(page);
        UserContext.clear();
        assertSame(page, service.publicPage("ZhangSan"));
        verify(visitCounter).hit(eq(42L), any(LocalDate.class));
    }

    @Test
    void onlyListedCollectionsAreCounted() {
        when(profileMapper.selectOne(any(Wrapper.class))).thenReturn(stored());
        service.collectionImported("zhangsan", "5");
        verify(profileMapper).increaseImports(3L);

        BizException e = assertThrows(BizException.class, () -> service.collectionViewed("zhangsan", "9"));
        assertEquals(HttpStatus.NOT_FOUND, e.getCode());
        verify(profileMapper, never()).increaseCollectionViews(anyLong());
    }

    @Test
    void previewUsesDraftWithoutSaving() {
        when(userMapper.displayName(42L)).thenReturn("张三");
        ProfileSaveRequest req = request("me");
        req.setEnabled(false);
        req.setCollections(List.of(collection(9L, "草稿")));
        service.preview(req);
        ArgumentCaptor<ProfileVO> draft = ArgumentCaptor.forClass(ProfileVO.class);
        verify(pageBuilder).build(eq(42L), eq("张三"), draft.capture(), any(LocalDate.class));
        assertEquals("me", draft.getValue().getHandle());
        assertEquals(1, draft.getValue().getCollections().size());
        verify(profileMapper, never()).insert(any(SpaceProfile.class));
        verify(profileMapper, never()).updateById(any(SpaceProfile.class));
    }
}
