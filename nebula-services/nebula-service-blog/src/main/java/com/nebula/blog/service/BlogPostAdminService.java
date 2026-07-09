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
import java.util.Set;

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
     * 异步批量导入 Markdown 文件，为每个文件创建一篇文章。
     * <p>请求线程内仅校验参数、读取文件字节并落库一条导入任务记录，随后交由后台线程池逐文件处理，
     * 立即返回任务 ID。前端通过任务 ID 轮询导入进度与逐文件明细。
     *
     * @param files       上传的 .md / .markdown 文件
     * @param status      统一应用的状态（draft/published/archived），为空默认 draft
     * @param visibility  统一应用的可见性（public/private），为空默认 public
     * @param postType    内容类型（article/essay），为空默认 article
     * @param categoryIds 统一关联的分类 ID，可为空
     * @param rehostImages 是否下载正文中的外链图片并转存到公开桶后替换 URL
     * @return 导入任务 ID
     */
    Long importMarkdown(MultipartFile[] files, String status, String visibility,
                        String postType, List<Long> categoryIds, boolean rehostImages);

    /**
     * 导入单个 Markdown 文件（供异步执行器逐文件调用，复用解析/转存/建文章逻辑）。
     * <p>逐文件隔离：单个文件失败仅在返回结果中记录错误，不抛出。
     *
     * @param bytes        文件字节（已在请求线程读入内存）
     * @param filename     原始文件名
     * @param status       已归一化的文章状态
     * @param visibility   已归一化的可见性
     * @param postType     已归一化的内容类型
     * @param categoryIds  统一关联的分类 ID，可为空
     * @param rehostImages 是否转存外链图片
     * @param usedSlugs    本批已分配的 slug 集合（跨文件去重，方法内会写入）
     * @return 单文件导入结果
     */
    PostImportResultVO importSingleMarkdown(byte[] bytes, String filename, String status, String visibility,
                                            String postType, List<Long> categoryIds, boolean rehostImages,
                                            Set<String> usedSlugs);
}
