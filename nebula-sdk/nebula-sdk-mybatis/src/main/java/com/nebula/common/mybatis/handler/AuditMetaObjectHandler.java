package com.nebula.common.mybatis.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.nebula.common.core.context.UserContext;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;

/**
 * 审计字段自动填充
 * - insert: createTime / updateTime / createBy / updateBy
 * - update: updateTime / updateBy
 * 不存在的字段 strictXxxFill 会跳过，不会报错
 *
 * @author nebula
 */
@Slf4j
public class AuditMetaObjectHandler implements MetaObjectHandler {

    private static final String CREATE_TIME = "createTime";
    private static final String UPDATE_TIME = "updateTime";
    private static final String CREATE_BY = "createBy";
    private static final String UPDATE_BY = "updateBy";

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = UserContext.getUserId();
        strictInsertFill(metaObject, CREATE_TIME, LocalDateTime.class, now);
        strictInsertFill(metaObject, UPDATE_TIME, LocalDateTime.class, now);
        if (userId != null) {
            strictInsertFill(metaObject, CREATE_BY, Long.class, userId);
            strictInsertFill(metaObject, UPDATE_BY, Long.class, userId);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, UPDATE_TIME, LocalDateTime.class, LocalDateTime.now());
        Long userId = UserContext.getUserId();
        if (userId != null) {
            strictUpdateFill(metaObject, UPDATE_BY, Long.class, userId);
        }
    }
}
