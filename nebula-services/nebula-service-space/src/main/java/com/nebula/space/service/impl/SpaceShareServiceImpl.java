package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.me.ShareCreateRequest;
import com.nebula.space.dto.me.ShareDownloadRequest;
import com.nebula.space.entity.SpaceFile;
import com.nebula.space.entity.SpaceShare;
import com.nebula.space.files.FileContents;
import com.nebula.space.files.FileDownload;
import com.nebula.space.files.FileTree;
import com.nebula.space.files.ShareAttemptLimiter;
import com.nebula.space.mapper.SpaceFileMapper;
import com.nebula.space.mapper.SpaceShareMapper;
import com.nebula.space.mapper.SpaceUserMapper;
import com.nebula.space.service.SpaceShareService;
import com.nebula.space.vo.me.SharePublicVO;
import com.nebula.space.vo.me.ShareVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 文件分享链接服务实现
 *
 * <p>链接 /s/{code} 指向 space 自己的分享页，下载时服务端校验提取码、有效期、次数后把内容转给对方，
 * 不暴露对象存储地址。提取码存原文，分享人在「我分享的」里能再次复制；试错次数由 {@link ShareAttemptLimiter} 限制。</p>
 */
@Service
@RequiredArgsConstructor
public class SpaceShareServiceImpl implements SpaceShareService {

    /**
     * 短码去掉了容易看错的 0O1Il，区分大小写（code 列用 utf8mb4_bin）
     */
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";

    /**
     * 提取码只用小写和数字，口头转述不用分大小写
     */
    private static final String PASSWORD_CHARS = "abcdefghjkmnpqrstuvwxyz23456789";

    private static final int CODE_LENGTH = 6;
    private static final int PASSWORD_LENGTH = 4;
    private static final int CODE_ATTEMPTS = 5;
    private static final String DEFAULT_OWNER = "一位朋友";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SpaceShareMapper shareMapper;
    private final SpaceFileMapper fileMapper;
    private final SpaceUserMapper userMapper;
    private final FileContents contents;
    private final ShareAttemptLimiter limiter;

