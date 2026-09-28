package com.nebula.space.service;

import com.nebula.space.dto.me.FileFolderCreateRequest;
import com.nebula.space.dto.me.FileQuery;
import com.nebula.space.dto.me.FileUpdateRequest;
import com.nebula.space.files.FileDownload;
import com.nebula.space.vo.me.FileUsageVO;
import com.nebula.space.vo.me.SpaceFileVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件柜服务：文件夹树、最近删除在 space_file，文件内容走公共文件存储
 */
public interface SpaceFileService {

    /**
     * 按视图列出文件；顺带彻底删除最近删除里超过 30 天的
     */
    List<SpaceFileVO> list(FileQuery query);

    SpaceFileVO get(Long id);

    FileUsageVO usage();

    SpaceFileVO createFolder(FileFolderCreateRequest req);

    /**
     * 上传到某个文件夹（空为根目录）；同名自动改名为「名字 (1).扩展名」
     */
    SpaceFileVO upload(MultipartFile file, Long folderId);

    /**
     * 重命名 / 移动
     */
    SpaceFileVO update(Long id, FileUpdateRequest req);

    /**
     * 移进最近删除；文件夹连同里面的一起
     */
    void trash(Long id);

    /**
     * 从最近删除恢复；原来的文件夹不在了就放回根目录
     */
    void restore(Long id);

    /**
     * 彻底删除，连同对象存储里的内容
     */
    void purge(Long id);

    FileDownload download(Long id);
}
