package com.nebula.scribe.dto;

import lombok.Data;

import java.util.List;

/**
 * 整棵目录树重排请求
 *
 * <p>有卷的作品传 {@code volumes}（卷的顺序 + 每卷的章节顺序，可跨卷移动）；
 * 无卷的作品传 {@code chapterIds}。都必须恰好覆盖本作品的全部卷与章节。</p>
 */
@Data
public class TocSortRequest {

    /**
     * 有卷时：按新顺序排列的卷，每卷带其章节的新顺序
     */
    private List<VolumeNode> volumes;

    /**
     * 无卷时：按新顺序排列的全部章节
     */
    private List<Long> chapterIds;

    @Data
    public static class VolumeNode {

        private Long id;

        private List<Long> chapterIds;
    }
}
