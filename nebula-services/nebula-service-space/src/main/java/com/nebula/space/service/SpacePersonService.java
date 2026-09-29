package com.nebula.space.service;

import com.nebula.space.dto.me.PersonSaveRequest;
import com.nebula.space.search.SearchCriteria;
import com.nebula.space.vo.me.PersonVO;

import java.util.List;

/**
 * 人物卡服务：只操作当前登录用户自己的数据
 *
 * <p>只存卡片本身；往来时间线、互相的承诺里来自会议 / 随手记 / 任务的部分，前端按名字实时汇总。
 * 隐私：人物卡不参与任何分享、公开主页、周报。</p>
 */
public interface SpacePersonService {

    /**
     * 全部人物，按添加顺序
     */
    List<PersonVO> list();

    /**
     * 全局搜索召回：姓名 / 称呼 / 其他叫法 / 分组 / 介绍 / 手记 / 信息命中；&#64;某人 按各种叫法、#xx 按分组
     */
    List<PersonVO> search(SearchCriteria q, int limit);

    /**
     * 新建；姓名必填，同一用户下不能重名
     */
    PersonVO create(PersonSaveRequest req);

    /**
     * 局部保存：只改请求里出现的字段，数组整份覆盖
     */
    PersonVO update(Long id, PersonSaveRequest req);

    /**
     * 只删卡片，会议和随手记里的内容不受影响
     */
    void delete(Long id);
}
