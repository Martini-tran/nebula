package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import com.nebula.space.files.FileDownload;
import com.nebula.space.files.FileKinds;
import com.nebula.space.files.FileTree;
import com.nebula.space.files.SpaceFileProperties;
import com.nebula.space.mapper.SpaceFileMapper;
import com.nebula.space.service.SpaceFileService;
import com.nebula.space.vo.me.FileUsageVO;
import com.nebula.space.vo.me.SpaceFileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 文件柜服务实现
 *
 * <p>文件夹树、名字、最近删除都在 space_file；文件内容交给公共文件组件（sys_file + MinIO，私有，
 * 键前缀 space/{userId}/）。彻底删除时连同 sys_file 和对象一起删。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpaceFileServiceImpl implements SpaceFileService {

    /**
     * 最近删除保留天数
     */
    static final int TRASH_DAYS = 30;

    /**
     * 在公共文件表 sys_file 里的业务类型
     */
    static final String TARGET_TYPE = "space_file";

    private static final String SOURCE_UPLOAD = "upload";
    private static final int RECENT_LIMIT = 30;
    private static final int MAX_NAME = 255;

    /**
     * 文件夹在前，其余按修改时间倒序
     */
    private static final Comparator<SpaceFile> FOLDERS_FIRST = Comparator
            .comparing((SpaceFile f) -> !FileTree.isFolder(f))
            .thenComparing(SpaceFile::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(SpaceFile::getId, Comparator.nullsLast(Comparator.reverseOrder()));

    private final SpaceFileMapper fileMapper;
    private final SysFileService sysFileService;
    private final FileContents contents;
    private final SpaceFileProperties properties;
    private final FileProperties fileProperties;

    @Override
    public List<SpaceFileVO> list(FileQuery query) {
        FileQuery q = query == null ? new FileQuery() : query;
        List<SpaceFile> rows = sweep(loadAll(requireUserId()));
        String view = StringUtils.hasText(q.getView()) ? q.getView() : "folder";
        if ("trash".equals(view)) {
            // 最近删除只列「被删的那一层」，文件夹里的跟着文件夹走
            return rows.stream()
                    .filter(f -> !FileTree.alive(f) && !trashedWithParent(rows, f))
                    .sorted(Comparator.comparing(SpaceFile::getDeleteTime).reversed()
                            .thenComparing(SpaceFile::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                    .map(SpaceFileServiceImpl::toVO)
                    .toList();
        }
        Stream<SpaceFile> alive = rows.stream().filter(FileTree::alive);
        if ("recent".equals(view)) {
            return alive.filter(f -> !FileTree.isFolder(f))
                    .sorted(FOLDERS_FIRST)
                    .limit(RECENT_LIMIT)
                    .map(SpaceFileServiceImpl::toVO)
                    .toList();
        }
        Stream<SpaceFile> picked = switch (view) {
            case "kind" -> alive.filter(f -> !FileTree.isFolder(f) && FileKinds.kindOf(f.getName(), f.getMime()).equals(q.getKind()));
            case "source" -> alive.filter(f -> Objects.equals(f.getSource(), q.getSource()));
            case "all" -> alive;
            default -> alive.filter(f -> SOURCE_UPLOAD.equals(f.getSource()) && Objects.equals(f.getFolderId(), q.getFolderId()));
        };
        return picked.sorted(FOLDERS_FIRST).map(SpaceFileServiceImpl::toVO).toList();
    }

    @Override
    public SpaceFileVO get(Long id) {
        return toVO(requireFile(requireUserId(), id));
    }

    @Override
    public FileUsageVO usage() {
        Map<String, Long> byKind = new LinkedHashMap<>();
        FileKinds.ALL.forEach(k -> byKind.put(k, 0L));
        for (SpaceFile f : loadAll(requireUserId())) {
            if (!FileTree.isFolder(f)) {
                byKind.merge(FileKinds.kindOf(f.getName(), f.getMime()), sizeOf(f), Long::sum);
            }
        }
        FileUsageVO vo = new FileUsageVO();
        vo.setUsed(BigDecimal.valueOf(byKind.values().stream().mapToLong(Long::longValue).sum()));
        vo.setTotal(BigDecimal.valueOf(properties.getQuota().toBytes()));
        vo.setMaxFileSize(BigDecimal.valueOf(fileProperties.getMaxFileSize()));
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        byKind.forEach((k, v) -> out.put(k, BigDecimal.valueOf(v)));
        vo.setByKind(out);
        return vo;
    }

    @Override
    public SpaceFileVO createFolder(FileFolderCreateRequest req) {
        Long userId = requireUserId();
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        String name = cleanName(req.getName());
        List<SpaceFile> rows = loadAll(userId);
        Long parent = requireTargetFolder(rows, req.getFolderId());
        if (nameTaken(rows, parent, true, name, null)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "这里已经有同名文件夹");
        }
        SpaceFile f = new SpaceFile();
        f.setUserId(userId);
        f.setFolderId(parent);
        f.setIsFolder(1);
        f.setName(name);
        f.setSizeBytes(0L);
        f.setMime("");
        f.setSource(SOURCE_UPLOAD);
        fileMapper.insert(f);
        return toVO(f);
    }

    @Override
    public SpaceFileVO upload(MultipartFile file, Long folderId) {
        Long userId = requireUserId();
        if (file == null || file.isEmpty()) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请选择要上传的文件（不能是空文件）");
        }
        List<SpaceFile> rows = loadAll(userId);
        Long parent = requireTargetFolder(rows, folderId);
        long used = rows.stream().filter(f -> !FileTree.isFolder(f)).mapToLong(SpaceFileServiceImpl::sizeOf).sum();
        long quota = properties.getQuota().toBytes();
        if (used + file.getSize() > quota) {
            throw new BizException(HttpStatus.BAD_REQUEST, "空间不够了：还剩 " + formatSize(Math.max(0, quota - used))
                    + "，这个文件 " + formatSize(file.getSize()) + "。清理一些文件或清空最近删除后再试");
        }
        String name = uniqueName(rows, parent, false, uploadName(file.getOriginalFilename()));

        FileUploadRequest req = new FileUploadRequest();
        req.setTargetType(TARGET_TYPE);
        req.setIsPublic(0);
        req.setPrefix("space/" + userId);
        FileInfoVO stored = sysFileService.upload(file, req);

        SpaceFile f = new SpaceFile();
        f.setUserId(userId);
        f.setFolderId(parent);
        f.setIsFolder(0);
        f.setName(name);
        f.setSizeBytes(stored.getSizeBytes() != null ? stored.getSizeBytes() : file.getSize());
        f.setMime(StringUtils.hasText(stored.getMimeType()) ? stored.getMimeType() : "application/octet-stream");
        f.setSource(SOURCE_UPLOAD);
        f.setSysFileId(stored.getId());
        try {
            fileMapper.insert(f);
            FileBindRequest bind = new FileBindRequest();
            bind.setTargetType(TARGET_TYPE);
            bind.setTargetId(f.getId());
            sysFileService.bind(stored.getId(), bind);
        } catch (RuntimeException e) {
            // 已经进了对象存储，这边没记上就删掉，免得占着空间没人认领
            discard(f, stored.getId());
            throw e;
        }
        return toVO(f);
    }

    @Override
    public SpaceFileVO update(Long id, FileUpdateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        List<SpaceFile> rows = loadAll(requireUserId());
        SpaceFile f = requireIn(rows, id);
        if (!FileTree.alive(f)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "它在最近删除里，恢复后才能改");
        }
        String name = req.has("name") ? cleanName(req.getName()) : f.getName();
        Long folder = f.getFolderId();
        if (req.has("folderId") && !Objects.equals(req.getFolderId(), f.getFolderId())) {
            if (!SOURCE_UPLOAD.equals(f.getSource())) {
                throw new BizException(HttpStatus.BAD_REQUEST, "其他模块的附件不能移动");
            }
            Long target = req.getFolderId();
            if (target != null && FileTree.isFolder(f) && (target.equals(f.getId())
                    || FileTree.descendants(rows, f.getId()).stream().anyMatch(d -> target.equals(d.getId())))) {
                throw new BizException(HttpStatus.BAD_REQUEST, "不能移到它自己里面");
            }
            folder = requireTargetFolder(rows, target);
        }
        if (name.equals(f.getName()) && Objects.equals(folder, f.getFolderId())) {
            return toVO(f);
        }
        if (nameTaken(rows, folder, FileTree.isFolder(f), name, f.getId())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "已经有叫「" + name + "」的" + (FileTree.isFolder(f) ? "文件夹" : "文件") + "了");
        }
        f.setName(name);
        f.setFolderId(folder);
        // 审计填充是严格模式，已有值不会覆盖，这里显式刷新
        f.setUpdateTime(LocalDateTime.now());
        fileMapper.updateById(f);
        return toVO(f);
    }

    @Override
    public void trash(Long id) {
        Long userId = requireUserId();
        List<SpaceFile> rows = loadAll(userId);
        SpaceFile f = requireIn(rows, id);
        if (!FileTree.alive(f)) {
            return;
        }
        // 同一次删除记同一个时间（到秒，和库里存的一致），恢复时靠它认出哪些是一起删的
        LocalDateTime stamp = LocalDateTime.now().withNano(0);
        List<Long> ids = FileTree.withDescendants(rows, f).stream()
                .filter(FileTree::alive)
                .map(SpaceFile::getId)
                .toList();
        fileMapper.update(null, new LambdaUpdateWrapper<SpaceFile>()
                .set(SpaceFile::getDeleteTime, stamp)
                .eq(SpaceFile::getUserId, userId)
                .in(SpaceFile::getId, ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restore(Long id) {
        Long userId = requireUserId();
        List<SpaceFile> rows = loadAll(userId);
        SpaceFile f = requireIn(rows, id);
        if (FileTree.alive(f)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "它不在最近删除里");
        }
        LocalDateTime stamp = f.getDeleteTime();
        SpaceFile parent = FileTree.find(rows, f.getFolderId());
        Long folder = parent != null && FileTree.alive(parent) ? parent.getId() : null;
        List<Long> inside = FileTree.descendants(rows, f.getId()).stream()
                .filter(d -> stamp.equals(d.getDeleteTime()))
                .map(SpaceFile::getId)
                .toList();
        // 删掉之后又建了同名的，恢复回来的改个名
        f.setName(uniqueName(rows, folder, FileTree.isFolder(f), f.getName(), f.getId()));
        f.setFolderId(folder);
        f.setDeleteTime(null);
        f.setUpdateTime(LocalDateTime.now());
        fileMapper.updateById(f);
        if (!inside.isEmpty()) {
            fileMapper.update(null, new LambdaUpdateWrapper<SpaceFile>()
                    .set(SpaceFile::getDeleteTime, null)
                    .eq(SpaceFile::getUserId, userId)
                    .in(SpaceFile::getId, inside));
        }
    }

    @Override
    public void purge(Long id) {
        List<SpaceFile> rows = loadAll(requireUserId());
        purgeRows(FileTree.withDescendants(rows, requireIn(rows, id)));
    }

    @Override
    public FileDownload download(Long id) {
        SpaceFile f = requireFile(requireUserId(), id);
        if (FileTree.isFolder(f)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "文件夹不能直接下载，打开后逐个下载");
        }
        if (!FileTree.alive(f)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "它在最近删除里，恢复后才能下载");
        }
        return contents.single(f);
    }

    // ----------------------------------------------------------------- 内部工具

    private List<SpaceFile> loadAll(Long userId) {
        return fileMapper.selectList(new LambdaQueryWrapper<SpaceFile>().eq(SpaceFile::getUserId, userId));
    }

    /**
     * 最近删除超过 30 天的彻底删掉，返回剩下的
     */
    private List<SpaceFile> sweep(List<SpaceFile> rows) {
        LocalDate cutoff = LocalDate.now().minusDays(TRASH_DAYS);
        Set<Long> gone = new HashSet<>();
        List<SpaceFile> expired = new ArrayList<>();
        rows.stream()
                .filter(f -> f.getDeleteTime() != null && f.getDeleteTime().toLocalDate().isBefore(cutoff))
                .flatMap(f -> FileTree.withDescendants(rows, f).stream())
                .filter(f -> gone.add(f.getId()))
                .forEach(expired::add);
        if (expired.isEmpty()) {
            return rows;
        }
        purgeRows(expired);
        return rows.stream().filter(f -> !gone.contains(f.getId())).toList();
    }

    /**
     * 先删自己的记录，再删公共文件和对象；对象删失败只留下孤儿对象，不会出现点了打不开的文件
     */
    private void purgeRows(List<SpaceFile> items) {
        if (items.isEmpty()) {
            return;
        }
        fileMapper.deleteByIds(items.stream().map(SpaceFile::getId).toList());
        List<Long> sysIds = items.stream().map(SpaceFile::getSysFileId).filter(Objects::nonNull).toList();
        if (sysIds.isEmpty()) {
            return;
        }
        try {
            sysFileService.deleteAll(sysIds, true);
        } catch (RuntimeException e) {
            log.warn("彻底删除时清理公共文件失败, sysFileIds={}", sysIds, e);
        }
    }

    private void discard(SpaceFile f, Long sysFileId) {
        try {
            if (f.getId() != null) {
                fileMapper.deleteById(f.getId());
            }
            sysFileService.delete(sysFileId, true);
        } catch (RuntimeException e) {
            log.warn("上传失败后清理失败, sysFileId={}", sysFileId, e);
        }
    }

    /**
     * 父文件夹也在同一次删除里：它跟着父文件夹走，不单独列在最近删除
     */
    private static boolean trashedWithParent(List<SpaceFile> rows, SpaceFile f) {
        SpaceFile parent = FileTree.find(rows, f.getFolderId());
        return parent != null && Objects.equals(parent.getDeleteTime(), f.getDeleteTime());
    }

    private SpaceFile requireFile(Long userId, Long id) {
        SpaceFile f = id == null ? null : fileMapper.selectById(id);
        if (f == null || !Objects.equals(f.getUserId(), userId)) {
            throw new BizException(HttpStatus.NOT_FOUND, "文件不存在或已删除");
        }
        return f;
    }

    private static SpaceFile requireIn(List<SpaceFile> rows, Long id) {
        SpaceFile f = FileTree.find(rows, id);
        if (f == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "文件不存在或已删除");
        }
        return f;
    }

    /**
     * 目标文件夹：空为根目录，否则必须是自己的、没删的、自己建的文件夹
     */
    private static Long requireTargetFolder(List<SpaceFile> rows, Long folderId) {
        if (folderId == null) {
            return null;
        }
        SpaceFile p = FileTree.find(rows, folderId);
        if (p == null || !FileTree.alive(p) || !FileTree.isFolder(p) || !SOURCE_UPLOAD.equals(p.getSource())) {
            throw new BizException(HttpStatus.NOT_FOUND, "文件夹不存在或已删除");
        }
        return p.getId();
    }

    private static boolean nameTaken(List<SpaceFile> rows, Long folderId, boolean folder, String name, Long exceptId) {
        return rows.stream().anyMatch(f -> FileTree.alive(f)
                && SOURCE_UPLOAD.equals(f.getSource())
                && FileTree.isFolder(f) == folder
                && Objects.equals(f.getFolderId(), folderId)
                && name.equals(f.getName())
                && !Objects.equals(f.getId(), exceptId));
    }

    private static String uniqueName(List<SpaceFile> rows, Long folderId, boolean folder, String name) {
        return uniqueName(rows, folderId, folder, name, null);
    }

    /**
     * 同名就加序号：合同.pdf → 合同 (1).pdf
     */
    private static String uniqueName(List<SpaceFile> rows, Long folderId, boolean folder, String name, Long exceptId) {
        if (!nameTaken(rows, folderId, folder, name, exceptId)) {
            return name;
        }
        int dot = folder ? -1 : name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; ; i++) {
            String candidate = base + " (" + i + ")" + ext;
            if (!nameTaken(rows, folderId, folder, candidate, exceptId)) {
                return candidate;
            }
        }
    }

    /**
     * 用户起的名字：不能空、不能带路径分隔符
     */
    static String cleanName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw new BizException(HttpStatus.BAD_REQUEST, "名称不能为空");
        }
        if (name.length() > MAX_NAME) {
            throw new BizException(HttpStatus.BAD_REQUEST, "名称最长 255 字");
        }
        if (name.contains("/") || name.contains("\\")) {
            throw new BizException(HttpStatus.BAD_REQUEST, "名称里不能有 / 或 \\");
        }
        if (".".equals(name) || "..".equals(name) || name.chars().anyMatch(Character::isISOControl)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "名称不合法");
        }
        return name;
    }

    /**
     * 上传的文件名：有的浏览器会带上本机路径，只留最后一段；太长的保留扩展名截断
     */
    static String uploadName(String original) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.codePoints()
                .filter(c -> !Character.isISOControl(c))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString()
                .trim();
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            return "未命名文件";
        }
        if (name.length() > MAX_NAME) {
            int dot = name.lastIndexOf('.');
            String ext = dot > 0 && name.length() - dot <= 16 ? name.substring(dot) : "";
            name = name.substring(0, MAX_NAME - ext.length()) + ext;
        }
        return name;
    }

    static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024) {
            return Math.round(bytes / 1024.0) + " KB";
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static long sizeOf(SpaceFile f) {
        return f.getSizeBytes() == null ? 0L : f.getSizeBytes();
    }

    private static SpaceFileVO toVO(SpaceFile f) {
        SpaceFileVO vo = new SpaceFileVO();
        vo.setId(f.getId());
        vo.setFolderId(f.getFolderId());
        vo.setIsFolder(FileTree.isFolder(f));
        vo.setName(f.getName());
        vo.setSize(BigDecimal.valueOf(sizeOf(f)));
        vo.setMime(f.getMime());
        vo.setSource(f.getSource());
        vo.setDeleteTime(f.getDeleteTime());
        vo.setCreateTime(f.getCreateTime());
        vo.setUpdateTime(f.getUpdateTime());
        return vo;
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "未获取到当前用户");
        }
        return userId;
    }
}
