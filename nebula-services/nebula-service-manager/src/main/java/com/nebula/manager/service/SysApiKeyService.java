package com.nebula.manager.service;

import com.nebula.common.core.domain.PageResult;
import com.nebula.manager.dto.ApiKeyPageQuery;
import com.nebula.manager.dto.ApiKeySaveRequest;
import com.nebula.manager.vo.ApiKeyCreatedVO;
import com.nebula.manager.vo.ApiKeyVO;

/**
 * API Key（SK）管理服务
 *
 * @author nebula
 */
public interface SysApiKeyService {

    /**
     * 分页查询
     */
    PageResult<ApiKeyVO> page(ApiKeyPageQuery query);

    /**
     * 创建 key，归属当前登录用户。返回值里带 key 明文，仅此一次。
     */
    ApiKeyCreatedVO create(ApiKeySaveRequest request);

    /**
     * 更新名称/授权范围/过期时间/备注（key 本身与所属用户不可变）
     */
    void update(Long id, ApiKeySaveRequest request);

    /**
     * 启用/禁用
     */
    void updateStatus(Long id, Integer status);

    /**
     * 删除（立即失效）
     */
    void delete(Long id);

    /**
     * 把库里的 key 全量同步到 Sa-Token 的 Redis 缓存。
     * 网关没有数据库，只认 Redis 里那份；Redis 丢数据后靠这个把 key 灌回去。
     *
     * @return 同步成功的条数
     */
    int syncToCache();
}
