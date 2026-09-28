package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.common.file.dto.FileBindRequest;
import com.nebula.common.file.dto.FileUploadRequest;
import com.nebula.common.file.properties.FileProperties;
import com.nebula.common.file.service.SysFileService;
import com.nebula.common.file.vo.FileInfoVO;
import com.nebula.space.dto.me.FileFolderCreateRequest;
import com.nebula.space.dto.me.FileQuery;
import com.nebula.space.dto.me.FileUpdateRequest;
import com.nebula.space.entity.SpaceFile;
import com.nebula.space.files.FileContents;
import com.nebula.space.files.SpaceFileProperties;
import com.nebula.space.mapper.SpaceFileMapper;
import com.nebula.space.vo.me.FileUsageVO;
import com.nebula.space.vo.me.SpaceFileVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpaceFileServiceImplTest {

    private static final long MB = 1024 * 1024;

    private SpaceFileMapper fileMapper;
    private SysFileService sysFileService;
    private FileContents contents;
    private SpaceFileProperties properties;
    private SpaceFileServiceImpl service;
    private List<SpaceFile> rows;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SpaceFile.class);
    }

    @BeforeEach
    void setUp() {
        fileMapper = mock(SpaceFileMapper.class);
        sysFileService = mock(SysFileService.class);
        contents = mock(FileContents.class);
        properties = new SpaceFileProperties();
        FileProperties fileProperties = new FileProperties();
        fileProperties.setMaxFileSize(100 * MB);
        service = new SpaceFileServiceImpl(fileMapper, sysFileService, contents, properties, fileProperties);
        rows = new ArrayList<>();
        when(fileMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(rows));
        UserContext.set(42L, "me", Collections.emptyList(), Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private SpaceFile add(long id, Long folderId, boolean folder, String name, long size) {
        SpaceFile f = new SpaceFile();
        f.setId(id);
        f.setUserId(42L);
        f.setFolderId(folderId);
        f.setIsFolder(folder ? 1 : 0);
        f.setName(name);
        f.setSizeBytes(size);
        f.setMime(folder ? "" : "application/octet-stream");
        f.setSource("upload");
        f.setSysFileId(folder ? null : 1000 + id);
        f.setCreateTime(LocalDateTime.of(2026, 9, 1, 10, 0).plusMinutes(id));
        f.setUpdateTime(f.getCreateTime());
        rows.add(f);
        return f;
    }

    private static MultipartFile multipart(String name, long size) {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(name);
        when(file.getSize()).thenReturn(size);
        when(file.isEmpty()).thenReturn(size == 0);
        return file;
    }

    private static FileQuery view(String view) {
        FileQuery q = new FileQuery();
        q.setView(view);
        return q;
    }

    private static List<String> names(List<SpaceFileVO> list) {
        return list.stream().map(SpaceFileVO::getName).toList();
    }

    @SuppressWarnings("unchecked")
    private List<Object> updatedIds() {
        ArgumentCaptor<LambdaUpdateWrapper<SpaceFile>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(fileMapper).update(isNull(), captor.capture());
        // 条件里的参数在拼 SQL 时才登记
        captor.getValue().getSqlSegment();
        return new ArrayList<>(captor.getValue().getParamNameValuePairs().values());
    }

    @Test
    void folderViewListsOnlyThatFolderFoldersFirst() {
        add(1, null, true, "证件", 0);
        add(2, null, false, "简历.docx", 10);
        add(3, 1L, false, "身份证.jpg", 20);
        add(4, null, false, "旧文件.pdf", 5).setDeleteTime(LocalDateTime.now());
        add(5, null, false, "白板.jpg", 30).setSource("notes");

        assertEquals(List.of("证件", "简历.docx"), names(service.list(view("folder"))));
        FileQuery inside = view("folder");
        inside.setFolderId(1L);
        assertEquals(List.of("身份证.jpg"), names(service.list(inside)));
        FileQuery notes = view("source");
        notes.setSource("notes");
        assertEquals(List.of("白板.jpg"), names(service.list(notes)));
        FileQuery images = view("kind");
        images.setKind("image");
        assertEquals(List.of("白板.jpg", "身份证.jpg"), names(service.list(images)));
    }

    @Test
    void trashListsOnlyTheLayerThatWasDeleted() {
        LocalDateTime stamp = LocalDateTime.now().withNano(0);
        add(1, null, true, "体检报告", 0).setDeleteTime(stamp);
        add(2, 1L, false, "血常规.jpg", 10).setDeleteTime(stamp);
        add(3, null, false, "截图.png", 10).setDeleteTime(stamp.minusDays(2));

        assertEquals(List.of("体检报告", "截图.png"), names(service.list(view("trash"))));
    }

    @Test
    void listPurgesTrashOlderThanThirtyDays() {
        LocalDateTime old = LocalDateTime.now().minusDays(31);
        add(1, null, true, "旧文件夹", 0).setDeleteTime(old);
        add(2, 1L, false, "里面.pdf", 10).setDeleteTime(old);
        add(3, null, false, "还在.pdf", 10);

        assertEquals(List.of("还在.pdf"), names(service.list(view("all"))));
        verify(fileMapper).deleteByIds(List.of(1L, 2L));
        verify(sysFileService).deleteAll(List.of(1002L), true);
    }

    @Test
    void usageCountsTrashButNotFolders() {
        add(1, null, true, "证件", 0);
        add(2, 1L, false, "合同.pdf", 3 * MB);
        add(3, null, false, "照片.jpg", 2 * MB).setDeleteTime(LocalDateTime.now());

        FileUsageVO u = service.usage();
        assertEquals(BigDecimal.valueOf(5 * MB), u.getUsed());
        assertEquals(BigDecimal.valueOf(3 * MB), u.getByKind().get("pdf"));
        assertEquals(BigDecimal.valueOf(2 * MB), u.getByKind().get("image"));
        assertEquals(BigDecimal.ZERO, u.getByKind().get("other"));
        assertEquals(BigDecimal.valueOf(DataSize.ofGigabytes(10).toBytes()), u.getTotal());
        assertEquals(BigDecimal.valueOf(100 * MB), u.getMaxFileSize());
    }

    @Test
    void uploadStoresPrivatelyRenamesDuplicateAndBinds() {
        add(1, null, true, "证件", 0);
        add(2, 1L, false, "合同.pdf", 10);
        FileInfoVO stored = new FileInfoVO();
        stored.setId(555L);
        stored.setSizeBytes(4L);
        stored.setMimeType("application/pdf");
        when(sysFileService.upload(any(MultipartFile.class), any())).thenReturn(stored);
        doAnswer(inv -> {
            inv.<SpaceFile>getArgument(0).setId(99L);
            return 1;
        }).when(fileMapper).insert(any(SpaceFile.class));

        SpaceFileVO vo = service.upload(multipart("C:\\fakepath\\合同.pdf", 4), 1L);

        assertEquals("合同 (1).pdf", vo.getName());
        assertEquals(1L, vo.getFolderId());
        assertEquals(BigDecimal.valueOf(4), vo.getSize());
        ArgumentCaptor<FileUploadRequest> req = ArgumentCaptor.forClass(FileUploadRequest.class);
        verify(sysFileService).upload(any(MultipartFile.class), req.capture());
        assertEquals(0, req.getValue().getIsPublic());
        assertEquals("space/42", req.getValue().getPrefix());
        assertEquals("space_file", req.getValue().getTargetType());
        ArgumentCaptor<FileBindRequest> bind = ArgumentCaptor.forClass(FileBindRequest.class);
        verify(sysFileService).bind(eq(555L), bind.capture());
        assertEquals(99L, bind.getValue().getTargetId());
    }

    @Test
    void uploadOverQuotaIsRejectedBeforeStoring() {
        properties.setQuota(DataSize.ofMegabytes(1));
        add(1, null, false, "大文件.zip", MB - 2).setDeleteTime(LocalDateTime.now());

        BizException e = assertThrows(BizException.class,
                () -> service.upload(multipart("a.txt", 3), null));
        assertTrue(e.getMessage().startsWith("空间不够了"));
        verify(sysFileService, never()).upload(any(MultipartFile.class), any());
    }

    @Test
    void uploadIntoTrashedFolderIsRejected() {
        add(1, null, true, "证件", 0).setDeleteTime(LocalDateTime.now());

        BizException e = assertThrows(BizException.class,
                () -> service.upload(multipart("a.txt", 3), 1L));
        assertEquals(HttpStatus.NOT_FOUND, e.getCode());
    }

    @Test
    void failedBindRemovesStoredObject() {
        FileInfoVO stored = new FileInfoVO();
        stored.setId(555L);
        when(sysFileService.upload(any(MultipartFile.class), any())).thenReturn(stored);
        doAnswer(inv -> {
            inv.<SpaceFile>getArgument(0).setId(99L);
            return 1;
        }).when(fileMapper).insert(any(SpaceFile.class));
        doThrow(new IllegalStateException("db down")).when(sysFileService).bind(eq(555L), any());

        assertThrows(IllegalStateException.class,
                () -> service.upload(multipart("a.txt", 3), null));
        verify(fileMapper).deleteById(99L);
        verify(sysFileService).delete(555L, true);
    }

    @Test
    void createFolderRejectsDuplicateName() {
        add(1, null, true, "发票", 0);
        FileFolderCreateRequest req = new FileFolderCreateRequest();
        req.setName(" 发票 ");

        assertThrows(BizException.class, () -> service.createFolder(req));
        verify(fileMapper, never()).insert(any(SpaceFile.class));
    }

    @Test
    void cannotMoveFolderIntoItsOwnSubfolder() {
        add(1, null, true, "A", 0);
        add(2, 1L, true, "B", 0);
        FileUpdateRequest req = new FileUpdateRequest();
        req.setFolderId(2L);

        BizException e = assertThrows(BizException.class, () -> service.update(1L, req));
        assertEquals("不能移到它自己里面", e.getMessage());
    }

    @Test
    void moveToRootAndRenameConflict() {
        add(1, null, true, "A", 0);
        add(2, 1L, false, "a.pdf", 1);
        add(3, null, false, "b.pdf", 1);

        FileUpdateRequest toRoot = new FileUpdateRequest();
        toRoot.setFolderId(null);
        SpaceFileVO moved = service.update(2L, toRoot);
        assertNull(moved.getFolderId());
        verify(fileMapper).updateById(any(SpaceFile.class));

        FileUpdateRequest rename = new FileUpdateRequest();
        rename.setName("b.pdf");
        assertThrows(BizException.class, () -> service.update(2L, rename));
    }

    @Test
    void renameRejectsPathSeparators() {
        add(1, null, false, "a.pdf", 1);
        FileUpdateRequest req = new FileUpdateRequest();
        req.setName("x/y.pdf");

        assertThrows(BizException.class, () -> service.update(1L, req));
    }

    @Test
    void trashFolderMarksAliveContentsWithOneStamp() {
        add(1, null, true, "A", 0);
        add(2, 1L, false, "a.pdf", 1);
        add(3, 1L, false, "早删的.pdf", 1).setDeleteTime(LocalDateTime.now().minusDays(3));

        service.trash(1L);

        List<Object> params = updatedIds();
        assertTrue(params.contains(1L) && params.contains(2L));
        assertTrue(!params.contains(3L));
    }

    @Test
    void restoreBringsBackSameStampAndFallsBackToRoot() {
        LocalDateTime stamp = LocalDateTime.now().withNano(0);
        add(1, null, true, "父", 0).setDeleteTime(stamp.minusDays(1));
        add(2, 1L, true, "A", 0).setDeleteTime(stamp);
        add(3, 2L, false, "a.pdf", 1).setDeleteTime(stamp);
        add(4, 2L, false, "早删的.pdf", 1).setDeleteTime(stamp.minusDays(5));
        add(5, null, true, "A", 0);

        service.restore(2L);

        ArgumentCaptor<SpaceFile> saved = ArgumentCaptor.forClass(SpaceFile.class);
        verify(fileMapper).updateById(saved.capture());
        assertNull(saved.getValue().getFolderId());
        assertNull(saved.getValue().getDeleteTime());
        assertEquals("A (1)", saved.getValue().getName());
        List<Object> params = updatedIds();
        assertTrue(params.contains(3L));
        assertTrue(!params.contains(4L));
    }

    @Test
    void purgeFolderRemovesRowsAndStoredObjects() {
        add(1, null, true, "A", 0).setDeleteTime(LocalDateTime.now());
        add(2, 1L, false, "a.pdf", 1).setDeleteTime(LocalDateTime.now());
        add(3, null, false, "b.pdf", 1);

        service.purge(1L);

        verify(fileMapper).deleteByIds(List.of(1L, 2L));
        verify(sysFileService).deleteAll(List.of(1002L), true);
    }

    @Test
    void downloadChecksOwnerFolderAndTrash() {
        SpaceFile mine = add(1, null, false, "a.pdf", 1);
        SpaceFile folder = add(2, null, true, "A", 0);
        SpaceFile trashed = add(3, null, false, "b.pdf", 1);
        trashed.setDeleteTime(LocalDateTime.now());
        SpaceFile others = add(4, null, false, "c.pdf", 1);
        others.setUserId(7L);
        rows.forEach(f -> when(fileMapper.selectById(f.getId())).thenReturn(f));

        service.download(1L);
        verify(contents).single(mine);
        assertThrows(BizException.class, () -> service.download(2L));
        assertThrows(BizException.class, () -> service.download(3L));
        BizException e = assertThrows(BizException.class, () -> service.download(4L));
        assertEquals(HttpStatus.NOT_FOUND, e.getCode());
        verify(contents, never()).single(folder);
    }

    @Test
    void otherUsersFilesAreInvisible() {
        add(1, null, false, "a.pdf", 1).setUserId(7L);
        when(fileMapper.selectById(1L)).thenReturn(rows.get(0));

        BizException e = assertThrows(BizException.class, () -> service.get(1L));
        assertEquals(HttpStatus.NOT_FOUND, e.getCode());
    }

    @Test
    void uploadNameIsSanitized() {
        assertEquals("合同.pdf", SpaceFileServiceImpl.uploadName("C:\\Users\\me\\合同.pdf"));
        assertEquals("a.txt", SpaceFileServiceImpl.uploadName("dir/a\u0007.txt"));
        assertEquals("未命名文件", SpaceFileServiceImpl.uploadName("  "));
        String longName = "x".repeat(300) + ".pdf";
        String cut = SpaceFileServiceImpl.uploadName(longName);
        assertEquals(255, cut.length());
        assertTrue(cut.endsWith(".pdf"));
    }

    @Test
    void sizeFormatting() {
        assertEquals("512 B", SpaceFileServiceImpl.formatSize(512));
        assertEquals("1.5 MB", SpaceFileServiceImpl.formatSize(3 * MB / 2));
    }
}
