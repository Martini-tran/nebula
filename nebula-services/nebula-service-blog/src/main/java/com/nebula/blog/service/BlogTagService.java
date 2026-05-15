package com.nebula.blog.service;

import com.nebula.blog.vo.front.TagVO;

import java.util.List;

public interface BlogTagService {

    List<TagVO> getPopularTags(int limit);
}
