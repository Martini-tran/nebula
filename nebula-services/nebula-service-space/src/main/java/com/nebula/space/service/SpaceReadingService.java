package com.nebula.space.service;

import com.nebula.space.dto.me.HighlightCreateRequest;
import com.nebula.space.dto.me.HighlightQuery;
import com.nebula.space.dto.me.HighlightSaveRequest;
import com.nebula.space.dto.me.ReadingCreateRequest;
import com.nebula.space.dto.me.ReadingQuery;
import com.nebula.space.dto.me.ReadingSaveRequest;
import com.nebula.space.search.SearchCriteria;
import com.nebula.space.vo.me.HighlightVO;
import com.nebula.space.vo.me.ReadingItemVO;

import java.util.Collection;
import java.util.List;

/**
 * 稍后读服务：文章与划线，只操作当前登录用户自己的数据
 *
 * <p>加入时抓取网页正文存档，原网址失效后阅读版仍可看；抓不到时只能打开原文。
 * 划线按「第几段、段内起止字符」定位，所以正文存档后不再重抓。</p>
 */
public interface SpaceReadingService {

    /**
     * 文章列表，新加入的在前；不带正文
     */
    List<ReadingItemVO> list(ReadingQuery query);

    /**
     * 全局搜索召回：标题 / 网址 / 摘要 / 存档正文命中，含归档的；content 只带命中词的段落（前端据此出上下文），新加入的在前
     */
    List<ReadingItemVO> search(SearchCriteria q, int limit);

    /**
     * 按 ID 取几篇，不带正文（搜索时补划线所属的文章）
     */
    List<ReadingItemVO> listByIds(Collection<Long> ids);

    /**
     * 单篇，带正文
     */
    ReadingItemVO get(Long id);

    /**
     * 加入：抓取正文存档。同一网址已经在了就返回原来那条（归档的放回队列，之前没抓到的再抓一次）
     */
    ReadingItemVO create(ReadingCreateRequest req);

    /**
     * 之前没抓到正文的再抓一次；已经存档的不重抓
     */
    ReadingItemVO refetch(Long id);

    /**
     * 改阅读状态、进度、位置、读后感、归档；不传的字段不动
     */
    ReadingItemVO update(Long id, ReadingSaveRequest req);

    /**
     * 删除文章和它的划线；转出的随手记、任务保留
     */
    void delete(Long id);

    /**
     * 划线，新的在前
     */
    List<HighlightVO> listHighlights(HighlightQuery query);

    /**
     * 全局搜索召回：划线原文或批注命中，新划的在前
     */
    List<HighlightVO> searchHighlights(SearchCriteria q, int limit);

    HighlightVO createHighlight(HighlightCreateRequest req);

    HighlightVO updateHighlight(Long id, HighlightSaveRequest req);

    void deleteHighlight(Long id);
}
