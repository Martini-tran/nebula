package com.nebula.scribe.service.impl;

import com.nebula.scribe.entity.ScribeChapter;
import com.nebula.scribe.entity.ScribeVolume;
import com.nebula.scribe.vo.ChapterDetailVO;
import com.nebula.scribe.vo.ChapterListVO;
import com.nebula.scribe.vo.VolumeVO;

/**
 * 卷/章节实体到 VO 的转换，卷服务与章节服务共用
 */
final class ScribeConverters {

    private ScribeConverters() {
    }

    static VolumeVO toVolumeVO(ScribeVolume volume) {
        VolumeVO vo = new VolumeVO();
        vo.setId(volume.getId());
        vo.setWorkId(volume.getWorkId());
        vo.setTitle(volume.getTitle());
        vo.setSynopsis(volume.getSynopsis());
        vo.setSortOrder(volume.getSortOrder());
        vo.setUpdateTime(volume.getUpdateTime());
        return vo;
    }

    static ChapterListVO toChapterListVO(ScribeChapter chapter) {
        return fillChapter(chapter, new ChapterListVO());
    }

    static ChapterDetailVO toChapterDetailVO(ScribeChapter chapter) {
        ChapterDetailVO vo = fillChapter(chapter, new ChapterDetailVO());
        vo.setContent(chapter.getContent() == null ? "" : chapter.getContent());
        vo.setRevision(chapter.getRevision());
        return vo;
    }

    private static <T extends ChapterListVO> T fillChapter(ScribeChapter chapter, T vo) {
        vo.setId(chapter.getId());
        vo.setWorkId(chapter.getWorkId());
        vo.setVolumeId(chapter.getVolumeId());
        vo.setTitle(chapter.getTitle());
        vo.setSortOrder(chapter.getSortOrder());
        vo.setStatus(chapter.getStatus());
        vo.setWordCount(chapter.getWordCount());
        vo.setSynopsis(chapter.getSynopsis());
        vo.setUpdateTime(chapter.getUpdateTime());
        return vo;
    }
}
