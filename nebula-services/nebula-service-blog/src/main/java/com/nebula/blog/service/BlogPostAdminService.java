package com.nebula.blog.service;

import com.nebula.blog.dto.admin.PostAdminPageQuery;
import com.nebula.blog.dto.admin.PostCreateRequest;
import com.nebula.blog.dto.admin.PostStatusUpdateRequest;
import com.nebula.blog.dto.admin.PostUpdateRequest;
import com.nebula.blog.vo.admin.PostAdminVO;
import com.nebula.blog.vo.admin.PostImportResultVO;
import com.nebula.common.core.domain.PageResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 后台文章管理服务接口
 */
public interface BlogPostAdminService {

    /**
     * 分页查询文章
     */
    PageResult<PostAdminVO> page(PostAdminPageQuery query);

    /**
     * 查看文章详情
     */
    PostAdminVO detail(Long id);

    /**
     * 新建文章
     */
    Long create(PostCreateRequest req);

    /**
     * 更新文章
     */
    void update(Long id, PostUpdateRequest req);

    /**
     * 删除文章
     */
    void delete(Long id);

    /**
     * 更新文章状态
     */
    void updateStatus(Long id, PostStatusUpdateRequest req);

    /**
     * 批量导入 Markdown 文件，为每个文件创建一篇文章。
     * <p>逐文件隔离：单个文件失败不影响其它文件，结果通过返回列表逐条反馈。
     *
     * @param files       上传的 .md / .markdown 文件
     * @param status      统一应用的状态（draft/published/archived），为空默认 draft
     * @param visibility  统一应用的可见性（public/private），为空默认 public
     * @param postType    内容类型（article/essay），为空默认 article
     * @param categoryIds 统一关联的分类 ID，可为空
     * @return 每个文件的导入结果
     */
    List<PostImportResultVO> importMarkdown(MultipartFile[] files, String status, String visibility,
                                            String postType, List<Long> categoryIds);
}
