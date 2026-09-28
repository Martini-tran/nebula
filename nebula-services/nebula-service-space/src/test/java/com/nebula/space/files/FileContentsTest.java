package com.nebula.space.files;

import com.nebula.common.core.exception.BizException;
import com.nebula.common.file.service.SysFileService;
import com.nebula.space.entity.SpaceFile;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileContentsTest {

    private final SysFileService sysFileService = mock(SysFileService.class);
    private final FileContents contents = new FileContents(sysFileService);
    private final List<SpaceFile> rows = new ArrayList<>();

    private SpaceFile add(long id, Long folderId, boolean folder, String name, String body) {
        SpaceFile f = new SpaceFile();
        f.setId(id);
        f.setFolderId(folderId);
        f.setIsFolder(folder ? 1 : 0);
        f.setName(name);
        f.setMime("application/octet-stream");
        if (!folder) {
            f.setSysFileId(1000 + id);
            f.setSizeBytes((long) body.getBytes(StandardCharsets.UTF_8).length);
            when(sysFileService.download(1000 + id)).thenAnswer(inv -> new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        }
        rows.add(f);
        return f;
    }

    private static Map<String, String> unzip(FileDownload d) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        d.body().writeTo(out);
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(out.toByteArray()), StandardCharsets.UTF_8)) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                entries.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    @Test
    void zipKeepsFolderLayoutAndSkipsTrash() throws IOException {
        SpaceFile root = add(1, null, true, "体检报告", null);
        add(2, 1L, false, "血常规.txt", "正常");
        add(3, 1L, true, "影像", null);
        add(4, 3L, false, "胸片.txt", "清晰");
        add(5, 1L, false, "旧的.txt", "删了").setDeleteTime(LocalDateTime.now());
        add(6, null, false, "别处.txt", "不在包里");

        FileDownload d = contents.zip(root, rows);

        assertEquals("体检报告.zip", d.name());
        assertEquals("application/zip", d.mime());
        assertNull(d.size());
        Map<String, String> entries = unzip(d);
        assertEquals(List.of("体检报告/", "体检报告/血常规.txt", "体检报告/影像/", "体检报告/影像/胸片.txt"), List.copyOf(entries.keySet()));
        assertEquals("清晰", entries.get("体检报告/影像/胸片.txt"));
    }

    @Test
    void singleOpensStreamEagerlyAndHidesJsonType() throws IOException {
        SpaceFile f = add(1, null, false, "config.json", "{\"code\":1}");
        f.setMime("application/json");

        FileDownload d = contents.single(f);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        d.body().writeTo(out);

        assertEquals("application/octet-stream", d.mime());
        assertEquals("{\"code\":1}", out.toString(StandardCharsets.UTF_8));
    }

    @Test
    void singleWithoutContentFails() {
        SpaceFile f = add(1, null, false, "a.txt", "x");
        f.setSysFileId(null);

        assertThrows(BizException.class, () -> contents.single(f));
    }

    @Test
    void kindsMatchFrontend() {
        assertEquals("pdf", FileKinds.kindOf("合同.PDF", ""));
        assertEquals("image", FileKinds.kindOf("scan", "image/png"));
        assertEquals("sheet", FileKinds.kindOf("账.xlsx", "application/octet-stream"));
        assertEquals("doc", FileKinds.kindOf("readme", "text/plain"));
        assertEquals("zip", FileKinds.kindOf("a.tar.gz", ""));
        assertEquals("other", FileKinds.kindOf("noext", ""));
    }
}
