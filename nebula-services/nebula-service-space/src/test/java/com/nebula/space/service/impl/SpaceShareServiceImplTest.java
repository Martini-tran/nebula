package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.me.ShareCreateRequest;
import com.nebula.space.dto.me.ShareDownloadRequest;
import com.nebula.space.entity.SpaceFile;
import com.nebula.space.entity.SpaceShare;
import com.nebula.space.files.FileContents;
import com.nebula.space.files.FileDownload;
import com.nebula.space.files.ShareAttemptLimiter;
import com.nebula.space.mapper.SpaceFileMapper;
import com.nebula.space.mapper.SpaceShareMapper;
import com.nebula.space.mapper.SpaceUserMapper;
import com.nebula.space.vo.me.SharePublicVO;
import com.nebula.space.vo.me.ShareVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpaceShareServiceImplTest {

    private SpaceShareMapper shareMapper;
    private SpaceFileMapper fileMapper;
    private SpaceUserMapper userMapper;
    private FileContents contents;
    private ShareAttemptLimiter limiter;
    private SpaceShareServiceImpl service;
    private List<SpaceFile> files;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), SpaceShare.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), SpaceFile.class);
    }

    @BeforeEach
    void setUp() {
        shareMapper = mock(SpaceShareMapper.class);
        fileMapper = mock(SpaceFileMapper.class);
        userMapper = mock(SpaceUserMapper.class);
        contents = mock(FileContents.class);
        limiter = mock(ShareAttemptLimiter.class);
        service = new SpaceShareServiceImpl(shareMapper, fileMapper, userMapper, contents, limiter);
        files = new ArrayList<>();
        when(fileMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(files));
        UserContext.set(42L, "me", Collections.emptyList(), Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private SpaceFile file(long id, Long folderId, boolean folder, String name, long size) {
        SpaceFile f = new SpaceFile();
        f.setId(id);
        f.setUserId(42L);
        f.setFolderId(folderId);
        f.setIsFolder(folder ? 1 : 0);
        f.setName(name);
        f.setSizeBytes(size);
        f.setSource("upload");
        f.setSysFileId(folder ? null : 1000 + id);
        files.add(f);
        when(fileMapper.selectById(id)).thenReturn(f);
        return f;
    }

    private SpaceShare share(long id, long fileId, String password) {
        SpaceShare s = new SpaceShare();
        s.setId(id);
        s.setUserId(42L);
        s.setCode("Code" + id);
        s.setFileId(fileId);
        s.setFileName("快照名");
        s.setIsFolder(0);
        s.setPassword(password);
        s.setDownloads(0);
        s.setRevoked(0);
        s.setCreateTime(LocalDateTime.of(2026, 9, 1, 10, 0).plusHours(id));
        when(shareMapper.selectOne(any())).thenReturn(s);
        return s;
    }

    private static ShareDownloadRequest password(String p) {
        ShareDownloadRequest req = new ShareDownloadRequest();
        req.setPassword(p);
        return req;
    }

    @Test
    void createGivesCodePasswordAndEndOfDayExpiry() {
        file(1, null, false, "合同.pdf", 10);
        ShareCreateRequest req = new ShareCreateRequest();
        req.setFileId(1L);
        req.setDays(7);
        req.setWithPassword(true);
        req.setMaxDownloads(3);
        when(shareMapper.insert(any(SpaceShare.class)))
                .thenThrow(new DuplicateKeyException("dup"))
                .thenReturn(1);

        ShareVO vo = service.create(req);

        assertTrue(vo.getCode().matches("[A-HJ-NP-Za-km-z2-9]{6}"));
        assertTrue(vo.getPassword().matches("[a-hj-km-z2-9]{4}"));
        assertEquals(LocalDate.now().plusDays(7).atTime(23, 59, 59), vo.getExpireAt());
        assertEquals(3, vo.getMaxDownloads());
        assertEquals("合同.pdf", vo.getFileName());
        verify(shareMapper, times(2)).insert(any(SpaceShare.class));
    }

    @Test
    void cannotShareTrashedOrOthersFile() {
        file(1, null, false, "a.pdf", 1).setDeleteTime(LocalDateTime.now());
        file(2, null, false, "b.pdf", 1).setUserId(7L);
        ShareCreateRequest req = new ShareCreateRequest();
        req.setFileId(1L);
        assertThrows(BizException.class, () -> service.create(req));
        req.setFileId(2L);
        assertThrows(BizException.class, () -> service.create(req));
        verify(shareMapper, never()).insert(any(SpaceShare.class));
    }

    @Test
    void listPutsEndedLastAndUsesCurrentName() {
        file(1, null, false, "改过名.pdf", 1);
        SpaceShare ended = share(1, 1, null);
        ended.setRevoked(1);
        ended.setCreateTime(LocalDateTime.now());
        SpaceShare open = share(2, 99, "abcd");
        when(shareMapper.selectList(any())).thenReturn(List.of(ended, open));
        when(fileMapper.selectByIds(anyList())).thenReturn(List.of(files.get(0)));

        List<ShareVO> list = service.list();

        assertEquals(List.of(2L, 1L), list.stream().map(ShareVO::getId).toList());
        assertEquals("快照名", list.get(0).getFileName());
        assertEquals("改过名.pdf", list.get(1).getFileName());
        assertTrue(list.get(1).isRevoked());
    }

    @Test
    void publicInfoSumsFolderAndNamesOwner() {
        file(1, null, true, "体检报告", 0);
        file(2, 1L, false, "a.pdf", 100);
        file(3, 1L, false, "b.jpg", 50).setDeleteTime(LocalDateTime.now());
        SpaceShare s = share(1, 1, "abcd");
        s.setIsFolder(1);
        when(userMapper.displayName(42L)).thenReturn("小明");

        SharePublicVO vo = service.publicInfo("Code1");

        assertEquals(BigDecimal.valueOf(100), vo.getSize());
        assertTrue(vo.isNeedPassword());
        assertNull(vo.getUnavailable());
        assertEquals("小明", vo.getOwner());
        assertTrue(vo.getIsFolder());
    }

    @Test
    void publicInfoExplainsWhyUnavailable() {
        file(1, null, false, "a.pdf", 1).setDeleteTime(LocalDateTime.now());
        share(1, 1, null);

        SharePublicVO vo = service.publicInfo("Code1");
        assertEquals("文件已被删除", vo.getUnavailable());
        assertEquals("快照名", vo.getFileName());
        assertEquals("一位朋友", vo.getOwner());
    }

    @Test
    void unknownCodeIsNotFound() {
        BizException e = assertThrows(BizException.class, () -> service.publicInfo("nope"));
        assertEquals(HttpStatus.NOT_FOUND, e.getCode());
    }

    @Test
    void wrongPasswordCountsAndLockStopsTrying() {
        file(1, null, false, "a.pdf", 1);
        share(1, 1, "k7q2");

        BizException wrong = assertThrows(BizException.class, () -> service.download("Code1", password("xxxx")));
        assertEquals("提取码不对", wrong.getMessage());
        verify(limiter).fail("Code1");

        when(limiter.locked("Code1")).thenReturn(true);
        BizException locked = assertThrows(BizException.class, () -> service.download("Code1", password("k7q2")));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, locked.getCode());
        verify(contents, never()).single(any());
    }

    @Test
    void rightPasswordIgnoresCaseAndCountsDownload() {
        SpaceFile f = file(1, null, false, "a.pdf", 1);
        share(1, 1, "k7q2");
        when(shareMapper.update(isNull(), any())).thenReturn(1);
        FileDownload d = new FileDownload("a.pdf", "application/pdf", 1L, out -> {
        });
        when(contents.single(f)).thenReturn(d);

        assertSame(d, service.download("Code1", password(" K7Q2 ")));
        verify(limiter).clear("Code1");
        verify(shareMapper).update(isNull(), any());
    }

    @Test
    void folderShareDownloadsZip() {
        SpaceFile folder = file(1, null, true, "体检报告", 0);
        SpaceShare s = share(1, 1, null);
        s.setIsFolder(1);
        when(shareMapper.update(isNull(), any())).thenReturn(1);

        service.download("Code1", null);
        verify(contents).zip(any(SpaceFile.class), anyList());
        verify(contents, never()).single(folder);
    }

    @Test
    void usedUpOrExpiredCannotDownload() {
        file(1, null, false, "a.pdf", 1);
        SpaceShare s = share(1, 1, null);
        s.setMaxDownloads(1);
        s.setDownloads(1);
        assertEquals("已达下载次数上限", assertThrows(BizException.class, () -> service.download("Code1", null)).getMessage());

        s.setDownloads(0);
        s.setExpireAt(LocalDateTime.now().minusMinutes(1));
        assertEquals("分享已过期", assertThrows(BizException.class, () -> service.download("Code1", null)).getMessage());

        // 并发下被别人抢走最后一次：条件更新没更新到
        s.setExpireAt(null);
        when(shareMapper.update(isNull(), any())).thenReturn(0);
        assertEquals("已达下载次数上限", assertThrows(BizException.class, () -> service.download("Code1", null)).getMessage());
        verify(contents, never()).single(any());
    }

    @Test
    void failedContentGivesTheDownloadBack() {
        SpaceFile f = file(1, null, false, "a.pdf", 1);
        share(1, 1, null);
        when(shareMapper.update(isNull(), any())).thenReturn(1);
        when(contents.single(f)).thenThrow(new BizException(HttpStatus.NOT_FOUND, "文件内容不存在"));

        assertThrows(BizException.class, () -> service.download("Code1", null));
        verify(shareMapper, times(2)).update(isNull(), any());
    }

    @Test
    void revokeOnlyOwnShares() {
        SpaceShare mine = share(1, 1, null);
        when(shareMapper.selectById(1L)).thenReturn(mine);
        SpaceShare others = share(2, 1, null);
        others.setUserId(7L);
        when(shareMapper.selectById(2L)).thenReturn(others);

        service.revoke(1L);
        ArgumentCaptor<SpaceShare> saved = ArgumentCaptor.forClass(SpaceShare.class);
        verify(shareMapper).updateById(saved.capture());
        assertEquals(1, saved.getValue().getRevoked());

        BizException e = assertThrows(BizException.class, () -> service.revoke(2L));
        assertEquals(HttpStatus.NOT_FOUND, e.getCode());
    }

    @Test
    void stateOfCoversEveryEnding() {
        SpaceShare s = new SpaceShare();
        s.setRevoked(0);
        s.setDownloads(0);
        LocalDateTime now = LocalDateTime.now();
        assertNull(SpaceShareServiceImpl.stateOf(s, now));
        s.setExpireAt(now.plusSeconds(1));
        assertNull(SpaceShareServiceImpl.stateOf(s, now));
        s.setRevoked(1);
        assertFalse(SpaceShareServiceImpl.stateOf(s, now) == null);
    }
}
