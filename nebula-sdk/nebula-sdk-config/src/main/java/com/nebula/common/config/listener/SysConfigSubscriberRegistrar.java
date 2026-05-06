package com.nebula.common.config.listener;

import com.nebula.common.config.constant.ConfigConstants;
import jakarta.annotation.PostConstruct;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 把 {@link SysConfigRefreshListener} 注册到共享的 RedisMessageListenerContainer
 *
 * @author nebula
 */
public class SysConfigSubscriberRegistrar {

    private final RedisMessageListenerContainer container;
    private final SysConfigRefreshListener listener;

    public SysConfigSubscriberRegistrar(RedisMessageListenerContainer container,
                                        SysConfigRefreshListener listener) {
        this.container = container;
        this.listener = listener;
    }

    @PostConstruct
    public void register() {
        container.addMessageListener(listener, new ChannelTopic(ConfigConstants.REFRESH_CHANNEL));
    }
}
