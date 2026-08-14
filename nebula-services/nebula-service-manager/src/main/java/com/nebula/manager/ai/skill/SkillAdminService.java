package com.nebula.manager.ai.skill;

import com.nebula.common.core.domain.PageResult;
import com.nebula.manager.dto.SkillPageQuery;
import com.nebula.manager.dto.SkillSaveRequest;
import com.nebula.manager.vo.SkillVO;

import java.util.List;

/**
 * AI技能管理服务接口（管理员端）
 * 负责技能（{@code ai_skill}）的分页/详情/创建/更新/删除/启停。
 *
 * @author nebula
 */
public interface SkillAdminService {

    /**
     * 分页查询技能
     *
     * @param query 查询参数
     * @return 分页结果
     */
    PageResult<SkillVO> page(SkillPageQuery query);

    /**
     * 获取技能详情
     *
     * @param id 技能ID
     * @return 技能详情
     */
    SkillVO detail(Long id);

    /**
     * 创建技能
     *
     * @param request 保存请求
     * @return 新建技能ID
     */
    Long create(SkillSaveRequest request);

    /**
     * 更新技能（技能编码不可变更）
     *
     * @param id      技能ID
     * @param request 保存请求
     */
    void update(Long id, SkillSaveRequest request);

    /**
     * 删除技能
     *
     * @param id 技能ID
     */
    void delete(Long id);

    /**
     * 更新启用/停用状态
     *
     * @param id     技能ID
     * @param status 状态：0=停用 1=启用
     */
    void updateStatus(Long id, Integer status);

    /**
     * 查询全部启用技能（编码+名称），供 Agent/节点配置面板下拉选择
     *
     * @return 启用技能列表
     */
    List<SkillVO> options();
}
