package com.nebula.blog.service;

import com.nebula.blog.dto.front.AiRelayModelStationFrontPageQuery;
import com.nebula.blog.vo.front.AiRelayModelStationFrontVO;
import com.nebula.common.core.domain.PageResult;

/**
 * AI 中转「模型选择站点」（前台）服务
 *
 * <p>以 ai_relay_package_model 为主表，按所选模型横向比较支持该模型的各主站套餐及其单价。</p>
 */
public interface AiRelayModelStationFrontService {

    PageResult<AiRelayModelStationFrontVO> pageModelStations(AiRelayModelStationFrontPageQuery query);
}
