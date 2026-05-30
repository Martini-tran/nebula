package com.nebula.common.file.service;

import com.nebula.common.core.domain.PageResult;
import com.nebula.common.file.dto.FileBindRequest;
import com.nebula.common.file.dto.FilePageQuery;
import com.nebula.common.file.dto.FileUploadRequest;
import com.nebula.common.file.vo.FileInfoVO;
import com.nebula.system.entity.SysFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * 系统文件服务接口
 * <p>
 * 公共文件能力封装：
 * <ul>
 *   <li>上传文件到对象存储并落库 sys_file</li>
 *   <li>查询文件元信息 / 业务文件列表 / 分页</li>
 *   <li>获取访问URL（公开URL或临时签名URL）</li>
 *   <li>下载文件流</li>
 *   <li>删除文件（逻辑/物理）</li>
 *   <li>将已上传文件绑定到业务实体</li>
 * </ul>
 *
 * @author nebula
 */
public interface SysFileService {

    /**
     * 上传文件
     * 上传到对象存储后写入 sys_file 表，并返回文件元信息
     *
     * @param file    上传文件
     * @param request 上传请求参数（业务关联、文件用途、桶等）
     * @return 文件元信息
     */
    FileInfoVO upload(MultipartFile file, FileUploadRequest request);

    /**
     * 上传文件（字节数组）
     *
     * @param bytes            文件字节数组
     * @param originalFilename 原始文件名
     * @param contentType      MIME 类型
     * @param request          上传请求参数
     * @return 文件元信息
     */
    FileInfoVO upload(byte[] bytes, String originalFilename, String contentType, FileUploadRequest request);

    /**
     * 根据ID获取文件元信息
     *
     * @param id 文件ID
     * @return 文件元信息（包含访问URL）
     */
    FileInfoVO getById(Long id);

    /**
     * 根据ID获取文件原始记录（不包含动态URL）
     *
     * @param id 文件ID
     * @return SysFile 实体
     */
    SysFile getEntityById(Long id);

    /**
     * 根据业务关联查询文件列表（按 sort_order 升序、create_time 升序）
     *
     * @param targetType 业务类型
     * @param targetId   业务ID
     * @return 文件列表
     */
    List<FileInfoVO> listByTarget(String targetType, Long targetId);

    /**
     * 根据业务关联+文件用途查询文件列表
     *
     * @param targetType 业务类型
     * @param targetId   业务ID
     * @param fileType   文件用途
     * @return 文件列表
     */
    List<FileInfoVO> listByTarget(String targetType, Long targetId, String fileType);

    /**
     * 分页查询文件列表
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<FileInfoVO> page(FilePageQuery query);

    /**
     * 获取文件访问URL
     * 公开文件返回永久URL，私有文件返回基于默认有效期的临时签名URL
     *
     * @param id 文件ID
     * @return 访问URL
     */
    String getAccessUrl(Long id);

    /**
     * 获取临时签名URL
     * 不论是否公开均返回临时签名URL，可用于下载或预览限时分享
     *
     * @param id            文件ID
     * @param expirySeconds 有效期（秒），传 null 或 &lt;=0 时使用默认值
     * @return 临时URL
     */
    String getPresignedUrl(Long id, Integer expirySeconds);

    /**
     * 批量获取文件访问URL
     *
     * @param ids 文件ID列表
     * @return 文件元信息列表（含 url 字段）
     */
    List<FileInfoVO> listByIds(List<Long> ids);

    /**
     * 获取文件下载流
     * 调用方负责关闭流
     *
     * @param id 文件ID
     * @return 文件输入流
     */
    InputStream download(Long id);

    /**
     * 将文件绑定到业务实体（用于上传时未确定 targetId 的场景）
     *
     * @param id      文件ID
     * @param request 绑定参数
     */
    void bind(Long id, FileBindRequest request);

    /**
     * 批量绑定文件到业务实体
     *
     * @param ids     文件ID列表
     * @param request 绑定参数
     */
    void bindAll(List<Long> ids, FileBindRequest request);

    /**
     * 解除文件与业务实体的绑定（设置 target 为空，状态保持正常）
     *
     * @param id 文件ID
     */
    void unbind(Long id);

    /**
     * 删除文件
     * 默认软删除：sys_file 状态置为 0
     *
     * @param id            文件ID
     * @param removeStorage 是否同时从对象存储中删除
     */
    void delete(Long id, boolean removeStorage);

    /**
     * 批量删除文件
     *
     * @param ids           文件ID列表
     * @param removeStorage 是否同时从对象存储中删除
     */
    void deleteAll(List<Long> ids, boolean removeStorage);
}