    @Override
    public List<ShareVO> list() {
        Long userId = requireUserId();
        List<SpaceShare> shares = shareMapper.selectList(new LambdaQueryWrapper<SpaceShare>().eq(SpaceShare::getUserId, userId));
        List<Long> fileIds = shares.stream().map(SpaceShare::getFileId).distinct().toList();
        Map<Long, String> names = fileIds.isEmpty() ? Map.of() : fileMapper.selectByIds(fileIds).stream()
                .filter(f -> userId.equals(f.getUserId()))
                .collect(Collectors.toMap(SpaceFile::getId, SpaceFile::getName));
        LocalDateTime now = LocalDateTime.now();
        return shares.stream()
                .sorted(Comparator.comparing((SpaceShare s) -> stateOf(s, now) != null)
                        .thenComparing(SpaceShare::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(SpaceShare::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(s -> toVO(s, names.getOrDefault(s.getFileId(), s.getFileName())))
                .toList();
    }

    @Override
    public ShareVO create(ShareCreateRequest req) {
        Long userId = requireUserId();
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        SpaceFile f = req.getFileId() == null ? null : fileMapper.selectById(req.getFileId());
        if (f == null || !userId.equals(f.getUserId()) || !FileTree.alive(f)) {
            throw new BizException(HttpStatus.NOT_FOUND, "文件不存在或已删除");
        }
        SpaceShare s = new SpaceShare();
        s.setUserId(userId);
        s.setFileId(f.getId());
        s.setFileName(f.getName());
        s.setIsFolder(FileTree.isFolder(f) ? 1 : 0);
        s.setPassword(req.isWithPassword() ? random(PASSWORD_CHARS, PASSWORD_LENGTH) : null);
        s.setExpireAt(req.getDays() == null ? null : LocalDate.now().plusDays(req.getDays()).atTime(LocalTime.of(23, 59, 59)));
        s.setMaxDownloads(req.getMaxDownloads());
        s.setDownloads(0);
        s.setRevoked(0);
        for (int attempt = 1; ; attempt++) {
            s.setCode(random(CODE_CHARS, CODE_LENGTH));
            try {
                shareMapper.insert(s);
                return toVO(s, f.getName());
            } catch (DuplicateKeyException e) {
                s.setId(null);
                if (attempt >= CODE_ATTEMPTS) {
                    throw new BizException(HttpStatus.CONFLICT, "生成链接失败，请再试一次");
                }
            }
        }
    }

    @Override
    public void revoke(Long id) {
        Long userId = requireUserId();
        SpaceShare s = id == null ? null : shareMapper.selectById(id);
        if (s == null || !userId.equals(s.getUserId())) {
            throw new BizException(HttpStatus.NOT_FOUND, "分享不存在");
        }
        if (Objects.equals(s.getRevoked(), 1)) {
            return;
        }
        s.setRevoked(1);
        s.setUpdateTime(LocalDateTime.now());
        shareMapper.updateById(s);
    }

    @Override
    public SharePublicVO publicInfo(String code) {
        SpaceShare s = requireShare(code);
        boolean folder = Objects.equals(s.getIsFolder(), 1);
        SpaceFile f;
        long size;
        if (folder) {
            List<SpaceFile> rows = ownerFiles(s.getUserId());
            f = FileTree.find(rows, s.getFileId());
            size = f == null ? 0 : FileTree.descendants(rows, f.getId()).stream()
                    .filter(d -> FileTree.alive(d) && !FileTree.isFolder(d))
                    .mapToLong(d -> d.getSizeBytes() == null ? 0 : d.getSizeBytes())
                    .sum();
        } else {
            f = fileMapper.selectById(s.getFileId());
            size = f == null || f.getSizeBytes() == null ? 0 : f.getSizeBytes();
        }
        boolean alive = f != null && Objects.equals(f.getUserId(), s.getUserId()) && FileTree.alive(f);
        String state = stateOf(s, LocalDateTime.now());

        SharePublicVO vo = new SharePublicVO();
        vo.setCode(s.getCode());
        vo.setFileName(alive ? f.getName() : s.getFileName());
        vo.setIsFolder(folder);
        vo.setSize(BigDecimal.valueOf(alive ? size : 0));
        vo.setNeedPassword(StringUtils.hasText(s.getPassword()));
        vo.setExpireAt(s.getExpireAt());
        vo.setUnavailable(state != null ? state : alive ? null : "文件已被删除");
        String owner = userMapper.displayName(s.getUserId());
        vo.setOwner(StringUtils.hasText(owner) ? owner : DEFAULT_OWNER);
        return vo;
    }

    @Override
    public FileDownload download(String code, ShareDownloadRequest req) {
        SpaceShare s = requireShare(code);
        String state = stateOf(s, LocalDateTime.now());
        if (state != null) {
            throw new BizException(HttpStatus.BAD_REQUEST, state);
        }
        if (StringUtils.hasText(s.getPassword())) {
            if (limiter.locked(s.getCode())) {
                throw new BizException(HttpStatus.TOO_MANY_REQUESTS, "提取码输错太多次了，15 分钟后再试");
            }
            String input = req == null || req.getPassword() == null ? "" : req.getPassword().trim();
            if (!s.getPassword().equalsIgnoreCase(input)) {
                limiter.fail(s.getCode());
                throw new BizException(HttpStatus.BAD_REQUEST, "提取码不对");
            }
            limiter.clear(s.getCode());
        }
        SpaceFile f = fileMapper.selectById(s.getFileId());
        if (f == null || !Objects.equals(f.getUserId(), s.getUserId()) || !FileTree.alive(f)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "文件已被删除");
        }
        // 先占一次下载名额（条件更新，并发下也不会超过上限），取不到内容再退回去
        if (!countDownload(s.getId())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "已达下载次数上限");
        }
        try {
            return FileTree.isFolder(f) ? contents.zip(f, ownerFiles(s.getUserId())) : contents.single(f);
        } catch (RuntimeException e) {
            shareMapper.update(null, new LambdaUpdateWrapper<SpaceShare>()
                    .setSql("downloads = downloads - 1")
                    .eq(SpaceShare::getId, s.getId())
                    .gt(SpaceShare::getDownloads, 0));
            throw e;
        }
    }

    // ----------------------------------------------------------------- 内部工具

    private boolean countDownload(Long id) {
        return shareMapper.update(null, new LambdaUpdateWrapper<SpaceShare>()
                .setSql("downloads = downloads + 1")
                .eq(SpaceShare::getId, id)
                .eq(SpaceShare::getRevoked, 0)
                .and(w -> w.isNull(SpaceShare::getMaxDownloads).or().apply("downloads < max_downloads"))) > 0;
    }

    private SpaceShare requireShare(String code) {
        SpaceShare s = !StringUtils.hasText(code) || code.length() > 16 ? null
                : shareMapper.selectOne(new LambdaQueryWrapper<SpaceShare>().eq(SpaceShare::getCode, code));
        if (s == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "链接不存在，可能输错了");
        }
        return s;
    }

    private List<SpaceFile> ownerFiles(Long userId) {
        return fileMapper.selectList(new LambdaQueryWrapper<SpaceFile>().eq(SpaceFile::getUserId, userId));
    }

    /**
     * 不能用的原因；能用返回 null
     */
    static String stateOf(SpaceShare s, LocalDateTime now) {
        if (Objects.equals(s.getRevoked(), 1)) {
            return "分享已被取消";
        }
        if (s.getExpireAt() != null && s.getExpireAt().isBefore(now)) {
            return "分享已过期";
        }
        if (s.getMaxDownloads() != null && s.getDownloads() != null && s.getDownloads() >= s.getMaxDownloads()) {
            return "已达下载次数上限";
        }
        return null;
    }

    private static String random(String chars, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static ShareVO toVO(SpaceShare s, String fileName) {
        ShareVO vo = new ShareVO();
        vo.setId(s.getId());
        vo.setCode(s.getCode());
        vo.setFileId(s.getFileId());
        vo.setFileName(fileName);
        vo.setIsFolder(Objects.equals(s.getIsFolder(), 1));
        vo.setPassword(s.getPassword());
        vo.setExpireAt(s.getExpireAt());
        vo.setMaxDownloads(s.getMaxDownloads());
        vo.setDownloads(s.getDownloads() == null ? 0 : s.getDownloads());
        vo.setRevoked(Objects.equals(s.getRevoked(), 1));
        vo.setCreateTime(s.getCreateTime());
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
