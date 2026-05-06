package com.nebula.common.config.listener;

import com.nebula.common.config.constant.ConfigConstants;
import com.nebula.common.redis.util.RedisUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 把 {@link SysConfigRefreshListener} 注册到共享的 RedisMessageListenerContainer。
 * 订阅的 channel 使用 RedisUtils 加 namespace 后的实际名称，与发布端保持一致。
 *
 * @author nebula
 */
public class SysConfigSubscriberRegistrar {

    private final RedisMessageListenerContainer container;
    private final SysConfigRefreshListener listener;
    private final RedisUtils redisUtils;

    public SysConfigSubscriberRegistrar(RedisMessageListenerContainer container,
                                        SysConfigRefreshListener listener,
                                        RedisUtils redisUtils) {
        this.container = container;
        this.listener = listener;
        this.redisUtils = redisUtils;
    }

    @PostConstruct
    public void register() {
        container.addMessageListener(listener, new ChannelTopic(redisUtils.key(ConfigConstants.REFRESH_CHANNEL)));
    }
}
