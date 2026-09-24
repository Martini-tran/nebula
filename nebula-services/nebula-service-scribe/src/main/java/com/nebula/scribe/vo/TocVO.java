package com.nebula.scribe.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 作品目录：卷 + 章节（不含正文），重排、删卷等会改动结构的操作都返回它，前端整体替换
 */
@Data
@AllArgsConstructor
public class TocVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<VolumeVO> volumes;

    private List<ChapterListVO> chapters;
}
