package com.nebula.scribe.service;

import com.nebula.scribe.dto.TocSortRequest;
import com.nebula.scribe.dto.VolumeSaveRequest;
import com.nebula.scribe.vo.TocVO;
import com.nebula.scribe.vo.VolumeVO;

import java.util.List;

/**
 * 卷与目录结构服务
 *
 * <p>守住一条不变式：作品要么没有卷（章节全部 volume_id 为空），要么每一章都属于某一卷。</p>
 */
public interface ScribeVolumeService {

    /**
     * 作品的卷，按排序值升序
     */
    List<VolumeVO> list(Long workId);

    /**
     * 追加一卷；这是第一卷时，把现有章节全部收编进来
     */
    VolumeVO create(Long workId, VolumeSaveRequest req);

    /**
     * 改卷名与梗概（整表单覆盖）
     */
    VolumeVO update(Long workId, Long volumeId, VolumeSaveRequest req);

    /**
     * 删除卷：章节并入上一卷末尾（删第一卷则并入下一卷开头，删唯一一卷则回到无卷），返回新目录
     */
    TocVO delete(Long workId, Long volumeId);

    /**
     * 按整棵目录树重排：卷顺序、卷内顺序与跨卷移动一次提交，返回新目录
     */
    TocVO sort(Long workId, TocSortRequest req);
}
