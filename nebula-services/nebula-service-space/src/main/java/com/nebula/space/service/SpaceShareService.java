package com.nebula.space.service;

import com.nebula.space.dto.me.ShareCreateRequest;
import com.nebula.space.dto.me.ShareDownloadRequest;
import com.nebula.space.files.FileDownload;
import com.nebula.space.vo.me.SharePublicVO;
import com.nebula.space.vo.me.ShareVO;

import java.util.List;

/**
 * 文件分享链接：链接指向 space 自己的下载页，才能撤销、计数、校验提取码
 */
public interface SpaceShareService {

    /**
     * 我分享的，还能用的在前
     */
    List<ShareVO> list();

    ShareVO create(ShareCreateRequest req);

    /**
     * 让链接失效
     */
    void revoke(Long id);

    /**
     * 分享页看到的信息（不需要登录）
     */
    SharePublicVO publicInfo(String code);

    /**
     * 分享页下载：校验提取码、有效期、次数，记一次下载；文件夹打成 zip
     */
    FileDownload download(String code, ShareDownloadRequest req);
}
