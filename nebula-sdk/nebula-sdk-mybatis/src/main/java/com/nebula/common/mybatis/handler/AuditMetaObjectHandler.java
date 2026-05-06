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

    /**
     * 创建时间字段名
     * 用于记录实体对象的创建时间
     */
    private static final String CREATE_TIME = "createTime";

    /**
     * 更新时间字段名
     * 用于记录实体对象的最后更新时间
     */
    private static final String UPDATE_TIME = "updateTime";

    /**
     * 创建人字段名
     * 用于记录实体对象的创建者ID
     */
    private static final String CREATE_BY = "createBy";

    /**
     * 更新人字段名
     * 用于记录实体对象的最后更新者ID
     */
    private static final String UPDATE_BY = "updateBy";

    /**
     * 插入操作时的字段自动填充
     * 在执行INSERT语句时自动填充审计字段
     * 包括创建时间和更新时间为当前时间，以及创建人和更新人为当前登录用户
     *
     * @param metaObject MyBatis的元对象，用于访问实体对象的属性
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        // 获取当前时间戳，用于设置创建时间和更新时间
        LocalDateTime now = LocalDateTime.now();
        // 从用户上下文中获取当前登录用户ID
        Long userId = UserContext.getUserId();
        // 严格插入填充创建时间字段
        strictInsertFill(metaObject, CREATE_TIME, LocalDateTime.class, now);
        // 严格插入填充更新时间字段
        strictInsertFill(metaObject, UPDATE_TIME, LocalDateTime.class, now);
        // 如果存在当前用户ID，则填充创建人和更新人字段
        if (userId != null) {
            strictInsertFill(metaObject, CREATE_BY, Long.class, userId);
            strictInsertFill(metaObject, UPDATE_BY, Long.class, userId);
        }
    }

    /**
     * 更新操作时的字段自动填充
     * 在执行UPDATE语句时自动填充审计字段
     * 只更新更新时间和更新人字段
     *
     * @param metaObject MyBatis的元对象，用于访问实体对象的属性
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        // 严格更新填充更新时间字段为当前时间
        strictUpdateFill(metaObject, UPDATE_TIME, LocalDateTime.class, LocalDateTime.now());
        // 从用户上下文中获取当前登录用户ID
        Long userId = UserContext.getUserId();
        // 如果存在当前用户ID，则更新填充更新人字段
        if (userId != null) {
            strictUpdateFill(metaObject, UPDATE_BY, Long.class, userId);
        }
    }
}
