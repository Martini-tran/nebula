package com.nebula.common.config.listener;

import com.nebula.common.config.event.SysConfigChangedEvent;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

/**
 * 监听 Redis Pub/Sub 通道 nebula:config:refresh。
 * Redis hash 是共享的，发布者已经写入最新值；本监听器只负责派发本进程内的 Spring 事件，
 * 让缓存了配置的下游模块能感知失效。
 *
 * @author nebula
 */
@Slf4j
public class SysConfigRefreshListener implements MessageListener {

    private final ApplicationEventPublisher events;

    public SysConfigRefreshListener(ApplicationEventPublisher events) {
        this.events = events;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String key = new String(message.getBody(), StandardCharsets.UTF_8);
        log.debug("[nebula-config] received refresh notification: key={}", key);
        events.publishEvent(new SysConfigChangedEvent(this, key));
    }
}
