package com.nebula.common.ai.flow.store;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 编排回调投递记录 Mapper
 * 继承 {@code BaseMapper<AiWebhookDelivery>}，获得完整 CRUD 能力。
 *
 * @author nebula
 */
@Mapper
public interface AiWebhookDeliveryMapper extends BaseMapper<AiWebhookDelivery> {
}
