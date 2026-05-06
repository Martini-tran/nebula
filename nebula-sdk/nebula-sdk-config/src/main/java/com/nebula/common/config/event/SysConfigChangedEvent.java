package com.nebula.common.config.event;

import org.springframework.context.ApplicationEvent;

/**
 * sys_config 变更事件——本进程内派发，下游本地缓存可订阅此事件做失效处理
 *
 * @author nebula
 */
public class SysConfigChangedEvent extends ApplicationEvent {

    private final String configKey;

    public SysConfigChangedEvent(Object source, String configKey) {
        super(source);
        this.configKey = configKey;
    }

    /** 变更的配置 key；全量刷新时为 {@link com.nebula.common.config.constant.ConfigConstants#PAYLOAD_ALL} */
    public String getConfigKey() {
        return configKey;
    }
}
